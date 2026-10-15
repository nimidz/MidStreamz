package com.midstreamz.manager

import android.content.Context
import com.midstreamz.audio.AlertSoundManager
import com.midstreamz.innertube.YouTubeInnerTubeReader
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

class LiveManager(
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

    private val innerTubeReader = YouTubeInnerTubeReader(
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
            innerTubeReader.start(targetInput)
        }
    }

    override fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        stopSimulation()
        innerTubeReader.stop()
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

        // Detect chat commands or text triggers for alerts
        if (message.type == EventType.CHAT) {
            val lower = message.message.trim().lowercase()
            if (lower == "!sub" || lower == "!subscribe" || lower == "/sub") {
                if (alertSoundManager.shouldPlaySound(EventType.SUBSCRIBER)) {
                    alertSoundManager.playSubscriberAlert()
                }
            } else if (lower == "!like" || lower == "/like" || lower == "!jempol") {
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
        _connectionStatus.value = "Simulasi Live Chat Berjalan"
        _isConnected.value = true

        val dummySenders = listOf(
            Triple("Andi Gaming", "Halo bang! Live streamnya seru banget!", false),
            Triple("Rina Member", "Semangat terus kontennya yaa! 🔥", true),
            Triple("Budi_Pro", "GG gameplaynya mantap abis!", false),
            Triple("Moderator_Citra", "Jaga bahasa ya teman-teman di live chat!", false)
        )

        simulationJob = scope.launch {
            var count = 0
            while (isActive) {
                delay(3500)
                count++
                when (count % 8) {
                    0 -> {
                        // Send superchat
                        handleIncomingMessage(
                            LiveChatMessage(
                                id = "sim_sc_${System.currentTimeMillis()}",
                                type = EventType.SUPER_CHAT,
                                authorName = "Sultan_Warkop",
                                message = "Buat beli kopi dan gorengan bang! Semangat terus!",
                                amountFormatted = "Rp 50.000",
                                amountColorHex = "#D97706"
                            )
                        )
                    }
                    2 -> {
                        // Send subscriber alert
                        handleIncomingMessage(
                            LiveChatMessage(
                                id = "sim_sub_${System.currentTimeMillis()}",
                                type = EventType.SUBSCRIBER,
                                authorName = "GamerBaru_123",
                                message = "Baru saja berlangganan / subscribe channel ini!"
                            )
                        )
                    }
                    4 -> {
                        // Send like event
                        handleIncomingMessage(
                            LiveChatMessage(
                                id = "sim_like_${System.currentTimeMillis()}",
                                type = EventType.LIKE,
                                authorName = "FansSejati",
                                message = "Menyukai live stream! (Milestone like)"
                            )
                        )
                    }
                    6 -> {
                        // Send member event
                        handleIncomingMessage(
                            LiveChatMessage(
                                id = "sim_mem_${System.currentTimeMillis()}",
                                type = EventType.MEMBERSHIP,
                                authorName = "Rina_VIP",
                                message = "Baru bergabung menjadi Member VIP!",
                                isMember = true
                            )
                        )
                    }
                    else -> {
                        val pick = dummySenders[count % dummySenders.size]
                        handleIncomingMessage(
                            LiveChatMessage(
                                id = "sim_chat_${System.currentTimeMillis()}",
                                type = EventType.CHAT,
                                authorName = pick.first,
                                message = pick.second,
                                isMember = pick.third,
                                isModerator = pick.first.startsWith("Mod")
                            )
                        )
                    }
                }
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulating.value = false
        if (streamJob == null) {
            _connectionStatus.value = "Siap"
            _isConnected.value = false
        }
    }

    fun sendTestMessage(isSuperChat: Boolean = false) {
        val testType = if (isSuperChat) EventType.SUPER_CHAT else EventType.CHAT
        sendTestEvent(testType)
    }

    override fun sendTestEvent(type: EventType) {
        val testMsg = when (type) {
            EventType.SUPER_CHAT -> {
                LiveChatMessage(
                    id = "test_${System.currentTimeMillis()}",
                    type = EventType.SUPER_CHAT,
                    authorName = "Donatur Sultan",
                    message = "Semangat streamingnya bro! Semoga lancar jaya!",
                    amountFormatted = "Rp 100.000",
                    amountColorHex = "#D97706"
                )
            }
            EventType.MEMBERSHIP -> {
                LiveChatMessage(
                    id = "test_${System.currentTimeMillis()}",
                    type = EventType.MEMBERSHIP,
                    authorName = "Member Baru VIP",
                    message = "Baru saja bergabung menjadi Member!",
                    isMember = true
                )
            }
            EventType.SHARE -> {
                LiveChatMessage(
                    id = "test_share_${System.currentTimeMillis()}",
                    type = EventType.SHARE,
                    authorName = "Penonton Setia",
                    message = "membagikan siaran langsung!"
                )
            }
            EventType.SUBSCRIBER -> {
                LiveChatMessage(
                    id = "test_${System.currentTimeMillis()}",
                    type = EventType.SUBSCRIBER,
                    authorName = "Subscriber Baru",
                    message = "Baru saja subscribe channel!"
                )
            }
            EventType.LIKE -> {
                LiveChatMessage(
                    id = "test_${System.currentTimeMillis()}",
                    type = EventType.LIKE,
                    authorName = "Penyuka Stream",
                    message = "Menyukai siaran langsung!"
                )
            }
            else -> {
                LiveChatMessage(
                    id = "test_${System.currentTimeMillis()}",
                    type = EventType.CHAT,
                    authorName = "PenontonSetia",
                    message = "Halo bang! Fitur lokal berjalan lancar tanpa server!",
                    isMember = true
                )
            }
        }
        handleIncomingMessage(testMsg)
    }

    override fun clearMessages() {
        _chatMessages.value = emptyList()
    }
}
