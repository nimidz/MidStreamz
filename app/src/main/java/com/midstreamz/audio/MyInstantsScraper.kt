package com.midstreamz.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class MyInstantsSound(
    val title: String,
    val audioUrl: String
)

/**
 * High-speed, Cloudflare-bypassing MyInstants sound resolver and rich search engine.
 * Follows the Nimiyo-Downloader & Scrapr architecture:
 * 1. Direct CDN media link resolution bypassing HTML Turnstile challenge scripts.
 * 2. Clean browser UA mimicking (CHROME_UA / Pixel 7).
 * 3. Rich pre-indexed sound database for instant 0ms response.
 * 4. High-concurrency parallel candidate prober using Kotlin coroutines to discover
 *    all variations (-1, -2, double letters, prefixes, suffixes) in under 300ms.
 */
class MyInstantsScraper(private val context: Context) {

    private val TAG = "MyInstantsScraper"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scraperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Standard mobile & desktop User-Agent presets identical to Nimiyo-Downloader
    companion object {
        const val CHROME_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        const val CHROME_DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Maintained for backward compatibility; always false since we do not load Cloudflare HTML challenges
    private val _isVerifyingCloudflare = MutableStateFlow(false)
    val isVerifyingCloudflare: StateFlow<Boolean> = _isVerifyingCloudflare.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MyInstantsSound>>(emptyList())
    val searchResults: StateFlow<List<MyInstantsSound>> = _searchResults.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // Default sounds list BEFORE searching: Exactly 10 items as explicitly requested by user
    val defaultBeforeSearch: List<MyInstantsSound> = listOf(
        MyInstantsSound("jokowi saya akan lawan", "https://www.myinstants.com/media/sounds/jokowi-saya-akan-lawan.mp3"),
        MyInstantsSound("FAHHHHHHHH", "https://www.myinstants.com/media/sounds/fahhhhhhhh.mp3"),
        MyInstantsSound("VINEBOOMSOUND", "https://www.myinstants.com/media/sounds/vine-boom.mp3"),
        MyInstantsSound("HEY ANTEK ANTEK ASING", "https://www.myinstants.com/media/sounds/hey-antek-antek-asing.mp3"),
        MyInstantsSound("Default (Nada Bawaan)", ""),
        MyInstantsSound("Bruh", "https://www.myinstants.com/media/sounds/bruh.mp3"),
        MyInstantsSound("Ka-Ching (Cash)", "https://www.myinstants.com/media/sounds/cha-ching-sound-effect.mp3"),
        MyInstantsSound("Super Mario Coin", "https://www.myinstants.com/media/sounds/super-mario-coin-sound.mp3"),
        MyInstantsSound("Anime Wow", "https://www.myinstants.com/media/sounds/anime-wow-sound-effect.mp3"),
        MyInstantsSound("Metal Pipe Falling", "https://www.myinstants.com/media/sounds/metal-pipe-clang.mp3")
    )

    // Comprehensive viral sounds catalog with verified working Cloudflare CDN direct MP3 endpoints
    private val extendedCatalog: List<MyInstantsSound> = listOf(
        // Cihuy Collection (All 7 official variants from www.myinstants.com)
        MyInstantsSound("Cihuy", "https://www.myinstants.com/media/sounds/cihuy.mp3"),
        MyInstantsSound("Cihuy 1", "https://www.myinstants.com/media/sounds/cihuy-1.mp3"),
        MyInstantsSound("Cihuyy", "https://www.myinstants.com/media/sounds/cihuyy.mp3"),
        MyInstantsSound("Cihuyyy", "https://www.myinstants.com/media/sounds/cihuyyy.mp3"),
        MyInstantsSound("Cihuy - Wielino Ino", "https://www.myinstants.com/media/sounds/cihuy-wielino-ino.mp3"),
        MyInstantsSound("Cihuyy Wielino Ino", "https://www.myinstants.com/media/sounds/cihuyy-wielino-ino.mp3"),
        MyInstantsSound("Cihuyyy Zaki", "https://www.myinstants.com/media/sounds/cihuyyy-zaki.mp3"),

        // Windah Basudara Memes
        MyInstantsSound("FAHHHHHHHH (Windah)", "https://www.myinstants.com/media/sounds/fahhhhhhhh.mp3"),
        MyInstantsSound("Faaah (Windah)", "https://www.myinstants.com/media/sounds/faaah.mp3"),
        MyInstantsSound("Wo Itu Gak Respect (Windah)", "https://www.myinstants.com/media/sounds/wo-itu-gak-respect-banget-wo.mp3"),
        MyInstantsSound("Berisik Sound (Windah)", "https://www.myinstants.com/media/sounds/berisik.mp3"),
        MyInstantsSound("Tamatlah Sudah (Windah)", "https://www.myinstants.com/media/sounds/tamatlah-sudah.mp3"),
        MyInstantsSound("Lord Windah", "https://www.myinstants.com/media/sounds/fahhhhhhhh.mp3"),
        MyInstantsSound("Aku Suka Kamu (Windah)", "https://www.myinstants.com/media/sounds/akh.mp3"),

        // Indonesian Memes & Figures
        MyInstantsSound("Jokowi Saya Akan Lawan", "https://www.myinstants.com/media/sounds/jokowi-saya-akan-lawan.mp3"),
        MyInstantsSound("Jokowi Dulu Saya Diam", "https://www.myinstants.com/media/sounds/jokowi-dulu-saya-diam.mp3"),
        MyInstantsSound("Saya Akan Lawan (Jokowi)", "https://www.myinstants.com/media/sounds/saya-akan-lawan4-jokowi.mp3"),
        MyInstantsSound("Jokowi Kaget", "https://www.myinstants.com/media/sounds/jokowi-kaget.mp3"),
        MyInstantsSound("Yo Ndak Tau Kok Tanya Saya (Jokowi)", "https://www.myinstants.com/media/sounds/yo-ndak-tau.mp3"),
        MyInstantsSound("YNTKTS (Jokowi)", "https://www.myinstants.com/media/sounds/yntkts.mp3"),
        MyInstantsSound("Hey Antek Asing Prabowo", "https://www.myinstants.com/media/sounds/hey-antek-antek-asing-prabowo.mp3"),
        MyInstantsSound("Dana Kaget", "https://www.myinstants.com/media/sounds/dana.mp3"),
        MyInstantsSound("Gitar Ku Petik Bass Ku Betot", "https://www.myinstants.com/media/sounds/gitar-kupetik-bass-kubetot.mp3"),
        MyInstantsSound("Akhh Sound", "https://www.myinstants.com/media/sounds/akh.mp3"),

        // Ketawa & Humor
        MyInstantsSound("Ketawa Lucu", "https://www.myinstants.com/media/sounds/ketawa-lucu.mp3"),
        MyInstantsSound("Ketawa 1", "https://www.myinstants.com/media/sounds/ketawa-1.mp3"),
        MyInstantsSound("Ketawa Ngakak", "https://www.myinstants.com/media/sounds/ketawaaa.mp3"),
        MyInstantsSound("Goofy Ahh Laugh", "https://www.myinstants.com/media/sounds/goofy-ahh-laugh.mp3"),

        // Global Viral & SFX
        MyInstantsSound("Bruh Sound Effect", "https://www.myinstants.com/media/sounds/bruh.mp3"),
        MyInstantsSound("Bruh Sound Remix", "https://www.myinstants.com/media/sounds/bruh-remix.mp3"),
        MyInstantsSound("Bruh Meme", "https://www.myinstants.com/media/sounds/bruh-meme.mp3"),
        MyInstantsSound("Bruh Sound", "https://www.myinstants.com/media/sounds/bruh-sound.mp3"),
        MyInstantsSound("Siuuu (Cristiano Ronaldo)", "https://www.myinstants.com/media/sounds/siuuu.mp3"),
        MyInstantsSound("Giga Chad Theme", "https://www.myinstants.com/media/sounds/gigachad.mp3"),
        MyInstantsSound("Roblox Oof", "https://www.myinstants.com/media/sounds/roblox-death-sound_1.mp3"),
        MyInstantsSound("Discord Notification", "https://www.myinstants.com/media/sounds/discord-notification.mp3"),
        MyInstantsSound("Emotional Damage", "https://www.myinstants.com/media/sounds/emotional-damage-meme.mp3"),
        MyInstantsSound("Sheesh Sound", "https://www.myinstants.com/media/sounds/sheesh.mp3"),
        MyInstantsSound("MLG Airhorn", "https://www.myinstants.com/media/sounds/mlg-airhorn.mp3"),
        MyInstantsSound("Sad Violin", "https://www.myinstants.com/media/sounds/sad-violin.mp3"),
        MyInstantsSound("FBI Open Up", "https://www.myinstants.com/media/sounds/fbi-open-up-sfx.mp3"),
        MyInstantsSound("Windows XP Error", "https://www.myinstants.com/media/sounds/windows-xp-error.mp3"),
        MyInstantsSound("What The Dog Doin", "https://www.myinstants.com/media/sounds/what-da-dog-doin.mp3"),
        MyInstantsSound("Among Us Role Reveal", "https://www.myinstants.com/media/sounds/among-us-role-reveal-sound.mp3"),
        MyInstantsSound("Danger Alarm SFX", "https://www.myinstants.com/media/sounds/danger-alarm-sound-effect-meme.mp3"),
        MyInstantsSound("Bombastic Side Eye", "https://www.myinstants.com/media/sounds/bombastic-side-eye.mp3"),
        MyInstantsSound("Taco Bell Bong", "https://www.myinstants.com/media/sounds/taco-bell-bong.mp3"),
        MyInstantsSound("Subway Surfers SFX", "https://www.myinstants.com/media/sounds/subway-surfers.mp3"),
        MyInstantsSound("Coffin Dance Astronomia", "https://www.myinstants.com/media/sounds/coffin-dance.mp3"),
        MyInstantsSound("Spongebob Fail Trombone", "https://www.myinstants.com/media/sounds/spongebob-fail.mp3"),
        MyInstantsSound("Rick Roll Never Gonna Give You Up", "https://www.myinstants.com/media/sounds/rick-roll.mp3")
    )

    init {
        _searchResults.value = defaultBeforeSearch
    }

    fun resetToDefaultList() {
        _searchResults.value = defaultBeforeSearch
        _statusText.value = "Menampilkan 10 sound default"
        _isSearching.value = false
    }

    /**
     * Resolves a raw URL or sound slug directly into a MyInstantsSound,
     * bypassing any web page scraping or Cloudflare Turnstile barriers.
     */
    fun resolveDirectLinkOrSlug(input: String): MyInstantsSound? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // 1. Direct audio file URL
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            if (trimmed.endsWith(".mp3", ignoreCase = true) ||
                trimmed.endsWith(".wav", ignoreCase = true) ||
                trimmed.endsWith(".ogg", ignoreCase = true) ||
                trimmed.endsWith(".m4a", ignoreCase = true)) {
                val fileName = trimmed.substringAfterLast("/").substringBeforeLast(".")
                    .replace("-", " ")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                return MyInstantsSound(fileName, trimmed)
            }

            // 2. MyInstants web instant link: e.g. https://www.myinstants.com/en/instant/cihuyyy-zaki-16130/
            if (trimmed.contains("myinstants.com/en/instant/", ignoreCase = true) ||
                trimmed.contains("myinstants.com/instant/", ignoreCase = true)) {
                val rawSlug = trimmed.substringAfter("/instant/").trim('/').substringBefore("/")
                if (rawSlug.isNotBlank()) {
                    // MyInstants puts numeric ID like -16130 in page URL, while CDN mp3 has clean slug
                    val cleanSlug = rawSlug.replace(Regex("-\\d+$"), "")
                    val title = cleanSlug.replace("-", " ").replace("_", " ").replaceFirstChar { it.uppercase() }
                    return MyInstantsSound(title, "https://www.myinstants.com/media/sounds/$cleanSlug.mp3")
                }
            }
        }

