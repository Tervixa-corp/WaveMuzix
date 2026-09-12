package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import com.example.wavemuzix.repository.FavoriteRepository
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.lazy.items
import com.example.wavemuzix.repository.UnifiedMusicRepository
import com.example.wavemuzix.repository.PlaylistRepository
import com.example.wavemuzix.data.database.AppDatabase
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.data.CookieManager
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.uiresource.components.GlassIconButton
import com.example.wavemuzix.uiresource.components.glassContainer
import kotlinx.coroutines.*
import java.io.File
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.SurfaceVariant
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.WaveUserAvatar
import com.example.wavemuzix.uiresource.components.StandardLoader
import android.content.Intent
import android.widget.Toast
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.data.DownloadManager
import com.example.wavemuzix.data.DataBackupManager
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.uiresource.components.TutorialOverlay
import com.example.wavemuzix.model.Song as SongModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LibraryScreen(
    unifiedRepository: UnifiedMusicRepository,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onLoginClick: () -> Unit,
    onManageAccountClick: () -> Unit,
    isLiquidGlass: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    var showLibraryTutorial by remember { mutableStateOf(!settingsManager.hasSeenLibraryTutorial) }
    val cookieManager = remember { CookieManager(context) }
    val auth = remember { Firebase.auth }
    
    val playlistRepository = remember {
        PlaylistRepository(AppDatabase.getDatabase(context).playlistDao(), context)
    }
    val playlists by remember(playlistRepository) { playlistRepository.allPlaylists }.collectAsState(initial = emptyList())
    val songDao = remember(context) { AppDatabase.getDatabase(context.applicationContext).songDao() }
    val favoriteRepository = remember(songDao) { FavoriteRepository(songDao, context) }
    val mostPlayedSongs by songDao.getMostPlayedSongs().collectAsState(initial = emptyList())
    val downloadedSongs by songDao.getAllDownloads().collectAsState(initial = emptyList())
    val likedSongs by songDao.getAllFavorites().collectAsState(initial = emptyList())
    
    // Restore and Sync data from Firebase and Local Backup
    var isSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(auth.currentUser?.uid) {
        val currentUserId = auth.currentUser?.uid
        
        // Use global session tracking to prevent redundant syncs when switching screens
        if (currentUserId != null && FirebaseSyncManager.needsSync(currentUserId)) {
            isSyncing = true
            try {
                val startTime = System.currentTimeMillis()
                
                // Sync from Firebase if logged in
                FirebaseSyncManager.syncFromFirebase(context)
                
                // Also restore from local backup
                DataBackupManager.restoreAll(context)
                
                // Ensure we mark as synced even if Firebase was empty
                FirebaseSyncManager.markSynced(currentUserId)
                
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 1000) {
                    delay(1000 - elapsedTime)
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "Initial sync/restore failed", e)
            } finally {
                isSyncing = false
            }
        } else {
            // Not logged in or already synced
            isSyncing = false
        }
    }

    // Explicitly check for changes in the database
    LaunchedEffect(downloadedSongs) {
        android.util.Log.d("LibraryScreen", "Total downloads in DB: ${downloadedSongs.size}")
    }

    var activeTab by remember { mutableStateOf("Liked") }
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
    var userName by remember { mutableStateOf<String?>(null) }
    var userAvatarUrl by remember { mutableStateOf<String?>(null) }

    val musicVideos = remember { mutableStateListOf<SongModel>() }
    var isVideoLoading by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) {
        if (activeTab == "Videos" && musicVideos.isEmpty()) {
            isVideoLoading = true
            try {
                val videoResults = unifiedRepository.fetchTopMusicVideos(cookieManager.getCookies())
                if (videoResults.isNotEmpty()) {
                    musicVideos.addAll(videoResults)
                } else {
                    // Fallback to search if charts fail
                    val searchResults = unifiedRepository.searchSongs("trending music videos", cookieManager.getCookies(), filterOnlyVideos = true)
                    musicVideos.addAll(searchResults)
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "Failed to fetch top music videos", e)
            } finally {
                isVideoLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        auth.addAuthStateListener { firebaseAuth ->
            isLoggedIn = firebaseAuth.currentUser != null
        }
    }

    // Fetch user profile info
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                Firebase.firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { doc ->
                        userName = doc.getString("displayName") ?: auth.currentUser?.displayName
                        userAvatarUrl = doc.getString("photoUrl") ?: auth.currentUser?.photoUrl?.toString()
                    }
            } else {
                userName = auth.currentUser?.displayName
                userAvatarUrl = auth.currentUser?.photoUrl?.toString()
            }
        } else {
            userName = null
            userAvatarUrl = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(SoftBlack, DeepBlack)
                    )
                )
        ) {
            // 1. Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val onProfileClick = {
                    if (isLoggedIn) {
                        onManageAccountClick()
                    } else {
                        onLoginClick()
                    }
                }

                Column {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 26.sp
                        )
                    )
                    val currentUserName = auth.currentUser?.displayName ?: userName
                    if (isLoggedIn && currentUserName != null) {
                        Text(
                            text = currentUserName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        icon = Icons.Default.Search,
                        onClick = onSearchClick,
                        isLiquidGlass = isLiquidGlass,
                        contentDescription = "Search"
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
                                url = userAvatarUrl ?: auth.currentUser?.photoUrl?.toString(),
                                name = userName ?: auth.currentUser?.displayName,
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

            // 2. Filter Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "tab_liked") {
                    LibraryTabIcon(
                        icon = Icons.Default.Favorite,
                        label = "Liked",
                        color = Color(0xFFFF2D55),
                        isSelected = activeTab == "Liked",
                        onClick = { activeTab = "Liked" }
                    )
                }
                item(key = "tab_most_played") {
                    LibraryTabIcon(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        label = "Most Played",
                        color = Color(0xFF4CD964),
                        isSelected = activeTab == "Most Played",
                        onClick = { activeTab = "Most Played" }
                    )
                }
                item(key = "tab_downloads") {
                    LibraryTabIcon(
                        icon = Icons.Default.Download,
                        label = "Downloads",
                        color = Color(0xFF007AFF),
                        isSelected = activeTab == "Downloads",
                        onClick = { activeTab = "Downloads" }
                    )
                }
                item(key = "tab_playlists") {
                    LibraryTabIcon(
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        label = "Playlists",
                        color = Color(0xFFFF9500),
                        isSelected = activeTab == "Playlists",
                        onClick = { activeTab = "Playlists" }
                    )
                }
                item(key = "tab_videos") {
                    LibraryTabIcon(
                        icon = Icons.Default.Videocam,
                        label = "Videos",
                        color = Color(0xFF5856D6),
                        isSelected = activeTab == "Videos",
                        onClick = { activeTab = "Videos" }
                    )
                }
            }

            // 3. Main Content Area
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (activeTab == "Liked") {
                        item(key = "liked_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Liked Songs",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )
                                
                                if (isLoggedIn) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { 
                                            scope.launch {
                                                isSyncing = true
                                                FirebaseSyncManager.syncFromFirebase(context)
                                                delay(500)
                                                isSyncing = false
                                                Toast.makeText(context, "Library Synced", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Library Sync", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (likedSongs.isEmpty()) {
                            item(key = "empty_liked") {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.FavoriteBorder, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("No liked songs yet", color = TextSecondary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Tap the heart to like a song", color = TextSecondary)
                                    }
                                }
                            }
                        }

                        items(
                            items = likedSongs,
                            key = { it.videoId }
                        ) { song ->
                            val songModel = SongModel(
                                id = song.videoId,
                                videoId = song.videoId,
                                title = song.title,
                                artist = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                artistId = song.artistId
                            )

                            var showOptions by remember { mutableStateOf(false) }
                            val isDownloaded = downloadedSongs.any { it.videoId == song.videoId }

                            LibraryListItem(
                                title = song.title,
                                subtitle = song.artist,
                                leadingIcon = Icons.Default.MusicNote,
                                imageUrl = song.thumbnailUrl,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.clickable {
                                    val likedPlaylist = likedSongs.map { s ->
                                        SongModel(
                                            id = s.videoId,
                                            videoId = s.videoId,
                                            title = s.title,
                                            artist = s.artist,
                                            thumbnailUrl = s.thumbnailUrl,
                                            artistId = s.artistId
                                        )
                                    }
                                    MusicPlayer.playSong(
                                        context = context,
                                        song = songModel,
                                        playlist = likedPlaylist,
                                        cookies = cookieManager.getCookies(),
                                        isFromFixedList = true
                                    )
                                },
                                onMoreClick = { showOptions = true }
                            )

                            if (showOptions) {
                                com.example.wavemuzix.uiresource.components.SongOptionsBottomSheet(
                                    song = songModel,
                                    isFavorite = true, // It's in Liked tab
                                    isDownloaded = isDownloaded,
                                    isLiquidGlass = isLiquidGlass,
                                    onDismiss = { showOptions = false },
                                    onFavoriteClick = {
                                        scope.launch { favoriteRepository.toggleFavorite(songModel) }
                                    },
                                    onDownloadClick = {
                                        scope.launch {
                                            if (isDownloaded) {
                                                AppDatabase.getDatabase(context).songDao().deleteDownload(song.videoId)
                                                downloadedSongs.find { it.videoId == song.videoId }?.filePath?.let { path ->
                                                    File(path).delete()
                                                }
                                                Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val result = unifiedRepository.getStreamingUrl(
                                                    videoId = song.videoId,
                                                    cookies = cookieManager.getCookies()
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
                                                            "cookies" to cookieManager.getCookies()
                                                        ))
                                                        .build()
                                                    androidx.work.WorkManager.getInstance(context).enqueue(request)
                                                    Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onPlayNextClick = { MusicPlayer.playNext(songModel) },
                                    onAddToQueueClick = { MusicPlayer.addToQueue(songModel) },
                                    onArtistClick = { /* Navigate to artist if needed, but Library usually stays */ },
                                    onAddToPlaylistClick = { /* Show playlist selection if needed */ },
                                    onShareClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Check out ${song.title} by ${song.artist}: https://www.youtube.com/watch?v=${song.videoId}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    }
                                )
                            }
                        }
                    } else if (activeTab == "Most Played") {
                        item(key = "most_played_header") {
                            Text(
                                "Top 50 Most Played",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(
                            items = mostPlayedSongs,
                            key = { it.videoId }
                        ) { song ->
                            var showOptions by remember { mutableStateOf(false) }
                            val isDownloaded = downloadedSongs.any { it.videoId == song.videoId }
                            val isFavorite = likedSongs.any { it.videoId == song.videoId }
                            val songModel = SongModel(
                                id = song.videoId,
                                videoId = song.videoId,
                                title = song.title,
                                artist = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                artistId = song.artistId
                            )

                            LibraryListItem(
                                title = song.title,
                                subtitle = "${song.artist} • ${song.playCount} plays",
                                leadingIcon = Icons.Default.MusicNote,
                                imageUrl = song.thumbnailUrl,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.clickable {
                                    MusicPlayer.playSong(
                                        context = context,
                                        song = songModel,
                                        cookies = cookieManager.getCookies(),
                                        isFromFixedList = true
                                    )
                                },
                                onMoreClick = { showOptions = true }
                            )

                            if (showOptions) {
                                com.example.wavemuzix.uiresource.components.SongOptionsBottomSheet(
                                    song = songModel,
                                    isFavorite = isFavorite,
                                    isDownloaded = isDownloaded,
                                    isLiquidGlass = isLiquidGlass,
                                    onDismiss = { showOptions = false },
                                    onFavoriteClick = {
                                        scope.launch { favoriteRepository.toggleFavorite(songModel) }
                                    },
                                    onDownloadClick = {
                                        scope.launch {
                                            if (isDownloaded) {
                                                AppDatabase.getDatabase(context).songDao().deleteDownload(song.videoId)
                                                downloadedSongs.find { it.videoId == song.videoId }?.filePath?.let { path ->
                                                    File(path).delete()
                                                }
                                                Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val result = unifiedRepository.getStreamingUrl(
                                                    videoId = song.videoId,
                                                    cookies = cookieManager.getCookies()
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
                                                            "cookies" to cookieManager.getCookies()
                                                        ))
                                                        .build()
                                                    androidx.work.WorkManager.getInstance(context).enqueue(request)
                                                    Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onPlayNextClick = { MusicPlayer.playNext(songModel) },
                                    onAddToQueueClick = { MusicPlayer.addToQueue(songModel) },
                                    onArtistClick = { /* Navigate if needed */ },
                                    onAddToPlaylistClick = { /* Show selection */ },
                                    onShareClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Check out ${song.title} by ${song.artist}: https://www.youtube.com/watch?v=${song.videoId}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    }
                                )
                            }
                        }
                    } else if (activeTab == "Downloads") {
                        item(key = "downloads_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Downloaded Songs",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )
                                if (downloadedSongs.isNotEmpty()) {
                                    TextButton(onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            DownloadManager.exportAllSongsToStorage(context, downloadedSongs)
                                        }
                                    }) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Export All", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        if (downloadedSongs.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No downloads found", color = TextSecondary)
                                }
                            }
                        }

                        items(
                            items = downloadedSongs,
                            key = { it.videoId }
                        ) { song ->
                            var showActionDialog by remember { mutableStateOf(false) }

                            val songModel = SongModel(
                                id = song.videoId,
                                videoId = song.videoId,
                                title = song.title,
                                artist = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                artistId = song.artistId,
                                thumbnailPath = song.thumbnailPath,
                                lyrics = song.lyrics
                            )

                            val displayImageUrl = remember(song.thumbnailPath, song.thumbnailUrl) {
                                if (song.thumbnailPath != null && File(song.thumbnailPath).exists()) {
                                    song.thumbnailPath
                                } else {
                                    song.thumbnailUrl
                                }
                            }

                            LibraryListItem(
                                title = song.title,
                                subtitle = song.artist,
                                leadingIcon = Icons.Default.MusicNote,
                                imageUrl = displayImageUrl,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.clickable {
                                    // When playing from downloads, create a queue of all downloaded songs
                                    val downloadedPlaylist = downloadedSongs.map { s ->
                                        SongModel(
                                            id = s.videoId,
                                            videoId = s.videoId,
                                            title = s.title,
                                            artist = s.artist,
                                            thumbnailUrl = s.thumbnailUrl,
                                            artistId = s.artistId,
                                            thumbnailPath = s.thumbnailPath,
                                            lyrics = s.lyrics
                                        )
                                    }
                                    
                                    MusicPlayer.playOffline(
                                        context = context,
                                        filePath = song.filePath,
                                        song = songModel,
                                        playlist = downloadedPlaylist,
                                        mimeType = song.mimeType,
                                        isFromFixedList = true
                                    )
                                },
                                onMoreClick = { showActionDialog = true }
                            )

                            if (showActionDialog) {
                                DownloadActionDialog(
                                    song = songModel,
                                    filePath = song.filePath,
                                    mimeType = song.mimeType,
                                    isLiquidGlass = isLiquidGlass,
                                    onDismiss = { showActionDialog = false },
                                    onDelete = {
                                        scope.launch {
                                            AppDatabase.getDatabase(context).songDao().deleteDownload(song.videoId)
                                                val file = File(song.filePath)
                                            if (file.exists()) file.delete()
                                            
                                            // Also delete thumbnail if it exists
                                            song.thumbnailPath?.let { path ->
                                                val thumbFile = File(path)
                                                if (thumbFile.exists()) thumbFile.delete()
                                            }
                                            
                                            Toast.makeText(context, "Removed download", Toast.LENGTH_SHORT).show()
                                        }
                                        showActionDialog = false
                                    },
                                    onFavoriteToggle = {
                                        scope.launch {
                                            favoriteRepository.toggleFavorite(songModel)
                                        }
                                    }
                                )
                            }
                        }
                    } else if (activeTab == "Playlists") {
                        item(key = "ai_mixes_header") {
                            Text(
                                "AI Personalized Mixes",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        val aiMixes = listOf(
                            Triple("Romantic Mix", "ai_romantic", "https://images.unsplash.com/photo-1518621736915-f3b1c41bfd00?w=800&q=80"),
                            Triple("Sleep Mix", "ai_sleep", "https://images.unsplash.com/photo-1444465693019-aa0b6392460d?w=800&q=80"),
                            Triple("Energy Mix", "ai_energy", "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=800&q=80"),
                            Triple("Trending Mix", "ai_trending", "https://images.unsplash.com/photo-1493225255756-d9584f8606e9?w=800&q=80")
                        )

                        items(aiMixes) { (title, browseId, thumb) ->
                            LibraryListItem(
                                title = title,
                                subtitle = "AI-Generated Mix",
                                leadingIcon = Icons.Default.AutoAwesome,
                                imageUrl = thumb,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.clickable { onPlaylistClick(browseId) }
                            )
                        }

                        item(key = "recent_activity_header") {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "My Playlists",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (playlists.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No custom playlists created", color = TextSecondary)
                                }
                            }
                        }

                        items(
                            items = playlists,
                            key = { it.id }
                        ) { playlist ->
                            LibraryListItem(
                                title = playlist.name,
                                subtitle = "Custom Playlist",
                                leadingIcon = Icons.Default.LibraryMusic,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.clickable { onPlaylistClick("local_${playlist.id}") }
                            )
                        }
                    } else if (activeTab == "Videos") {
                        item(key = "videos_header") {
                            Text(
                                "Music Videos",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (isVideoLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    StandardLoader()
                                }
                            }
                        } else if (musicVideos.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No videos found", color = TextSecondary)
                                }
                            }
                        }

                        items(musicVideos) { video ->
                            LibraryListItem(
                                title = video.title,
                                subtitle = video.artist,
                                leadingIcon = Icons.Default.Videocam,
                                imageUrl = video.thumbnailUrl,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.clickable {
                                    MusicPlayer.playSong(context, video, cookies = cookieManager.getCookies())
                                }
                            )
                        }
                    }
                }
        }

        // Syncing Loader
        if (isSyncing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepBlack.copy(alpha = 0.85f))
                    .clickable(enabled = false) { }, // Prevent interaction
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(24.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(32.dp)
                ) {
                    StandardLoader(
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Restoring Library",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bringing back your favorites and playlists...",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        if (showLibraryTutorial) {
            TutorialOverlay(
                title = "Your Personal Universe",
                description = "Manage your music here. Sync with YouTube to bring your likes, or explore AI-generated mixes tailored to your mood.",
                onDismiss = {
                    showLibraryTutorial = false
                    settingsManager.hasSeenLibraryTutorial = true
                }
            )
        }
    }
}

