package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPlaying: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    waveAmplitude: Dp = 6.dp,
    waveFrequency: Float = 0.045f
) {
    // Animate amplitude factor to settle to 0 when not playing
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "amplitudeFactor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))
                    horizontalDrag(down.id) { change ->
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                }
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val centerY = height / 2
        val activeWidth = width * value

        Canvas(modifier = Modifier.fillMaxSize()) {
            val amplitude = waveAmplitude.toPx() * amplitudeFactor
            
            // Draw Inactive Track (Straight Line) - Only if there's remaining width
            if (activeWidth < width) {
                drawLine(
                    color = colors.inactiveTrackColor,
                    start = Offset(activeWidth, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Draw Active Wavy Track - Only if value > 0 and using a threshold to avoid "dots"
            if (activeWidth > 1f) {
                val path = Path().apply {
                    moveTo(0f, centerY)
                    for (x in 1..activeWidth.toInt()) {
                        val relativeX = x.toFloat()
                        // Taper at start and end for a clean visual and smooth join
                        val taperStart = (relativeX / 20f).coerceIn(0f, 1f)
                        val taperEnd = ((activeWidth - relativeX) / 20f).coerceIn(0f, 1f)
                        val y = centerY + sin(relativeX * waveFrequency + phase) * amplitude * taperStart * taperEnd
                        lineTo(relativeX, y)
                    }
                    lineTo(activeWidth, centerY)
                }
                drawPath(
                    path = path,
                    color = colors.activeTrackColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Draw Thumb (Fixed Y - straight line movement)
            drawCircle(
                color = colors.thumbColor,
                radius = 10.dp.toPx(),
                center = Offset(activeWidth, centerY)
            )
        }
    }
}
