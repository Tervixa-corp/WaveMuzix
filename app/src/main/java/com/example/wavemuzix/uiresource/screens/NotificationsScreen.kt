package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.data.DownloadManager
import com.example.wavemuzix.data.DownloadProgress
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.GlassIconButton

import com.example.wavemuzix.data.AppNotification
import com.example.wavemuzix.data.AppNotificationManager
import com.example.wavemuzix.data.NotificationType
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    isLiquidGlass: Boolean = false
) {
    val activeDownloads = DownloadManager.activeDownloads
    val downloadHistory = DownloadManager.downloadHistory
    val appNotifications = AppNotificationManager.notifications
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.example.wavemuzix.data.AppNotificationManager.hasUnread = false
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
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                isLiquidGlass = isLiquidGlass,
                contentDescription = "Back"
            )
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )

            if (activeDownloads.isNotEmpty()) {
                TextButton(onClick = { DownloadManager.cancelAllDownloads(context) }) {
                    Text("Cancel All", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (activeDownloads.isEmpty() && downloadHistory.isEmpty() && appNotifications.isEmpty()) {
            EmptyNotificationsView()
        } else {
            // Notifications Content in Glass Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
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
                    .clip(RoundedCornerShape(24.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (appNotifications.isNotEmpty()) {
                        item {
                            Text("Updates & Info", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        items(appNotifications, key = { it.id }) { notification ->
                            var showExpandedSheet by remember { mutableStateOf(false) }
                            
                            AppNotificationCard(notification) {
                                if (notification.type == NotificationType.UPDATE) {
                                    AppNotificationManager.triggerUpdateDialog = true
                                    onBack()
                                } else {
                                    showExpandedSheet = true
                                }
                            }

                            if (showExpandedSheet) {
                                NotificationDetailSheet(
                                    notification = notification,
                                    onDismiss = { showExpandedSheet = false },
                                    isLiquidGlass = isLiquidGlass
                                )
                            }
                        }
                    }

                    if (activeDownloads.isNotEmpty()) {
                        item {
                            Text("Active Downloads", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        items(activeDownloads, key = { it.videoId }) { download ->
                            DownloadProgressCard(download)
                        }
                    }

                    if (downloadHistory.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("Recently Finished", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        items(downloadHistory, key = { it.videoId + it.timestamp }) { download ->
                            NotificationCard(download)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class)
@Composable
fun NotificationDetailSheet(
    notification: AppNotification,
    onDismiss: () -> Unit,
    isLiquidGlass: Boolean = false
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isLiquidGlass) Color.Transparent else SoftBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        tonalElevation = 0.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (notification.imageUrl != null && notification.imageUrl.isNotEmpty()) {
                com.bumptech.glide.integration.compose.GlideImage(
                    model = notification.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = notification.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = notification.description,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                lineHeight = 24.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AppNotificationCard(notification: AppNotification, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (notification.type == NotificationType.UPDATE) Icons.Default.SystemUpdate else Icons.Default.NotificationsNone,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = notification.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = notification.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun DownloadProgressCard(download: DownloadProgress) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val activeGreen = Color(0xFF4CD964)
                Icon(Icons.Default.Download, null, tint = activeGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(download.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text("${download.progress}%", color = activeGreen, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = Color(0xFF4CD964),
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun NotificationCard(download: DownloadProgress) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CD964).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DownloadDone, null, tint = Color(0xFF4CD964), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Download finished",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = download.title,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun EmptyNotificationsView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(60.dp), tint = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No new notifications", color = TextSecondary)
    }
}