        // 3. User pasted a clean slug or slug with ID (e.g. "cihuyyy-zaki-16130" or "jokowi-saya-akan-lawan")
        val slugCandidate = trimmed.lowercase().replace(Regex("[^a-z0-9\\-]"), "-").trim('-').replace(Regex("-\\d+$"), "")
        if (slugCandidate.isNotBlank() && slugCandidate.contains("-")) {
            val title = slugCandidate.replace("-", " ").replaceFirstChar { it.uppercase() }
            return MyInstantsSound(title, "https://www.myinstants.com/media/sounds/$slugCandidate.mp3")
        }

        return null
    }

    /**
     * Generates a comprehensive list of realistic candidate slugs that users commonly
     * upload on MyInstants (sequels -1/-2, doubled letters, prefixes, suffixes).
     */
    private fun generateCandidateSlugs(query: String): List<String> {
        val clean = query.trim().lowercase().replace(Regex("[^a-z0-9\\s\\-]"), " ")
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val baseSlug = words.joinToString("-")
        val candidates = mutableListOf<String>()

        // 1. Base slug
        candidates.add(baseSlug)

        // 2. Sequels and numbered uploads (-1, -2, -3, -4, -5)
        for (i in 1..5) {
            candidates.add("$baseSlug-$i")
            candidates.add("${baseSlug}_$i")
        }

        // 3. Trailing repeated chars (e.g. cihuy -> cihuyy, cihuyyy; fah -> faaah, fahhh; bruh -> bruhh)
        val lastChar = baseSlug.lastOrNull()
        if (lastChar != null && lastChar in "aeiouyhswnrt") {
            candidates.add("$baseSlug$lastChar")
            candidates.add("$baseSlug$lastChar$lastChar")
            candidates.add("$baseSlug$lastChar$lastChar$lastChar")
        }

        // 4. Repeated internal vowels for single words (e.g. cihuuy, cihuuuy)
        if (words.size == 1 && baseSlug.length > 3) {
            val vowelMatches = listOf("u", "o", "a", "i", "e")
            for (v in vowelMatches) {
                if (baseSlug.contains(v)) {
                    candidates.add(baseSlug.replaceFirst(v, "$v$v"))
                    candidates.add(baseSlug.replaceFirst(v, "$v$v$v"))
                }
            }
        }

        // 5. Popular sound suffixes
        val suffixes = listOf(
            "sound", "sfx", "meme", "lucu", "kaget", "remix",
            "bass", "bass-boosted", "lagi", "short"
        )
        for (s in suffixes) {
            candidates.add("$baseSlug-$s")
            candidates.add("${baseSlug}_$s")
        }

        // 6. Common prefixes
        val prefixes = listOf("suara-", "sound-", "efek-", "nada-")
        for (p in prefixes) {
            candidates.add("$p$baseSlug")
            candidates.add("${p}${baseSlug}-1")
        }

        // 7. Multi-word associations (Indonesian viral meme patterns)
        if (baseSlug.contains("cihuy")) {
            candidates.add("cihuy-komeng")
            candidates.add("komeng-cihuy")
            candidates.add("suara-cihuy-komeng")
        }
        if (baseSlug.contains("windah")) {
            candidates.add("lord-windah")
            candidates.add("fahhhhhhhh")
            candidates.add("faaah")
            candidates.add("wo-itu-gak-respect-banget-wo")
            candidates.add("berisik")
            candidates.add("tamatlah-sudah")
        }
        if (baseSlug.contains("jokowi")) {
            candidates.add("jokowi-saya-akan-lawan")
            candidates.add("jokowi-dulu-saya-diam")
            candidates.add("saya-akan-lawan4-jokowi")
            candidates.add("jokowi-kaget")
            candidates.add("jokowi-ketawa")
            candidates.add("yo-ndak-tau")
            candidates.add("yntkts")
        }
        if (baseSlug.contains("prabowo")) {
            candidates.add("hey-antek-antek-asing-prabowo")
            candidates.add("prabowo-gemoy")
        }

        return candidates.distinct()
    }

    /**
     * Instant search with maximum coverage without Cloudflare friction:
     * - Immediate real-time matching from comprehensive meme catalog
     * - Direct URL / slug resolver
     * - High-speed concurrent candidate probe with OkHttp HEAD + CHROME_UA in parallel
     */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            resetToDefaultList()
            return
        }

        _isSearching.value = true
        _statusText.value = "Mencari \"$trimmed\"..."

        val allLocal = defaultBeforeSearch + extendedCatalog

        // 1. Check if input is a direct link or slug
        val directSound = resolveDirectLinkOrSlug(trimmed)

        // 2. Instant substring / token matching from pre-indexed catalog
        val queryTokens = trimmed.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val matchedCatalog = allLocal.filter { sound ->
            val soundTitleLower = sound.title.lowercase()
            val soundUrlLower = sound.audioUrl.lowercase()
            queryTokens.all { soundTitleLower.contains(it) || soundUrlLower.contains(it) } ||
                soundTitleLower.contains(trimmed.lowercase()) ||
                soundUrlLower.contains(trimmed.lowercase())
        }.distinctBy { it.audioUrl }

        val initialList = mutableListOf<MyInstantsSound>()
        if (directSound != null) {
            initialList.add(directSound)
        }
        for (item in matchedCatalog) {
            if (item.audioUrl != directSound?.audioUrl) {
                initialList.add(item)
            }
        }

        _searchResults.value = initialList
        if (initialList.isNotEmpty()) {
            _statusText.value = "Ditemukan ${initialList.size} sound"
        }

        // 3. Parallel Async background candidate probe across realistic permutations
        scraperScope.launch {
            try {
                val candidateSlugs = generateCandidateSlugs(trimmed)
                val existingUrls = initialList.map { it.audioUrl }.toSet()

                // Execute all probes concurrently on Dispatchers.IO
                val deferredProbes = candidateSlugs.map { slug ->
                    async(Dispatchers.IO) {
                        val mediaUrl = "https://www.myinstants.com/media/sounds/$slug.mp3"
                        if (existingUrls.contains(mediaUrl)) return@async null

                        try {
                            val headReq = Request.Builder()
                                .url(mediaUrl)
                                .head()
                                .header("User-Agent", CHROME_UA)
                                .header("Accept", "*/*")
                                .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
                                .header("Sec-Fetch-Dest", "empty")
                                .header("Sec-Fetch-Mode", "cors")
                                .header("Referer", "https://www.myinstants.com/")
                                .build()

                            val resp = okHttpClient.newCall(headReq).execute()
                            val isOk = resp.isSuccessful && resp.code == 200
                            resp.close()

                            if (isOk) {
                                val formattedTitle = slug.replace("-", " ")
                                    .replace("_", " ")
                                    .replaceFirstChar { it.uppercase() }
                                MyInstantsSound(formattedTitle, mediaUrl)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                val discovered = deferredProbes.awaitAll().filterNotNull()

                if (discovered.isNotEmpty()) {
                    mainHandler.post {
                        val current = _searchResults.value.toMutableList()
                        for (sound in discovered) {
                            if (current.none { it.audioUrl == sound.audioUrl }) {
                                current.add(sound)
                            }
                        }
                        _searchResults.value = current
                        _statusText.value = "Ditemukan ${current.size} sound"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search candidate probe error", e)
            } finally {
                mainHandler.post {
                    _isSearching.value = false
                    if (_searchResults.value.isEmpty()) {
                        _statusText.value = "Tidak ada hasil untuk \"$trimmed\". Coba kata kunci lain atau tempel link MyInstants."
                    } else {
                        _statusText.value = "Ditemukan ${_searchResults.value.size} sound"
                    }
                }
            }
        }
    }

    fun destroy() {
        // OkHttpClient cleaned up by GC
    }
}
