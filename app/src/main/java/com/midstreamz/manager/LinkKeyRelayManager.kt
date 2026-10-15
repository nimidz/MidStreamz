package com.midstreamz.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

data class OverlayEvent(
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, Any?>
)

class LinkKeyRelayManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("linkkey_relay_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var mqttClient: MqttClient? = null
    private val brokerUri = "tcp://broker.emqx.io:1883"

    private val _linkKey = MutableStateFlow(loadOrCreateLinkKey())
    val linkKey: StateFlow<String> = _linkKey.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastStatus = MutableStateFlow("Menghubungkan ke Cloud Relay...")
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    // Sync Toggles
    private val _syncChatToOverlay = MutableStateFlow(prefs.getBoolean("sync_chat", true))
    val syncChatToOverlay: StateFlow<Boolean> = _syncChatToOverlay.asStateFlow()

    private val _syncAlertsToOverlay = MutableStateFlow(prefs.getBoolean("sync_alerts", true))
    val syncAlertsToOverlay: StateFlow<Boolean> = _syncAlertsToOverlay.asStateFlow()

    private val _syncSongToOverlay = MutableStateFlow(prefs.getBoolean("sync_song", true))
    val syncSongToOverlay: StateFlow<Boolean> = _syncSongToOverlay.asStateFlow()

    init {
        connect()
    }

    private fun loadOrCreateLinkKey(): String {
        var key = prefs.getString("user_linkkey", null)
        if (key.isNullOrBlank()) {
            val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val randomSuffix = (1..4).map { chars.random() }.joinToString("")
            key = "MID-$randomSuffix"
            prefs.edit().putString("user_linkkey", key).apply()
        }
        return key
    }

    fun updateLinkKey(newKey: String) {
        val sanitized = newKey.trim().uppercase().replace(" ", "")
        if (sanitized.length >= 3) {
            _linkKey.value = sanitized
            prefs.edit().putString("user_linkkey", sanitized).apply()
            reconnect()
        }
    }

    fun toggleSyncChat(enabled: Boolean) {
        _syncChatToOverlay.value = enabled
        prefs.edit().putBoolean("sync_chat", enabled).apply()
    }

    fun toggleSyncAlerts(enabled: Boolean) {
        _syncAlertsToOverlay.value = enabled
        prefs.edit().putBoolean("sync_alerts", enabled).apply()
    }

    fun toggleSyncSong(enabled: Boolean) {
        _syncSongToOverlay.value = enabled
        prefs.edit().putBoolean("sync_song", enabled).apply()
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    fun connect() {
        scope.launch {
            try {
                _lastStatus.value = "Menghubungkan ke EMQX Relay..."
                val clientId = "midstreamz_app_" + UUID.randomUUID().toString().substring(0, 8)
                val client = MqttClient(brokerUri, clientId, MemoryPersistence())
                mqttClient = client

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                }

                client.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        _isConnected.value = true
                        _lastStatus.value = "Terhubung: ${_linkKey.value}"
                    }

                    override fun connectionLost(cause: Throwable?) {
                        _isConnected.value = false
                        _lastStatus.value = "Koneksi terputus: ${cause?.localizedMessage ?: "Relay offline"}"
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {}

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                client.connect(options)
                _isConnected.value = true
                _lastStatus.value = "Terhubung: ${_linkKey.value}"
            } catch (e: Exception) {
                _isConnected.value = false
                _lastStatus.value = "Gagal konek: ${e.message}"
            }
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (_: Exception) {}
        mqttClient = null
        _isConnected.value = false
        _lastStatus.value = "Offline"
    }

    private fun publishRaw(topic: String, json: String) {
        scope.launch {
            try {
                val client = mqttClient
                if (client != null && client.isConnected) {
                    val message = MqttMessage(json.toByteArray(Charsets.UTF_8)).apply {
                        qos = 1
                    }
                    client.publish(topic, message)
                }
            } catch (e: Exception) {
                _lastStatus.value = "Gagal kirim: ${e.message}"
            }
        }
    }

    fun publishEvent(type: String, data: Map<String, Any?>) {
        val topic = "midstreamz/v1/${_linkKey.value}/events"
        val event = OverlayEvent(type = type, data = data)
        val json = gson.toJson(event)
        publishRaw(topic, json)
    }

    fun publishChat(chat: LiveChatMessage) {
        if (!_syncChatToOverlay.value) return
        val data = mapOf(
            "id" to chat.id,
            "author" to chat.authorName,
            "message" to chat.message,
            "isSuperChat" to (chat.type == EventType.SUPER_CHAT),
            "amount" to (chat.amountFormatted ?: ""),
            "isMember" to chat.isMember,
            "isModerator" to chat.isModerator
        )
        publishEvent("CHAT", data)

        // If it's a super chat, automatically send an ALERT event too!
        if (chat.type == EventType.SUPER_CHAT && _syncAlertsToOverlay.value) {
            publishAlert(
                alertType = "SUPER_CHAT",
                user = chat.authorName,
                amount = chat.amountFormatted ?: "SUPER CHAT",
                message = chat.message,
                durationMs = 6000
            )
        } else if (chat.type == EventType.MEMBERSHIP && _syncAlertsToOverlay.value) {
            publishAlert(
                alertType = "MEMBER",
                user = chat.authorName,
                message = "Bergabung menjadi Member!",
                durationMs = 5000
            )
        }
    }

    fun publishAlert(
        alertType: String,
        user: String,
        amount: String? = null,
        message: String? = null,
        durationMs: Long = 5500
    ) {
        if (!_syncAlertsToOverlay.value) return
        val data = mapOf(
            "alertType" to alertType,
            "user" to user,
            "amount" to amount,
            "message" to message,
            "durationMs" to durationMs
        )
        publishEvent("ALERT", data)
    }

    fun publishSongEvent(action: String, song: SongItem?, queue: List<SongItem>) {
        if (!_syncSongToOverlay.value) return
        val data = mapOf(
            "action" to action,
            "song" to if (song != null) mapOf(
                "id" to song.id,
                "title" to song.title,
                "youtubeId" to song.youtubeId,
                "requester" to song.requester
            ) else null,
            "queueCount" to queue.size
        )
        publishEvent("SONG_REQUEST", data)
    }

    fun testAlert(type: String) {
        when (type) {
            "SUPER_CHAT" -> publishAlert(
                alertType = "SUPER_CHAT",
                user = "Sultan Gaming",
                amount = "Rp 100.000",
                message = "Semangat terus live streamingnya bang!",
                durationMs = 6000
            )
            "SUBSCRIBE" -> publishAlert(
                alertType = "SUBSCRIBE",
                user = "Subscriber Baru",
                message = "Baru saja subscribe channel kamu!",
                durationMs = 5000
            )
            "MEMBER" -> publishAlert(
                alertType = "MEMBER",
                user = "Kawan Setia",
                message = "Bergabung menjadi Membership Level 1!",
                durationMs = 5000
            )
            "LIKE_MILESTONE" -> publishAlert(
                alertType = "LIKE_MILESTONE",
                user = "Target 1.000 Like Tercapai!",
                message = "Terima kasih untuk semua yang sudah tap-tap like!",
                durationMs = 5000
            )
        }
    }

    fun testChat() {
        val testChat = LiveChatMessage(
            id = "test_obs_${System.currentTimeMillis()}",
            type = EventType.CHAT,
            authorName = "StreamViewer99",
            message = "Halo bang! Overlay OBS nya keren banget & jernih!",
            isMember = true
        )
        publishChat(testChat)
    }
}
