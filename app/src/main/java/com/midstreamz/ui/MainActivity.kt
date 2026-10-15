package com.midstreamz.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midstreamz.MidStreamzApp
import com.midstreamz.audio.AlertEventSound
import com.midstreamz.audio.AlertOutputMode
import com.midstreamz.audio.AlertSoundType
import com.midstreamz.audio.MyInstantsScraper
import com.midstreamz.audio.MyInstantsSound
import com.midstreamz.manager.LinkKeyRelayManager
import com.midstreamz.manager.SongItem
import com.midstreamz.manager.SongRequestManager
import com.midstreamz.manager.AppLanguage
import com.midstreamz.manager.AppLanguageManager
import com.midstreamz.manager.LocalizedStrings
import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import com.midstreamz.model.OverlayAnimIn
import com.midstreamz.model.OverlayAnimOut
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.midstreamz.model.OverlayAnimType
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.midstreamz.model.OverlayBgStyle
import com.midstreamz.model.OverlayItemConfig
import com.midstreamz.model.OverlayTextStyle
import com.midstreamz.model.FullOverlayConfig
import com.midstreamz.service.LiveOverlayService
import com.midstreamz.tts.TtsFormatTemplate
import com.midstreamz.tts.TtsLanguage
import com.midstreamz.tts.VoiceProfileConfig
import com.midstreamz.tts.WordReplacement
import com.midstreamz.ui.theme.*

enum class AppScreen {
    PLATFORM_SELECTOR,
    YOUTUBE_TOOLS,
    TIKTOK_TOOLS
}

enum class YouTubeTab {
    TTS_SETTINGS,
    VOICE_PROFILES,
    WORD_REPLACEMENTS,
    ALERT_AUDIO,
    FLOATING_HUD,
    LIVE_FEED,
    LINKKEY_OVERLAY,
    SONG_REQUEST
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock High Refresh Rate (120Hz FSync) to eliminate MIUI/HyperOS frame pacing jitter
        setupHighRefreshRateFSync()

