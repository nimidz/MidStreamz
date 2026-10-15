package com.midstreamz.innertube

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeInnerTubeReader(
    private val onMessageReceived: (LiveChatMessage) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private val TAG = "InnerTubeReader"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var isRunning = false
    private val seenMessageIds = mutableSetOf<String>()

    fun stop() {
        isRunning = false
        onStatusChanged("Terputus")
    }

    suspend fun start(targetInput: String) = withContext(Dispatchers.IO) {
        isRunning = true
        seenMessageIds.clear()
        onStatusChanged("Mencari live: $targetInput...")

        val (videoId, resolveMessage) = resolveTargetToVideoId(targetInput)
        if (videoId.isNullOrBlank()) {
            onStatusChanged(resolveMessage ?: "Channel/Video tidak ditemukan atau sedang offline")
            isRunning = false
            return@withContext
        }

        onStatusChanged("Menghubungkan ke chat ($videoId)...")

        try {
            val (apiKey, initialContinuation) = fetchLivePageData(videoId)
            if (initialContinuation.isNullOrBlank()) {
                onStatusChanged("Live chat tidak aktif / video offline ($videoId)")
                isRunning = false
                return@withContext
            }

            onStatusChanged("Terhubung: Live chat aktif ($videoId)")
            var currentContinuation: String = initialContinuation
            val effectiveApiKey = apiKey?.takeIf { it.isNotBlank() } ?: "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

            while (isRunning && isActive) {
                val result = fetchNextChatBatch(effectiveApiKey, currentContinuation)
                if (result == null) {
                    delay(2000)
                    continue
                }

                currentContinuation = result.nextContinuation ?: currentContinuation
                val pollDelayMs = (result.timeoutMs ?: 1500L).coerceIn(800L, 5000L)
                delay(pollDelayMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in live loop", e)
            onStatusChanged("Error: ${e.message}")
            isRunning = false
        }
    }

    private data class BatchResult(
        val nextContinuation: String?,
        val timeoutMs: Long?
    )

    private fun resolveTargetToVideoId(input: String): Pair<String?, String?> {
        val trimmed = input.trim()

        // 1. Direct Video ID (11 chars alphanumeric/_/-)
        if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?") && !trimmed.contains("@") && !trimmed.contains(" ")) {
            return Pair(trimmed, null)
        }

        // 2. Direct Video URLs
        val videoPatterns = listOf(
            "(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})",
            "(?:https?://)?(?:www\\.)?youtube\\.com/live/([a-zA-Z0-9_-]{11})",
            "(?:https?://)?youtu\\.be/([a-zA-Z0-9_-]{11})"
        )
        for (p in videoPatterns) {
            val matcher = Pattern.compile(p).matcher(trimmed)
            if (matcher.find()) {
                return Pair(matcher.group(1), null)
            }
        }

        // 3. Channel Handle (@name) or Channel URL (/channel/UC...)
        val liveCheckUrl = when {
            trimmed.startsWith("@") -> "https://www.youtube.com/$trimmed/live"
            trimmed.contains("/@") -> {
                val handle = trimmed.substring(trimmed.indexOf("/@") + 1).split("/", "?")[0]
                "https://www.youtube.com/$handle/live"
            }
            trimmed.startsWith("UC") && trimmed.length == 24 -> "https://www.youtube.com/channel/$trimmed/live"
            trimmed.contains("/channel/UC") -> {
                val startIdx = trimmed.indexOf("/channel/") + 9
                val channelId = trimmed.substring(startIdx).split("/", "?")[0]
                "https://www.youtube.com/channel/$channelId/live"
            }
            trimmed.startsWith("http") -> {
                if (!trimmed.endsWith("/live")) "$trimmed/live" else trimmed
            }
            else -> "https://www.youtube.com/@$trimmed/live"
        }

        Log.d(TAG, "Resolving live URL: $liveCheckUrl")
        try {
            val request = Request.Builder()
                .url(liveCheckUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            val html = response.body?.string() ?: ""

            // Check if final URL redirected to a specific video
            for (p in videoPatterns) {
                val matcher = Pattern.compile(p).matcher(finalUrl)
                if (matcher.find()) {
                    val id = matcher.group(1)
                    if (!html.contains("LIVE_STREAM_OFFLINE")) {
                        return Pair(id, null)
                    }
                }
            }

            // Check if page indicates stream is offline
            val isOffline = html.contains("LIVE_STREAM_OFFLINE")
            val isLiveNow = html.contains("\"isLiveNow\":true") || html.contains("\"isLive\":true")

            val videoIdInBody = extractRegex(html, """"videoId":"([a-zA-Z0-9_\-]{11})"""")

            if (isLiveNow && !videoIdInBody.isNullOrBlank()) {
                return Pair(videoIdInBody, null)
            }

            if (isOffline) {
                // Try searching live search filter as fallback
                val searchFallback = searchLiveStreamFallback(trimmed)
                if (searchFallback != null) {
                    return Pair(searchFallback, null)
                }
                return Pair(null, "Channel $trimmed sedang offline (tidak ada siaran langsung)")
            }

            if (!videoIdInBody.isNullOrBlank()) {
                return Pair(videoIdInBody, null)
            }

            // 4. Fallback search for active live
            val searchFallback = searchLiveStreamFallback(trimmed)
            if (searchFallback != null) {
                return Pair(searchFallback, null)
            }

            return Pair(null, "Siaran live untuk '$trimmed' tidak ditemukan atau sedang offline")
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving live videoId", e)
            val searchFallback = searchLiveStreamFallback(trimmed)
            if (searchFallback != null) {
                return Pair(searchFallback, null)
            }
            return Pair(null, "Koneksi gagal: ${e.message}")
        }
    }

    private fun searchLiveStreamFallback(query: String): String? {
        try {
            val cleanQuery = query.replace("@", "").trim()
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encoded&sp=CAMSAkAB"

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            val videoId = extractRegex(html, """"videoId":"([a-zA-Z0-9_\-]{11})"""")
            Log.d(TAG, "Live search fallback for '$cleanQuery' resolved: $videoId")
            return videoId
        } catch (e: Exception) {
            Log.w(TAG, "Search fallback failed", e)
            return null
        }
    }

    private fun fetchLivePageData(videoId: String): Pair<String?, String?> {
        // Method 1: Lightweight live_chat endpoint (~250KB)
        val liveChatUrl = "https://www.youtube.com/live_chat?v=$videoId"
        try {
            val request = Request.Builder()
                .url(liveChatUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val apiKey = extractRegex(html, """"INNERTUBE_API_KEY":"([^"]+)"""")
                ?: extractRegex(html, """"apiKey":"([^"]+)"""")

            val continuation = extractRegex(html, """"continuation":"([^"]+)"""")

            if (!continuation.isNullOrBlank()) {
                Log.d(TAG, "Successfully extracted continuation from live_chat page")
                return Pair(apiKey, continuation)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed live_chat page, trying watch page fallback", e)
        }

        // Method 2: Watch page fallback
        try {
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val request = Request.Builder()
                .url(watchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return Pair(null, null)

            val apiKey = extractRegex(html, """"INNERTUBE_API_KEY":"([^"]+)"""")
                ?: extractRegex(html, """"apiKey":"([^"]+)"""")

            val continuation = extractRegex(html, """"continuation":"([^"]+)"""")

            return Pair(apiKey, continuation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed watch page fallback", e)
            return Pair(null, null)
        }
    }

    private fun fetchNextChatBatch(apiKey: String, continuation: String?): BatchResult? {
        if (continuation.isNullOrBlank()) return null
        val url = if (apiKey.isNotBlank() && apiKey != "DEFAULT") {
            "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=$apiKey"
        } else {
            "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat"
        }

        val jsonBody = JsonObject().apply {
            val context = JsonObject()
            val clientObj = JsonObject().apply {
                addProperty("clientName", "WEB")
                addProperty("clientVersion", "2.20240101.00.00")
            }
            context.add("client", clientObj)
            add("context", context)
            addProperty("continuation", continuation)
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null

            val root = JsonParser.parseString(bodyStr).asJsonObject
            val continuationContents = root.getAsJsonObject("continuationContents")
                ?.getAsJsonObject("liveChatContinuation") ?: return null

            val actions = continuationContents.getAsJsonArray("actions")
            if (actions != null) {
                for (actionElem in actions) {
                    val actionObj = actionElem.asJsonObject
                    val addChatItemAction = actionObj.getAsJsonObject("addChatItemAction") ?: continue
                    val item = addChatItemAction.getAsJsonObject("item") ?: continue

                    parseChatItem(item)
                }
            }

            val continuations = continuationContents.getAsJsonArray("continuations")
            var nextKey: String? = null
            var timeoutMs: Long? = 1500L

            if (continuations != null && continuations.size() > 0) {
                val contObj = continuations[0].asJsonObject
                val timedCont = contObj.getAsJsonObject("timedContinuationData")
                    ?: contObj.getAsJsonObject("invalidationContinuationData")
                    ?: contObj.getAsJsonObject("reloadContinuationData")

                if (timedCont != null) {
                    nextKey = timedCont.get("continuation")?.asString
                    timeoutMs = timedCont.get("timeoutMs")?.asLong ?: 1500L
                }
            }

            return BatchResult(nextKey, timeoutMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chat batch", e)
            return null
        }
    }

    private fun parseChatItem(item: JsonObject) {
        // 1. Normal Chat
        item.getAsJsonObject("liveChatTextMessageRenderer")?.let { renderer ->
            val id = renderer.get("id")?.asString ?: System.currentTimeMillis().toString()
            if (seenMessageIds.contains(id)) return
            seenMessageIds.add(id)

            val author = renderer.getAsJsonObject("authorName")?.get("simpleText")?.asString ?: "Anonymous"
            val message = extractRunsText(renderer.getAsJsonObject("message")?.getAsJsonArray("runs"))

            var isMod = false
            var isOwner = false
            var isMember = false
            renderer.getAsJsonArray("authorBadges")?.forEach { badgeElem ->
                val label = badgeElem.asJsonObject.getAsJsonObject("liveChatAuthorBadgeRenderer")
                    ?.getAsJsonObject("accessibility")
                    ?.getAsJsonObject("accessibilityData")
                    ?.get("label")?.asString ?: ""
                if (label.contains("Moderator", ignoreCase = true)) isMod = true
                if (label.contains("Owner", ignoreCase = true)) isOwner = true
                if (label.contains("Member", ignoreCase = true)) isMember = true
            }

            val chatMsg = LiveChatMessage(
                id = id,
                type = EventType.CHAT,
                authorName = author,
                message = message,
                isModerator = isMod,
                isOwner = isOwner,
                isMember = isMember
            )
            onMessageReceived(chatMsg)
            return
        }

        // 2. Super Chat
        item.getAsJsonObject("liveChatPaidMessageRenderer")?.let { renderer ->
            val id = renderer.get("id")?.asString ?: System.currentTimeMillis().toString()
            if (seenMessageIds.contains(id)) return
            seenMessageIds.add(id)

            val author = renderer.getAsJsonObject("authorName")?.get("simpleText")?.asString ?: "Anonymous"
            val message = extractRunsText(renderer.getAsJsonObject("message")?.getAsJsonArray("runs"))
            val amount = renderer.getAsJsonObject("purchaseAmountText")?.get("simpleText")?.asString ?: "Super Chat"

            val chatMsg = LiveChatMessage(
                id = id,
                type = EventType.SUPER_CHAT,
                authorName = author,
                message = message,
                amountFormatted = amount
            )
            onMessageReceived(chatMsg)
            return
        }

        // 3. Membership Item
        item.getAsJsonObject("liveChatMembershipItemRenderer")?.let { renderer ->
            val id = renderer.get("id")?.asString ?: System.currentTimeMillis().toString()
            if (seenMessageIds.contains(id)) return
            seenMessageIds.add(id)

            val author = renderer.getAsJsonObject("authorName")?.get("simpleText")?.asString ?: "Anonymous"
            val headerText = extractRunsText(renderer.getAsJsonObject("headerSubtext")?.getAsJsonArray("runs"))

            val chatMsg = LiveChatMessage(
                id = id,
                type = EventType.MEMBERSHIP,
                authorName = author,
                message = if (headerText.isNotBlank()) headerText else "Welcome to membership!",
                isMember = true
            )
            onMessageReceived(chatMsg)
            return
        }
    }

    private fun extractRunsText(runs: JsonArray?): String {
        if (runs == null) return ""
        val sb = StringBuilder()
        for (elem in runs) {
            val run = elem.asJsonObject
            val text = run.get("text")?.asString
            if (text != null) {
                sb.append(text)
            }
        }
        return sb.toString()
    }

    private fun extractRegex(content: String, regex: String): String? {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }
}
