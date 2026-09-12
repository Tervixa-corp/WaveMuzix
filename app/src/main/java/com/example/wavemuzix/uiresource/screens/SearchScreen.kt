package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.repository.UnifiedMusicRepository
import com.example.wavemuzix.data.SettingsManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.SurfaceVariant
import com.example.wavemuzix.ui.theme.TextSecondary
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import com.example.wavemuzix.repository.PlaylistDetail
import com.example.wavemuzix.data.DownloadManager
import com.example.wavemuzix.uiresource.components.StandardLoader
import com.example.wavemuzix.uiresource.components.GlassIconButton
import androidx.compose.foundation.border
import com.example.wavemuzix.uiresource.components.glassContainer
import com.example.wavemuzix.data.LocalPerformanceConfig

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SearchScreen(
    unifiedRepository: UnifiedMusicRepository,
    cookies: String?,
    onPlaylistClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    isLiquidGlass: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    val suggestions = remember { mutableStateListOf<String>() }
    val searchResults = remember { mutableStateListOf<Song>() }
    val playlistResults = remember { mutableStateListOf<PlaylistDetail>() }
    val artistResults =
        remember { mutableStateListOf<com.example.wavemuzix.repository.ArtistDetail>() }
    var isLoading by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var isRealTimeLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    // Search Filter State: 0 = Both, 1 = Audio Only, 2 = Video Only
    var searchFilterMode by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val performance = LocalPerformanceConfig.current

    // Real-time suggestions and instant search
    LaunchedEffect(searchQuery, searchFilterMode) {
        if (searchQuery.isNotBlank()) {
            // Trigger skeleton instantly by setting loading and clearing results
            isRealTimeLoading = true
            isError = false
            suggestions.clear()
            searchResults.clear()
            playlistResults.clear()
            artistResults.clear()

            delay(300) // Debounce for smoother typing
            val startTime = System.currentTimeMillis()
            hasSearched = true
            try {
                // Use UnifiedMusicRepository for all metadata/UI sourcing
                // (Routed to YouTubeRepository internally)
                val suggestionsTask =
                    async { unifiedRepository.getSearchSuggestions(searchQuery, cookies) }

                val isAudioOnly = searchFilterMode == 1
                val isVideoOnly = searchFilterMode == 2

                // Use the filtered searchSongs for the songs list
                val filteredSongsTask = async {
                    unifiedRepository.searchSongs(
                        searchQuery,
                        cookies,
                        filterVideos = isAudioOnly,
                        filterOnlyVideos = isVideoOnly,
                        filterOnlyAudio = isAudioOnly
                    )
                }
                val searchAllTask = async {
                    unifiedRepository.searchAll(
                        searchQuery,
                        cookies,
                        filterVideos = isAudioOnly,
                        filterOnlyVideos = isVideoOnly,
                        filterOnlyAudio = isAudioOnly
                    )
                }

                val suggs = suggestionsTask.await()
                val filteredSongs = filteredSongsTask.await()
                val (_, playlists, artists) = searchAllTask.await()

                // Update UI in one go to prevent flickering
                suggestions.clear()
                suggestions.addAll(suggs.take(8))

                searchResults.clear()
                searchResults.addAll(filteredSongs)

                playlistResults.clear()
                playlistResults.addAll(playlists)

                artistResults.clear()
                artistResults.addAll(artists)

                val remainingTime = 1000 - (System.currentTimeMillis() - startTime)
                if (remainingTime > 0) delay(remainingTime)
            } catch (e: Exception) {
                e.printStackTrace()
                isError = true
            } finally {
                isRealTimeLoading = false
            }
        } else if (searchQuery.isEmpty()) {
            suggestions.clear()
            searchResults.clear()
            playlistResults.clear()
            artistResults.clear()
            hasSearched = false
            isRealTimeLoading = false
        }
    }

    val performSearch = {
        if (searchQuery.isNotBlank()) {
            scope.launch {
                isLoading = true
                isError = false
                val startTime = System.currentTimeMillis()
                hasSearched = true
                keyboardController?.hide()
                suggestions.clear() // Clear suggestions on formal search
                try {
                    // Use UnifiedMusicRepository for all metadata/UI sourcing
                    val isAudioOnly = searchFilterMode == 1
                    val isVideoOnly = searchFilterMode == 2

                    val songsTask = async {
                        unifiedRepository.searchSongs(
                            searchQuery,
                            cookies,
                            filterVideos = isAudioOnly,
                            filterOnlyVideos = isVideoOnly,
                            filterOnlyAudio = isAudioOnly
                        )
                    }
                    val allTask = async {
                        unifiedRepository.searchAll(
                            searchQuery,
                            cookies,
                            filterVideos = isAudioOnly,
                            filterOnlyVideos = isVideoOnly,
                            filterOnlyAudio = isAudioOnly
                        )
                    }

                    val songs = songsTask.await()
                    val (_, playlists, artists) = allTask.await()

                    searchResults.clear()
                    searchResults.addAll(songs)

                    playlistResults.clear()
                    playlistResults.addAll(playlists)

                    artistResults.clear()
                    artistResults.addAll(artists)

                    val remainingTime = 1000 - (System.currentTimeMillis() - startTime)
                    if (remainingTime > 0) delay(remainingTime)
                } catch (e: Exception) {
                    e.printStackTrace()
                    isError = true
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SoftBlack, DeepBlack)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Custom Modern Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .glassContainer(
                    shape = RoundedCornerShape(28.dp),
                    isLiquidGlass = isLiquidGlass,
                    alphaTop = performance.surfaceAlpha,
                    alphaBottom = performance.surfaceAlpha / 2f
                )
                .clickable { focusRequester.requestFocus() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )

                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search for songs, artists...",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                        singleLine = true
                    )
                }

                if (isLoading || isRealTimeLoading) {
                    StandardLoader(
                        size = 20.dp
                    )
                } else if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Default.Clear,
                            null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Search Filter Row (Below Search Bar)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filterIcon = when (searchFilterMode) {
                1 -> Icons.Default.MusicNote
                2 -> Icons.Default.Movie
                else -> Icons.Default.AutoMode // Combined look
            }
            val filterText = when (searchFilterMode) {
                1 -> "Audio Only"
                2 -> "Video Only"
                else -> "Audio & Video"
            }

            Box(
                modifier = Modifier
                    .glassContainer(
                        shape = RoundedCornerShape(12.dp),
                        isLiquidGlass = isLiquidGlass,
                        alphaTop = performance.surfaceAlpha.coerceAtLeast(0.1f),
                        alphaBottom = performance.surfaceAlpha.coerceAtLeast(0.05f)
                    )
                    .clickable {
                        searchFilterMode = (searchFilterMode + 1) % 3
                        Toast.makeText(context, "Filter: $filterText", Toast.LENGTH_SHORT).show()
                        if (searchQuery.isNotBlank()) performSearch()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = filterIcon,
                        contentDescription = null,
                        tint = if (searchFilterMode != 0) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = filterText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Text(
                "Suggestions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                items(
                    items = suggestions,
                    key = { it }
                ) { suggestion ->
                    Box(
                        modifier = Modifier
                            .glassContainer(
                                shape = RoundedCornerShape(16.dp),
                                isLiquidGlass = isLiquidGlass,
                                alphaTop = performance.surfaceAlpha.coerceAtLeast(0.1f)
                            )
                            .clickable {
                                searchQuery = suggestion
                                performSearch()
                            }
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
        ) {
            val trendingSongs = remember { mutableStateListOf<Song>() }

            LaunchedEffect(Unit) {
                if (searchResults.isEmpty() && !hasSearched) {
                    try {
                        val trending = unifiedRepository.fetchTopMusicVideos(cookies)
                        trendingSongs.clear()
                        trendingSongs.addAll(trending)
                    } catch (e: Exception) {
                    }
                }
            }

            com.example.wavemuzix.uiresource.components.WaveStateContainer(
                isLoading = (isLoading || isRealTimeLoading) && searchResults.isEmpty() && playlistResults.isEmpty() && artistResults.isEmpty() && trendingSongs.isEmpty(),
                isError = isError || MusicPlayer.isPlaybackError,
                errorMessage = if (MusicPlayer.isPlaybackError) MusicPlayer.playbackErrorMessage else "Check Your Internet Connection",
                onRetry = {
                    if (MusicPlayer.isPlaybackError) {
                        MusicPlayer.currentSong?.let { MusicPlayer.playSong(context, it, cookies = cookies) }
                    } else {
                        performSearch()
                    }
                },
                loadingContent = {
                    com.example.wavemuzix.uiresource.components.SearchScreenSkeleton()
                }
            ) {
                if (hasSearched && searchResults.isEmpty() && playlistResults.isEmpty() && artistResults.isEmpty() && !isLoading && !isRealTimeLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No results found. Try a different query.", color = TextSecondary)
                        }
                    }
                } else if (!hasSearched && searchResults.isEmpty() && playlistResults.isEmpty() && artistResults.isEmpty() && !isLoading && !isRealTimeLoading) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 190.dp
                        )
                    ) {
                        if (trendingSongs.isNotEmpty()) {
                            item(key = "trending_header") {
                                Text(
                                    "Trending Now",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(
                                items = trendingSongs,
                                key = { it.videoId }
                            ) { song ->
                                SearchSongCard(
                                    song = song,
                                    isLiquidGlass = isLiquidGlass,
                                    onClick = {
                                        MusicPlayer.playSong(context, song, cookies = cookies)
                                    }
                                )
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            null,
                                            tint = TextSecondary.copy(alpha = 0.2f),
                                            modifier = Modifier.size(100.dp)
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "Search for your favorite tracks",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
                    var showPlaylistDialogFor by remember { mutableStateOf<Song?>(null) }
                    val favoriteRepository = remember {
                        com.example.wavemuzix.repository.FavoriteRepository(
                            com.example.wavemuzix.data.database.AppDatabase.getDatabase(context)
                                .songDao(), context
                        )
                    }
                    val playlistRepository = remember {
                        com.example.wavemuzix.repository.PlaylistRepository(
                            com.example.wavemuzix.data.database.AppDatabase.getDatabase(context)
                                .playlistDao(), context
                        )
                    }

                    val downloadedSongs by com.example.wavemuzix.data.database.AppDatabase.getDatabase(
                        context
                    ).songDao().getAllDownloads().collectAsState(initial = emptyList())
                    val likedSongs by com.example.wavemuzix.data.database.AppDatabase.getDatabase(
                        context
                    ).songDao().getAllFavorites().collectAsState(initial = emptyList())
                    val allPlaylists by playlistRepository.allPlaylists.collectAsState(initial = emptyList())

                    if (showPlaylistDialogFor != null) {
                        com.example.wavemuzix.uiresource.components.AddToPlaylistDialog(
                            song = showPlaylistDialogFor!!,
                            playlists = allPlaylists,
                            onDismiss = { showPlaylistDialogFor = null },
                            isLiquidGlass = isLiquidGlass,
                            onPlaylistSelected = { playlist ->
                                scope.launch {
                                    playlistRepository.addSongToPlaylist(
                                        playlist.id,
                                        showPlaylistDialogFor!!
                                    )
                                    Toast.makeText(
                                        context,
                                        "Added to ${playlist.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showPlaylistDialogFor = null
                                }
                            },
                            onCreatePlaylist = { name ->
                                scope.launch {
                                    val id = playlistRepository.createPlaylist(name)
                                    playlistRepository.addSongToPlaylist(
                                        id,
                                        showPlaylistDialogFor!!
                                    )
                                    Toast.makeText(
                                        context,
                                        "Created and added to $name",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showPlaylistDialogFor = null
                                }
                            }
                        )
                    }

                    if (selectedSongForOptions != null) {
                        val song = selectedSongForOptions!!
                        val isDownloaded = downloadedSongs.any { it.videoId == song.videoId }
                        val isFavorite = likedSongs.any { it.videoId == song.videoId }

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
                                        com.example.wavemuzix.data.database.AppDatabase.getDatabase(
                                            context
                                        ).songDao().deleteDownload(song.videoId)
                                        downloadedSongs.find { it.videoId == song.videoId }?.filePath?.let {
                                            java.io.File(
                                                it
                                            ).delete()
                                        }
                                        Toast.makeText(
                                            context,
                                            "Removed download",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        
                                        val result = unifiedRepository.getStreamingUrl(
                                            videoId = song.videoId,
                                            cookies = cookies
                                        )
                                        if (result != null) {
                                            DownloadManager.updateProgress(
                                                song.videoId,
                                                song.title,
                                                0
                                            )
                                            val request =
                                                androidx.work.OneTimeWorkRequestBuilder<com.example.wavemuzix.data.DownloadWorker>()
                                                    .setInputData(
                                                        androidx.work.workDataOf(
                                                            "url" to result.url,
                                                            "videoId" to song.videoId,
                                                            "title" to song.title,
                                                            "artist" to song.artist,
                                                            "thumbnailUrl" to song.thumbnailUrl,
                                                            "artistId" to song.artistId,
                                                            "userAgent" to result.userAgent,
                                                            "cookies" to cookies
                                                        )
                                                    )
                                                    .build()
                                            androidx.work.WorkManager.getInstance(context)
                                                .enqueue(request)
                                            Toast.makeText(
                                                context,
                                                "Downloading...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onPlayNextClick = { MusicPlayer.playNext(song) },
                            onAddToQueueClick = { MusicPlayer.addToQueue(song) },
                            onArtistClick = { onArtistClick(it); selectedSongForOptions = null },
                            onAddToPlaylistClick = {
                                showPlaylistDialogFor = song; selectedSongForOptions = null
                            },
                            onShareClick = {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        "Listen to ${song.title} by ${song.artist} on WaveMuzix: https://www.youtube.com/watch?v=${song.videoId}"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(
                                        sendIntent,
                                        null
                                    )
                                )
                            }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 190.dp
                        )
                    ) {
                        // Artists Section
                        if (artistResults.isNotEmpty()) {
                            item(key = "artists_header") {
                                Text(
                                    "Artists",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            item(key = "artists_row") {
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(
                                        items = artistResults,
                                        key = { it.browseId }
                                    ) { artist ->
                                        SearchArtistCard(
                                            artist = artist,
                                            isLiquidGlass = isLiquidGlass
                                        ) {
                                            onArtistClick(artist.browseId)
                                        }
                                    }
                                }
                            }
                        }

                        // Playlists Section
                        if (playlistResults.isNotEmpty()) {
                            item(key = "playlists_header") {
                                Text(
                                    "Playlists",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            item(key = "playlists_row") {
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(
                                        items = playlistResults,
                                        key = { it.browseId }
                                    ) { playlist ->
                                        SearchPlaylistCard(
                                            playlist = playlist,
                                            isLiquidGlass = isLiquidGlass,
                                            onClick = { onPlaylistClick(playlist.browseId) }
                                        )
                                    }
                                }
                            }
                        }

                        // Songs Section
                        if (searchResults.isNotEmpty()) {
                            item(key = "top_results_header") {
                                Text(
                                    "Songs",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            items(
                                items = searchResults,
                                key = { it.videoId },
                                contentType = { "search_song_item" }
                            ) { song ->
                                SearchSongCard(
                                    song = song,
                                    isLiquidGlass = isLiquidGlass,
                                    onClick = {
                                        MusicPlayer.playSong(context, song, cookies = cookies)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SearchArtistCard(
    artist: com.example.wavemuzix.repository.ArtistDetail,
    isLiquidGlass: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val performance = LocalPerformanceConfig.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = performance.springDampingRatio, stiffness = performance.springStiffness),
        label = "SearchArtistCardScale"
    )

    Box(
        modifier = Modifier
            .width(120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassContainer(
                shape = RoundedCornerShape(20.dp),
                isLiquidGlass = isLiquidGlass,
                alphaTop = performance.surfaceAlpha
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlideImage(
                model = artist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = artist.name,
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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SearchPlaylistCard(
    playlist: PlaylistDetail,
    isLiquidGlass: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val performance = LocalPerformanceConfig.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = performance.springDampingRatio, stiffness = performance.springStiffness),
        label = "SearchPlaylistCardScale"
    )

    Box(
        modifier = Modifier
            .width(120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassContainer(
                shape = RoundedCornerShape(20.dp),
                isLiquidGlass = isLiquidGlass,
                alphaTop = performance.surfaceAlpha
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box {
                GlideImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(104.dp)
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
                playlist.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
            Text(
                text = "Playlist",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SearchSongCard(
    song: Song,
    isLiquidGlass: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val performance = LocalPerformanceConfig.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = performance.springDampingRatio, stiffness = performance.springStiffness),
        label = "SearchSongCardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassContainer(
                shape = RoundedCornerShape(16.dp),
                isLiquidGlass = isLiquidGlass,
                alphaTop = performance.surfaceAlpha
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlideImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    fontSize = 14.sp,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
