package com.example.wavemuzix.uiresource.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wavemuzix.data.LocalPerformanceConfig

/**
 * Standard loader component that delegates to the Material 3 Expressive Loading Indicator.
 * On low-spec devices, it falls back to a standard CircularProgressIndicator to save resources.
 */
@Composable
fun StandardLoader(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    color: Color = Color.White
) {
    val performance = LocalPerformanceConfig.current
    
    if (performance.isLowSpec) {
        CircularProgressIndicator(
            modifier = modifier.size(if (size > 40.dp) 40.dp else size),
            color = color,
            strokeWidth = if (size < 20.dp) 2.dp else 3.dp
        )
    } else {
        MaterialLoadingIndicator(
            modifier = modifier,
            size = size,
            color = color
        )
    }
}