@Composable
fun LibraryTabIcon(
    icon: ImageVector, 
    label: String, 
    color: Color, 
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isSelected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .height(40.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LibraryFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isSelected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .height(40.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadActionDialog(
    song: SongModel,
    filePath: String,
    mimeType: String? = null,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favoriteRepository = remember { FavoriteRepository(AppDatabase.getDatabase(context).songDao(), context) }
    var isFavorite by remember { mutableStateOf(false) }

    LaunchedEffect(song.videoId) {
        isFavorite = favoriteRepository.isFavorite(song.videoId)
    }

    @OptIn(ExperimentalGlideComposeApi::class)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        tonalElevation = 0.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassContainer(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    isLiquidGlass = isLiquidGlass
                )
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    GlideImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                    Text(song.artist, color = TextSecondary, fontSize = 14.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            ActionItem(
                icon = Icons.Default.Queue,
                text = "Add to Queue",
                onClick = {
                    MusicPlayer.addToQueue(song)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                text = "Play Next",
                onClick = {
                    MusicPlayer.playNext(song)
                    Toast.makeText(context, "Will play next", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            ActionItem(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                iconColor = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                onClick = {
                    onFavoriteToggle()
                    isFavorite = !isFavorite
                }
            )

            ActionItem(
                icon = Icons.Default.Share,
                text = "Share Song",
                onClick = {
                    try {
                        val file = File(filePath)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Sharing ${song.title}")
                            putExtra(Intent.EXTRA_TEXT, "Check out ${song.title} by ${song.artist}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share via"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Download,
                text = "Export to Storage",
                onClick = {
                    scope.launch {
                        try {
                            DownloadManager.exportSongToStorage(context, song, filePath, mimeType)
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Delete,
                text = "Delete Download",
                iconColor = Color.Red.copy(alpha = 0.8f),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    iconColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LibraryListItem(
    title: String, 
    subtitle: String, 
    leadingIcon: ImageVector,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    onMoreClick: (() -> Unit)? = null
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    val model = remember(imageUrl) {
                        if (imageUrl.startsWith("/") && File(imageUrl).exists()) {
                            File(imageUrl)
                        } else {
                            imageUrl
                        }
                    }
                    GlideImage(
                        model = model,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            GlassIconButton(
                icon = Icons.Default.MoreVert,
                onClick = { onMoreClick?.invoke() },
                isLiquidGlass = isLiquidGlass,
                contentDescription = "More"
            )
        }
    }
}
