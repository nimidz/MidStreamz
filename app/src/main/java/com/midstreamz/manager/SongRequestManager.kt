package com.midstreamz.manager

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SongItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val youtubeId: String = "",
    val requester: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class SongRequestManager(
    private val context: Context,
    private val onSongStateChanged: (action: String, currentSong: SongItem?, queue: List<SongItem>) -> Unit
) {
    private val TAG = "SongRequestManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("song_request_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var audioWebView: WebView? = null

    private val _isSrEnabled = MutableStateFlow(prefs.getBoolean("is_sr_enabled", true))
    val isSrEnabled: StateFlow<Boolean> = _isSrEnabled.asStateFlow()

    private val _commandKeyword = MutableStateFlow(prefs.getString("sr_command_keyword", "sr") ?: "sr")
    val commandKeyword: StateFlow<String> = _commandKeyword.asStateFlow()

    private val _playOnPhone = MutableStateFlow(prefs.getBoolean("play_on_phone", true))
    val playOnPhone: StateFlow<Boolean> = _playOnPhone.asStateFlow()

    private val _volume = MutableStateFlow(prefs.getInt("sr_volume", 80))
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _duckOnTts = MutableStateFlow(prefs.getBoolean("duck_on_tts", true))
    val duckOnTts: StateFlow<Boolean> = _duckOnTts.asStateFlow()

    private var isCurrentlyDucked = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _maxQueuePerUser = MutableStateFlow(prefs.getInt("max_queue_per_user", 2))
    val maxQueuePerUser: StateFlow<Int> = _maxQueuePerUser.asStateFlow()

    private val _statusText = MutableStateFlow("Siap")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    inner class AndroidSrBridge {
        @JavascriptInterface
        fun onStateChange(state: Int) {
            Log.d(TAG, "YouTube Player state: $state")
            mainHandler.post {
                when (state) {
                    0 -> { // ENDED
                        Log.d(TAG, "Song finished, auto-playing next song in queue...")
                        playNext()
                    }
                    1 -> { // PLAYING
                        _isPlaying.value = true
                        _currentSong.value?.let {
                            _statusText.value = "Memutar: ${it.title}"
                        }
                    }
                    2 -> { // PAUSED
                        _isPlaying.value = false
                        _statusText.value = "Musik Dijeda"
                    }
                    3 -> { // BUFFERING
                        _statusText.value = "Memuat musik..."
                    }
                    5 -> { // CUED
                        Log.d(TAG, "Song cued, requesting playback...")
                        resumePhoneAudio()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onError(errorCode: Int) {
            Log.w(TAG, "YouTube Player onError code: $errorCode")
            mainHandler.post {
                _isPlaying.value = false
                val remainingQueue = _queue.value
                if (remainingQueue.isNotEmpty()) {
                    _statusText.value = "Error ($errorCode), memutar lagu berikutnya..."
                    mainHandler.postDelayed({
                        playNext()
                    }, 2500)
                } else {
                    _statusText.value = "Gagal memutar ($errorCode)"
                    // Do not wipe _currentSong so user can see the song and status
                }
            }
        }
    }

    fun getOrCreateAudioWebView(actContext: Context): WebView {
        val existing = audioWebView
        if (existing != null) {
            (existing.parent as? android.view.ViewGroup)?.removeView(existing)
            return existing
        }
        val created = WebView(actContext).apply {
            setupAudioWebView(this)
        }
        audioWebView = created
        return created
    }

    private fun setupAudioWebView(webView: WebView) {
        webView.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Use default WebView user agent to prevent YouTube embed bot detection

            addJavascriptInterface(AndroidSrBridge(), "AndroidSrBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Audio WebView page finished loading: $url")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    Log.d(TAG, "WebView Console: [${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                    return true
                }
            }
        }
    }

    private fun buildPlayerHtml(youtubeId: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background-color: #000;
                        overflow: hidden;
                        position: fixed;
                    }
                    #player {
                        width: 100%;
                        height: 100%;
                    }
                </style>
                <script defer src="https://www.youtube.com/iframe_api"></script>
            </head>
            <body>
                <div id="player"></div>
                <script>
                    var player;
                    function onYouTubeIframeAPIReady() {
                        console.log("YT Iframe API Ready for video: $youtubeId");
                        player = new YT.Player('player', {
                            height: '100%',
                            width: '100%',
                            videoId: '$youtubeId',
                            playerVars: {
                                'autoplay': 1,
                                'playsinline': 1,
                                'controls': 0,
                                'disablekb': 1,
                                'fs': 0,
                                'rel': 0,
                                'enablejsapi': 1,
                                'origin': 'https://com.midstreamz',
                                'iv_load_policy': 3
                            },
                            events: {
                                'onReady': onPlayerReady,
                                'onStateChange': onPlayerStateChange,
                                'onError': onPlayerError
                            }
                        });
                    }

                    function onPlayerReady(event) {
                        console.log("YT Player ready, playing video: $youtubeId");
                        try {
                            event.target.unMute();
                            event.target.setVolume(${_volume.value});
                            event.target.playVideo();
                            setTimeout(function() {
                                try {
                                    var state = event.target.getPlayerState();
                                    if (state !== 1 && state !== 3) {
                                        console.log("Autoplay retry unmuting...");
                                        event.target.mute();
                                        event.target.playVideo();
                                        setTimeout(function() {
                                            event.target.unMute();
                                            event.target.setVolume(${_volume.value});
                                        }, 300);
                                    }
                                } catch(e){}
                            }, 500);
                        } catch (e) {
                            console.error("onPlayerReady play error: " + e);
                        }
                    }

                    window.setPlayerVolume = function(vol) {
                        try {
                            if (player && typeof player.setVolume === 'function') {
                                player.setVolume(vol);
                            }
                        } catch(e) {}
                    };

                    function onPlayerStateChange(event) {
                        console.log("YT Player state change: " + event.data);
                        if (event.data === 5) {
                            try { player.playVideo(); } catch(e){}
                        }
                        if (window.AndroidSrBridge) {
                            window.AndroidSrBridge.onStateChange(event.data);
                        }
                    }

                    function onPlayerError(event) {
                        console.error("YT Player error: " + event.data);
                        if (window.AndroidSrBridge) {
                            window.AndroidSrBridge.onError(event.data);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    init {
        mainHandler.post {
            try {
                if (audioWebView == null) {
                    audioWebView = WebView(context).apply {
                        setupAudioWebView(this)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing audio WebView", e)
            }
        }
    }

    fun toggleSrEnabled(enabled: Boolean) {
        _isSrEnabled.value = enabled
        prefs.edit().putBoolean("is_sr_enabled", enabled).apply()
    }

    fun togglePlayOnPhone(enabled: Boolean) {
        _playOnPhone.value = enabled
        prefs.edit().putBoolean("play_on_phone", enabled).apply()
        if (!enabled) {
            stopPhoneAudio()
        } else if (_currentSong.value != null && _isPlaying.value) {
            playPhoneAudio(_currentSong.value!!.youtubeId)
        }
    }

    fun toggleDuckOnTts(enabled: Boolean) {
        _duckOnTts.value = enabled
        prefs.edit().putBoolean("duck_on_tts", enabled).apply()
        if (!enabled && isCurrentlyDucked) {
            onTtsFinished()
        }
    }

    fun onTtsStarted() {
        if (!_duckOnTts.value) return
        if (isCurrentlyDucked) return
        isCurrentlyDucked = true
        val baseVol = _volume.value
        val duckedVol = (baseVol * 0.20f).toInt().coerceIn(5, 25)
        mainHandler.post {
            try {
                audioWebView?.evaluateJavascript(
                    "try { if (window.setPlayerVolume) window.setPlayerVolume($duckedVol); else if (player && player.setVolume) player.setVolume($duckedVol); } catch(e){}",
                    null
                )
            } catch (_: Exception) {}
        }
        Log.d(TAG, "Song volume ducked for TTS: $duckedVol% (normal was $baseVol%)")
    }

    fun onTtsFinished() {
        val normalVol = _volume.value
        isCurrentlyDucked = false
        mainHandler.post {
            try {
                audioWebView?.evaluateJavascript(
                    "try { if (player && player.unMute) player.unMute(); if (window.setPlayerVolume) window.setPlayerVolume($normalVol); else if (player && player.setVolume) player.setVolume($normalVol); } catch(e){}",
                    null
                )
            } catch (_: Exception) {}
        }
        Log.d(TAG, "Song volume restored to normal: $normalVol% after TTS")
    }

    fun setVolume(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _volume.value = clamped
        prefs.edit().putInt("sr_volume", clamped).apply()
        if (!isCurrentlyDucked) {
            mainHandler.post {
                try {
                    audioWebView?.evaluateJavascript("try { if (window.setPlayerVolume) window.setPlayerVolume($clamped); else if (player && player.setVolume) player.setVolume($clamped); } catch(e){}", null)
                } catch (_: Exception) {}
            }
        }
        onSongStateChanged("VOLUME", _currentSong.value, _queue.value)
    }

    fun setMaxQueuePerUser(limit: Int) {
        _maxQueuePerUser.value = limit
        prefs.edit().putInt("max_queue_per_user", limit).apply()
    }

    fun setCommandKeyword(keyword: String) {
        val clean = keyword.trim().removePrefix("!").removePrefix("/").lowercase()
        val finalKey = if (clean.isBlank()) "sr" else clean
        _commandKeyword.value = finalKey
        prefs.edit().putString("sr_command_keyword", finalKey).apply()
    }

    fun processChatMessage(authorName: String, messageText: String) {
        if (!_isSrEnabled.value) return

        val trimmed = messageText.trim()
        val keyword = _commandKeyword.value

        val isCommand = trimmed.startsWith("!$keyword ", ignoreCase = true)
                || trimmed.startsWith("/$keyword ", ignoreCase = true)
                || trimmed.startsWith("!sr ", ignoreCase = true)
                || trimmed.startsWith("!request ", ignoreCase = true)

        if (isCommand) {
            val query = trimmed.substringAfter(" ").trim()
            if (query.isNotEmpty()) {
                requestSong(query, authorName)
            }
        } else if (trimmed.equals("!skip", ignoreCase = true) || trimmed.equals("!skip-$keyword", ignoreCase = true)) {
            skipCurrent()
        }
    }

    fun requestSong(query: String, requester: String) {
        val currentQueue = _queue.value
        val userPendingCount = currentQueue.count { it.requester.equals(requester, ignoreCase = true) }

        if (userPendingCount >= _maxQueuePerUser.value) {
            _statusText.value = "Maksimal antrian untuk @$requester tercapai"
            return
        }

        _statusText.value = "Mencari lagu: '$query'..."

        scope.launch {
            val (resolvedTitle, videoId) = resolveSongToYoutube(query)
            if (videoId.isNullOrBlank()) {
                _statusText.value = "Lagu '$query' tidak ditemukan di YouTube"
                return@launch
            }

            val newSong = SongItem(
                title = resolvedTitle ?: query,
                youtubeId = videoId,
                requester = requester
            )

            withContext(Dispatchers.Main) {
                val updatedQueue = _queue.value.toMutableList()
                updatedQueue.add(newSong)
                _queue.value = updatedQueue
                _statusText.value = "Ditambahkan: ${newSong.title}"

                if (_currentSong.value == null) {
                    playNext()
                } else {
                    onSongStateChanged("ENQUEUE", _currentSong.value, _queue.value)
                }
            }
        }
    }

    private fun resolveSongToYoutube(query: String): Pair<String?, String?> {
        val trimmed = query.trim()

        // 1. Direct Video ID (11 chars)
        if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?") && !trimmed.contains(" ")) {
            return Pair("YouTube Video ($trimmed)", trimmed)
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
                val id = matcher.group(1)
                return Pair("YouTube Video ($id)", id)
            }
        }

        // 3. Search YouTube for song query
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encoded"

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: return Pair(null, null)

            val videoId = extractRegex(html, """"videoId":"([a-zA-Z0-9_\-]{11})"""")
            val title = extractRegex(html, """"title":\{"runs":\[\{"text":"(.*?)"\}\]""")

            Log.d(TAG, "Search '$trimmed' resolved to id: $videoId, title: $title")
            return Pair(title ?: trimmed, videoId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed searching YouTube for '$trimmed'", e)
            return Pair(null, null)
        }
    }

    fun playNext() {
        val currentQueue = _queue.value.toMutableList()
        if (currentQueue.isNotEmpty()) {
            val nextSong = currentQueue.removeAt(0)
            _queue.value = currentQueue
            _currentSong.value = nextSong
            _isPlaying.value = true
            _statusText.value = "Memutar: ${nextSong.title}"

            // Play audio on phone if enabled
            if (_playOnPhone.value) {
                playPhoneAudio(nextSong.youtubeId)
            }

            onSongStateChanged("PLAY", nextSong, currentQueue)
        } else {
            _currentSong.value = null
            _isPlaying.value = false
            _statusText.value = "Antrian selesai"
            stopPhoneAudio()
            onSongStateChanged("STOP", null, emptyList())
        }
    }

    fun pause() {
        if (_isPlaying.value) {
            _isPlaying.value = false
            _statusText.value = "Musik Dijeda"
            pausePhoneAudio()
            onSongStateChanged("PAUSE", _currentSong.value, _queue.value)
        }
    }

    fun resume() {
        if (!_isPlaying.value && _currentSong.value != null) {
            _isPlaying.value = true
            _statusText.value = "Memutar: ${_currentSong.value?.title}"
            resumePhoneAudio()
            onSongStateChanged("RESUME", _currentSong.value, _queue.value)
        }
    }

    fun skipCurrent() {
        playNext()
    }

    fun removeSong(id: String) {
        val updatedQueue = _queue.value.filter { it.id != id }
        _queue.value = updatedQueue
        onSongStateChanged("REMOVE", _currentSong.value, updatedQueue)
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _currentSong.value = null
        _isPlaying.value = false
        _statusText.value = "Antrian dikosongkan"
        stopPhoneAudio()
        onSongStateChanged("CLEAR", null, emptyList())
    }

    fun stopCurrent() {
        _isPlaying.value = false
        _currentSong.value = null
        _statusText.value = "Musik Dihentikan"
        stopPhoneAudio()
        onSongStateChanged("STOP", null, _queue.value)
    }

    // Phone Audio Helpers (YouTube Iframe API with Origin & Auto-unmute)
    private fun playPhoneAudio(youtubeId: String) {
        mainHandler.post {
            try {
                requestAudioFocus()
                audioWebView?.onResume()
                audioWebView?.resumeTimers()
                val html = buildPlayerHtml(youtubeId)
                audioWebView?.loadDataWithBaseURL("https://com.midstreamz", html, "text/html", "UTF-8", null)
                Log.d(TAG, "Playing audio on phone for videoId: $youtubeId via YouTube Iframe API")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio on phone", e)
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                onTtsFinished()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                if (_duckOnTts.value) {
                                    onTtsStarted()
                                }
                            }
                        }
                    }
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus({ }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request exception", e)
        }
    }

    private fun pausePhoneAudio() {
        mainHandler.post {
            try {
                audioWebView?.evaluateJavascript("try { if (player && player.pauseVideo) player.pauseVideo(); } catch(e){}", null)
            } catch (_: Exception) {}
        }
    }

    private fun resumePhoneAudio() {
        mainHandler.post {
            try {
                audioWebView?.onResume()
                audioWebView?.resumeTimers()
                val curVol = _volume.value
                audioWebView?.evaluateJavascript("try { if (player && player.playVideo) { player.unMute(); player.setVolume($curVol); player.playVideo(); } } catch(e){}", null)
            } catch (_: Exception) {}
        }
    }

    private fun stopPhoneAudio() {
        mainHandler.post {
            try {
                audioWebView?.loadUrl("about:blank")
            } catch (_: Exception) {}
        }
    }

    private fun extractRegex(content: String, regex: String): String? {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }
}
