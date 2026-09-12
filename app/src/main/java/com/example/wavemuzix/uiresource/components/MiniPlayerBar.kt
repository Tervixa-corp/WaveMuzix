package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.data.LocalPerformanceConfig
import com.example.wavemuzix.data.MusicPlayer
import kotlin.math.abs

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayerBar(
    isLiquidGlass: Boolean,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onClick: () -> Unit
) {
    val currentSong = MusicPlayer.currentSong ?: return
    val isPlaying = MusicPlayer.isPlaying
    val isLoading = MusicPlayer.isLoading
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val performance = LocalPerformanceConfig.current

    val accentColor = MusicPlayer.accentColors.firstOrNull() ?: Color(0xFF1A1A1A)
    val progress = if (MusicPlayer.duration > 0) MusicPlayer.currentPosition.toFloat() / MusicPlayer.duration else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "progress"
    )
    val animatedBgColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(800, easing = EaseInOutQuart),
        label = "bgColor"
    )

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.7f,
            stiffness = if (performance.isLowSpec) performance.springStiffness else 400f
        ),
        label = "interactionScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                compositingStrategy = if (performance.useCompositingStrategyOffscreen) 
                    CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isLiquidGlass) {
                    Modifier.glassContainer(
                        shape = RoundedCornerShape(24.dp),
                        alphaTop = 0.1f,
                        alphaBottom = 0.05f,
                        borderAlphaTop = 0.25f,
                        borderAlphaBottom = 0.15f,
                        backgroundColor = animatedBgColor
                    )
                } else {
                    Modifier.background(animatedBgColor.copy(alpha = 1.0f))
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onClick() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (abs(dragAmount.x) > abs(dragAmount.y) && abs(dragAmount.x) > 30) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (dragAmount.x > 0) {
                                MusicPlayer.playPreviousInQueue(context)
                            } else {
                                MusicPlayer.playNextInQueue(context)
                            }
                            change.consume()
                        }
                    }
                )
            }
    ) {
        // Content Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                shadowElevation = 8.dp,
                color = Color.Transparent
            ) {
                Crossfade(
                    targetState = currentSong.thumbnailUrl,
                    animationSpec = tween(800, easing = EaseInOutQuart),
                    label = "MiniPlayerArtCrossfade"
                ) { thumbnailUrl ->
                    GlideImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = currentSong,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(600, delayMillis = 50)) +
                                slideInVertically(animationSpec = tween(600)) { it / 3 })
                            .togetherWith(fadeOut(animationSpec = tween(400)) +
                                    slideOutVertically(animationSpec = tween(400)) { -it / 3 })
                    },
                    label = "SongInfoTransition"
                ) { targetSong ->
                    Column {
                        Text(
                            text = targetSong.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = targetSong.artist,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Play/Pause Action
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    StandardLoader(
                        size = 24.dp
                    )
                } else {
                    GlassIconButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            MusicPlayer.togglePlayPause(context)
                        },
                        isLiquidGlass = isLiquidGlass,
                        containerSize = 44.dp,
                        iconSize = 28.dp
                    )
                }
            }
        }

        // Progress Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = animatedProgress
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.6f),
                                Color.White.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
        }
    }
}