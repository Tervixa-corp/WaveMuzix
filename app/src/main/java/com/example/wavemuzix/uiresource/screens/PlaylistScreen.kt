package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.data.CookieManager
import com.example.wavemuzix.data.DownloadManager
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.data.database.AppDatabase
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.repository.PlaylistDetail
import com.example.wavemuzix.repository.UnifiedMusicRepository
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.filled.MoreVert

import com.example.wavemuzix.uiresource.components.GlassIconButton
import com.example.wavemuzix.uiresource.components.StandardLoader
import androidx.compose.foundation.border
import com.example.wavemuzix.uiresource.components.glassContainer
import kotlinx.coroutines.flow.first

import com.example.wavemuzix.data.LocalPerformanceConfig

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlaylistScreen(
    browseId: String,
    isLocal: Boolean = false,
    repository: UnifiedMusicRepository,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    isLiquidGlass: Boolean
) {
    val context = LocalContext.current
    val cookieManager = remember { CookieManager(context) }
    val scope = rememberCoroutineScope()
    val playlistRepository = remember { com.example.wavemuzix.repository.PlaylistRepository(AppDatabase.getDatabase(context).playlistDao(), context) }
    
    var playlistDetail by remember { mutableStateOf<PlaylistDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var isMoreLoading by remember { mutableStateOf(false) }
    var continuationToken by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val fetchData = {
        scope.launch {
            isLoading = true
            isError = false
            val startTime = System.currentTimeMillis()
            
            try {
                if (isLocal) {
                    val playlistId = browseId.toLongOrNull()
                    if (playlistId != null) {
                        val playlist = AppDatabase.getDatabase(context).playlistDao().getPlaylistById(playlistId)
                        if (playlist != null) {
                            val songs = AppDatabase.getDatabase(context).playlistDao().getSongsInPlaylist(playlistId).first().map { songEntry ->
                                Song(
                                    id = songEntry.videoId,
                                    title = songEntry.title,
                                    artist = songEntry.artist,
                                    thumbnailUrl = songEntry.thumbnailUrl,
                                    videoId = songEntry.videoId,
                                    artistId = songEntry.artistId
                                )
                            }
                            playlistDetail = PlaylistDetail(playlist.name, "Custom Playlist", songs.firstOrNull()?.thumbnailUrl ?: "", songs, browseId, null)
                        }
                    }
                } else {
                    val settingsManager = SettingsManager(context)
                    val detail = repository.fetchPlaylist(browseId, cookieManager.getCookies(), settingsManager.selectedLanguages)
                    playlistDetail = detail
                    continuationToken = detail?.continuation
                }
                
                if (playlistDetail == null) isError = true
            } catch (e: Exception) {
                isError = true
            } finally {
                val remainingTime = 500 - (System.currentTimeMillis() - startTime)
                if (remainingTime > 0) delay(remainingTime)
                isLoading = false
            }
        }
    }

    LaunchedEffect(browseId, isLocal) {
        fetchData()
    }

    val filteredSongs = remember(playlistDetail, searchQuery) {
        if (searchQuery.isEmpty()) {
            playlistDetail?.songs ?: emptyList()
        } else {
            playlistDetail?.songs?.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            } ?: emptyList()
        }
    }

    val lazyListState = rememberLazyListState()
    val performance = LocalPerformanceConfig.current
    
    // Load more when reaching end
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= (playlistDetail?.songs?.size ?: 0) - 5 && continuationToken != null && !isMoreLoading) {
                    isMoreLoading = true
                    val (nextSongs, nextContinuation) = repository.fetchNextSongs("", browseId, cookieManager.getCookies(), continuationToken)
                    if (nextSongs.isNotEmpty()) {
                        playlistDetail = playlistDetail?.copy(songs = playlistDetail!!.songs + nextSongs)
                        continuationToken = nextContinuation
                    } else {
                        continuationToken = null
                    }
                    isMoreLoading = false
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SoftBlack, DeepBlack)))
    ) {
        com.example.wavemuzix.uiresource.components.WaveStateContainer(
            isLoading = isLoading,
            isError = isError || MusicPlayer.isPlaybackError,
            errorMessage = if (MusicPlayer.isPlaybackError) MusicPlayer.playbackErrorMessage else "Check Your Internet Connection",
            onRetry = { 
                if (MusicPlayer.isPlaybackError) {
                    MusicPlayer.currentSong?.let { MusicPlayer.playSong(context, it) }
                } else {
                    fetchData()
                }
            }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 190.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
                item {
                    PlaylistHeader(
                        playlist = playlistDetail!!,
                        repository = repository,
                        scope = scope,
                        cookieManager = cookieManager,
                        onPlayAll = {
                            if (playlistDetail!!.songs.isNotEmpty()) {
                                val firstSong = playlistDetail!!.songs.first()
                                MusicPlayer.playSong(context, firstSong, playlistDetail!!.songs, cookieManager.getCookies(), isFromFixedList = true)
                            }
                        },
                        onShuffle = {
                            if (playlistDetail!!.songs.isNotEmpty()) {
                                val shuffled = playlistDetail!!.songs.shuffled()
                                MusicPlayer.playSong(context, shuffled.first(), shuffled, cookieManager.getCookies(), isFromFixedList = true)
                            }
                        }
                    )
                }

                items(
                    items = filteredSongs,
                    key = { it.videoId }
                ) { song ->
                    PlaylistSongItem(
                        song = song,
                        modifier = Modifier,
                        repository = repository,
                        scope = scope,
                        onClick = {
                            MusicPlayer.playSong(context, song, playlistDetail!!.songs, cookieManager.getCookies(), isFromFixedList = true)
                        },
                        onArtistClick = onArtistClick,
                        isLiquidGlass = isLiquidGlass
                    )
                }

                if (filteredSongs.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No songs found matching \"$searchQuery\"", color = TextSecondary)
                        }
                    }
                }

                if (isMoreLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            StandardLoader(size = 32.dp)
                        }
                    }
                }
            }
        }

        // Top Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchActive) {
                IconButton(onClick = {
                    isSearchActive = false
                    searchQuery = ""
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search in playlist...", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    isLiquidGlass = isLiquidGlass,
                    contentDescription = "Back"
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    icon = Icons.Default.Search,
                    onClick = { isSearchActive = true },
                    isLiquidGlass = isLiquidGlass,
                    contentDescription = "Search"
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlaylistHeader(
    playlist: PlaylistDetail,
    repository: UnifiedMusicRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    cookieManager: CookieManager,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            GlideImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Gradient Overlay on bottom of image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, SoftBlack),
                            startY = 300f
                        )
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        
        if (!playlist.description.isNullOrEmpty()) {
            Text(
                text = playlist.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp).fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Play and Shuffle Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Play All", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onShuffle,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Shuffle, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Shuffle", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIcon(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "Like",
                tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White
            ) {
                isLiked = !isLiked
                Toast.makeText(context, if (isLiked) "Added to Liked Songs" else "Removed from Liked Songs", Toast.LENGTH_SHORT).show()
            }

            // Download all icon
            ActionIcon(icon = Icons.Default.FileDownload, label = "Download") {
                scope.launch {
                    val cookies = cookieManager.getCookies()
                    val settingsManager = SettingsManager(context)
                    val targetBitrate = null
                    Toast.makeText(context, "Downloading playlist...", Toast.LENGTH_SHORT).show()
                    playlist.songs.forEach { song ->
                        val result = repository.getStreamingUrl(
                            videoId = song.videoId,
                            cookies = cookies,
                            targetBitrate = targetBitrate
                        )
                        if (result != null) {
                            DownloadManager.updateProgress(song.videoId, song.title, 0)
                            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.wavemuzix.data.DownloadWorker>()
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
                            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                        }
                    }
                }
            }

            ActionIcon(icon = Icons.Default.Share, label = "Share") {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Check out this playlist: ${playlist.title}\nhttps://music.youtube.com/playlist?list=${playlist.thumbnailUrl.substringAfter("list=", "")}")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            }
        }
    }
}

