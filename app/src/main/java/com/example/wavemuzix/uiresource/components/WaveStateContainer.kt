package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun WaveStateContainer(
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    isError: Boolean,
    errorMessage: String? = "Check Your Internet Connection",
    onRetry: () -> Unit,
    loadingMessage: String = "Hold tight..We're connecting",
    loadingTimeoutMillis: Long = 2000L,
    loadingContent: (@Composable () -> Unit)? = null,
    showRefreshingIndicator: Boolean = true,
    content: @Composable () -> Unit
) {
    var showDelayedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(loadingTimeoutMillis)
            showDelayedMessage = true
        } else {
            showDelayedMessage = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isError -> {
                ErrorScreen(
                    message = errorMessage ?: "Check Your Internet Connection",
                    onRetry = onRetry
                )
            }
            isLoading -> {
                if (loadingContent != null) {
                    loadingContent()
                } else {
                    LoadingScreen(
                        message = if (showDelayedMessage) loadingMessage else null
                    )
                }
            }
            else -> {
                content()
                
                if (isRefreshing && showRefreshingIndicator) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialLoadingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MaterialLoadingIndicator()
            
            message?.let {
                Spacer(modifier = Modifier.height(4.dp))
                TypewriterText(
                    text = it,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    var visibleChars by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        visibleChars = 0
        for (i in 1..text.length) {
            visibleChars = i
            delay(50)
        }
    }

    Text(
        text = text.take(visibleChars),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        letterSpacing = letterSpacing
    )
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "No Internet",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = message,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Retry",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
