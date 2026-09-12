package com.example.wavemuzix.data

import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.CompositingStrategy

@Immutable
data class PerformanceConfig(
    val isLowSpec: Boolean = false,
    val useGraphicsLayerForSize: Boolean = true,
    val useCompositingStrategyOffscreen: Boolean = true,
    val blurRadiusMultiplier: Float = 1.0f,
    val springDampingRatio: Float = 0.4f,
    val springStiffness: Float = 600f,
    val isLiquidGlassEnabled: Boolean = true,
    val surfaceAlpha: Float = 0.1f, // New field for glass opacity
    val maxVisualizerComplexity: Int = 100 // Scale of 0-100
) {
    companion object {
        val VeryLow = PerformanceConfig(
            isLowSpec = true,
            useGraphicsLayerForSize = true,
            useCompositingStrategyOffscreen = false,
            blurRadiusMultiplier = 1.0f,
            springDampingRatio = Spring.DampingRatioNoBouncy,
            springStiffness = Spring.StiffnessVeryLow,
            isLiquidGlassEnabled = false,
            surfaceAlpha = 0.95f,
            maxVisualizerComplexity = 15
        )

        val Low = PerformanceConfig(
            isLowSpec = true,
            useGraphicsLayerForSize = true,
            useCompositingStrategyOffscreen = false,
            blurRadiusMultiplier = 1.0f,
            springDampingRatio = Spring.DampingRatioNoBouncy,
            springStiffness = Spring.StiffnessLow,
            isLiquidGlassEnabled = false,
            surfaceAlpha = 0.85f,
            maxVisualizerComplexity = 30
        )

        val Mid = PerformanceConfig(
            isLowSpec = false,
            useGraphicsLayerForSize = true,
            useCompositingStrategyOffscreen = true,
            blurRadiusMultiplier = 1.0f,
            springDampingRatio = 0.7f,
            springStiffness = 600f,
            isLiquidGlassEnabled = true,
            surfaceAlpha = 0.6f,
            maxVisualizerComplexity = 50
        )
        
        val Balanced = PerformanceConfig(
            isLowSpec = false,
            useGraphicsLayerForSize = true,
            useCompositingStrategyOffscreen = true,
            blurRadiusMultiplier = 0.0f, // Keep blur off for speed as previously requested
            springDampingRatio = 0.6f,
            springStiffness = 700f,
            isLiquidGlassEnabled = true,
            surfaceAlpha = 0.4f,
            maxVisualizerComplexity = 70
        )

        val High = PerformanceConfig(
            isLowSpec = false,
            useGraphicsLayerForSize = false,
            useCompositingStrategyOffscreen = true,
            blurRadiusMultiplier = 1.0f,
            springDampingRatio = 0.5f,
            springStiffness = 800f,
            isLiquidGlassEnabled = true,
            surfaceAlpha = 0.15f,
            maxVisualizerComplexity = 90
        )

        val Beast = PerformanceConfig(
            isLowSpec = false,
            useGraphicsLayerForSize = false,
            useCompositingStrategyOffscreen = true,
            blurRadiusMultiplier = 1.2f, // Extra punchy blurs
            springDampingRatio = 0.4f,
            springStiffness = 1000f, // Ultra snappy
            isLiquidGlassEnabled = true,
            surfaceAlpha = 0.08f,
            maxVisualizerComplexity = 100
        )
    }
}

val LocalPerformanceConfig = staticCompositionLocalOf { PerformanceConfig.High }
