package com.midstreamz.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.midstreamz.model.EventType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class AlertSoundType(val displayName: String) {
    DEFAULT("Default (Nada Bawaan)")
}

enum class AlertOutputMode(val id: String, val label: String) {
    ONLY_SOUND("only_sound", "Only Sound"),
    TTS("tts", "TTS"),
    SOUND_AND_TTS("sound_and_tts", "Sound & TTS"),
    DISABLE("disable", "Disable")
}

data class AlertEventSound(
    val title: String = "Default",
    val audioUrl: String? = null // null = use default synthesized chime
) {
    val isDefault: Boolean
        get() = audioUrl.isNullOrBlank() || title.equals("Default", ignoreCase = true)
}

class AlertSoundManager(context: Context? = null) {

    private val TAG = "AlertSoundManager"
    private val scope = CoroutineScope(Dispatchers.Default)
    private val appContext: Context? = context?.applicationContext

    private val prefs: SharedPreferences? = context?.getSharedPreferences("midstreamz_alert_sound", Context.MODE_PRIVATE)

    var isEnabled: Boolean = prefs?.getBoolean("alert_enabled", true) ?: true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean("alert_enabled", value)?.apply()
        }

    var volume: Float = prefs?.getFloat("alert_volume", 0.85f) ?: 0.85f
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs?.edit()?.putFloat("alert_volume", field)?.apply()
        }

    // Output Mode for each event (Only Sound / TTS / Sound & TTS)
    // NOTE: Like defaults to ONLY_SOUND so like is not read by TTS during live
    var superChatOutputMode: AlertOutputMode = loadOutputMode("superchat", AlertOutputMode.SOUND_AND_TTS)
        set(value) {
            field = value
            saveOutputMode("superchat", value)
        }

    var memberOutputMode: AlertOutputMode = loadOutputMode("member", AlertOutputMode.SOUND_AND_TTS)
        set(value) {
            field = value
            saveOutputMode("member", value)
        }

    var subscriberOutputMode: AlertOutputMode = loadOutputMode("subscriber", AlertOutputMode.SOUND_AND_TTS)
        set(value) {
            field = value
            saveOutputMode("subscriber", value)
        }

    var likeOutputMode: AlertOutputMode = loadOutputMode("like", AlertOutputMode.ONLY_SOUND)
        set(value) {
            field = value
            saveOutputMode("like", value)
        }

    var shareOutputMode: AlertOutputMode = loadOutputMode("share", AlertOutputMode.SOUND_AND_TTS)
        set(value) {
            field = value
            saveOutputMode("share", value)
        }

    // Selected sounds for each event (Default or custom MyInstants sound)
    var superChatSound: AlertEventSound = loadSound("superchat", "Default", null)
        set(value) {
            field = value
            saveSound("superchat", value)
        }

    var memberSound: AlertEventSound = loadSound("member", "Default", null)
        set(value) {
            field = value
            saveSound("member", value)
        }

    var subscriberSound: AlertEventSound = loadSound("subscriber", "Default", null)
        set(value) {
            field = value
            saveSound("subscriber", value)
        }

    var likeSound: AlertEventSound = loadSound("like", "Default", null)
        set(value) {
            field = value
            saveSound("like", value)
        }

    var shareSound: AlertEventSound = loadSound("share", "Default", null)
        set(value) {
            field = value
            saveSound("share", value)
        }

    private var activeMediaPlayer: MediaPlayer? = null
    private val playerLock = Any()

    private fun loadOutputMode(keyPrefix: String, defaultMode: AlertOutputMode): AlertOutputMode {
        val saved = prefs?.getString("${keyPrefix}_output_mode", defaultMode.id) ?: defaultMode.id
        return AlertOutputMode.values().find { it.id == saved } ?: defaultMode
    }

    private fun saveOutputMode(keyPrefix: String, mode: AlertOutputMode) {
        prefs?.edit()?.putString("${keyPrefix}_output_mode", mode.id)?.apply()
    }

    fun shouldPlaySound(type: EventType): Boolean {
        if (!isEnabled) return false
        val mode = when (type) {
            EventType.SUPER_CHAT -> superChatOutputMode
            EventType.MEMBERSHIP -> memberOutputMode
            EventType.SUBSCRIBER -> subscriberOutputMode
            EventType.LIKE -> likeOutputMode
            EventType.SHARE -> shareOutputMode
            else -> AlertOutputMode.SOUND_AND_TTS
        }
        return mode == AlertOutputMode.ONLY_SOUND || mode == AlertOutputMode.SOUND_AND_TTS
    }

    fun shouldSpeakTts(type: EventType): Boolean {
        val mode = when (type) {
            EventType.SUPER_CHAT -> superChatOutputMode
            EventType.MEMBERSHIP -> memberOutputMode
            EventType.SUBSCRIBER -> subscriberOutputMode
            EventType.LIKE -> likeOutputMode
            EventType.SHARE -> shareOutputMode
            else -> AlertOutputMode.SOUND_AND_TTS
        }
        return mode == AlertOutputMode.TTS || mode == AlertOutputMode.SOUND_AND_TTS
    }

    private fun loadSound(keyPrefix: String, defaultTitle: String, defaultUrl: String?): AlertEventSound {
        val title = prefs?.getString("${keyPrefix}_title", defaultTitle) ?: defaultTitle
        val url = prefs?.getString("${keyPrefix}_url", defaultUrl) ?: defaultUrl
        return AlertEventSound(title, url)
    }

    private fun saveSound(keyPrefix: String, sound: AlertEventSound) {
        prefs?.edit()?.apply {
            putString("${keyPrefix}_title", sound.title)
            putString("${keyPrefix}_url", sound.audioUrl)
            apply()
        }
    }

    fun playSuperChatAlert(onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        Log.d(TAG, "Playing Super Chat alert: ${superChatSound.title}")
        playEventSound(superChatSound, onCompletion)
    }

    fun playMemberAlert(onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        Log.d(TAG, "Playing Member alert: ${memberSound.title}")
        playEventSound(memberSound, onCompletion)
    }

    fun playSubscriberAlert(onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        Log.d(TAG, "Playing Subscriber alert: ${subscriberSound.title}")
        playEventSound(subscriberSound, onCompletion)
    }

    fun playLikeAlert(onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        Log.d(TAG, "Playing Like alert: ${likeSound.title}")
        playEventSound(likeSound, onCompletion)
    }

    fun playShareAlert(onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        Log.d(TAG, "Playing Share alert: ${shareSound.title}")
        playEventSound(shareSound, onCompletion)
    }

    fun playAlertForEvent(type: EventType, onCompletion: (() -> Unit)? = null) {
        when (type) {
            EventType.SUPER_CHAT -> playSuperChatAlert(onCompletion)
            EventType.MEMBERSHIP -> playMemberAlert(onCompletion)
            EventType.SUBSCRIBER -> playSubscriberAlert(onCompletion)
            EventType.LIKE -> playLikeAlert(onCompletion)
            EventType.SHARE -> playShareAlert(onCompletion)
            else -> onCompletion?.invoke()
        }
    }

    fun playSound(type: AlertSoundType, onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        playDefaultSound(onCompletion)
    }

    fun playEventSound(sound: AlertEventSound, onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        val url = sound.audioUrl
        if (!url.isNullOrBlank()) {
            playRemoteOrCacheAudio(url, onCompletion)
        } else {
            playDefaultSound(onCompletion)
        }
    }

    fun previewAudio(audioUrl: String, onCompletion: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            synchronized(playerLock) {
                try {
                    activeMediaPlayer?.stop()
                    activeMediaPlayer?.release()
                    activeMediaPlayer = null

                    val mp = MediaPlayer()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    if (audioUrl.startsWith("content://") && appContext != null) {
                        mp.setDataSource(appContext, Uri.parse(audioUrl))
                    } else {
                        mp.setDataSource(audioUrl)
                    }
                    val vol = volume.coerceIn(0f, 1f)
                    mp.setVolume(vol, vol)
                    mp.setOnPreparedListener { player ->
                        player.start()
                    }
                    mp.setOnCompletionListener { player ->
                        try {
                            player.release()
                        } catch (_: Exception) {}
                        synchronized(playerLock) {
                            if (activeMediaPlayer == player) activeMediaPlayer = null
                        }
                        onCompletion?.invoke()
                    }
                    mp.setOnErrorListener { player, what, extra ->
                        Log.e(TAG, "MediaPlayer preview error: what=$what, extra=$extra")
                        try {
                            player.release()
                        } catch (_: Exception) {}
                        synchronized(playerLock) {
                            if (activeMediaPlayer == player) activeMediaPlayer = null
                        }
                        onCompletion?.invoke()
                        true
                    }
                    activeMediaPlayer = mp
                    mp.prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "Error previewing audio $audioUrl", e)
                    onCompletion?.invoke()
                }
            }
        }
    }

    fun stopPreview() {
        synchronized(playerLock) {
            try {
                activeMediaPlayer?.stop()
                activeMediaPlayer?.release()
                activeMediaPlayer = null
            } catch (_: Exception) {}
        }
    }

    private fun playRemoteOrCacheAudio(audioUrl: String, onCompletion: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            synchronized(playerLock) {
                try {
                    activeMediaPlayer?.stop()
                    activeMediaPlayer?.release()
                    activeMediaPlayer = null

                    val mp = MediaPlayer()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    if (audioUrl.startsWith("content://") && appContext != null) {
                        mp.setDataSource(appContext, Uri.parse(audioUrl))
                    } else {
                        mp.setDataSource(audioUrl)
                    }
                    val vol = volume.coerceIn(0f, 1f)
                    mp.setVolume(vol, vol)
                    mp.setOnPreparedListener { player ->
                        player.start()
                    }
                    mp.setOnCompletionListener { player ->
                        try {
                            player.release()
                        } catch (_: Exception) {}
                        synchronized(playerLock) {
                            if (activeMediaPlayer == player) activeMediaPlayer = null
                        }
                        onCompletion?.invoke()
                    }
                    mp.setOnErrorListener { player, what, extra ->
                        Log.w(TAG, "Custom audio failed ($what, $extra), falling back to default chime")
                        try {
                            player.release()
                        } catch (_: Exception) {}
                        synchronized(playerLock) {
                            if (activeMediaPlayer == player) activeMediaPlayer = null
                        }
                        playDefaultSound(onCompletion)
                        true
                    }
                    activeMediaPlayer = mp
                    mp.prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing custom audio $audioUrl, fallback to default", e)
                    playDefaultSound(onCompletion)
                }
            }
        }
    }

    fun playDefaultSound(onCompletion: (() -> Unit)? = null) {
        if (!isEnabled) {
            onCompletion?.invoke()
            return
        }
        scope.launch(Dispatchers.Default) {
            try {
                val samples = generateDefaultSamples()
                playPcmSamples(samples, volume)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing default chime", e)
            } finally {
                onCompletion?.invoke()
            }
        }
    }

    private fun generateDefaultSamples(): ShortArray {
        // High quality dual-chime bell tone (987.77 Hz -> 1318.51 Hz)
        val sampleRate = 44100
        val durationMs = 450
        val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val splitIndex = (sampleRate * 0.08).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val freq = if (i < splitIndex) 987.77 else 1318.51
            val envelope = if (i < splitIndex) 1.0 else exp(-(t - 0.08) * 8.0)
            val sample = sin(2.0 * PI * freq * t) * envelope
            buffer[i] = (sample * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return buffer
    }

    private fun playPcmSamples(samples: ShortArray, vol: Float) {
        val sampleRate = 44100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, samples.size * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.setVolume(vol.coerceIn(0f, 1f))
        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()

        val durationMs = (samples.size.toDouble() / sampleRate * 1000).toLong() + 100L
        Thread.sleep(durationMs)
        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (_: Exception) {}
    }
}
