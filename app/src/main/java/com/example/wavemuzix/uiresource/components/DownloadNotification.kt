package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.data.DownloadManager
import com.example.wavemuzix.ui.theme.SoftBlack
import kotlinx.coroutines.delay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DownloadNotificationOverlay(onClick: () -> Unit) {
    val latestNotification by DownloadManager.latestNotification
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    AnimatedVisibility(
        visible = latestNotification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
    ) {
        latestNotification?.let { notification ->
            LaunchedEffect(notification) {
                offsetX.snapTo(0f)
            }

            LaunchedEffect(notification.isFinished) {
                if (notification.isFinished) {
                    delay(5000)
                    DownloadManager.clearLatestNotification()
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(notification.videoId) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (abs(offsetX.value) > size.width / 3) {
                                        offsetX.animateTo(
                                            targetValue = if (offsetX.value > 0) size.width.toFloat() else -size.width.toFloat(),
                                            animationSpec = tween(300)
                                        )
                                        DownloadManager.ignoreNotification(notification.videoId)
                                    } else {
                                        offsetX.animateTo(0f, tween(300))
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                }
                            }
                        )
                    }
                    .clickable { 
                        onClick()
                        DownloadManager.clearLatestNotification()
                    },
                color = SoftBlack,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (notification.isFinished) Color(0xFF4CD964).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (notification.isFinished) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = null,
                            tint = if (notification.isFinished) Color(0xFF4CD964) else MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (notification.isFinished) "Download finished" else "Downloading ${notification.title}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        if (!notification.isFinished) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { notification.progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        } else {
                            Text(
                                text = notification.title,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
