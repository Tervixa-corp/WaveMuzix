package com.example.wavemuzix.uiresource.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.data.SettingsManager

data class Language(val name: String, val code: String)

@Composable
fun LanguageSelectionScreen(
    settingsManager: SettingsManager,
    onContinue: () -> Unit
) {
    val languages = remember {
        listOf(
            Language("English", "en"),
            Language("Hindi", "hi"),
            Language("Malayalam", "ml"),
            Language("Tamil", "ta"),
            Language("Telugu", "te"),
            Language("Kannada", "kn"),
            Language("Punjabi", "pa"),
            Language("Bengali", "bn"),
            Language("Marathi", "mr"),
            Language("Gujarati", "gu"),
            Language("Spanish", "es"),
            Language("French", "fr"),
            Language("Korean", "ko"),
            Language("Japanese", "ja")
        )
    }

    var selectedSet by remember { mutableStateOf(settingsManager.selectedLanguages) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        TextButton(
            onClick = onContinue,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            Text(
                text = "Skip",
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            Text(
                text = "Choose Your Music Languages",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "Pick one or more to personalize your feed.",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(languages) { language ->
                    val isSelected = selectedSet.contains(language.code)
                    LanguageItem(
                        language = language,
                        isSelected = isSelected,
                        accentColor = Color(settingsManager.accentColor),
                        onClick = {
                            selectedSet = if (isSelected) {
                                selectedSet - language.code
                            } else {
                                selectedSet + language.code
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    settingsManager.selectedLanguages = selectedSet
                    onContinue()
                },
                enabled = selectedSet.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(settingsManager.accentColor),
                    disabledContainerColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.2f)
                else Color(0xFF1A1A1A)
            )
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = language.name,
            color = if (isSelected) accentColor else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 18.sp
        )
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, accentColor.copy(alpha = 0.1f))
                        )
                    )
            )
        }
    }
}
