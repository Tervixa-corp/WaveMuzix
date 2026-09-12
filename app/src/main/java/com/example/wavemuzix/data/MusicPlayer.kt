package com.example.wavemuzix.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.PlayerMessage
import kotlin.math.abs
import kotlin.math.hypot
import androidx.media3.common.MediaMetadata
import android.net.Uri
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.repository.StreamingResult
import com.example.wavemuzix.repository.YtDescriptionRepository
import com.example.wavemuzix.repository.VideoDetails
import com.example.wavemuzix.repository.AudioStreamRepository
import com.example.wavemuzix.repository.UnifiedMusicRepository
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.wavemuzix.data.SponsorSegment
import androidx.core.content.ContextCompat
import com.example.wavemuzix.data.database.FavoriteSongEntity
import com.example.wavemuzix.data.DataBackupManager
import kotlinx.coroutines.*
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader

import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.utils.NetworkUtils

@OptIn(UnstableApi::class)
object MusicPlayer {
    private var activePlayer: ExoPlayer? = null
    private var inactivePlayer: ExoPlayer? = null
    private var videoPlayer: ExoPlayer? = null
    private var settingsManager: SettingsManager? = null
    private var appContext: Context? = null

    var unifiedRepository: UnifiedMusicRepository? = null
    var cookies: String? = null

    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var isPlaybackError by mutableStateOf(false)
    var playbackErrorMessage by mutableStateOf<String?>(null)
    var isVideoAvailable by mutableStateOf(false)
    var isPlayerViewVisible by mutableStateOf(false)
    var isVideoActuallyPlaying by mutableStateOf(false)
    var currentVideoQuality by mutableStateOf<String?>(null)
    var currentServer by mutableStateOf<String>("Unknown")

