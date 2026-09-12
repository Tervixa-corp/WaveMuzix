package com.example.wavemuzix.uiresource.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.glassContainer

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun SongOptionsBottomSheet(
    song: Song,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onShareClick: () -> Unit
) {
    var showPlaylistDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isLiquidGlass) Color.Transparent else SoftBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()

                .glassContainer(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    isLiquidGlass = isLiquidGlass,
                    blurRadius = 10.dp
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Song Info Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlideImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        song.title, 
                        color = Color.White, 
                        style = MaterialTheme.typography.titleMedium, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist, 
                        color = TextSecondary, 
                        style = MaterialTheme.typography.bodyMedium, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            OptionItem(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (isFavorite) "Liked" else "Like",
                iconTint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
            ) { onFavoriteClick(); onDismiss() }

            OptionItem(
                Icons.Default.Download,
                if (isDownloaded) "Downloaded" else "Download",
                iconTint = if (isDownloaded) MaterialTheme.colorScheme.primary else Color.White
            ) { onDownloadClick(); onDismiss() }

            OptionItem(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") { onPlayNextClick(); onDismiss() }
            
            OptionItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { onAddToQueueClick(); onDismiss() }

            if (song.artistId != null) {
                OptionItem(Icons.Default.Person, "Go to artist") { onArtistClick(song.artistId); onDismiss() }
            }
            
            OptionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { onAddToPlaylistClick() }

            OptionItem(Icons.Default.Share, "Share") { onShareClick() }
        }
    }
}

@Composable
private fun OptionItem(icon: ImageVector, label: String, iconTint: Color = Color.White, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(24.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
