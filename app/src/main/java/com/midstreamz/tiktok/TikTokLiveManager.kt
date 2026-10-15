package com.midstreamz.tiktok

import android.content.Context
import com.midstreamz.audio.AlertSoundManager
import com.midstreamz.manager.LinkKeyRelayManager
import com.midstreamz.manager.LiveStreamManager
import com.midstreamz.manager.SongRequestManager
import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import com.midstreamz.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TikTokLiveManager(
    private val context: Context,
    val ttsManager: TtsManager,
    val alertSoundManager: AlertSoundManager,
    val linkKeyRelayManager: LinkKeyRelayManager,
    val songRequestManager: SongRequestManager
) : LiveStreamManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var streamJob: Job? = null
    private var simulationJob: Job? = null

    private val _connectionStatus = MutableStateFlow("Siap")
    override val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    override val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    override val chatMessages: StateFlow<List<LiveChatMessage>> = _chatMessages.asStateFlow()

    private val tikTokReader = TikTokLiveReader(
        onMessageReceived = { message ->
            handleIncomingMessage(message)
        },
        onStatusChanged = { status ->
            _connectionStatus.value = status
            _isConnected.value = status.startsWith("Terhubung", ignoreCase = true)
        }
    )

    override fun connect(targetInput: String) {
        disconnect()
        streamJob = scope.launch {
            tikTokReader.start(targetInput)
        }
    }

    override fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        stopSimulation()
        tikTokReader.stop()
        _connectionStatus.value = "Terputus"
        _isConnected.value = false
    }

    private fun handleIncomingMessage(message: LiveChatMessage) {
        val current = _chatMessages.value.toMutableList()
        current.add(0, message)
        if (current.size > 150) {
            current.removeAt(current.size - 1)
        }
        _chatMessages.value = current

        val shouldPlaySound = alertSoundManager.shouldPlaySound(message.type)
        val shouldEnqueueTts = when (message.type) {
            EventType.SUPER_CHAT, EventType.MEMBERSHIP, EventType.SUBSCRIBER, EventType.LIKE, EventType.SHARE -> {
                alertSoundManager.shouldSpeakTts(message.type)
            }
            else -> true
        }

        // Play Alert Audio first, then enqueue to TTS (jangan berbarengan)
        if (shouldPlaySound && shouldEnqueueTts) {
            alertSoundManager.playAlertForEvent(message.type) {
                ttsManager.enqueue(message)
            }
        } else if (shouldPlaySound) {
            alertSoundManager.playAlertForEvent(message.type)
        } else if (shouldEnqueueTts) {
            ttsManager.enqueue(message)
        }

        // Detect chat command triggers
        if (message.type == EventType.CHAT) {
            val lower = message.message.trim().lowercase()
            if (lower == "!sub" || lower == "!follow" || lower == "/follow") {
                if (alertSoundManager.shouldPlaySound(EventType.SUBSCRIBER)) {
                    alertSoundManager.playSubscriberAlert()
                }
            } else if (lower == "!like" || lower == "/like" || lower == "!tap") {
                if (alertSoundManager.shouldPlaySound(EventType.LIKE)) {
                    alertSoundManager.playLikeAlert()
                }
            }
        }

        // Forward to LinkKey Cloud Relay (OBS Overlay Sync)
        linkKeyRelayManager.publishChat(message)

        // Process Song Request command
        songRequestManager.processChatMessage(message.authorName, message.message)
    }

    override fun toggleSimulation() {
        if (_isSimulating.value) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    fun startSimulation() {
        stopSimulation()
        _isSimulating.value = true
        _connectionStatus.value = "Simulasi TikTok Live Berjalan"
        _isConnected.value = true

        val dummySenders = listOf(
            Triple("Ahmad TikTokers", "Halo kak! Live-nya seru banget malam ini!", false),
            Triple("Siti Nurhaliza", "Semangat terus kak ngontennya!", false),
            Triple("Rian Gaming", "!sr Mahalini - Sial", false),
            Triple("Mod_Bagus", "Jangan lupa tap-tap layar dan follow ya guys!", true),
            Triple("Dedi Setiawan", "Bagi gift mawar dong kawan-kawan!", false),
            Triple("Fikri_ID", "!like", false),
            Triple("Maya Putri", "Lagu background-nya enak banget didengar", false),
            Triple("OwnerStreamer", "Makasih semua yang sudah mampir ke live aku ya!", false)
        )

        val dummyGifts = listOf(
            Pair("Sultan Bekasi", "Paus Biru (2150 Koin)"),
            Pair("Amanda_Chantika", "10x Mawar (10 Koin)"),
            Pair("Bang Oyen", "Singa Sultan (29999 Koin)"),
            Pair("Dinda Lucu", "Donat Cokelat (30 Koin)"),
            Pair("Rizky_Bro", "Topi Koboi (99 Koin)")
        )

        val dummyFollowers = listOf(
            "Sarah Gemoy", "Rian Hidayat", "Citra Kirana", "Bagus Pratama", "Dwi Anggraini"
        )

        simulationJob = scope.launch {
            var counter = 0
            while (isActive && _isSimulating.value) {
                delay(3200L)
                counter++

                val event: LiveChatMessage = when {
                    counter % 7 == 0 -> {
                        // Gift Event (mapped to SUPER_CHAT)
                        val gift = dummyGifts.random()
                        LiveChatMessage(
                            id = "sim_tt_gift_${System.currentTimeMillis()}",
                            type = EventType.SUPER_CHAT,
                            authorName = gift.first,
                            message = "mengirim ${gift.second}!",
                            amountFormatted = gift.second,
                            amountColorHex = "#FE2C55",
                            isTikTok = true
                        )
                    }
                    counter % 5 == 0 -> {
                        // Follower Event (mapped to SUBSCRIBER)
                        val follower = dummyFollowers.random()
                        LiveChatMessage(
                            id = "sim_tt_follow_${System.currentTimeMillis()}",
                            type = EventType.SUBSCRIBER,
                            authorName = follower,
                            message = "baru saja mengikuti host!",
                            isTikTok = true
                        )
                    }
                    counter % 4 == 0 -> {
                        // Like Event
                        val liker = dummySenders.random().first
                        LiveChatMessage(
                            id = "sim_tt_like_${System.currentTimeMillis()}",
                            type = EventType.LIKE,
                            authorName = liker,
                            message = "menyukai siaran langsung! (x15)",
                            isTikTok = true
                        )
                    }
                    counter % 9 == 0 -> {
                        // Share Event (mapped to MEMBERSHIP)
                        val sharer = dummySenders.random().first
                        LiveChatMessage(
                            id = "sim_tt_share_${System.currentTimeMillis()}",
                            type = EventType.SHARE,
                            authorName = sharer,
                            message = "membagikan siaran langsung!",
                            isTikTok = true
                        )
                    }
                    else -> {
                        // Normal Chat
                        val sender = dummySenders.random()
                        val isOwner = sender.first == "OwnerStreamer"
                        LiveChatMessage(
                            id = "sim_tt_chat_${System.currentTimeMillis()}",
                            type = EventType.CHAT,
                            authorName = sender.first,
                            message = sender.second,
                            isModerator = sender.third,
                            isOwner = isOwner,
                            isTikTok = true
                        )
                    }
                }

                handleIncomingMessage(event)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        if (_isSimulating.value) {
            _isSimulating.value = false
            _connectionStatus.value = "Siap"
            _isConnected.value = false
        }
    }

    override fun sendTestEvent(type: EventType) {
        val testMessage = when (type) {
            EventType.SUPER_CHAT -> {
                LiveChatMessage(
                    id = "test_tt_gift_${System.currentTimeMillis()}",
                    type = EventType.SUPER_CHAT,
                    authorName = "Sultan TikTok",
                    message = "mengirim Paus Biru (2150 Koin)!",
                    amountFormatted = "Paus Biru (2150 Koin)",
                    amountColorHex = "#FE2C55",
                    isTikTok = true
                )
            }
            EventType.SUBSCRIBER -> {
                LiveChatMessage(
                    id = "test_tt_follow_${System.currentTimeMillis()}",
                    type = EventType.SUBSCRIBER,
                    authorName = "Follower Baru",
                    message = "baru saja mengikuti host!",
                    isTikTok = true
                )
            }
            EventType.LIKE -> {
                LiveChatMessage(
                    id = "test_tt_like_${System.currentTimeMillis()}",
                    type = EventType.LIKE,
                    authorName = "Penonton Setia",
                    message = "mengirim Like x20!",
                    isTikTok = true
                )
            }
            EventType.SHARE -> {
                LiveChatMessage(
                    id = "test_tt_share_${System.currentTimeMillis()}",
                    type = EventType.SHARE,
                    authorName = "Viewer Aktif",
                    message = "membagikan siaran langsung!",
                    isTikTok = true
                )
            }
            EventType.MEMBERSHIP -> {
                LiveChatMessage(
                    id = "test_tt_member_${System.currentTimeMillis()}",
                    type = EventType.MEMBERSHIP,
                    authorName = "Member TikTok",
                    message = "bergabung menjadi member!",
                    isTikTok = true
                )
            }
            EventType.CHAT -> {
                LiveChatMessage(
                    id = "test_tt_chat_${System.currentTimeMillis()}",
                    type = EventType.CHAT,
                    authorName = "Budi Santoso",
                    message = "!sr Komang - Raim Laode",
                    isTikTok = true
                )
            }
            else -> {
                LiveChatMessage(
                    id = "test_tt_sys_${System.currentTimeMillis()}",
                    type = EventType.SYSTEM,
                    authorName = "TikTok System",
                    message = "Tes event berhasil dijalankan.",
                    isTikTok = true
                )
            }
        }

        handleIncomingMessage(testMessage)
    }

    override fun clearMessages() {
        _chatMessages.value = emptyList()
    }
}
