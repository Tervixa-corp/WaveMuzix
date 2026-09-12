package com.example.wavemuzix.uiresource.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.data.CookieManager
import com.example.wavemuzix.data.DownloadManager
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.GlassIconButton
import com.example.wavemuzix.uiresource.components.glassContainer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun QueueScreen(
    isLiquidGlass: Boolean,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (com.example.wavemuzix.model.Song) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val queue = MusicPlayer.queue
    val currentIndex = MusicPlayer.currentIndex
    val currentSong = MusicPlayer.currentSong
    val listState = rememberLazyListState()

    // Scroll to current song on entry
    LaunchedEffect(Unit) {
        if (currentIndex != -1) {
            listState.scrollToItem(currentIndex)
        }
    }

    BackHandler(onBack = onBack)

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-scroll logic during drag
    LaunchedEffect(draggingIndex) {
        if (draggingIndex == null) return@LaunchedEffect
        while (true) {
            val layoutInfo = listState.layoutInfo
            val draggingItem = layoutInfo.visibleItemsInfo.find { it.index == draggingIndex }
            if (draggingItem != null) {
                val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                val top = draggingItem.offset + dragOffset
                val bottom = top + draggingItem.size
                
                val threshold = 120f
                val maxScrollSpeed = 30f
                
                var scrollAmount = 0f
                if (top < threshold) {
                    val proximity = ((threshold - top) / threshold).coerceIn(0f, 1f)
                    scrollAmount = -(proximity * maxScrollSpeed)
                } else if (bottom > viewportHeight - threshold) {
                    val proximity = ((bottom - (viewportHeight - threshold)) / threshold).coerceIn(0f, 1f)
                    scrollAmount = proximity * maxScrollSpeed
                }
                
                if (scrollAmount != 0f) {
                    val actualScroll = listState.scrollBy(scrollAmount)
                    dragOffset += actualScroll
                }

                // Update targetIndex based on current position after potential scroll
                val dragCenterY = draggingItem.offset + dragOffset + draggingItem.size / 2
                val newTarget = layoutInfo.visibleItemsInfo
                    .minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        kotlin.math.abs(dragCenterY - itemCenter)
                    }?.index
                if (newTarget != null && newTarget != targetIndex) {
                    targetIndex = newTarget
                }
            }
            delay(10) // Faster polling for smoother scroll/reorder
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLiquidGlass && currentSong != null) {
            GlideImage(
                model = MusicPlayer.highResThumbnailUrl ?: currentSong.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(25.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Playing Queue", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        GlassIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onBack,
                            isLiquidGlass = isLiquidGlass,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Curating your vibe...",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { item ->
                                            offset.y.toInt() in item.offset..(item.offset + item.size)
                                        }
                                        ?.let { item ->
                                            draggingIndex = item.index
                                            targetIndex = item.index
                                        }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    
                                    draggingIndex?.let { startIdx ->
                                        val currentItem = listState.layoutInfo.visibleItemsInfo.find { it.index == startIdx }
                                        if (currentItem != null) {
                                            val dragY = currentItem.offset + dragOffset
                                            val newTarget = listState.layoutInfo.visibleItemsInfo
                                                .minByOrNull { item ->
                                                    val itemCenter = item.offset + item.size / 2
                                                    kotlin.math.abs(dragY + currentItem.size / 2 - itemCenter)
                                                }?.index
                                            if (newTarget != null && newTarget != targetIndex) {
                                                targetIndex = newTarget
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (draggingIndex != null && targetIndex != null && draggingIndex != targetIndex) {
                                        MusicPlayer.moveItem(draggingIndex!!, targetIndex!!)
                                    }
                                    draggingIndex = null
                                    targetIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    targetIndex = null
                                    dragOffset = 0f
                                }
                            )
                        },
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item(key = "now_playing_card") {
                        if (currentSong != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .glassContainer(shape = RoundedCornerShape(24.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.size(90.dp),
                                        color = Color.Black.copy(alpha = 0.2f)
                                    ) {
                                        GlideImage(
                                            model = MusicPlayer.highResThumbnailUrl ?: currentSong.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Now Playing",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = currentSong.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = currentSong.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    itemsIndexed(
                                items = queue,
                                key = { _, song -> song.videoId }
                            ) { index, song ->
                                val isCurrent = index == currentIndex
                                val isDragging = index == draggingIndex
                                
                                // Physics: OriginOS 6 style bouncy "fall back" effect
                                val settleSpec = spring<Float>(
                                    dampingRatio = 0.45f, // More bouncy for the "scatter and come back" feel
                                    stiffness = 150f
                                )
                                val dragSpec = spring<Float>(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 600f
                                )

                                val itemTranslationY by animateFloatAsState(
                                    targetValue = if (draggingIndex != null && !isDragging) {
                                        val start = draggingIndex!!
                                        val end = targetIndex!!
                                        if (start < end) {
                                            if (index in (start + 1)..end) -72f else 0f
                                        } else {
                                            if (index in end until start) 72f else 0f
                                        }
                                    } else 0f,
                                    animationSpec = if (draggingIndex == null) settleSpec else dragSpec,
                                    label = "translationY"
                                )

                                // Scattered Effect: X offset and rotation
                                val itemTranslationX by animateFloatAsState(
                                    targetValue = if (draggingIndex != null && !isDragging) {
                                        val dist = kotlin.math.abs(index - (targetIndex ?: index))
                                        if (dist <= 1) (if (index % 2 == 0) 12f else -12f) else 0f
                                    } else 0f,
                                    animationSpec = settleSpec,
                                    label = "translationX"
                                )

                                val itemRotation by animateFloatAsState(
                                    targetValue = if (draggingIndex != null && !isDragging) {
                                        val dist = kotlin.math.abs(index - (targetIndex ?: index))
                                        if (dist <= 1) (if (index % 2 == 0) 2f else -2f) else 0f
                                    } else 0f,
                                    animationSpec = settleSpec,
                                    label = "rotation"
                                )

                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.08f else if (draggingIndex != null) 0.97f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = if (draggingIndex == null) 0.5f else Spring.DampingRatioNoBouncy,
                                        stiffness = if (draggingIndex == null) 200f else 500f
                                    ),
                                    label = "scale"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 2f else 1f)
                                        .graphicsLayer {
                                            translationY = if (isDragging) dragOffset else itemTranslationY
                                            translationX = itemTranslationX
                                            rotationZ = itemRotation
                                            scaleX = scale
                                            scaleY = scale
                                            shadowElevation = if (isDragging) 16f else 0f
                                        }
                                        .background(if (isCurrent) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable {
                                            MusicPlayer.playAtIndex(context, index)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Artwork with 1:1 ratio and larger size
                                    Box(
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxSize(),
                                            color = Color.Black.copy(alpha = 0.2f)
                                        ) {
                                            GlideImage(
                                                model = song.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                        )
                                        Text(
                                            text = song.artist,
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                        )
                                    }

                                    var showMenu by remember { mutableStateOf(false) }

                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                        
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                            modifier = Modifier
                                                .background(if (isLiquidGlass) Color.Black.copy(alpha = 0.85f) else SoftBlack)
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.15f),
                                                            Color.White.copy(alpha = 0.05f)
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Like", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.FavoriteBorder, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                onClick = {
                                                    MusicPlayer.toggleFavorite(context, song)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Download", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Download, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                onClick = {
                                                    DownloadManager.downloadSong(
                                                        context = context,
                                                        song = song,
                                                        repository = MusicPlayer.unifiedRepository,
                                                        cookies = CookieManager(context).getCookies()
                                                    )
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Play Next", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.SkipNext, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                onClick = {
                                                    MusicPlayer.playNext(song)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove from Queue", color = Color.Red.copy(alpha = 0.9f)) },
                                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.8f)) },
                                                onClick = {
                                                    MusicPlayer.removeFromQueue(index)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Go to Artist", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                onClick = {
                                                    onArtistClick(song.artistId ?: "")
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Add to Playlist", color = Color.White) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                onClick = {
                                                    onPlaylistClick(song)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Share", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Share, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                onClick = {
                                                    val sendIntent = android.content.Intent().apply {
                                                        action = android.content.Intent.ACTION_SEND
                                                        putExtra(android.content.Intent.EXTRA_TEXT, "Listen to ${song.title} by ${song.artist} on WaveMuzix: https://www.youtube.com/watch?v=${song.videoId}")
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                                                    showMenu = false
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
}
