package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * A smooth OriginOS 6 inspired bubble orbit loader.
 * Featuring a single clean bubble orbiting along the inner edge of a subtle light white circle.
 */
@Composable
fun ExpressiveLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OriginOSLoader")

    // Master linear progress drives the timing (1.6s for a fluid cycle)
    val linearProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "linearProgress"
    )

    // A highly fluid, premium easing curve for natural-feeling bubble acceleration
    val premiumEasing = CubicBezierEasing(0.6f, 0f, 0.4f, 1f)

    Canvas(
        modifier = modifier.size(size)
    ) {
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val strokeWidth = 5.dp.toPx()
        val outerRadius = (size.toPx() - strokeWidth) / 2f
        
        // 1. Draw the subtle outer outline circle in a light white
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // 2. Draw the single dynamic bubble orbiting the circle
        val bubbleRadius = size.toPx() * 0.085f
        
        // The bubble orbits exactly along the inner edge of the main circle
        val orbitRadius = outerRadius - bubbleRadius - (strokeWidth / 2f)

        // Apply the premium easing curve
        val easedProgress = premiumEasing.transform(linearProgress.value)
        
        // Calculate orbit position (starting from the top / -90 degrees)
        val angleRad = (easedProgress * 2 * PI.toFloat()) - (PI.toFloat() / 2f)
        
        val x = center.x + orbitRadius * cos(angleRad)
        val y = center.y + orbitRadius * sin(angleRad)

        drawCircle(
            color = color,
            radius = bubbleRadius,
            center = Offset(x, y)
        )
    }
}
