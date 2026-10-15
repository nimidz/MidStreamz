package com.midstreamz.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import com.midstreamz.MidStreamzApp
import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import com.midstreamz.model.OverlayAnimIn
import com.midstreamz.model.OverlayAnimOut
import com.midstreamz.model.OverlayAnimType
import com.midstreamz.model.OverlayBgStyle
import com.midstreamz.model.OverlayItemConfig
import com.midstreamz.model.OverlayTextStyle
import com.midstreamz.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LiveOverlayService : Service() {

    private val TAG = "LiveOverlayService"
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var audioHolderView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isHudVisible = false

    companion object {
        const val NOTIFICATION_ID = 9101
        const val CHANNEL_ID = "midstreamz_background_channel"

        const val ACTION_START = "com.midstreamz.action.START"
        const val ACTION_STOP = "com.midstreamz.action.STOP"
        const val ACTION_SHOW_HUD = "com.midstreamz.action.SHOW_HUD"
        const val ACTION_HIDE_HUD = "com.midstreamz.action.HIDE_HUD"
        const val ACTION_TEST_FOLLOW = "com.midstreamz.action.TEST_FOLLOW"
        const val ACTION_TEST_COMMENT = "com.midstreamz.action.TEST_COMMENT"
        const val ACTION_TEST_GIFT = "com.midstreamz.action.TEST_GIFT"
        const val ACTION_TEST_LIKE = "com.midstreamz.action.TEST_LIKE"
        const val ACTION_TEST_SONG = "com.midstreamz.action.TEST_SONG"

        var isServiceRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        acquireLocks()
        startForegroundWithNotification("MidStream", "MidStream sedang berjalan di latar belakang")

        attachAudioPlayer()

        observeChats()
        observeSongStatus()
        observeConnectionStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        attachAudioPlayer()

        when (action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping service via ACTION_STOP")
                try {
                    MidStreamzApp.instance.liveManager.disconnect()
                } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_HUD -> {
                showFloatingHud()
            }
            ACTION_HIDE_HUD -> {
                hideFloatingHud()
            }
            ACTION_TEST_FOLLOW -> {
                showAnimatedOverlay("follow", LiveChatMessage(
                    id = "test_follow_${System.currentTimeMillis()}",
                    type = EventType.SUBSCRIBER,
                    authorName = "Nama",
                    message = "telah follow akun kamu"
                ))
            }
            ACTION_TEST_COMMENT -> {
                showAnimatedOverlay("comment", LiveChatMessage(
                    id = "test_comment_${System.currentTimeMillis()}",
                    type = EventType.CHAT,
                    authorName = "RianStreamer",
                    message = "Halo bang! Overlay komentar nya keren banget 🔥"
                ))
            }
            ACTION_TEST_GIFT -> {
                showAnimatedOverlay("gift", LiveChatMessage(
                    id = "test_gift_${System.currentTimeMillis()}",
                    type = EventType.SUPER_CHAT,
                    authorName = "SultanAndi",
                    message = "Semangat push rank sampai Mythic bang!",
                    amountFormatted = "Rp 50.000"
                ))
            }
            ACTION_TEST_LIKE -> {
                showAnimatedOverlay("like", LiveChatMessage(
                    id = "test_like_${System.currentTimeMillis()}",
                    type = EventType.LIKE,
                    authorName = "DinaCantik",
                    message = "like"
                ))
            }
            ACTION_TEST_SONG -> {
                showAnimatedOverlay("song", LiveChatMessage(
                    id = "test_song_${System.currentTimeMillis()}",
                    type = EventType.CHAT,
                    authorName = "Farel",
                    message = "Sheila On 7 - Dan"
                ))
            }
            ACTION_START -> {
                // If permission is already granted, show HUD according to preference
                val config = MidStreamzApp.instance.overlayConfigManager.config
                val prefs = getSharedPreferences("midstreamz_hud_prefs", Context.MODE_PRIVATE)
                val shouldShowHud = prefs.getBoolean("hud_enabled", true)
                if (!config.masterOverlayDisabled && shouldShowHud && Settings.canDrawOverlays(this)) {
                    showFloatingHud()
                }
            }
        }

        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MidStreamz:BackgroundWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock", e)
        }

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MidStreamz:WifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WifiLock", e)
        }
    }

    private fun startForegroundWithNotification(title: String, message: String) {
        createNotificationChannel()

        val notification = buildNotification(title, message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MidStreamz Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga koneksi live chat, TTS dan musik tetap aktif di latar belakang"
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, message: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MidStream")
            .setContentText("MidStream sedang berjalan di latar belakang")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hentikan", stopPendingIntent)
            .build()
    }

    private fun updateNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(title, message))
    }

    fun showFloatingHud() {
        val config = MidStreamzApp.instance.overlayConfigManager.config
        if (config.masterOverlayDisabled) return
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        try {
            createFloatingView()
            isHudVisible = true
            getSharedPreferences("midstreamz_hud_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("hud_enabled", true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create floating view", e)
        }
    }

    fun hideFloatingHud() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view", e)
            }
            overlayView = null
            isHudVisible = false
            getSharedPreferences("midstreamz_hud_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("hud_enabled", false).apply()
        }
        detachAudioPlayer()
    }

    private fun attachAudioPlayer() {
        val config = MidStreamzApp.instance.overlayConfigManager.config
        if (config.masterOverlayDisabled) {
            detachAudioPlayer()
            return
        }
        // Only attach overlay window if HUD is actively showing to prevent Android displaying over other apps system alert
        val isHudActive = overlayView != null || isHudVisible
        if (!isHudActive) {
            detachAudioPlayer()
            return
        }

        try {
            if (audioHolderView != null) return
            if (!Settings.canDrawOverlays(this)) return

            val webView = MidStreamzApp.instance.songRequestManager.getOrCreateAudioWebView(applicationContext)
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                2,
                2,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            windowManager.addView(webView, params)
            audioHolderView = webView
            Log.d(TAG, "Audio WebView attached to Foreground Service WindowManager successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed attaching audio player to WindowManager", e)
        }
    }

    private fun detachAudioPlayer() {
        if (audioHolderView != null) {
            try {
                windowManager.removeView(audioHolderView)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing audio holder view", e)
            }
            audioHolderView = null
        }
    }

    private fun createFloatingView() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0B0D13"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#3B82F6"))
            }
            background = bg
            setPadding(20, 14, 20, 14)
            elevation = 10f
        }

        // Top bar (Header + Close button)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val header = TextView(this).apply {
            text = "🔴 MIDSTREAMZ HUD"
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 4, 0, 4)
        }
        headerRow.addView(header)

        // Close 'X' button
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 14f
            setPadding(16, 4, 8, 4)
            setOnClickListener {
                hideFloatingHud()
            }
        }
        headerRow.addView(closeBtn)
        container.addView(headerRow)

        // Song ticker row (Optional when playing)
        val songTicker = TextView(this).apply {
            id = View.generateViewId()
            text = ""
            setTextColor(Color.parseColor("#34D399"))
            textSize = 10f
            maxLines = 1
            visibility = View.GONE
            setPadding(0, 4, 0, 2)
        }
        container.addView(songTicker)

        // Author text
        val authorText = TextView(this).apply {
            id = View.generateViewId()
            text = "Menunggu chat..."
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 2)
        }
        container.addView(authorText)

        // Message text
        val messageText = TextView(this).apply {
            id = View.generateViewId()
            text = "Live tools aktif di background"
            setTextColor(Color.parseColor("#D1D5DB"))
            textSize = 11f
            maxLines = 3
        }
        container.addView(messageText)

        // Drag listener on header and content without blocking closeBtn
        val dragTouchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        header.setOnTouchListener(dragTouchListener)
        authorText.setOnTouchListener(dragTouchListener)
        messageText.setOnTouchListener(dragTouchListener)
        songTicker.setOnTouchListener(dragTouchListener)

        overlayView = container
        windowManager.addView(container, params)
    }

    private fun observeChats() {
        val liveManager = MidStreamzApp.instance.liveManager
        val tikTokLiveManager = MidStreamzApp.instance.tikTokLiveManager
        scope.launch {
            liveManager.chatMessages.collect { messages ->
                val latest = messages.firstOrNull() ?: return@collect
                updateOverlayContent(latest)
                dispatchAnimatedOverlay(latest)
            }
        }
        scope.launch {
            tikTokLiveManager.chatMessages.collect { messages ->
                val latest = messages.firstOrNull() ?: return@collect
                updateOverlayContent(latest)
                dispatchAnimatedOverlay(latest)
            }
        }
    }

    private fun dispatchAnimatedOverlay(chat: LiveChatMessage) {
        when (chat.type) {
            EventType.SUBSCRIBER -> showAnimatedOverlay("follow", chat)
            EventType.CHAT -> showAnimatedOverlay("comment", chat)
            EventType.SUPER_CHAT -> showAnimatedOverlay("gift", chat)
            EventType.LIKE -> showAnimatedOverlay("like", chat)
            EventType.MEMBERSHIP -> {
                if (chat.isTikTok) {
                    showAnimatedOverlay("follow", chat)
                } else {
                    showAnimatedOverlay("gift", chat)
                }
            }
            else -> {}
        }
    }

    fun showAnimatedOverlay(type: String, chat: LiveChatMessage) {
        if (!Settings.canDrawOverlays(this)) return

        val overlayConfigManager = MidStreamzApp.instance.overlayConfigManager
        val config = overlayConfigManager.config
        if (config.masterOverlayDisabled) return

        // Check landscape-only setting
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (config.landscapeOnly && !isLandscape) {
            return
        }

        if (type == "like") {
            if (config.likeOverlayEnabled) {
                showLikeReactionOverlay(chat)
            }
            return
        }

        val itemConfig = when (type) {
            "follow" -> if (config.followConfig.isEnabled) config.followConfig else null
            "comment" -> if (config.commentConfig.isEnabled) config.commentConfig else null
            "gift" -> if (config.giftConfig.isEnabled) config.giftConfig else null
            "song" -> if (config.songRequestConfig.isEnabled) config.songRequestConfig else null
            else -> null
        } ?: return

        val targetX = if (isLandscape) itemConfig.landscapeX else itemConfig.portraitX
        val targetY = if (isLandscape) itemConfig.landscapeY else itemConfig.portraitY

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = if (itemConfig.isLocked) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX
            y = targetY
        }

        val density = resources.displayMetrics.density

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bgDrawable = GradientDrawable().apply {
                val bgCol = try { Color.parseColor(itemConfig.bgColorHex) } catch (_: Exception) { Color.parseColor("#0F172A") }
                val alpha = (itemConfig.bgOpacity.coerceIn(0f, 1f) * 255).toInt()
                setColor(Color.argb(alpha, Color.red(bgCol), Color.green(bgCol), Color.blue(bgCol)))

                when (itemConfig.bgStyle) {
                    OverlayBgStyle.ROUNDED -> cornerRadius = 16f * density
                    OverlayBgStyle.PILL -> cornerRadius = 999f
                    OverlayBgStyle.SHARP -> cornerRadius = 4f * density
                    OverlayBgStyle.GLASS -> {
                        cornerRadius = 16f * density
                        setStroke((1.5f * density).toInt(), Color.argb(120, 255, 255, 255))
                    }
                }
            }
            background = bgDrawable
            val padH = (16 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
            elevation = 12f
            scaleX = itemConfig.scale
            scaleY = itemConfig.scale
            pivotX = 0f
            pivotY = 0f
        }

        // Icon Badge based on type
        val iconBadge = TextView(this).apply {
            text = when (type) {
                "follow" -> "💖 "
                "gift" -> "🎁 "
                "song" -> "🎵 "
                else -> "💬 "
            }
            textSize = 14f
        }
        card.addView(iconBadge)

        // Text formatting
        val formatted = overlayConfigManager.formatText(itemConfig.template, chat)
        val authorColor = overlayConfigManager.getAuthorColor(itemConfig, chat)

        val spannable = android.text.SpannableString(formatted)
        val nameIndex = formatted.indexOf(chat.authorName)
        if (nameIndex != -1) {
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(authorColor),
                nameIndex,
                nameIndex + chat.authorName.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val contentText = TextView(this).apply {
            val textColor = try { Color.parseColor(itemConfig.textColorHex) } catch (_: Exception) { Color.WHITE }
            setTextColor(textColor)
            text = spannable
            textSize = 13f

            when (itemConfig.textStyle) {
                OverlayTextStyle.NORMAL -> setTypeface(null, android.graphics.Typeface.NORMAL)
                OverlayTextStyle.BOLD -> setTypeface(null, android.graphics.Typeface.BOLD)
                OverlayTextStyle.ITALIC -> setTypeface(null, android.graphics.Typeface.ITALIC)
                OverlayTextStyle.BOLD_ITALIC -> setTypeface(null, android.graphics.Typeface.BOLD_ITALIC)
            }

            if (itemConfig.outlineEnabled) {
                val outlineColor = try { Color.parseColor(itemConfig.outlineColorHex) } catch (_: Exception) { Color.BLACK }
                val radius = itemConfig.outlineWidthDp * density * 1.5f
                setShadowLayer(radius, 0f, 0f, outlineColor)
            }
        }
        card.addView(contentText)

        // Drag Listener if not locked
        if (!itemConfig.isLocked) {
            var dragStartX = 0
            var dragStartY = 0
            var touchDownX = 0f
            var touchDownY = 0f
            card.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = params.x
                        dragStartY = params.y
                        touchDownX = event.rawX
                        touchDownY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = dragStartX + (event.rawX - touchDownX).toInt()
                        params.y = dragStartY + (event.rawY - touchDownY).toInt()
                        try { windowManager.updateViewLayout(card, params) } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        overlayConfigManager.updatePosition(type, params.x, params.y, isLandscape)
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            windowManager.addView(card, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view for $type", e)
            return
        }

        // Apply Entrance Animation
        when (itemConfig.animIn) {
            OverlayAnimIn.FADE_IN -> {
                card.alpha = 0f
                card.animate().alpha(1f).setDuration(350).start()
            }
            OverlayAnimIn.SLIDE_RIGHT -> {
                card.translationX = 350f * density
                card.alpha = 0f
                card.animate().translationX(0f).alpha(1f).setDuration(350).start()
            }
            OverlayAnimIn.SLIDE_LEFT -> {
                card.translationX = -350f * density
                card.alpha = 0f
                card.animate().translationX(0f).alpha(1f).setDuration(350).start()
            }
            OverlayAnimIn.SLIDE_UP -> {
                card.translationY = 120f * density
                card.alpha = 0f
                card.animate().translationY(0f).alpha(1f).setDuration(350).start()
            }
            OverlayAnimIn.SLIDE_DOWN -> {
                card.translationY = -120f * density
                card.alpha = 0f
                card.animate().translationY(0f).alpha(1f).setDuration(350).start()
            }
        }

        // Auto Dismiss with Exit Animation (Anti-Flicker)
        card.postDelayed({
            try {
                val animExit = card.animate().setDuration(320)
                when (itemConfig.animOut) {
                    OverlayAnimOut.FADE_OUT -> {
                        animExit.alpha(0f)
                    }
                    OverlayAnimOut.SLIDE_RIGHT -> {
                        animExit.translationX(350f * density).alpha(0f)
                    }
                    OverlayAnimOut.SLIDE_LEFT -> {
                        animExit.translationX(-350f * density).alpha(0f)
                    }
                    OverlayAnimOut.SLIDE_UP -> {
                        animExit.translationY(-120f * density).alpha(0f)
                    }
                    OverlayAnimOut.SLIDE_DOWN -> {
                        animExit.translationY(120f * density).alpha(0f)
                    }
                }
                animExit.withEndAction {
                    try {
                        card.animate().setListener(null)
                        card.visibility = View.INVISIBLE
                        card.alpha = 0f
                        windowManager.removeView(card)
                    } catch (_: Exception) {}
                }.start()
            } catch (_: Exception) {
                try {
                    card.visibility = View.INVISIBLE
                    windowManager.removeView(card)
                } catch (_: Exception) {}
            }
        }, 4500L)
    }

    private fun showLikeReactionOverlay(chat: LiveChatMessage) {
        if (!Settings.canDrawOverlays(this)) return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val overlayConfigManager = MidStreamzApp.instance.overlayConfigManager
        if (overlayConfigManager.config.landscapeOnly && !isLandscape) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (30 * density).toInt()
            y = (180 * density).toInt()
        }

        val likeBubble = TextView(this).apply {
            text = "❤️ ${chat.authorName} menyukai live"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E11D48"))
                cornerRadius = 999f
            }
            background = bg
            val padH = (14 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            elevation = 10f
            translationY = 40f
            alpha = 0f
        }

        try {
            windowManager.addView(likeBubble, params)
            likeBubble.animate().translationY(0f).alpha(1f).setDuration(250).start()
            likeBubble.postDelayed({
                likeBubble.animate().translationY(-80f).alpha(0f).setDuration(400).withEndAction {
                    try { windowManager.removeView(likeBubble) } catch (_: Exception) {}
                }.start()
            }, 2200L)
        } catch (_: Exception) {}
    }

    private fun observeConnectionStatus() {
        val liveManager = MidStreamzApp.instance.liveManager
        val tikTokLiveManager = MidStreamzApp.instance.tikTokLiveManager
        scope.launch {
            liveManager.connectionStatus.collect { status ->
                updateNotification("MidStream (YT): $status", "MidStream sedang berjalan di latar belakang")
            }
        }
        scope.launch {
            tikTokLiveManager.connectionStatus.collect { status ->
                updateNotification("MidStream (TikTok): $status", "MidStream sedang berjalan di latar belakang")
            }
        }
    }

    private fun observeSongStatus() {
        val songManager = MidStreamzApp.instance.songRequestManager
        var lastPlayedSongId: String? = null
        scope.launch {
            songManager.currentSong.collect { song ->
                val container = overlayView as? LinearLayout
                val songTicker = container?.getChildAt(1) as? TextView
                if (song != null) {
                    songTicker?.text = "🎵 ${song.title}"
                    songTicker?.visibility = View.VISIBLE
                    if (song.id != lastPlayedSongId) {
                        lastPlayedSongId = song.id
                        showAnimatedOverlay("song", LiveChatMessage(
                            id = "song_${song.id}_${System.currentTimeMillis()}",
                            type = EventType.CHAT,
                            authorName = song.requester.ifBlank { "Penonton" },
                            message = song.title
                        ))
                    }
                } else {
                    songTicker?.visibility = View.GONE
                }
            }
        }
    }

    private fun updateOverlayContent(chat: LiveChatMessage) {
        val container = overlayView as? LinearLayout ?: return
        val authorText = container.getChildAt(2) as? TextView
        val messageText = container.getChildAt(3) as? TextView

        if (chat.type == EventType.SUPER_CHAT) {
            authorText?.text = "💰 ${chat.authorName} (${chat.amountFormatted})"
            authorText?.setTextColor(Color.parseColor("#F59E0B"))
        } else if (chat.type == EventType.MEMBERSHIP) {
            authorText?.text = "⭐ Member: ${chat.authorName}"
            authorText?.setTextColor(Color.parseColor("#10B981"))
        } else {
            authorText?.text = chat.authorName
            authorText?.setTextColor(Color.parseColor("#60A5FA"))
        }

        messageText?.text = chat.message
    }

    override fun onDestroy() {
        isServiceRunning = false
        scope.cancel()
        hideFloatingHud()

        if (audioHolderView != null) {
            try {
                windowManager.removeView(audioHolderView)
            } catch (_: Exception) {}
            audioHolderView = null
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}

        super.onDestroy()
    }
}
