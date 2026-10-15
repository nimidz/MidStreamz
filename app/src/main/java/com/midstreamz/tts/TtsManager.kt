package com.midstreamz.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.midstreamz.model.EventType
import com.midstreamz.model.LiveChatMessage
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern

enum class TtsFormatTemplate(val displayName: String) {
    AUTHOR_AND_MESSAGE("{author}: {message}"),
    AUTHOR_SAYS("{author} berkata: {message}"),
    MESSAGE_ONLY("Hanya Pesan ({message})")
}

data class TtsLanguage(
    val code: String,
    val displayName: String,
    val locale: Locale,
    val sampleText: String
)

data class WordReplacement(
    val id: String = java.util.UUID.randomUUID().toString(),
    var targetWord: String,
    var replacement: String,
    var isEnabled: Boolean = true
)

data class VoiceProfileConfig(
    var name: String,
    var languageCode: String = "all",
    var voiceName: String? = null,
    var pitch: Float = 1.0f,
    var speed: Float = 1.05f,
    var volume: Float = 1.0f
)

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "TtsManager"
    private val PREFS_NAME = "midstreamz_tts_prefs"
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var tts: TextToSpeech? = null
        private set
    var isInitialized = false
        private set

    // 1. Master Toggle
    var isEnabled: Boolean = true

    // 2. Global Voice Parameters
    var volumeBoost: Boolean = true
    var dynamicSpeed: Boolean = true // Dinamis (cepat saat antrean menumpuk)
    var readEmoji: Boolean = true // Baca Emoji
    var skipUrls: Boolean = true // Lewati URL (jangan baca link http)
    var onlySuperChat: Boolean = false // Hanya Obrolan Super

    // 3. Multi-Language Catalog
    val supportedLanguages = listOf(
        TtsLanguage("all", "Semua Bahasa (All Language)", Locale("id", "ID"), "Halo, Hello, こんにちは! Mode Semua Bahasa aktif. Suara otomatis mengikuti bahasa komentar."),
        TtsLanguage("en-US", "English (American)", Locale("en", "US"), "Hello! This is an American English text to speech voice."),
        TtsLanguage("en-GB", "English (British)", Locale("en", "GB"), "Hello! This is a British English text to speech voice."),
        TtsLanguage("zh-CN", "简体中文 (Tionghoa Sederhana)", Locale("zh", "CN"), "你好！这是简体中文语音合成测试。"),
        TtsLanguage("zh-TW", "繁體中文 (Tionghoa Tradisional)", Locale("zh", "TW"), "你好！這是繁體中文語音合成測試。"),
        TtsLanguage("ja-JP", "日本語 (Jepang)", Locale("ja", "JP"), "こんにちは！これは日本語の音声合成テストです。"),
        TtsLanguage("de-DE", "Deutsch (Jerman)", Locale("de", "DE"), "Hallo! Dies ist ein deutscher Text-zu-Sprache-Test."),
        TtsLanguage("fr-FR", "Français (Prancis)", Locale("fr", "FR"), "Bonjour ! Ceci est un test de synthèse vocale en français."),
        TtsLanguage("ko-KR", "한국어 (Korea)", Locale("ko", "KR"), "안녕하세요! 이것은 한국어 텍스트 음성 변환 테스트입니다."),
        TtsLanguage("es-ES", "Español (España) (Spanyol - Spanyol)", Locale("es", "ES"), "¡Hola! Esta es una prueba de texto a voz en español de España."),
        TtsLanguage("es-MX", "Español (México) (Spanyol - Meksiko)", Locale("es", "MX"), "¡Hola! Esta es una prueba de texto a voz en español de México."),
        TtsLanguage("it-IT", "Italiano (Italia)", Locale("it", "IT"), "Ciao! Questo è un test di sintesi vocale in italiano."),
        TtsLanguage("nl-NL", "Nederlands (Belanda)", Locale("nl", "NL"), "Hallo! Dit is een test van tekst-naar-spraak in het Nederlands."),
        TtsLanguage("ar-SA", "العربية (Arab)", Locale("ar", "SA"), "مرحباً! هذا اختبار تحويل النص إلى كلام باللغة العربية."),
        TtsLanguage("hi-IN", "हिन्दी (Hindi)", Locale("hi", "IN"), "नमस्ते! यह हिंदी टेक्स्ट टू स्पीच का परीक्षण है।"),
        TtsLanguage("ru-RU", "Русский (Rusia)", Locale("ru", "RU"), "Здравствуйте! Это проверка синтеза речи на русском языке."),
        TtsLanguage("sv-SE", "Svenska (Swedia)", Locale("sv", "SE"), "Hej! Detta är ett test av talsyntes på svenska."),
        TtsLanguage("th-TH", "ไทย (Thailand)", Locale("th", "TH"), "สวัสดี! นี่คือการทดสอบการอ่านออกเสียงข้อความเป็นคำพูดภาษาไทย"),
        TtsLanguage("tr-TR", "Türkçe (Turki)", Locale("tr", "TR"), "Merhaba! Bu, Türkçe metin okuma testidir."),
        TtsLanguage("pt-PT", "Português (Portugal) (Portugis - Portugal)", Locale("pt", "PT"), "Olá! Este é um teste de conversão de texto em voz em português de Portugal."),
        TtsLanguage("pt-BR", "Português (Brasil) (Portugis - Brasil)", Locale("pt", "BR"), "Olá! Este é um teste de conversão de texto em fala em português do Brasil."),
        TtsLanguage("vi-VN", "Tiếng Việt (Vietnam)", Locale("vi", "VN"), "Xin chào! Đây là bài kiểm tra chuyển văn bản thành giọng nói tiếng Việt."),
        TtsLanguage("no-NO", "Norsk (Norwegia)", Locale("no", "NO"), "Hei! Dette er en test av tekst-til-tale på norsk."),
        TtsLanguage("da-DK", "Dansk (Denmark)", Locale("da", "DK"), "Hej! Dette er en test af tekst-til-tale på dansk."),
        TtsLanguage("fi-FI", "Suomi (Finlandia)", Locale("fi", "FI"), "Hei! Tämä on tekstistä puheeksi -testi suomeksi."),
        TtsLanguage("id-ID", "Bahasa Indonesia", Locale("id", "ID"), "Halo! Ini adalah contoh suara Text to Speech bahasa Indonesia."),
        TtsLanguage("pl-PL", "Polski (Polandia)", Locale("pl", "PL"), "Cześć! To jest test syntezy mowy w języku polskim."),
        TtsLanguage("uk-UA", "Українська (Ukraina)", Locale("uk", "UA"), "Привіт! Це перевірка синтезу мовлення українською мовою."),
        TtsLanguage("cs-CZ", "Čeština (Ceko)", Locale("cs", "CZ"), "Ahoj! Toto je test převodu textu na řeč v češtině."),
        TtsLanguage("hu-HU", "Magyar (Hongaria)", Locale("hu", "HU"), "Helló! Ez egy szövegfelolvasási teszt magyar nyelven."),
        TtsLanguage("ro-RO", "Română (Rumania)", Locale("ro", "RO"), "Bună! Acesta este un test de redare a textului în vorbire în limba română."),
        TtsLanguage("ms-MY", "Bahasa Melayu", Locale("ms", "MY"), "Halo! Ini adalah contoh suara Text to Speech bahasa Melayu."),
        TtsLanguage("fil-PH", "Filipino", Locale("fil", "PH"), "Kumusta! Ito ay isang pagsubok ng text to speech sa Filipino."),
        TtsLanguage("he-IL", "עברית (Ibrani)", Locale("he", "IL"), "שלום! זוהי בדיקת טקסט לדיבור בעברית."),
        TtsLanguage("el-GR", "Ελληνικά (Yunani)", Locale("el", "GR"), "Γεια σας! Αυτή είναι μια δοκιμή μετατροπής κειμένου σε ομιλία στα ελληνικά."),
        TtsLanguage("bg-BG", "Български (Bulgaria)", Locale("bg", "BG"), "Здравейте! Това е тест за преобразуване на текст в реч на български."),
        TtsLanguage("sk-SK", "Slovenčina (Slowakia)", Locale("sk", "SK"), "Ahoj! Toto je test prevodu textu na reč v slovenčine."),
        TtsLanguage("hr-HR", "Hrvatski (Kroasia)", Locale("hr", "HR"), "Pozdrav! Ovo je test pretvaranja teksta u govor na hrvatskom.")
    )

    fun getDefaultCountryLanguageCode(): String {
        val appPrefs = context.getSharedPreferences("midstreamz_app_prefs", Context.MODE_PRIVATE)
        val appLangCode = appPrefs.getString("app_language_code", null)
        if (!appLangCode.isNullOrEmpty()) {
            val exact = supportedLanguages.find { it.code.equals(appLangCode, ignoreCase = true) }
            if (exact != null && exact.code != "all") return exact.code
            val prefix = appLangCode.substringBefore("-").lowercase()
            val matchPrefix = supportedLanguages.find { it.code != "all" && it.code.lowercase().startsWith(prefix) }
            if (matchPrefix != null) return matchPrefix.code
        }
        val systemLang = Locale.getDefault().language.lowercase()
        val matchSystem = supportedLanguages.find { it.code != "all" && it.code.lowercase().startsWith(systemLang) }
        return matchSystem?.code ?: "id-ID"
    }

    var selectedGlobalLanguageCode: String = "id-ID"
    var selectedGlobalVoiceName: String? = null

    // 4. Role Voice Profiles
    val normalProfile = VoiceProfileConfig(name = "Komentar Normal", languageCode = "id-ID", pitch = 1.0f, speed = 1.05f, volume = 1.0f)
    val moderatorProfile = VoiceProfileConfig(name = "Moderator", languageCode = "id-ID", pitch = 1.15f, speed = 1.1f, volume = 1.0f)
    val ownerProfile = VoiceProfileConfig(name = "Pemilik Channel", languageCode = "id-ID", pitch = 0.9f, speed = 1.0f, volume = 1.0f)
    val superChatProfile = VoiceProfileConfig(name = "Super Chat", languageCode = "id-ID", pitch = 1.25f, speed = 1.15f, volume = 1.0f)

    // 5. Word Replacements (Penggantian Kata TTS / Kamus Slang)
    var isWordReplacementEnabled: Boolean = true
    val wordReplacements: MutableList<WordReplacement> = mutableListOf(
        WordReplacement(targetWord = "bg", replacement = "bang"),
        WordReplacement(targetWord = "ap", replacement = "apa"),
        WordReplacement(targetWord = "mntp", replacement = "mantap")
    )

    // 6. Format Template
    var formatTemplate: TtsFormatTemplate = TtsFormatTemplate.AUTHOR_AND_MESSAGE

    // 7. Anti-Spam & Delay Prevention (Free The Voice YT)
    var maxMessageLength: Int = 120 // Batasi Komentar Panjang
    var maxEmojiCount: Int = 3
    var queueThreshold: Int = 4 // Ambang batas antrean
    var protectVipInQueue: Boolean = true // Lindungi Pemilik, Moderator & SuperChat

    // 8. Blacklist & Commands
    var ignoreCommandPrefix: Boolean = true // Abaikan Perintah Bot (!)
    var filterBlacklistWords: MutableList<String> = mutableListOf("kasar", "anjing", "babi", "kontol", "memek", "tolol", "bangsat")

    // Voice OS list from Android System
    var allVoices: List<Voice> = emptyList()
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ConcurrentLinkedQueue<LiveChatMessage>()
    @Volatile
    private var isSpeaking = false
    private var currentUtteranceId: String? = null

    var onSpeakingStateChanged: ((Boolean) -> Unit)? = null
    var duckAudioOnTts: Boolean = true
    private var currentAudioFocusRequest: AudioFocusRequest? = null

    init {
        val defLang = getDefaultCountryLanguageCode()
        selectedGlobalLanguageCode = defLang
        normalProfile.languageCode = defLang
        moderatorProfile.languageCode = defLang
        ownerProfile.languageCode = defLang
        superChatProfile.languageCode = defLang
        loadPreferences()
        tts = TextToSpeech(context.applicationContext, this)
    }

    fun syncLanguageWithApp(code: String) {
        val targetCode = supportedLanguages.find { it.code.equals(code, ignoreCase = true) }?.code
            ?: supportedLanguages.find { it.code != "all" && it.code.lowercase().startsWith(code.substringBefore("-").lowercase()) }?.code
            ?: getDefaultCountryLanguageCode()
        setGlobalLanguage(targetCode)
        normalProfile.languageCode = targetCode
        normalProfile.voiceName = null
        moderatorProfile.languageCode = targetCode
        moderatorProfile.voiceName = null
        ownerProfile.languageCode = targetCode
        ownerProfile.voiceName = null
        superChatProfile.languageCode = targetCode
        superChatProfile.voiceName = null
        saveProfiles()
    }

    fun loadPreferences() {
        try {
            val defLang = getDefaultCountryLanguageCode()
            val isMigratedToCountryDefault = prefs.getBoolean("migrated_to_country_default_v5", false)
            if (!isMigratedToCountryDefault) {
                selectedGlobalLanguageCode = defLang
                selectedGlobalVoiceName = null
                normalProfile.languageCode = defLang
                normalProfile.voiceName = null
                moderatorProfile.languageCode = defLang
                moderatorProfile.voiceName = null
                ownerProfile.languageCode = defLang
                ownerProfile.voiceName = null
                superChatProfile.languageCode = defLang
                superChatProfile.voiceName = null
                prefs.edit()
                    .putString("global_lang_code", defLang)
                    .putString("global_voice_name", null)
                    .putBoolean("migrated_to_country_default_v5", true)
                    .apply()
                saveProfiles()
            } else {
                val savedLang = prefs.getString("global_lang_code", defLang) ?: defLang
                selectedGlobalLanguageCode = if (savedLang == "all") defLang else savedLang
                selectedGlobalVoiceName = prefs.getString("global_voice_name", null)
                loadProfile("normal", normalProfile)
                loadProfile("moderator", moderatorProfile)
                loadProfile("owner", ownerProfile)
                loadProfile("superchat", superChatProfile)
                if (normalProfile.languageCode == "all") normalProfile.languageCode = defLang
                if (moderatorProfile.languageCode == "all") moderatorProfile.languageCode = defLang
                if (ownerProfile.languageCode == "all") ownerProfile.languageCode = defLang
                if (superChatProfile.languageCode == "all") superChatProfile.languageCode = defLang
            }

            isEnabled = prefs.getBoolean("global_is_enabled", true)
            dynamicSpeed = prefs.getBoolean("global_dynamic_speed", true)
            readEmoji = prefs.getBoolean("global_read_emoji", true)
            skipUrls = prefs.getBoolean("global_skip_urls", true)
            onlySuperChat = prefs.getBoolean("global_only_super_chat", false)
            duckAudioOnTts = prefs.getBoolean("global_duck_audio", true)
            maxMessageLength = prefs.getInt("global_max_length", 120)
            queueThreshold = prefs.getInt("global_queue_threshold", 4)
            protectVipInQueue = prefs.getBoolean("global_protect_vip", true)
            ignoreCommandPrefix = prefs.getBoolean("global_ignore_commands", true)

            val templateStr = prefs.getString("global_format_template", TtsFormatTemplate.AUTHOR_AND_MESSAGE.name)
            formatTemplate = try {
                TtsFormatTemplate.valueOf(templateStr ?: TtsFormatTemplate.AUTHOR_AND_MESSAGE.name)
            } catch (_: Exception) {
                TtsFormatTemplate.AUTHOR_AND_MESSAGE
            }

            val savedBlacklist = prefs.getStringSet("global_blacklist_words", null)
            if (savedBlacklist != null) {
                filterBlacklistWords.clear()
                filterBlacklistWords.addAll(savedBlacklist)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TTS preferences", e)
        }
    }

    private fun loadProfile(key: String, profile: VoiceProfileConfig) {
        profile.languageCode = prefs.getString("profile_${key}_lang", "all") ?: "all"
        profile.voiceName = prefs.getString("profile_${key}_voice", null)
        profile.pitch = prefs.getFloat("profile_${key}_pitch", profile.pitch)
        profile.speed = prefs.getFloat("profile_${key}_speed", profile.speed)
        profile.volume = prefs.getFloat("profile_${key}_volume", profile.volume)
    }

    fun saveGlobalPreferences() {
        try {
            prefs.edit()
                .putString("global_lang_code", selectedGlobalLanguageCode)
                .putString("global_voice_name", selectedGlobalVoiceName)
                .putBoolean("global_is_enabled", isEnabled)
                .putBoolean("global_dynamic_speed", dynamicSpeed)
                .putBoolean("global_read_emoji", readEmoji)
                .putBoolean("global_skip_urls", skipUrls)
                .putBoolean("global_only_super_chat", onlySuperChat)
                .putBoolean("global_duck_audio", duckAudioOnTts)
                .putString("global_format_template", formatTemplate.name)
                .putInt("global_max_length", maxMessageLength)
                .putInt("global_queue_threshold", queueThreshold)
                .putBoolean("global_protect_vip", protectVipInQueue)
                .putBoolean("global_ignore_commands", ignoreCommandPrefix)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving global TTS preferences", e)
        }
    }

    fun saveProfiles() {
        try {
            val editor = prefs.edit()
            saveProfileToEditor(editor, "normal", normalProfile)
            saveProfileToEditor(editor, "moderator", moderatorProfile)
            saveProfileToEditor(editor, "owner", ownerProfile)
            saveProfileToEditor(editor, "superchat", superChatProfile)
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile TTS preferences", e)
        }
    }

    fun saveBlacklistPreferences() {
        prefs.edit().putStringSet("global_blacklist_words", filterBlacklistWords.toSet()).apply()
    }

    fun addBlacklistWord(word: String) {
        val clean = word.trim().lowercase()
        if (clean.isNotBlank() && !filterBlacklistWords.any { it.equals(clean, ignoreCase = true) }) {
            filterBlacklistWords.add(clean)
            saveBlacklistPreferences()
        }
    }

    fun removeBlacklistWord(word: String) {
        val clean = word.trim().lowercase()
        filterBlacklistWords.removeAll { it.equals(clean, ignoreCase = true) }
        saveBlacklistPreferences()
    }

    fun resetBlacklistWords() {
        filterBlacklistWords.clear()
        filterBlacklistWords.addAll(listOf("kasar", "anjing", "babi", "kontol", "memek", "tolol", "bangsat"))
        saveBlacklistPreferences()
    }

    private fun saveProfileToEditor(editor: android.content.SharedPreferences.Editor, key: String, profile: VoiceProfileConfig) {
        editor.putString("profile_${key}_lang", profile.languageCode)
        editor.putString("profile_${key}_voice", profile.voiceName)
        editor.putFloat("profile_${key}_pitch", profile.pitch)
        editor.putFloat("profile_${key}_speed", profile.speed)
        editor.putFloat("profile_${key}_volume", profile.volume)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val initialLang = getLocaleForCode(selectedGlobalLanguageCode)
            val result = tts?.setLanguage(initialLang)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language $selectedGlobalLanguageCode not supported, falling back to default")
                tts?.setLanguage(Locale.getDefault())
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting audio attributes on TTS", e)
            }

            try {
                allVoices = tts?.voices?.sortedBy { it.name } ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching voices", e)
            }

            // Restore saved global voice if present
            if (selectedGlobalVoiceName != null) {
                val voice = allVoices.find { it.name == selectedGlobalVoiceName }
                if (voice != null) {
                    try {
                        tts?.voice = voice
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed applying saved voice $selectedGlobalVoiceName", e)
                    }
                }
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    onSpeakingStateChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    speakNextInQueue()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TAG, "TTS utterance error code $errorCode for $utteranceId, advancing queue")
                    isSpeaking = false
                    speakNextInQueue()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    speakNextInQueue()
                }
            })

            isInitialized = true
            Log.d(TAG, "TTS Initialized with ${allVoices.size} voices available on system")
        } else {
            Log.e(TAG, "Failed to initialize TTS")
        }
    }

    fun getLocaleForCode(code: String): Locale {
        if (code.equals("all", ignoreCase = true)) return Locale("id", "ID")
        return supportedLanguages.find { it.code.equals(code, ignoreCase = true) }?.locale
            ?: Locale("id", "ID")
    }

    fun getVoicesForLanguage(langCode: String): List<Voice> {
        if (langCode.equals("all", ignoreCase = true)) return emptyList()
        val targetLocale = getLocaleForCode(langCode)
        val targetLang = targetLocale.language.lowercase()
        return allVoices.filter { voice ->
            val vLang = voice.locale.language.lowercase()
            vLang == targetLang || (targetLang == "id" && vLang == "in")
        }
    }

    fun setGlobalLanguage(code: String) {
        selectedGlobalLanguageCode = code
        if (!code.equals("all", ignoreCase = true)) {
            val loc = getLocaleForCode(code)
            try {
                tts?.language = loc
            } catch (e: Exception) {
                Log.e(TAG, "Error setting global language", e)
            }
            if (selectedGlobalVoiceName != null) {
                val validVoices = getVoicesForLanguage(code)
                if (validVoices.none { it.name == selectedGlobalVoiceName }) {
                    selectedGlobalVoiceName = null
                }
            }
        } else {
            selectedGlobalVoiceName = null
        }
        saveGlobalPreferences()
    }

    fun setGlobalVoice(voiceName: String?) {
        if (selectedGlobalLanguageCode.equals("all", ignoreCase = true)) {
            selectedGlobalVoiceName = null
            saveGlobalPreferences()
            return
        }
        selectedGlobalVoiceName = voiceName
        if (voiceName != null) {
            val v = allVoices.find { it.name == voiceName }
            if (v != null) {
                try {
                    tts?.voice = v
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying global voice", e)
                }
            }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val loc = getLocaleForCode(selectedGlobalLanguageCode)
                    tts?.defaultVoice?.let { defVoice ->
                        if (defVoice.locale.language.equals(loc.language, ignoreCase = true)) {
                            tts?.voice = defVoice
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        saveGlobalPreferences()
    }

    fun enqueue(chat: LiveChatMessage) {
        if (!isEnabled || !isInitialized) return

        // 1. "Hanya Obrolan Super" mode
        if (onlySuperChat && chat.type != EventType.SUPER_CHAT) {
            return
        }

        // 2. Command filter (!song, !rules)
        if (ignoreCommandPrefix && chat.message.trimStart().startsWith("!")) {
            return
        }

        // 3. Blacklist filter
        val lowerMessage = chat.message.lowercase()
        if (filterBlacklistWords.any { badWord -> lowerMessage.contains(badWord.lowercase()) }) {
            return
        }

        // 4. Superchat priority: interrupt immediately
        if (chat.type == EventType.SUPER_CHAT) {
            stopCurrentAndClear()
            queue.add(chat)
            speakNextInQueue()
            return
        }

        // 5. Delay Prevention (Lewati ukuran antrean ambang batas)
        if (queue.size >= queueThreshold) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                val isVip = item.type == EventType.SUPER_CHAT || item.isOwner || item.isModerator || item.isMember
                if (!protectVipInQueue || !isVip) {
                    iterator.remove()
                    break
                }
            }
        }

        queue.add(chat)
        if (!isSpeaking) {
            speakNextInQueue()
        }
    }

    @Synchronized
    private fun speakNextInQueue() {
        if (!isEnabled || !isInitialized) {
            isSpeaking = false
            currentUtteranceId = null
            releaseTtsAudioFocus()
            onSpeakingStateChanged?.invoke(false)
            return
        }
        val nextChat = queue.poll()
        if (nextChat == null) {
            isSpeaking = false
            currentUtteranceId = null
            releaseTtsAudioFocus()
            onSpeakingStateChanged?.invoke(false)
            return
        }

        isSpeaking = true
        onSpeakingStateChanged?.invoke(true)

        // 1. Pick Voice Profile based on user role
        val profile = when {
            nextChat.type == EventType.SUPER_CHAT -> superChatProfile
            nextChat.isOwner -> ownerProfile
            nextChat.isModerator -> moderatorProfile
            else -> normalProfile
        }

        // 2. Format and clean text (replacements, emoji, url, length)
        val textToSpeak = formatChatText(nextChat)

        // 3. Determine Language (Auto-Detect or Specific)
        val profileLang = if (profile.languageCode.isNotEmpty()) profile.languageCode else selectedGlobalLanguageCode
        if (profileLang.equals("all", ignoreCase = true)) {
            LanguageDetector.detectLanguage(nextChat.message, defaultFallbackCode = "id-ID") { detectedCode ->
                executeSpeak(profile, detectedCode, textToSpeak, isAutoDetected = true)
            }
        } else {
            executeSpeak(profile, profileLang, textToSpeak, isAutoDetected = false)
        }
    }

    private fun executeSpeak(
        profile: VoiceProfileConfig,
        langCode: String,
        textToSpeak: String,
        isAutoDetected: Boolean
    ) {
        if (!isEnabled || !isInitialized) {
            isSpeaking = false
            currentUtteranceId = null
            releaseTtsAudioFocus()
            onSpeakingStateChanged?.invoke(false)
            return
        }

        val locale = getLocaleForCode(langCode)
        try {
            tts?.language = locale
        } catch (_: Exception) {}

        val voiceToUse = if (isAutoDetected) null else (profile.voiceName ?: selectedGlobalVoiceName)
        if (voiceToUse != null) {
            val voice = allVoices.find { it.name == voiceToUse }
            if (voice != null) {
                try {
                    tts?.voice = voice
                } catch (_: Exception) {}
            }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val matchingVoice = allVoices.firstOrNull {
                        it.locale.language.equals(locale.language, ignoreCase = true) && !it.isNetworkConnectionRequired
                    } ?: allVoices.firstOrNull {
                        it.locale.language.equals(locale.language, ignoreCase = true)
                    } ?: tts?.defaultVoice
                    if (matchingVoice != null && matchingVoice.locale.language.equals(locale.language, ignoreCase = true)) {
                        tts?.voice = matchingVoice
                    }
                }
            } catch (_: Exception) {}
        }

        // Dynamic speed adjustment if queue is backing up
        val effectiveSpeed = if (dynamicSpeed && queue.size > 2) {
            (profile.speed * 1.25f).coerceAtMost(2.0f)
        } else {
            profile.speed
        }

        tts?.setPitch(profile.pitch)
        tts?.setSpeechRate(effectiveSpeed)

        val utteranceId = "msg_${System.currentTimeMillis()}"
        currentUtteranceId = utteranceId

        requestTtsAudioFocus()

        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, profile.volume.coerceIn(0.1f, 1.0f))
        params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())

        // Watchdog: If onDone is not fired within 12 seconds, auto advance queue
        mainHandler.postDelayed({
            if (isSpeaking && currentUtteranceId == utteranceId) {
                Log.w(TAG, "TTS watchdog: utterance $utteranceId timed out, auto advancing queue")
                isSpeaking = false
                speakNextInQueue()
            }
        }, 12000L)

        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun requestTtsAudioFocus() {
        if (!duckAudioOnTts) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                currentAudioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (_: Exception) {}
    }

    private fun releaseTtsAudioFocus() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                currentAudioFocusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
                currentAudioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun formatChatText(chat: LiveChatMessage): String {
        var msg = chat.message

        // 1. Skip URLs if enabled
        if (skipUrls) {
            msg = msg.replace(Regex("""https?://\S+"""), "tautan")
        }

        // 2. Apply Word Replacements (Kamus Penggantian Kata)
        if (isWordReplacementEnabled) {
            for (rule in wordReplacements) {
                if (rule.isEnabled && rule.targetWord.isNotBlank()) {
                    val pattern = "(?i)\\b" + Pattern.quote(rule.targetWord.trim()) + "\\b"
                    msg = msg.replace(Regex(pattern), rule.replacement.trim())
                }
            }
        }

        // 3. Emoji filter
        if (!readEmoji) {
            msg = msg.replace(Regex("""[\p{So}\p{Cn}]"""), "")
        } else {
            msg = limitEmojis(msg, maxEmojiCount)
        }

        // 4. Truncate long messages (Batasi Komentar Panjang)
        if (msg.length > maxMessageLength) {
            msg = msg.take(maxMessageLength) + "..."
        }

        if (chat.type == EventType.SYSTEM || chat.message.contains("Terhubung ke live stream", ignoreCase = true)) {
            return "Berhasil Terhubung"
        }

        return when (chat.type) {
            EventType.SYSTEM -> "Berhasil Terhubung"
            EventType.SUPER_CHAT -> {
                val amount = chat.amountFormatted ?: if (chat.isTikTok) "Gift TikTok" else "Super Chat"
                "${chat.authorName} mengirim $amount: $msg"
            }
            EventType.MEMBERSHIP -> {
                if (chat.isTikTok) {
                    "${chat.authorName} membagikan siaran langsung!"
                } else {
                    "${chat.authorName} bergabung menjadi member! $msg"
                }
            }
            EventType.SUBSCRIBER -> {
                if (chat.isTikTok) {
                    "${chat.authorName} telah follow!"
                } else {
                    "${chat.authorName} baru saja subscribe!"
                }
            }
            EventType.LIKE -> {
                "${chat.authorName} menyukai siaran langsung!"
            }
            else -> {
                when (formatTemplate) {
                    TtsFormatTemplate.AUTHOR_AND_MESSAGE -> "${chat.authorName}: $msg"
                    TtsFormatTemplate.AUTHOR_SAYS -> "${chat.authorName} berkata $msg"
                    TtsFormatTemplate.MESSAGE_ONLY -> msg
                }
            }
        }
    }

    private fun limitEmojis(text: String, maxEmojis: Int): String {
        if (maxEmojis <= 0) return text
        val emojiPattern = Pattern.compile("[\\p{So}\\p{Cn}]")
        val matcher = emojiPattern.matcher(text)
        val sb = StringBuffer()
        var count = 0

        while (matcher.find()) {
            if (count < maxEmojis) {
                count++
            } else {
                matcher.appendReplacement(sb, "")
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    fun playSample(
        pitch: Float,
        speed: Float,
        volume: Float = 1.0f,
        langCode: String = "all",
        voiceName: String? = null,
        sampleText: String? = null
    ) {
        if (!isInitialized) return
        val effectiveLang = if (langCode.equals("all", ignoreCase = true)) {
            val textToCheck = sampleText ?: "Halo, Hello! Mode Semua Bahasa aktif. Suara otomatis mengikuti bahasa komentar."
            LanguageDetector.detectQuickHeuristic(textToCheck) ?: "id-ID"
        } else {
            langCode
        }
        val loc = getLocaleForCode(effectiveLang)
        try {
            tts?.language = loc
        } catch (_: Exception) {}

        if (voiceName != null && !langCode.equals("all", ignoreCase = true)) {
            val v = allVoices.find { it.name == voiceName }
            if (v != null) {
                try {
                    tts?.voice = v
                } catch (_: Exception) {}
            }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val matchingVoice = allVoices.firstOrNull {
                        it.locale.language.equals(loc.language, ignoreCase = true) && !it.isNetworkConnectionRequired
                    } ?: allVoices.firstOrNull {
                        it.locale.language.equals(loc.language, ignoreCase = true)
                    } ?: tts?.defaultVoice
                    if (matchingVoice != null && matchingVoice.locale.language.equals(loc.language, ignoreCase = true)) {
                        tts?.voice = matchingVoice
                    }
                }
            } catch (_: Exception) {}
        }

        tts?.setPitch(pitch)
        tts?.setSpeechRate(speed)

        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.1f, 1.0f))

        val text = sampleText ?: supportedLanguages.find { it.code == langCode }?.sampleText ?: "Halo, Hello! Mode Semua Bahasa aktif."
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sample_${System.currentTimeMillis()}")
    }

    fun speakSample(text: String) {
        if (!isInitialized) return
        val langCode = if (selectedGlobalLanguageCode.equals("all", ignoreCase = true)) {
            LanguageDetector.detectQuickHeuristic(text) ?: "id-ID"
        } else {
            selectedGlobalLanguageCode
        }
        val loc = getLocaleForCode(langCode)
        try {
            tts?.language = loc
        } catch (_: Exception) {}
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sample_alert_${System.currentTimeMillis()}")
    }

    fun stopCurrentAndClear() {
        queue.clear()
        tts?.stop()
        isSpeaking = false
        currentUtteranceId = null
        releaseTtsAudioFocus()
        onSpeakingStateChanged?.invoke(false)
    }

    fun release() {
        queue.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isSpeaking = false
        currentUtteranceId = null
        releaseTtsAudioFocus()
        onSpeakingStateChanged?.invoke(false)
    }
}
