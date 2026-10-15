package com.midstreamz.tts

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Intelligent Language Detector combining:
 * 1. Instant Script-based Detection (Japanese, Korean, Chinese, Arabic, Cyrillic, Thai, Hindi) (~0ms)
 * 2. High-speed Slang/Keyword Heuristic for Indonesian & English (~0ms)
 * 3. On-Device Google ML Kit Language Identification (100% offline, ~5-15ms)
 */
object LanguageDetector {
    private const val TAG = "LanguageDetector"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val languageIdentifier: LanguageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    // Heuristic patterns for non-Latin scripts (Instant ~0ms)
    private val JAPANESE_PATTERN = Pattern.compile("[\\u3040-\\u309F\\u30A0-\\u30FF]")
    private val KOREAN_PATTERN = Pattern.compile("[\\uAC00-\\uD7AF\\u1100-\\u11FF]")
    private val CHINESE_PATTERN = Pattern.compile("[\\u4E00-\\u9FFF]")
    private val ARABIC_PATTERN = Pattern.compile("[\\u0600-\\u06FF]")
    private val CYRILLIC_PATTERN = Pattern.compile("[\\u0400-\\u04FF]")
    private val THAI_PATTERN = Pattern.compile("[\\u0E00-\\u0E7F]")
    private val DEVANAGARI_PATTERN = Pattern.compile("[\\u0900-\\u097F]")
    private val GREEK_PATTERN = Pattern.compile("[\\u0370-\\u03FF]")
    private val HEBREW_PATTERN = Pattern.compile("[\\u0590-\\u05FF]")

    // High confidence Indonesian keywords & slang
    private val INDONESIAN_WORDS = setOf(
        "halo", "hai", "bang", "bg", "ap", "apa", "mntp", "mantap", "min", "kak", "bro", "wkwk",
        "wkwkwk", "siapa", "dimana", "ini", "itu", "yang", "dan", "di", "gak", "nggak", "ga",
        "bisa", "dong", "udah", "lagi", "keren", "bagus", "makasih", "terima", "kasih", "sama",
        "juga", "live", "gw", "lu", "gua", "kapan", "kok", "knp", "kenapa", "gimana", "ada",
        "tidak", "aja", "saja", "udah", "sudah", "belum", "lah", "sih", "deh", "banget", "bgt",
        "ya", "iya", "mabar", "ku", "mu", "saya", "kamu", "dia", "mereka", "kita", "nih", "tuh",
        "makasi", "makasih", "om", "mas", "gan", "kakak", "adek", "yuk", "kuy", "ayok", "ayo"
    )

    // High confidence English words
    private val ENGLISH_WORDS = setOf(
        "hello", "hi", "hey", "nice", "good", "great", "cool", "bro", "dude", "stream",
        "streamer", "live", "play", "game", "why", "what", "how", "who", "when", "where",
        "thanks", "thank", "you", "awesome", "lol", "lmao", "omg", "yes", "no", "ok", "okay",
        "sub", "subscribe", "channel", "love", "please", "can", "could", "would", "is", "are",
        "am", "was", "were", "the", "this", "that", "there", "their", "they", "we", "us",
        "with", "from", "for", "and", "but", "so", "very", "much", "welcome", "watch", "watching",
        "gg", "wp", "let", "lets", "go", "man", "guy", "guys", "first", "best"
    )

    /**
     * Fast synchronous heuristic check (instant, non-blocking).
     * Returns detected code or null if ambiguous/needs ML Kit.
     */
    fun detectQuickHeuristic(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 1. Script checks
        if (JAPANESE_PATTERN.matcher(trimmed).find()) return "ja-JP"
        if (KOREAN_PATTERN.matcher(trimmed).find()) return "ko-KR"
        if (ARABIC_PATTERN.matcher(trimmed).find()) return "ar-SA"
        if (THAI_PATTERN.matcher(trimmed).find()) return "th-TH"
        if (DEVANAGARI_PATTERN.matcher(trimmed).find()) return "hi-IN"
        if (GREEK_PATTERN.matcher(trimmed).find()) return "el-GR"
        if (HEBREW_PATTERN.matcher(trimmed).find()) return "he-IL"
        if (CYRILLIC_PATTERN.matcher(trimmed).find()) return "ru-RU"
        if (CHINESE_PATTERN.matcher(trimmed).find()) return "zh-CN"

        // 2. Tokenized keyword scoring
        val words = trimmed.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.isNotEmpty() }
        var indoScore = 0
        var engScore = 0
        for (w in words) {
            if (INDONESIAN_WORDS.contains(w)) indoScore++
            if (ENGLISH_WORDS.contains(w)) engScore++
        }

