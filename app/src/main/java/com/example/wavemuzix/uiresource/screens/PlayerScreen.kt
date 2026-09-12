package com.example.wavemuzix.uiresource.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.SliderDefaults
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.data.DownloadWorker
import com.example.wavemuzix.data.LocalPerformanceConfig
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.data.database.AppDatabase
import com.example.wavemuzix.data.database.PlaylistEntity
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.repository.*
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.*
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@UnstableApi
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    onSearchClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    isLiquidGlass: Boolean,
    backdrop: LayerBackdrop? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val activeSong = MusicPlayer.currentSong
    val isPlaying = MusicPlayer.isPlaying
    val currentPosition = MusicPlayer.currentPosition
    val duration = MusicPlayer.duration
    val context = LocalContext.current
    val performance = LocalPerformanceConfig.current
    val playlistRepository = remember {
        PlaylistRepository(AppDatabase.getDatabase(context).playlistDao(), context)
    }
    val scope = rememberCoroutineScope()
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

    var showLyrics by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    // Centralized states from MusicPlayer
    val videoDetails by remember { derivedStateOf { MusicPlayer.videoDetails } }
    val isLoadingDetails by remember { derivedStateOf { MusicPlayer.isDetailsLoading } }
    val accentColors by remember { derivedStateOf { MusicPlayer.accentColors } }
    val color1 = accentColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val color2 = Color(
        red = color1.red * 0.28f,
        green = color1.green * 0.28f,
        blue = color1.blue * 0.28f,
        alpha = 1f
    )

    var showMoreOptions by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var showPlaylistSelection by remember { mutableStateOf(false) }

    val anySheetVisible = showLyrics || showDetails || showMoreOptions || showSleepTimerMenu || showPlaylistSelection
    if (anySheetVisible) { /* Suppress unused */ }
    if (anySheetVisible) { /* Suppress unused */ }
    // STABILITY FIX: Removed root scaling/alpha animations to prevent SIGSEGV in hardware layers

    val pagerState = rememberPagerState(
        initialPage = if (MusicPlayer.queue.isEmpty()) 0 else (MusicPlayer.currentIndex).coerceIn(0, MusicPlayer.queue.size - 1),
        pageCount = { MusicPlayer.queue.size }
    )

    // Synchronization logic to prevent "sync-wars" and restoration bugs
    var isLaunched by remember { mutableStateOf(false) }

    LaunchedEffect(MusicPlayer.currentIndex) {
        if (MusicPlayer.currentIndex != -1 && MusicPlayer.queue.isNotEmpty()) {
            val targetPage = MusicPlayer.currentIndex.coerceIn(0, MusicPlayer.queue.size - 1)
            if (pagerState.currentPage != targetPage) {
                if (!isLaunched) {
                    try {
                        pagerState.scrollToPage(targetPage)
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "Failed to scroll to page: $targetPage", e)
                    }
                } else {
                    try {
                        pagerState.animateScrollToPage(
                            page = targetPage,
                            animationSpec = spring(
                                dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.8f,
                                stiffness = if (performance.isLowSpec) performance.springStiffness else Spring.StiffnessLow,
                                visibilityThreshold = 0.001f
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "Failed to animate scroll to page: $targetPage", e)
                    }
                }
            }
            isLaunched = true
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (isLaunched && pagerState.settledPage != MusicPlayer.currentIndex && MusicPlayer.currentIndex != -1 && MusicPlayer.queue.isNotEmpty()) {
            MusicPlayer.playAtIndex(context, pagerState.settledPage)
        }
    }

    // Lyrics State from MusicPlayer
    val lyricsText by remember { derivedStateOf {
        if (MusicPlayer.isLyricsLoading) "Searching for lyrics..."
        else MusicPlayer.lyricsText ?: "Lyrics not available for this track."
    } }
    val lyricsProviderName by remember { derivedStateOf { MusicPlayer.lyricsProvider ?: "" } }

    val settingsManager = remember { SettingsManager(context) }
    val playerType = settingsManager.playerType

    if (playerType == "Material 3") {
        Material3PlayerScreen(
            onBack = onBack,
            onQueueClick = onQueueClick,
            onSearchClick = onSearchClick,
            onArtistClick = onArtistClick,
            isLiquidGlass = isLiquidGlass,
            hazeState = hazeState,
            backdrop = backdrop,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    } else {
        ClassicPlayerScreen(
            onBack = onBack,
            onQueueClick = onQueueClick,
            onSearchClick = onSearchClick,
            onArtistClick = onArtistClick,
            isLiquidGlass = isLiquidGlass,
            hazeState = hazeState,
            backdrop = backdrop,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@UnstableApi
@Composable
fun ClassicPlayerScreen(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    onSearchClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    isLiquidGlass: Boolean,
    hazeState: dev.chrisbanes.haze.HazeState,
    backdrop: LayerBackdrop? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val activeSong = MusicPlayer.currentSong
    val isPlaying = MusicPlayer.isPlaying
    val currentPosition = MusicPlayer.currentPosition
    val duration = MusicPlayer.duration
    val context = LocalContext.current
    val performance = LocalPerformanceConfig.current
    val playlistRepository = remember {
        PlaylistRepository(AppDatabase.getDatabase(context).playlistDao(), context)
    }
    val scope = rememberCoroutineScope()

    var showLyrics by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    // Centralized states from MusicPlayer
    val videoDetails by remember { derivedStateOf { MusicPlayer.videoDetails } }
    val isLoadingDetails by remember { derivedStateOf { MusicPlayer.isDetailsLoading } }
    val accentColors by remember { derivedStateOf { MusicPlayer.accentColors } }
    val color1 = accentColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val color2 = Color(
        red = color1.red * 0.28f,
        green = color1.green * 0.28f,
        blue = color1.blue * 0.28f,
        alpha = 1f
    )

    var showMoreOptions by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var showPlaylistSelection by remember { mutableStateOf(false) }

    // STABILITY FIX: Removed root scaling/alpha animations to prevent SIGSEGV in hardware layers
    val anySheetVisible = showLyrics || showDetails || showMoreOptions || showSleepTimerMenu || showPlaylistSelection
    if (anySheetVisible) { /* Suppress unused */ }
    if (anySheetVisible) { /* Suppress unused */ }

    val pagerState = rememberPagerState(
        initialPage = if (MusicPlayer.queue.isEmpty()) 0 else (MusicPlayer.currentIndex).coerceIn(0, MusicPlayer.queue.size - 1),
        pageCount = { MusicPlayer.queue.size }
    )

    // Synchronization logic to prevent "sync-wars" and restoration bugs
    var isLaunched by remember { mutableStateOf(false) }

    LaunchedEffect(MusicPlayer.currentIndex) {
        if (MusicPlayer.currentIndex != -1 && MusicPlayer.queue.isNotEmpty()) {
            val targetPage = MusicPlayer.currentIndex.coerceIn(0, MusicPlayer.queue.size - 1)
            if (pagerState.currentPage != targetPage) {
                if (!isLaunched) {
                    try {
                        pagerState.scrollToPage(targetPage)
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "Failed to scroll to page: $targetPage", e)
                    }
                } else {
                    try {
                        pagerState.animateScrollToPage(
                            page = targetPage,
                            animationSpec = spring(
                                dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.8f,
                                stiffness = if (performance.isLowSpec) performance.springStiffness else Spring.StiffnessLow,
                                visibilityThreshold = 0.001f
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "Failed to animate scroll to page: $targetPage", e)
                    }
                }
            }
            isLaunched = true
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (isLaunched && pagerState.settledPage != MusicPlayer.currentIndex && MusicPlayer.currentIndex != -1 && MusicPlayer.queue.isNotEmpty()) {
            MusicPlayer.playAtIndex(context, pagerState.settledPage)
        }
    }

    // Lyrics State from MusicPlayer
    val lyricsText by remember { derivedStateOf {
        if (MusicPlayer.isLyricsLoading) "Searching for lyrics..."
        else MusicPlayer.lyricsText ?: "Lyrics not available for this track."
    } }
    val lyricsProviderName by remember { derivedStateOf { MusicPlayer.lyricsProvider ?: "" } }

    val settingsManager = remember { SettingsManager(context) }
    var showPlayerTutorial by remember { mutableStateOf(!settingsManager.hasSeenPlayerTutorial) }

    val isPlayVideoEnabled = settingsManager.isPlayVideoIfAvailable

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = isLandscape) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    DisposableEffect(Unit) {
        MusicPlayer.isPlayerViewVisible = true
        MusicPlayer.updateVideoPlaybackState()
        MusicPlayer.updateVisualizerState()
        onDispose {
            MusicPlayer.isPlayerViewVisible = false
            MusicPlayer.updateVideoPlaybackState()
            MusicPlayer.releaseVisualizer()
        }
    }

    val isFavorite = MusicPlayer.isFavorite
    val isDownloaded = MusicPlayer.isDownloaded

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLandscape) {
            LandscapePlayerOverlay(
                onBack = {
                    val activity = context as? Activity
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                },
                isLiquidGlass = isLiquidGlass
            )
        } else {
            if (activeSong != null) {
                val isAmbientEnabled = settingsManager.isAmbientBackgroundEnabled
                if (isAmbientEnabled) {
                    AmbientFluidBackground(
                        accentColors = accentColors,
                        amplitudeProvider = { MusicPlayer.currentVolumeAmplitude },
                        bassAmplitudeProvider = { MusicPlayer.currentBassAmplitude },
                        trebleAmplitudeProvider = { MusicPlayer.currentTrebleAmplitude },
                        playbackSpeedProvider = { MusicPlayer.playbackSpeed },
                        isPlayingProvider = { MusicPlayer.isPlaying }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                // Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        onClick = onBack,
                        isLiquidGlass = isLiquidGlass,
                        contentDescription = "Close",
                        containerSize = 44.dp,
                        iconSize = 32.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PLAYING FROM SEARCH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            MusicPlayer.sanitizedArtistName.ifEmpty { activeSong?.artist ?: "Unknown Artist" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GlassIconButton(
                        icon = Icons.Default.Search,
                        onClick = onSearchClick,
                        isLiquidGlass = isLiquidGlass,
                        contentDescription = "Search",
                        containerSize = 44.dp,
                        iconSize = 28.dp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Premium Album Art Carousel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (MusicPlayer.isVideoAvailable && isPlayVideoEnabled && !showLyrics) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(percent = 4))) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = MusicPlayer.getVideoPlayer(ctx)
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

                                        // STABILITY FIX: Force TextureView to prevent SIGSEGV during transformations
                                        try {
                                            val surfaceTypeField = PlayerView::class.java.getDeclaredField("surfaceType")
                                            surfaceTypeField.isAccessible = true
                                            surfaceTypeField.set(this, 2) // 2 = TEXTURE_VIEW
                                        } catch (e: Exception) {}
                                    }
                                },
                                update = { view ->
                                    val currentPlayer = MusicPlayer.getVideoPlayer(view.context)
                                    if (view.player != currentPlayer) {
                                        view.player = currentPlayer
                                    }
                                },
                                onRelease = { view ->
                                    view.player = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (!MusicPlayer.isVideoActuallyPlaying) {
                                val currentSong = MusicPlayer.currentSong
                                GlideImage(
                                    model = MusicPlayer.highResThumbnailUrl ?: currentSong?.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 0.dp),
                            pageSpacing = 16.dp,
                            beyondViewportPageCount = 1
                        ) { page ->
                            val song = MusicPlayer.queue.getOrNull(page)
                            val isCurrentPage = page == MusicPlayer.currentIndex

                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            val pageOffsetAbs = abs(pageOffset)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    /* .then(
                                        if (isCurrentPage && sharedTransitionScope != null && animatedVisibilityScope != null) {
                                            with(sharedTransitionScope) {
                                                Modifier.sharedElement(
                                                    rememberSharedContentState(key = "artwork"),
                                                    animatedVisibilityScope = animatedVisibilityScope
                                                )
                                            }
                                        } else Modifier
                                    ) */
                                    .graphicsLayer {
                                        // STABILITY FIX: Highly subtle scaling and no alpha variance
                                        val scale = 0.99f + (1f - 0.99f) * (1f - pageOffsetAbs.coerceIn(0f, 1f))
                                        scaleX = scale
                                        scaleY = scale

                                        compositingStrategy = if (performance.useCompositingStrategyOffscreen)
                                            CompositingStrategy.Offscreen else CompositingStrategy.Auto
                                    },
                                shape = RoundedCornerShape(percent = 4),
                                shadowElevation = 4.dp,
                                color = Color.Black
                            ) {
                                val model = if (isCurrentPage) (MusicPlayer.highResThumbnailUrl ?: song?.thumbnailUrl) else song?.thumbnailUrl

                                GlideImage(
                                    model = model,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                if (MusicPlayer.isVideoAvailable && isPlayVideoEnabled && !showLyrics) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassIconButton(
                        icon = Icons.Default.Fullscreen,
                        onClick = {
                            val activity = context as? Activity
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        isLiquidGlass = isLiquidGlass,
                        contentDescription = "Landscape Mode",
                        iconSize = 28.dp,
                        containerSize = 48.dp,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Song Info & Title
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSong?.title ?: "No Song Selected",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = MusicPlayer.sanitizedArtistName.ifEmpty { activeSong?.artist ?: "Select a track to play" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1
                            )

                            // Artist Source Attribution
                            val source = MusicPlayer.artistAvatarSource
                            if (!source.isNullOrEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (source == "Spotify") Icons.Default.Info else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (source == "Spotify") Color(0xFF1DB954) else Color.Red,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "via $source",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            MusicPlayer.toggleFavorite(context)
                        }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val sliderPosition = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                Slider(
                    value = sliderPosition,
                    onValueChange = { newValue ->
                        if (duration > 0) {
                            MusicPlayer.seekTo((newValue * duration).toLong())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
                        activeTrackColor = accentColors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(duration), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { MusicPlayer.shuffleEnabled = !MusicPlayer.shuffleEnabled }) {
                        Icon(
                            Icons.Default.Shuffle,
                            null,
                            tint = if (MusicPlayer.shuffleEnabled) MaterialTheme.colorScheme.primary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { MusicPlayer.playPreviousInQueue(context) },
                            shape = CircleShape,
                            color = color2,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    if (!MusicPlayer.isLoading) {
                                        MusicPlayer.togglePlayPause(context)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (MusicPlayer.isLoading && !isPlaying) {
                                    StandardLoader(color = DeepBlack, size = 36.dp)
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = DeepBlack,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Surface(
                            onClick = { MusicPlayer.playNextInQueue(context) },
                            shape = CircleShape,
                            color = color2,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    IconButton(onClick = {
                        MusicPlayer.repeatMode = when (MusicPlayer.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }) {
                        Icon(
                            imageVector = if (MusicPlayer.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (MusicPlayer.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionIcon(Icons.Default.Info, "Details") { showDetails = true }
                    ActionIcon(Icons.Default.Lyrics, "Lyrics") { showLyrics = !showLyrics }
                    ActionIcon(Icons.AutoMirrored.Filled.PlaylistPlay, "Queue") { onQueueClick() }
                    ActionIcon(Icons.Default.Share, "Share") {
                        activeSong?.let { song ->
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Listen to ${song.title} by ${song.artist} on WaveMuzix: https://www.youtube.com/watch?v=${song.videoId}")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    }
                    ActionIcon(Icons.Default.MoreHoriz, "More") { showMoreOptions = true }
                }
            }
                }
                item {
                    LyricsPreviewBox(
                        lyrics = lyricsText,
                        provider = lyricsProviderName,
                        currentPosition = currentPosition,
                        backgroundColor = color2,
                        onShowClick = { showLyrics = true }
                    )
                }

                // Channel Branding Section
                item {
                    val currentDetails = videoDetails
                    val artistAvatar = MusicPlayer.artistAvatarUrl
                    if ((currentDetails != null && currentDetails.channelThumbnailUrl.isNotEmpty()) || !artistAvatar.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp, bottom = 32.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(28.dp))
                        ) {
                            // Artist Image directly with Haze Source
                            GlideImage(
                                model = artistAvatar ?: currentDetails?.channelThumbnailUrl,
                                contentDescription = "Artist/Channel Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(state = hazeState),
                                contentScale = ContentScale.Crop
                            )

                            val canShowHaze = isLiquidGlass && performance.isLiquidGlassEnabled

                            // Transparent Haze Container above the banner
                            if (canShowHaze) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .hazeEffect(state = hazeState) {
                                            progressive = HazeProgressive.verticalGradient(
                                                startIntensity = 0f, // Clear at top
                                                endIntensity = 1f    // Blurred at bottom
                                            )
                                            style = HazeStyle(
                                                blurRadius = 30.dp,
                                                tint = HazeTint(Color.Black.copy(alpha = 0.45f))
                                            )
                                        }
                                )
                            }

                            // Bottom-fade blur scrim for text readability
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f)
                                    .align(Alignment.BottomCenter)
                                    .then(
                                        if (!canShowHaze) {
                                            Modifier.background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.4f),
                                                        Color.Black.copy(alpha = 0.7f),
                                                        Color.Black.copy(alpha = 0.9f)
                                                    ),
                                                )
                                            )
                                        } else Modifier
                                    )
                                    .padding(20.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Column {
                                    Text(
                                        text = if (!artistAvatar.isNullOrEmpty()) MusicPlayer.sanitizedArtistName else currentDetails?.channelName ?: MusicPlayer.sanitizedArtistName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = currentDetails?.subscriberCountText ?: "Official Artist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            LyricsOverlay(
                lyrics = lyricsText,
                provider = lyricsProviderName,
                currentPosition = currentPosition,
                backgroundColor = color2,
                isLiquidGlass = isLiquidGlass,
                onDismiss = { showLyrics = false }
            )
        }

        if (showDetails) {
            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalPreferHaze provides false
            ) {
                DetailsBottomSheet(
                    details = videoDetails,
                    isLoading = isLoadingDetails,
                    isLiquidGlass = isLiquidGlass,
                    onDismiss = { showDetails = false }
                )
            }
        }

        if (showMoreOptions && activeSong != null) {
            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalPreferHaze provides false
            ) {
                PlayerOptionsBottomSheet(
                    song = activeSong,
                    isFavorite = isFavorite,
                    isDownloaded = isDownloaded,
                    isLiquidGlass = isLiquidGlass,
                    onDismiss = { showMoreOptions = false },
                    onFavoriteClick = { MusicPlayer.toggleFavorite(context) },
                    onDownloadClick = {
                        scope.launch {
                            if (isDownloaded) {
                                AppDatabase.getDatabase(context).songDao().deleteDownload(activeSong.videoId)
                                val downloaded = AppDatabase.getDatabase(context).songDao().getDownloadByVideoId(activeSong.videoId)
                                downloaded?.filePath?.let { path -> java.io.File(path).delete() }
                                MusicPlayer.isDownloaded = false
                                Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val result = MusicPlayer.unifiedRepository?.getStreamingUrl(
                                videoId = activeSong.videoId,
                                cookies = MusicPlayer.cookies,
                                failedClients = emptyList(),
                                targetBitrate = null,
                                priority = if (settingsManager.isDataSaverEnabled) "Data Saver" else "Force OPUS 160kbps",
                                preferredVideoQuality = settingsManager.preferredVideoQuality
                            )
                            if (result != null) {
                                val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                                    .setInputData(workDataOf(
                                        "url" to result.url,
                                        "videoId" to activeSong.videoId,
                                        "title" to activeSong.title,
                                        "artist" to activeSong.artist,
                                        "thumbnailUrl" to activeSong.thumbnailUrl,
                                        "userAgent" to result.userAgent,
                                        "cookies" to MusicPlayer.cookies
                                    ))
                                    .build()
                                WorkManager.getInstance(context).enqueue(downloadRequest)
                                Toast.makeText(context, "Downloading ${activeSong.title}...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onAddToPlaylistClick = {
                        showMoreOptions = false
                        showPlaylistSelection = true
                    },
                    onSleepTimerClick = {
                        showMoreOptions = false
                        showSleepTimerMenu = true
                    },
                    onPlayNextClick = { MusicPlayer.playNext(activeSong) },
                    onArtistClick = { id ->
                        onArtistClick(id)
                        showMoreOptions = false
                    },
                    onShareClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Listen to ${activeSong.title} by ${activeSong.artist} on WaveMuzix: https://www.youtube.com/watch?v=${activeSong.videoId}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }
                )
            }
        }

        if (showSleepTimerMenu) {
            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalPreferHaze provides false
            ) {
                SleepTimerBottomSheet(
                    isLiquidGlass = isLiquidGlass,
                    onDismiss = { showSleepTimerMenu = false },
                    onTimerSelected = { minutes ->
                        if (minutes == -1) {
                            MusicPlayer.toggleStopAfterCurrentSong(true)
                        } else if (minutes > 0) {
                            MusicPlayer.startSleepTimer(minutes)
                        } else {
                            MusicPlayer.cancelSleepTimer()
                        }
                        showSleepTimerMenu = false
                    }
                )
            }
        }

        if (showPlaylistSelection && activeSong != null) {
            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalPreferHaze provides false
            ) {
                PlaylistSelectionBottomSheet(
                    playlists = playlistRepository.allPlaylists.collectAsState(initial = emptyList()).value,
                    isLiquidGlass = isLiquidGlass,
                    onDismiss = { showPlaylistSelection = false },
                    onCreatePlaylist = { name ->
                        scope.launch {
                            val id = playlistRepository.createPlaylist(name)
                            playlistRepository.addSongToPlaylist(id, activeSong)
                            showPlaylistSelection = false
                        }
                    },
                    onPlaylistSelected = { playlist ->
                        scope.launch {
                            playlistRepository.addSongToPlaylist(playlist.id, activeSong)
                            showPlaylistSelection = false
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@UnstableApi
@Composable
fun Material3PlayerScreen(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    onSearchClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    isLiquidGlass: Boolean,
    hazeState: dev.chrisbanes.haze.HazeState,
    backdrop: LayerBackdrop? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val activeSong = MusicPlayer.currentSong
    val isPlaying = MusicPlayer.isPlaying
    val currentPosition = MusicPlayer.currentPosition
    val duration = MusicPlayer.duration
    val context = LocalContext.current
    val performance = LocalPerformanceConfig.current
    val playlistRepository = remember {
        PlaylistRepository(AppDatabase.getDatabase(context).playlistDao(), context)
    }
    val scope = rememberCoroutineScope()

    var showLyrics by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val videoDetails by remember { derivedStateOf { MusicPlayer.videoDetails } }
    val accentColors by remember { derivedStateOf { MusicPlayer.accentColors } }
    val color1 = accentColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val color2 = Color(
        red = color1.red * 0.22f,
        green = color1.green * 0.22f,
        blue = color1.blue * 0.22f,
        alpha = 1f
    )
    val mainAccentColor = color1

    var showMoreOptions by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var showPlaylistSelection by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = if (MusicPlayer.queue.isEmpty()) 0 else (MusicPlayer.currentIndex).coerceIn(0, MusicPlayer.queue.size - 1),
        pageCount = { MusicPlayer.queue.size }
    )

    var isLaunched by remember { mutableStateOf(false) }

    LaunchedEffect(MusicPlayer.currentIndex) {
        if (MusicPlayer.currentIndex != -1 && MusicPlayer.queue.isNotEmpty()) {
            val targetPage = MusicPlayer.currentIndex.coerceIn(0, MusicPlayer.queue.size - 1)
            if (pagerState.currentPage != targetPage) {
                if (!isLaunched) {
                    try { pagerState.scrollToPage(targetPage) } catch (e: Exception) {}
                } else {
                    try {
                        pagerState.animateScrollToPage(
                            page = targetPage,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                        )
                    } catch (e: Exception) {}
                }
            }
            isLaunched = true
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (isLaunched && pagerState.settledPage != MusicPlayer.currentIndex && MusicPlayer.currentIndex != -1 && MusicPlayer.queue.isNotEmpty()) {
            MusicPlayer.playAtIndex(context, pagerState.settledPage)
        }
    }

    val lyricsText by remember { derivedStateOf {
        if (MusicPlayer.isLyricsLoading) "Searching for lyrics..."
        else MusicPlayer.lyricsText ?: ""
    } }
    val lyricsProviderName by remember { derivedStateOf { MusicPlayer.lyricsProvider ?: "" } }

    val settingsManager = remember { SettingsManager(context) }
    val isPlayVideoEnabled = settingsManager.isPlayVideoIfAvailable

    DisposableEffect(Unit) {
        MusicPlayer.isPlayerViewVisible = true
        MusicPlayer.updateVideoPlaybackState()
        onDispose {
            MusicPlayer.isPlayerViewVisible = false
            MusicPlayer.updateVideoPlaybackState()
        }
    }

    val isFavorite = MusicPlayer.isFavorite

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelMedium,
                            color = color1,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (MusicPlayer.queue.isNotEmpty()) "Playlist \"Wave Queue\"" else "No Playlist",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, "Back", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { showMoreOptions = true }) {
                        Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Ambient Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                mainAccentColor.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(0.5f, 0.3f)
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                Spacer(modifier = Modifier.weight(0.02f))

                // Artwork Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 24.dp
                ) { page ->
                    val song = MusicPlayer.queue.getOrNull(page)
                    val isCurrentPage = page == MusicPlayer.currentIndex
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)

                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .graphicsLayer {
                                val scale = 0.9f + (0.1f * (1f - abs(pageOffset).coerceIn(0f, 1f)))
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.6f + (0.4f * (1f - abs(pageOffset).coerceIn(0f, 1f)))
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.DarkGray
                    ) {
                        GlideImage(
                            model = if (isCurrentPage) (MusicPlayer.highResThumbnailUrl ?: song?.thumbnailUrl) else song?.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.1f))

                // Lyrics Line
                if (lyricsText.isNotEmpty() && !showLyrics) {
                    Text(
                        text = lyricsText.split("\n").firstOrNull { it.contains("]") }?.substringAfter("]") ?: lyricsText.take(50),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).clickable { showLyrics = true }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Song Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSong?.title ?: "No Song",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = MusicPlayer.sanitizedArtistName.ifEmpty { activeSong?.artist ?: "Unknown Artist" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1
                            )

                            // Artist Source Attribution
                            val source = MusicPlayer.artistAvatarSource
                            if (!source.isNullOrEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (source == "Spotify") Icons.Default.Info else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (source == "Spotify") Color(0xFF1DB954) else Color.Red,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "via $source",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                        Surface(
                            onClick = { MusicPlayer.toggleFavorite(context) },
                            shape = CircleShape,
                            color = color2,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = color1,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress Bar
                    val sliderPosition = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    WavySlider(
                        value = sliderPosition,
                        onValueChange = { newValue: Float ->
                            if (duration > 0) MusicPlayer.seekTo((newValue * duration).toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isPlaying = isPlaying,
                        colors = SliderDefaults.colors(
                            activeTrackColor = color1,
                            inactiveTrackColor = color2,
                            thumbColor = color1
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        Text(formatTime(duration), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.weight(0.02f))

                // Main Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { MusicPlayer.playPreviousInQueue(context) },
                        shape = RoundedCornerShape(32.dp),
                        color = color2,
                        modifier = Modifier.size(width = 96.dp, height = 80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    Surface(
                        onClick = { if (!MusicPlayer.isLoading) MusicPlayer.togglePlayPause(context) },
                        shape = RoundedCornerShape(32.dp),
                        color = color1,
                        modifier = Modifier.size(width = 190.dp, height = 80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (MusicPlayer.isLoading && !isPlaying) {
                                CircularProgressIndicator(color = DeepBlack, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = DeepBlack,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { MusicPlayer.playNextInQueue(context) },
                        shape = RoundedCornerShape(32.dp),
                        color = color2,
                        modifier = Modifier.size(width = 96.dp, height = 80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.05f))

                // Bottom Utilities
                MultiChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    val colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = color1.copy(alpha = 0.3f),
                        activeContentColor = color1,
                        inactiveContainerColor = color2.copy(alpha = 0.2f),
                        inactiveContentColor = color1.copy(alpha = 0.5f),
                        activeBorderColor = color1.copy(alpha = 0.15f),
                        inactiveBorderColor = color1.copy(alpha = 0.15f)
                    )

                    val segmentModifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp)
                    val squircleRadius = 16.dp

                    SegmentedButton(
                        checked = false,
                        onCheckedChange = { showDetails = true },
                        shape = RoundedCornerShape(topStart = CornerSize(50), bottomStart = CornerSize(50), topEnd = CornerSize(squircleRadius), bottomEnd = CornerSize(squircleRadius)),
                        label = { Icon(Icons.Default.Info, "Info", modifier = Modifier.size(24.dp)) },
                        colors = colors,
                        modifier = segmentModifier
                    )

                    SegmentedButton(
                        checked = MusicPlayer.shuffleEnabled,
                        onCheckedChange = { MusicPlayer.shuffleEnabled = !MusicPlayer.shuffleEnabled },
                        shape = RoundedCornerShape(squircleRadius),
                        label = { Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(24.dp)) },
                        colors = colors,
                        modifier = segmentModifier
                    )

                    SegmentedButton(
                        checked = MusicPlayer.repeatMode != Player.REPEAT_MODE_OFF,
                        onCheckedChange = {
                            MusicPlayer.repeatMode = when (MusicPlayer.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        },
                        shape = RoundedCornerShape(squircleRadius),
                        label = {
                            Icon(
                                if (MusicPlayer.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                "Repeat",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        colors = colors,
                        modifier = segmentModifier
                    )

                    SegmentedButton(
                        checked = false,
                        onCheckedChange = { onQueueClick() },
                        shape = RoundedCornerShape(squircleRadius),
                        label = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Queue", modifier = Modifier.size(24.dp)) },
                        colors = colors,
                        modifier = segmentModifier
                    )

                    SegmentedButton(
                        checked = false,
                        onCheckedChange = { activeSong?.let { onArtistClick(it.artist) } },
                        shape = RoundedCornerShape(topStart = CornerSize(squircleRadius), bottomStart = CornerSize(squircleRadius), topEnd = CornerSize(50), bottomEnd = CornerSize(50)),
                        label = { Icon(Icons.Default.Person, "Artist", modifier = Modifier.size(24.dp)) },
                        colors = colors,
                        modifier = segmentModifier
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
                }
                item {
                    LyricsPreviewBox(
                        lyrics = lyricsText,
                        provider = lyricsProviderName,
                        currentPosition = currentPosition,
                        backgroundColor = color2,
                        onShowClick = { showLyrics = true }
                    )
                }

                // Channel Branding Section
                item {
                    val currentDetails = videoDetails
                    val artistAvatar = MusicPlayer.artistAvatarUrl
                    if ((currentDetails != null && currentDetails.channelThumbnailUrl.isNotEmpty()) || !artistAvatar.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp, bottom = 32.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(28.dp))
                        ) {
                            // Artist Image directly with Haze Source
                            GlideImage(
                                model = artistAvatar ?: currentDetails?.channelThumbnailUrl,
                                contentDescription = "Artist/Channel Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(state = hazeState),
                                contentScale = ContentScale.Crop
                            )

                            val canShowHaze = isLiquidGlass && performance.isLiquidGlassEnabled

                            // Transparent Haze Container above the banner
                            if (canShowHaze) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .hazeEffect(state = hazeState) {
                                            progressive = HazeProgressive.verticalGradient(
                                                startIntensity = 0f, // Clear at top
                                                endIntensity = 1f    // Blurred at bottom
                                            )
                                            style = HazeStyle(
                                                blurRadius = 30.dp,
                                                tint = HazeTint(Color.Black.copy(alpha = 0.45f))
                                            )
                                        }
                                )
                            }

                            // Bottom-fade blur scrim for text readability
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f)
                                    .align(Alignment.BottomCenter)
                                    .then(
                                        if (!canShowHaze) {
                                            Modifier.background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.4f),
                                                        Color.Black.copy(alpha = 0.7f),
                                                        Color.Black.copy(alpha = 0.9f)
                                                    ),
                                                )
                                            )
                                        } else Modifier
                                    )
                                    .padding(20.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Column {
                                    Text(
                                        text = if (!artistAvatar.isNullOrEmpty()) MusicPlayer.sanitizedArtistName else currentDetails?.channelName ?: MusicPlayer.sanitizedArtistName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = currentDetails?.subscriberCountText ?: "Official Artist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            LyricsOverlay(
                lyrics = lyricsText,
                provider = lyricsProviderName,
                currentPosition = currentPosition,
                backgroundColor = color2,
                isLiquidGlass = isLiquidGlass,
                onDismiss = { showLyrics = false }
            )
        }

        if (showDetails) {
            DetailsBottomSheet(
                details = videoDetails,
                isLiquidGlass = isLiquidGlass,
                isLoading = MusicPlayer.isDetailsLoading,
                onDismiss = { showDetails = false }
            )
        }

        if (showMoreOptions && activeSong != null) {
            PlayerOptionsBottomSheet(
                song = activeSong,
                isFavorite = isFavorite,
                isDownloaded = MusicPlayer.isDownloaded,
                isLiquidGlass = isLiquidGlass,
                accentColor = color1,
                onDismiss = { showMoreOptions = false },
                onFavoriteClick = { MusicPlayer.toggleFavorite(context) },
                onDownloadClick = {
                    val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(workDataOf(
                            "videoId" to activeSong.videoId,
                            "title" to activeSong.title,
                            "artist" to activeSong.artist,
                            "thumbnail" to activeSong.thumbnailUrl
                        ))
                        .build()
                    WorkManager.getInstance(context).enqueue(workRequest)
                    showMoreOptions = false
                },
                onAddToPlaylistClick = { showPlaylistSelection = true },
                onSleepTimerClick = { showSleepTimerMenu = true },
                onPlayNextClick = {
                    MusicPlayer.playNext(activeSong)
                    showMoreOptions = false
                },
                onArtistClick = { onArtistClick(activeSong.artist) },
                onShareClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listen to ${activeSong.title} by ${activeSong.artist} on WaveMuzix: https://www.youtube.com/watch?v=${activeSong.videoId}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            )
        }

        if (showSleepTimerMenu) {
            SleepTimerBottomSheet(
                isLiquidGlass = isLiquidGlass,
                onDismiss = { showSleepTimerMenu = false },
                onTimerSelected = { minutes ->
                    if (minutes == 0) {
                        MusicPlayer.cancelSleepTimer()
                    } else if (minutes == -1) {
                        MusicPlayer.stopAfterCurrentSong = true
                    } else {
                        MusicPlayer.startSleepTimer(minutes)
                    }
                    showSleepTimerMenu = false
                    Toast.makeText(context, if (minutes > 0) "Sleep timer set for $minutes minutes" else "Timer cancelled", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showPlaylistSelection && activeSong != null) {
            PlaylistSelectionBottomSheet(
                playlists = playlistRepository.allPlaylists.collectAsState(initial = emptyList()).value,
                isLiquidGlass = isLiquidGlass,
                onDismiss = { showPlaylistSelection = false },
                onCreatePlaylist = { name ->
                    scope.launch {
                        val id = playlistRepository.createPlaylist(name)
                        playlistRepository.addSongToPlaylist(id, activeSong)
                        showPlaylistSelection = false
                    }
                },
                onPlaylistSelected = { playlist ->
                    scope.launch {
                        playlistRepository.addSongToPlaylist(playlist.id, activeSong)
                        showPlaylistSelection = false
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@UnstableApi
@Composable
fun LandscapePlayerOverlay(
    onBack: () -> Unit,
    isLiquidGlass: Boolean
) {
    val context = LocalContext.current
    val activeSong = MusicPlayer.currentSong
    val isPlaying = MusicPlayer.isPlaying
    val currentPosition = MusicPlayer.currentPosition
    val duration = MusicPlayer.duration
    val accentColors = MusicPlayer.accentColors
    val videoQuality = MusicPlayer.currentVideoQuality

    var showControls by remember { mutableStateOf(true) }
    val performance = LocalPerformanceConfig.current
    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        animationSpec = if (performance.isLowSpec) tween(300) else tween(500),
        label = "ControlsAlpha"
    )

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showControls = !showControls })
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = MusicPlayer.getVideoPlayer(ctx)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)

                    // STABILITY FIX: Force TextureView to prevent SIGSEGV during transformations
                    try {
                        val surfaceTypeField = PlayerView::class.java.getDeclaredField("surfaceType")
                        surfaceTypeField.isAccessible = true
                        surfaceTypeField.set(this, 2) // 2 = TEXTURE_VIEW
                    } catch (e: Exception) {}
                }
            },
            update = { view ->
                val currentPlayer = MusicPlayer.getVideoPlayer(view.context)
                if (view.player != currentPlayer) {
                    view.player = currentPlayer
                }
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!MusicPlayer.isVideoActuallyPlaying) {
            GlideImage(
                model = MusicPlayer.highResThumbnailUrl ?: activeSong?.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        if (controlsAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = controlsAlpha }
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        onClick = onBack,
                        isLiquidGlass = isLiquidGlass,
                        contentDescription = "Back",
                        modifier = Modifier.graphicsLayer { rotationZ = 90f },
                        containerSize = 48.dp,
                        iconSize = 32.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            activeSong?.title ?: "Unknown Title",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            MusicPlayer.sanitizedArtistName.ifEmpty { activeSong?.artist ?: "Unknown Artist" },
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (videoQuality != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text(
                                text = videoQuality,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    IconButton(onClick = { MusicPlayer.playPreviousInQueue(context) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier
                            .size(80.dp)
                            .clickable { MusicPlayer.togglePlayPause(context) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = DeepBlack,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    IconButton(onClick = { MusicPlayer.playNextInQueue(context) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                ) {
                    val sliderPosition = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            if (duration > 0) {
                                MusicPlayer.seekTo((newValue * duration).toLong())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accentColors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
                            activeTrackColor = accentColors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionBottomSheet(
    playlists: List<PlaylistEntity>,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlaylistSelected: (PlaylistEntity) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxHeight = remember(configuration, density) { (configuration.screenHeightDp * 0.7f).dp }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .glassContainer(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    isLiquidGlass = isLiquidGlass
                )
        ) {
            Text(
                "Add to playlist",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold
            )

            ListItem(
                headlineContent = { Text("Create new playlist", color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showCreateDialog = true }
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(playlists) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, color = Color.White) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = Color.White) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPlaylistSelected(playlist) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SoftBlack,
            title = { Text("New Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName)
                        showCreateDialog = false
                    }
                }) { Text("Create", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = Color.White) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onTimerSelected: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxHeight = remember(configuration, density) { (configuration.screenHeightDp * 0.7f).dp }

    var showCustomTimerDialog by remember { mutableStateOf(false) }
    var customTimeInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .glassContainer(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    isLiquidGlass = isLiquidGlass
                )
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp)
        ) {
            Text(
                "Sleep timer",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(24.dp),
                fontWeight = FontWeight.Bold
            )

            OptionItem(Icons.Default.Timer, "5 minutes") { onTimerSelected(5) }
            OptionItem(Icons.Default.Timer, "10 minutes") { onTimerSelected(10) }
            OptionItem(Icons.Default.Timer, "30 minutes") { onTimerSelected(30) }
            OptionItem(Icons.Default.Timer, "60 minutes") { onTimerSelected(60) }
            OptionItem(Icons.Default.Edit, "Custom time") { showCustomTimerDialog = true }
            OptionItem(Icons.AutoMirrored.Filled.PlaylistPlay, "End of track") { onTimerSelected(-1) }

            if (MusicPlayer.sleepTimerMillis > 0 || MusicPlayer.stopAfterCurrentSong) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                OptionItem(Icons.Default.Timer, "Turn off timer", tint = Color.Red) { onTimerSelected(0) }
            }
        }
    }

    if (showCustomTimerDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTimerDialog = false },
            containerColor = SoftBlack,
            title = { Text("Custom Sleep Timer", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = customTimeInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) customTimeInput = it },
                    label = { Text("Minutes") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customTimeInput.toIntOrNull()?.let { onTimerSelected(it); showCustomTimerDialog = false; onDismiss() }
                }) { Text("Set", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun PlayerOptionsBottomSheet(
    song: Song,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onShareClick: () -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxHeight = remember(configuration, density) { (configuration.screenHeightDp * 0.7f).dp }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassContainer(
                        shape = RoundedCornerShape(24.dp),
                        isLiquidGlass = isLiquidGlass
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = MusicPlayer.userVolume,
                        onValueChange = { MusicPlayer.userVolume = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassContainer(
                        shape = RoundedCornerShape(24.dp),
                        isLiquidGlass = isLiquidGlass
                    )
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlideImage(
                        model = MusicPlayer.getHighResThumbnailUrl(song.thumbnailUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = song.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = MusicPlayer.sanitizedArtistName.ifEmpty { song.artist },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                OptionItem(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Liked" else "Like", if (isFavorite) accentColor else Color.White) { onFavoriteClick(); onDismiss() }
                OptionItem(Icons.Default.Download, if (isDownloaded) "Downloaded" else "Download", if (isDownloaded) accentColor else Color.White) { onDownloadClick(); onDismiss() }
                OptionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { onAddToPlaylistClick(); onDismiss() }
                OptionItem(Icons.Default.Timer, "Sleep timer") { onSleepTimerClick(); onDismiss() }
                OptionItem(Icons.AutoMirrored.Filled.QueueMusic, "Play next") { onPlayNextClick(); onDismiss() }
                song.artistId?.let { id -> OptionItem(Icons.Default.Person, "Go to artist") { onArtistClick(id) } } ?: OptionItem(Icons.Default.Person, "Go to artist") { onArtistClick(MusicPlayer.sanitizedArtistName) }
                OptionItem(Icons.Default.Share, "Share") { onShareClick(); onDismiss() }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                val sourceLabel = when {
                    MusicPlayer.currentServer == "OFFLINE" -> "Offline Storage"
                    else -> "YouTube"
                }
                OptionItem(
                    icon = Icons.Default.BugReport,
                    title = "Source: $sourceLabel (${MusicPlayer.currentServer})",
                    tint = Color.White.copy(alpha = 0.5f),
                    onClick = {
                        Toast.makeText(context, "Playing via ${MusicPlayer.currentServer} extraction", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun OptionItem(icon: ImageVector, title: String, tint: Color = Color.White, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(24.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LyricsPreviewBox(
    lyrics: String,
    provider: String,
    currentPosition: Long,
    backgroundColor: Color,
    onShowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = remember(lyrics) {
        lyrics.split("\n").mapNotNull { line ->
            val match = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)").find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = msStr.toLong() * (if (msStr.length == 2) 10 else 1)
                (min * 60000 + sec * 1000 + ms) to match.groupValues[4].trim()
            } else if (line.trim().isNotEmpty() && !line.startsWith("[")) 0L to line.trim() else null
        }
    }

    val isSynced = remember(lyrics) {
        lyrics.contains(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}]"))
    }

    val activeLineIndex = if (isSynced) lines.indexOfLast { it.first <= currentPosition } else -1
    val lazyListState = rememberLazyListState()

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex != -1) {
            lazyListState.animateScrollToItem(activeLineIndex, -100)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = backgroundColor.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onShowClick) {
                    Text(
                        "SHOW",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (lines.isEmpty() || lyrics.contains("Lyrics not available")) {
                Text(
                    "No lyrics available to preview",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = false,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(lines) { index, (_, text) ->
                        val isActive = index == activeLineIndex
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = 22.sp,
                                lineHeight = 32.sp,
                                textAlign = TextAlign.Center
                            ),
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (isSynced) "SYNCED" else "UNSYNCED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (provider.isNotEmpty()) {
                    Text(
                        text = provider,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}


@OptIn(com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class)
@Composable
fun LyricsOverlay(
    lyrics: String,
    provider: String,
    currentPosition: Long,
    backgroundColor: Color,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit
) {
    val details = MusicPlayer.videoDetails
    val lines = remember(lyrics) {
        lyrics.split("\n").mapNotNull { line ->
            val match = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)").find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msStr = match.groupValues[3]
                val ms = msStr.toLong() * (if (msStr.length == 2) 10 else 1)
                (min * 60000 + sec * 1000 + ms) to match.groupValues[4].trim()
            } else if (line.trim().isNotEmpty()) 0L to line.trim() else null
        }
    }
    val activeLineIndex = lines.indexOfLast { it.first <= currentPosition }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex != -1) lazyListState.animateScrollToItem(activeLineIndex, -200)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor.copy(alpha = 0.98f)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lyrics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (provider.isNotEmpty()) {
                        Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                provider,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    GlassIconButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        onClick = onDismiss,
                        isLiquidGlass = isLiquidGlass,
                        containerSize = 44.dp,
                        iconSize = 32.dp
                    )
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (lines.isEmpty()) {
                    item {
                        Text(
                            lyrics,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    itemsIndexed(lines) { index, (_, text) ->
                        val isActive = index == activeLineIndex
                        Text(
                            text = text,
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                            style = if (isActive) MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                lineHeight = 38.sp
                            ) else MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                lineHeight = 34.sp
                            ),
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 24.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun DetailsBottomSheet(
    details: VideoDetails?,
    isLoading: Boolean,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxHeight = remember(configuration, density) { (configuration.screenHeightDp * 0.7f).dp }
    val artistAvatar = MusicPlayer.artistAvatarUrl

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .glassContainer(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    isLiquidGlass = isLiquidGlass
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Artist Details", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
            if (isLoading) StandardLoader() else {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(modifier = Modifier.size(100.dp).clip(CircleShape), color = Color.White.copy(alpha = 0.1f)) {
                        if (!artistAvatar.isNullOrEmpty()) {
                            GlideImage(model = artistAvatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else if (details?.channelThumbnailUrl?.isNotEmpty() == true) {
                            GlideImage(model = details.channelThumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(50.dp)) }
                        }
                    }

                    // Artist Metadata Source Badge
                    val source = MusicPlayer.artistAvatarSource
                    if (!source.isNullOrEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(24.dp).offset(x = 4.dp, y = 4.dp),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (source == "Spotify") {
                                    Icon(
                                        painter = painterResource(id = android.R.drawable.ic_menu_info_details), // Placeholder for Spotify icon
                                        contentDescription = "via Spotify",
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow, // Placeholder for YouTube icon
                                        contentDescription = "via YouTube",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (!artistAvatar.isNullOrEmpty()) MusicPlayer.sanitizedArtistName else details?.channelName ?: MusicPlayer.sanitizedArtistName,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (details?.subscriberCountText != null) {
                    Text(details.subscriberCountText!!, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (details != null) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(16.dp)) {
                        Text("About", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        Text(details.description, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("Additional details not available", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Text(label, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
