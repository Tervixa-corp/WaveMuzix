package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.data.CookieManager
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.repository.UnifiedMusicRepository
import com.example.wavemuzix.uiresource.components.StandardLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ArtistSelection(val id: String, val name: String, val imageUrl: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ArtistSelectionScreen(
    unifiedRepository: UnifiedMusicRepository,
    settingsManager: SettingsManager,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val cookies = remember { CookieManager(context).getCookies() }
    val selectedLanguages = settingsManager.selectedLanguages
    
    val allArtists = remember { mutableStateListOf<ArtistSelection>() }
    val searchResults = remember { mutableStateListOf<ArtistSelection>() }
    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    val languageMap = remember {
        mapOf(
            "en" to "English", "hi" to "Hindi", "ml" to "Malayalam",
            "ta" to "Tamil", "te" to "Telugu", "kn" to "Kannada",
            "pa" to "Punjabi", "bn" to "Bengali", "mr" to "Marathi",
            "gu" to "Gujarati", "es" to "Spanish", "fr" to "French",
            "ko" to "Korean", "ja" to "Japanese"
        )
    }

    LaunchedEffect(selectedLanguages) {
        isLoading = true
        val fetchedArtists = java.util.Collections.synchronizedList(mutableListOf<ArtistSelection>())
        
        val scope = this
        val jobs = selectedLanguages.map { langCode ->
            scope.launch {
                val langName = languageMap[langCode] ?: "Global"
                try {
                    // Primary: Fetch HD artists from Deezer by language
                    val results = unifiedRepository.discoverArtistsByLanguage(langName, 20)
                    if (results.isNotEmpty()) {
                        fetchedArtists.addAll(results.map { ArtistSelection(it.browseId, it.name, it.thumbnailUrl) })
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ArtistSelection", "Error fetching Deezer artists for: $langName", e)
                }
            }
        }
        jobs.forEach { it.join() }
        
        // Secondary Fallback: If Deezer has few results, fill with YouTube
        if (fetchedArtists.distinctBy { it.id }.size < 30) {
            selectedLanguages.forEach { langCode ->
                val langName = languageMap[langCode] ?: "Global"
                try {
                    val ytResults = unifiedRepository.searchArtists("top $langName artists", cookies)
                    fetchedArtists.addAll(ytResults.map { ArtistSelection(it.browseId, it.name, it.thumbnailUrl) })
                } catch (e: Exception) {}
            }
        }

        allArtists.clear()
        allArtists.addAll(fetchedArtists.distinctBy { it.name.lowercase().trim() }.shuffled())
        isLoading = false
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSet by remember { mutableStateOf(settingsManager.selectedArtists) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            isSearching = false
            searchResults.clear()
            return@LaunchedEffect
        }
        
        delay(600)
        isSearching = true
        try {
            // Hybrid search: Deezer (HD) + YouTube (Broad)
            val deezerResults = unifiedRepository.discoverArtistsByLanguage(searchQuery, 5)
            val ytResults = unifiedRepository.searchArtists(searchQuery, cookies)
            
            val combined = (deezerResults.map { ArtistSelection(it.browseId, it.name, it.thumbnailUrl) } + 
                           ytResults.map { ArtistSelection(it.browseId, it.name, it.thumbnailUrl) })
                           .distinctBy { it.name.lowercase().trim() }
            
            searchResults.clear()
            searchResults.addAll(combined)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isSearching = false
        }
    }

    val displayArtists = if (searchQuery.isEmpty()) {
        allArtists
    } else {
        searchResults
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        TextButton(
            onClick = {
                settingsManager.isOnboardingComplete = true
                onFinish()
            },
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "Pick Your Favorites",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "We'll suggest more of what you love.",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                placeholder = { Text("Search artists...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, tint = Color.Gray, contentDescription = null) },
                trailingIcon = {
                    if (isSearching) {
                        StandardLoader(modifier = Modifier.size(20.dp))
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(settingsManager.accentColor),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading && searchQuery.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StandardLoader()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayArtists, key = { it.id }) { artist ->
                        val isSelected = selectedSet.contains(artist.id)
                        ArtistItem(
                            artist = artist,
                            isSelected = isSelected,
                            accentColor = Color(settingsManager.accentColor),
                            onClick = {
                                selectedSet = if (isSelected) {
                                    selectedSet - artist.id
                                } else {
                                    selectedSet + artist.id
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    settingsManager.selectedArtists = selectedSet
                    settingsManager.isOnboardingComplete = true
                    onFinish()
                },
                enabled = selectedSet.size >= 3,
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
                    text = if (selectedSet.size >= 3) "Finish" else "Select ${3 - selectedSet.size} more",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistItem(
    artist: ArtistSelection,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlideImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A)),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = artist.name,
            color = if (isSelected) accentColor else Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
