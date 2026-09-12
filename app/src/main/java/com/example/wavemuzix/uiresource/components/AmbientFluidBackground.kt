package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.wavemuzix.data.LocalPerformanceConfig
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientFluidBackground(
    modifier: Modifier = Modifier,
    accentColors: List<Color>,
    amplitudeProvider: () -> Float,
    bassAmplitudeProvider: () -> Float = { 0f },
    trebleAmplitudeProvider: () -> Float = { 0f },
    playbackSpeedProvider: () -> Float = { 1f },
    isPlayingProvider: () -> Boolean
) {
    val performance = LocalPerformanceConfig.current
    
    // Section 2: Real-time phase accumulator for beat-driven motion acceleration
    val frameTime = remember { mutableLongStateOf(0L) }
    val phaseAccumulator = remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        var lastTime = 0L
        snapshotFlow { isPlayingProvider() }.collectLatest { isPlaying ->
            if (isPlaying) {
                lastTime = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        val delta = (now - lastTime) / 1_000_000_000f
                        lastTime = now
                        
                        val amp = if (performance.isLowSpec) 0f else amplitudeProvider()
                        // Motion acceleration: base translation speed scaled (3.0x) + audio energy boost
                        val baseSpeed = if (performance.isLowSpec) 1.5f else 3.0f 
                        val audioAcceleration = amp * 5.5f
                        phaseAccumulator.floatValue += delta * (baseSpeed + audioAcceleration) * playbackSpeedProvider()
                        
                        frameTime.longValue = now
                    }
                }
            }
        }
    }

    // Section 1: smooth linear interpolation transitions when switching colors between track changes
    val animatedColors = (0..5).map { index ->
        val targetColor = accentColors.getOrElse(index) { 
            accentColors.firstOrNull() ?: Color.Black 
        }
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(2500, easing = LinearOutSlowInEasing),
            label = "Color$index"
        ).value
    }

    // Section 2: Multi-layered directional wave motion configs
    val blobConfigs = remember(performance.isLowSpec) {
        if (performance.isLowSpec) {
            listOf(
                BlobConfig(0.5f, 0.5f, 0.85f, 2.5f, 1.2f, 0.3f, "bass"),
                BlobConfig(0.3f, 0.7f, 0.75f, 2.0f, 1.0f, 0.25f, "mid")
            )
        } else {
            listOf(
                BlobConfig(0.2f, 0.3f, 0.75f, 3.8f, 2.0f, 0.45f, "bass"),
                BlobConfig(0.8f, 0.2f, 0.85f, 3.4f, 1.7f, 0.4f, "bass"),
                BlobConfig(0.5f, 0.5f, 0.65f, 2.8f, 2.2f, 0.5f, "mid"),
                BlobConfig(0.3f, 0.8f, 0.75f, 4.2f, 1.8f, 0.45f, "treble"),
                BlobConfig(0.7f, 0.9f, 0.7f, 3.8f, 2.0f, 0.4f, "treble"),
                BlobConfig(0.1f, 0.5f, 0.55f, 3.2f, 1.5f, 0.35f, "mid")
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { 
                // Section 3: Optimized rendering strategy to prevent UI recompositions
                compositingStrategy = if (performance.useCompositingStrategyOffscreen) 
                    CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .blur(60.dp * performance.blurRadiusMultiplier) // Section 3: High-density blur for fluid blending
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Deep base for contrast
            drawRect(Color.Black)

            val currentFrameTime = frameTime.longValue
            if (currentFrameTime == 0L) return@Canvas

            val basePhase = phaseAccumulator.floatValue
            val globalRotation = basePhase * 0.15f 
            
            blobConfigs.forEachIndexed { index, config ->
                val color = animatedColors.getOrElse(index) { animatedColors.first() }
                
                val activeAmplitude = if (performance.isLowSpec) 0f else when(config.type) {
                    "bass" -> bassAmplitudeProvider()
                    "treble" -> trebleAmplitudeProvider()
                    else -> amplitudeProvider()
                }

                // Section 2: Audio-driven scaling and multi-layered swirl
                val musicBoost = 1.0f + (activeAmplitude * 0.7f)
                val rotationOffset = globalRotation + (index * PI.toFloat() / 2.5f)
                
                drawFluidBlob(
                    center = Offset(
                        width * config.x + (90.dp.toPx() * cos(rotationOffset) * musicBoost),
                        height * config.y + (90.dp.toPx() * sin(rotationOffset) * musicBoost)
                    ),
                    radius = width * config.radiusSize * musicBoost,
                    phase = basePhase + (index * 1.2f),
                    amplitude = activeAmplitude,
                    color = color.copy(alpha = config.alpha),
                    speed = config.speed,
                    deformationFactor = config.deformation,
                    isLowSpec = performance.isLowSpec
                )
            }
            
            // Smooth linear gradient overlay for vignette depth
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                    center = Offset(width / 2, height / 2),
                    radius = width * 1.4f
                )
            )
        }
    }
}

private data class BlobConfig(
    val x: Float,
    val y: Float,
    val radiusSize: Float,
    val speed: Float,
    val deformation: Float,
    val alpha: Float,
    val type: String
)

private fun DrawScope.drawFluidBlob(
    center: Offset,
    radius: Float,
    phase: Float,
    amplitude: Float,
    color: Color,
    speed: Float,
    deformationFactor: Float = 1.0f,
    isLowSpec: Boolean = false
) {
    if (isLowSpec) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
        return
    }

    val reactiveAmp = (amplitude * 1.8f).coerceIn(0f, 2.5f)
    
    // Section 2: Overlapping trigonometric wave shifts
    val baseDeformation = (sin(phase * speed * 0.8f) * radius * 0.12f)
    val musicDeformation = (reactiveAmp * radius * 0.9f * deformationFactor)
    
    val blobRadius = (radius + baseDeformation + musicDeformation).coerceIn(10f, size.maxDimension * 1.6f)
    
    // Section 2: Pulse and jitter driven by low/high frequency audio energy
    val jitterMagnitude = reactiveAmp * 45.dp.toPx()
    val dynamicCenter = Offset(
        (center.x + (jitterMagnitude * cos(phase * 15f))),
        (center.y + (jitterMagnitude * sin(phase * 13f)))
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = dynamicCenter,
            radius = blobRadius
        ),
        center = dynamicCenter,
        radius = blobRadius
    )
}
