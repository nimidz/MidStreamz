package com.midstreamz.tiktok

import android.util.Log
import com.google.gson.JsonParser
import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.net.HttpCookie
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class TikTokLiveReader(
    private val onMessageReceived: (LiveChatMessage) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private val TAG = "TikTokLiveReader"

    private val cookieStore = HashMap<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                val host = url.host
                val list = cookieStore.getOrPut(host) { mutableListOf() }
                for (newC in cookies) {
                    list.removeAll { it.name == newC.name }
                    list.add(newC)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            synchronized(cookieStore) {
                val result = mutableListOf<Cookie>()
                cookieStore.forEach { (host, list) ->
                    if (url.host.endsWith(host) || host.endsWith(url.host)) {
                        result.addAll(list)
                    }
                }
                return result
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()

    @Volatile
    private var isRunning = false
    private var activeWebSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val seenMessageIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun stop() {
        isRunning = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            activeWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        activeWebSocket = null
        onStatusChanged("Terputus")
    }

    suspend fun start(targetInput: String) = withContext(Dispatchers.IO) {
        isRunning = true
        seenMessageIds.clear()

        onStatusChanged("Menganalisis link / target TikTok...")
        val target = resolveTarget(targetInput)
        val cleanName = target.username.ifBlank { target.directRoomId ?: "Live" }

        if (target.username.isBlank() && target.directRoomId.isNullOrBlank()) {
            onStatusChanged("Format link / username TikTok tidak valid")
            isRunning = false
            return@withContext
        }

        onStatusChanged("Mencari live TikTok: @$cleanName...")

        var hostIndex = 0

        while (isRunning && isActive) {
            try {
                // 1. Resolve Room Info (Room ID & live status)
                val roomInfo = resolveRoomInfo(target)
                if (!isRunning) return@withContext

                if (roomInfo == null || roomInfo.roomId.isBlank()) {
                    onStatusChanged("User @$cleanName sedang offline / belum live. Mencoba kembali...")
                    delay(4000L)
                    continue
                }

                // 2. Fast cookie warmup
                val ttwid = getOrFetchTtwid()
                if (!isRunning) return@withContext

                // 3. Connect to WebSocket
                val displayName = roomInfo.nickname.ifBlank { cleanName }
                val clusterName = if (hostIndex % 3 == 1) "SG" else "Global"
                onStatusChanged("Menyambungkan chat live (@$displayName - Server $clusterName)...")

                val connectedCompleter = CompletableDeferred<Boolean>()

                connectWebSocket(
                    roomId = roomInfo.roomId,
                    displayName = displayName,
                    ttwid = ttwid,
                    hostIndex = hostIndex,
                    connectedCompleter = connectedCompleter
                )

                // Wait until connected or failed
                val connected = connectedCompleter.await()
                if (!connected) {
                    hostIndex++
                    onStatusChanged("Koneksi gagal. Mencoba server cadangan (${hostIndex % 3 + 1}/3)...")
                    delay(3000L)
                } else {
                    // Stay alive while WebSocket is connected
                    while (isRunning && activeWebSocket != null) {
                        delay(2000L)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in TikTok Live connection loop", e)
                if (isRunning) {
                    hostIndex++
                    onStatusChanged("Koneksi terputus: ${e.message ?: "Mencoba kembali"}...")
                    delay(4000L)
                }
            }
        }
    }

    private data class TikTokRoomInfo(
        val roomId: String,
        val isLive: Boolean,
        val nickname: String,
        val title: String
    )

    private data class TargetResult(
        val username: String,
        val directRoomId: String? = null
    )

    private fun resolveTarget(input: String): TargetResult {
        var raw = input.trim()

        // 1. Check if raw input is direct room ID (15-22 digits)
        val numMatch = Pattern.compile("""\b(\d{15,22})\b""").matcher(raw)
        if (numMatch.find()) {
            val rid = numMatch.group(1)
            if (rid != null && raw.matches(Regex("""^\d{15,22}$"""))) {
                return TargetResult(username = "", directRoomId = rid)
            }
        }

        // 2. Extract URL if user pasted full shared text (e.g. from WhatsApp/Telegram)
        val urlMatch = Pattern.compile("""https?://[^\s"'<>]+""").matcher(raw)
        val url = if (urlMatch.find()) urlMatch.group(0) else null

        if (url != null) {
            // Check direct room_id in URL params
            val rParamMatch = Pattern.compile("""[?&]room_id=(\d{15,22})""").matcher(url)
            if (rParamMatch.find()) {
                val rid = rParamMatch.group(1)
                if (rid != null) return TargetResult(username = "", directRoomId = rid)
            }

            // Check if URL has @username
            val userMatch = Pattern.compile("""tiktok\.com/@([^/?#&]+)""").matcher(url)
            if (userMatch.find()) {
                val u = userMatch.group(1)
                if (!u.isNullOrBlank()) return TargetResult(username = u.trim())
            }

            // If it is a short link (vt.tiktok.com, vm.tiktok.com, tiktok.com/t/)
            if (url.contains("vt.tiktok.com") || url.contains("vm.tiktok.com") || url.contains("/t/")) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val finalUrl = resp.request.url.toString()
                        Log.d(TAG, "Unshortened TikTok URL from $url to $finalUrl")

                        val finalRoomMatch = Pattern.compile("""[?&]room_id=(\d{15,22})|/live/(\d{15,22})""").matcher(finalUrl)
                        if (finalRoomMatch.find()) {
                            val rid = finalRoomMatch.group(1) ?: finalRoomMatch.group(2)
                            if (!rid.isNullOrBlank()) return TargetResult(username = "", directRoomId = rid)
                        }

                        val finalUserMatch = Pattern.compile("""tiktok\.com/@([^/?#&]+)""").matcher(finalUrl)
                        if (finalUserMatch.find()) {
                            val u = finalUserMatch.group(1)
                            if (!u.isNullOrBlank()) return TargetResult(username = u.trim())
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed unshortening link: ${e.message}")
                }
            }
        }

        // 3. Fallback: clean plain username or handle
        var clean = raw.replace("\n", " ").trim()
        val atMatch = Pattern.compile("""@([a-zA-Z0-9_.]+)""").matcher(clean)
        if (atMatch.find()) {
            clean = atMatch.group(1) ?: clean
        }
        clean = clean.removePrefix("@").trim()
        clean = clean.split("?")[0].split("/")[0].split("&")[0].split(" ")[0]

        return TargetResult(username = clean)
    }

    private fun resolveRoomInfo(target: TargetResult): TikTokRoomInfo? {
        // Direct numeric room ID
        if (!target.directRoomId.isNullOrBlank()) {
            return TikTokRoomInfo(target.directRoomId, true, target.username.ifBlank { "TikTok Live" }, "TikTok Live")
        }

        val username = target.username
        if (username.isBlank()) return null

        // Method 1: TikTok official web api-live user/room endpoint
        try {
            val apiUrl = "https://www.tiktok.com/api-live/user/room?aid=1988&app_name=tiktok_web" +
                    "&device_platform=web_pc&app_language=id&browser_language=id-ID&region=ID" +
                    "&user_is_login=false&sourceType=54&staleTime=600000&uniqueId=$username"

            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JsonParser.parseString(body).asJsonObject
                        val data = json.getAsJsonObject("data")
                        val user = data?.getAsJsonObject("user")
                        val liveRoom = data?.getAsJsonObject("liveRoom")

                        val roomId = user?.get("roomId")?.asString
                            ?: liveRoom?.get("roomId")?.asString
                            ?: liveRoom?.get("rawdata")?.asString?.let { raw ->
                                val m = Pattern.compile(""""id"\s*:\s*"(\d+)"""").matcher(raw)
                                if (m.find()) m.group(1) else null
                            } ?: ""

                        val status = liveRoom?.get("status")?.asInt ?: user?.get("status")?.asInt ?: 0
                        val isLive = (status == 2) || (roomId.isNotEmpty() && status != 4) || (roomId.length >= 15)
                        val nickname = user?.get("nickname")?.asString ?: username
                        val title = liveRoom?.get("title")?.asString ?: "TikTok Live"

                        if (roomId.isNotEmpty() && roomId != "0") {
                            Log.d(TAG, "Resolved room info from api-live: roomId=$roomId, isLive=$isLive")
                            return TikTokRoomInfo(roomId, isLive, nickname, title)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "api-live resolution error: ${e.message}")
        }

        // Method 2: Webcast room info endpoint
        try {
            val webcastUrl = "https://webcast.tiktok.com/webcast/room/info/?aid=1988&device_platform=web&unique_id=$username"
            val req = Request.Builder()
                .url(webcastUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val m = Pattern.compile(""""roomId"\s*:\s*"(\d{15,22})"""").matcher(body)
                        if (m.find()) {
                            val rid = m.group(1)
                            if (!rid.isNullOrBlank()) {
                                return TikTokRoomInfo(rid, true, username, "TikTok Live")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "webcast resolution error: ${e.message}")
        }

        // Method 3: Fallback scraping live webpage & universal data
        try {
            val url = "https://www.tiktok.com/@$username/live"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""

                    // Search for roomId or room_id
                    val m = Pattern.compile(""""(?:roomId|room_id|live_room_id)"\s*:\s*"(\d{15,22})"""").matcher(html)
                    if (m.find()) {
                        val roomId = m.group(1)
                        if (!roomId.isNullOrBlank()) {
                            return TikTokRoomInfo(roomId, true, username, "TikTok Live")
                        }
                    }

                    // Search in Universal Data
                    val uniMatcher = Pattern.compile("""<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">([^<]+)</script>""").matcher(html)
                    if (uniMatcher.find()) {
                        val jsonStr = uniMatcher.group(1)
                        val mRoom = Pattern.compile(""""(?:roomId|room_id)"\s*:\s*"(\d{15,22})"""").matcher(jsonStr ?: "")
                        if (mRoom.find()) {
                            val rid = mRoom.group(1)
                            if (!rid.isNullOrBlank()) {
                                return TikTokRoomInfo(rid, true, username, "TikTok Live")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML live fallback resolution error: ${e.message}")
        }

        // Method 4: Fallback scraping user profile page
        try {
            val url = "https://www.tiktok.com/@$username"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val m = Pattern.compile(""""(?:roomId|room_id|live_room_id)"\s*:\s*"(\d{15,22})"""").matcher(html)
                    if (m.find()) {
                        val roomId = m.group(1)
                        if (!roomId.isNullOrBlank()) {
                            return TikTokRoomInfo(roomId, true, username, "TikTok Live")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML profile fallback resolution error: ${e.message}")
        }

        return null
    }

    private fun getOrFetchTtwid(): String {
        // 1. Check existing in cookieJar
        synchronized(cookieStore) {
            cookieStore.values.flatten().forEach { c ->
                if (c.name.equals("ttwid", ignoreCase = true) && c.value.isNotBlank()) {
                    return c.value
                }
            }
        }

        // 2. Quick single attempt to warmup
        try {
            val req = Request.Builder()
                .url("https://www.tiktok.com/live")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                for (header in resp.headers("Set-Cookie")) {
                    for (c in HttpCookie.parse(header)) {
                        if (c.name.equals("ttwid", ignoreCase = true) && c.value.isNotBlank()) {
                            return c.value
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback to valid synthetic ttwid
        return "1%7C${UUID.randomUUID()}%7C${System.currentTimeMillis()}%7C${UUID.randomUUID().toString().substring(0, 8)}"
    }

    private fun buildWssUrl(roomId: String, hostIndex: Int = 0): String {
        val lastRtt = String.format(Locale.US, "%.3f", 100.0 + Math.random() * 100.0)
        val tz = TimeZone.getDefault().id

        val params = linkedMapOf(
            "version_code" to "180800",
            "device_platform" to "web",
            "cookie_enabled" to "true",
            "screen_width" to "1920",
            "screen_height" to "1080",
            "browser_language" to "id-ID",
            "browser_platform" to "Linux x86_64",
            "browser_name" to "Mozilla",
            "browser_version" to "5.0 (X11)",
            "browser_online" to "true",
            "tz_name" to tz,
            "app_name" to "tiktok_web",
            "sup_ws_ds_opt" to "1",
            "update_version_code" to "2.0.0",
            "compress" to "gzip",
            "webcast_language" to "id",
            "ws_direct" to "1",
            "aid" to "1988",
            "live_id" to "12",
            "app_language" to "id",
            "client_enter" to "1",
            "room_id" to roomId,
            "identity" to "audience",
            "history_comment_count" to "6",
            "last_rtt" to lastRtt,
            "heartbeat_duration" to "10000",
            "resp_content_type" to "protobuf",
            "did_rule" to "3"
        )

        val query = params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

        return when (hostIndex % 3) {
            1 -> "wss://webcast16-ws-web-sg.tiktok.com/webcast/im/push/v2/?$query"
            2 -> "wss://webcast-ws.tiktok.com/webcast/im/push/v2/?$query"
            else -> "wss://webcast-ws.tiktok.com/webcast/im/ws_proxy/ws_reuse_supplement/?$query"
        }
    }

    private fun connectWebSocket(
        roomId: String,
        displayName: String,
        ttwid: String,
        hostIndex: Int,
        connectedCompleter: CompletableDeferred<Boolean>
    ) {
        val wssUrl = buildWssUrl(roomId, hostIndex)

        val requestBuilder = Request.Builder()
            .url(wssUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Origin", "https://www.tiktok.com")
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cache-Control", "no-cache")

        if (ttwid.isNotBlank()) {
            requestBuilder.header("Cookie", "ttwid=$ttwid")
        }

        val request = requestBuilder.build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "TikTok WebSocket opened successfully on host $wssUrl!")
                activeWebSocket = webSocket

                // Send initial heartbeat and enter-room frames
                val hbFrame = TikTokFrames.buildHeartbeat(roomId)
                val enterFrame = TikTokFrames.buildEnterRoom(roomId)
                webSocket.send(hbFrame.toByteString())
                webSocket.send(enterFrame.toByteString())

                onStatusChanged("Terhubung: Live chat aktif (@$displayName)")

                // Announce connection to UI
                onMessageReceived(
                    LiveChatMessage(
                        id = "sys_connected_${System.currentTimeMillis()}",
                        type = EventType.SYSTEM,
                        authorName = "TikTok Live",
                        message = "Terhubung ke live stream @$displayName (Room: $roomId)"
                    )
                )

                // Start periodic 10-second heartbeat
                heartbeatJob?.cancel()
                heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive && activeWebSocket != null) {
                        delay(10000L)
                        try {
                            val hb = TikTokFrames.buildHeartbeat(roomId)
                            activeWebSocket?.send(hb.toByteString())
                        } catch (e: Exception) {
                            Log.w(TAG, "Heartbeat send error: ${e.message}")
                            break
                        }
                    }
                }

                if (!connectedCompleter.isCompleted) {
                    connectedCompleter.complete(true)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(webSocket, bytes.toByteArray(), roomId)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
                activeWebSocket = null
                heartbeatJob?.cancel()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                activeWebSocket = null
                heartbeatJob?.cancel()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                activeWebSocket = null
                heartbeatJob?.cancel()
                if (!connectedCompleter.isCompleted) {
                    connectedCompleter.complete(false)
                }
            }
        })
    }

    private fun handleBinaryMessage(webSocket: WebSocket, raw: ByteArray, roomId: String) {
        try {
            val frame = Proto.decode(raw)
            val payloadType = frame.getString(7)

            if ("msg" == payloadType) {
                val compressed = frame.getRawBytes(8)
                val payload = TikTokFrames.decompressIfGzipped(compressed)
                val response = Proto.decode(payload)

                val needsAck = response.getBool(9)
                val internalExt = response.getRawBytes(5)
                if (needsAck && internalExt.isNotEmpty()) {
                    val logId = frame.getVarint(2)
                    val ack = TikTokFrames.buildAck(logId, internalExt)
                    webSocket.send(ack.toByteString())
                }

                val messages = response.getRepeatedMessages(1)
                for (msg in messages) {
                    val method = msg.getString(1)
                    val msgPayload = msg.getRawBytes(2)
                    dispatchWebcastMessage(method, msgPayload)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing binary frame", e)
        }
    }

    private fun dispatchWebcastMessage(method: String, payload: ByteArray) {
        try {
            val proto = Proto.decode(payload)

            when (method) {
                "WebcastChatMessage" -> {
                    // Tag 1: common, Tag 2: user, Tag 3: content
                    val content = proto.getString(3)
                    val user = proto.getMessage(2)
                    val nickname = user.getString(3).ifBlank { user.getString(38) }.ifBlank { "Penonton" }
                    val uniqueId = user.getString(38)
                    val common = proto.getMessage(1)
                    val msgId = common.getVarint(2)

                    val id = "tt_chat_${msgId.takeIf { it != 0L } ?: System.currentTimeMillis()}_$uniqueId"
                    if (content.isNotBlank() && seenMessageIds.add(id)) {
                        onMessageReceived(
                            LiveChatMessage(
                                id = id,
                                type = EventType.CHAT,
                                authorName = nickname,
                                message = content,
                                isTikTok = true
                            )
                        )
                    }
                }

                "WebcastGiftMessage" -> {
                    // Tag 2: giftId, Tag 5: repeatCount, Tag 6: comboCount, Tag 7: user, Tag 15: gift
                    val user = proto.getMessage(7)
                    val nickname = user.getString(3).ifBlank { user.getString(38) }.ifBlank { "Sultan TikTok" }
                    val repeatCount = proto.getInt(5).coerceAtLeast(1)
                    val gift = proto.getMessage(15)
                    val giftName = gift.getString(2).ifBlank { "Gift TikTok" }
                    val diamonds = gift.getInt(12)

                    val common = proto.getMessage(1)
                    val msgId = common.getVarint(2)
                    val id = "tt_gift_${msgId.takeIf { it != 0L } ?: System.currentTimeMillis()}"

                    if (seenMessageIds.add(id)) {
                        val amountText = if (diamonds > 0) "$giftName x$repeatCount ($diamonds Koin)" else "$giftName x$repeatCount"
                        onMessageReceived(
                            LiveChatMessage(
                                id = id,
                                type = EventType.SUPER_CHAT,
                                authorName = nickname,
                                message = "mengirim $amountText!",
                                amountFormatted = amountText,
                                amountColorHex = "#FE2C55",
                                isTikTok = true
                            )
                        )
                    }
                }

                "WebcastLikeMessage" -> {
                    // Tag 3: count, Tag 4: total, Tag 5: user
                    val user = proto.getMessage(5)
                    val nickname = user.getString(3).ifBlank { user.getString(38) }.ifBlank { "Penonton" }
                    val count = proto.getInt(3).coerceAtLeast(1)
                    val total = proto.getVarint(4)

                    val id = "tt_like_${System.currentTimeMillis()}_${user.getString(38)}"
                    if (seenMessageIds.add(id)) {
                        onMessageReceived(
                            LiveChatMessage(
                                id = id,
                                type = EventType.LIKE,
                                authorName = nickname,
                                message = "menyukai siaran langsung ($count like${if (total > 0) " • Total: $total" else ""})",
                                isTikTok = true
                            )
                        )
                    }
                }

                "WebcastSocialMessage" -> {
                    // Tag 2: user, Tag 3: action (1 = Follow, 2..5 = Share)
                    val user = proto.getMessage(2)
                    val nickname = user.getString(3).ifBlank { user.getString(38) }.ifBlank { "Penonton" }
                    val action = proto.getVarint(3)

                    val id = "tt_social_${System.currentTimeMillis()}_${user.getString(38)}_$action"
                    if (seenMessageIds.add(id)) {
                        if (action == 1L) {
                            // Follow
                            onMessageReceived(
                                LiveChatMessage(
                                    id = id,
                                    type = EventType.SUBSCRIBER,
                                    authorName = nickname,
                                    message = "mulai mengikuti host!",
                                    isTikTok = true
                                )
                            )
                        } else if (action in 2L..5L) {
                            // Share
                            onMessageReceived(
                                LiveChatMessage(
                                    id = id,
                                    type = EventType.SHARE,
                                    authorName = nickname,
                                    message = "membagikan siaran langsung!",
                                    isTikTok = true
                                )
                            )
                        }
                    }
                }

                "WebcastControlMessage" -> {
                    // Tag 2: action (3 = live ended)
                    val action = proto.getInt(2)
                    if (action == 3) {
                        onStatusChanged("Live stream TikTok telah berakhir")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error decoding webcast message method $method", e)
        }
    }
}
