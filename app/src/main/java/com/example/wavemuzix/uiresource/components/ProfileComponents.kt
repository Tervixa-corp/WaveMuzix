package com.example.wavemuzix.uiresource.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.ui.theme.SurfaceVariant

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun WaveUserAvatar(
    url: String?,
    name: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.1f)
) {
    Box(modifier = modifier) {
        if (!url.isNullOrEmpty()) {
            GlideImage(
                model = url,
                contentDescription = "User Avatar",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val initials = remember(name) {
                if (name.isNullOrBlank()) "?"
                else {
                    val cleanName = if (name.contains("@")) name.substringBefore("@") else name
                    val parts = cleanName.trim().split(Regex("[\\s._-]+"))
                    if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                        (parts[0].take(1) + parts[1].take(1)).uppercase()
                    } else if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        parts[0].take(1).uppercase()
                    } else {
                        "?"
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = backgroundColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
