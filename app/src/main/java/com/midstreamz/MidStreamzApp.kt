package com.midstreamz

import android.app.Application
import com.midstreamz.audio.AlertSoundManager
import com.midstreamz.manager.LinkKeyRelayManager
import com.midstreamz.manager.LiveManager
import com.midstreamz.manager.SongItem
import com.midstreamz.manager.SongRequestManager
import com.midstreamz.tts.TtsManager

class MidStreamzApp : Application() {

    lateinit var ttsManager: TtsManager
        private set
    lateinit var alertSoundManager: AlertSoundManager
        private set
    lateinit var linkKeyRelayManager: LinkKeyRelayManager
        private set
    lateinit var songRequestManager: SongRequestManager
        private set
    lateinit var liveManager: LiveManager
        private set
    lateinit var tikTokLiveManager: com.midstreamz.tiktok.TikTokLiveManager
        private set
    lateinit var overlayConfigManager: com.midstreamz.manager.OverlayConfigManager
        private set
    lateinit var appLanguageManager: com.midstreamz.manager.AppLanguageManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appLanguageManager = com.midstreamz.manager.AppLanguageManager(this)
        overlayConfigManager = com.midstreamz.manager.OverlayConfigManager(this)
        ttsManager = TtsManager(this)
        alertSoundManager = AlertSoundManager(this)
        linkKeyRelayManager = LinkKeyRelayManager(this)
        songRequestManager = SongRequestManager(this) { action, song, queue ->
            linkKeyRelayManager.publishSongEvent(action, song, queue)
        }
        ttsManager.duckAudioOnTts = songRequestManager.duckOnTts.value
        ttsManager.onSpeakingStateChanged = { isSpeaking ->
            if (isSpeaking) {
                songRequestManager.onTtsStarted()
            } else {
                songRequestManager.onTtsFinished()
            }
        }
        liveManager = LiveManager(this, ttsManager, alertSoundManager, linkKeyRelayManager, songRequestManager)
        tikTokLiveManager = com.midstreamz.tiktok.TikTokLiveManager(this, ttsManager, alertSoundManager, linkKeyRelayManager, songRequestManager)
    }

    companion object {
        lateinit var instance: MidStreamzApp
            private set
    }
}
