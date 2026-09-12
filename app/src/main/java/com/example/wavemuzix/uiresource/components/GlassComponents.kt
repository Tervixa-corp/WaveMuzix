package com.example.wavemuzix.uiresource.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wavemuzix.data.LocalPerformanceConfig
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.exposureAdjustment
import com.kashif_e.backdrop.effects.lens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalPreferHaze = staticCompositionLocalOf { false }

@Composable
fun Modifier.glassContainer(
    shape: Shape,
    isLiquidGlass: Boolean = true,
    borderWidth: Dp = 1.dp,
    alphaTop: Float = 0.05f,
    alphaBottom: Float = 0.02f,
    borderAlphaTop: Float = 0.1f,
    borderAlphaBottom: Float = 0.05f,
    backgroundColor: Color = Color.Black,
    clip: Boolean = true,
    blurRadius: Dp? = null
): Modifier {
    val backdrop = LocalBackdrop.current
    val hazeState = LocalHazeState.current
    val preferHaze = LocalPreferHaze.current
    val performance = LocalPerformanceConfig.current

    val finalIsLiquidGlass = isLiquidGlass && performance.isLiquidGlassEnabled
    val blurMultiplier = performance.blurRadiusMultiplier

    // Safety check: Only apply refraction once the node is attached and placed.
    // This prevents IllegalStateException during AnimatedVisibility transitions.
    var isPlaced by remember { mutableStateOf(false) }
    val refractionAmount by animateFloatAsState(
        targetValue = if (isPlaced && !performance.isLowSpec) 40f else 0f,
        animationSpec = tween(500),
        label = "refraction"
    )

    // AGSL & RenderEffect shaders require Android 12+ (API 31+)
    val isApiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isForced = blurRadius != null

    val useHaze = isApiSupported && hazeState != null && 
                 (preferHaze || backdrop == null) && 
                 (isForced || !performance.isLowSpec)
                 
    val useLiquid = isApiSupported && backdrop != null && !useHaze && 
                   (isForced || (finalIsLiquidGlass && blurMultiplier > 0f))

    val density = LocalDensity.current
    val blurPx = remember(blurRadius, density) {
        blurRadius?.let { with(density) { it.toPx() } }
    }

    val luminance = try {
        (0.2126f * backgroundColor.red) +
                (0.7152f * backgroundColor.green) +
                (0.0722f * backgroundColor.blue)
    } catch (_: Exception) {
        0.1f
    }

    val dynamicExposure = (0.05f + (luminance * 0.3f)).coerceIn(0.05f, 0.8f)
    val dynamicBlur = ((13f + (luminance * 10f)) * blurMultiplier).coerceIn(0f, 45f)

    // Build the dynamic effect safely
    val effectModifier = Modifier.then(
        runCatching {
            when {
                useHaze && hazeState != null -> {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = backgroundColor,
                            blurRadius = blurRadius ?: (12.dp * blurMultiplier),
                            noiseFactor = 0f,
                            tint = HazeTint(Color.Black.copy(alpha = 0.1f))
                        )
                    )
                }
                useLiquid && backdrop != null && (blurMultiplier > 0f || blurRadius != null) -> {
                    // Isolating the layer and deferred activation prevents refraction displacement crashes
                    Modifier
                        .onPlaced { isPlaced = true }
                        .graphicsLayer {
                            this.clip = clip
                            this.shape = shape
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                // Convert Dp to float if blurRadius is provided, otherwise use dynamicBlur
                                val blurVal = blurPx ?: dynamicBlur
                                blur(radius = blurVal)
                                lens(
                                    refractionHeight = 100f,
                                    refractionAmount = refractionAmount,
                                    depthEffect = true,
                                    chromaticAberration = false
                                )
                                exposureAdjustment(ev = dynamicExposure)
                            }
                        )
                }
                else -> Modifier
            }
        }.getOrDefault(Modifier)
    )

    val finalModifier = if (clip) this.clip(shape) else this

    return finalModifier
        .then(effectModifier)
        // Reduced dark tint layer to keep the nav bar cleaner
        .background(
            Color.Black.copy(alpha = (0.05f + (luminance * 0.05f)).coerceIn(0.05f, 0.2f)),
            shape = shape
        )
        .background(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (useHaze || useLiquid) alphaTop * 0.5f else alphaTop + 0.05f),
                    Color.White.copy(alpha = if (useHaze || useLiquid) alphaBottom * 0.5f else alphaBottom + 0.02f)
                )
            ),
            shape = shape
        )
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = borderAlphaTop),
                    Color.Transparent,
                    Color.White.copy(alpha = borderAlphaBottom)
                )
            ),
            shape = shape
        )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = true,
    contentDescription: String? = null,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White,
    containerSize: Dp = 40.dp,
    backgroundColor: Color = Color.Black,
    shape: Shape = CircleShape
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(containerSize)
            .then(
                if (isLiquidGlass) {
                    Modifier.glassContainer(
                        shape = shape,
                        backgroundColor = backgroundColor
                    )
                } else {
                    Modifier
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.1f))
                }
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}