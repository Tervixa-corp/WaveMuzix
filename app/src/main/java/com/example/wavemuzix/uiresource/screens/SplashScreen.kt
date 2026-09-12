package com.example.wavemuzix.uiresource.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.ui.theme.AccentPrimary
import com.example.wavemuzix.ui.theme.AccentSecondary
import com.example.wavemuzix.ui.theme.DeepBlack
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    onPreloadTrigger: () -> Unit = {}
) {
    var startAnimation by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "borealis")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1500, easing = EaseInOutCubic),
        label = "logoAlpha"
    )
    
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.95f,
        animationSpec = tween(2000, easing = EaseOutQuart),
        label = "logoScale"
    )

    LaunchedEffect(Unit) {
        delay(300)
        startAnimation = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        
        delay(1000)
        onPreloadTrigger()
        
        delay(2500)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center
    ) {
        // 1. Fluid Borealis Background - Using Brand Colors
        Canvas(modifier = Modifier.fillMaxSize().alpha(logoAlpha * 0.5f)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // Primary Accent (Pink/Red)
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to AccentPrimary.copy(alpha = 0.2f),
                    1.0f to Color.Transparent,
                    center = Offset(
                        center.x + cos(phase) * size.width * 0.15f,
                        center.y + sin(phase * 0.6f) * size.height * 0.1f
                    ),
                    radius = size.minDimension * 0.8f
                )
            )
            
            // Secondary Accent (Purple)
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to AccentSecondary.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                    center = Offset(
                        center.x + sin(phase * 0.8f) * size.width * 0.2f,
                        center.y + cos(phase * 0.5f) * size.height * 0.12f
                    ),
                    radius = size.minDimension * 0.7f
                )
            )
        }

        // 2. Premium Branding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Soft Glow
                Text(
                    text = "WAVEMUZIX",
                    style = TextStyle(
                        fontWeight = FontWeight.Black,
                        fontSize = 54.sp,
                        letterSpacing = 2.sp,
                        color = AccentPrimary.copy(alpha = 0.3f * logoAlpha)
                    ),
                    modifier = Modifier
                        .blur(30.dp)
                        .graphicsLayer {
                            scaleX = logoScale * 1.1f
                            scaleY = logoScale * 1.1f
                        }
                )

                // Main Text
                Text(
                    text = "WAVEMUZIX",
                    style = TextStyle(
                        fontWeight = FontWeight.Black,
                        fontSize = 54.sp,
                        letterSpacing = 2.sp,
                        color = Color.White
                    ),
                    modifier = Modifier
                        .alpha(logoAlpha)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ELEVATE YOUR SOUND",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.alpha(logoAlpha.coerceIn(0f, 0.6f))
            )
        }

        // 3. Bottom Progress Bar - Using Brand Accent
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
                .width(200.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            val progress by animateFloatAsState(
                targetValue = if (startAnimation) 1f else 0f,
                animationSpec = tween(3000, easing = EaseInOutCubic)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, AccentPrimary.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )
        }
    }
}
