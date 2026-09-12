package com.example.wavemuzix.uiresource.screens

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.data.CookieManager
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.repository.HomeShelf
import com.example.wavemuzix.repository.UnifiedMusicRepository
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.ui.theme.SurfaceVariant
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.WaveUserAvatar
import com.example.wavemuzix.data.DownloadManager
import com.example.wavemuzix.uiresource.components.GlassIconButton
import com.example.wavemuzix.uiresource.components.StandardLoader
import com.example.wavemuzix.data.AppNotification
import com.example.wavemuzix.data.AppNotificationManager
import com.example.wavemuzix.data.NotificationType
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.wavemuzix.uiresource.components.glassContainer
import com.example.wavemuzix.uiresource.components.WaveStateContainer
import com.example.wavemuzix.uiresource.components.HomeScreenSkeleton
import com.example.wavemuzix.utils.LocationHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import com.example.wavemuzix.data.LocalPerformanceConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun HomeScreen(
    repository: UnifiedMusicRepository,
    onNotifyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onLoginClick: () -> Unit,
    onManageAccountClick: () -> Unit,
    isLiquidGlass: Boolean,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val cookieManager = remember { CookieManager(context) }
    val auth = remember { Firebase.auth }
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
    val scope = rememberCoroutineScope()

    val vmLoadingHomeState = viewModel?.isLoadingHome?.collectAsState()
    val isLoadingHome = vmLoadingHomeState?.value ?: false
    var isError by remember { mutableStateOf(false) }

    val vmRefreshingState = viewModel?.isRefreshing?.collectAsState()
    val isRefreshing = vmRefreshingState?.value ?: false
    
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val performance = LocalPerformanceConfig.current
    
    // Performance optimization for list scrolling
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    // Bind to ViewModel persistent state
    val shelves = viewModel?.shelves ?: remember { mutableStateListOf<HomeShelf>() }
    val quickPicks = viewModel?.quickPicks ?: remember { mutableStateListOf<Song>() }
    val trending = viewModel?.trending ?: remember { mutableStateListOf<Song>() }
    val continuationToken = viewModel?.continuationToken
    val isLoadingMore = viewModel?.isLoadingMore ?: false

    var userName by remember { mutableStateOf<String?>(viewModel?.userName) }
    var userAvatarUrl by remember { mutableStateOf<String?>(viewModel?.userAvatarUrl) }
    var userLocation by remember { mutableStateOf<String?>(settingsManager.userLocation) }

    // Use persistent metadata from ViewModel
    LaunchedEffect(viewModel?.userName, viewModel?.userAvatarUrl) {
        viewModel?.let {
            userName = it.userName
            userAvatarUrl = it.userAvatarUrl
        }
    }

    // Use pre-warmed data to update metadata if needed
    val homeData by (viewModel?.homeData ?: MutableStateFlow(null)).collectAsState()
    
    LaunchedEffect(homeData) {
        homeData?.let { response ->
            userName = auth.currentUser?.displayName ?: response.userName
            userAvatarUrl = auth.currentUser?.photoUrl?.toString() ?: response.userAvatarUrl
        }
    }

    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialogFor by remember { mutableStateOf<Song?>(null) }
    var showLoginPrompt by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Map<String, Any>?>(null) }

    val favoriteRepository = remember { com.example.wavemuzix.repository.FavoriteRepository(com.example.wavemuzix.data.database.AppDatabase.getDatabase(context).songDao(), context) }
    val playlistRepository = remember { com.example.wavemuzix.repository.PlaylistRepository(com.example.wavemuzix.data.database.AppDatabase.getDatabase(context).playlistDao(), context) }

    val downloadedSongs by com.example.wavemuzix.data.database.AppDatabase.getDatabase(context).songDao().getAllDownloads().collectAsState(initial = emptyList())
    val likedSongs by com.example.wavemuzix.data.database.AppDatabase.getDatabase(context).songDao().getAllFavorites().collectAsState(initial = emptyList())
    val allPlaylists by playlistRepository.allPlaylists.collectAsState(initial = emptyList())

    val refreshData: () -> Unit = {
        isError = false
        if (viewModel != null) {
            viewModel.refreshFeed(context, repository)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            scope.launch {
                val countryCode = LocationHelper.getCountryCode(context)
                val region = LocationHelper.getRegionName(context)
                val loc = region ?: countryCode
                userLocation = loc
                settingsManager.userLocation = loc
                viewModel?.setUserLocation(context, loc)
                if (shelves.isEmpty()) {
                    refreshData()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // If we already have a location, we don't necessarily need to fetch it immediately, 
        // but we should still check for permissions to update it.
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            scope.launch {
                val countryCode = LocationHelper.getCountryCode(context)
                val region = LocationHelper.getRegionName(context)
                val loc = region ?: countryCode
                if (loc != null) {
                    userLocation = loc
                    settingsManager.userLocation = loc
                    viewModel?.setUserLocation(context, loc)
                }
            }
        } else if (userLocation == null) {
            // Only ask for permission if we don't have a saved location
            locationPermissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(Unit) {
        auth.addAuthStateListener { firebaseAuth ->
            isLoggedIn = firebaseAuth.currentUser != null
        }
    }

    LaunchedEffect(isLoggedIn) {
        // Only trigger initial fetch if MainViewModel doesn't already have data (from Splash pre-warming)
        // This prevents redundant fetches when navigating back to Home, saving CPU and network.
        if (viewModel?.homeData?.value == null) {
            refreshData()
        }
    }

    LaunchedEffect(Unit) {
        if (!isLoggedIn && settingsManager.shouldShowLoginPrompt) {
            showLoginPrompt = true
        }
    }

    // Endless Scrolling Logic
    val endOfListReached by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(endOfListReached) {
        if (endOfListReached && continuationToken != null && !isLoadingMore && !isRefreshing) {
            viewModel?.loadMore(context, repository)
        }
    }

    if (showPlaylistDialogFor != null) {
        com.example.wavemuzix.uiresource.components.AddToPlaylistDialog(
            song = showPlaylistDialogFor!!,
            playlists = allPlaylists,
            onDismiss = { showPlaylistDialogFor = null },
            isLiquidGlass = isLiquidGlass,
            onPlaylistSelected = { playlist ->
                scope.launch {
                    playlistRepository.addSongToPlaylist(playlist.id, showPlaylistDialogFor!!)
                    Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                    showPlaylistDialogFor = null
                }
            },
            onCreatePlaylist = { name ->
                scope.launch {
                    val id = playlistRepository.createPlaylist(name)
                    playlistRepository.addSongToPlaylist(id, showPlaylistDialogFor!!)
                    Toast.makeText(context, "Created and added to $name", Toast.LENGTH_SHORT).show()
                    showPlaylistDialogFor = null
                }
            }
        )
    }

    if (showLoginPrompt) {
        com.example.wavemuzix.uiresource.components.LoginPromptDialog(
            onDismiss = { showLoginPrompt = false },
            onLoginClick = { onLoginClick() },
            onDontShowAgain = { settingsManager.shouldShowLoginPrompt = !it },
            isLiquidGlass = isLiquidGlass
        )
    }

    LaunchedEffect(AppNotificationManager.triggerUpdateDialog) {
        if (AppNotificationManager.triggerUpdateDialog) {
            showUpdateDialog = true
            AppNotificationManager.triggerUpdateDialog = false
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        com.example.wavemuzix.uiresource.components.UpdateDialog(
            newVersionName = updateInfo!!["versionName"] as String,
            updateNotes = updateInfo!!["notes"] as String,
            onDismiss = { showUpdateDialog = false },
            onUpdateClick = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateInfo!!["url"] as String))
                context.startActivity(intent)
                showUpdateDialog = false
            },
            onIgnoreClick = {
                AppNotificationManager.addNotification(
                    AppNotification(
                        id = "update_${updateInfo!!["versionCode"]}",
                        title = "New update available",
                        description = "Version ${updateInfo!!["versionName"]} is ready to install",
                        type = NotificationType.UPDATE
                    )
                )
                showUpdateDialog = false
            },
            isLiquidGlass = isLiquidGlass
        )
    }

    if (selectedSongForOptions != null) {
        val song = selectedSongForOptions!!
        val isDownloaded = downloadedSongs.any { it.videoId == song.videoId }
        val isFavorite = likedSongs.any { it.videoId == song.videoId }
        val cookies = cookieManager.getCookies()

        com.example.wavemuzix.uiresource.components.SongOptionsBottomSheet(
            song = song,
            isFavorite = isFavorite,
            isDownloaded = isDownloaded,
            isLiquidGlass = isLiquidGlass,
            onDismiss = { selectedSongForOptions = null },
            onFavoriteClick = {
                scope.launch { favoriteRepository.toggleFavorite(song) }
            },
            onDownloadClick = {
                scope.launch {
                    if (isDownloaded) {
                        com.example.wavemuzix.data.database.AppDatabase.getDatabase(context).songDao().deleteDownload(song.videoId)
                        downloadedSongs.find { it.videoId == song.videoId }?.filePath?.let { java.io.File(it).delete() }
                        Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show()
                    } else {
                        val result = repository.getStreamingUrl(
                            videoId = song.videoId,
                            cookies = cookies
                        )
                        if (result != null) {
                            DownloadManager.updateProgress(song.videoId, song.title, 0)
                            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.wavemuzix.data.DownloadWorker>()
                                .setInputData(androidx.work.workDataOf(
                                    "url" to result.url,
                                    "videoId" to song.videoId,
                                    "title" to song.title,
                                    "artist" to song.artist,
                                    "thumbnailUrl" to song.thumbnailUrl,
                                    "artistId" to song.artistId,
                                    "userAgent" to result.userAgent,
                                    "cookies" to cookies
                                ))
                                .build()
                            androidx.work.WorkManager.getInstance(context).enqueue(request)
                            Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onPlayNextClick = { MusicPlayer.playNext(song) },
            onAddToQueueClick = { MusicPlayer.addToQueue(song) },
            onArtistClick = { onArtistClick(it); selectedSongForOptions = null },
            onAddToPlaylistClick = { showPlaylistDialogFor = song; selectedSongForOptions = null },
            onShareClick = {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Listen to ${song.title} by ${song.artist} on WaveMuzix: https://www.youtube.com/watch?v=${song.videoId}")
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, null))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SoftBlack, DeepBlack)
                )
            )
    ) {
        val onProfileClick = {
            if (isLoggedIn) onManageAccountClick() else onLoginClick()
        }

        Spacer(Modifier.statusBarsPadding())
        HomeTopBar(
            isLoggedIn = isLoggedIn,
            userAvatarUrl = userAvatarUrl ?: auth.currentUser?.photoUrl?.toString(),
            onNotifyClick = onNotifyClick,
            onSettingsClick = onSettingsClick,
            onProfileClick = onProfileClick,
            isLiquidGlass = isLiquidGlass
        )
        FilterChipsRow()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshData() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            }
        ) {
            WaveStateContainer(
                isLoading = isLoadingHome && shelves.isEmpty(),
                isRefreshing = isRefreshing && shelves.isNotEmpty(),
                showRefreshingIndicator = false,
                isError = isError || MusicPlayer.isPlaybackError,
                errorMessage = if (MusicPlayer.isPlaybackError) MusicPlayer.playbackErrorMessage else "Check Your Internet Connection",
                onRetry = { 
                    if (MusicPlayer.isPlaybackError) {
                        MusicPlayer.currentSong?.let { MusicPlayer.playSong(context, it) }
                    } else {
                        refreshData()
                    }
                },
                loadingContent = {
                    HomeScreenSkeleton()
                }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)
                ) {
                item(key = "welcome") {
                    UserWelcomeSection(
                        isLoggedIn = isLoggedIn,
                        userName = userName ?: auth.currentUser?.displayName,
                        userAvatarUrl = userAvatarUrl ?: auth.currentUser?.photoUrl?.toString(),
                        onProfileClick = onProfileClick
                    )
                }

                if (quickPicks.isNotEmpty()) {
                    item(key = "quick_picks_header") {
                        SectionHeader(title = "Quick Picks", subtitle = "START RADIO FROM A SONG")
                    }
                    item(key = "quick_picks_grid") {
                        SongHorizontalGrid(
                            songs = quickPicks,
                            onSongClick = onSongClick,
                            performance = performance
                        )
                    }
                }

                if (trending.isNotEmpty()) {
                    item(key = "trending_header") {
                        SectionHeader(title = "Trending", subtitle = "")
                    }
                    item(key = "trending_grid") {
                        SongHorizontalGrid(
                            songs = trending,
                            onSongClick = onSongClick,
                            performance = performance
                        )
                    }
                }
                    
                shelves.forEachIndexed { index, shelf ->
                    if (shelf.items.isNotEmpty()) {
                        item(key = "shelf_header_$index") {
                            SectionHeader(title = shelf.title, subtitle = "")
                        }

                        if (shelf.type == "videos") {
                            val songItems = shelf.items.filterIsInstance<Song>()
                            if (songItems.isNotEmpty()) {
                                item(key = "shelf_content_$index") {
                                    VideoHorizontalRow(
                                        songs = songItems,
                                        onSongClick = onSongClick,
                                        performance = performance
                                    )
                                }
                            }
                        } else if (shelf.type == "songs" || shelf.type == "quick_picks" || shelf.type == "trending") {
                            val songItems = shelf.items.filterIsInstance<Song>()
                            if (songItems.isNotEmpty()) {
                                item(key = "shelf_content_$index") {
                                    SongHorizontalGrid(
                                        songs = songItems,
                                        onSongClick = onSongClick,
                                        performance = performance
                                    )
                                }
                            } else {
                                item(key = "shelf_content_$index") {
                                    PlaylistShelfRow(
                                        items = shelf.items,
                                        shelfIndex = index,
                                        onPlaylistClick = onPlaylistClick,
                                        onArtistClick = onArtistClick,
                                        onSongClick = onSongClick,
                                        repository = repository,
                                        cookieManager = cookieManager,
                                        scope = scope,
                                        context = context,
                                        performance = performance
                                    )
                                }
                            }
                        } else {
                            item(key = "shelf_content_$index") {
                                PlaylistShelfRow(
                                    items = shelf.items,
                                    shelfIndex = index,
                                    onPlaylistClick = onPlaylistClick,
                                    onArtistClick = onArtistClick,
                                    onSongClick = onSongClick,
                                    repository = repository,
                                    cookieManager = cookieManager,
                                    scope = scope,
                                    context = context,
                                    performance = performance
                                )
                            }
                        }
                    }
                }

                // Footer Section
                item(key = "home_footer") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Wave Muzix",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lumino Global",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeTopBar(
    isLoggedIn: Boolean,
    userAvatarUrl: String?,
    onNotifyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    isLiquidGlass: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Wave Muzix",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 26.sp
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            var showHardwareDialog by remember { mutableStateOf(false) }
            GlassIconButton(
                icon = Icons.Default.Speed,
                onClick = { showHardwareDialog = true },
                isLiquidGlass = isLiquidGlass,
                contentDescription = "Hardware Optimization"
            )
            if (showHardwareDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showHardwareDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    HardwareOptimizationScreen(
                        onBack = { showHardwareDialog = false },
                        isLiquidGlass = isLiquidGlass
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            GlassIconButton(
                icon = Icons.Default.NotificationsNone,
                onClick = onNotifyClick,
                isLiquidGlass = isLiquidGlass,
                contentDescription = "Notifications"
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassIconButton(
                icon = Icons.Default.Settings,
                onClick = onSettingsClick,
                isLiquidGlass = isLiquidGlass,
                contentDescription = "Settings"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isLoggedIn) {
                    WaveUserAvatar(
                        url = userAvatarUrl,
                        name = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.Login,
                        onClick = onProfileClick,
                        isLiquidGlass = isLiquidGlass,
                        contentDescription = "Login"
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipsRow() {
    val filters = remember { listOf("All", "Relax", "Sleep", "Energize", "Sad", "Romance") }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = filters,
            key = { it }
        ) { filter ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (filter == "All") Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (filter == "All") Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (filter == "All") Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(text = filter, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun UserWelcomeSection(
    isLoggedIn: Boolean,
    userName: String?,
    userAvatarUrl: String?,
    onProfileClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text(
            "Welcome,",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            WaveUserAvatar(
                url = userAvatarUrl,
                name = if (isLoggedIn) userName else null,
                modifier = Modifier.size(52.dp),
                backgroundColor = SurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (isLoggedIn) (userName ?: "Wave User") else "Sign In",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onProfileClick() }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        if (title.contains("Quick Picks", ignoreCase = true)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.clickable { }
                ) {
                    Text(
                        "Play all",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun SongHorizontalGrid(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    performance: com.example.wavemuzix.data.PerformanceConfig = com.example.wavemuzix.data.LocalPerformanceConfig.current
) {
    val chunks = remember(songs) { songs.chunked(4) }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = chunks,
            key = { chunk -> chunk.firstOrNull()?.videoId ?: chunk.hashCode() }
        ) { chunk ->
            Column(
                modifier = Modifier.fillParentMaxWidth(0.95f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                chunk.forEach { song ->
                    HomeSongCard(
                        song = song,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSongClick(song) },
                        performance = performance
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistShelfRow(
    items: List<Any>,
    shelfIndex: Int,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    repository: UnifiedMusicRepository,
    cookieManager: CookieManager,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    performance: com.example.wavemuzix.data.PerformanceConfig = com.example.wavemuzix.data.LocalPerformanceConfig.current
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = items,
            key = { item ->
                when (item) {
                    is Song -> "song_${item.videoId}_$shelfIndex"
                    is Map<*, *> -> "playlist_${item["title"]}_${item["browseId"]}_$shelfIndex"
                    else -> item.hashCode()
                }
            },
            contentType = { item -> if (item is Song) "song_card" else "playlist_card" }
        ) { item ->
            when (item) {
                is Song -> {
                    HomeSongCard(
                        song = item,
                        modifier = Modifier.fillParentMaxWidth(0.95f),
                        onClick = { onSongClick(item) },
                        performance = performance
                    )
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val data = item as Map<String, String>
                    val type = data["type"] ?: "playlist"
                    val browseId = data["browseId"] ?: ""

                    if (type == "artist") {
                        ArtistCard(
                            artist = data,
                            isLiquidGlass = performance.isLiquidGlassEnabled,
                            modifier = Modifier.width(160.dp),
                            onClick = { if (browseId.isNotEmpty()) onArtistClick(browseId) }
                        )
                    } else {
                        PlaylistCard(
                            playlist = data,
                            isLiquidGlass = performance.isLiquidGlassEnabled,
                            modifier = Modifier.fillParentMaxWidth(0.45f),
                            onClick = { if (browseId.isNotEmpty()) onPlaylistClick(browseId) }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun QuickPickSongItem(song: Song, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(320.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlideImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SongItemRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlideImage(model = song.thumbnailUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeSongCard(
    song: Song, 
    modifier: Modifier = Modifier, 
    onClick: () -> Unit,
    performance: com.example.wavemuzix.data.PerformanceConfig = com.example.wavemuzix.data.LocalPerformanceConfig.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = performance.springDampingRatio,
            stiffness = performance.springStiffness
        ),
        label = "HomeSongCardScale"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlideImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                fontSize = 16.sp,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlaylistCard(
    playlist: Map<String, String>,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = true,
    onClick: () -> Unit
) {
    val performance = LocalPerformanceConfig.current
    Box(
        modifier = modifier
            .glassContainer(
                shape = RoundedCornerShape(12.dp),
                isLiquidGlass = isLiquidGlass,
                alphaTop = performance.surfaceAlpha
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box {
                GlideImage(
                    model = playlist["thumbnail"],
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                playlist["title"] ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Text(
                text = playlist["author"] ?: "Playlist",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistCard(
    artist: Map<String, String>,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = true,
    onClick: () -> Unit
) {
    val performance = LocalPerformanceConfig.current
    Box(
        modifier = modifier
            .width(160.dp)
            .glassContainer(
                shape = CircleShape,
                isLiquidGlass = isLiquidGlass,
                alphaTop = performance.surfaceAlpha
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlideImage(
                model = artist["thumbnail"],
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = artist["title"] ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
            Text(
                text = "Artist",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun VideoHorizontalRow(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    performance: com.example.wavemuzix.data.PerformanceConfig = com.example.wavemuzix.data.LocalPerformanceConfig.current
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = songs,
            key = { it.videoId }
        ) { song ->
            VideoCard(
                song = song,
                isLiquidGlass = performance.isLiquidGlassEnabled,
                modifier = Modifier.fillParentMaxWidth(0.95f),
                onClick = { onSongClick(song) }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun VideoCard(
    song: Song,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = true,
    onClick: () -> Unit
) {
    val performance = LocalPerformanceConfig.current
    Box(
        modifier = modifier
            .glassContainer(
                shape = RoundedCornerShape(12.dp),
                isLiquidGlass = isLiquidGlass,
                alphaTop = performance.surfaceAlpha
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box {
                GlideImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = song.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontSize = 18.sp
            )
            Text(
                text = song.artist,
                color = TextSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
