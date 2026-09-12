package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GlobalRotationDurationMillis = 4666
private const val MorphIntervalMillis = 650L

/**
 * Enhanced Material 3 Expressive Loading Indicator.
 * Both the indicator and its background container are expressive, morphing between shapes.
 * They rotate in opposite directions and maintain distinct shapes at any given time.
 * The core "bouncing" physics and shape-shifting logic from Material 3 are preserved.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    color: Color = Color.White.copy(alpha = 1f)
) {
    val indicatorColor = color
    val containerColor = Color.White.copy(alpha = 0.24f)

    val indicatorPolygons = remember {
        LoadingIndicatorDefaults.IndeterminateIndicatorPolygons.filter { it != MaterialShapes.Pentagon }
    }
    val containerPolygons = remember(indicatorPolygons) {
        // Shift the sequence by 3 to ensure they never have the same shape simultaneously
        indicatorPolygons.drop(3) + indicatorPolygons.take(3)
    }

    val morphProgress = remember { Animatable(0f) }
    val globalRotation = remember { Animatable(0f) }
    var currentMorphIndex by remember { mutableIntStateOf(0) }
    var morphRotationTargetAngle by remember { mutableFloatStateOf(90f) }

    LaunchedEffect(indicatorPolygons) {
        launch {
            // Preserving the exact physics: dampingRatio 0.6, stiffness 200
            val morphAnimationSpec = spring<Float>(
                dampingRatio = 0.6f,
                stiffness = 200f,
                visibilityThreshold = 0.1f
            )
            while (true) {
                val deferred = async {
                    val result = morphProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = morphAnimationSpec
                    )
                    if (result.endReason == AnimationEndReason.Finished) {
                        currentMorphIndex = (currentMorphIndex + 1) % indicatorPolygons.size
                        morphProgress.snapTo(0f)
                        morphRotationTargetAngle = (morphRotationTargetAngle + 90f) % 360f
                    }
                }
                delay(MorphIntervalMillis)
                deferred.await()
            }
        }

        globalRotation.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(GlobalRotationDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    val indicatorMorphs = remember(indicatorPolygons) {
        List(indicatorPolygons.size) { i ->
            Morph(indicatorPolygons[i], indicatorPolygons[(i + 1) % indicatorPolygons.size])
        }
    }
    val containerMorphs = remember(containerPolygons) {
        List(containerPolygons.size) { i ->
            Morph(containerPolygons[i], containerPolygons[(i + 1) % containerPolygons.size])
        }
    }

    // Prepare paths and matrices for drawing
    val androidPath1 = remember { android.graphics.Path() }
    val androidPath2 = remember { android.graphics.Path() }
    val matrix = remember { Matrix() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size).aspectRatio(1f)) {
            val rawProgress = morphProgress.value
            val coercedProgress = rawProgress.coerceIn(0f, 1f)
            val rotation = rawProgress * 90 + morphRotationTargetAngle + globalRotation.value

            // 1. Draw Expressive Container (Rotating counter-clockwise)
            rotate(-rotation) {
                val morph = containerMorphs[currentMorphIndex]
                morph.toPath(coercedProgress, androidPath1)
                // scaleFactor 0.45f fills most of the container area
                drawProcessedPath(androidPath1.asComposePath(), containerColor, 0.45f, matrix)
            }

            // 2. Draw Expressive Indicator (Rotating clockwise)
            rotate(rotation) {
                val morph = indicatorMorphs[currentMorphIndex]
                morph.toPath(coercedProgress, androidPath2)
                // scaleFactor 0.3f increases the indicator size slightly (prev was 0.26f)
                drawProcessedPath(androidPath2.asComposePath(), indicatorColor, 0.285f, matrix)
            }
        }
    }
}

/**
 * Process, scale, center and draw the morphed path.
 */
private fun DrawScope.drawProcessedPath(
    path: Path,
    color: Color,
    scaleFactor: Float,
    matrix: Matrix
) {
    matrix.reset()
    // Polygons are normalized (range roughly -1 to 1). Scale them to fit DrawScope size.
    val scale = size.minDimension * scaleFactor
    matrix.scale(scale, scale)
    path.transform(matrix)

    // Center the path in the Canvas
    val bounds = path.getBounds()
    path.translate(size.center - bounds.center)

    drawPath(path = path, color = color, style = Fill)
}