        if (indoScore > 0 && engScore == 0) return "id-ID"
        if (engScore > 0 && indoScore == 0) return "en-US"
        if (indoScore > engScore) return "id-ID"
        if (engScore > indoScore) return "en-US"

        return null
    }

    /**
     * Asynchronously detects language:
     * 1. Returns immediately if quick heuristic matches.
     * 2. Otherwise runs ML Kit on-device model with a 300ms fallback watchdog.
     */
    fun detectLanguage(
        text: String,
        defaultFallbackCode: String = "id-ID",
        onResult: (String) -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onResult(defaultFallbackCode)
            return
        }

        // Fast path: Quick heuristic (~0ms)
        val quick = detectQuickHeuristic(trimmed)
        if (quick != null) {
            onResult(quick)
            return
        }

        // Deep path: Google ML Kit (On-Device, Offline)
        val isHandled = AtomicBoolean(false)

        val timeoutRunnable = Runnable {
            if (isHandled.compareAndSet(false, true)) {
                Log.d(TAG, "ML Kit detection timed out, fallback to $defaultFallbackCode")
                onResult(defaultFallbackCode)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 350L)

        try {
            languageIdentifier.identifyLanguage(trimmed)
                .addOnSuccessListener { langTag ->
                    if (isHandled.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        val mapped = if (langTag != null && langTag != "und") {
                            mapMlKitTagToSupported(langTag)
                        } else null
                        val finalLang = mapped ?: defaultFallbackCode
                        Log.d(TAG, "ML Kit detected '$langTag' -> mapped to '$finalLang' for: $trimmed")
                        onResult(finalLang)
                    }
                }
                .addOnFailureListener { e ->
                    if (isHandled.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        Log.w(TAG, "ML Kit identification failed: ${e.message}, fallback to $defaultFallbackCode")
                        onResult(defaultFallbackCode)
                    }
                }
        } catch (e: Exception) {
            if (isHandled.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                Log.e(TAG, "Error invoking ML Kit", e)
                onResult(defaultFallbackCode)
            }
        }
    }

    private fun mapMlKitTagToSupported(tag: String): String? {
        val clean = tag.lowercase()
        return when {
            clean == "id" || clean == "in" -> "id-ID"
            clean == "en" || clean == "en-us" -> "en-US"
            clean == "en-gb" -> "en-GB"
            clean == "ja" -> "ja-JP"
            clean == "ko" -> "ko-KR"
            clean == "zh-tw" || clean == "zh-hant" -> "zh-TW"
            clean.startsWith("zh") -> "zh-CN"
            clean == "de" -> "de-DE"
            clean == "fr" -> "fr-FR"
            clean == "es-mx" -> "es-MX"
            clean.startsWith("es") -> "es-ES"
            clean == "it" -> "it-IT"
            clean == "nl" -> "nl-NL"
            clean == "ar" -> "ar-SA"
            clean == "hi" -> "hi-IN"
            clean == "ru" -> "ru-RU"
            clean == "sv" -> "sv-SE"
            clean == "th" -> "th-TH"
            clean == "tr" -> "tr-TR"
            clean == "pt-pt" -> "pt-PT"
            clean.startsWith("pt") -> "pt-BR"
            clean == "vi" -> "vi-VN"
            clean == "no" || clean == "nb" || clean == "nn" -> "no-NO"
            clean == "da" -> "da-DK"
            clean == "fi" -> "fi-FI"
            clean == "pl" -> "pl-PL"
            clean == "uk" -> "uk-UA"
            clean == "cs" -> "cs-CZ"
            clean == "hu" -> "hu-HU"
            clean == "ro" -> "ro-RO"
            clean == "ms" -> "ms-MY"
            clean == "tl" || clean == "fil" -> "fil-PH"
            clean == "he" || clean == "iw" -> "he-IL"
            clean == "el" -> "el-GR"
            clean == "bg" -> "bg-BG"
            clean == "sk" -> "sk-SK"
            clean == "hr" -> "hr-HR"
            else -> null
        }
    }
}