    fun createMediaMetadata(song: Song): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumArtist(song.artist)
            .setDisplayTitle(song.title)
            .setSubtitle(song.artist)
            .setAlbumTitle(song.artist)
            .setArtworkUri(if (song.thumbnailUrl.isBlank()) null else Uri.parse(song.thumbnailUrl))
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    }

    private var _currentSong by mutableStateOf<Song?>(null)
    var currentSong: Song?
        get() = _currentSong
        set(value) {
            val oldId = _currentSong?.videoId
            _currentSong = value
            if (value != null) {
                val newId = value.videoId
                // Sync State Clearing: Prevent UI flickering/stale data during transitions
                val cleanName = value.artist.replace(Regex(" - Topic$", RegexOption.IGNORE_CASE), "").trim()
                sanitizedArtistName = cleanName
                
                // CRITICAL: Compute high-res URL immediately to prevent blurry thumbnail flash
                highResThumbnailUrl = getHighResThumbnailUrl(value.thumbnailUrl)
                
                currentSongDurationText = value.durationText
                duration = parseDurationToMs(value.durationText)
                
                // Only reset/restore metadata if the song actually changed
                if (oldId != newId) {
                    videoDetails = null
                    isDetailsLoading = true
                    
                    // Instant restoration from cache to eliminate UI flickering
                    accentColors = accentColorsCache[newId] ?: emptyList()
                    artistAvatarUrl = artistAvatarCache[cleanName]

                    // Lyrics Restoration: Immediate recovery from cache to prevent "Searching..." flicker
                    val cachedLyrics = lyricsRepository.getCachedLyrics(newId)
                    if (cachedLyrics != null) {
                        lyricsText = cachedLyrics
                        lyricsProvider = lyricsRepository.getCachedProvider(newId)
                        isLyricsLoading = false
                    } else {
                        lyricsText = null
                        lyricsProvider = null
                        isLyricsLoading = true
                    }
                }
            } else {
                sanitizedArtistName = ""
                artistAvatarUrl = null
                artistAvatarSource = null
                videoDetails = null
                highResThumbnailUrl = null
                lyricsText = null
                lyricsProvider = null
                isLyricsLoading = false
            }
            
            // Automatically update MediaSession metadata when currentSong changes
            mainScope.launch {
                activePlayer?.let { player ->
                    val song = value ?: return@launch
                    val metadata = createMediaMetadata(song)

                    val currentItem = player.currentMediaItem
                    if (currentItem != null && currentItem.mediaId == song.videoId) {
                        if (currentItem.mediaMetadata.title != metadata.title) {
                            player.replaceMediaItem(
                                player.currentMediaItemIndex,
                                currentItem.buildUpon().setMediaMetadata(metadata).build()
                            )
                        }
                    }
                }
            }
        }

    private val defaultAmbientColors = listOf(
        Color(0xFF6200EE), // Deep Purple
        Color(0xFF03DAC6), // Teal
        Color(0xFFBB86FC), // Light Purple
        Color(0xFF3700B3), // Dark Purple
        Color(0xFF018786)  // Dark Teal
    )

    private var _accentColors by mutableStateOf<List<Color>>(defaultAmbientColors)
    var accentColors: List<Color>
        get() = _accentColors
        set(value) {
            _accentColors = value.ifEmpty { defaultAmbientColors }
        }
    var videoDetails by mutableStateOf<VideoDetails?>(null)
    var artistAvatarUrl by mutableStateOf<String?>(null)
    var artistAvatarSource by mutableStateOf<String?>(null)
    var sanitizedArtistName by mutableStateOf<String>("")
    var isDetailsLoading by mutableStateOf(false)
    var highResThumbnailUrl by mutableStateOf<String?>(null)
    var currentSongDurationText by mutableStateOf<String?>(null)
    var isFavorite by mutableStateOf(false)
    var isDownloaded by mutableStateOf(false)
    private var hasIncrementedPlayCount = false

    var skipSilenceEnabled: Boolean = false
        set(value) {
            field = value
            activePlayer?.skipSilenceEnabled = value
            inactivePlayer?.skipSilenceEnabled = value
        }

    fun toggleFavorite(context: Context, song: Song? = currentSong, isCurrent: Boolean = true) {
        if (song == null) return

        // Update Favorite and Downloaded status
        ioScope.launch {
            val dao = database?.songDao() ?: return@launch
            try {
                val currentlyFavorite = dao.isFavorite(song.videoId)
                if (currentlyFavorite) {
                    dao.deleteFavorite(FavoriteSongEntity(
                        videoId = song.videoId,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                        artistId = song.artistId
                    ))
                    FirebaseSyncManager.removeFavorite(song.videoId)
                } else {
                    val fav = FavoriteSongEntity(
                        videoId = song.videoId,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                        artistId = song.artistId
                    )
                    dao.insertFavorite(fav)
                    FirebaseSyncManager.pushFavorite(context, fav)
                }

                if (isCurrent) {
                    withContext(Dispatchers.Main) {
                        isFavorite = !currentlyFavorite
                    }
                }
                DataBackupManager.backupAll(context)
            } catch (e: Exception) {
                Log.e("MusicPlayer", "Failed to toggle favorite", e)
            }
        }
    }

    fun release() {
        fetchJob?.cancel()
        skipJob?.cancel()
        progressJob?.cancel()
        volumeFadeJob?.cancel()
        sleepTimerJob?.cancel()
        refreshQueueJob?.cancel()

        activePlayer?.release()
        activePlayer = null
        inactivePlayer?.release()
        inactivePlayer = null
        videoPlayer?.release()
        videoPlayer = null

        releaseVisualizer()
        loudnessEnhancer?.release()
        loudnessEnhancer = null

        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null

        clearState()
    }

    fun clearState() {
        currentSong = null
        isPlaying = false
        isLoading = false
        isPlaybackError = false
        playbackErrorMessage = null
        isCrossfadeTriggered = false
        videoDetails = null
        artistAvatarUrl = null
        artistAvatarSource = null
        sanitizedArtistName = ""
        highResThumbnailUrl = null
        currentSongDurationText = null
        accentColors = emptyList() // Resets to defaults
        lyricsText = null
        lyricsProvider = null
        isFavorite = false
        isDownloaded = false
        currentVolumeAmplitude = 0f
        currentBassAmplitude = 0f
        currentTrebleAmplitude = 0f
        videoDetailsCache.clear()
        accentColorsCache.clear()
        artistAvatarCache.clear()
    }

    var lyricsText by mutableStateOf<String?>(null)
    var lyricsProvider by mutableStateOf<String?>(null)
    var isLyricsLoading by mutableStateOf(false)

    val lyricsRepository = com.example.wavemuzix.repository.LyricsRepository()

    val queue = mutableStateListOf<Song>()
    var currentIndex by mutableIntStateOf(-1)
    private var shuffledIndices = mutableListOf<Int>()

    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    private var consecutiveErrors = 0
    private const val MAX_CONSECUTIVE_ERRORS = 3

    private var _shuffleEnabled by mutableStateOf(false)
    var shuffleEnabled: Boolean
        get() = _shuffleEnabled
        set(value) {
            _shuffleEnabled = value
            if (value && queue.isNotEmpty()) {
                generateShuffledIndices()
            }
        }

    private var _repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var repeatMode: Int
        get() = _repeatMode
        set(value) {
            _repeatMode = value
            // Only pass REPEAT_MODE_ONE to ExoPlayer.
            // REPEAT_MODE_ALL is handled manually via our queue and prefetching.
            val exoMode = if (value == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            activePlayer?.repeatMode = exoMode
            inactivePlayer?.repeatMode = exoMode
        }

    var currentVolumeAmplitude by mutableFloatStateOf(0f)
    var currentBassAmplitude by mutableFloatStateOf(0f)
    var currentTrebleAmplitude by mutableFloatStateOf(0f)
    var playbackSpeed by mutableFloatStateOf(1f)

    private var dynamicsProcessing: android.media.audiofx.DynamicsProcessing? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null
    private var reverb: android.media.audiofx.PresetReverb? = null
    private var currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET

    private fun generateShuffledIndices() {
        shuffledIndices = queue.indices.toMutableList()
        shuffledIndices.shuffle()
        // Move current index to front of shuffled list to avoid jumping
        val currentInShuffled = shuffledIndices.indexOf(currentIndex)
        if (currentInShuffled != -1) {
            shuffledIndices.removeAt(currentInShuffled)
            shuffledIndices.add(0, currentIndex)
        }
    }

    var sleepTimerMillis by mutableLongStateOf(0L)
    var stopAfterCurrentSong by mutableStateOf(false)
    private var sleepTimerJob: Job? = null

    private val sponsorSegments = mutableStateListOf<SponsorSegment>()

    var onErrorCallback: ((PlaybackException) -> Unit)? = null
    var onReadyCallback: (() -> Unit)? = null
    var onPlayerChanged: ((ExoPlayer) -> Unit)? = null

    private var progressJob: Job? = null
    private var visualizer: Visualizer? = null
    private var fetchJob: Job? = null
    private var skipJob: Job? = null
    private var lastSkipTime = 0L
    private var isSkipping = false
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var database: com.example.wavemuzix.data.database.AppDatabase? = null
    private var refreshQueueJob: Job? = null

    private val videoDetailsCache = java.util.concurrent.ConcurrentHashMap<String, VideoDetails>()
    private val accentColorsCache = java.util.concurrent.ConcurrentHashMap<String, List<Color>>()
    private val artistAvatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val sponsorSegmentsCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<Long, Long>>>()

    private var prefetchedVideoId: String? = null
    var scheduledVideoId: String? = null
    var scheduledIndex: Int = -1
    var isPrefetching: Boolean by mutableStateOf(false)
    private var currentVisualizerSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var lastSponsorSeekTarget = -1L
    private var cleanPositionAtSkipTrigger = 0L
    private var skipTriggerTime = 0L
    private var lastSponsorSeekAttemptTime = 0L
    private var mergedSponsorSegments: List<Pair<Long, Long>> = emptyList()
    private var isFadingForSkip = false
    private var isCrossfadeTriggered = false
    private var volumeFadeJob: Job? = null
    private var baseVolume = 1.0f
    private var videoSyncStartTime = 0L
    private var currentLoudness: Double? = null

    private var _userVolume = mutableFloatStateOf(1.0f)
    var userVolume: Float
        get() = _userVolume.floatValue
        set(value) {
            _userVolume.floatValue = value
            updatePlayerVolume()
            settingsManager?.userVolume = value
        }

    private var _volumeBoost = mutableFloatStateOf(60.0f)
    var volumeBoost: Float
        get() = 60.0f
        set(value) {
            // No-op for user changes, always 60.0f
        }

    private fun updatePlayerVolume() {
        val vol = userVolume * baseVolume
        activePlayer?.volume = vol
    }

    private fun calculateBaseVolume(loudness: Double?): Float {
        if (settingsManager?.isNormalizeVolume != true) return 1.0f
        val l = loudness ?: 0.0
        val targetLUFS = -14.0
        val diff = targetLUFS - l
        return if (diff < 0) {
            // Attenuate loud tracks exactly to -14 LUFS
            Math.pow(10.0, (diff / 20.0)).toFloat().coerceIn(0.1f, 1.0f)
        } else {
            1.0f
        }
    }

    @OptIn(UnstableApi::class)
    private val httpDataSourceFactory = OkHttpDataSource.Factory(RetrofitClient.getOkHttpClient())
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
        .setDefaultRequestProperties(mapOf(
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "Accept" to "*/*"
        ))

    @OptIn(UnstableApi::class)
    private val dataSourceFactory by lazy {
        androidx.media3.datasource.DefaultDataSource.Factory(
            appContext!!,
            httpDataSourceFactory
        )
    }

    private val ytDescriptionRepository by lazy {
        YtDescriptionRepository()
    }

    fun getHighResThumbnailUrl(url: String?): String? {
        if (url == null) return null

        return when {
            url.contains("i.scdn.co") -> {
                // Spotify high-res images usually use /image/ URL segments. 
                // Ensure we don't truncate them.
                url
            }
            url.contains("e-cdns-images.dzcdn.net") -> {
                // Deezer high-res: Replace size with 1000x1000 if not already XL
                if (url.contains("1000x1000")) url else url.replace(Regex("/\\d+x\\d+/"), "/1000x1000/")
            }
            url.contains("googleusercontent.com") || url.contains("ggpht.com") -> {
                if (url.contains("=")) {
                    url.substringBeforeLast("=") + "=s1080"
                } else {
                    "$url=s1080"
                }
            }
            url.contains("i.ytimg.com") -> {
                url.replace(Regex("(default|hqdefault|mqdefault|sddefault)\\.jpg"), "maxresdefault.jpg")
            }
            else -> url
        }
    }

    private fun fetchMetadata(context: Context, song: Song) {
        val targetVideoId = song.videoId
        val highResUrl = getHighResThumbnailUrl(song.thumbnailUrl)

        // 1. Fetch Video Details and Artist HD Metadata
        ioScope.launch {
            try {
                // Fetch Artist HD Metadata (Spotify -> YTM Fallback) FIRST for better UI priority
                val currentName = sanitizedArtistName
                val cachedAvatar = artistAvatarCache[currentName]
                
                if (cachedAvatar != null) {
                    if (currentSong?.videoId == targetVideoId) {
                        artistAvatarUrl = cachedAvatar
                    }
                } else {
                    unifiedRepository?.let { repo ->
                        val hdResult = repo.getArtistHDMetadata(currentName, cookies)
                        if (hdResult != null) {
                            artistAvatarCache[currentName] = hdResult.artist.thumbnailUrl
                            if (currentSong?.videoId == targetVideoId) {
                                withContext(Dispatchers.Main) {
                                    val hdUrl = getHighResThumbnailUrl(hdResult.artist.thumbnailUrl)
                                    artistAvatarUrl = hdUrl
                                    artistAvatarSource = hdResult.source
                                }
                            }
                        }
                    }
                }

                // Fetch Video Details
                val details = ytDescriptionRepository.fetchVideoDetails(targetVideoId, cookies)
                if (details != null) {
                    videoDetailsCache[targetVideoId] = details
                    if (currentSong?.videoId == targetVideoId) {
                        withContext(Dispatchers.Main) {
                            videoDetails = details
                            // Fallback to channel avatar ONLY if Spotify/HD metadata failed
                            if (artistAvatarUrl == null && details.channelThumbnailUrl.isNotEmpty()) {
                                val hdAvatarUrl = getHighResThumbnailUrl(details.channelThumbnailUrl)
                                artistAvatarUrl = hdAvatarUrl
                                artistAvatarSource = "YouTube"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayer", "Failed to fetch video/artist details", e)
            } finally {
                if (currentSong?.videoId == targetVideoId) {
                    isDetailsLoading = false
                }
            }
        }

        // 2. Update Favorite and Downloaded status
        ioScope.launch {
            val fav = database?.songDao()?.isFavorite(targetVideoId) ?: false
            val down = database?.songDao()?.getDownloadByVideoId(targetVideoId) != null
            withContext(Dispatchers.Main) {
                if (currentSong?.videoId == targetVideoId) {
                    isFavorite = fav
                    isDownloaded = down
                }
            }
        }

        // 3. Extract Palette Colors and Pre-load High-res Image
        mainScope.launch {
            // If already in cache, we still reload Glide to ensure highResThumbnailUrl is updated,
            // but the palette generation is redundant if already cached.
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(highResUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        if (currentSong?.videoId == targetVideoId) {
                            highResThumbnailUrl = highResUrl // Successfully loaded high-res

                            // CRITICAL: Update the actual MediaItem in the player so the notification
                            // gets the high-res art and refreshes its text.
                            activePlayer?.let { player ->
                                val currentItem = player.currentMediaItem
                                if (currentItem != null && currentItem.mediaId == targetVideoId) {
                                    val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                                        .setArtworkUri(Uri.parse(highResUrl))
                                        .build()

                                    // Use replaceMediaItem to force the notification to update
                                    player.replaceMediaItem(
                                        player.currentMediaItemIndex,
                                        currentItem.buildUpon().setMediaMetadata(updatedMetadata).build()
                                    )
                                }
                            }

                            val cachedColors = accentColorsCache[targetVideoId]
                            if (cachedColors != null) {
                                accentColors = cachedColors
                            } else {
                                Palette.from(resource).generate { palette ->
                                    if (currentSong?.videoId == targetVideoId) {
                                        palette?.let { p ->
                                            val colors = mutableListOf<Color>()
                                            
                                            // 1. Dominant / Vibrant
                                            val dominant = p.getVibrantColor(p.getDominantColor(0))
                                            if (dominant != 0) colors.add(Color(dominant))
                                            
                                            // 2. Light Vibrant / Muted Vibrant
                                            val light = p.getLightVibrantColor(p.getLightMutedColor(0))
                                            if (light != 0) colors.add(Color(light))
                                            
                                            // 3. Dark Vibrant / Dark Muted
                                            val dark = p.getDarkVibrantColor(p.getDarkMutedColor(0))
                                            if (dark != 0) colors.add(Color(dark))
                                            
                                            // 4. Secondary Accent
                                            val muted = p.getMutedColor(0)
                                            if (muted != 0 && !colors.contains(Color(muted))) colors.add(Color(muted))

                                            // Filter out near-black
                                            var filteredColors = colors.filter { ColorUtils.calculateLuminance(it.toArgb()) > 0.02 }

                                            // If fewer than 4 colors, generate using HSL shifting
                                            if (filteredColors.size < 4 && filteredColors.isNotEmpty()) {
                                                val baseColor = filteredColors.first().toArgb()
                                                val hsl = FloatArray(3)
                                                ColorUtils.colorToHSL(baseColor, hsl)
                                                
                                                val generated = mutableListOf<Color>()
                                                generated.addAll(filteredColors)
                                                
                                                while (generated.size < 4) {
                                                    hsl[0] = (hsl[0] + 60f) % 360f // Rotate hue
                                                    generated.add(Color(ColorUtils.HSLToColor(hsl)))
                                                }
                                                filteredColors = generated
                                            }
                                            
                                            if (filteredColors.isNotEmpty()) {
                                                accentColorsCache[targetVideoId] = filteredColors
                                                accentColors = filteredColors
                                            } else {
                                                accentColors = defaultAmbientColors
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }

    private fun prefetchMetadata(context: Context, song: Song) {
        val targetVideoId = song.videoId
        // If already cached, don't refetch
        if (videoDetailsCache.containsKey(targetVideoId) && accentColorsCache.containsKey(targetVideoId)) return

        ioScope.launch {
            // 1. Prefetch Video Details
            try {
                if (!videoDetailsCache.containsKey(targetVideoId)) {
                    val details = ytDescriptionRepository.fetchVideoDetails(targetVideoId, cookies)
                    if (details != null) {
                        videoDetailsCache[targetVideoId] = details
                    }
                }
            } catch (e: Exception) {
            }

            // 2. Prefetch Palette/Accent Colors
            if (!accentColorsCache.containsKey(targetVideoId)) {
                val highResUrl = getHighResThumbnailUrl(song.thumbnailUrl)
                withContext(Dispatchers.Main) {
                    Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(highResUrl)
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                Palette.from(resource).generate { palette ->
                                    palette?.let { p ->
                                        val colors = mutableListOf<Color>()
                                        val dominant = p.getVibrantColor(p.getDominantColor(0))
                                        val light = p.getLightVibrantColor(p.getLightMutedColor(0))
                                        val dark = p.getDarkVibrantColor(p.getDarkMutedColor(0))
                                        val muted = p.getMutedColor(0)
                                        
                                        if (dominant != 0) colors.add(Color(dominant))
                                        if (light != 0) colors.add(Color(light))
                                        if (dark != 0) colors.add(Color(dark))
                                        if (muted != 0) colors.add(Color(muted))
                                        
                                        var finalColors = colors.filter { ColorUtils.calculateLuminance(it.toArgb()) > 0.02 }.distinct()
                                        
                                        if (finalColors.size < 4 && finalColors.isNotEmpty()) {
                                            val baseColor = finalColors.first().toArgb()
                                            val hsl = FloatArray(3)
                                            ColorUtils.colorToHSL(baseColor, hsl)
                                            val generated = finalColors.toMutableList()
                                            while (generated.size < 4) {
                                                hsl[0] = (hsl[0] + 60f) % 360f
                                                generated.add(Color(ColorUtils.HSLToColor(hsl)))
                                            }
                                            finalColors = generated
                                        }

                                        accentColorsCache[targetVideoId] = finalColors.ifEmpty { defaultAmbientColors }
                                    }
                                }
                            }
                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun getInstance(context: Context): ExoPlayer {
        val player = activePlayer
        if (player != null) return player

        synchronized(this) {
            if (activePlayer != null) return activePlayer!!

            if (appContext == null) appContext = context.applicationContext
            if (settingsManager == null) {
                settingsManager = SettingsManager(context.applicationContext)
            }
            if (database == null) {
                database = com.example.wavemuzix.data.database.AppDatabase.getDatabase(context.applicationContext)
                // Ensure offline metadata is synced from JSON backups
                mainScope.launch {
                    DownloadManager.syncWithStorage(context.applicationContext)
                }
            }
            if (wifiLock == null) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                wifiLock = wifiManager?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WaveMuzix:WifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            
            val active = createPlayer(context.applicationContext)
            activePlayer = active
            
            // LAZY INITIALIZATION: The inactive and video players are heavy. 
            // We defer them to the Main thread to keep startup snappy.
            // ExoPlayer MUST be created on a thread with a Looper (typically Main).
            mainScope.launch(Dispatchers.Main) {
                if (inactivePlayer == null) {
                    inactivePlayer = createPlayer(context.applicationContext)
                }
                if (videoPlayer == null) {
                    videoPlayer = createVideoPlayer(context.applicationContext)
                }
                
                // Warm up the service in the background if it's not already started.
                // This prepares the MediaSession and Notification early.
                ensureServiceStarted(context.applicationContext)
            }

            // Properly restore user volume via setter to ensure players are updated
            userVolume = settingsManager?.userVolume ?: 1.0f
            volumeBoost = settingsManager?.volumeBoost ?: 1.0f
            
            return active
        }
    }

    fun getVideoPlayer(context: Context): ExoPlayer {
        return videoPlayer ?: synchronized(this) {
            videoPlayer ?: createVideoPlayer(context.applicationContext).also { videoPlayer = it }
        }
    }

    fun getInactivePlayer(context: Context): ExoPlayer {
        return inactivePlayer ?: synchronized(this) {
            inactivePlayer ?: createPlayer(context.applicationContext).also { 
                inactivePlayer = it 
            }
        }
    }

    private fun createVideoPlayer(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20000, // Min buffer to start/resume
                60000, // Max buffer
                2500,  // Buffer for playback start
                5000   // After rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (videoSize.height > 0) {
                            currentVideoQuality = "${videoSize.height}p"
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                            currentVideoQuality = null
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("MusicPlayer", "Video Player Error: ${error.message}", error)
                        isVideoAvailable = false
                    }
                })
            }
    }

    private fun ensureServiceStarted(context: Context) {
        try {
            val intent = Intent(context.applicationContext, PlaybackService::class.java)
            context.applicationContext.startService(intent)
        } catch (e: Exception) {
            Log.e("MusicPlayer", "CRITICAL: Failed to start PlaybackService: ${e.message}")
        }
    }

    private val failedClientsForCurrentSong = mutableListOf<String>()

    @OptIn(UnstableApi::class)
    private fun createPlayer(context: Context): ExoPlayer {
        val settings = SettingsManager(context)

        val audioAttributesBuilder = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)

        if (settings.isDirectVolumeControlEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use reflection or constant if the SDK version in build.gradle is too low for the symbol
            // AudioAttributes.FLAG_AUDIOPROCESSING_CONTROL_BYPASS = 0x200000
            audioAttributesBuilder.setFlags(0x200000)
        }
        val audioAttributes = audioAttributesBuilder.build()

        val loadControlBuilder = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16 * 1024))

        // Optimized buffer settings: High stability with snappy start.
        // We give the player a larger cushion (30s-50s) to prevent tempo jitter 
        // while keeping the start-threshold low (1.5s) for instant response.
        val userConfigBuffer = settings.networkBufferSizeMs
        val minBuffer = if (userConfigBuffer > 0) userConfigBuffer.toInt() else if (settings.isDataSaverEnabled) 20000 else 30000
        val maxBuffer = if (userConfigBuffer > 0) (userConfigBuffer + 20000).toInt() else if (settings.isDataSaverEnabled) 40000 else 60000
        
        loadControlBuilder.setBufferDurationsMs(
            minBuffer,
            maxBuffer,
            if (settings.isDataSaverEnabled) 2500 else 1500,
            if (settings.isDataSaverEnabled) 4000 else 2500
        ).setPrioritizeTimeOverSizeThresholds(true)

        val loadControl = loadControlBuilder.build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableAudioTrackPlaybackParams(true) // Enable hardware params for smoother tempo control

        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context.applicationContext)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .build().apply {
                // Use EXACT seeking for absolute precision as requested
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
                playWhenReady = true
                skipSilenceEnabled = settings.isSkipSilenceEnabled
                playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                this.repeatMode = if (this@MusicPlayer.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        // Prevent ExoPlayer from using REPEAT_MODE_ALL internally as it loops our small pruned queue.
                        // We handle "Repeat All" manually via queue logic.
                        if (repeatMode == Player.REPEAT_MODE_ALL) {
                            this@apply.repeatMode = Player.REPEAT_MODE_OFF
                            this@MusicPlayer.repeatMode = Player.REPEAT_MODE_ALL
                        }
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                        playbackSpeed = playbackParameters.speed
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        if (this@apply == activePlayer) {
                            // Logic: The UI should consider us "playing" if the user has intended to play.
                            // This keeps the wave visualizer moving during skips and buffering.
                            this@MusicPlayer.isPlaying = playWhenReady

                            if (playWhenReady) {
                                try {
                                    if (wifiLock?.isHeld == false) wifiLock?.acquire()
                                } catch (e: Exception) {
                                    Log.e("MusicPlayer", "Failed to acquire WifiLock", e)
                                }
                                startProgressUpdate(context)
                                if (isVideoAvailable && isPlayerViewVisible) {
                                    videoPlayer?.play()
                                }
                            } else {
                                try {
                                    if (wifiLock?.isHeld == true) wifiLock?.release()
                                } catch (e: Exception) {
                                    Log.e("MusicPlayer", "Failed to release WifiLock", e)
                                }
                                stopProgressUpdate()
                                videoPlayer?.pause()
                            }
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        // CRITICAL: Only the active player updates the global currentIndex to prevent sync wars
                        if (this@apply != activePlayer) return

                        val mediaId = mediaItem?.mediaId ?: this@apply.currentMediaItem?.mediaId

                        // Prune played items from the player's internal queue to keep it lean
                        if (this@apply.currentMediaItemIndex > 0) {
                            val count = this@apply.currentMediaItemIndex
                            try {
                                this@apply.removeMediaItems(0, count)
                            } catch (e: Exception) {
                                Log.e("MusicPlayer", "Pruning failed", e)
                            }
                        }

                        // We must update state on the main thread for Compose visibility
                        mainScope.launch {
                            // Nudge Media3 to refresh the notification for automatic transitions
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                                try {
                                    activePlayer?.playlistMetadata = activePlayer?.playlistMetadata ?: MediaMetadata.EMPTY
                                } catch (e: Exception) {}
                            }

                            if (mediaId != null) {
                                // Sync currentServer from the MediaItem tag
                                (mediaItem?.localConfiguration?.tag as? StreamingResult)?.let { result ->
                                    currentServer = result.clientName
                                }

                                // Find the song in the queue to sync details correctly.
                                val nextExpectedIndex = getNextIndex()
                                val index = when {
                                    // 1. Check if it's the song we scheduled via prefetch
                                    scheduledIndex != -1 && queue.getOrNull(scheduledIndex)?.videoId == mediaId -> scheduledIndex
                                    // 2. Check if it's the song we expected to play next anyway
                                    nextExpectedIndex != -1 && queue.getOrNull(nextExpectedIndex)?.videoId == mediaId -> nextExpectedIndex
                                    // 3. Check if it's the current song (e.g. repeat one or manual restart)
                                    currentSong?.videoId == mediaId -> currentIndex
                                    // 4. Search for the mediaId, prioritizing indices "after" the current one in playback order
                                    else -> {
                                        if (shuffleEnabled) {
                                            val currentPos = shuffledIndices.indexOf(currentIndex)
                                            val searchIndices = if (currentPos != -1) shuffledIndices.drop(currentPos + 1) else shuffledIndices
                                            val found = searchIndices.firstOrNull { queue.getOrNull(it)?.videoId == mediaId }
                                            found ?: queue.indexOfFirst { it.videoId == mediaId }
                                        } else {
                                            val firstAfter = (currentIndex + 1 until queue.size).firstOrNull { queue[it].videoId == mediaId }
                                            firstAfter ?: queue.indexOfFirst { it.videoId == mediaId }
                                        }
                                    }
                                }

                                if (index != -1) {
                                    val song = queue[index]
                                    val songChanged = index != currentIndex || currentSong?.videoId != mediaId

                                    if (songChanged) {
                                        currentIndex = index
                                        currentSong = song
                                        scheduledVideoId = null
                                        scheduledIndex = -1
                                        
                                        // Skip clearing lyrics if they are already for this song
                                        // (Fixes flicker on gapless/repeat-one transitions)
                                        if (lyricsRepository.getLastSongId() != mediaId) {
                                            clearLyrics()
                                        }
                                        
                                        hasIncrementedPlayCount = false

                                        // Reset state for new song
                                        lastSponsorSeekTarget = -1L
                                        lastSponsorSeekAttemptTime = 0L
                                        cleanPositionAtSkipTrigger = 0L
                                        skipTriggerTime = 0L

                                        fetchSponsorSegments(mediaId)
                                        fetchMetadata(context, song)
                                        startLyricsFetch(song)
                                        refreshQueueIfLow()
                                        releaseVisualizer()
                                    }
                                }
                            }

                            // SYNC VIDEO PLAYER FOR AUTOMATIC/GAPLESS TRANSITIONS
                            val currentResult = this@apply.currentMediaItem?.localConfiguration?.tag as? StreamingResult
                            
                            // SYNC VOLUME NORMALIZATION & EFFECTS
                            val loudness = currentResult?.loudnessDb
                            baseVolume = calculateBaseVolume(loudness)
                            this@apply.volume = baseVolume * userVolume
                            applyAudioEffects(this@apply, loudness)

                            val hasVideo = currentResult?.videoUrl != null
                            isVideoAvailable = hasVideo && settingsManager?.isPlayVideoIfAvailable == true
                            
                            if (isVideoAvailable && isPlayerViewVisible) {
                                videoPlayer?.let { vPlayer ->
                                    val videoMediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(currentResult!!.videoUrl))
                                        .setMimeType(currentResult.videoMimeType ?: "video/mp4")
                                        .build()
                                    vPlayer.setMediaItem(videoMediaItem)
                                    vPlayer.prepare()
                                    // Optimization: Delay video to allow buffering on slow connections
                                    videoSyncStartTime = System.currentTimeMillis() + 3000
                                    vPlayer.playWhenReady = false
                                    isVideoActuallyPlaying = false
                                    vPlayer.seekTo(this@apply.currentPosition)
                                }
                            } else {
                                videoPlayer?.stop()
                                videoPlayer?.clearMediaItems()
                            }

                            scheduleBackgroundTriggers(context, this@apply)

                            // Proactively prefetch metadata for the next song in queue
                            val nextIdx = getNextIndex()
                            if (nextIdx != -1) {
                                prefetchMetadata(context, queue[nextIdx])
                            }

                            if (settingsManager?.isCrossfadeEnabled == false) {
                                prefetchNextSong(context)
                            } else if (settingsManager?.isCrossfadeEnabled == true && this@apply.mediaItemCount <= 1) {
                                // If crossfade is on but prefetch hasn't added the next item yet,
                                // we should try to prefetch if we are close to the end.
                                // Actually, startCrossfade handles its own item.
                            }

                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !stopAfterCurrentSong) {
                                this@apply.playWhenReady = true
                                this@apply.play()
                                this@MusicPlayer.isPlaying = true
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (this@apply != activePlayer) return

                        this@MusicPlayer.isPlaying = this@apply.playWhenReady

                        if (state == Player.STATE_READY) {
                            applyAudioEffects(this@apply)

                            // Reset skip state and restore volume quickly (100ms fade)
                            if (isFadingForSkip) {
                                isFadingForSkip = false
                                animateVolume(this@apply, baseVolume * userVolume, 100)
                                cleanPositionAtSkipTrigger = 0L
                                skipTriggerTime = 0L
                                lastSponsorSeekAttemptTime = 0L
                                // Restore high-accuracy seeking for normal playback
                                this@apply.setSeekParameters(SeekParameters.EXACT)
                            }

                            consecutiveErrors = 0
                            this@MusicPlayer.isLoading = false
                            setupVisualizer() // Ensure visualizer starts as soon as player is ready
                            scheduleBackgroundTriggers(context, this@apply)

                            // Proactively prefetch metadata for the next song
                            val nextIdx = getNextIndex()
                            if (nextIdx != -1) {
                                prefetchMetadata(context, queue[nextIdx])
                            }

                            skipSilenceEnabled = settingsManager?.isSkipSilenceEnabled ?: false
                            playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                            onReadyCallback?.invoke()

                            if (settingsManager?.isCrossfadeEnabled == false) {
                                prefetchNextSong(context)
                            }
                        } else if (state == Player.STATE_BUFFERING) {
                            // Suppress loader flicker during SponsorBlock skips.
                            // We check if a skip was recently triggered to distinguish between
                            // normal buffering (network) and skip-induced buffering (seeking).
                            val isSkippingSponsor = lastSponsorSeekTarget != -1L &&
                                (System.currentTimeMillis() - lastSponsorSeekAttemptTime < 3000)

                            if (!isSkippingSponsor) {
                                this@MusicPlayer.isLoading = true
                            }
                            startProgressUpdate(context)
                        } else if (state == Player.STATE_ENDED) {
                            if (stopAfterCurrentSong) {
                                stopAfterCurrentSong = false
                                pause()
                            } else {
                                // Logic: If we reached ENDED, the internal player queue is exhausted.
                                // We must force advance to the next song in our logical queue.
                                lastSkipTime = 0L
                                skipJob?.cancel()
                                skipJob = mainScope.launch {
                                    playNextInQueue(context, isAuto = true)
                                }
                                refreshQueueIfLow()
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (this@apply == activePlayer) {
                            val errorCodeName = error.errorCodeName
                            val errorCause = error.cause?.message ?: "Unknown cause"
                            Log.e("MusicPlayer", "Playback Error: $errorCodeName (Code: ${error.errorCode}) - $errorCause", error)

                            // Check internet connectivity
                            val context = appContext ?: return
                            val isConnected = NetworkUtils.isNetworkAvailable(context)

                            if (!isConnected) {
                                Log.e("MusicPlayer", "DIAGNOSTIC: Network unavailable. Connectivity check failed.")
                                this@apply.pause()
                                this@MusicPlayer.isPlaying = false
                                this@MusicPlayer.isLoading = false
                                isPlaybackError = true
                                playbackErrorMessage = "No Internet Connection"
                                onErrorCallback?.invoke(error)
                                return
                            }

                            // Distinguish between Network issues (timeouts/403s) and Extraction/Parsing blocks
                            val isNetworkError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                               error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                               error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                                               error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED

                            val isParsingError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                               error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED

                            if (isNetworkError && errorCause.contains("403")) {
                                Log.w("MusicPlayer", "DIAGNOSTIC: Potential Extraction Block or Link Expiry (HTTP 403).")
                            } else if (isNetworkError) {
                                Log.w("MusicPlayer", "DIAGNOSTIC: Network timeout or connection reset.")
                            } else if (isParsingError) {
                                Log.e("MusicPlayer", "DIAGNOSTIC: Media3 failed to parse the stream. Possible extraction corruption.")
                            }

                            if ((isNetworkError || isParsingError) && consecutiveErrors < 1) {
                                Log.w("MusicPlayer", "Attempting automated recovery/URL refresh for current song.")
                                consecutiveErrors++
                                mainScope.launch {
                                    val song = currentSong ?: return@launch
                                    val lastResult = (this@apply.currentMediaItem?.localConfiguration?.tag as? StreamingResult)
                                    if (lastResult != null && lastResult.clientName.isNotEmpty()) {
                                        failedClientsForCurrentSong.add(lastResult.clientName)
                                    }

                                    val targetBitrate = null
                                    val priority = if (settingsManager?.isDataSaverEnabled == true) "Data Saver" else "Force OPUS 160kbps"
                                    val preferredQuality = settingsManager?.preferredVideoQuality ?: "auto"
                                    
                                    // Fallback Logic: URL refresh for current song using YouTube.

                                    val result = unifiedRepository?.getStreamingUrl(
                                        videoId = song.videoId,
                                        cookies = cookies,
                                        failedClients = failedClientsForCurrentSong,
                                        targetBitrate = targetBitrate,
                                        priority = priority,
                                        preferredVideoQuality = preferredQuality
                                    )
                                    if (result != null) {
                                        playStream(context, result, song, cookies, isAutoPlay = true)
                                    } else {
                                        Log.e("MusicPlayer", "Failed to refresh URL on error. Skipping in 10s...")
                                        delay(10000)
                                        playNextInQueue(context, isAuto = true)
                                    }
                                }
                                return
                            }

                            this@apply.pause()
                            consecutiveErrors++
                            this@MusicPlayer.isPlaying = false
                            this@MusicPlayer.isLoading = false

                            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                Log.e("MusicPlayer", "Max consecutive errors reached: $consecutiveErrors")
                                consecutiveErrors = 0
                                onErrorCallback?.invoke(error)
                            } else {
                                skipJob?.cancel()
                                skipJob = mainScope.launch {
                                    Log.w("MusicPlayer", "Auto-skipping to next song in 10s... (Count: $consecutiveErrors)")
                                    delay(10000)
                                    if (isActive) playNextInQueue(context, isAuto = true)
                                }
                                onErrorCallback?.invoke(error)
                            }
                        }
                    }
                })
            }
    }

    private fun applyAudioEffects(player: ExoPlayer, trackLoudness: Double? = null) {
        currentLoudness = trackLoudness
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

            val settings = settingsManager ?: return

            if (currentAudioSessionId != audioSessionId) {
                releaseAudioEffects()
                currentAudioSessionId = audioSessionId
            }

            // 1. Loudness Enhancer (Limit Volume / True-Volume Normalization + Volume Boost)
            val targetLUFS = -14.0
            val loudness = trackLoudness ?: 0.0
            val normDiff = if (settings.isNormalizeVolume) targetLUFS - loudness else 0.0
            
            // Volume Boost: 60.0x (fixed)
            // Target gain in millibels: 2000 * log10(60.0) ≈ 3556 mB
            val boostGainmB = (2000 * Math.log10(60.0)).toInt()
            
            // Total gain in millibels
            val totalGainmB = ((normDiff * 100).toInt() + boostGainmB).coerceAtLeast(0)

            if (totalGainmB > 0) {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                        setTargetGain(totalGainmB)
                        enabled = true
                    }
                } else {
                    loudnessEnhancer?.setTargetGain(totalGainmB)
                    loudnessEnhancer?.enabled = true
                }
            } else {
                loudnessEnhancer?.enabled = false
            }

            // 2. Anti-Peak Limiter (Dynamic Processor)
            if (settings.isPeakLimiterEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (dynamicsProcessing == null) {
                    // Limiter config: 1 channel (mono/stereo both handled by framework), enabled=true,
                    // ratio=10 (heavy), threshold=-0.1dB (just below ceiling),
                    // attack=0.1ms (instant), release=100ms
                    val limiter = android.media.audiofx.DynamicsProcessing.Config.Builder(0, 1, true, 1, false, 0, false, 0, true).build().getLimiterByChannelIndex(0).apply {
                        isEnabled = true
                        ratio = 10f
                        threshold = -0.1f
                        attackTime = 0.1f
                        releaseTime = 100f
                    }

                    val config = android.media.audiofx.DynamicsProcessing.Config.Builder(
                        android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        1, // channel count
                        true, 1, // preEq
                        false, 0, // mbc
                        false, 0, // postEq
                        true // limiter
                    ).setLimiterByChannelIndex(0, limiter).build()

                    dynamicsProcessing = android.media.audiofx.DynamicsProcessing(0, audioSessionId, config).apply {
                        enabled = true
                    }
                } else {
                    dynamicsProcessing?.enabled = true
                }
            } else {
                dynamicsProcessing?.enabled = false
            }

            // 3. Virtual 3D Surround & Custom Reverb
            if (settings.surroundMode != "None") {
                if (virtualizer == null) {
                    virtualizer = android.media.audiofx.Virtualizer(0, audioSessionId).apply {
                        setStrength(1000.toShort())
                        enabled = true
                    }
                } else {
                    virtualizer?.enabled = true
                }

                if (reverb == null) {
                    reverb = android.media.audiofx.PresetReverb(0, audioSessionId).apply {
                        preset = when (settings.surroundMode) {
                            "Empty Stadium" -> android.media.audiofx.PresetReverb.PRESET_LARGEHALL
                            "Cyberpunk Club" -> android.media.audiofx.PresetReverb.PRESET_LARGEROOM
                            "Small Studio" -> android.media.audiofx.PresetReverb.PRESET_SMALLROOM
                            else -> android.media.audiofx.PresetReverb.PRESET_NONE
                        }
                        enabled = true
                    }
                } else {
                    reverb?.enabled = true
                    reverb?.preset = when (settings.surroundMode) {
                        "Empty Stadium" -> android.media.audiofx.PresetReverb.PRESET_LARGEHALL
                        "Cyberpunk Club" -> android.media.audiofx.PresetReverb.PRESET_LARGEROOM
                        "Small Studio" -> android.media.audiofx.PresetReverb.PRESET_SMALLROOM
                        else -> android.media.audiofx.PresetReverb.PRESET_NONE
                    }
                }
            } else {
                virtualizer?.enabled = false
                reverb?.enabled = false
            }

        } catch (e: Exception) {
            Log.e("MusicPlayer", "Failed to apply audio effects", e)
        }
    }

    private fun releaseAudioEffects() {
        try {
            loudnessEnhancer?.release()
            dynamicsProcessing?.release()
            virtualizer?.release()
            reverb?.release()
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error releasing effects", e)
        } finally {
            loudnessEnhancer = null
            dynamicsProcessing = null
            virtualizer = null
            reverb = null
            currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    private fun animateVolume(player: ExoPlayer, targetVolume: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        if (durationMs <= 50) {
            player.volume = targetVolume
            onEnd?.invoke()
            return
        }
        volumeFadeJob?.cancel()
        volumeFadeJob = mainScope.launch {
            val startTime = System.currentTimeMillis()
            val startVol = player.volume
            val endVol = targetVolume

            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = now - startTime
                if (elapsed >= durationMs) break

                val progress = elapsed.toFloat() / durationMs
                player.volume = (startVol + (endVol - startVol) * progress).coerceIn(0f, 1f)

                // Use a slightly larger delay to be more background-friendly,
                // but the time-based calculation ensures we finish on time even if throttled.
                delay(40)
            }
            if (isActive) {
                player.volume = targetVolume
                onEnd?.invoke()
            }
        }
    }

    private fun startProgressUpdate(context: Context) {
        progressJob?.cancel()
        progressJob = mainScope.launch {
            while (isActive) {
                val currentP = activePlayer
                if (currentP == null) {
                    delay(500)
                    continue
                }

                // Robust Visualizer check: Ensure it's running if player is active and screen is visible
                if (currentP.playWhenReady && currentP.playbackState == Player.STATE_READY) {
                    if (settingsManager?.isVisualizerEnabled == true && isPlayerViewVisible) {
                        setupVisualizer()
                    } else if (visualizer != null) {
                        releaseVisualizer()
                    }
                }

                // Sync video player with slow-internet optimization
                videoPlayer?.let { vPlayer ->
                    if (isVideoAvailable && isPlayerViewVisible && vPlayer.playbackState != Player.STATE_IDLE) {
                        val audioPos = currentP.currentPosition
                        val now = System.currentTimeMillis()

                        // Logic: Wait 3s AND ensure 10s of video is buffered ahead of current audio position
                        val isTimeReached = now >= videoSyncStartTime
                        val bufferAhead = vPlayer.bufferedPosition - audioPos
                        
                        // We need 10s to START, but we can stay playing if we have at least 5s
                        val startThreshold = 10000L
                        val stayThreshold = 5000L
                        
                        val isEnoughToStart = bufferAhead >= startThreshold || (vPlayer.duration > 0 && vPlayer.bufferedPosition >= vPlayer.duration - 1000)
                        val isEnoughToStay = bufferAhead >= stayThreshold

                        if (isTimeReached && (if (isVideoActuallyPlaying) isEnoughToStay else isEnoughToStart) && currentP.playWhenReady) {
                            if (!vPlayer.playWhenReady) {
                                vPlayer.seekTo(audioPos)
                                vPlayer.playWhenReady = true
                            }
                            
                            // Transition to video only when it's ACTUALLY ready to show frames
                            if (vPlayer.playbackState == Player.STATE_READY) {
                                isVideoActuallyPlaying = true
                            } else if (vPlayer.playbackState == Player.STATE_BUFFERING && bufferAhead < 2000) {
                                // If it's buffering and buffer is very low, fall back to thumbnail
                                isVideoActuallyPlaying = false
                            }

                            val diff = abs(vPlayer.currentPosition - audioPos)
                            if (diff > 1000 && vPlayer.playbackState == Player.STATE_READY) {
                                vPlayer.seekTo(audioPos)
                            }
                        } else {
                            vPlayer.playWhenReady = false
                            // If we stall, don't have enough buffer, or haven't reached 3s, show thumbnail
                            isVideoActuallyPlaying = false
                            
                            val diff = abs(vPlayer.currentPosition - audioPos)
                            if (diff > 15000) {
                                vPlayer.seekTo(audioPos)
                            }
                        }
                    } else {
                        isVideoActuallyPlaying = false
                    }
                }

                // FIX: Update UI position only if we are actually supposed to be playing or if we are actively seeking/buffering.
                if (!currentP.playWhenReady && currentP.playbackState == Player.STATE_READY) {
                    // Update duration/position once and then wait
                    duration = (currentP.duration.takeIf { it != C.TIME_UNSET } ?: 0L).coerceAtLeast(0L)
                    currentPosition = currentP.currentPosition.coerceAtLeast(0L)
                    delay(100)
                    continue
                }

                // Calculate "Clean" UI Position and Duration (Trimming skipped segments)
                val rawPosition = currentP.currentPosition
                val rawDuration = currentP.duration

                // Use cached merged segments to avoid UI flickering during re-fetches
                val activeSegments = if (settingsManager?.isSponsorBlockEnabled == true) {
                    mergedSponsorSegments
                } else {
                    emptyList()
                }

                // 1. Proactive SponsorBlock Skip Check
                if (settingsManager?.isSponsorBlockEnabled == true && activeSegments.isNotEmpty()) {
                    val currentMs = rawPosition

                    // Case B: Already inside a segment (e.g. after manual seek or logic fallback) -> Jump immediately
                    val inside = activeSegments.find {
                        currentMs >= it.first - 100 && currentMs < it.second - 10
                    }

                    if (inside != null) {
                        val skipToMs = inside.second
                        val now = System.currentTimeMillis()

                        // Loop Protection: Only trigger if we haven't skipped this segment yet OR 1s has passed (retry)
                        if (lastSponsorSeekTarget != skipToMs || (now - lastSponsorSeekAttemptTime > 1000)) {
                            lastSponsorSeekTarget = skipToMs
                            lastSponsorSeekAttemptTime = now
                            cleanPositionAtSkipTrigger = currentPosition
                            skipTriggerTime = now
                            // Jump immediately if we're already inside
                            currentP.volume = 0f
                            isFadingForSkip = true
                            // Use NEXT_SYNC to ensure we land definitively past the sponsor segment
                            currentP.setSeekParameters(SeekParameters.NEXT_SYNC)
                            currentP.seekTo(skipToMs)
                            Log.d("MusicPlayer", "SponsorBlock: Emergency jump to $skipToMs")

                            delay(20)
                            continue
                        }
                    } else {
                        // Reset the target tracking once we are clearly past the segment
                        if (lastSponsorSeekTarget != -1L && !isFadingForSkip) {
                            if (rawPosition > lastSponsorSeekTarget - 500) {
                                lastSponsorSeekTarget = -1L
                            }
                        }
                    }
                }

                // 2. Continuous Wave Movement & Interpolation during seeks
                val isBuffering = currentP.playbackState == Player.STATE_BUFFERING
                val isNearSkip = isFadingForSkip // Use the fade flag to drive UI interpolation accurately

                // 3. Track Most Played at 50%
                if (!hasIncrementedPlayCount && duration > 0 && currentPosition >= duration / 2) {
                    hasIncrementedPlayCount = true
                    val song = currentSong
                    if (song != null) {
                        ioScope.launch {
                            database?.songDao()?.incrementPlayCount(
                                song.videoId, song.title, song.artist, song.thumbnailUrl, song.artistId
                            )
                            // Call specialized increment for instant Firebase Console feedback
                            // Pass the actual duration to track total minutes played
                            FirebaseSyncManager.incrementPlayCount(
                                context,
                                song.videoId,
                                song.title,
                                song.artist,
                                song.thumbnailUrl,
                                song.artistId,
                                rawDuration // passing the raw duration in ms
                            )
                        }
                    }
                }

                if ((isBuffering || isNearSkip) && currentP.playWhenReady) {
                    currentVolumeAmplitude = 0.15f + (Math.random().toFloat() * 0.25f)
                    if (isNearSkip) {
                        val elapsed = System.currentTimeMillis() - skipTriggerTime
                        // Smoothly advance UI while player buffers the jump.
                        // Cap interpolation at 5 seconds to prevent runaway drift if network fails.
                        currentPosition = cleanPositionAtSkipTrigger + elapsed.coerceAtMost(5000)
                        delay(50)
                        continue
                    }
                }

                // 3. Map Raw Position to Clean Position (Trimming)
                var totalSkippedBefore = 0L
                for (seg in activeSegments) {
                    if (rawPosition >= seg.second) {
                        totalSkippedBefore += (seg.second - seg.first)
                    } else if (rawPosition >= seg.first) {
                        // Freeze UI at segment start ONLY if we are actively skipping it.
                        totalSkippedBefore += (rawPosition - seg.first)
                    }
                }

                // 4. Auto-Crossfade dB Threshold Check (Early trigger if quiet near end)
                if (settingsManager?.isCrossfadeEnabled == true &&
                    !isCrossfadeTriggered &&
                    rawDuration > 15000 &&
                    rawPosition > (rawDuration - 15000) &&
                    repeatMode != Player.REPEAT_MODE_ONE
                ) {
                    val thresholdDb = settingsManager?.crossfadeThresholdDb ?: -10f
                    // Amplitude mapping: dB = 20 * log10(linear). Linear = 10^(dB/20)
                    // Visualizer amplitude is approx 0.0 to 1.0. 
                    val thresholdLinear = Math.pow(10.0, (thresholdDb / 20.0)).toFloat()

                    // Trigger if volume is below threshold (and not just absolute zero/buffered)
                    if (currentVolumeAmplitude > 0.001f && currentVolumeAmplitude < thresholdLinear) {
                        Log.d("MusicPlayer", "Early Crossfade: Volume (${currentVolumeAmplitude}f) dropped below threshold (${thresholdLinear}f)")
                        isCrossfadeTriggered = true
                        startCrossfade(context)
                    }
                }

                // Use a small 500ms safety buffer to ensure duration updates don't cause
                // negative clean positions or erratic slider jumps at start/end.
                if (rawDuration > 0) {
                    currentPosition = (rawPosition - totalSkippedBefore).coerceIn(0, rawDuration)
                    val totalSkippedMs = activeSegments.sumOf { it.second - it.first }
                    duration = (rawDuration - totalSkippedMs).coerceAtLeast(0)
                } else {
                    currentPosition = if (rawPosition != C.TIME_UNSET) rawPosition else 0L
                    duration = (rawDuration.takeIf { it != C.TIME_UNSET } ?: 0L).coerceAtLeast(0L)
                }

                delay(100)
            }
        }
    }

    private fun startCrossfade(context: Context) {
        val nextIndex = getNextIndex()
        if (nextIndex == -1) return

        val nextSong = queue[nextIndex]
        val currentP = activePlayer
        val nextP = getInactivePlayer(context)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaveMuzix:Crossfade")

        ioScope.launch {
            try {
                wakeLock?.acquire(30000L)
                Log.d("MusicPlayer", "Background Crossfade Starting: ${nextSong.title}")
                val targetBitrate = null
                
                val priority = if (settingsManager?.isDataSaverEnabled == true) {
                    "Data Saver"
                } else {
                    "Force OPUS 160kbps"
                }
                val preferredQuality = settingsManager?.preferredVideoQuality ?: "auto"

                val result = unifiedRepository?.getStreamingUrl(
                    videoId = nextSong.videoId,
                    cookies = cookies,
                    failedClients = emptyList(),
                    targetBitrate = targetBitrate,
                    priority = priority,
                    preferredVideoQuality = preferredQuality
                )
                if (result != null) {
                    withContext(Dispatchers.Main) {
                        val metadata = createMediaMetadata(nextSong)

                        val mediaItem = MediaItem.Builder()
                            .setUri(result.url)
                            .setMediaId(nextSong.videoId)
                            .setMediaMetadata(metadata)
                            .setMimeType(result.mimeType)
                            .build()

                        // Set item first, then swap active player and notify service
                        nextP.setMediaItem(mediaItem, true)
                        currentServer = result.clientName

                        releaseVisualizer() // Explicitly release before player swap to avoid ID conflicts
                        inactivePlayer = currentP
                        activePlayer = nextP

                        val oldIndex = currentIndex
                        currentIndex = nextIndex
                        currentSong = nextSong
                        scheduledVideoId = null
                        isCrossfadeTriggered = false
                        
                        // Ensure metadata and colors are fetched for the crossfaded song
                        fetchMetadata(context, nextSong)

                        Log.d("MusicPlayer", "Crossfade Transition: $oldIndex -> $currentIndex (${nextSong.title})")

                        onPlayerChanged?.invoke(nextP)

                        nextP.volume = 0f
                        nextP.prepare()
                        nextP.play()

                        // Apply audio effects to nextP before it starts fading in
                        applyAudioEffects(nextP, result.loudnessDb)

                        this@MusicPlayer.isPlaying = true
                        startProgressUpdate(context)

                        fetchSponsorSegments(nextSong.videoId)
                        fetchMetadata(context, nextSong)
                        startLyricsFetch(nextSong)
                        refreshQueueIfLow()

                        val oldBaseVol = baseVolume
                        val nextBaseVol = calculateBaseVolume(result.loudnessDb)
                        baseVolume = nextBaseVol
                        fadeBetween(currentP, nextP, oldBaseVol, nextBaseVol)
                    }
                } else {
                    Log.e("MusicPlayer", "Crossfade failed: Could not get URL")
                }
            } catch (e: Exception) {
                Log.e("MusicPlayer", "Crossfade fetch failed", e)
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }

    private fun fadeBetween(outPlayer: ExoPlayer?, inPlayer: ExoPlayer, outBaseVol: Float, inBaseVol: Float) {
        val thresholdDb = settingsManager?.crossfadeThresholdDb ?: -10f
        // Perfect Decibel Transition: Duration is mapped directly to the dB sensitivity.
        // Higher threshold sensitivity (e.g. -20dB) allows for a longer, more professional blend.
        val fadeDuration = (Math.abs(thresholdDb) * 500).toLong().coerceIn(2000, 15000)

        mainScope.launch {
            val steps = 100
            val interval = fadeDuration / steps
            
            val minDb = -60f 
            for (i in 0..steps) {
                val progress = i.toFloat() / steps
                
                // Logarithmic (Decibel-Linear) Crossfade
                // This ensures the perceived loudness changes linearly, which is the "Golden Standard" for audio
                val outDb = progress * minDb
                val outVol = Math.pow(10.0, (outDb / 20.0)).toFloat() * outBaseVol * userVolume
                
                val inDb = (1f - progress) * minDb
                val inVol = Math.pow(10.0, (inDb / 20.0)).toFloat() * inBaseVol * userVolume
                
                outPlayer?.volume = outVol.coerceIn(0f, 1f)
                inPlayer.volume = inVol.coerceIn(0f, 1f)
                delay(interval)
            }
            outPlayer?.stop()
            outPlayer?.clearMediaItems()
            outPlayer?.volume = outBaseVol * userVolume
            inPlayer.volume = inBaseVol * userVolume
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        // DO NOT release visualizer here as it may cause crash if toggled rapidly.
        // It is managed by setupVisualizer's check and releaseVisualizer() call on song change.
    }

    private fun setupVisualizer() {
        val context = appContext ?: return
        if (settingsManager?.isVisualizerEnabled == false) {
            releaseVisualizer()
            return
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("MusicPlayer", "Visualizer skip: RECORD_AUDIO permission not granted")
            return
        }

        val player = activePlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId <= 0) return 

        if (visualizer != null && currentVisualizerSessionId == audioSessionId) {
            try {
                if (visualizer?.enabled == false) visualizer?.enabled = true
                return 
            } catch (e: Exception) {
                Log.e("MusicPlayer", "Visualizer health check failed, recreating", e)
            }
        }

        releaseVisualizer()
        try {
            currentVisualizerSessionId = audioSessionId
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    private val mainHandler = Handler(Looper.getMainLooper())
                    private var lastUpdateTime = 0L
                    private val THROTTLE_MS = 11L // ~90 FPS

                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (isLoading || !this@MusicPlayer.isPlaying) {
                            resetAmplitudes()
                            return
                        }
                        if (lastSponsorSeekTarget != -1L) return

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime < THROTTLE_MS) return
                        lastUpdateTime = now

                        waveform?.let {
                            var sum = 0f
                            for (b in it) {
                                sum += abs(b.toInt() - 128).toFloat()
                            }
                            val amplitude = (sum / it.size) / 64f
                            mainHandler.post { currentVolumeAmplitude = amplitude }
                        }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (isLoading || fft == null || !this@MusicPlayer.isPlaying) {
                            resetAmplitudes()
                            return
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime < THROTTLE_MS) return
                        // Note: we don't update lastUpdateTime here to allow both listeners to fire 
                        // within the same "frame" if they arrive close together.
                        
                        var bassSum = 0f
                        var trebleSum = 0f
                        val bassRange = (fft.size * 0.1).toInt().coerceAtLeast(2)
                        for (i in 2 until bassRange step 2) {
                            bassSum += hypot(fft[i].toFloat(), fft[i+1].toFloat())
                        }
                        val trebleStart = (fft.size * 0.7).toInt()
                        for (i in trebleStart until fft.size - 1 step 2) {
                            trebleSum += hypot(fft[i].toFloat(), fft[i+1].toFloat())
                        }
                        
                        val bassAmp = (bassSum / (bassRange / 2)) / 50f
                        val trebleAmp = (trebleSum / ((fft.size - trebleStart) / 2)) / 30f
                        
                        mainHandler.post {
                            currentBassAmplitude = bassAmp.coerceIn(0f, 2f)
                            currentTrebleAmplitude = trebleAmp.coerceIn(0f, 2f)
                        }
                    }

                    private fun resetAmplitudes() {
                        if (currentVolumeAmplitude > 0 || currentBassAmplitude > 0 || currentTrebleAmplitude > 0) {
                            mainHandler.post {
                                currentVolumeAmplitude = 0f
                                currentBassAmplitude = 0f
                                currentTrebleAmplitude = 0f
                            }
                        }
                    }
                }, Visualizer.getMaxCaptureRate(), true, true)
                enabled = true
            }
            Log.d("MusicPlayer", "Visualizer attached to session: $audioSessionId at max rate")
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Visualizer setup failed", e)
            currentVisualizerSessionId = C.AUDIO_SESSION_ID_UNSET
            visualizer = null
        }
    }

    fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {}
        visualizer = null
        currentVisualizerSessionId = C.AUDIO_SESSION_ID_UNSET
        
        // Immediate reset of amplitudes to prevent "frozen" UI state
        currentVolumeAmplitude = 0f
        currentBassAmplitude = 0f
        currentTrebleAmplitude = 0f
    }

    @OptIn(UnstableApi::class)
    fun playStream(
        context: Context,
        result: StreamingResult,
        song: Song,
        cookies: String? = null,
        playlist: List<Song> = emptyList(),
        isAutoPlay: Boolean = false,
        startRadio: Boolean = true
    ) {
        releaseVisualizer() // Ensure clean slate for new stream
        // Initialize player and context first to ensure background tasks have access to appContext
        val player = getInstance(context)

        // Ensure repository is synced if this call came from a screen with its own repository instance
        if (this.unifiedRepository == null && context is com.example.wavemuzix.MainActivity) {
            // This is a fallback; primary initialization happens in MainActivity.onCreate
        }

        releaseVisualizer()
        volumeFadeJob?.cancel()
        isFadingForSkip = false
        if (settingsManager == null) settingsManager = SettingsManager(context)
        this.cookies = cookies
        if (playlist.isNotEmpty() && !isAutoPlay) {
            queue.clear()
            queue.addAll(playlist)
            currentIndex = playlist.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        } else if (isAutoPlay) {
            // Stability: If already at this song in the queue, don't search and jump
            val isAlreadyAtCorrectIndex = currentIndex in queue.indices && queue[currentIndex].videoId == song.videoId
            if (!isAlreadyAtCorrectIndex) {
                val index = queue.indexOfFirst { it.videoId == song.videoId }
                if (index != -1) {
                    currentIndex = index
                } else {
                    // Don't add if we are just autoplaying/retrying a fetch
                }
            }
        } else {
            // Solo song from search - create a new queue and trigger radio curation
            queue.clear()
            queue.add(song)
            currentIndex = 0
        }

        currentServer = result.clientName
        currentSong = song
        prefetchedVideoId = null
        scheduledVideoId = null
        scheduledIndex = -1
        hasIncrementedPlayCount = false
        val shouldPlayVideo = result.videoUrl != null && settingsManager?.isPlayVideoIfAvailable == true
        isVideoAvailable = shouldPlayVideo

        // Only clear failed clients if this is a fresh manual play (not an auto-retry)
        if (!isAutoPlay) {
            failedClientsForCurrentSong.clear()
        }

        // Force refresh to curate the "endless" queue immediately if we only have one song
        if (queue.size <= 1 && startRadio) {
            refreshQueueIfLow(force = true)
        }
        // This helps maintain foreground service status.
        val metadata = createMediaMetadata(song)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(result.url)
            .setMediaId(song.videoId)
            .setMediaMetadata(metadata)
            .setTag(result)

        if (!result.mimeType.isNullOrEmpty()) mediaItemBuilder.setMimeType(result.mimeType)

        val mediaItem = mediaItemBuilder.build()

        if (shouldPlayVideo) {
            val videoMediaItem = MediaItem.Builder()
                .setUri(result.videoUrl)
                .setMimeType(result.videoMimeType ?: "video/mp4")
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem, true)
            
            val vPlayer = getVideoPlayer(context)
            vPlayer.stop()
            vPlayer.clearMediaItems()
            vPlayer.setMediaItem(videoMediaItem)
            vPlayer.prepare()
            // Optimization for slow internet: Delay video by 3s and ensure 10s buffer
            videoSyncStartTime = System.currentTimeMillis() + 3000
            vPlayer.playWhenReady = false
            isVideoActuallyPlaying = false
        } else {
            player.setMediaItem(mediaItem, true) // Replaces all items and resets position
            videoPlayer?.stop()
            videoPlayer?.clearMediaItems()
        }

        // Track most played removed from here - now at 50% in progress update

        // refreshQueueIfLow() is already called earlier in playStream with startRadio=true
        // or by playAtIndex/playNextInQueue, so removing this duplicate call to avoid extra requests.

        baseVolume = calculateBaseVolume(result.loudnessDb)
        player.volume = 0f

        applyAudioEffects(player, result.loudnessDb)
        animateVolume(player, baseVolume * userVolume, 2000L)

        // CRITICAL: Set User-Agent and Headers to match the client used during extraction
        httpDataSourceFactory.setUserAgent(result.userAgent)

        val headers = mutableMapOf(
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "Accept" to "*/*"
        )
        if (!cookies.isNullOrEmpty()) headers["Cookie"] = cookies

        httpDataSourceFactory.setDefaultRequestProperties(headers)

        // Pre-fetch SponsorBlock segments early
        fetchSponsorSegments(song.videoId)

        // Ensure PlaybackService is running BEFORE we start playback
        ensureServiceStarted(context)

        player.prepare()
        player.play()

        if (settingsManager?.isSaveLastPlayed == true) {
            settingsManager?.lastPlayedSongId = song.videoId
        }

        // Force refresh to curate the "endless" queue after playback starts
        refreshQueueIfLow(force = true)
        isLoading = false
        isPlaying = true
        consecutiveErrors = 0
        startProgressUpdate(context)
    }

    fun playNext(song: Song) {
        if (currentIndex != -1 && currentIndex < queue.size) {
            val insertIndex = currentIndex + 1
            queue.add(insertIndex, song)
            if (shuffleEnabled) {
                // Update shuffledIndices to include the new song right after the current one
                val currentInShuffled = shuffledIndices.indexOf(currentIndex)
                if (currentInShuffled != -1) {
                    val newShuffledIndices = shuffledIndices.toMutableList()
                    // Shift all indices >= insertIndex in the current mapping
                    for (i in newShuffledIndices.indices) {
                        if (newShuffledIndices[i] >= insertIndex) {
                            newShuffledIndices[i]++
                        }
                    }
                    // Insert the new index at the position after currentInShuffled
                    newShuffledIndices.add(currentInShuffled + 1, insertIndex)
                    shuffledIndices.clear()
                    shuffledIndices.addAll(newShuffledIndices)
                } else {
                    generateShuffledIndices()
                }
            }

            // Update ExoPlayer's internal queue to include the new song next
            activePlayer?.let { player ->
                val currentIdx = player.currentMediaItemIndex
                if (currentIdx < player.mediaItemCount - 1) {
                    player.removeMediaItems(currentIdx + 1, player.mediaItemCount)
                }
                appContext?.let { prefetchNextSong(it) }
            }
        } else {
            queue.add(song)
            if (currentIndex == -1) currentIndex = 0
            if (shuffleEnabled) generateShuffledIndices()

            activePlayer?.let { player ->
                if (player.mediaItemCount <= 1) {
                    appContext?.let { prefetchNextSong(it) }
                }
            }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return
        val song = queue.removeAt(fromIndex)
        queue.add(toIndex, song)

        // Update currentIndex to follow the song
        if (currentIndex == fromIndex) {
            currentIndex = toIndex
        } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
            currentIndex--
        } else if (fromIndex > currentIndex && toIndex <= currentIndex) {
            currentIndex++
        }

        // Re-generate shuffled indices if needed to maintain consistency
        if (shuffleEnabled) {
            generateShuffledIndices()
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        queue.removeAt(index)
        if (index < currentIndex) {
            currentIndex--
        } else if (index == currentIndex) {
            if (currentIndex >= queue.size) {
                currentIndex = queue.size - 1
            }
            // If the queue is now empty, reset
            if (queue.isEmpty()) {
                clearState()
            }
        }

        if (shuffleEnabled) {
            generateShuffledIndices()
        }
    }

    fun addToQueue(song: Song) {
        queue.add(song)
        if (currentIndex == -1) currentIndex = 0
        if (shuffleEnabled) {
            shuffledIndices.add(queue.size - 1)
        }
    }

    fun playNextInQueue(context: Context, isAuto: Boolean = false) {
        if (queue.isEmpty() || isSkipping) return

        // Strict Anti-rapid-fire: throttle manual skips, but be more lenient for auto skips
        val now = System.currentTimeMillis()
        val throttleMs = if (isAuto) 500L else 1500L
        if (now - lastSkipTime < throttleMs) {
            Log.w("MusicPlayer", "Skipping too fast (throttled), ignoring request. Delta: ${now - lastSkipTime}ms")
            return
        }
        lastSkipTime = now
        skipJob?.cancel()
        isSkipping = true

        if (repeatMode == Player.REPEAT_MODE_ONE && !isAuto) {
            // Manual skip or error-skip while on Repeat One should still go to next
            // to avoid infinite loops on a broken song.
            val nextIndex = if (currentIndex + 1 < queue.size) currentIndex + 1 else 0
            playAtIndex(context, nextIndex)
            isSkipping = false
            return
        }

        if (shuffleEnabled) {
            if (shuffledIndices.isEmpty() || !shuffledIndices.contains(currentIndex)) generateShuffledIndices()

            val nextShuffledPos = shuffledIndices.indexOf(currentIndex) + 1
            if (nextShuffledPos < shuffledIndices.size) {
                playAtIndex(context, shuffledIndices[nextShuffledPos])
            } else if (this.repeatMode == Player.REPEAT_MODE_ALL) {
                // End of shuffle reached: start a fresh shuffle cycle
                generateShuffledIndices()
                // Pick the second one in the new shuffle to avoid immediate repeat of the song that just ended
                val nextPos = if (shuffledIndices.size > 1) 1 else 0
                playAtIndex(context, shuffledIndices[nextPos])
            }
        } else {
            if (currentIndex + 1 < queue.size) {
                playAtIndex(context, currentIndex + 1)
            } else if (this.repeatMode == Player.REPEAT_MODE_ALL) {
                playAtIndex(context, 0)
            }
        }
        isSkipping = false
    }

    fun playAtIndex(context: Context, index: Int) {
        if (index in queue.indices) {
            val song = queue[index]

            // CRITICAL GUARD: If this song is already active, just sync the index and skip restarting.
            // This prevents the "stop and restart" bug when navigating between screens with pagers.
            val isSameSong = currentSong?.videoId == song.videoId
            val isActive = isPlaying || isLoading ||
                           activePlayer?.playbackState == Player.STATE_READY ||
                           activePlayer?.playbackState == Player.STATE_BUFFERING

            if (isSameSong && isActive) {
                Log.d("MusicPlayer", "playAtIndex: Song already active, syncing index $index and skipping restart.")
                currentIndex = index
                return
            }

            val oldIndex = currentIndex
            currentIndex = index
            releaseVisualizer()
            Log.d("MusicPlayer", "Play At Index: $oldIndex -> $currentIndex (${song.title})")

            // Cancel any ongoing fetch/skip jobs
            fetchJob?.cancel()
            skipJob?.cancel()

            fetchAndPlay(context, song)
            refreshQueueIfLow()
        }
    }

    private fun refreshQueueIfLow(force: Boolean = false) {
        val song = currentSong ?: return
        val context = appContext ?: return

        // AI-like behavior: Only curate if auto-curation is enabled (Solo plays)
        if (!isAutoCurationEnabled) return

        // Trigger refresh if forced or when we are nearing the end of the current block.
        if (!force && currentIndex < queue.size - 15) return

        refreshQueueJob?.cancel()
        refreshQueueJob = ioScope.launch {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaveMuzix:AI_Queue")
            try {
                wakeLock?.acquire(30000L)
                Log.d("MusicPlayer", "AI Curation: Refreshing personalized queue for: ${song.title}")

                // 1. YouTube AI Radio (The "Real" Curation)
                // Fetching the RD playlist directly gives a much larger and better curated set of songs.
                val radioId = "RD${song.videoId}"
                val radioPlaylist = unifiedRepository?.fetchPlaylist(radioId, cookies, emptySet())
                val radioSuggestions = radioPlaylist?.songs ?: emptyList()
                
                // 2. Related Songs (Up Next) as secondary source
                val (relatedSuggestions, _) = unifiedRepository?.fetchNextSongs(song.videoId, null, cookies, filterAudioOnly = false) ?: Pair(null, null)

                // Combine sources, prioritizing the AI Radio
                val allSuggestions = (radioSuggestions + (relatedSuggestions ?: emptyList())).distinctBy { it.videoId }

                // Filter suggestions: Relax audio-only check for AI-curated tracks as they are highly relevant
                var suggestions = allSuggestions.filter { suggestion ->
                    // Basic sanity check: Don't add extremely short videos or obvious non-music
                    val lowerTitle = suggestion.title.lowercase()
                    !lowerTitle.contains("tutorial") && !lowerTitle.contains("how to") && !lowerTitle.contains("review")
                }

                // 3. Metadata Extraction for strict vibe/language matching
                val details = videoDetails ?: ytDescriptionRepository.fetchVideoDetails(song.videoId, cookies)
                val description = details?.description ?: ""
                val tags = details?.tags?.joinToString(" ") ?: ""
                val category = details?.category ?: ""
                
                val combinedText = "${song.title} ${song.artist} $description $tags $category".lowercase()
                val detectedLanguage = when {
                    combinedText.contains(Regex("[\\u0900-\\u097F]")) -> "hindi"
                    combinedText.contains(Regex("[\\u0D00-\\u0D7F]")) -> "malayalam"
                    combinedText.contains(Regex("[\\u0C80-\\u0CFF]")) -> "kannada"
                    combinedText.contains(Regex("[\\u0B80-\\u0BFF]")) -> "tamil"
                    combinedText.contains(Regex("[\\u0C00-\\u0C7F]")) -> "telugu"
                    combinedText.contains("bollywood") || combinedText.contains("t-series") -> "hindi"
                    combinedText.contains("mollywood") -> "malayalam"
                    combinedText.contains("tollywood") -> "telugu"
                    combinedText.contains("kollywood") -> "tamil"
                    else -> "english"
                }
                
                val vibeKeywords = extractVibeKeywords(song.title, "$description $tags $category")

                // Filter suggestions: Prioritize items that match BOTH vibe AND language
                val filteredSuggestions = suggestions.filter { suggestion ->
                    val sTitle = suggestion.title.lowercase()
                    val sArtist = suggestion.artist.lowercase()
                    val sCombined = "$sTitle $sArtist"
                    
                    // Language Check: Does the suggestion contain the script or keywords of the detected language?
                    val matchesLanguage = when(detectedLanguage) {
                        "hindi" -> sCombined.contains(Regex("[\\u0900-\\u097F]")) || sCombined.contains("hindi") || sCombined.contains("bollywood") || sCombined.contains("t-series")
                        "malayalam" -> sCombined.contains(Regex("[\\u0D00-\\u0D7F]")) || sCombined.contains("malayalam") || sCombined.contains("mollywood")
                        "tamil" -> sCombined.contains(Regex("[\\u0B80-\\u0BFF]")) || sCombined.contains("tamil") || sCombined.contains("kollywood")
                        "telugu" -> sCombined.contains(Regex("[\\u0C00-\\u0C7F]")) || sCombined.contains("telugu") || sCombined.contains("tollywood")
                        "kannada" -> sCombined.contains(Regex("[\\u0C80-\\u0CFF]")) || sCombined.contains("kannada")
                        else -> !sCombined.contains(Regex("[\\u0900-\\u0D7F]")) // English: Filter out regional scripts
                    }
                    
                    // Vibe Check: Check if suggestion matches ANY of the detected vibe keywords
                    val matchesVibe = vibeKeywords.split(" ").any { kw -> 
                        kw != "music" && kw != "similar" && kw != "vibe" && sCombined.contains(kw)
                    }

                    // Strict matching: If we have strong vibe/language signals, enforce them.
                    // Otherwise, trust YouTube's AI suggestions but bias towards language.
                    matchesLanguage && (matchesVibe || vibeKeywords == "similar music vibe")
                }

                // 4. Smart Fallback - Only if filtered AI results are too few
                suggestions = if (filteredSuggestions.size < 10) {
                    val curationQuery = "$detectedLanguage $vibeKeywords music similar to ${song.title}"
                    val searchSuggestions = unifiedRepository?.searchSongs(curationQuery, cookies, filterOnlyAudio = true) ?: emptyList()
                    (filteredSuggestions + searchSuggestions).distinctBy { it.videoId }
                } else {
                    filteredSuggestions
                }

                suggestions = suggestions.shuffled()

                if (suggestions.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val existingIds = queue.map { it.videoId }.toSet()
                        val existingTitles = queue.map { it.title.lowercase().trim() }.toSet()
                        val artistCounts = mutableMapOf<String, Int>()
                        
                        val newSongs = suggestions
                            .filter { it.videoId != song.videoId && !existingIds.contains(it.videoId) }
                            .filter { suggestion ->
                                // Near-duplicate / Remix Filter: Avoid multiple versions of the same song
                                val lowerTitle = suggestion.title.lowercase()
                                val isRemixOrLive = lowerTitle.contains("remix") || 
                                                  lowerTitle.contains("live") || 
                                                  lowerTitle.contains("performance") || 
                                                  lowerTitle.contains("cover") ||
                                                  lowerTitle.contains("mashup")
                                
                                // Only allow remix/live if the original title isn't already there 
                                // OR if the current song itself is a remix/live (respecting user's current vibe)
                                val currentIsSpecial = song.title.lowercase().let { it.contains("remix") || it.contains("live") }
                                
                                if (!currentIsSpecial && isRemixOrLive) return@filter false
                                
                                // Title Similarity check (Basic)
                                val cleanTitle = suggestion.title.lowercase()
                                    .replace(Regex("\\(.*?\\)"), "")
                                    .replace(Regex("\\[.*?\\]"), "")
                                    .trim()
                                
                                if (existingTitles.any { it.contains(cleanTitle) || cleanTitle.contains(it) }) return@filter false

                                true
                            }
                            .filter { 
                                // Strict Diversity Filter: Max 2 songs per artist to avoid artist-looping
                                val count = artistCounts.getOrDefault(it.artist, 0)
                                if (count < 2) {
                                    artistCounts[it.artist] = count + 1
                                    true
                                } else false
                            }
                            .take(50)

                        if (newSongs.isNotEmpty()) {
                            val oldSize = queue.size
                            queue.addAll(newSongs)
                            if (shuffleEnabled) {
                                // Stability: Append new indices to the end of shuffle and shuffle only those
                                // to prevent already played songs from repeating immediately.
                                val newIndices = (oldSize until queue.size).toMutableList()
                                newIndices.shuffle()
                                shuffledIndices.addAll(newIndices)
                            }
                            Log.d("MusicPlayer", "AI Queue expanded with ${newSongs.size} personalized songs. Total queue: ${queue.size}")

                            prefetchNextSong(context)

                            if (activePlayer?.playbackState == Player.STATE_ENDED) {
                                playNextInQueue(context)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayer", "AI Queue refresh failed", e)
            } finally {
                if (wakeLock?.isHeld == true) try { wakeLock.release() } catch (e: Exception) {}
            }
        }
    }

    private fun extractVibeKeywords(title: String, metadata: String? = null): String {
        val keywords = mutableListOf<String>()
        val combined = (title + " " + (metadata ?: "")).lowercase()
        
        // Precise Vibe Detection (Moods, Genres, and Context)
        val vibeMap = mapOf(
            "lofi chill" to listOf("lofi", "lo-fi", "chill", "study", "relax"),
            "slowed reverb" to listOf("slowed", "reverb", "aesthetic"),
            "trap hiphop" to listOf("trap", "hip hop", "rap", "drill", "beat"),
            "sad emotional" to listOf("sad", "emotional", "heartbreak", "broken", "pain", "crying"),
            "party dance edm" to listOf("party", "dance", "edm", "club", "remix", "house", "techno"),
            "romantic love" to listOf("romantic", "love", "couple", "sweet", "valentine", "heart"),
            "sleep relax ambient" to listOf("sleep", "relax", "calm", "ambient", "lullaby", "meditation", "soothing"),
            "workout gym energetic" to listOf("workout", "gym", "energetic", "motivation", "power", "beast"),
            "acoustic guitar" to listOf("acoustic", "unplugged", "guitar", "piano version"),
            "rock metal" to listOf("rock", "metal", "heavy", "punk", "alternative"),
            "pop hits" to listOf("pop", "chart", "hits"),
            "classical piano" to listOf("classical", "piano", "violin", "orchestral", "composition"),
            "bass boosted" to listOf("bass", "subwoofer", "loud"),
            "devotional spiritual" to listOf("devotional", "bhajan", "spiritual", "mantra", "god", "prayer", "worship"),
            "jazz soul" to listOf("jazz", "soul", "blues", "r&b", "funk")
        )

        for ((vibe, triggerWords) in vibeMap) {
            if (triggerWords.any { combined.contains(it) }) {
                keywords.add(vibe)
            }
        }
        
        return if (keywords.isEmpty()) "similar music vibe" else keywords.distinct().joinToString(" ")
    }

    var isAutoCurationEnabled by mutableStateOf(true)

    fun playSong(context: Context, song: Song, playlist: List<Song> = emptyList(), cookies: String? = null, isFromFixedList: Boolean = false) {
        if (cookies != null) this.cookies = cookies
        isPlaybackError = false
        playbackErrorMessage = null
        isAutoCurationEnabled = !isFromFixedList

        // Handle Queue before fetching to ensure UI updates immediately
        if (playlist.isNotEmpty()) {
            queue.clear()
            queue.addAll(playlist)
            currentIndex = queue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        } else {
            // Starting a new "solo" play (e.g. from search) should clear the queue
            // to allow the AI-driven radio to populate a fresh personalized session.
            queue.clear()
            queue.add(song)
            currentIndex = 0
        }

        fetchAndPlay(context, song)
    }

    private fun fetchAndPlay(context: Context, song: Song) {
        fetchJob?.cancel()
        skipJob?.cancel()

        val player = getInstance(context)
        // Stop current playback immediately to provide instant feedback
        player.stop()
        player.clearMediaItems()

        isLoading = true
        currentSong = song
        currentPosition = 0L
        duration = parseDurationToMs(song.durationText)
        highResThumbnailUrl = getHighResThumbnailUrl(song.thumbnailUrl)
        currentSongDurationText = song.durationText

        scheduledVideoId = null

        // Trigger metadata and lyrics fetching IMMEDIATELY so they are ready by the time streaming starts
        fetchMetadata(context, song)
        startLyricsFetch(song)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaveMuzix:FetchNext")

        fetchJob = ioScope.launch {
            try {
                failedClientsForCurrentSong.clear()
                wakeLock?.acquire(30000L)
                if (wifiLock?.isHeld == false) wifiLock?.acquire()

                // 1. Check for offline version first
                val downloadedSong = database?.songDao()?.getDownloadByVideoId(song.videoId)
                if (downloadedSong != null) {
                    val file = java.io.File(downloadedSong.filePath)
                    if (file.exists()) {
                        Log.d("MusicPlayer", "Found offline version for ${song.title} at ${downloadedSong.filePath}, playing locally.")
                        withContext(Dispatchers.Main) {
                            playOffline(context, downloadedSong.filePath, song, downloadedSong.mimeType)
                            isLoading = false
                        }
                        // Trigger queue expansion even for offline songs if internet is available
                        refreshQueueIfLow()
                        return@launch
                    } else {
                        Log.w("MusicPlayer", "Download record exists but file not found at ${downloadedSong.filePath}")
                    }
                }

                // 2. Fallback to network if not offline
                // Lyrics are now handled inside playStream and playOffline via startLyricsFetch

                if (!NetworkUtils.isNetworkAvailable(context)) {
                    Log.e("MusicPlayer", "Network unavailable, cannot fetch streaming URL.")
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        isPlaybackError = true
                        playbackErrorMessage = "No Internet Connection"
                        // Use a custom PlaybackException or trigger the callback to show error UI
                        onErrorCallback?.invoke(
                            PlaybackException(
                                "No Internet Connection",
                                null,
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                            )
                        )
                    }
                    return@launch
                }

                Log.d("MusicPlayer", "Fetching streaming URL for: ${song.title} using repository: $unifiedRepository")
                val targetBitrate = null
                
                val priority = if (settingsManager?.isDataSaverEnabled == true) {
                    "Data Saver"
                } else {
                    "Force OPUS 160kbps"
                }
                val preferredQuality = settingsManager?.preferredVideoQuality ?: "auto"
                val result = unifiedRepository?.getStreamingUrl(
                    videoId = song.videoId,
                    cookies = cookies,
                    failedClients = emptyList(),
                    targetBitrate = targetBitrate,
                    priority = priority,
                    preferredVideoQuality = preferredQuality
                )

                fetchSponsorSegments(song.videoId)

                // Trigger queue expansion for network songs as well
                refreshQueueIfLow()

                if (!isActive) return@launch

                withContext(Dispatchers.Main) {
                    if (result != null) {
                        playStream(
                            context = context,
                            result = result,
                            song = song,
                            cookies = cookies,
                            isAutoPlay = true
                        )
                    } else {
                        Log.e("MusicPlayer", "Failed to get streaming URL for ${song.title}")
                        
                        // Fallback retry with a different set of clients if initial set failed
                        if (failedClientsForCurrentSong.isEmpty()) {
                             Log.w("MusicPlayer", "Initial extraction failed. Retrying with full client rotation.")
                             // This will trigger a second attempt in the same fetchJob
                             val secondAttempt = unifiedRepository?.getStreamingUrl(
                                 videoId = song.videoId,
                                 cookies = cookies,
                                 failedClients = listOf("ANDROID_MUSIC")
                             )
                             if (secondAttempt != null) {
                                 playStream(context, secondAttempt, song, cookies, isAutoPlay = true)
                                 return@withContext
                             }
                        }

                        isLoading = false
                        consecutiveErrors++
                        if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS) {
                            skipJob?.cancel()
                            skipJob = mainScope.launch {
                                Log.w("MusicPlayer", "Auto-skipping due to extraction failure in 10s... (Count: $consecutiveErrors)")
                                delay(10000)
                                if (isActive) playNextInQueue(context, isAuto = true)
                            }
                        } else {
                            Log.e("MusicPlayer", "Max consecutive extraction errors reached. Stopping.")
                            isLoading = false
                            isPlaying = false
                            consecutiveErrors = 0
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Log.e("MusicPlayer", "Fetch error: ${e.message}")
                    consecutiveErrors++
                    if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS) {
                        skipJob?.cancel()
                        skipJob = mainScope.launch {
                            delay(10000)
                            if (isActive) playNextInQueue(context, isAuto = true)
                        }
                    }
                }
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (e: Exception) {}
                }
                if (wifiLock?.isHeld == true) {
                    try { wifiLock?.release() } catch (e: Exception) {}
                }
            }
        }
    }

    private fun prefetchNextSong(context: Context) {
        if (isPrefetching) return
        val player = activePlayer ?: return

        // Count how many items are ahead of the current one in the timeline
        val currentIdx = player.currentMediaItemIndex
        val itemsAhead = player.mediaItemCount - 1 - currentIdx

        // We want to maintain at least 2 items ahead for stability
        if (itemsAhead >= 2) return

        // Find the last item in the player's queue to determine what comes next
        val lastItemIndex = player.mediaItemCount - 1
        val lastMediaId = if (lastItemIndex >= 0) player.getMediaItemAt(lastItemIndex).mediaId else null

        val baseIndex = if (lastMediaId != null) {
            // Find the lastMediaId in the queue, prioritizing indices "after" the current one
            if (shuffleEnabled) {
                val currentPos = shuffledIndices.indexOf(currentIndex)
                val searchIndices = if (currentPos != -1) shuffledIndices.drop(currentPos) else shuffledIndices
                val found = searchIndices.firstOrNull { queue.getOrNull(it)?.videoId == lastMediaId }
                found ?: queue.indexOfFirst { it.videoId == lastMediaId }
            } else {
                val searchStart = currentIndex.coerceAtLeast(0)
                val firstAfter = (searchStart until queue.size).firstOrNull { queue[it].videoId == lastMediaId }
                firstAfter ?: queue.indexOfFirst { it.videoId == lastMediaId }
            }
        } else {
            currentIndex
        }

        val nextIndex = getNextIndex(baseIndex)
        if (nextIndex == -1 || (nextIndex == baseIndex && repeatMode != Player.REPEAT_MODE_ALL)) return

        val nextSong = queue[nextIndex]

        // Double check it's not already in the player queue to avoid duplicates
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId == nextSong.videoId) return
        }

        isPrefetching = true
        scheduledIndex = nextIndex
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaveMuzix:Prefetch")

        ioScope.launch {
            try {
                wakeLock?.acquire(60000L)
                Log.d("MusicPlayer", "Background Prefetching: ${nextSong.title} (Items Ahead: $itemsAhead)")
                val targetBitrate = null
                
                val priority = if (settingsManager?.isDataSaverEnabled == true) {
                    "Data Saver"
                } else {
                    "Force OPUS 160kbps"
                }
                val preferredQuality = settingsManager?.preferredVideoQuality ?: "auto"
                val result = unifiedRepository?.getStreamingUrl(
                    videoId = nextSong.videoId,
                    cookies = cookies,
                    failedClients = emptyList(),
                    targetBitrate = targetBitrate,
                    priority = priority,
                    preferredVideoQuality = preferredQuality
                )
                if (result != null) {
                    withContext(Dispatchers.Main) {
                        // Re-verify room in queue before adding
                        val freshItemsAhead = player.mediaItemCount - 1 - player.currentMediaItemIndex
                        if (freshItemsAhead < 2) {
                        val metadata = createMediaMetadata(nextSong)

                            val mediaItem = MediaItem.Builder()
                                .setUri(result.url)
                                .setMediaId(nextSong.videoId)
                                .setMediaMetadata(metadata)
                                .setMimeType(result.mimeType)
                                .setTag(result)
                                .build()

                            player.addMediaItem(mediaItem)
                            prefetchedVideoId = nextSong.videoId

                            // Pre-fetch metadata (details and colors) for the next song
                            prefetchMetadata(context, nextSong)

                            // Pre-fetch SponsorBlock segments to eliminate skip latency
                            fetchSponsorSegments(nextSong.videoId, isPrefetch = true)

                            // Pre-fetch lyrics for the song we just queued up
                            ioScope.launch {
                                val provider = settingsManager?.lyricsProvider ?: "Auto"
                                lyricsRepository.fetchLyrics(nextSong, preferredProvider = provider, dao = database?.songDao())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayer", "Prefetch failed", e)
            } finally {
                isPrefetching = false
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }

    fun clearLyrics() {
        lyricsText = null
        lyricsProvider = null
        isLyricsLoading = false
    }

    private fun startLyricsFetch(song: Song) {
        ioScope.launch {
            if (currentSong?.videoId == song.videoId) {
                // Check cache first to avoid setting isLyricsLoading to true
                val cached = lyricsRepository.getCachedLyrics(song.videoId)
                if (cached != null) {
                    lyricsText = cached
                    lyricsProvider = lyricsRepository.getCachedProvider(song.videoId)
                    isLyricsLoading = false
                    return@launch
                }

                isLyricsLoading = true
                val provider = settingsManager?.lyricsProvider ?: "Auto"
                val result = lyricsRepository.fetchLyrics(song, preferredProvider = provider, dao = database?.songDao())
                if (currentSong?.videoId == song.videoId) {
                    lyricsText = result?.lyrics
                    lyricsProvider = result?.provider
                    isLyricsLoading = false
                }
            }
        }
    }

    fun playPreviousInQueue(context: Context) {
        if (queue.isEmpty()) return

        val player = activePlayer
        val currentPos = player?.currentPosition ?: 0L

        // Standard player behavior: if more than 3s in, restart current song
        if (currentPos > 3000) {
            player?.seekTo(0)
            Log.d("MusicPlayer", "playPrevious: Restarting current song (pos: $currentPos)")
            return
        }

        // Logic check: if index is out of bounds due to queue change, reset to 0
        if (currentIndex < 0 || currentIndex >= queue.size) {
            currentIndex = 0
        }

        if (shuffleEnabled) {
            if (shuffledIndices.isEmpty() || !shuffledIndices.contains(currentIndex)) generateShuffledIndices()
            val currentInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentInShuffled > 0) {
                playAtIndex(context, shuffledIndices[currentInShuffled - 1])
            } else if (this.repeatMode == Player.REPEAT_MODE_ALL && shuffledIndices.isNotEmpty()) {
                playAtIndex(context, shuffledIndices.last())
            } else {
                player?.seekTo(0)
            }
        } else {
            if (currentIndex > 0) {
                playAtIndex(context, currentIndex - 1)
            } else if (this.repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) {
                playAtIndex(context, queue.size - 1)
            } else {
                player?.seekTo(0)
            }
        }
    }

    fun seekTo(cleanPosition: Long) {
        val player = activePlayer ?: return
        volumeFadeJob?.cancel()
        isFadingForSkip = false
        player.volume = baseVolume * userVolume
        // Reset skip state to allow the mapping logic to take full control of this seek
        lastSponsorSeekTarget = -1L
        lastSponsorSeekAttemptTime = 0L
        cleanPositionAtSkipTrigger = 0L
        skipTriggerTime = 0L

        val activeSegments = if (settingsManager?.isSponsorBlockEnabled == true) mergedSponsorSegments else emptyList()
        var rawPosition = cleanPosition

        // Translate Clean UI position back to Raw Player position.
        for (seg in activeSegments) {
            if (rawPosition >= seg.first - 500) {
                rawPosition += (seg.second - seg.first)
            } else {
                break
            }
        }

        Log.d("MusicPlayer", "SeekTo Mapping: Clean $cleanPosition -> Raw $rawPosition")
        player.seekTo(rawPosition)

        // Reset video sync on seek to prevent stuttering/mismatch during the jump
        if (isVideoAvailable) {
            videoSyncStartTime = System.currentTimeMillis() + 1500 // Slightly shorter delay for manual seeks
            videoPlayer?.playWhenReady = false
            videoPlayer?.seekTo(rawPosition)
        }
    }

    fun togglePlayPause(context: Context) {
        val player = getInstance(context)
        if (player.isPlaying) {
            player.pause()
            videoPlayer?.pause()
            stopProgressUpdate()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                // If the player is idle or ended, we might want to reload the current song or next song.
                // However, for simplicity, if currentSong exists, just prepare and play.
                player.prepare()
            }
            player.play()
            if (isVideoAvailable && isPlayerViewVisible) {
                videoPlayer?.play()
            }
            startProgressUpdate(context)

            // Ensure PlaybackService is running to maintain background continuity
            ensureServiceStarted(context)
        }
    }

    fun stopPlayback() {
        activePlayer?.stop()
        activePlayer?.clearMediaItems()
        inactivePlayer?.stop()
        inactivePlayer?.clearMediaItems()
        videoPlayer?.stop()
        videoPlayer?.clearMediaItems()
        clearState()
        stopProgressUpdate()
    }

    fun playOffline(
        context: Context,
        filePath: String,
        song: Song,
        mimeType: String? = null,
        playlist: List<Song> = emptyList(),
        isFromFixedList: Boolean = false
    ) {
        releaseVisualizer()
        currentSong = song
        currentServer = "OFFLINE"
        isAutoCurationEnabled = !isFromFixedList
        Log.d("MusicPlayer", "playOffline: Path=$filePath, Title=${song.title}, MimeType=$mimeType")

        // Ensure file actually exists before trying to play
        val file = java.io.File(filePath)
        if (!file.exists()) {
            Log.e("MusicPlayer", "playOffline FAILED: File does not exist at $filePath")
            // Fallback to streaming if file is missing
            fetchAndPlay(context, song)
            return
        }

        // Handle Queue
        if (playlist.isNotEmpty()) {
            queue.clear()
            queue.addAll(playlist)
            currentIndex = playlist.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        } else {
            if (queue.none { it.videoId == song.videoId }) {
                if (currentIndex != -1 && currentIndex < queue.size) {
                    queue.add(currentIndex + 1, song)
                    currentIndex++
                } else {
                    queue.add(song)
                    currentIndex = queue.size - 1
                }
            } else {
                currentIndex = queue.indexOfFirst { it.videoId == song.videoId }
            }
        }

        val player = getInstance(context)

        val metadata = createMediaMetadata(song)

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId(song.videoId)
            .setMimeType(mimeType ?: "audio/mpeg") // Use stored mimeType or fallback to mp3
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem, true) // Reset position

        // Reset volume for offline playback to avoid carrying over normalization from previous streams
        baseVolume = 1.0f
        player.volume = 0f
        animateVolume(player, baseVolume * userVolume, 2000L)

        // Track most played removed from here - now at 50% in progress update

        refreshQueueIfLow()

        player.prepare()
        player.play()

        isLoading = false
        isPlaying = true
        consecutiveErrors = 0 // Reset errors on successful load
        startProgressUpdate(context)

        ensureServiceStarted(context)
    }

    fun startSleepTimer(minutes: Int) {
        stopAfterCurrentSong = false
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            sleepTimerMillis = 0L
            return
        }

        val durationMs = minutes * 60 * 1000L
        sleepTimerMillis = System.currentTimeMillis() + durationMs

        sleepTimerJob = mainScope.launch {
            delay(durationMs)
            activePlayer?.pause()
            sleepTimerMillis = 0L
        }
    }

    fun toggleStopAfterCurrentSong(enabled: Boolean) {
        if (enabled) {
            cancelSleepTimer()
        }
        stopAfterCurrentSong = enabled
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerMillis = 0L
        stopAfterCurrentSong = false
    }

    fun updateDvcState() {
        mainScope.launch {
            val settings = settingsManager ?: return@launch
            val audioAttributesBuilder = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)

            if (settings.isDirectVolumeControlEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // AudioAttributes.FLAG_AUDIOPROCESSING_CONTROL_BYPASS = 0x200000
                audioAttributesBuilder.setFlags(0x200000)
            }
            val audioAttributes = audioAttributesBuilder.build()
            activePlayer?.setAudioAttributes(audioAttributes, true)
            inactivePlayer?.setAudioAttributes(audioAttributes, true)
            Log.d("MusicPlayer", "DVC State updated: ${settings.isDirectVolumeControlEnabled}")
        }
    }

    fun updateAudioEffects() {
        activePlayer?.let { player ->
            val result = player.currentMediaItem?.localConfiguration?.tag as? StreamingResult
            applyAudioEffects(player, result?.loudnessDb)
        }
    }

    fun updateVisualizerState() {
        if (settingsManager?.isVisualizerEnabled == true && isPlayerViewVisible) {
            setupVisualizer()
        } else {
            releaseVisualizer()
        }
    }

    fun toggleVisualizer(enabled: Boolean) {
        updateVisualizerState()
    }

    fun updateSponsorBlockSettings() {
        mainScope.launch {
            if (settingsManager?.isSponsorBlockEnabled == false) {
                sponsorSegments.clear()
                mergedSponsorSegments = emptyList()
                lastSponsorSeekTarget = -1L
                isFadingForSkip = false
                activePlayer?.volume = baseVolume * userVolume
                sponsorSegmentsCache.clear() // Clear cache when disabled
                Log.d("MusicPlayer", "SponsorBlock disabled instantly.")
            } else {
                refreshSponsorSegments()
            }
        }
    }

    fun updateCrossfadeState(context: Context) {
        activePlayer?.let { scheduleBackgroundTriggers(context, it, force = true) }
    }

    fun updateVideoPlaybackState() {
        mainScope.launch {
            val shouldPlayVideo = isVideoAvailable && settingsManager?.isPlayVideoIfAvailable == true && isPlayerViewVisible
            if (!shouldPlayVideo) {
                videoPlayer?.pause()
                isVideoActuallyPlaying = false
            }
        }
    }

    fun refreshSponsorSegments() {
        currentSong?.videoId?.let { 
            sponsorSegmentsCache.remove(it)
            fetchSponsorSegments(it) 
        }
    }

    private fun fetchSponsorSegments(videoId: String, isPrefetch: Boolean = false) {
        if (settingsManager?.isSponsorBlockEnabled != true) {
            return
        }

        // Check cache first to eliminate latency
        sponsorSegmentsCache[videoId]?.let { cached ->
            if (!isPrefetch) {
                mainScope.launch {
                    sponsorSegments.clear()
                    mergedSponsorSegments = cached
                    lastSponsorSeekTarget = -1L
                    activePlayer?.let { player ->
                        scheduleSponsorMessages(player, cached)
                        // Proactive skip check for segments at the very start
                        val currentMs = player.currentPosition
                        val startSegment = cached.find { currentMs >= it.first - 300 && currentMs < it.second - 100 }
                        if (startSegment != null) {
                            val skipTo = startSegment.second
                            lastSponsorSeekTarget = skipTo
                            lastSponsorSeekAttemptTime = System.currentTimeMillis()
                            player.volume = 0f
                            isFadingForSkip = true
                            player.seekTo(skipTo)
                            Log.d("MusicPlayer", "SponsorBlock: Initial skip to $skipTo")
                        }
                    }
                    Log.d("MusicPlayer", "SponsorBlock: Loaded from cache for $videoId")
                }
            }
            return
        }

        if (!isPrefetch) {
            // Clear previous segments immediately to avoid UI/logic ghosting
            sponsorSegments.clear()
            mergedSponsorSegments = emptyList()
            lastSponsorSeekTarget = -1L // Ensure no pending skip carries over
        }

        ioScope.launch {
            try {
                // The Cockpit: User-defined categories
                val categoryList = mutableListOf<String>()
                if (settingsManager?.skipSponsorMentions == true) categoryList.add("sponsor")
                if (settingsManager?.skipIntroOutro == true) {
                    categoryList.add("intro")
                    categoryList.add("outro")
                }
                if (settingsManager?.skipNonMusicSkits == true) {
                    categoryList.add("selfpromo")
                    categoryList.add("interaction")
                    categoryList.add("preview")
                    // Dialogue skipper integrated here
                    categoryList.add("music_offtopic")
                    categoryList.add("filler")
                }

                if (categoryList.isEmpty()) return@launch
                // Correctly formatted JSON array without extra spaces for API compatibility
                val categories = "[${categoryList.joinToString(",") { "\"$it\"" }}]"

                val response = RetrofitClient.sponsorBlockInstance.getSkipSegments(videoId, categories)

                if (response.isSuccessful) {
                    val segments = response.body() ?: emptyList()
                    
                    // Filter for "Edge skipping" (first/last 10s) to avoid middle-track interruptions
                    val songDuration = (queue.find { it.videoId == videoId } ?: currentSong?.takeIf { it.videoId == videoId })
                        ?.let { parseDurationToMs(it.durationText) } ?: 0L
                        
                    val merged = mergeSponsorSegments(segments, songDuration)
                    sponsorSegmentsCache[videoId] = merged

                    if (!isPrefetch && currentSong?.videoId == videoId) {
                        withContext(Dispatchers.Main) {
                            sponsorSegments.addAll(segments)
                            mergedSponsorSegments = merged
                            Log.d("MusicPlayer", "SponsorBlock: Added ${segments.size} segments for $videoId")
                            
                            activePlayer?.let { player ->
                                scheduleSponsorMessages(player, merged)

                                val currentPos = player.currentPosition
                                // Immediate skip if we are already inside a segment at load time
                                val segmentToSkip = merged.find {
                                    currentPos >= it.first - 300 && currentPos < it.second - 100
                                }
                                if (segmentToSkip != null) {
                                    val skipToMs = segmentToSkip.second
                                    if (lastSponsorSeekTarget != skipToMs) {
                                        cleanPositionAtSkipTrigger = currentPosition
                                        skipTriggerTime = System.currentTimeMillis()
                                        lastSponsorSeekTarget = skipToMs
                                        lastSponsorSeekAttemptTime = skipTriggerTime
                                        Log.d("MusicPlayer", "SponsorBlock: Immediate skip to $skipToMs")
                                        player.volume = 0f
                                        isFadingForSkip = true
                                        player.seekTo(skipToMs)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.d("MusicPlayer", "SponsorBlock: No segments found for $videoId (Code: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("MusicPlayer", "SponsorBlock: Failed to fetch segments for $videoId", e)
            }
        }
    }

    private fun scheduleSponsorMessages(player: ExoPlayer, mergedSegments: List<Pair<Long, Long>>) {
        mergedSegments.forEach { (startMs, endMs) ->
            // Trigger 250ms BEFORE the segment starts for a snappy transition
            val triggerPosition = (startMs - 250).coerceAtLeast(0)
            player.createMessage(object : PlayerMessage.Target {
                override fun handleMessage(messageType: Int, payload: Any?) {
                    if (settingsManager?.isSponsorBlockEnabled != true) return

                    if (lastSponsorSeekTarget != endMs) {
                        lastSponsorSeekTarget = endMs
                        cleanPositionAtSkipTrigger = currentPosition
                        skipTriggerTime = System.currentTimeMillis()

                        // Ultra-snappy skip: Instant mute and jump to eliminate transition lag
                        isFadingForSkip = true
                        player.volume = 0f
                        // Use NEXT_SYNC to ensure we land definitively past the sponsor segment
                        player.setSeekParameters(SeekParameters.NEXT_SYNC)
                        player.seekTo(endMs)
                        
                        // Small 50ms fade out to clean up any remaining buffer audio
                        animateVolume(player, 0f, 50)
                        Log.d("MusicPlayer", "SponsorBlock: Bypassed segment to $endMs")
                    }
                }
            })
            .setLooper(Looper.getMainLooper())
            .setPosition(triggerPosition)
            .setDeleteAfterDelivery(true)
            .send()
        }
    }

    private fun getNextIndex(fromIndex: Int = currentIndex): Int {
        if (queue.isEmpty()) return -1
        return if (shuffleEnabled) {
            if (shuffledIndices.isEmpty() || !shuffledIndices.contains(fromIndex)) generateShuffledIndices()
            val currentInShuffled = shuffledIndices.indexOf(fromIndex)
            if (currentInShuffled != -1 && currentInShuffled + 1 < shuffledIndices.size) {
                shuffledIndices[currentInShuffled + 1]
            } else if (this.repeatMode == Player.REPEAT_MODE_ALL && shuffledIndices.isNotEmpty()) {
                // Return index 0 of the shuffled list
                shuffledIndices[0]
            } else {
                -1
            }
        } else {
            if (fromIndex + 1 < queue.size) {
                fromIndex + 1
            } else if (this.repeatMode == Player.REPEAT_MODE_ALL) {
                0
            } else {
                -1
            }
        }
    }

    private fun mergeSponsorSegments(segments: List<SponsorSegment>, totalDurationMs: Long): List<Pair<Long, Long>> {
        if (segments.isEmpty()) return emptyList()

        // Filter: Only skip segments that occur within the first or last 10 seconds of the track.
        // This targets dialogues (intros/outros) and edge sponsor mentions while preserving music sections.
        val filtered = segments.filter { seg ->
            val startMs = (seg.segment[0] * 1000).toLong()
            val endMs = (seg.segment[1] * 1000).toLong()

            val isAtBeginning = startMs < 10000
            val isAtEnd = totalDurationMs > 10000 && endMs > (totalDurationMs - 10000)

            isAtBeginning || isAtEnd
        }

        if (filtered.isEmpty()) return emptyList()

        val sorted = filtered.map {
            (it.segment[0] * 1000).toLong().coerceAtLeast(0) to
            (it.segment[1] * 1000).toLong()
        }.sortedBy { it.first }

        val merged = mutableListOf<Pair<Long, Long>>()
        var currentStart = sorted[0].first
        var currentEnd = sorted[0].second

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.first <= currentEnd) {
                currentEnd = maxOf(currentEnd, next.second)
            } else {
                merged.add(currentStart to currentEnd)
                currentStart = next.first
                currentEnd = next.second
            }
        }
        merged.add(currentStart to currentEnd)
        return merged
    }

    private fun parseDurationToMs(duration: String?): Long {
        if (duration == null) return 0L
        return try {
            val parts = duration.split(":").map { it.toLongOrNull() ?: 0L }
            when (parts.size) {
                1 -> parts[0] * 1000
                2 -> (parts[0] * 60 + parts[1]) * 1000
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun scheduleBackgroundTriggers(context: Context, player: ExoPlayer, force: Boolean = false) {
        val songId = currentSong?.videoId ?: return
        if (!force && scheduledVideoId == songId) return

        scheduledVideoId = songId

        mainScope.launch {
            // Wait for duration to be known (especially important for gapless transitions)
            var attempts = 0
            while (player.duration <= 1000 && attempts < 50) {
                delay(100)
                attempts++
            }

            val duration = player.duration
            if (duration <= 1000) {
                Log.w("MusicPlayer", "Could not schedule triggers: Duration unknown for $songId")
                scheduledVideoId = null // Allow retry
                return@launch
            }

            Log.d("MusicPlayer", "Scheduling triggers for $songId (Duration: $duration)")

            // Gapless Pre-fetch Trigger (30s before end)
            if (settingsManager?.isCrossfadeEnabled == false && duration > 40000) {
                player.createMessage(object : PlayerMessage.Target {
                    override fun handleMessage(messageType: Int, payload: Any?) {
                        Log.d("MusicPlayer", "Trigger: Prefetch Next Song")
                        prefetchNextSong(context)
                    }
                })
                .setLooper(Looper.getMainLooper())
                .setPosition(duration - 30000)
                .setDeleteAfterDelivery(true)
                .send()
            }

            // Crossfade Trigger (Dynamic threshold based on dB setting)
            val thresholdDb = settingsManager?.crossfadeThresholdDb ?: -10f
            val crossfadeDuration = (Math.abs(thresholdDb) * 500).toLong().coerceIn(2000, 15000)

            if (settingsManager?.isCrossfadeEnabled == true && duration > (crossfadeDuration + 2000) && repeatMode != Player.REPEAT_MODE_ONE) {
                player.createMessage(object : PlayerMessage.Target {
                    override fun handleMessage(messageType: Int, payload: Any?) {
                        Log.d("MusicPlayer", "Trigger: Start Crossfade (Threshold: $thresholdDb dB)")
                        startCrossfade(context)
                    }
                })
                .setLooper(Looper.getMainLooper())
                .setPosition(duration - crossfadeDuration)
                .setDeleteAfterDelivery(true)
                .send()
            }
        }
    }

}
