package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

import com.example.wavemuzix.data.LocalPerformanceConfig

fun Modifier.shimmerEffect(): Modifier = composed {
    val performance = LocalPerformanceConfig.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    if (performance.isLowSpec) {
        return@composed this.background(Color.DarkGray.copy(alpha = 0.4f))
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "shimmerOffsetX"
    )

    this
        .graphicsLayer {
            // Ensure GPU acceleration
        }
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.DarkGray.copy(alpha = 0.6f),
                    Color.Gray.copy(alpha = 0.2f),
                    Color.DarkGray.copy(alpha = 0.6f),
                ),
                start = Offset(startOffsetX * size.width.toFloat(), 0f),
                end = Offset((startOffsetX + 1) * size.width.toFloat(), size.height.toFloat())
            )
        )
        .onGloballyPositioned {
            size = it.size
        }
}
