package com.midstreamz.manager

import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import kotlinx.coroutines.flow.StateFlow

interface LiveStreamManager {
    val connectionStatus: StateFlow<String>
    val isConnected: StateFlow<Boolean>
    val isSimulating: StateFlow<Boolean>
    val chatMessages: StateFlow<List<LiveChatMessage>>
    fun connect(targetInput: String)
    fun disconnect()
    fun toggleSimulation()
    fun sendTestEvent(type: EventType)
    fun clearMessages()
}