        // Request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        if (intent?.getBooleanExtra("TRIGGER_UPDATE_TEST", false) == true) {
            MidStreamzApp.instance.appUpdateManager.triggerTestUpdate()
        }

        setContent {
            MidStreamzTheme {
                MainAppContainer(
                    onToggleOverlay = { enable -> handleToggleOverlay(enable) },
                    onOpenYouTubeApp = { openYouTubeApp() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra("TRIGGER_UPDATE_TEST", false) == true) {
            MidStreamzApp.instance.appUpdateManager.triggerTestUpdate()
        }
    }

    override fun onResume() {
        super.onResume()
        MidStreamzApp.instance.appUpdateManager.checkPendingInstallOnResume()
    }

    private fun setupHighRefreshRateFSync() {
        try {
            window.setFormat(PixelFormat.RGBA_8888)
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val currentDisplay = this.display
                val supportedModes = currentDisplay?.supportedModes
                // Find highest available refresh rate (e.g. 120Hz on Xiaomi 12 Pro)
                val maxRefreshMode = supportedModes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null && maxRefreshMode.refreshRate >= 90f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxRefreshMode.modeId
                    window.attributes = lp
                    Log.d("MainActivity", "FSync locked to ${maxRefreshMode.refreshRate}Hz (Mode ID: ${maxRefreshMode.modeId})")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val lp = window.attributes
                lp.preferredRefreshRate = 120f
                window.attributes = lp
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to configure FSync high refresh rate", e)
        }
    }

    private fun handleToggleOverlay(enable: Boolean) {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain'", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return
            }
            val intent = Intent(this, LiveOverlayService::class.java).apply {
                action = LiveOverlayService.ACTION_SHOW_HUD
            }
            ContextCompat.startForegroundService(this, intent)
        } else {
            val intent = Intent(this, LiveOverlayService::class.java).apply {
                action = LiveOverlayService.ACTION_HIDE_HUD
            }
            startService(intent)
        }
    }

    private fun openYouTubeApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (intent != null) {
                startActivity(intent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com")))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Aplikasi YouTube tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }
}

val LocalAppStrings = staticCompositionLocalOf { LocalizedStrings() }

@Composable
fun MainAppContainer(
    onToggleOverlay: (Boolean) -> Unit,
    onOpenYouTubeApp: () -> Unit
) {
    val appLanguageManager = MidStreamzApp.instance.appLanguageManager
    val locStrings by appLanguageManager.strings.collectAsState()

    CompositionLocalProvider(LocalAppStrings provides locStrings) {
        var currentScreen by remember { mutableStateOf(AppScreen.PLATFORM_SELECTOR) }
        val songRequestManager = MidStreamzApp.instance.songRequestManager

        // Intercept system Back navigation when inside YouTube live window or child screen
        BackHandler(enabled = currentScreen != AppScreen.PLATFORM_SELECTOR) {
            currentScreen = AppScreen.PLATFORM_SELECTOR
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = BgDark
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Persistent Audio Player WebView Host (Guarantees audioWebView is attached to Window for audio playback)
                AndroidView(
                    factory = { ctx ->
                        android.widget.FrameLayout(ctx).apply {
                            isClickable = false
                            isFocusable = false
                            val wv = songRequestManager.getOrCreateAudioWebView(ctx)
                            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                            addView(wv, android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                    },
                    update = { container ->
                        val wv = songRequestManager.getOrCreateAudioWebView(container.context)
                        if (wv.parent != container) {
                            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                            container.removeAllViews()
                            container.addView(wv, android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                    },
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(0.01f)
                )

                when (currentScreen) {
                    AppScreen.PLATFORM_SELECTOR -> {
                        PlatformSelectorScreen(
                            onSelectYouTube = { currentScreen = AppScreen.YOUTUBE_TOOLS },
                            onSelectTikTok = { currentScreen = AppScreen.TIKTOK_TOOLS }
                        )
                    }
                    AppScreen.YOUTUBE_TOOLS -> {
                        YouTubeToolsScreen(
                            onBack = { currentScreen = AppScreen.PLATFORM_SELECTOR },
                            onToggleOverlay = onToggleOverlay,
                            onOpenYouTubeApp = onOpenYouTubeApp
                        )
                    }
                    AppScreen.TIKTOK_TOOLS -> {
                        TikTokToolsScreen(
                            onBack = { currentScreen = AppScreen.PLATFORM_SELECTOR },
                            onToggleOverlay = onToggleOverlay
                        )
                    }
                }

                // Window Update Available
                val appUpdateManager = MidStreamzApp.instance.appUpdateManager
                val availableUpdate by appUpdateManager.availableUpdate.collectAsState()

                LaunchedEffect(Unit) {
                    appUpdateManager.checkForUpdates(force = false)
                }

                if (availableUpdate != null) {
                    UpdateAvailableDialog(
                        updateInfo = availableUpdate!!,
                        appUpdateManager = appUpdateManager,
                        onDismiss = {
                            appUpdateManager.dismissDialog()
                        }
                    )
                }
            }
        }
    }
}


// ==========================================
// 1. SCREEN PILIHAN PLATFORM
// ==========================================
@Composable
fun PlatformSelectorScreen(
    onSelectYouTube: () -> Unit,
    onSelectTikTok: () -> Unit
) {
    val appLanguageManager = MidStreamzApp.instance.appLanguageManager
    val currentLang by appLanguageManager.currentLanguage.collectAsState()
    val locStrings by appLanguageManager.strings.collectAsState()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Brand Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(PrimaryBlue, CircleShape)
                    )
                    Text(
                        text = "MIDSTREAMZ",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "${locStrings.appTitle}\n${locStrings.appSubtitle}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                )
            }

        // Platform Cards Column
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. YouTube Live Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectYouTube() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = locStrings.youtubeCardTitle,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = CircleShape,
                                color = EmeraldSuccess.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = locStrings.activeBadge,
                                    color = EmeraldSuccess,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = locStrings.youtubeCardDesc,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Button(
                        onClick = onSelectYouTube,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(locStrings.openButton, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            // 2. TikTok Live Card (Active)
            Surface(
                onClick = onSelectTikTok,
                shape = RoundedCornerShape(24.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = locStrings.tiktokCardTitle,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = CircleShape,
                                color = EmeraldSuccess.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = locStrings.activeBadge,
                                    color = EmeraldSuccess,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = locStrings.tiktokCardDesc,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Button(
                        onClick = onSelectTikTok,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(locStrings.openButton, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            // 3. Platform Lainnya (Coming Soon)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = locStrings.otherPlatformsTitle,
                                color = TextMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = CircleShape,
                                color = SurfaceLightDark,
                                border = BorderStroke(1.dp, BorderDark)
                            ) {
                                Text(
                                    text = locStrings.comingSoon,
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = locStrings.otherPlatformsDesc,
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Top Bar Actions di Pojok Kanan Atas (Tombol Cek Update & Pengaturan Bahasa)
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 16.dp, end = 20.dp)
            .zIndex(25f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val appUpdateManager = MidStreamzApp.instance.appUpdateManager
        Surface(
            onClick = {
                Toast.makeText(context, "Memeriksa pembaruan...", Toast.LENGTH_SHORT).show()
                appUpdateManager.checkForUpdates(force = true) { update ->
                    if (update == null) {
                        // Jika belum ada rilis resmi di GitHub, tampilkan simulasi pembaruan untuk pengujian langsung
                        appUpdateManager.triggerTestUpdate()
                    }
                }
            },
            shape = CircleShape,
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Cek Update",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Update",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        AppLanguageButton(
            onClick = { showLanguageDialog = true }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(onDismiss = { showLanguageDialog = false })
    }
}
}

@Composable
fun AppLanguageButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val currentLang by MidStreamzApp.instance.appLanguageManager.currentLanguage.collectAsState()
    val locStrings = LocalAppStrings.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderDark),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = locStrings.languageSettings,
                tint = PrimaryBlue,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = currentLang.flagEmoji,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit
) {
    val appLanguageManager = MidStreamzApp.instance.appLanguageManager
    val currentLang by appLanguageManager.currentLanguage.collectAsState()
    val locStrings = LocalAppStrings.current
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            AppLanguageManager.ALL_LANGUAGES
        } else {
            val q = searchQuery.trim().lowercase()
            AppLanguageManager.ALL_LANGUAGES.filter {
                it.name.lowercase().contains(q) ||
                it.code.lowercase().contains(q) ||
                it.shortCode.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = SurfaceDark,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = locStrings.languageSettings,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = locStrings.supportedLanguagesSub,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(locStrings.searchLanguage, color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceLightDark,
                        unfocusedContainerColor = SurfaceLightDark
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLanguages, key = { it.code }) { lang ->
                        val isSelected = lang.code.equals(currentLang.code, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryBlue else BorderDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appLanguageManager.setLanguage(lang)
                                    MidStreamzApp.instance.ttsManager.syncLanguageWithApp(lang.code)
                                    Toast.makeText(context, "${locStrings.toastLanguageChanged}: ${lang.name}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = lang.flagEmoji,
                                        fontSize = 22.sp
                                    )
                                    Column {
                                        Text(
                                            text = lang.name,
                                            color = if (isSelected) TextPrimary else TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = "[${lang.shortCode}] • ${lang.code}",
                                            color = if (isSelected) PrimaryBlue else TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = CircleShape
            ) {
                Text(locStrings.close, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }
        }
    )
}

/**
 * Petunjuk Geser Horizontal dengan Teks "Geser" ber-outline tebal dan 3 Panah Beranimasi Gelombang (Running Wave ⨠⨠⨠).
 * Setiap panah memiliki stroke/outline hitam pekat agar sangat jelas dan mencolok, tidak menyatu dengan text navbar.
 */
@Composable
fun AnimatedScrollHint(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val locStrings by MidStreamzApp.instance.appLanguageManager.strings.collectAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "scrollHintWave")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hintProgress"
    )

    Row(
        modifier = modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Teks Petunjuk Geser dengan Outline / Stroke Hitam Pekat
        Box(
            modifier = Modifier.padding(end = 3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = locStrings.swipeHint,
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                style = LocalTextStyle.current.copy(
                    drawStyle = Stroke(
                        miter = 10f,
                        width = 3.5f,
                        join = StrokeJoin.Round
                    )
                )
            )
            Text(
                text = locStrings.swipeHint,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        // 3 Panah Animasi Berjalan (Running Wave Cascade ⨠⨠⨠)
        val density = LocalDensity.current
        val maxShiftPx = with(density) { 3.dp.toPx() }

        for (i in 0 until 3) {
            val shifted = (progress - (i * 0.20f)).let { if (it < 0f) it + 1f else it }
            val wave = if (shifted in 0f..0.45f) {
                kotlin.math.sin((shifted / 0.45f) * Math.PI).toFloat()
            } else {
                0f
            }
            val arrowAlpha = 0.35f + (0.65f * wave)
            val translationXPx = wave * maxShiftPx

            Box(
                modifier = Modifier
                    .graphicsLayer { this.translationX = translationXPx },
                contentAlignment = Alignment.Center
            ) {
                // Outline Hitam Tebal Panah
                Icon(
                    imageVector = Icons.Filled.KeyboardDoubleArrowRight,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.95f),
                    modifier = Modifier.size(16.dp)
                )
                // Isi Panah Menyala (Glow Putih)
                Icon(
                    imageVector = Icons.Filled.KeyboardDoubleArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = arrowAlpha.coerceIn(0.35f, 1f)),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Indikator Posisi Geser Horizontal (Horizontal Scroll Indicator / Scrollbar Track & Thumb).
 * Dibuat lebih panjang (180.dp) dan menggunakan graphicsLayer translationX agar 100% mulus (Zero Recomposition).
 */
@Composable
fun HorizontalScrollIndicator(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    trackWidth: androidx.compose.ui.unit.Dp = 180.dp,
    indicatorWidth: androidx.compose.ui.unit.Dp = 56.dp,
    height: androidx.compose.ui.unit.Dp = 3.dp,
    trackColor: Color = BorderDark,
    indicatorColor: Color = PrimaryBlue
) {
    val density = LocalDensity.current
    val maxOffsetDp = (trackWidth - indicatorWidth).coerceAtLeast(0.dp)
    val maxOffsetPx = with(density) { maxOffsetDp.toPx() }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Track (Garis lintasan latar belakang yang panjang dan jelas)
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(height)
                .background(trackColor, CircleShape)
        ) {
            // Thumb (Garis geser aktif yang bergerak mengikuti posisi scroll tanpa memicu recomposition)
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(height)
                    .graphicsLayer {
                        val maxScroll = scrollState.maxValue.toFloat()
                        val currentScroll = scrollState.value.toFloat()
                        val scrollFraction = if (maxScroll > 0f) {
                            (currentScroll / maxScroll).coerceIn(0f, 1f)
                        } else 0f
                        translationX = scrollFraction * maxOffsetPx
                    }
                    .background(indicatorColor, CircleShape)
            )
        }
    }
}

// ==========================================
// 2. SCREEN UTAMA YOUTUBE TOOLS
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeToolsScreen(
    onBack: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onOpenYouTubeApp: () -> Unit
) {
    val liveManager = MidStreamzApp.instance.liveManager
    val ttsManager = MidStreamzApp.instance.ttsManager
    val alertSoundManager = MidStreamzApp.instance.alertSoundManager
    val linkKeyRelayManager = MidStreamzApp.instance.linkKeyRelayManager
    val songRequestManager = MidStreamzApp.instance.songRequestManager
    val appLanguageManager = MidStreamzApp.instance.appLanguageManager
    val locStrings by appLanguageManager.strings.collectAsState()
    val context = LocalContext.current

    val isConnected by liveManager.isConnected.collectAsState()
    val isSimulating by liveManager.isSimulating.collectAsState()

    var streamInput by remember { mutableStateOf("") }
    val tabs = remember(locStrings) {
        listOf(
            YouTubeTab.TTS_SETTINGS to locStrings.tabTtsGlobal,
            YouTubeTab.VOICE_PROFILES to locStrings.tabVoiceProfiles,
            YouTubeTab.WORD_REPLACEMENTS to locStrings.tabWordReplacements,
            YouTubeTab.ALERT_AUDIO to locStrings.tabAlertAudio,
            YouTubeTab.FLOATING_HUD to locStrings.tabFloatingHud,
            YouTubeTab.LINKKEY_OVERLAY to locStrings.tabLinkKeyOverlay,
            YouTubeTab.SONG_REQUEST to locStrings.tabSongRequest,
            YouTubeTab.LIVE_FEED to locStrings.tabLiveFeed
        )
    }
    var currentTab by rememberSaveable { mutableStateOf(YouTubeTab.TTS_SETTINGS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("←", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(locStrings.menuBack, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column {
                    Text(
                        text = locStrings.youtubeLiveTitle,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = locStrings.youtubeLiveSubtitle,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            // Status Indicator Pill
            Surface(
                    shape = CircleShape,
                    color = when {
                        isConnected -> EmeraldSuccess.copy(alpha = 0.15f)
                        isSimulating -> AmberSuperChat.copy(alpha = 0.15f)
                        else -> SurfaceLightDark
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isConnected -> EmeraldSuccess.copy(alpha = 0.5f)
                            isSimulating -> AmberSuperChat.copy(alpha = 0.5f)
                            else -> BorderDark
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    when {
                                        isConnected -> EmeraldSuccess
                                        isSimulating -> AmberSuperChat
                                        else -> TextMuted
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            text = when {
                                isSimulating -> locStrings.simulationStatus
                                isConnected -> locStrings.onlineStatus
                                else -> locStrings.offlineStatus
                            },
                            color = when {
                                isConnected -> EmeraldSuccess
                                isSimulating -> AmberSuperChat
                                else -> TextMuted
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        // Stream Connection Box
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = locStrings.targetAccount,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Video ID • @Handle • URL",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = streamInput,
                        onValueChange = { streamInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(locStrings.youtubeChannelHint, color = TextMuted, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceLightDark,
                            unfocusedContainerColor = SurfaceLightDark,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Button(
                        onClick = {
                            if (isConnected) {
                                liveManager.disconnect()
                                val stopIntent = Intent(context, LiveOverlayService::class.java).apply {
                                    action = LiveOverlayService.ACTION_STOP
                                }
                                context.startService(stopIntent)
                            } else {
                                liveManager.connect(streamInput)
                                val startIntent = Intent(context, LiveOverlayService::class.java).apply {
                                    action = LiveOverlayService.ACTION_START
                                }
                                ContextCompat.startForegroundService(context, startIntent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) RoseError else PrimaryBlue
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(46.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (isConnected) locStrings.stopMonitoring else locStrings.startMonitoring,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Segmented Pill Tabs with Highlight
        val tabScrollState = rememberScrollState()
        val prefs = remember { context.getSharedPreferences("midstreamz_ui_prefs", Context.MODE_PRIVATE) }
        var hasScrolledTabs by remember { mutableStateOf(prefs.getBoolean("has_scrolled_tabs", false)) }

        LaunchedEffect(Unit) {
            if (!hasScrolledTabs) {
                snapshotFlow { tabScrollState.value }
                    .collect { scrollVal ->
                        if (scrollVal > 20) {
                            hasScrolledTabs = true
                            withContext(Dispatchers.IO) {
                                prefs.edit().putBoolean("has_scrolled_tabs", true).apply()
                            }
                        }
                    }
            }
        }

        // Horizontal Segmented Pill Tabs with Firefly Hint Overlay (Tinggi pas 38.dp, tidak memotong tombol)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(tabScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (tab, label) ->
                    YouTubeTabChip(label, currentTab == tab) {
                        currentTab = tab
                    }
                }
            }

            // Highlight memudar panjang ke kiri, sudut ngotak, tanpa box latar belakang di panahnya
            androidx.compose.animation.AnimatedVisibility(
                visible = !hasScrolledTabs,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(500)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.65f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    PrimaryBlue.copy(alpha = 0.15f),
                                    PrimaryBlue.copy(alpha = 0.50f),
                                    PrimaryBlue.copy(alpha = 0.88f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AnimatedScrollHint(accentColor = PrimaryBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Scroll Indicator (Garis geser kiri-kanan yang ikut bergerak saat navbar di-scroll)
        HorizontalScrollIndicator(
            scrollState = tabScrollState,
            indicatorColor = PrimaryBlue
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main Tab Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (currentTab) {
                YouTubeTab.TTS_SETTINGS -> TtsGlobalSettingsPanel(ttsManager)
                YouTubeTab.VOICE_PROFILES -> VoiceProfilesPanel(ttsManager)
                YouTubeTab.WORD_REPLACEMENTS -> WordReplacementsPanel(ttsManager)
                YouTubeTab.ALERT_AUDIO -> AlertAudioSettingsPanel(alertSoundManager, isTikTok = false)
                YouTubeTab.FLOATING_HUD -> FloatingOverlayPanel(onToggleOverlay)
                YouTubeTab.LINKKEY_OVERLAY -> LinkKeyOverlayPanel(linkKeyRelayManager)
                YouTubeTab.SONG_REQUEST -> SongRequestPanel(songRequestManager)
                YouTubeTab.LIVE_FEED -> LiveFeedPanel(liveManager)
            }
        }
    }
}

// ==========================================
// 3. SCREEN UTAMA TIKTOK TOOLS
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TikTokToolsScreen(
    onBack: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit
) {
    val tikTokLiveManager = MidStreamzApp.instance.tikTokLiveManager
    val ttsManager = MidStreamzApp.instance.ttsManager
    val alertSoundManager = MidStreamzApp.instance.alertSoundManager
    val linkKeyRelayManager = MidStreamzApp.instance.linkKeyRelayManager
    val songRequestManager = MidStreamzApp.instance.songRequestManager
    val appLanguageManager = MidStreamzApp.instance.appLanguageManager
    val locStrings by appLanguageManager.strings.collectAsState()
    val context = LocalContext.current

    val isConnected by tikTokLiveManager.isConnected.collectAsState()
    val isSimulating by tikTokLiveManager.isSimulating.collectAsState()

    var streamInput by remember { mutableStateOf("") }
    val tabs = remember(locStrings) {
        listOf(
            YouTubeTab.TTS_SETTINGS to locStrings.tabTtsGlobal,
            YouTubeTab.VOICE_PROFILES to locStrings.tabVoiceProfiles,
            YouTubeTab.WORD_REPLACEMENTS to locStrings.tabWordReplacements,
            YouTubeTab.ALERT_AUDIO to locStrings.tabAlertAudio,
            YouTubeTab.FLOATING_HUD to locStrings.tabFloatingHud,
            YouTubeTab.LINKKEY_OVERLAY to locStrings.tabLinkKeyOverlay,
            YouTubeTab.SONG_REQUEST to locStrings.tabSongRequest,
            YouTubeTab.LIVE_FEED to locStrings.tabLiveFeed
        )
    }
    var currentTab by rememberSaveable { mutableStateOf(YouTubeTab.TTS_SETTINGS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("←", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(locStrings.menuBack, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column {
                    Text(
                        text = locStrings.tiktokLiveTitle,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = locStrings.tiktokLiveSubtitle,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            // Status Indicator Pill
            Surface(
                    shape = CircleShape,
                    color = when {
                        isConnected -> EmeraldSuccess.copy(alpha = 0.15f)
                        isSimulating -> AmberSuperChat.copy(alpha = 0.15f)
                        else -> SurfaceLightDark
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isConnected -> EmeraldSuccess.copy(alpha = 0.5f)
                            isSimulating -> AmberSuperChat.copy(alpha = 0.5f)
                            else -> BorderDark
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    when {
                                        isConnected -> EmeraldSuccess
                                        isSimulating -> AmberSuperChat
                                        else -> TextMuted
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            text = when {
                                isSimulating -> locStrings.simulationStatus
                                isConnected -> locStrings.onlineStatus
                                else -> locStrings.offlineStatus
                            },
                            color = when {
                                isConnected -> EmeraldSuccess
                                isSimulating -> AmberSuperChat
                                else -> TextMuted
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        // Stream Connection Box
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = locStrings.targetAccount,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "@username • tiktok.com/@user/live",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = streamInput,
                        onValueChange = { streamInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(locStrings.tiktokUserHint, color = TextMuted, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceLightDark,
                            unfocusedContainerColor = SurfaceLightDark,
                            focusedBorderColor = Color(0xFFFE2C55),
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Button(
                        onClick = {
                            if (isConnected) {
                                tikTokLiveManager.disconnect()
                                val stopIntent = Intent(context, LiveOverlayService::class.java).apply {
                                    action = LiveOverlayService.ACTION_STOP
                                }
                                context.startService(stopIntent)
                            } else {
                                tikTokLiveManager.connect(streamInput)
                                val startIntent = Intent(context, LiveOverlayService::class.java).apply {
                                    action = LiveOverlayService.ACTION_START
                                }
                                ContextCompat.startForegroundService(context, startIntent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) RoseError else Color(0xFFFE2C55)
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(46.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (isConnected) locStrings.stopMonitoring else locStrings.startMonitoring,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Segmented Pill Tabs with Highlight
        val tabScrollState = rememberScrollState()
        val prefs = remember { context.getSharedPreferences("midstreamz_ui_prefs", Context.MODE_PRIVATE) }
        var hasScrolledTabs by remember { mutableStateOf(prefs.getBoolean("has_scrolled_tabs", false)) }

        LaunchedEffect(Unit) {
            if (!hasScrolledTabs) {
                snapshotFlow { tabScrollState.value }
                    .collect { scrollVal ->
                        if (scrollVal > 20) {
                            hasScrolledTabs = true
                            withContext(Dispatchers.IO) {
                                prefs.edit().putBoolean("has_scrolled_tabs", true).apply()
                            }
                        }
                    }
            }
        }

        // Horizontal Segmented Pill Tabs with Firefly Hint Overlay (Tinggi pas 38.dp, tidak memotong tombol)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(tabScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (tab, label) ->
                    YouTubeTabChip(label, currentTab == tab) {
                        currentTab = tab
                    }
                }
            }

            // Highlight memudar panjang ke kiri, sudut ngotak, tanpa box latar belakang di panahnya
            androidx.compose.animation.AnimatedVisibility(
                visible = !hasScrolledTabs,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(500)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.65f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFFE2C55).copy(alpha = 0.15f),
                                    Color(0xFFFE2C55).copy(alpha = 0.50f),
                                    Color(0xFFFE2C55).copy(alpha = 0.88f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AnimatedScrollHint(accentColor = Color(0xFFFE2C55))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Scroll Indicator (Garis geser kiri-kanan yang ikut bergerak saat navbar di-scroll)
        HorizontalScrollIndicator(
            scrollState = tabScrollState,
            indicatorColor = Color(0xFFFE2C55)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main Tab Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (currentTab) {
                YouTubeTab.TTS_SETTINGS -> TtsGlobalSettingsPanel(ttsManager)
                YouTubeTab.VOICE_PROFILES -> VoiceProfilesPanel(ttsManager)
                YouTubeTab.WORD_REPLACEMENTS -> WordReplacementsPanel(ttsManager)
                YouTubeTab.ALERT_AUDIO -> AlertAudioSettingsPanel(alertSoundManager, isTikTok = true)
                YouTubeTab.FLOATING_HUD -> FloatingOverlayPanel(onToggleOverlay)
                YouTubeTab.LINKKEY_OVERLAY -> LinkKeyOverlayPanel(linkKeyRelayManager)
                YouTubeTab.SONG_REQUEST -> SongRequestPanel(songRequestManager)
                YouTubeTab.LIVE_FEED -> LiveFeedPanel(liveManager = tikTokLiveManager, isTikTok = true)
            }
        }
    }
}

@Composable
fun YouTubeTabChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

// ==========================================
// 3. TAB 1: TTS GLOBAL SETTINGS
// ==========================================
@Composable
fun TtsGlobalSettingsPanel(ttsManager: com.midstreamz.tts.TtsManager) {
    val locStrings = LocalAppStrings.current
    var isEnabled by remember { mutableStateOf(ttsManager.isEnabled) }
    var selectedLanguageCode by remember { mutableStateOf(ttsManager.selectedGlobalLanguageCode) }
    var selectedVoiceName by remember { mutableStateOf(ttsManager.selectedGlobalVoiceName) }
    var selectedTemplate by remember { mutableStateOf(ttsManager.formatTemplate) }
    var onlySuperChat by remember { mutableStateOf(ttsManager.onlySuperChat) }
    var readEmoji by remember { mutableStateOf(ttsManager.readEmoji) }
    var skipUrls by remember { mutableStateOf(ttsManager.skipUrls) }
    var dynamicSpeed by remember { mutableStateOf(ttsManager.dynamicSpeed) }
    var maxChars by remember { mutableIntStateOf(ttsManager.maxMessageLength) }
    var queueLimit by remember { mutableIntStateOf(ttsManager.queueThreshold) }
    var protectVip by remember { mutableStateOf(ttsManager.protectVipInQueue) }
    var ignoreCommands by remember { mutableStateOf(ttsManager.ignoreCommandPrefix) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }

    val currentLang = remember(selectedLanguageCode) {
        ttsManager.supportedLanguages.find { it.code == selectedLanguageCode }
            ?: ttsManager.supportedLanguages.first()
    }
    val voicesForLang = remember(selectedLanguageCode, ttsManager.allVoices) {
        ttsManager.getVoicesForLanguage(selectedLanguageCode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Master TTS Toggle Card
        SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(locStrings.ttsGlobalTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(locStrings.ttsGlobalDesc, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            ttsManager.isEnabled = it
                            ttsManager.saveGlobalPreferences()
                            if (!it) ttsManager.stopCurrentAndClear()
                        }
                    )
                }
            }

        // Multi-Language & OS Voice Selection Card
        SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = locStrings.ttsLangVoiceSection,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    // 1. Language Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsVoiceLang, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${currentLang.displayName} (${currentLang.code})", color = PrimaryBlue, fontSize = 11.sp)
                        }

                        Surface(
                            shape = CircleShape,
                            color = SurfaceLightDark,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.clickable { showLanguageDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = locStrings.ttsChangeLangBtn,
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)

                    // 2. OS Voice Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsVoiceChar, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            val voiceDisplay = if (selectedLanguageCode == "all") {
                                "All Language (Auto)"
                            } else {
                                selectedVoiceName ?: "Default Engine (${voicesForLang.size})"
                            }
                            Text(voiceDisplay, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                        }

                        Surface(
                            shape = CircleShape,
                            color = SurfaceLightDark,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.clickable { showVoiceDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = locStrings.ttsSelectVoiceBtn,
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Test Voice Button
                    Button(
                        onClick = {
                            ttsManager.playSample(
                                pitch = 1.0f,
                                speed = 1.05f,
                                langCode = selectedLanguageCode,
                                voiceName = selectedVoiceName
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(locStrings.ttsTestVoiceBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

        // Mode Filter Pembacaan Card
        SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(locStrings.ttsFiltersSection, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

                    // Hanya Obrolan Super
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsOnlySuperChat, color = TextPrimary, fontSize = 13.sp)
                            Text(locStrings.ttsOnlySuperChatDesc, color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = onlySuperChat,
                            onCheckedChange = {
                                onlySuperChat = it
                                ttsManager.onlySuperChat = it
                                ttsManager.saveGlobalPreferences()
                            }
                        )
                    }

                    HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)

                    // Baca Emoji
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsReadEmoji, color = TextPrimary, fontSize = 13.sp)
                            Text(locStrings.ttsReadEmojiDesc, color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = readEmoji,
                            onCheckedChange = {
                                readEmoji = it
                                ttsManager.readEmoji = it
                                ttsManager.saveGlobalPreferences()
                            }
                        )
                    }

                    HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)

                    // Lewati URL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsSkipUrls, color = TextPrimary, fontSize = 13.sp)
                            Text(locStrings.ttsSkipUrlsDesc, color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = skipUrls,
                            onCheckedChange = {
                                skipUrls = it
                                ttsManager.skipUrls = it
                                ttsManager.saveGlobalPreferences()
                            }
                        )
                    }

                    HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)

                    // Dinamis Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsDynamicSpeed, color = TextPrimary, fontSize = 13.sp)
                            Text(locStrings.ttsDynamicSpeedDesc, color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = dynamicSpeed,
                            onCheckedChange = {
                                dynamicSpeed = it
                                ttsManager.dynamicSpeed = it
                                ttsManager.saveGlobalPreferences()
                            }
                        )
                    }

                    HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)

                    // Kecilkan Musik saat TTS (Auto Ducking)
                    var duckAudio by remember { mutableStateOf(ttsManager.duckAudioOnTts) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Ducking (Music)", color = TextPrimary, fontSize = 13.sp)
                            Text("Ducks music volume when TTS is reading, restores when finished", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = duckAudio,
                            onCheckedChange = {
                                duckAudio = it
                                ttsManager.duckAudioOnTts = it
                                ttsManager.saveGlobalPreferences()
                                MidStreamzApp.instance.songRequestManager.toggleDuckOnTts(it)
                            }
                        )
                    }
                }
            }

        // Format Template Card
        SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(locStrings.ttsReadingFormatSection, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    TtsFormatTemplate.values().forEach { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTemplate = template
                                    ttsManager.formatTemplate = template
                                    ttsManager.saveGlobalPreferences()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedTemplate == template,
                                onClick = {
                                    selectedTemplate = template
                                    ttsManager.formatTemplate = template
                                    ttsManager.saveGlobalPreferences()
                                }
                            )
                            Text(template.displayName, color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

        // Delay Prevention & Anti-Spam Card
        SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(locStrings.ttsFiltersSection, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

                    // Max Chars Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(locStrings.ttsMaxChars, color = TextPrimary, fontSize = 13.sp)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SurfaceLightDark,
                            border = BorderStroke(1.dp, BorderDark)
                        ) {
                            Text(
                                text = "$maxChars chars",
                                color = PrimaryBlue,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Slider(
                        value = maxChars.toFloat(),
                        onValueChange = {
                            maxChars = it.toInt()
                            ttsManager.maxMessageLength = it.toInt()
                            ttsManager.saveGlobalPreferences()
                        },
                        valueRange = 30f..300f
                    )

                    // Queue Threshold Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(locStrings.ttsQueueLimit, color = TextPrimary, fontSize = 13.sp)
                            Text("Skip older chats when queue overflows", color = TextMuted, fontSize = 10.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SurfaceLightDark,
                            border = BorderStroke(1.dp, BorderDark)
                        ) {
                            Text(
                                text = "max $queueLimit",
                                color = PrimaryBlue,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Slider(
                        value = queueLimit.toFloat(),
                        onValueChange = {
                            queueLimit = it.toInt()
                            ttsManager.queueThreshold = it.toInt()
                            ttsManager.saveGlobalPreferences()
                        },
                        valueRange = 2f..15f
                    )

                    HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)

                    // Protect VIP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsProtectVip, color = TextPrimary, fontSize = 13.sp)
                            Text(locStrings.ttsProtectVipDesc, color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = protectVip,
                            onCheckedChange = {
                                protectVip = it
                                ttsManager.protectVipInQueue = it
                                ttsManager.saveGlobalPreferences()
                            }
                        )
                    }

                    HorizontalDivider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)

                    // Ignore Commands
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(locStrings.ttsIgnoreCommands, color = TextPrimary, fontSize = 13.sp)
                            Text(locStrings.ttsIgnoreCommandsDesc, color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = ignoreCommands,
                            onCheckedChange = {
                                ignoreCommands = it
                                ttsManager.ignoreCommandPrefix = it
                                ttsManager.saveGlobalPreferences()
                            }
                        )
                    }
                }
        }
    }

    // Modal: Language Selector
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(locStrings.ttsSelectLangDialogTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ttsManager.supportedLanguages, key = { it.code }) { lang ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (lang.code == selectedLanguageCode) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (lang.code == selectedLanguageCode) PrimaryBlue else BorderDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguageCode = lang.code
                                    ttsManager.setGlobalLanguage(lang.code)
                                    selectedVoiceName = ttsManager.selectedGlobalVoiceName
                                    showLanguageDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(lang.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(lang.code, color = TextMuted, fontSize = 10.sp)
                                }
                                if (lang.code == selectedLanguageCode) {
                                    Text("AKTIF", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("TUTUP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Modal: OS Voice Selector
    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Pilih Suara OS (${currentLang.displayName})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                if (selectedLanguageCode == "all") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PrimaryBlue.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedVoiceName = null
                                ttsManager.setGlobalVoice(null)
                                showVoiceDialog = false
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("All Language (Otomatis)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Suara sistem dan aksen otomatis menyesuaikan setiap bahasa yang terdeteksi dari pesan komentar.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (voicesForLang.isEmpty()) {
                    Text("Tidak ditemukan variasi suara terpisah untuk bahasa ini. Menggunakan suara default sistem.", color = TextMuted, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selectedVoiceName == null) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                                border = BorderStroke(1.dp, if (selectedVoiceName == null) PrimaryBlue else BorderDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedVoiceName = null
                                        ttsManager.setGlobalVoice(null)
                                        showVoiceDialog = false
                                    }
                            ) {
                                Text("Default Suara Sistem", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
                            }
                        }

                        items(voicesForLang, key = { it.name }) { voice ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (voice.name == selectedVoiceName) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                                border = BorderStroke(1.dp, if (voice.name == selectedVoiceName) PrimaryBlue else BorderDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedVoiceName = voice.name
                                        ttsManager.setGlobalVoice(voice.name)
                                        showVoiceDialog = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(voice.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Kualitas: ${if (voice.quality == 400) "Tinggi" else "Standar"} • Latensi: ${if (voice.latency == 400) "Cepat" else "Normal"}",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceDialog = false }) {
                    Text("TUTUP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

// ==========================================
// 4. TAB 2: PROFIL SUARA TERPISAH
// ==========================================
@Composable
fun VoiceProfilesPanel(ttsManager: com.midstreamz.tts.TtsManager) {
    val locStrings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProfileConfigCard(
            title = locStrings.voiceProfileAllUsers,
            subtitle = locStrings.voiceProfilesDesc,
            badgeColor = PrimaryBlue,
            config = ttsManager.normalProfile,
            ttsManager = ttsManager
        )

        ProfileConfigCard(
            title = locStrings.voiceProfileModerator,
            subtitle = locStrings.voiceProfilesDesc,
            badgeColor = EmeraldSuccess,
            config = ttsManager.moderatorProfile,
            ttsManager = ttsManager
        )

        ProfileConfigCard(
            title = "Owner / Streamer",
            subtitle = locStrings.voiceProfilesDesc,
            badgeColor = Color(0xFF8B5CF6),
            config = ttsManager.ownerProfile,
            ttsManager = ttsManager
        )

        ProfileConfigCard(
            title = locStrings.voiceProfileDonor,
            subtitle = locStrings.voiceProfilesDesc,
            badgeColor = AmberSuperChat,
            config = ttsManager.superChatProfile,
            ttsManager = ttsManager
        )
    }
}

@Composable
fun ProfileConfigCard(
    title: String,
    subtitle: String,
    badgeColor: Color,
    config: VoiceProfileConfig,
    ttsManager: com.midstreamz.tts.TtsManager
) {
    val locStrings = LocalAppStrings.current
    var pitch by remember { mutableFloatStateOf(config.pitch) }
    var speed by remember { mutableFloatStateOf(config.speed) }
    var volume by remember { mutableFloatStateOf(config.volume) }
    var langCode by remember { mutableStateOf(config.languageCode) }
    var voiceName by remember { mutableStateOf(config.voiceName) }

    var showLangPicker by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }

    val currentLang = remember(langCode) {
        ttsManager.supportedLanguages.find { it.code == langCode }
            ?: ttsManager.supportedLanguages.first()
    }
    val voicesForLang = remember(langCode, ttsManager.allVoices) {
        ttsManager.getVoicesForLanguage(langCode)
    }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "PROFILE",
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(subtitle, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }

                Button(
                    onClick = {
                        ttsManager.playSample(
                            pitch = pitch,
                            speed = speed,
                            volume = volume,
                            langCode = langCode,
                            voiceName = voiceName
                        )
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(locStrings.alertTestAudioBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Language & OS Voice Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showLangPicker = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                        Text(
                            text = currentLang.displayName,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showVoicePicker = true }
                ) {
                    val label = if (langCode == "all") {
                        "All Language (Auto)"
                    } else if (voiceName != null) {
                        voiceName!!.take(10) + "..."
                    } else {
                        "Default OS"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                        Text(
                            text = label,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            // Kecepatan Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(locStrings.ttsSpeechRate, color = TextSecondary, fontSize = 12.sp)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Text(
                        text = "${(speed * 10).toInt() / 10.0}x",
                        color = PrimaryBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    config.speed = it
                    ttsManager.saveProfiles()
                },
                valueRange = 0.5f..2.5f
            )

            // Nada (Pitch) Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(locStrings.ttsPitch, color = TextSecondary, fontSize = 12.sp)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Text(
                        text = "${(pitch * 10).toInt() / 10.0}x",
                        color = PrimaryBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Slider(
                value = pitch,
                onValueChange = {
                    pitch = it
                    config.pitch = it
                    ttsManager.saveProfiles()
                },
                valueRange = 0.5f..2.0f
            )

            // Volume Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(locStrings.ttsVolume, color = TextSecondary, fontSize = 12.sp)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        color = PrimaryBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    config.volume = it
                    ttsManager.saveProfiles()
                },
                valueRange = 0.1f..1.0f
            )
        }
    }

    if (showLangPicker) {
        AlertDialog(
            onDismissRequest = { showLangPicker = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Pilih Bahasa ($title)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ttsManager.supportedLanguages, key = { it.code }) { lang ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (lang.code == langCode) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (lang.code == langCode) PrimaryBlue else BorderDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    langCode = lang.code
                                    config.languageCode = lang.code
                                    voiceName = null
                                    config.voiceName = null
                                    ttsManager.saveProfiles()
                                    showLangPicker = false
                                }
                        ) {
                            Text(lang.displayName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangPicker = false }) {
                    Text("TUTUP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showVoicePicker) {
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Pilih Suara OS ($title)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                if (langCode == "all") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PrimaryBlue.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                voiceName = null
                                config.voiceName = null
                                ttsManager.saveProfiles()
                                showVoicePicker = false
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("All Language (Otomatis per Bahasa)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Suara profil ini otomatis menyesuaikan bahasa teks yang terdeteksi.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (voiceName == null) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                                border = BorderStroke(1.dp, if (voiceName == null) PrimaryBlue else BorderDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        voiceName = null
                                        config.voiceName = null
                                        ttsManager.saveProfiles()
                                        showVoicePicker = false
                                    }
                            ) {
                                Text("Default Suara Sistem", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
                            }
                        }
                        items(voicesForLang, key = { it.name }) { voice ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (voice.name == voiceName) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                                border = BorderStroke(1.dp, if (voice.name == voiceName) PrimaryBlue else BorderDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        voiceName = voice.name
                                        config.voiceName = voice.name
                                        ttsManager.saveProfiles()
                                        showVoicePicker = false
                                    }
                            ) {
                                Text(voice.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoicePicker = false }) {
                    Text("TUTUP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

// ==========================================
// 5. TAB 3: KAMUS PENGGANTIAN KATA TTS
// ==========================================
@Composable
fun WordReplacementsPanel(ttsManager: com.midstreamz.tts.TtsManager) {
    val locStrings = LocalAppStrings.current
    var isEnabled by remember { mutableStateOf(ttsManager.isWordReplacementEnabled) }
    var newTargetWord by remember { mutableStateOf("") }
    var newReplacementWord by remember { mutableStateOf("") }
    var rulesList by remember { mutableStateOf(ttsManager.wordReplacements.toList()) }
    var blacklistInput by remember { mutableStateOf("") }
    var blacklistWords by remember { mutableStateOf(ttsManager.filterBlacklistWords.toList()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Master Toggle Card
        item {
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(locStrings.wordsTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(locStrings.wordsDesc, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            ttsManager.isWordReplacementEnabled = it
                        }
                    )
                }
            }
        }

        // Combined Rule Form & List Card
        item {
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(locStrings.wordsAddBtn, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTargetWord,
                            onValueChange = { newTargetWord = it },
                            placeholder = { Text("${locStrings.wordsOriginal} (e.g. lol)", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceLightDark,
                                unfocusedContainerColor = SurfaceLightDark,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = newReplacementWord,
                            onValueChange = { newReplacementWord = it },
                            placeholder = { Text("${locStrings.wordsReplacement} (e.g. haha)", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceLightDark,
                                unfocusedContainerColor = SurfaceLightDark,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (newTargetWord.isNotBlank() && newReplacementWord.isNotBlank()) {
                                val newRule = WordReplacement(
                                    targetWord = newTargetWord.trim(),
                                    replacement = newReplacementWord.trim()
                                )
                                ttsManager.wordReplacements.add(0, newRule)
                                rulesList = ttsManager.wordReplacements.toList()
                                newTargetWord = ""
                                newReplacementWord = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("+ ${locStrings.add}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = BorderDark
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${locStrings.wordsTitle} (${rulesList.size})",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (rulesList.isEmpty()) {
                        Text(
                            text = locStrings.wordsEmpty,
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rulesList.forEach { rule ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceLightDark, RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, BorderDark), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { checked ->
                                                rule.isEnabled = checked
                                                rulesList = ttsManager.wordReplacements.toList()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (rule.isEnabled) RoseError.copy(alpha = 0.15f) else SurfaceLightDark,
                                                border = BorderStroke(1.dp, if (rule.isEnabled) RoseError.copy(alpha = 0.4f) else BorderDark)
                                            ) {
                                                Text(
                                                    text = rule.targetWord,
                                                    color = if (rule.isEnabled) RoseError else TextMuted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text("→", color = TextSecondary, fontSize = 11.sp)
                                            Surface(
                                                shape = CircleShape,
                                                color = if (rule.isEnabled) EmeraldSuccess.copy(alpha = 0.15f) else SurfaceLightDark,
                                                border = BorderStroke(1.dp, if (rule.isEnabled) EmeraldSuccess.copy(alpha = 0.4f) else BorderDark)
                                            ) {
                                                Text(
                                                    text = rule.replacement,
                                                    color = if (rule.isEnabled) EmeraldSuccess else TextMuted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            ttsManager.wordReplacements.remove(rule)
                                            rulesList = ttsManager.wordReplacements.toList()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus Aturan",
                                            tint = RoseError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // KATA TERLARANG (BLACKLIST) SECTION
        item {
            Spacer(modifier = Modifier.height(6.dp))
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("KATA TERLARANG / BLACKLIST TTS", color = RoseError, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Komentar yang mengandung kata di bawah ini TIDAK AKAN dibacakan oleh TTS sama sekali.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = blacklistInput,
                            onValueChange = { blacklistInput = it },
                            placeholder = { Text("Kata terlarang (mis: spam)", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceLightDark,
                                unfocusedContainerColor = SurfaceLightDark,
                                focusedBorderColor = RoseError,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Button(
                            onClick = {
                                if (blacklistInput.isNotBlank()) {
                                    ttsManager.addBlacklistWord(blacklistInput)
                                    blacklistWords = ttsManager.filterBlacklistWords.toList()
                                    blacklistInput = ""
                                }
                            },
                            modifier = Modifier.height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                        ) {
                            Text("+ TAMBAH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Text(
                        text = "DAFTAR KATA TERLARANG AKTIF (${blacklistWords.size})",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        blacklistWords.forEach { word ->
                            Surface(
                                shape = CircleShape,
                                color = RoseError.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Text(
                                        text = word,
                                        color = RoseError,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            ttsManager.removeBlacklistWord(word)
                                            blacklistWords = ttsManager.filterBlacklistWords.toList()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus kata",
                                            tint = RoseError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                ttsManager.resetBlacklistWords()
                                blacklistWords = ttsManager.filterBlacklistWords.toList()
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, BorderDark),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Reset ke Default", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. TAB 4: ALERT AUDIO SETTINGS
// ==========================================
@Composable
fun AlertAudioSettingsPanel(
    alertSoundManager: com.midstreamz.audio.AlertSoundManager,
    isTikTok: Boolean = false
) {
    val context = LocalContext.current
    val ttsManager = MidStreamzApp.instance.ttsManager
    val scraper = remember { MyInstantsScraper(context) }
    DisposableEffect(scraper) {
        onDispose {
            scraper.destroy()
            alertSoundManager.stopPreview()
        }
    }

    var isEnabled by remember { mutableStateOf(alertSoundManager.isEnabled) }
    var volume by remember { mutableFloatStateOf(alertSoundManager.volume) }
    var superChatSound by remember { mutableStateOf(alertSoundManager.superChatSound) }
    var memberSound by remember { mutableStateOf(alertSoundManager.memberSound) }
    var subSound by remember { mutableStateOf(alertSoundManager.subscriberSound) }
    var likeSound by remember { mutableStateOf(alertSoundManager.likeSound) }
    var shareSound by remember { mutableStateOf(alertSoundManager.shareSound) }

    var superChatMode by remember { mutableStateOf(alertSoundManager.superChatOutputMode) }
    var memberMode by remember { mutableStateOf(alertSoundManager.memberOutputMode) }
    var subMode by remember { mutableStateOf(alertSoundManager.subscriberOutputMode) }
    var likeMode by remember { mutableStateOf(alertSoundManager.likeOutputMode) }
    var shareMode by remember { mutableStateOf(alertSoundManager.shareOutputMode) }

    // Event currently being configured in the modal window
    var selectingForEvent by remember { mutableStateOf<EventType?>(null) }

    fun testAlert(type: EventType, mode: AlertOutputMode) {
        if (mode == AlertOutputMode.DISABLE) {
            Toast.makeText(context, "Alert event ini dinonaktifkan (Disabled)", Toast.LENGTH_SHORT).show()
            return
        }
        val shouldSound = mode == AlertOutputMode.ONLY_SOUND || mode == AlertOutputMode.SOUND_AND_TTS
        val shouldTts = mode == AlertOutputMode.TTS || mode == AlertOutputMode.SOUND_AND_TTS

        val sampleText = when (type) {
            EventType.SUPER_CHAT -> if (isTikTok) "Nama mengirim Paus Biru!" else "Nama mengirim Super Chat Rp 50.000!"
            EventType.MEMBERSHIP -> "Nama bergabung menjadi Member!"
            EventType.SUBSCRIBER -> if (isTikTok) "Nama telah follow!" else "Nama telah subscribe!"
            EventType.LIKE -> if (isTikTok) "Nama menyukai siaran langsung!" else "Nama menyukai siaran langsung!"
            EventType.SHARE -> "Nama membagikan siaran langsung!"
            else -> "Nama berinteraksi di live stream!"
        }

        if (shouldSound && shouldTts) {
            // Mainkan nada alert dulu, setelah selesai baru TTS berbicara
            alertSoundManager.playAlertForEvent(type) {
                ttsManager.speakSample(sampleText)
            }
        } else if (shouldSound) {
            alertSoundManager.playAlertForEvent(type)
        } else if (shouldTts) {
            ttsManager.speakSample(sampleText)
        }
    }

    val locStrings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // Master Alert Switch Card
        SettingCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(locStrings.alertAudioTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = locStrings.alertAudioDesc,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        alertSoundManager.isEnabled = it
                    }
                )
            }
        }

        // Volume Slider Card
        SettingCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(locStrings.alertVolumeLabel, color = TextPrimary, fontSize = 13.sp)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceLightDark,
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            color = PrimaryBlue,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        alertSoundManager.volume = it
                    },
                    valueRange = 0f..1f
                )
            }
        }

        Text(
            text = "DAFTAR ALERT SOUND",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // 1. Super chat / Gift TikTok
        AlertEventRow(
            eventName = if (isTikTok) "Gift TikTok" else "Super Chat",
            badgeColor = AmberSuperChat,
            currentSound = superChatSound,
            outputMode = superChatMode,
            onOutputModeChange = {
                superChatMode = it
                alertSoundManager.superChatOutputMode = it
            },
            onSelect = { selectingForEvent = EventType.SUPER_CHAT },
            onTest = { testAlert(EventType.SUPER_CHAT, superChatMode) },
            onReset = {
                val def = AlertEventSound("Default", null)
                superChatSound = def
                alertSoundManager.superChatSound = def
                Toast.makeText(context, "${if (isTikTok) "Gift TikTok" else "Super Chat"} direset ke Default", Toast.LENGTH_SHORT).show()
            }
        )

        // 2. Member (YouTube only)
        if (!isTikTok) {
            AlertEventRow(
                eventName = "New Member",
                badgeColor = EmeraldSuccess,
                currentSound = memberSound,
                outputMode = memberMode,
                onOutputModeChange = {
                    memberMode = it
                    alertSoundManager.memberOutputMode = it
                },
                onSelect = { selectingForEvent = EventType.MEMBERSHIP },
                onTest = { testAlert(EventType.MEMBERSHIP, memberMode) },
                onReset = {
                    val def = AlertEventSound("Default", null)
                    memberSound = def
                    alertSoundManager.memberSound = def
                    Toast.makeText(context, "New Member direset ke Default", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 3. Subscriber / Follower Baru
        AlertEventRow(
            eventName = if (isTikTok) "Follower Baru" else "Subscriber Baru",
            badgeColor = RoseError,
            currentSound = subSound,
            outputMode = subMode,
            onOutputModeChange = {
                subMode = it
                alertSoundManager.subscriberOutputMode = it
            },
            onSelect = { selectingForEvent = EventType.SUBSCRIBER },
            onTest = { testAlert(EventType.SUBSCRIBER, subMode) },
            onReset = {
                val def = AlertEventSound("Default", null)
                subSound = def
                alertSoundManager.subscriberSound = def
                Toast.makeText(context, "${if (isTikTok) "Follower Baru" else "Subscriber Baru"} direset ke Default", Toast.LENGTH_SHORT).show()
            }
        )

        // 4. Share Live (TikTok & YouTube)
        AlertEventRow(
            eventName = "Share Live",
            badgeColor = Color(0xFF06B6D4),
            currentSound = shareSound,
            outputMode = shareMode,
            onOutputModeChange = {
                shareMode = it
                alertSoundManager.shareOutputMode = it
            },
            onSelect = { selectingForEvent = EventType.SHARE },
            onTest = { testAlert(EventType.SHARE, shareMode) },
            onReset = {
                val def = AlertEventSound("Default", null)
                shareSound = def
                alertSoundManager.shareSound = def
                Toast.makeText(context, "Share Live direset ke Default", Toast.LENGTH_SHORT).show()
            }
        )

        // 5. Like / Like Stream
        AlertEventRow(
            eventName = if (isTikTok) "Like Stream" else "Like Baru",
            badgeColor = Color(0xFFEC4899),
            currentSound = likeSound,
            outputMode = likeMode,
            onOutputModeChange = {
                likeMode = it
                alertSoundManager.likeOutputMode = it
            },
            onSelect = { selectingForEvent = EventType.LIKE },
            onTest = { testAlert(EventType.LIKE, likeMode) },
            onReset = {
                val def = AlertEventSound("Default", null)
                likeSound = def
                alertSoundManager.likeSound = def
                Toast.makeText(context, "${if (isTikTok) "Like Stream" else "Like Baru"} direset ke Default", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal Window: "Pilih Sound"
    if (selectingForEvent != null) {
        val targetEvent = selectingForEvent!!
        val targetEventName = when (targetEvent) {
            EventType.SUPER_CHAT -> if (isTikTok) "Gift TikTok" else "Super Chat"
            EventType.MEMBERSHIP -> "New Member"
            EventType.SUBSCRIBER -> if (isTikTok) "Follower Baru" else "Subscriber Baru"
            EventType.LIKE -> if (isTikTok) "Like Stream" else "Like Baru"
            EventType.SHARE -> "Share Live"
            else -> "Alert"
        }
        val targetBadgeColor = when (targetEvent) {
            EventType.SUPER_CHAT -> AmberSuperChat
            EventType.MEMBERSHIP -> EmeraldSuccess
            EventType.SUBSCRIBER -> RoseError
            EventType.LIKE -> Color(0xFFEC4899)
            EventType.SHARE -> Color(0xFF06B6D4)
            else -> PrimaryBlue
        }

        SelectSoundDialog(
            targetEventName = targetEventName,
            targetBadgeColor = targetBadgeColor,
            scraper = scraper,
            alertSoundManager = alertSoundManager,
            onDismiss = {
                selectingForEvent = null
                scraper.resetToDefaultList()
                alertSoundManager.stopPreview()
            },
            onSoundChosen = { sound ->
                val chosen = if (sound.audioUrl.isBlank()) {
                    AlertEventSound("Default", null)
                } else {
                    AlertEventSound(sound.title, sound.audioUrl)
                }

                when (targetEvent) {
                    EventType.SUPER_CHAT -> {
                        superChatSound = chosen
                        alertSoundManager.superChatSound = chosen
                    }
                    EventType.MEMBERSHIP -> {
                        memberSound = chosen
                        alertSoundManager.memberSound = chosen
                    }
                    EventType.SUBSCRIBER -> {
                        subSound = chosen
                        alertSoundManager.subscriberSound = chosen
                    }
                    EventType.LIKE -> {
                        likeSound = chosen
                        alertSoundManager.likeSound = chosen
                    }
                    EventType.SHARE -> {
                        shareSound = chosen
                        alertSoundManager.shareSound = chosen
                    }
                    else -> {}
                }

                Toast.makeText(context, "Suara $targetEventName diatur: ${chosen.title}", Toast.LENGTH_SHORT).show()
                selectingForEvent = null
                scraper.resetToDefaultList()
                alertSoundManager.stopPreview()
            }
        )
    }
}

@Composable
fun AlertEventRow(
    eventName: String,
    badgeColor: Color,
    currentSound: AlertEventSound,
    outputMode: AlertOutputMode,
    onOutputModeChange: (AlertOutputMode) -> Unit,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onReset: () -> Unit
) {
    var showOutputDialog by remember { mutableStateOf(false) }

    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Event Badge, Sound Name, and Active Output Mode
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = eventName.uppercase(),
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                val isLocalAudio = currentSound.audioUrl?.startsWith("/") == true || currentSound.title.startsWith("📁")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (isLocalAudio) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(13.dp))
                    } else if (!currentSound.isDefault) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = if (currentSound.isDefault) {
                            "Default (Nada Bawaan)"
                        } else {
                            currentSound.title.removePrefix("📁 ")
                        },
                        color = if (currentSound.isDefault) TextPrimary else if (isLocalAudio) EmeraldSuccess else PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Current Output Mode Chip
                val (modeLabel, modeColor) = when (outputMode) {
                    AlertOutputMode.ONLY_SOUND -> "Only Sound" to PrimaryBlue
                    AlertOutputMode.TTS -> "TTS" to AmberSuperChat
                    AlertOutputMode.SOUND_AND_TTS -> "Sound + TTS" to EmeraldSuccess
                    AlertOutputMode.DISABLE -> "Disabled" to RoseError
                }
                Surface(
                    shape = CircleShape,
                    color = modeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, modeColor.copy(alpha = 0.35f)),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        when (outputMode) {
                            AlertOutputMode.ONLY_SOUND -> Icon(Icons.Default.VolumeUp, contentDescription = null, tint = modeColor, modifier = Modifier.size(11.dp))
                            AlertOutputMode.TTS -> Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = modeColor, modifier = Modifier.size(11.dp))
                            AlertOutputMode.SOUND_AND_TTS -> Icon(Icons.Default.VolumeUp, contentDescription = null, tint = modeColor, modifier = Modifier.size(11.dp))
                            AlertOutputMode.DISABLE -> Icon(Icons.Default.VolumeOff, contentDescription = null, tint = modeColor, modifier = Modifier.size(11.dp))
                        }
                        Text(
                            text = modeLabel,
                            color = modeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right Column: 2x2 Button Grid
            // Output | Test
            // Reset  | Pilih
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ROW 1: Output | Test
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showOutputDialog = true },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Output", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }

                    OutlinedButton(
                        onClick = onTest,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, EmeraldSuccess),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Test", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                    }
                }

                // ROW 2: Reset | Pilih
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        enabled = !currentSound.isDefault,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (!currentSound.isDefault) BorderDark else Color.Transparent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Reset", fontSize = 10.sp, color = if (!currentSound.isDefault) TextMuted else Color.Transparent)
                    }

                    Button(
                        onClick = onSelect,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Pilih", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // Modal dialog for selecting Output Mode
    if (showOutputDialog) {
        AlertDialog(
            onDismissRequest = { showOutputDialog = false },
            title = {
                Text(
                    text = "Output Suara : $eventName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih bagaimana alert ini dibunyikan saat siaran langsung:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    listOf(
                        AlertOutputMode.ONLY_SOUND to "Only Sound (Hanya Efek Suara)",
                        AlertOutputMode.TTS to "TTS (Hanya Suara Teks TTS)",
                        AlertOutputMode.SOUND_AND_TTS to "Sound dan TTS (Efek Suara + Suara Teks TTS)",
                        AlertOutputMode.DISABLE to "Disable (Nonaktifkan Suara & TTS)"
                    ).forEach { (mode, label) ->
                        val isSelected = outputMode == mode
                        Surface(
                            onClick = {
                                onOutputModeChange(mode)
                                showOutputDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else BorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) PrimaryBlue else TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onOutputModeChange(mode)
                                        showOutputDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOutputDialog = false }) {
                    Text("TUTUP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
fun SelectSoundDialog(
    targetEventName: String,
    targetBadgeColor: Color,
    scraper: MyInstantsScraper,
    alertSoundManager: com.midstreamz.audio.AlertSoundManager,
    onDismiss: () -> Unit,
    onSoundChosen: (MyInstantsSound) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val isSearching by scraper.isSearching.collectAsState()
    val searchResults by scraper.searchResults.collectAsState()
    val statusText by scraper.statusText.collectAsState()
    var previewingUrl by remember { mutableStateOf<String?>(null) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Local files state & live refresh counter
    var localFilesRevision by remember { mutableIntStateOf(0) }
    val savedLocalSounds = remember(localFilesRevision) {
        val alertDir = File(context.filesDir, "custom_alert_sounds")
        if (alertDir.exists()) {
            alertDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    val cleanTitle = file.name.substringAfter("_").substringAfter("_")
                    MyInstantsSound(
                        title = if (cleanTitle.isNotBlank()) cleanTitle else file.name,
                        audioUrl = file.absolutePath
                    )
                } ?: emptyList()
        } else emptyList()
    }

    // Android SAF Audio File Picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var displayName = "Audio_Lokal_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }

                val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val alertDir = File(context.filesDir, "custom_alert_sounds")
                if (!alertDir.exists()) alertDir.mkdirs()
                val targetFile = File(alertDir, "alert_${System.currentTimeMillis()}_$safeName")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    localFilesRevision++
                    val pickedSound = MyInstantsSound(
                        title = displayName,
                        audioUrl = targetFile.absolutePath
                    )
                    onSoundChosen(pickedSound)
                } else {
                    Toast.makeText(context, "Gagal memproses file audio", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SelectSoundDialog", "Error loading local audio", e)
                Toast.makeText(context, "Gagal membuka file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Window
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Pilih Sound",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Surface(
                                shape = CircleShape,
                                color = targetBadgeColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, targetBadgeColor)
                            ) {
                                Text(
                                    text = targetEventName.uppercase(),
                                    color = targetBadgeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Pilih file dari HP atau cari sound efek online",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }

                // 1. Pilih File Audio Lokal dari HP (Pixel OS Card)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Pilih File Audio Lokal",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Format didukung: MP3, WAV, OGG, M4A, AAC",
                                color = TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Button(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("BROWSE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // 2. Search engine input + Tempel link MyInstants
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isBlank()) {
                                scraper.resetToDefaultList()
                            } else {
                                scraper.search(it)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Cari MyInstants / tempel link...", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    scraper.resetToDefaultList()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Tombol Tempel dari Clipboard jika ada teks
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim() ?: ""
                            if (clipText.isNotBlank()) {
                                searchQuery = clipText
                                scraper.search(clipText)
                            }
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(12.dp))
                            Text("TEMPEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }

                    Button(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                scraper.search(searchQuery)
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("CARI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Status searching bar
                if (isSearching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryBlue
                        )
                        Text(statusText, color = PrimaryBlue, fontSize = 11.sp)
                    }
                } else if (statusText.isNotBlank()) {
                    Text(statusText, color = TextMuted, fontSize = 10.sp)
                }

                // List Sound di Bawah (Termasuk Saved Local Files + Online Sounds)
                val filteredLocalSounds = if (searchQuery.isBlank()) {
                    savedLocalSounds
                } else {
                    savedLocalSounds.filter { it.title.contains(searchQuery, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Local sounds section if available
                    if (filteredLocalSounds.isNotEmpty()) {
                        item {
                            Text(
                                text = "FILE AUDIO LOKAL TERSIMPAN (${filteredLocalSounds.size})",
                                color = EmeraldSuccess,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        items(filteredLocalSounds, key = { it.audioUrl }) { sound ->
                            val isPlaying = previewingUrl == sound.audioUrl

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceLightDark,
                                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = sound.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Penyimpanan Perangkat HP",
                                            color = EmeraldSuccess,
                                            fontSize = 9.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Preview button
                                        Button(
                                            onClick = {
                                                if (previewingUrl == sound.audioUrl) {
                                                    alertSoundManager.stopPreview()
                                                    previewingUrl = null
                                                } else {
                                                    previewingUrl = sound.audioUrl
                                                    alertSoundManager.previewAudio(sound.audioUrl) {
                                                        if (previewingUrl == sound.audioUrl) {
                                                            previewingUrl = null
                                                        }
                                                    }
                                                }
                                            },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isPlaying) RoseError else SurfaceDark
                                            ),
                                            border = BorderStroke(1.dp, if (isPlaying) RoseError else BorderDark),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = if (isPlaying) Color.White else PrimaryBlue,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (isPlaying) "STOP" else "DENGAR",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPlaying) Color.White else PrimaryBlue
                                                )
                                            }
                                        }

                                        // Pilih button
                                        Button(
                                            onClick = { onSoundChosen(sound) },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                                        ) {
                                            Text("PILIH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Online / Default section
                    item {
                        Text(
                            text = if (searchQuery.isBlank()) "LIST SOUND DEFAULT SEBELUM MENCARI (10 SOUND)" else "HASIL PENCARIAN WWW.MYINSTANTS.COM",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    items(searchResults, key = { it.audioUrl.ifBlank { it.title } }) { sound ->
                        val isPlaying = previewingUrl == sound.audioUrl && sound.audioUrl.isNotBlank()

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceLightDark,
                            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = sound.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (sound.audioUrl.isNotBlank()) {
                                        Text(
                                            text = "www.myinstants.com",
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                    } else {
                                        Text(
                                            text = "Nada Sintetis Bawaan",
                                            color = EmeraldSuccess,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Preview button
                                    Button(
                                        onClick = {
                                            if (sound.audioUrl.isBlank()) {
                                                alertSoundManager.playDefaultSound()
                                            } else if (previewingUrl == sound.audioUrl) {
                                                alertSoundManager.stopPreview()
                                                previewingUrl = null
                                            } else {
                                                previewingUrl = sound.audioUrl
                                                alertSoundManager.previewAudio(sound.audioUrl) {
                                                    if (previewingUrl == sound.audioUrl) {
                                                        previewingUrl = null
                                                    }
                                                }
                                            }
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPlaying) RoseError else SurfaceDark
                                        ),
                                        border = BorderStroke(1.dp, if (isPlaying) RoseError else BorderDark),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isPlaying) Color.White else PrimaryBlue,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (isPlaying) "STOP" else "DENGAR",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPlaying) Color.White else PrimaryBlue
                                            )
                                        }
                                    }

                                    // Pilih button
                                    Button(
                                        onClick = { onSoundChosen(sound) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                                    ) {
                                        Text("PILIH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. TAB 5: FLOATING OVERLAY HUD
// ==========================================
@Composable
fun FloatingOverlayPanel(onToggleOverlay: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("midstreamz_hud_prefs", Context.MODE_PRIVATE) }
    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isOverlayActive by remember {
        mutableStateOf(hasPermission && prefs.getBoolean("hud_enabled", false))
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val allowed = Settings.canDrawOverlays(context)
                hasPermission = allowed
                if (allowed) {
                    isOverlayActive = prefs.getBoolean("hud_enabled", false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val overlayConfigManager = MidStreamzApp.instance.overlayConfigManager
    val config = overlayConfigManager.config
    var configVersion by remember { mutableIntStateOf(0) }
    var showVisualLayoutEditor by remember { mutableStateOf(false) }
    var visualEditorInitialLandscape by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        // CARD 1: PERMISSION ALERT (If not granted)
        if (!hasPermission) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AmberSuperChat.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AmberSuperChat.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AmberSuperChat, modifier = Modifier.size(18.dp))
                        Text(
                            "Izin Diperlukan untuk Floating Overlay",
                            color = AmberSuperChat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "Android memerlukan izin 'Tampilkan di atas aplikasi lain' agar HUD chat dapat muncul saat kamu membuka game atau aplikasi lain.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberSuperChat),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BUKA PENGATURAN IZIN OVERLAY", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // CARD: MATIKAN TOTAL TAMPILKAN ATAS LAYAR
        var isMasterOverlayDisabled by remember { mutableStateOf(config.masterOverlayDisabled) }

        SettingCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "Matikan Total Tampilkan atas layar",
                            color = if (isMasterOverlayDisabled) RoseError else TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Mematikan Semua Overlay, Jika hanya ingin menggunakan Fitur Suara saja dan menghemat Sumber Daya.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = isMasterOverlayDisabled,
                        onCheckedChange = { disabled ->
                            isMasterOverlayDisabled = disabled
                            config.masterOverlayDisabled = disabled
                            overlayConfigManager.savePreferences()
                            if (disabled) {
                                val hideIntent = Intent(context, LiveOverlayService::class.java).apply {
                                    action = LiveOverlayService.ACTION_HIDE_HUD
                                }
                                context.startService(hideIntent)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RoseError
                        )
                    )
                }
            }
        }

        // SUBORDINATE OVERLAY SETTINGS (Disabled and dimmed when master overlay is turned off)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isMasterOverlayDisabled) 0.35f else 1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // CARD 2: MASTER TOGGLE & GLOBAL OPTIONS
                var isLandscapeOnly by remember { mutableStateOf(config.landscapeOnly) }
                var selectedOverlayTab by remember { mutableIntStateOf(0) } // 0: Follow, 1: Comment, 2: Gift, 3: Song

                SettingCard {
                    // (CheckBox) Munculkan Overlay saat Layar Miring Lanskap saja
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextVal = !isLandscapeOnly
                                isLandscapeOnly = nextVal
                                config.landscapeOnly = nextVal
                                overlayConfigManager.savePreferences()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Munculkan Hanya Layar Lanskap (Miring)", color = TextPrimary, fontSize = 13.sp)
                        Checkbox(
                            checked = isLandscapeOnly,
                            onCheckedChange = null // Click handled by Row to avoid double-toggle cancellation
                        )
                    }
                }

        // CARD: WARNA NAMA PENONTON (UNIVERSAL)
        var showUniversalColorDialogFor by remember { mutableStateOf<String?>(null) } // "user", "mod", "follower"
        SettingCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "WARNA NAMA PENONTON",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Pengaturan warna universal untuk nama penonton di semua jenis overlay",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OverlayOptionButton(
                        modifier = Modifier.weight(1f),
                        label = "User",
                        valueBadge = config.universalUserColorHex,
                        badgeColor = parseHexColor(config.universalUserColorHex),
                        onClick = { showUniversalColorDialogFor = "user" }
                    )
                    OverlayOptionButton(
                        modifier = Modifier.weight(1f),
                        label = "Moderator",
                        valueBadge = config.universalModeratorColorHex,
                        badgeColor = parseHexColor(config.universalModeratorColorHex),
                        onClick = { showUniversalColorDialogFor = "mod" }
                    )
                    OverlayOptionButton(
                        modifier = Modifier.weight(1f),
                        label = "Follower",
                        valueBadge = config.universalFollowerColorHex,
                        badgeColor = parseHexColor(config.universalFollowerColorHex),
                        onClick = { showUniversalColorDialogFor = "follower" }
                    )
                }
            }
        }

        if (showUniversalColorDialogFor != null) {
            val colorKey = showUniversalColorDialogFor!!
            val initialColor = when (colorKey) {
                "user" -> config.universalUserColorHex
                "mod" -> config.universalModeratorColorHex
                else -> config.universalFollowerColorHex
            }
            OverlayColorPickerDialog(
                title = "Pilih Warna ${if (colorKey == "user") "Penonton Biasa" else if (colorKey == "mod") "Moderator" else "Follower / Member"}",
                initialHex = initialColor,
                onDismiss = { showUniversalColorDialogFor = null },
                onColorSelected = { newHex ->
                    when (colorKey) {
                        "user" -> config.universalUserColorHex = newHex
                        "mod" -> config.universalModeratorColorHex = newHex
                        "follower" -> config.universalFollowerColorHex = newHex
                    }
                    overlayConfigManager.savePreferences()
                    configVersion++
                    showUniversalColorDialogFor = null
                }
            )
        }

        // CARD 3: OVERLAY SELECTOR TABS (Follower, Komentar, Gift, Lagu)
        SettingCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PILIH OVERLAY YANG DIATUR",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Triple("FOLLOWER", Icons.Default.Favorite, 0),
                        Triple("KOMENTAR", Icons.Default.ChatBubble, 1),
                        Triple("GIFT", Icons.Default.CardGiftcard, 2),
                        Triple("LAGU", Icons.Default.MusicNote, 3)
                    ).forEach { (label, icon, idx) ->
                        val isSelected = selectedOverlayTab == idx
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) PrimaryBlue else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else BorderDark),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedOverlayTab = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else PrimaryBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Item Config based on Selected Tab
        val activeItemConfig = when (selectedOverlayTab) {
            0 -> config.followConfig
            1 -> config.commentConfig
            2 -> config.giftConfig
            else -> config.songRequestConfig
        }
        val overlayTypePrefix = when (selectedOverlayTab) {
            0 -> "follow"
            1 -> "comment"
            2 -> "gift"
            else -> "song"
        }
        val overlayTitle = when (selectedOverlayTab) {
            0 -> "Follower / Subscriber"
            1 -> "Komentar / Chat"
            2 -> "Gift / Super Chat"
            else -> "Song Request (Lagu)"
        }

        // CARD 4: OVERLAY EDITOR
        key(selectedOverlayTab) {
            OverlayEditorCard(
                title = overlayTitle,
                prefix = overlayTypePrefix,
                itemConfig = activeItemConfig,
                universalUserColorHex = config.universalUserColorHex,
                onConfigUpdated = {
                    overlayConfigManager.savePreferences()
                },
                onTestOverlay = {
                    if (!hasPermission) {
                        Toast.makeText(context, "Harap aktifkan izin overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                        return@OverlayEditorCard
                    }
                    val intent = Intent(context, LiveOverlayService::class.java).apply {
                        action = when (selectedOverlayTab) {
                            0 -> LiveOverlayService.ACTION_TEST_FOLLOW
                            1 -> LiveOverlayService.ACTION_TEST_COMMENT
                            2 -> LiveOverlayService.ACTION_TEST_GIFT
                            else -> LiveOverlayService.ACTION_TEST_SONG
                        }
                    }
                    ContextCompat.startForegroundService(context, intent)
                    Toast.makeText(context, "Menampilkan animasi overlay $overlayTitle...", Toast.LENGTH_SHORT).show()
                },
                onOpenVisualEditor = { isLandscape ->
                    visualEditorInitialLandscape = isLandscape
                    showVisualLayoutEditor = true
                }
            )
        }
        } // Close inner Column

        // Intercept touches and show hint when disabled
        if (isMasterOverlayDisabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        Toast.makeText(
                            context,
                            "Overlay sedang dimatikan total. Aktifkan saklar di atas untuk mengatur.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            )
        }
    } // Close Box
}

    // FULLSCREEN 70% DARK BACKDROP INTERACTIVE CANVAS
    if (showVisualLayoutEditor) {
        OverlayVisualLayoutEditor(
            fullConfig = config,
            initialIsLandscape = visualEditorInitialLandscape,
            onDismiss = { showVisualLayoutEditor = false },
            onSaveAndLock = {
                overlayConfigManager.savePreferences()
                configVersion++
                showVisualLayoutEditor = false
                Toast.makeText(context, "Tata letak overlay berhasil disimpan & dikunci!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
}

// ==========================================
// OVERLAY ANIMATION PREVIEW COMPONENT
// ==========================================
@Composable
fun OverlayAnimationPreview(
    prefix: String,
    itemConfig: OverlayItemConfig,
    universalUserColorHex: String
) {
    var replayTrigger by remember { mutableIntStateOf(0) }

    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val animAlpha = remember { Animatable(1f) }

    // Start/Loop Animation with entrance and exit
    LaunchedEffect(replayTrigger, itemConfig.animIn, itemConfig.animOut) {
        while (true) {
            // 1. Initial Position for Entrance
            when (itemConfig.animIn) {
                OverlayAnimIn.FADE_IN -> {
                    animOffsetX.snapTo(0f)
                    animOffsetY.snapTo(0f)
                    animAlpha.snapTo(0f)
                }
                OverlayAnimIn.SLIDE_RIGHT -> {
                    animOffsetX.snapTo(120f)
                    animOffsetY.snapTo(0f)
                    animAlpha.snapTo(0f)
                }
                OverlayAnimIn.SLIDE_LEFT -> {
                    animOffsetX.snapTo(-120f)
                    animOffsetY.snapTo(0f)
                    animAlpha.snapTo(0f)
                }
                OverlayAnimIn.SLIDE_UP -> {
                    animOffsetX.snapTo(0f)
                    animOffsetY.snapTo(45f)
                    animAlpha.snapTo(0f)
                }
                OverlayAnimIn.SLIDE_DOWN -> {
                    animOffsetX.snapTo(0f)
                    animOffsetY.snapTo(-45f)
                    animAlpha.snapTo(0f)
                }
            }

            // 2. Play Entrance Animation (380ms)
            launch {
                animOffsetX.animateTo(0f, animationSpec = tween(380, easing = FastOutSlowInEasing))
            }
            launch {
                animOffsetY.animateTo(0f, animationSpec = tween(380, easing = FastOutSlowInEasing))
            }
            animAlpha.animateTo(1f, animationSpec = tween(380, easing = LinearEasing))

            // 3. Display Duration (2.2 seconds)
            delay(2200)

            // 4. Play Exit Animation (350ms)
            val exitTargetX = when (itemConfig.animOut) {
                OverlayAnimOut.SLIDE_RIGHT -> 120f
                OverlayAnimOut.SLIDE_LEFT -> -120f
                else -> 0f
            }
            val exitTargetY = when (itemConfig.animOut) {
                OverlayAnimOut.SLIDE_UP -> -45f
                OverlayAnimOut.SLIDE_DOWN -> 45f
                else -> 0f
            }

            launch {
                animOffsetX.animateTo(exitTargetX, animationSpec = tween(350, easing = FastOutLinearInEasing))
            }
            launch {
                animOffsetY.animateTo(exitTargetY, animationSpec = tween(350, easing = FastOutLinearInEasing))
            }
            animAlpha.animateTo(0f, animationSpec = tween(350, easing = LinearEasing))

            // 5. Pause between loops (750ms)
            delay(750)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "PREVIEW ANIMASI OVERLAY",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Surface(
                    shape = CircleShape,
                    color = PrimaryBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        "LIVE",
                        color = PrimaryBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Replay Button
            Surface(
                shape = CircleShape,
                color = SurfaceLightDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.clickable { replayTrigger++ }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Putar Ulang",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Text("Putar Ulang", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Preview Stage Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF090D16))
                .border(BorderStroke(1.2.dp, Color(0xFF273142)), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val shape = when (itemConfig.bgStyle) {
                OverlayBgStyle.ROUNDED -> RoundedCornerShape(16.dp)
                OverlayBgStyle.PILL -> CircleShape
                OverlayBgStyle.SHARP -> RoundedCornerShape(4.dp)
                OverlayBgStyle.GLASS -> RoundedCornerShape(16.dp)
            }
            val baseBgColor = parseHexColor(itemConfig.bgColorHex).copy(alpha = itemConfig.bgOpacity)
            val borderStroke = if (itemConfig.outlineEnabled) {
                BorderStroke(itemConfig.outlineWidthDp.dp, parseHexColor(itemConfig.outlineColorHex))
            } else if (itemConfig.bgStyle == OverlayBgStyle.GLASS) {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            } else null

            val authorColor = parseHexColor(universalUserColorHex)
            val textColor = parseHexColor(itemConfig.textColorHex)
            val fontWeight = when (itemConfig.textStyle) {
                OverlayTextStyle.BOLD, OverlayTextStyle.BOLD_ITALIC -> FontWeight.Bold
                else -> FontWeight.Normal
            }
            val fontStyle = when (itemConfig.textStyle) {
                OverlayTextStyle.ITALIC, OverlayTextStyle.BOLD_ITALIC -> FontStyle.Italic
                else -> FontStyle.Normal
            }

            val authorName = "Budi Santoso"
            val sampleMessage = "Semangat terus live-nya!"
            val sampleGift = "Mawar"
            val sampleAmount = "10"

            val templateStr = if (itemConfig.template.isNotBlank()) itemConfig.template else when (prefix) {
                "follow" -> "\$name baru saja mengikuti!"
                "gift" -> "\$name mengirim \$gift x\$amount"
                else -> "\$name: \$message"
            }

            val resolvedText = templateStr
                .replace("\$name", authorName)
                .replace("\$message", sampleMessage)
                .replace("\$gift", sampleGift)
                .replace("\$amount", sampleAmount)

            // Animated Overlay Item Box
            Box(
                modifier = Modifier
                    .offset(x = animOffsetX.value.dp, y = animOffsetY.value.dp)
                    .alpha(animAlpha.value)
                    .clip(shape)
                    .background(baseBgColor)
                    .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val iconVector = when (prefix) {
                        "follow" -> Icons.Default.Favorite
                        "gift" -> Icons.Default.CardGiftcard
                        else -> Icons.Default.ChatBubble
                    }
                    val iconTint = when (prefix) {
                        "follow" -> Color(0xFFF43F5E)
                        "gift" -> Color(0xFFF59E0B)
                        else -> Color(0xFF38BDF8)
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )

                    val nameIdx = resolvedText.indexOf(authorName)
                    if (nameIdx != -1) {
                        val beforeName = resolvedText.substring(0, nameIdx)
                        val afterName = resolvedText.substring(nameIdx + authorName.length)
                        Text(
                            text = buildAnnotatedString {
                                if (beforeName.isNotEmpty()) {
                                    append(beforeName)
                                }
                                withStyle(SpanStyle(color = authorColor, fontWeight = FontWeight.Bold)) {
                                    append(authorName)
                                }
                                if (afterName.isNotEmpty()) {
                                    append(afterName)
                                }
                            },
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle
                        )
                    } else {
                        Text(
                            text = resolvedText,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// OVERLAY EDITOR CARD & DIALOG WINDOWS
// ==========================================
@Composable
fun OverlayEditorCard(
    title: String,
    prefix: String,
    itemConfig: OverlayItemConfig,
    universalUserColorHex: String = "#60A5FA",
    onConfigUpdated: () -> Unit,
    onTestOverlay: () -> Unit,
    onOpenVisualEditor: (Boolean) -> Unit
) {
    var templateText by remember(itemConfig) { mutableStateOf(itemConfig.template) }
    var selectedAnimIn by remember(itemConfig) { mutableStateOf(itemConfig.animIn) }
    var selectedAnimOut by remember(itemConfig) { mutableStateOf(itemConfig.animOut) }
    var isEnabled by remember(itemConfig) { mutableStateOf(itemConfig.isEnabled) }

    // Dialog States
    var showColorDialogFor by remember { mutableStateOf<String?>(null) } // "textColor", "bgColor", "userColor", "modColor", "followerColor", "outlineColor"
    var showTextStyleDialog by remember { mutableStateOf(false) }
    var showOutlineDialog by remember { mutableStateOf(false) }
    var showBgStyleDialog by remember { mutableStateOf(false) }
    var showBgOpacityDialog by remember { mutableStateOf(false) }

    var showAnimInDialog by remember { mutableStateOf(false) }
    var showAnimOutDialog by remember { mutableStateOf(false) }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header + Active Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        itemConfig.isEnabled = it
                        onConfigUpdated()
                    }
                )
            }

            HorizontalDivider(color = BorderDark.copy(alpha = 0.4f), thickness = 1.dp)

            // PREVIEW ANIMASI OVERLAY (Terletak di atas PILIHAN ANIMASI)
            OverlayAnimationPreview(
                prefix = prefix,
                itemConfig = itemConfig,
                universalUserColorHex = universalUserColorHex
            )

            HorizontalDivider(color = BorderDark.copy(alpha = 0.4f), thickness = 1.dp)

            // 1. Pilihan Animasi Masuk & Keluar (Dipisah)
            Text("PILIHAN ANIMASI", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Masuk :
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Masuk :", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceLightDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAnimInDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                selectedAnimIn.displayName,
                                color = PrimaryBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Keluar :
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Keluar :", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceLightDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAnimOutDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                selectedAnimOut.displayName,
                                color = Color(0xFFF472B6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderDark.copy(alpha = 0.4f), thickness = 1.dp)

            // 2. Format Teks Template
            Text("FORMAT TEKS OVERLAY", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            OutlinedTextField(
                value = templateText,
                onValueChange = {
                    templateText = it
                    itemConfig.template = it
                    onConfigUpdated()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceLightDark,
                    unfocusedContainerColor = SurfaceLightDark
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                placeholder = { Text("Template pesan", color = TextMuted, fontSize = 11.sp) }
            )

            // Placeholder chips
            val placeholderTags = when (prefix) {
                "song" -> listOf("\$name", "\$judul", "\$message")
                "gift" -> listOf("\$name", "\$gift", "\$amount", "\$message")
                "follow" -> listOf("\$name", "\$message")
                else -> listOf("\$name", "\$message")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                placeholderTags.forEach { tag ->
                    Surface(
                        shape = CircleShape,
                        color = SurfaceLightDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.clickable {
                            val newText = if (templateText.isEmpty()) tag else "$templateText $tag"
                            templateText = newText
                            itemConfig.template = newText
                            onConfigUpdated()
                        }
                    ) {
                        Text(tag, color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }

            HorizontalDivider(color = BorderDark.copy(alpha = 0.4f), thickness = 1.dp)

            // 3. Tampilan Text: Warna | Gaya | Outline
            Text("TAMPILAN TEKS", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OverlayOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "Warna",
                    valueBadge = itemConfig.textColorHex,
                    badgeColor = parseHexColor(itemConfig.textColorHex),
                    onClick = { showColorDialogFor = "textColor" }
                )
                OverlayOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "Gaya",
                    valueBadge = when (itemConfig.textStyle) {
                        OverlayTextStyle.NORMAL -> "Normal"
                        OverlayTextStyle.BOLD -> "Tebal"
                        OverlayTextStyle.ITALIC -> "Miring"
                        OverlayTextStyle.BOLD_ITALIC -> "Tebal+Miring"
                    },
                    onClick = { showTextStyleDialog = true }
                )
                OverlayOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "Outline",
                    valueBadge = if (itemConfig.outlineEnabled) "${itemConfig.outlineWidthDp.toInt()}dp" else "Off",
                    badgeColor = if (itemConfig.outlineEnabled) parseHexColor(itemConfig.outlineColorHex) else null,
                    onClick = { showOutlineDialog = true }
                )
            }

            HorizontalDivider(color = BorderDark.copy(alpha = 0.4f), thickness = 1.dp)

            // 4. Latar Belakang: Warna | Opacity | Gaya
            Text("LATAR BELAKANG", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OverlayOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "Warna",
                    valueBadge = itemConfig.bgColorHex,
                    badgeColor = parseHexColor(itemConfig.bgColorHex),
                    onClick = { showColorDialogFor = "bgColor" }
                )
                OverlayOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "Opacity",
                    valueBadge = "${(itemConfig.bgOpacity * 100).toInt()}%",
                    onClick = { showBgOpacityDialog = true }
                )
                OverlayOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "Gaya",
                    valueBadge = itemConfig.bgStyle.displayName.take(7),
                    onClick = { showBgStyleDialog = true }
                )
            }

            HorizontalDivider(color = BorderDark.copy(alpha = 0.4f), thickness = 1.dp)

            // 5. Atur Tata Letak & Save and Lock (Bisa atur Vertikal & Lanskap secara terpisah)
            Text("TATA LETAK & KUNCI POSISI", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceLightDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (itemConfig.isLocked) "Status: Terkunci" else "Status: Bebas Digeser",
                            color = if (itemConfig.isLocked) AmberSuperChat else Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Card Info Posisi Vertikal & Lanskap secara terpisah
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                                    Text("Mode Vertikal", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("X: ${itemConfig.portraitX}  Y: ${itemConfig.portraitY}", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                                    Text("Mode Lanskap", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("X: ${itemConfig.landscapeX}  Y: ${itemConfig.landscapeY}", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Dua Tombol Terpisah untuk masuk ke editor visual
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOpenVisualEditor(false) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ATUR VERTIKAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = { onOpenVisualEditor(true) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ATUR LANSKAP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Test Button
            Button(
                onClick = onTestOverlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("TES ANIMASI OVERLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    // DIALOG PILIHAN ANIMASI MASUK
    if (showAnimInDialog) {
        AlertDialog(
            onDismissRequest = { showAnimInDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Pilih Animasi Masuk", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayAnimIn.values().forEach { anim ->
                        val isAnimSelected = selectedAnimIn == anim
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isAnimSelected) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (isAnimSelected) PrimaryBlue else BorderDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAnimIn = anim
                                    itemConfig.animIn = anim
                                    onConfigUpdated()
                                    showAnimInDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    anim.displayName,
                                    color = if (isAnimSelected) PrimaryBlue else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isAnimSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isAnimSelected) {
                                    Text("AKTIF", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnimInDialog = false }) {
                    Text("TUTUP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // DIALOG PILIHAN ANIMASI KELUAR
    if (showAnimOutDialog) {
        AlertDialog(
            onDismissRequest = { showAnimOutDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Pilih Animasi Keluar", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayAnimOut.values().forEach { anim ->
                        val isAnimSelected = selectedAnimOut == anim
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isAnimSelected) Color(0xFFF472B6).copy(alpha = 0.15f) else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (isAnimSelected) Color(0xFFF472B6) else BorderDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAnimOut = anim
                                    itemConfig.animOut = anim
                                    onConfigUpdated()
                                    showAnimOutDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    anim.displayName,
                                    color = if (isAnimSelected) Color(0xFFF472B6) else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isAnimSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isAnimSelected) {
                                    Text("AKTIF", color = Color(0xFFF472B6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnimOutDialog = false }) {
                    Text("TUTUP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // MODAL WINDOW 1: COLOR PICKER
    if (showColorDialogFor != null) {
        val dialogTitle = when (showColorDialogFor) {
            "textColor" -> "Pilih Warna Teks"
            "bgColor" -> "Pilih Warna Background"
            "userColor" -> "Pilih Warna Nama User"
            "modColor" -> "Pilih Warna Nama Moderator"
            "followerColor" -> "Pilih Warna Nama Follower/Member"
            "outlineColor" -> "Pilih Warna Outline"
            else -> "Pilih Warna"
        }
        val currentHex = when (showColorDialogFor) {
            "textColor" -> itemConfig.textColorHex
            "bgColor" -> itemConfig.bgColorHex
            "userColor" -> itemConfig.userColorHex
            "modColor" -> itemConfig.moderatorColorHex
            "followerColor" -> itemConfig.followerColorHex
            "outlineColor" -> itemConfig.outlineColorHex
            else -> "#FFFFFF"
        }

        OverlayColorPickerDialog(
            title = dialogTitle,
            initialHex = currentHex,
            onDismiss = { showColorDialogFor = null },
            onColorSelected = { selectedHex ->
                when (showColorDialogFor) {
                    "textColor" -> itemConfig.textColorHex = selectedHex
                    "bgColor" -> itemConfig.bgColorHex = selectedHex
                    "userColor" -> itemConfig.userColorHex = selectedHex
                    "modColor" -> itemConfig.moderatorColorHex = selectedHex
                    "followerColor" -> itemConfig.followerColorHex = selectedHex
                    "outlineColor" -> itemConfig.outlineColorHex = selectedHex
                }
                onConfigUpdated()
                showColorDialogFor = null
            }
        )
    }

    // MODAL WINDOW 2: TEXT STYLE
    if (showTextStyleDialog) {
        OverlayTextStyleDialog(
            currentStyle = itemConfig.textStyle,
            onDismiss = { showTextStyleDialog = false },
            onStyleSelected = {
                itemConfig.textStyle = it
                onConfigUpdated()
                showTextStyleDialog = false
            }
        )
    }

    // MODAL WINDOW 3: OUTLINE
    if (showOutlineDialog) {
        OverlayOutlineDialog(
            initialEnabled = itemConfig.outlineEnabled,
            initialWidth = itemConfig.outlineWidthDp,
            initialColorHex = itemConfig.outlineColorHex,
            onDismiss = { showOutlineDialog = false },
            onSave = { enabled, width, colHex ->
                itemConfig.outlineEnabled = enabled
                itemConfig.outlineWidthDp = width
                itemConfig.outlineColorHex = colHex
                onConfigUpdated()
                showOutlineDialog = false
            }
        )
    }

    // MODAL WINDOW 4: BG STYLE
    if (showBgStyleDialog) {
        OverlayBgStyleDialog(
            currentStyle = itemConfig.bgStyle,
            onDismiss = { showBgStyleDialog = false },
            onStyleSelected = {
                itemConfig.bgStyle = it
                onConfigUpdated()
                showBgStyleDialog = false
            }
        )
    }

    // MODAL WINDOW 5: BG OPACITY
    if (showBgOpacityDialog) {
        OverlayBgOpacityDialog(
            initialOpacity = itemConfig.bgOpacity,
            onDismiss = { showBgOpacityDialog = false },
            onSave = {
                itemConfig.bgOpacity = it
                onConfigUpdated()
                showBgOpacityDialog = false
            }
        )
    }
}

// ==========================================
// REUSABLE OVERLAY OPTION BUTTON
// ==========================================
@Composable
fun OverlayOptionButton(
    modifier: Modifier = Modifier,
    label: String,
    valueBadge: String,
    badgeColor: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceLightDark,
        border = BorderStroke(1.dp, BorderDark),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (badgeColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                            .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                }
                Text(valueBadge, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ==========================================
// MODAL DIALOGS IMPLEMENTATION
// ==========================================
@Composable
fun OverlayColorPickerDialog(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var hexInput by remember { mutableStateOf(initialHex) }
    val popularColors = listOf(
        "#FFFFFF", "#F8FAFC", "#94A3B8", "#60A5FA", "#38BDF8", "#34D399",
        "#10B981", "#FBBF24", "#F59E0B", "#F472B6", "#EC4899", "#A855F7",
        "#EF4444", "#0F172A", "#1E293B", "#4A044E", "#78350F", "#000000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Color Preview Box
                val previewColor = parseHexColor(hexInput)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(previewColor)
                            .border(1.5.dp, BorderDark, RoundedCornerShape(12.dp))
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.trim() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Kode HEX", fontSize = 10.sp) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                }

                Text("Palet Warna Populer:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                // Color Palette Grid
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    popularColors.chunked(6).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowColors.forEach { colHex ->
                                val col = parseHexColor(colHex)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            if (hexInput.equals(colHex, ignoreCase = true)) 2.5.dp else 1.dp,
                                            if (hexInput.equals(colHex, ignoreCase = true)) PrimaryBlue else BorderDark,
                                            CircleShape
                                        )
                                        .clickable { hexInput = colHex }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(hexInput) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("TERAPKAN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("BATAL", color = TextMuted)
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun OverlayTextStyleDialog(
    currentStyle: OverlayTextStyle,
    onDismiss: () -> Unit,
    onStyleSelected: (OverlayTextStyle) -> Unit
) {
    var selected by remember { mutableStateOf(currentStyle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Pilih Gaya Teks", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayTextStyle.values().forEach { style ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected == style) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                        border = BorderStroke(1.dp, if (selected == style) PrimaryBlue else BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = style }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(style.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = if (style == OverlayTextStyle.BOLD || style == OverlayTextStyle.BOLD_ITALIC) FontWeight.Bold else FontWeight.Normal)
                            if (selected == style) {
                                Text("AKTIF", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStyleSelected(selected) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("SIMPAN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun OverlayOutlineDialog(
    initialEnabled: Boolean,
    initialWidth: Float,
    initialColorHex: String,
    onDismiss: () -> Unit,
    onSave: (Boolean, Float, String) -> Unit
) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    var width by remember { mutableFloatStateOf(initialWidth) }
    var colorHex by remember { mutableStateOf(initialColorHex) }
    var showColorPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Pengaturan Outline Text", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aktifkan Outline Teks", color = TextPrimary, fontSize = 13.sp)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    Text("Ketebalan Outline: ${width.toInt()} dp", color = TextSecondary, fontSize = 11.sp)
                    Slider(
                        value = width,
                        onValueChange = { width = it },
                        valueRange = 1f..6f,
                        steps = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Warna Outline: $colorHex", color = TextPrimary, fontSize = 12.sp)
                        Surface(
                            shape = CircleShape,
                            color = parseHexColor(colorHex),
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { showColorPicker = true }
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(enabled, width, colorHex) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("SIMPAN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL", color = TextMuted) }
        },
        containerColor = SurfaceDark
    )

    if (showColorPicker) {
        OverlayColorPickerDialog(
            title = "Pilih Warna Outline",
            initialHex = colorHex,
            onDismiss = { showColorPicker = false },
            onColorSelected = {
                colorHex = it
                showColorPicker = false
            }
        )
    }
}

@Composable
fun OverlayBgOpacityDialog(
    initialOpacity: Float,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    var opacity by remember { mutableFloatStateOf(initialOpacity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Opacity Latar Belakang", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tingkat Transparansi: ${(opacity * 100).toInt()}%", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.1f..1.0f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(opacity) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("SIMPAN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL", color = TextMuted) }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun OverlayBgStyleDialog(
    currentStyle: OverlayBgStyle,
    onDismiss: () -> Unit,
    onStyleSelected: (OverlayBgStyle) -> Unit
) {
    var selected by remember { mutableStateOf(currentStyle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Pilih Gaya Latar Belakang", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayBgStyle.values().forEach { style ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected == style) PrimaryBlue.copy(alpha = 0.15f) else SurfaceLightDark,
                        border = BorderStroke(1.dp, if (selected == style) PrimaryBlue else BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = style }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(style.displayName, color = TextPrimary, fontSize = 13.sp)
                            if (selected == style) {
                                Text("AKTIF", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStyleSelected(selected) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("SIMPAN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SurfaceDark
    )
}

fun parseHexColor(hex: String, defaultColor: Color = Color.White): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        defaultColor
    }
}

@Composable
fun DraggableOverlayCardPreview(
    labelBadge: String,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    authorName: String,
    authorColorHex: String,
    messageText: String,
    itemConfig: OverlayItemConfig,
    isLandscape: Boolean,
    scale: Float = 1.0f,
    isSelected: Boolean = false,
    x: Float,
    y: Float,
    onClick: () -> Unit = {},
    onDrag: (Float, Float) -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val shape = when (itemConfig.bgStyle) {
        OverlayBgStyle.ROUNDED -> RoundedCornerShape(16.dp)
        OverlayBgStyle.PILL -> CircleShape
        OverlayBgStyle.SHARP -> RoundedCornerShape(4.dp)
        OverlayBgStyle.GLASS -> RoundedCornerShape(16.dp)
    }
    val bgColor = parseHexColor(itemConfig.bgColorHex).copy(alpha = itemConfig.bgOpacity)
    val textColor = parseHexColor(itemConfig.textColorHex)
    val authorColor = parseHexColor(authorColorHex)

    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .pointerInput(isLandscape, itemConfig) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onClick() }
    ) {
        Surface(
            shape = shape,
            color = bgColor,
            border = BorderStroke(if (isSelected) 2.5.dp else 1.5.dp, if (isSelected) Color.White else badgeColor),
            shadowElevation = if (isSelected) 14.dp else 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) Color.White else badgeColor, modifier = Modifier.size(15.dp))
                Text(
                    authorName,
                    color = authorColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    messageText,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = when (itemConfig.textStyle) {
                        OverlayTextStyle.NORMAL -> FontWeight.Normal
                        OverlayTextStyle.BOLD -> FontWeight.Bold
                        OverlayTextStyle.ITALIC -> FontWeight.Normal
                        OverlayTextStyle.BOLD_ITALIC -> FontWeight.Bold
                    }
                )
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) badgeColor else badgeColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else badgeColor)
                ) {
                    Text(
                        if (isSelected) "✓ $labelBadge" else "✥ $labelBadge",
                        color = if (isSelected) Color.White else badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var cur = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

@Composable
fun OverlayVisualLayoutEditor(
    fullConfig: FullOverlayConfig,
    initialIsLandscape: Boolean = false,
    onDismiss: () -> Unit,
    onSaveAndLock: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var isLandscapeTab by rememberSaveable { mutableStateOf(initialIsLandscape) }
    var selectedOverlayKey by rememberSaveable { mutableStateOf("follower") }

    // State koordinat dipisah 100% dan di-hoist di level luar Dialog menggunakan rememberSaveable
    // agar rotasi layar Activity / Dialog tidak pernah me-reset atau mencampur nilai vertikal & horizontal
    var followPortX by rememberSaveable { mutableFloatStateOf(fullConfig.followConfig.portraitX.toFloat()) }
    var followPortY by rememberSaveable { mutableFloatStateOf(fullConfig.followConfig.portraitY.toFloat()) }
    var followLandX by rememberSaveable { mutableFloatStateOf(fullConfig.followConfig.landscapeX.toFloat()) }
    var followLandY by rememberSaveable { mutableFloatStateOf(fullConfig.followConfig.landscapeY.toFloat()) }
    var followScale by rememberSaveable { mutableFloatStateOf(fullConfig.followConfig.scale) }

    var commentPortX by rememberSaveable { mutableFloatStateOf(fullConfig.commentConfig.portraitX.toFloat()) }
    var commentPortY by rememberSaveable { mutableFloatStateOf(fullConfig.commentConfig.portraitY.toFloat()) }
    var commentLandX by rememberSaveable { mutableFloatStateOf(fullConfig.commentConfig.landscapeX.toFloat()) }
    var commentLandY by rememberSaveable { mutableFloatStateOf(fullConfig.commentConfig.landscapeY.toFloat()) }
    var commentScale by rememberSaveable { mutableFloatStateOf(fullConfig.commentConfig.scale) }

    var giftPortX by rememberSaveable { mutableFloatStateOf(fullConfig.giftConfig.portraitX.toFloat()) }
    var giftPortY by rememberSaveable { mutableFloatStateOf(fullConfig.giftConfig.portraitY.toFloat()) }
    var giftLandX by rememberSaveable { mutableFloatStateOf(fullConfig.giftConfig.landscapeX.toFloat()) }
    var giftLandY by rememberSaveable { mutableFloatStateOf(fullConfig.giftConfig.landscapeY.toFloat()) }
    var giftScale by rememberSaveable { mutableFloatStateOf(fullConfig.giftConfig.scale) }

    var songPortX by rememberSaveable { mutableFloatStateOf(fullConfig.songRequestConfig.portraitX.toFloat()) }
    var songPortY by rememberSaveable { mutableFloatStateOf(fullConfig.songRequestConfig.portraitY.toFloat()) }
    var songLandX by rememberSaveable { mutableFloatStateOf(fullConfig.songRequestConfig.landscapeX.toFloat()) }
    var songLandY by rememberSaveable { mutableFloatStateOf(fullConfig.songRequestConfig.landscapeY.toFloat()) }
    var songScale by rememberSaveable { mutableFloatStateOf(fullConfig.songRequestConfig.scale) }

    // Otomatis putar layar HP saat tab orientasi dipilih
    LaunchedEffect(isLandscapeTab) {
        if (isLandscapeTab) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Kembalikan orientasi ke portrait saat keluar dari editor
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let { w ->
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
        ) {
            // TOP CONTROL HEADER
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    "Atur Tata Letak Overlay",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                if (isLandscapeTab) "Mengatur posisi layar LANSKAP (Miring)" else "Mengatur posisi layar VERTIKAL (Tegak)",
                                color = if (isLandscapeTab) Color(0xFF10B981) else PrimaryBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = SurfaceLightDark,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.clickable { onDismiss() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                Text(
                                    "Tutup",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Switch Tabs: Vertikal vs Lanskap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (!isLandscapeTab) PrimaryBlue else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (!isLandscapeTab) PrimaryBlue else BorderDark),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isLandscapeTab = false }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = if (!isLandscapeTab) Color.White else PrimaryBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "Mode Vertikal",
                                    color = if (!isLandscapeTab) Color.White else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isLandscapeTab) Color(0xFF10B981) else SurfaceLightDark,
                            border = BorderStroke(1.dp, if (isLandscapeTab) Color(0xFF10B981) else BorderDark),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isLandscapeTab = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ScreenRotation,
                                    contentDescription = null,
                                    tint = if (isLandscapeTab) Color.White else Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "Mode Lanskap",
                                    color = if (isLandscapeTab) Color.White else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // DRAGGABLE OVERLAY 1: FOLLOWER
            val curFollowX = if (isLandscapeTab) followLandX else followPortX
            val curFollowY = if (isLandscapeTab) followLandY else followPortY
            DraggableOverlayCardPreview(
                labelBadge = "Follower",
                badgeColor = Color(0xFFF472B6),
                icon = Icons.Default.Favorite,
                authorName = "Nimi",
                authorColorHex = fullConfig.followConfig.followerColorHex,
                messageText = "telah memfollow",
                itemConfig = fullConfig.followConfig,
                isLandscape = isLandscapeTab,
                scale = followScale,
                isSelected = selectedOverlayKey == "follower",
                x = curFollowX,
                y = curFollowY,
                onClick = { selectedOverlayKey = "follower" },
                onDrag = { dx: Float, dy: Float ->
                    if (isLandscapeTab) {
                        followLandX = (followLandX + dx).coerceAtLeast(0f)
                        followLandY = (followLandY + dy).coerceAtLeast(0f)
                    } else {
                        followPortX = (followPortX + dx).coerceAtLeast(0f)
                        followPortY = (followPortY + dy).coerceAtLeast(0f)
                    }
                }
            )

            // DRAGGABLE OVERLAY 2: KOMENTAR
            val curCommentX = if (isLandscapeTab) commentLandX else commentPortX
            val curCommentY = if (isLandscapeTab) commentLandY else commentPortY
            DraggableOverlayCardPreview(
                labelBadge = "Komentar",
                badgeColor = Color(0xFF60A5FA),
                icon = Icons.Default.ChatBubble,
                authorName = "Nimi: ",
                authorColorHex = fullConfig.commentConfig.userColorHex,
                messageText = "Semangat livenya bang!",
                itemConfig = fullConfig.commentConfig,
                isLandscape = isLandscapeTab,
                scale = commentScale,
                isSelected = selectedOverlayKey == "comment",
                x = curCommentX,
                y = curCommentY,
                onClick = { selectedOverlayKey = "comment" },
                onDrag = { dx: Float, dy: Float ->
                    if (isLandscapeTab) {
                        commentLandX = (commentLandX + dx).coerceAtLeast(0f)
                        commentLandY = (commentLandY + dy).coerceAtLeast(0f)
                    } else {
                        commentPortX = (commentPortX + dx).coerceAtLeast(0f)
                        commentPortY = (commentPortY + dy).coerceAtLeast(0f)
                    }
                }
            )

            // DRAGGABLE OVERLAY 3: GIFT
            val curGiftX = if (isLandscapeTab) giftLandX else giftPortX
            val curGiftY = if (isLandscapeTab) giftLandY else giftPortY
            DraggableOverlayCardPreview(
                labelBadge = "Gift",
                badgeColor = Color(0xFFF59E0B),
                icon = Icons.Default.CardGiftcard,
                authorName = "Nimi ",
                authorColorHex = fullConfig.giftConfig.userColorHex,
                messageText = "Mawar x10",
                itemConfig = fullConfig.giftConfig,
                isLandscape = isLandscapeTab,
                scale = giftScale,
                isSelected = selectedOverlayKey == "gift",
                x = curGiftX,
                y = curGiftY,
                onClick = { selectedOverlayKey = "gift" },
                onDrag = { dx: Float, dy: Float ->
                    if (isLandscapeTab) {
                        giftLandX = (giftLandX + dx).coerceAtLeast(0f)
                        giftLandY = (giftLandY + dy).coerceAtLeast(0f)
                    } else {
                        giftPortX = (giftPortX + dx).coerceAtLeast(0f)
                        giftPortY = (giftPortY + dy).coerceAtLeast(0f)
                    }
                }
            )

            // DRAGGABLE OVERLAY 4: SONG REQUEST (LAGU)
            val curSongX = if (isLandscapeTab) songLandX else songPortX
            val curSongY = if (isLandscapeTab) songLandY else songPortY
            DraggableOverlayCardPreview(
                labelBadge = "Lagu",
                badgeColor = Color(0xFF818CF8),
                icon = Icons.Default.MusicNote,
                authorName = "Nimi: ",
                authorColorHex = fullConfig.songRequestConfig.userColorHex,
                messageText = "memutar Sheila On 7 - Dan",
                itemConfig = fullConfig.songRequestConfig,
                isLandscape = isLandscapeTab,
                scale = songScale,
                isSelected = selectedOverlayKey == "song",
                x = curSongX,
                y = curSongY,
                onClick = { selectedOverlayKey = "song" },
                onDrag = { dx: Float, dy: Float ->
                    if (isLandscapeTab) {
                        songLandX = (songLandX + dx).coerceAtLeast(0f)
                        songLandY = (songLandY + dy).coerceAtLeast(0f)
                    } else {
                        songPortX = (songPortX + dx).coerceAtLeast(0f)
                        songPortY = (songPortY + dy).coerceAtLeast(0f)
                    }
                }
            )

            // BOTTOM ACTION BAR
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = SurfaceDark.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // BOX SIZE SLIDER (Di atas Text "Sedang Mengatur: Mode...")
                    val currentSelectedScale = when (selectedOverlayKey) {
                        "follower" -> followScale
                        "comment" -> commentScale
                        "gift" -> giftScale
                        else -> songScale
                    }
                    val currentSelectedTitle = when (selectedOverlayKey) {
                        "follower" -> "Follower"
                        "comment" -> "Komentar"
                        "gift" -> "Gift"
                        else -> "Song Request"
                    }
                    val currentSelectedBadgeColor = when (selectedOverlayKey) {
                        "follower" -> Color(0xFFF472B6)
                        "comment" -> Color(0xFF60A5FA)
                        "gift" -> Color(0xFFF59E0B)
                        else -> Color(0xFF818CF8)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceLightDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "Ukuran (Size) Overlay:",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = currentSelectedBadgeColor.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, currentSelectedBadgeColor)
                                    ) {
                                        Text(
                                            currentSelectedTitle,
                                            color = currentSelectedBadgeColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    "${(currentSelectedScale * 100).roundToInt()}%",
                                    color = PrimaryBlue,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = currentSelectedScale,
                                onValueChange = { newScale ->
                                    when (selectedOverlayKey) {
                                        "follower" -> followScale = newScale
                                        "comment" -> commentScale = newScale
                                        "gift" -> giftScale = newScale
                                        else -> songScale = newScale
                                    }
                                },
                                valueRange = 0.5f..2.0f,
                                steps = 15,
                                colors = SliderDefaults.colors(
                                    thumbColor = PrimaryBlue,
                                    activeTrackColor = PrimaryBlue,
                                    inactiveTrackColor = BorderDark
                                ),
                                modifier = Modifier.fillMaxWidth().height(26.dp)
                            )

                            Text(
                                "Ketuk overlay di layar untuk memilih lalu geser slider ukuran",
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isLandscapeTab) "Sedang Mengatur: Mode Lanskap" else "Sedang Mengatur: Mode Vertikal",
                            color = if (isLandscapeTab) Color(0xFF10B981) else PrimaryBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isLandscapeTab) "Posisi Vertikal Aman Tersimpan" else "Posisi Lanskap Aman Tersimpan",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("BATAL", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                // Simpan Vertikal
                                fullConfig.followConfig.portraitX = followPortX.roundToInt()
                                fullConfig.followConfig.portraitY = followPortY.roundToInt()
                                fullConfig.commentConfig.portraitX = commentPortX.roundToInt()
                                fullConfig.commentConfig.portraitY = commentPortY.roundToInt()
                                fullConfig.giftConfig.portraitX = giftPortX.roundToInt()
                                fullConfig.giftConfig.portraitY = giftPortY.roundToInt()
                                fullConfig.songRequestConfig.portraitX = songPortX.roundToInt()
                                fullConfig.songRequestConfig.portraitY = songPortY.roundToInt()

                                // Simpan Lanskap
                                fullConfig.followConfig.landscapeX = followLandX.roundToInt()
                                fullConfig.followConfig.landscapeY = followLandY.roundToInt()
                                fullConfig.commentConfig.landscapeX = commentLandX.roundToInt()
                                fullConfig.commentConfig.landscapeY = commentLandY.roundToInt()
                                fullConfig.giftConfig.landscapeX = giftLandX.roundToInt()
                                fullConfig.giftConfig.landscapeY = giftLandY.roundToInt()
                                fullConfig.songRequestConfig.landscapeX = songLandX.roundToInt()
                                fullConfig.songRequestConfig.landscapeY = songLandY.roundToInt()

                                // Simpan Skala
                                fullConfig.followConfig.scale = followScale
                                fullConfig.commentConfig.scale = commentScale
                                fullConfig.giftConfig.scale = giftScale
                                fullConfig.songRequestConfig.scale = songScale

                                fullConfig.followConfig.isLocked = true
                                fullConfig.commentConfig.isLocked = true
                                fullConfig.giftConfig.isLocked = true
                                fullConfig.songRequestConfig.isLocked = true

                                onSaveAndLock()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.weight(2f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SIMPAN & KUNCI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. TAB 6: LIVE CHAT FEED & SIMULASI
// ==========================================
@Composable
fun LiveFeedPanel(
    liveManager: com.midstreamz.manager.LiveStreamManager,
    isTikTok: Boolean = false
) {
    val locStrings = LocalAppStrings.current
    val chatList by liveManager.chatMessages.collectAsState()
    val isSimulating by liveManager.isSimulating.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Action Bar for Simulation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { liveManager.toggleSimulation() },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSimulating) RoseError else if (isTikTok) Color(0xFFFE2C55) else PrimaryBlue
                )
            ) {
                Text(
                    text = if (isSimulating) locStrings.liveFeedStopSim else locStrings.liveFeedStartSim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Quick Test Buttons Row
            Surface(
                shape = CircleShape,
                color = AmberSuperChat.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AmberSuperChat.copy(alpha = 0.5f)),
                modifier = Modifier
                    .height(40.dp)
                    .clickable { liveManager.sendTestEvent(EventType.SUPER_CHAT) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isTikTok) "+ Gift" else "+ SuperChat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberSuperChat)
                }
            }

            Surface(
                shape = CircleShape,
                color = EmeraldSuccess.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                modifier = Modifier
                    .height(40.dp)
                    .clickable { liveManager.sendTestEvent(EventType.MEMBERSHIP) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isTikTok) "+ Share" else "+ Member", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                }
            }

            Surface(
                shape = CircleShape,
                color = RoseError.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                modifier = Modifier
                    .height(40.dp)
                    .clickable { liveManager.sendTestEvent(EventType.SUBSCRIBER) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isTikTok) "+ Follow" else "+ Sub", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoseError)
                }
            }

            Surface(
                shape = CircleShape,
                color = Color(0xFFEC4899).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.5f)),
                modifier = Modifier
                    .height(40.dp)
                    .clickable { liveManager.sendTestEvent(EventType.LIKE) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Like", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEC4899))
                }
            }

            if (chatList.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { liveManager.clearMessages() }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Clear", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }

        if (chatList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = locStrings.liveFeedEmpty,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = locStrings.liveFeedWaiting,
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatList, key = { it.id }) { chat ->
                    ChatMessageRow(chat)
                }
            }
        }
    }
}

// ==========================================
// 9. REUSABLE SETTING CARD & CHAT ROW
// ==========================================
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark,
        border = BorderStroke(1.2.dp, Color(0xFF38404E)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun ChatMessageRow(chat: LiveChatMessage) {
    val borderColor = when (chat.type) {
        EventType.SUPER_CHAT -> AmberSuperChat
        EventType.MEMBERSHIP -> EmeraldSuccess
        EventType.SUBSCRIBER -> RoseError
        EventType.LIKE -> Color(0xFFEC4899)
        else -> BorderDark
    }

    val badgeBackground = when (chat.type) {
        EventType.SUPER_CHAT -> AmberSuperChat
        EventType.MEMBERSHIP -> EmeraldSuccess
        EventType.SUBSCRIBER -> RoseError
        EventType.LIKE -> Color(0xFFEC4899)
        else -> PrimaryBlue
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chat.type == EventType.SUPER_CHAT) {
                        Surface(
                            shape = CircleShape,
                            color = badgeBackground
                        ) {
                            Text(
                                text = chat.amountFormatted ?: if (chat.isTikTok) "GIFT TIKTOK" else "SUPER CHAT",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else if (chat.type == EventType.MEMBERSHIP) {
                        Surface(
                            shape = CircleShape,
                            color = badgeBackground
                        ) {
                            Text(
                                text = if (chat.isTikTok) "SHARE LIVE" else "MEMBER",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else if (chat.type == EventType.SUBSCRIBER) {
                        Surface(
                            shape = CircleShape,
                            color = badgeBackground
                        ) {
                            Text(
                                text = if (chat.isTikTok) "FOLLOWER" else "SUBSCRIBE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else if (chat.type == EventType.LIKE) {
                        Surface(
                            shape = CircleShape,
                            color = badgeBackground
                        ) {
                            Text(
                                text = if (chat.isTikTok) "LIKE STREAM" else "LIKE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else if (chat.isModerator) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = EmeraldSuccess
                        ) {
                            Text(
                                text = "MOD",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = chat.authorName,
                        color = if (chat.type == EventType.SUPER_CHAT) AmberSuperChat else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                val elapsedSeconds = remember(chat.timestampMs) {
                    val sec = (System.currentTimeMillis() - chat.timestampMs) / 1000
                    "${sec.coerceAtLeast(0)}s lalu"
                }
                Text(
                    text = elapsedSeconds,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = chat.message,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
        }
    }
}

// ==========================================
// 8. TAB 6: LINKKEY OVERLAY PANEL (NO LOCALHOST)
// ==========================================
@Composable
fun LinkKeyOverlayPanel(relayManager: LinkKeyRelayManager) {
    val locStrings = LocalAppStrings.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val linkKey by relayManager.linkKey.collectAsState()
    val isConnected by relayManager.isConnected.collectAsState()
    val statusText by relayManager.lastStatus.collectAsState()

    val syncChat by relayManager.syncChatToOverlay.collectAsState()
    val syncAlerts by relayManager.syncAlertsToOverlay.collectAsState()
    val syncSong by relayManager.syncSongToOverlay.collectAsState()

    var showEditKeyDialog by remember { mutableStateOf(false) }
    var editKeyInput by remember { mutableStateOf("") }

    if (showEditKeyDialog) {
        AlertDialog(
            onDismissRequest = { showEditKeyDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = SurfaceDark,
            title = {
                Text(locStrings.linkKeyChangeTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        locStrings.linkKeyChangeDesc,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = editKeyInput,
                        onValueChange = { editKeyInput = it.uppercase() },
                        singleLine = true,
                        placeholder = { Text("Contoh: STREAM-99", color = TextMuted) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceLightDark,
                            unfocusedContainerColor = SurfaceLightDark,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editKeyInput.trim().length >= 3) {
                            relayManager.updateLinkKey(editKeyInput.trim())
                            showEditKeyDialog = false
                            Toast.makeText(context, locStrings.linkKeyUpdatedToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(locStrings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditKeyDialog = false }) {
                    Text(locStrings.cancel, color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // EXPERIMENTAL NOTICE BANNER (Pixel OS / M3)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AmberSuperChat.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, AmberSuperChat.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = AmberSuperChat.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, AmberSuperChat)
                ) {
                    Text(
                        text = "🧪",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = locStrings.linkKeyBetaBadge,
                        color = AmberSuperChat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = locStrings.linkKeyBetaDesc,
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // CARD 1: RELAY STATUS & LINKKEY
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "LINKKEY CLOUD RELAY",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = CircleShape,
                            color = AmberSuperChat.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AmberSuperChat.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "EKSPERIMENTAL",
                                color = AmberSuperChat,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (isConnected) EmeraldSuccess.copy(alpha = 0.15f) else RoseError.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isConnected) EmeraldSuccess.copy(alpha = 0.4f) else RoseError.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isConnected) "ONLINE" else "DISCONNECTED",
                            color = if (isConnected) EmeraldSuccess else RoseError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Key Display
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceLightDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "KODE LINKKEY ANDA",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = linkKey,
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(linkKey))
                                Toast.makeText(context, "LinkKey disalin: $linkKey", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("SALIN KEY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val localPath = "overlay\\overlay.html"
                            clipboardManager.setText(AnnotatedString(localPath))
                            Toast.makeText(context, "Path file overlay.html disalin! Buka via Local File di OBS.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("SALIN PATH FILE OBS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            editKeyInput = linkKey
                            showEditKeyDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("GANTI KEY", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = statusText,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // CARD 2: QUICK TEST BUTTONS (CEK VISUAL DI OBS)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "TES VISUAL KE OVERLAY OBS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Tekan tombol di bawah untuk menguji animasi & audio di OBS secara instan.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            relayManager.testAlert("SUPER_CHAT")
                            Toast.makeText(context, "Tes Super Chat dikirim ke OBS!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberSuperChat),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text("SUPER CHAT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
                    }

                    Button(
                        onClick = {
                            relayManager.testAlert("SUBSCRIBE")
                            Toast.makeText(context, "Tes Subscriber dikirim ke OBS!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text("SUBSCRIBER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            relayManager.testChat()
                            Toast.makeText(context, "Tes Chat dikirim ke OBS!", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CHAT OBS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            relayManager.publishSongEvent(
                                "PLAY",
                                SongItem(title = "Armada - Asal Kau Bahagia", youtubeId = "py6GDNgye6k", requester = "Siti"),
                                emptyList()
                            )
                            Toast.makeText(context, "Tes Song Banner dikirim ke OBS!", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("BANNER !SR", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // CARD 3: PENGATURAN SINKRONISASI
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "OPSI SINKRONISASI KE OVERLAY",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kirim Live Chat ke OBS", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Tampilkan chat bubble transparan di layar stream", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = syncChat,
                        onCheckedChange = { relayManager.toggleSyncChat(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                    )
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kirim Alert Super Chat & Member", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Pop-up visual animasi + efek suara audio", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = syncAlerts,
                        onCheckedChange = { relayManager.toggleSyncAlerts(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                    )
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kirim Banner Song Request (!sr)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Banner Now Playing lagu di sudut layar", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = syncSong,
                        onCheckedChange = { relayManager.toggleSyncSong(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                    )
                }
            }
        }

        // CARD 4: PANDUAN OBS STUDIO
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "PANDUAN PASANG DI OBS STUDIO",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Buka OBS Studio di PC -> Klik ikon '+' di Sources -> Pilih 'Browser Source'.\n2. Centang 'Local file' dan pilih file overlay.html dari project (atau isi URL overlay dengan parameter ?key=$linkKey).\n3. Set resolusi ke 1920x1080 (background otomatis transparan).\n4. Selesai! Semua alert, chat, & lagu tersinkron otomatis via cloud tanpa butuh localhost & tanpa satu WiFi.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 9. TAB 7: SONG REQUEST PANEL (!sr)
// ==========================================
@Composable
fun SongRequestPanel(songManager: SongRequestManager) {
    val locStrings = LocalAppStrings.current
    val context = LocalContext.current
    val isEnabled by songManager.isSrEnabled.collectAsState()
    val commandKeyword by songManager.commandKeyword.collectAsState()
    val playOnPhone by songManager.playOnPhone.collectAsState()
    val isPlaying by songManager.isPlaying.collectAsState()
    val currentSong by songManager.currentSong.collectAsState()
    val queue by songManager.queue.collectAsState()
    val maxQueuePerUser by songManager.maxQueuePerUser.collectAsState()
    val statusText by songManager.statusText.collectAsState()
    val volume by songManager.volume.collectAsState()
    val duckOnTts by songManager.duckOnTts.collectAsState()

    var manualSongInput by remember { mutableStateOf("") }
    var keywordInput by remember(commandKeyword) { mutableStateOf(commandKeyword) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // CARD 1: STATUS & TOGGLE !sr
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SONG REQUEST (!${commandKeyword})",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isEnabled) EmeraldSuccess.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isEnabled) locStrings.songRequestActive else locStrings.songRequestInactive,
                                    color = if (isEnabled) EmeraldSuccess else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = locStrings.songRequestHelp,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { songManager.toggleSrEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                    )
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                // TOGGLE AUDIO DI SPEAKER HP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Putar Audio di HP Ini",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Dengarkan musik langsung dari speaker HP (selain OBS Overlay)",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = playOnPhone,
                        onCheckedChange = { songManager.togglePlayOnPhone(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldSuccess)
                    )
                }

                // ATUR VOLUME MUSIK
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Volume",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Volume Musik: $volume%",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Presets
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(25, 50, 80, 100).forEach { preset ->
                                Surface(
                                    onClick = { songManager.setVolume(preset) },
                                    shape = CircleShape,
                                    color = if (volume == preset) PrimaryBlue else SurfaceLightDark,
                                    border = BorderStroke(1.dp, if (volume == preset) PrimaryBlue else BorderDark)
                                ) {
                                    Text(
                                        text = "$preset%",
                                        color = if (volume == preset) Color.White else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { songManager.setVolume(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryBlue,
                            activeTrackColor = PrimaryBlue,
                            inactiveTrackColor = BorderDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }

                // TOGGLE LOWER VOLUME SAAT TTS MASUK
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kecilkan Musik saat TTS (Auto Ducking)",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Otomatis meredam volume musik saat ada TTS bicara, dan langsung normal kembali saat selesai",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = duckOnTts,
                        onCheckedChange = {
                            songManager.toggleDuckOnTts(it)
                            MidStreamzApp.instance.ttsManager.duckAudioOnTts = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldSuccess)
                    )
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Maks Antrian per Orang: $maxQueuePerUser",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 3, 5).forEach { limit ->
                            Surface(
                                onClick = { songManager.setMaxQueuePerUser(limit) },
                                shape = CircleShape,
                                color = if (maxQueuePerUser == limit) PrimaryBlue else SurfaceLightDark,
                                border = BorderStroke(1.dp, if (maxQueuePerUser == limit) PrimaryBlue else BorderDark)
                            ) {
                                Text(
                                    text = "$limit",
                                    color = if (maxQueuePerUser == limit) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // CARD: KUSTOM PERINTAH / PREFIX REQUEST
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CUSTOM PERINTAH / PREFIX REQUEST",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ganti kata perintah 'sr' dengan kata apapun (misal: 'lagu', 'request', 'play'). Penonton mengetik '!<kata> <judul>' untuk request lagu.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it.trim().removePrefix("!").removePrefix("/") },
                        leadingIcon = {
                            Text("!", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        },
                        placeholder = { Text("sr", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceLightDark,
                            unfocusedContainerColor = SurfaceLightDark,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Button(
                        onClick = {
                            songManager.setCommandKeyword(keywordInput)
                            Toast.makeText(context, "Perintah diubah menjadi: !${songManager.commandKeyword.value}", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("SIMPAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Text(
                    text = "Saat ini aktif: !${commandKeyword} <judul lagu> (juga mendukung !sr dan !request)",
                    color = EmeraldSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // CARD 2: SEDANG DIPUTAR (NOW PLAYING)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SEDANG DIPUTAR (NOW PLAYING)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (isPlaying) EmeraldSuccess.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isPlaying) "MEMUTAR" else if (currentSong != null) "DIJEDA" else "STANDBY",
                            color = if (isPlaying) EmeraldSuccess else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (currentSong != null) {
                    val song = currentSong!!
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceLightDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = "Diminta oleh: @${song.requester}",
                                        color = PrimaryBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    if (song.youtubeId.isNotBlank()) {
                                        Text(
                                            text = "YouTube ID: ${song.youtubeId}",
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons: Jeda/Putar, Skip, Stop
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isPlaying) songManager.pause() else songManager.resume()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPlaying) SurfaceLightDark else EmeraldSuccess
                                    ),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, if (isPlaying) BorderDark else EmeraldSuccess),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (isPlaying) "JEDA" else "PUTAR",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isPlaying) TextPrimary else Color.White
                                    )
                                }

                                Button(
                                    onClick = { songManager.skipCurrent() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("SKIP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { songManager.stopCurrent() },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoseError.copy(alpha = 0.2f)),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, RoseError.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("STOP", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = RoseError)
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceLightDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Tidak ada lagu yang sedang diputar",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Status: $statusText",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // CARD 3: DAFTAR ANTRIAN
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${locStrings.songRequestQueueTitle} (${queue.size})",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    if (queue.isNotEmpty()) {
                        TextButton(
                            onClick = { songManager.clearQueue() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(locStrings.songRequestClearQueueBtn, color = RoseError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (queue.isEmpty()) {
                    Text(
                        text = locStrings.songRequestQueueEmpty,
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        queue.forEachIndexed { index, item ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceLightDark,
                                border = BorderStroke(1.dp, BorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Column {
                                            Text(
                                                text = item.title,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Req: @${item.requester}",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { songManager.removeSong(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // CARD 4: TAMBAH LAGU MANUAL / TES
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "TAMBAH LAGU MANUAL / SIMULASI",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualSongInput,
                        onValueChange = { manualSongInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Judul lagu / URL YouTube", color = TextMuted, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceLightDark,
                            unfocusedContainerColor = SurfaceLightDark,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Button(
                        onClick = {
                            if (manualSongInput.isNotBlank()) {
                                songManager.requestSong(manualSongInput.trim(), "Streamer")
                                manualSongInput = ""
                                Toast.makeText(context, "Lagu ditambahkan ke antrian!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = CircleShape,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(locStrings.add, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