@Composable
fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlaylistSongItem(
    song: Song,
    modifier: Modifier = Modifier,
    repository: UnifiedMusicRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    onClick: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    isLiquidGlass: Boolean = false
) {
    var showOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val favoriteRepository = remember { com.example.wavemuzix.repository.FavoriteRepository(AppDatabase.getDatabase(context).songDao(), context) }
    val downloadedSongs by AppDatabase.getDatabase(context).songDao().getAllDownloads().collectAsState(initial = emptyList())
    val likedSongs by AppDatabase.getDatabase(context).songDao().getAllFavorites().collectAsState(initial = emptyList())

    val isDownloaded = downloadedSongs.any { it.videoId == song.videoId }
    val isFavorite = likedSongs.any { it.videoId == song.videoId }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val performance = LocalPerformanceConfig.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = performance.springDampingRatio,
            stiffness = performance.springStiffness
        ),
        label = "PlaylistSongItemScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlideImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                val activeDownload = DownloadManager.activeDownloads.find { it.videoId == song.videoId }

                if (isDownloaded) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                } else if (activeDownload != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    StandardLoader(
                        size = 12.dp
                    )
                }
            }
        }

        GlassIconButton(
            icon = Icons.Default.MoreVert,
            onClick = { showOptions = true },
            isLiquidGlass = isLiquidGlass,
            contentDescription = "Menu",
            containerSize = 40.dp,
            iconSize = 20.dp
        )

        if (showOptions) {
            com.example.wavemuzix.uiresource.components.SongOptionsBottomSheet(
                song = song,
                isFavorite = isFavorite,
                isDownloaded = isDownloaded,
                isLiquidGlass = isLiquidGlass,
                onDismiss = { showOptions = false },
                onFavoriteClick = {
                    scope.launch { favoriteRepository.toggleFavorite(song) }
                },
                onDownloadClick = {
                    scope.launch {
                        if (isDownloaded) {
                            AppDatabase.getDatabase(context).songDao().deleteDownload(song.videoId)
                            downloadedSongs.find { it.videoId == song.videoId }?.filePath?.let { java.io.File(it).delete() }
                            Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show()
                        } else {
                            val cookies = CookieManager(context).getCookies()
                            val settingsManager = SettingsManager(context)
                            val result = repository.getStreamingUrl(
                                videoId = song.videoId,
                                cookies = cookies,
                                targetBitrate = null
                            )
                            if (result != null) {
                                DownloadManager.updateProgress(song.videoId, song.title, 0)
                                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.wavemuzix.data.DownloadWorker>()
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
                                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                                Toast.makeText(context, "Downloading ${song.title}...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onPlayNextClick = { MusicPlayer.playNext(song) },
                onAddToQueueClick = { MusicPlayer.addToQueue(song) },
                onArtistClick = { onArtistClick(it); showOptions = false },
                onAddToPlaylistClick = { /* Implement playlist selection */ },
                onShareClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listen to ${song.title} by ${song.artist} on WaveMuzix: https://www.youtube.com/watch?v=${song.videoId}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            )
        }
    }
}

