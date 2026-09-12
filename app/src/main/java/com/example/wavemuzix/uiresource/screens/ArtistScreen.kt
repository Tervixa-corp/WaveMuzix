package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.repository.ArtistDetail
import com.example.wavemuzix.repository.UnifiedMusicRepository
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.data.database.AppDatabase
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.repository.FavoriteRepository
import com.example.wavemuzix.data.CookieManager
import com.example.wavemuzix.data.DownloadManager
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.wavemuzix.data.DownloadWorker
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import com.example.wavemuzix.uiresource.components.SongOptionsBottomSheet
import com.example.wavemuzix.uiresource.components.GlassIconButton
import com.example.wavemuzix.uiresource.components.StandardLoader
import com.example.wavemuzix.uiresource.components.glassContainer
import com.example.wavemuzix.uiresource.components.WaveStateContainer
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import com.example.wavemuzix.data.LocalPerformanceConfig

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: String,
    repository: UnifiedMusicRepository,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (String) -> Unit,
    isLiquidGlass: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val performance = LocalPerformanceConfig.current
    val favoriteRepository = remember { FavoriteRepository(AppDatabase.getDatabase(context).songDao(), context) }
    val downloadedSongs by AppDatabase.getDatabase(context).songDao().getAllDownloads().collectAsState(initial = emptyList())
    val likedSongs by AppDatabase.getDatabase(context).songDao().getAllFavorites().collectAsState(initial = emptyList())

    var artistDetail by remember { mutableStateOf<ArtistDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var showAllSongs by remember { mutableStateOf(false) }
    var fullArtistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingMoreSongs by remember { mutableStateOf(false) }

    val fetchData = {
        scope.launch {
            isLoading = true
            isError = false
            try {
                // Primary: Try fetching as a browseId (UC... or FMa...)
                artistDetail = if (artistId.startsWith("UC") || artistId.startsWith("FMa")) {
                    repository.fetchArtist(artistId, null)
                } else {
                    // Fallback: If artistId is actually a name (from Deezer/Search), resolve it
                    repository.getArtistChannel(artistId, null)
                }

                if (artistDetail == null) isError = true
            } catch (e: Exception) {
                isError = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(artistId) {
        fetchData()
    }

    LaunchedEffect(showAllSongs) {
        if (showAllSongs && fullArtistSongs.isEmpty()) {
            val detail = artistDetail
            if (detail?.songsBrowseId != null) {
                isLoadingMoreSongs = true
                fullArtistSongs = repository.fetchArtistSongs(detail.songsBrowseId, detail.songsParams, null)
                isLoadingMoreSongs = false
            }
        }
    }

    if (selectedSongForOptions != null) {
        val song = selectedSongForOptions!!
        val isDownloaded = downloadedSongs.any { it.videoId == song.videoId }
        val isFavorite = likedSongs.any { it.videoId == song.videoId }

        SongOptionsBottomSheet(
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
                        AppDatabase.getDatabase(context).songDao().deleteDownload(song.videoId)
                        downloadedSongs.find { it.videoId == song.videoId }?.filePath?.let { java.io.File(it).delete() }
                        Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show()
                    } else {
                        val cookies = CookieManager(context).getCookies()
                        val result = repository.getStreamingUrl(
                            videoId = song.videoId,
                            cookies = cookies
                        )
                        if (result != null) {
                            DownloadManager.updateProgress(song.videoId, song.title, 0)
                            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                                .setInputData(workDataOf(
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
                            WorkManager.getInstance(context).enqueue(request)
                            Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onPlayNextClick = { MusicPlayer.playNext(song) },
            onAddToQueueClick = { MusicPlayer.addToQueue(song) },
            onArtistClick = { /* Already on artist page */ },
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

    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(SoftBlack, DeepBlack)
            )
        )
    ) {
        artistDetail?.thumbnailUrl?.let { thumb ->
            GlideImage(
                model = thumb,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .blur(25.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, DeepBlack)
                        )
                    )
            )
        }

        WaveStateContainer(
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
            artistDetail?.let { detail ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 190.dp)
                ) {
                    item {
                        ArtistHeaderSection(detail, onBack, isLiquidGlass)
                    }

                    if (detail.songs.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionLabel("Top Tracks")
                                if (detail.songsBrowseId != null) {
                                    Text(
                                        text = if (showAllSongs) "Show less" else "Show all",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { showAllSongs = !showAllSongs }
                                    )
                                }
                            }
                        }

                        if (showAllSongs && isLoadingMoreSongs) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    StandardLoader()
                                }
                            }
                        }

                        val songsToShow = if (showAllSongs) (if (fullArtistSongs.isNotEmpty()) fullArtistSongs else detail.songs) else detail.songs
                        items(songsToShow) { song ->
                            ArtistSongCard(
                                song = song,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            ) {
                                MusicPlayer.playSong(context, song, songsToShow, isFromFixedList = true)
                            }
                        }
                    }

                    val albums = detail.items.filter { it["type"] == "album" || it["type"] == "single" }
                    if (albums.isNotEmpty()) {
                        item {
                            SectionLabel("Discography")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = albums,
                                    key = { it["browseId"] ?: it.hashCode() }
                                ) { album ->
                                    ArtistAlbumCard(
                                        album = album.mapValues { it.value.toString() },
                                        isLiquidGlass = isLiquidGlass
                                    ) {
                                        album["browseId"]?.toString()?.let { onAlbumClick(it) }
                                    }
                                }
                            }
                        }
                    }

                    val playlists = detail.items.filter { it["type"] == "playlist" }
                    if (playlists.isNotEmpty()) {
                        item {
                            SectionLabel("Featured In")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = playlists,
                                    key = { it["browseId"] ?: it.hashCode() }
                                ) { playlist ->
                                    ArtistAlbumCard(
                                        album = playlist.mapValues { it.value.toString() },
                                        isLiquidGlass = isLiquidGlass
                                    ) {
                                        playlist["browseId"]?.toString()?.let { onAlbumClick(it) }
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

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        fontWeight = FontWeight.ExtraBold
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistHeaderSection(detail: ArtistDetail, onBack: () -> Unit, isLiquidGlass: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                modifier = Modifier.padding(8.dp),
                isLiquidGlass = isLiquidGlass,
                contentDescription = "Back"
            )
        }

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 32.dp,
                color = Color.Transparent
            ) {
                GlideImage(
                    model = detail.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = detail.name.uppercase(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontSize = 32.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (!detail.description.isNullOrEmpty()) {
            Text(
                detail.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.7f),
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 8.dp),
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (detail.songs.isNotEmpty()) {
                        MusicPlayer.playSong(context, detail.songs.shuffled().first(), detail.songs, isFromFixedList = true)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text("SHUFFLE", color = Color.Black, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }

            OutlinedButton(
                onClick = { /* Follow */ },
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Text("FOLLOW", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistSongCard(
    song: Song,
    isLiquidGlass: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val performance = LocalPerformanceConfig.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = performance.springDampingRatio, stiffness = performance.springStiffness),
        label = "ArtistSongCardScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassContainer(
                shape = RoundedCornerShape(12.dp),
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
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                GlideImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    fontSize = 15.sp,
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

            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistAlbumCard(
    album: Map<String, String>,
    isLiquidGlass: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val performance = LocalPerformanceConfig.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = performance.springDampingRatio,
            stiffness = performance.springStiffness
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .width(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassContainer(
                shape = RoundedCornerShape(12.dp),
                isLiquidGlass = isLiquidGlass,
                alphaTop = performance.surfaceAlpha
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                GlideImage(
                    model = album["thumbnail"],
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                            )
                        )
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                album["title"] ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
            Text(
                text = album["type"]?.uppercase() ?: "ALBUM",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
