package com.example.wavemuzix.uiresource.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.uiresource.components.TutorialOverlay
import com.example.wavemuzix.uiresource.components.glassContainer
import kotlinx.coroutines.launch

@Composable
fun AccentColorPickerDialog(
    current: Color,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Color) -> Unit
) {
    val colors = listOf(
        Color(0xFFFF2D55), // Original Red/Pink
        Color(0xFFFF3B30), // Red
        Color(0xFFFF9500), // Orange
        Color(0xFFFFCC00), // Yellow
        Color(0xFF34C759), // Green
        Color(0xFF00C7BE), // Teal
        Color(0xFF30B0C7), // Cyan
        Color(0xFF32ADE6), // Blue
        Color(0xFF007AFF), // iOS Blue
        Color(0xFF5856D6), // Indigo
        Color(0xFFAF52DE), // Purple
        Color(0xFFFF2D55), // Pink
        Color(0xFFA2845E), // Brown
        Color(0xFF8E8E93), // Gray
        Color(0xFFE5E5EA), // Light Gray
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent Colour", color = Color.White) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 60.dp),
                modifier = Modifier.height(300.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(colors) { color ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == current) 3.dp else if (isLiquidGlass) 0.dp else 1.dp,
                                color = if (color == current) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .clickable { onSelect(color) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SettingsScreen(
    onAccentColorChange: (Color) -> Unit,
    onLiquidGlassChange: (Boolean) -> Unit,
    onUpdateTrigger: () -> Unit = {},
    onManageAccountClick: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    isLiquidGlass: Boolean = false
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    var showSettingsTutorial by remember { mutableStateOf(!settingsManager.hasSeenSettingsTutorial) }
    val scope = rememberCoroutineScope()

    // UI States for instant reactivity
    var isNormalizeVolume by remember { mutableStateOf(settingsManager.isNormalizeVolume) }
    var isPeakLimiterEnabled by remember { mutableStateOf(settingsManager.isPeakLimiterEnabled) }
    var isGaplessEnabled by remember { mutableStateOf(settingsManager.isGaplessRenderingEnabled) }
    var isDvcEnabled by remember { mutableStateOf(settingsManager.isDirectVolumeControlEnabled) }
    var isSkipSilenceEnabled by remember { mutableStateOf(settingsManager.isSkipSilenceEnabled) }
    
    var isVisualizerEnabled by remember { mutableStateOf(settingsManager.isVisualizerEnabled) }
    var playerType by remember { mutableStateOf(settingsManager.playerType) }
    var isDataSaverEnabled by remember { mutableStateOf(settingsManager.isDataSaverEnabled) }
    var isPlayVideoIfAvailable by remember { mutableStateOf(settingsManager.isPlayVideoIfAvailable) }
    var preferredVideoQuality by remember { mutableStateOf(settingsManager.preferredVideoQuality) }
    var isAmbientEnabled by remember { mutableStateOf(settingsManager.isAmbientBackgroundEnabled) }
    var isCrossfadeEnabled by remember { mutableStateOf(settingsManager.isCrossfadeEnabled) }
    var crossfadeThresholdDb by remember { mutableFloatStateOf(settingsManager.crossfadeThresholdDb) }

    var isSponsorBlockEnabled by remember { mutableStateOf(settingsManager.isSponsorBlockEnabled) }
    var skipSponsorMentions by remember { mutableStateOf(settingsManager.skipSponsorMentions) }
    var skipIntroOutro by remember { mutableStateOf(settingsManager.skipIntroOutro) }
    var skipNonMusicSkits by remember { mutableStateOf(settingsManager.skipNonMusicSkits) }
    
    var networkBufferSizeMs by remember { mutableLongStateOf(settingsManager.networkBufferSizeMs) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Basic, 1: Advanced

    fun syncSettings() {
        scope.launch {
            FirebaseSyncManager.syncToFirebase(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SoftBlack, DeepBlack)
                )
            )
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 26.sp
                )
            )
        }

        // Tab Selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(horizontal = 16.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            listOf("Basic", "Advanced").forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == index) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { selectedTab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
                val accentColor = Color(settingsManager.accentColor)
                
                if (selectedTab == 0) {
                    // BASIC SETTINGS (Duplicate of core items)
                    item(key = "basic_header_interface") { SettingHeader("Essential Interface", accentColor) }
                    item(key = "basic_item_accent_color") {
                        var showColorPicker by remember { mutableStateOf(false) }
                        SettingItem(
                            "Accent Colour",
                            "Change the theme accent colour",
                            onClick = { showColorPicker = true }
                        )
                        if (showColorPicker) {
                            AccentColorPickerDialog(
                                current = accentColor,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showColorPicker = false },
                                onSelect = { color ->
                                    settingsManager.accentColor = color.toArgb()
                                    onAccentColorChange(color)
                                    syncSettings()
                                    showColorPicker = false
                                }
                            )
                        }
                    }
                    item(key = "basic_toggle_liquid_glass") {
                        SettingToggle(
                            "Liquid Glass",
                            settingsManager.isLiquidGlass,
                            onCheckedChange = {
                                settingsManager.isLiquidGlass = it
                                onLiquidGlassChange(it)
                                syncSettings()
                            }
                        )
                    }
                    item(key = "basic_item_player_type") {
                        var showPlayerTypeDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "Player Screen Design",
                            playerType,
                            onClick = { showPlayerTypeDialog = true }
                        )
                        if (showPlayerTypeDialog) {
                            PlayerTypeSelectionDialog(
                                current = playerType,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showPlayerTypeDialog = false },
                                onSelect = {
                                    playerType = it
                                    settingsManager.playerType = it
                                    syncSettings()
                                    showPlayerTypeDialog = false
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }
                    item(key = "basic_toggle_ambient") {
                        SettingToggle(
                            "Ambient Universe Background",
                            isAmbientEnabled,
                            subtitle = "Organic fluid animations driven by music",
                            onCheckedChange = {
                                isAmbientEnabled = it
                                settingsManager.isAmbientBackgroundEnabled = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }

                    item(key = "basic_header_account") { SettingHeader("Account & Experience", accentColor) }
                    item(key = "basic_item_manage_account") {
                        SettingItem(
                            "Manage Account",
                            "Update your profile and settings",
                            onClick = onManageAccountClick
                        )
                    }
                    item(key = "basic_item_languages") {
                        var showLanguageSelection by remember { mutableStateOf(false) }
                        SettingItem(
                            "Content Languages",
                            "Change selected languages",
                            onClick = { showLanguageSelection = true }
                        )
                        if (showLanguageSelection) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = {
                                    showLanguageSelection = false
                                    syncSettings()
                                },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                LanguageSelectionScreen(
                                    settingsManager = settingsManager,
                                    onContinue = {
                                        showLanguageSelection = false
                                        syncSettings()
                                        onUpdateTrigger()
                                    }
                                )
                            }
                        }
                    }
                    
                    item(key = "basic_header_audio") { SettingHeader("Audio & Playback", accentColor) }
                    item(key = "basic_toggle_visualizer") {
                        SettingToggle(
                            "Audio Visualizer",
                            isVisualizerEnabled,
                            onCheckedChange = {
                                isVisualizerEnabled = it
                                settingsManager.isVisualizerEnabled = it
                                MusicPlayer.toggleVisualizer(it)
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "basic_header_about") { SettingHeader("About", accentColor) }
                    item(key = "basic_item_hardware_engine") {
                        var showHardwareDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "Hardware Optimization",
                            "Tailor engine performance for your device",
                            onClick = { showHardwareDialog = true }
                        )
                        if (showHardwareDialog) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showHardwareDialog = false },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                HardwareOptimizationScreen(
                                    onBack = { showHardwareDialog = false },
                                    isLiquidGlass = isLiquidGlass
                                )
                            }
                        }
                    }
                    item(key = "basic_item_version") { SettingItem("Version", "v1.0.0 (stable)") }
                } else {
                    // ADVANCED SETTINGS (Original list)
                    item(key = "header_interface") { SettingHeader("Interface", accentColor) }
                    item(key = "item_accent_color") {
                        var showColorPicker by remember { mutableStateOf(false) }
                        SettingItem(
                            "Accent Colour",
                            "Change the theme accent colour",
                            onClick = { showColorPicker = true }
                        )
                        if (showColorPicker) {
                            AccentColorPickerDialog(
                                current = accentColor,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showColorPicker = false },
                                onSelect = { color ->
                                    settingsManager.accentColor = color.toArgb()
                                    onAccentColorChange(color)
                                    syncSettings()
                                    showColorPicker = false
                                }
                            )
                        }
                    }
                    item(key = "toggle_liquid_glass") {
                        SettingToggle(
                            "Liquid Glass",
                            settingsManager.isLiquidGlass,
                            onCheckedChange = {
                                settingsManager.isLiquidGlass = it
                                onLiquidGlassChange(it)
                                syncSettings()
                            }
                        )
                    }
                    item(key = "item_player_type") {
                        var showPlayerTypeDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "Player Screen Design",
                            playerType,
                            onClick = { showPlayerTypeDialog = true }
                        )
                        if (showPlayerTypeDialog) {
                            PlayerTypeSelectionDialog(
                                current = playerType,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showPlayerTypeDialog = false },
                                onSelect = {
                                    playerType = it
                                    settingsManager.playerType = it
                                    syncSettings()
                                    showPlayerTypeDialog = false
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }
                    item(key = "toggle_ambient_universe") {
                        SettingToggle(
                            "Ambient Universe Background",
                            isAmbientEnabled,
                            subtitle = "Organic fluid animations driven by music",
                            onCheckedChange = {
                                isAmbientEnabled = it
                                settingsManager.isAmbientBackgroundEnabled = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_keep_screen_on") {
                        SettingToggle(
                            "Keep Screen On",
                            settingsManager.isKeepScreenOn,
                            onCheckedChange = {
                                settingsManager.isKeepScreenOn = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }

                    item(key = "header_content") { SettingHeader("Account & Content", accentColor) }
                    item(key = "item_manage_account") {
                        SettingItem(
                            "Manage Account",
                            "Update your profile, phone and settings",
                            onClick = onManageAccountClick
                        )
                    }
                    item(key = "item_content_country") {
                        var showCountryDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "Content Country",
                            settingsManager.contentCountry,
                            onClick = { showCountryDialog = true }
                        )
                        if (showCountryDialog) {
                            CountrySelectionDialog(
                                current = settingsManager.contentCountry,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showCountryDialog = false },
                                onSelect = {
                                    settingsManager.contentCountry = it
                                    syncSettings()
                                    showCountryDialog = false
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }
                    item(key = "item_languages") {
                        var showLanguageSelection by remember { mutableStateOf(false) }
                        SettingItem(
                            "Content Languages",
                            "Change selected languages",
                            onClick = { showLanguageSelection = true }
                        )
                        if (showLanguageSelection) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = {
                                    showLanguageSelection = false
                                    syncSettings()
                                },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                LanguageSelectionScreen(
                                    settingsManager = settingsManager,
                                    onContinue = {
                                        showLanguageSelection = false
                                        syncSettings()
                                        onUpdateTrigger()
                                    }
                                )
                            }
                        }
                    }
                    item(key = "item_artists") {
                        var showArtistSelection by remember { mutableStateOf(false) }
                        SettingItem(
                            "Artist Selection",
                            "Change favorite artists",
                            onClick = { showArtistSelection = true }
                        )
                        if (showArtistSelection) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = {
                                    showArtistSelection = false
                                    syncSettings()
                                },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                ArtistSelectionScreen(
                                    unifiedRepository = MusicPlayer.unifiedRepository!!,
                                    settingsManager = settingsManager,
                                    onFinish = {
                                        showArtistSelection = false
                                        syncSettings()
                                        onUpdateTrigger()
                                    }
                                )
                            }
                        }
                    }

                    item(key = "item_download_quality") {
                        var showQualityDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "Download Quality",
                            settingsManager.downloadQuality,
                            onClick = { showQualityDialog = true }
                        )
                        if (showQualityDialog) {
                            DownloadQualitySelectionDialog(
                                current = settingsManager.downloadQuality,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showQualityDialog = false },
                                onSelect = {
                                    settingsManager.downloadQuality = it
                                    syncSettings()
                                    showQualityDialog = false
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }

                    item(key = "item_download_threads") {
                        var showThreadsDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "Download Threads",
                            "${settingsManager.downloadThreads} Threads\ndownload speed (Thread)",
                            onClick = { showThreadsDialog = true }
                        )
                        if (showThreadsDialog) {
                            DownloadThreadsSelectionDialog(
                                current = settingsManager.downloadThreads,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showThreadsDialog = false },
                                onSelect = {
                                    settingsManager.downloadThreads = it
                                    syncSettings()
                                    showThreadsDialog = false
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }

                    item(key = "header_audio") { SettingHeader("Next-Gen Audio Engine", Color(settingsManager.accentColor)) }
                    item(key = "toggle_normalize") {
                        SettingToggle(
                            "Limit Volume",
                            isNormalizeVolume,
                            subtitle = "True-Volume Normalization",
                            onCheckedChange = {
                                isNormalizeVolume = it
                                settingsManager.isNormalizeVolume = it
                                MusicPlayer.updateAudioEffects()
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_peak_limiter") {
                        SettingToggle(
                            "Anti-Clip Peak Limiter",
                            isPeakLimiterEnabled,
                            subtitle = "Avoid Disturbance while Volume Normalization",
                            onCheckedChange = {
                                isPeakLimiterEnabled = it
                                settingsManager.isPeakLimiterEnabled = it
                                MusicPlayer.updateAudioEffects()
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_gapless") {
                        SettingToggle(
                            "Seamless Gapless Playback",
                            isGaplessEnabled,
                            subtitle = "preload songs",
                            onCheckedChange = {
                                isGaplessEnabled = it
                                settingsManager.isGaplessRenderingEnabled = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "item_surround_mode") {
                        var showSurroundDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "3D Surround Virtualizer",
                            settingsManager.surroundMode,
                            onClick = { showSurroundDialog = true }
                        )
                        if (showSurroundDialog) {
                            SurroundModeSelectionDialog(
                                current = settingsManager.surroundMode,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showSurroundDialog = false },
                                onSelect = {
                                    settingsManager.surroundMode = it
                                    MusicPlayer.updateAudioEffects()
                                    syncSettings()
                                    showSurroundDialog = false
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }
                    item(key = "toggle_dvc") {
                        SettingToggle(
                            "Direct Volume Control (Hi-Res)",
                            isDvcEnabled,
                            subtitle = "Avoid audio Duckling while notification",
                            onCheckedChange = {
                                isDvcEnabled = it
                                settingsManager.isDirectVolumeControlEnabled = it
                                MusicPlayer.updateDvcState()
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_skip_silence") {
                        SettingToggle(
                            "Hardware Skip Silence",
                            isSkipSilenceEnabled,
                            onCheckedChange = {
                                isSkipSilenceEnabled = it
                                settingsManager.isSkipSilenceEnabled = it
                                MusicPlayer.skipSilenceEnabled = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }

                    item(key = "header_playback") { SettingHeader("Playback", accentColor) }
                    item(key = "toggle_save_state") {
                        SettingToggle(
                            "Save Playback State",
                            settingsManager.isSavePlaybackState,
                            onCheckedChange = {
                                settingsManager.isSavePlaybackState = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_save_last") {
                        SettingToggle(
                            "Save Last Played",
                            settingsManager.isSaveLastPlayed,
                            onCheckedChange = {
                                settingsManager.isSaveLastPlayed = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_visualizer") {
                        SettingToggle(
                            "Audio Visualizer",
                            isVisualizerEnabled,
                            onCheckedChange = {
                                isVisualizerEnabled = it
                                settingsManager.isVisualizerEnabled = it
                                MusicPlayer.toggleVisualizer(it)
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_keep_alive") {
                        SettingToggle(
                            "Keep service alive",
                            settingsManager.isKeepServiceAlive,
                            onCheckedChange = {
                                settingsManager.isKeepServiceAlive = it
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_crossfade") {
                        SettingToggle(
                            "Crossfade",
                            isCrossfadeEnabled,
                            onCheckedChange = {
                                isCrossfadeEnabled = it
                                settingsManager.isCrossfadeEnabled = it
                                MusicPlayer.updateCrossfadeState(context)
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    if (isCrossfadeEnabled) {
                        item(key = "item_crossfade_threshold") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Crossfade Threshold",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White
                                    )
                                    Text(
                                        "${crossfadeThresholdDb.toInt()} dB",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = crossfadeThresholdDb,
                                    onValueChange = {
                                        crossfadeThresholdDb = it
                                        settingsManager.crossfadeThresholdDb = it
                                    },
                                    onValueChangeFinished = {
                                        syncSettings()
                                    },
                                    valueRange = -40f..-6f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                                Text(
                                    text = when {
                                        crossfadeThresholdDb >= -11f && crossfadeThresholdDb <= -7f -> "Excellent transition"
                                        crossfadeThresholdDb > -12f -> "Quick, loud crossover"
                                        crossfadeThresholdDb < -30f -> "Very deep, near-silent fade"
                                        else -> "Cinematic smooth fade"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    item(key = "header_cockpit") { SettingHeader("Extreme Control (The Cockpit)", accentColor) }
                    item(key = "toggle_sponsorblock") {
                        SettingToggle(
                            "Enable SponsorBlock (NEXT_SYNC)",
                            isSponsorBlockEnabled,
                            subtitle = "Bypass segments with near-zero latency",
                            onCheckedChange = {
                                isSponsorBlockEnabled = it
                                settingsManager.isSponsorBlockEnabled = it
                                MusicPlayer.updateSponsorBlockSettings()
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "item_buffer_size") {
                        var showBufferDialog by remember { mutableStateOf(false) }
                        val currentLabel = when (networkBufferSizeMs) {
                            0L -> "AI Auto (Adaptive)"
                            10000L -> "Low Latency (10s)"
                            30000L -> "Balanced (30s)"
                            50000L -> "High Cache (50s)"
                            else -> "AI Auto (Adaptive)"
                        }
                        SettingItem(
                            "Network Buffer Size",
                            currentLabel,
                            onClick = { showBufferDialog = true }
                        )
                        if (showBufferDialog) {
                            BufferSelectionDialog(
                                current = networkBufferSizeMs,
                                isLiquidGlass = isLiquidGlass,
                                onDismiss = { showBufferDialog = false },
                                onSelect = {
                                    networkBufferSizeMs = it
                                    settingsManager.networkBufferSizeMs = it
                                    syncSettings()
                                    showBufferDialog = false
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }
                    item(key = "toggle_data_saver") {
                        SettingToggle(
                            "Data Saver Mode",
                            isDataSaverEnabled,
                            subtitle = "Reduce Quality",
                            onCheckedChange = {
                                isDataSaverEnabled = it
                                settingsManager.isDataSaverEnabled = it
                                if (it) {
                                    isPlayVideoIfAvailable = false
                                    settingsManager.isPlayVideoIfAvailable = false
                                }
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_play_video") {
                        Box(modifier = Modifier.fillMaxWidth().clickable {
                            if (isDataSaverEnabled) {
                                android.widget.Toast.makeText(context, "Data Saver is On", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            SettingToggle(
                                "Play video if available",
                                isPlayVideoIfAvailable,
                                enabled = !isDataSaverEnabled,
                                onCheckedChange = {
                                    isPlayVideoIfAvailable = it
                                    settingsManager.isPlayVideoIfAvailable = it
                                    syncSettings()
                                    onUpdateTrigger()
                                }
                            )
                        }
                    }
                    if (isPlayVideoIfAvailable) {
                        item(key = "video_quality") {
                            var showQualityDialog by remember { mutableStateOf(false) }
                            SettingItem(
                                "Preferred Video Quality",
                                preferredVideoQuality,
                                onClick = { showQualityDialog = true }
                            )
                            if (showQualityDialog) {
                                VideoQualitySelectionDialog(
                                    current = preferredVideoQuality,
                                    isLiquidGlass = isLiquidGlass,
                                    onDismiss = { showQualityDialog = false },
                                    onSelect = {
                                        preferredVideoQuality = it
                                        settingsManager.preferredVideoQuality = it
                                        syncSettings()
                                        showQualityDialog = false
                                        onUpdateTrigger()
                                    }
                                )
                            }
                        }
                    }
                    item(key = "toggle_skip_sponsor") {
                        SettingToggle(
                            "Skip Sponsor Mentions",
                            skipSponsorMentions,
                            enabled = isSponsorBlockEnabled,
                            onCheckedChange = {
                                skipSponsorMentions = it
                                settingsManager.skipSponsorMentions = it
                                MusicPlayer.updateSponsorBlockSettings()
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_skip_intro") {
                        SettingToggle(
                            "Skip Intro/Outro Silence",
                            skipIntroOutro,
                            enabled = isSponsorBlockEnabled,
                            onCheckedChange = {
                                skipIntroOutro = it
                                settingsManager.skipIntroOutro = it
                                MusicPlayer.updateSponsorBlockSettings()
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }
                    item(key = "toggle_skip_skits") {
                        SettingToggle(
                            "Skip Non-Music Skits & Dialogues",
                            skipNonMusicSkits,
                            enabled = isSponsorBlockEnabled,
                            onCheckedChange = {
                                skipNonMusicSkits = it
                                settingsManager.skipNonMusicSkits = it
                                MusicPlayer.updateSponsorBlockSettings()
                                syncSettings()
                                onUpdateTrigger()
                            }
                        )
                    }

                    item(key = "header_lyrics") { SettingHeader("Lyrics", accentColor) }

                    item(key = "header_contact") { SettingHeader("Contact Us", accentColor) }
                    item(key = "item_email1") {
                        SettingItem(
                            "Email Support",
                            "hritvikkrishnansajeev@gmail.com",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:hritvikkrishnansajeev@gmail.com")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    item(key = "item_email2") {
                        SettingItem(
                            "Email WaveMuzix",
                            "wavemuzix@gmail.com",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:wavemuzix@gmail.com")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    item(key = "item_whatsapp") {
                        SettingItem(
                            "WhatsApp",
                            "+91 75111 55295",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://api.whatsapp.com/send?phone=917511155295")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    item(key = "header_about") { SettingHeader("About us", accentColor) }
                    item(key = "item_hardware_engine") {
                        var showHardwareDialog by remember { mutableStateOf(false) }
                        SettingItem(
                            "Hardware Optimization",
                            "Tailor engine performance for your device",
                            onClick = { showHardwareDialog = true }
                        )
                        if (showHardwareDialog) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showHardwareDialog = false },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                HardwareOptimizationScreen(
                                    onBack = { showHardwareDialog = false },
                                    isLiquidGlass = isLiquidGlass
                                )
                            }
                        }
                    }
                    item(key = "item_version") { SettingItem("Version", "v1.0.0 (stable)") }
                    item(key = "item_author") { SettingItem("Author", "Lumino Global") }
                    item(key = "item_sub_author") { SettingItem("Sub-Author", "Lumino Studios") }
                }

                // Extra space for mini-player visibility
                item { Spacer(modifier = Modifier.height(150.dp)) }
            }
        }

        if (showSettingsTutorial) {
            TutorialOverlay(
                title = "The Command Center",
                description = "Fine-tune your audio engine, customize the 'Ambient Universe', and manage your account. Everything you need to control the experience.",
                onDismiss = {
                    showSettingsTutorial = false
                    settingsManager.hasSeenSettingsTutorial = true
                }
            )
        }
    }



@Composable
fun PlayerTypeSelectionDialog(
    current: String,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf("Classic", "Material 3")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Player Design") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun BufferSelectionDialog(
    current: Long,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val options = listOf(
        0L to "AI Auto (Adaptive)",
        30000L to "Balanced (30s)",
        45000L to "Stable (45s)",
        60000L to "Extreme Cache (60s)"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network Buffer Size") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == current, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun PrioritySelectionDialog(
    current: String,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf("Force OPUS 160kbps", "Fallback to AAC", "Prefer Low-Bandwidth")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Source Priority") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) })
                        Spacer(Modifier.width(8.dp))
                        Text(option, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun SurroundModeSelectionDialog(
    current: String,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf("None", "Empty Stadium", "Cyberpunk Club", "Small Studio")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("3D Surround Mode") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) })
                        Spacer(Modifier.width(8.dp))
                        Text(option, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun VideoQualitySelectionDialog(
    current: String,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf("auto(recommended)", "4k", "2k", "1080p", "720p", "480p")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Quality") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(option, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun DownloadQualitySelectionDialog(
    current: String,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf("Standard (OPUS 160kbps)", "Data Saver")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Quality") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(option, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun DownloadThreadsSelectionDialog(
    current: Int,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(1, 2, 4, 8, 12, 16)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Threads") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("$option Threads", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun LyricsProviderSelectionDialog(
    current: String,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val providers = listOf("Auto", "Alsong", "Netease", "LRCLIB")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Lyrics Provider") },
        text = {
            LazyColumn {
                items(
                    count = providers.size,
                    key = { providers[it] }
                ) { index ->
                    val provider = providers[index]
                    ListItem(
                        modifier = Modifier.clickable { onSelect(provider) },
                        headlineContent = { Text(provider) },
                        trailingContent = {
                            RadioButton(selected = provider == current, onClick = { onSelect(provider) })
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
fun CountrySelectionDialog(
    current: String,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val countries = listOf("US", "IN", "GB", "CA", "AU", "DE", "FR", "JP", "KR", "BR")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Country") },
        text = {
            LazyColumn {
                items(
                    count = countries.size,
                    key = { countries[it] }
                ) { index ->
                    val country = countries[index]
                    ListItem(
                        modifier = Modifier.clickable { onSelect(country) },
                        headlineContent = { Text(country) },
                        trailingContent = {
                            RadioButton(selected = country == current, onClick = { onSelect(country) })
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = if (isLiquidGlass) Color.Transparent else Color(0xFF1A1A1A),
        modifier = Modifier.then(
            if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            } else Modifier
        )
    )
}

@Composable
private fun SettingHeader(text: String, accentColor: Color = Color.White) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (accentColor == Color.White) Color.White else accentColor,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingToggle(
    title: String, 
    initialValue: Boolean, 
    subtitle: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    var checked by remember(initialValue) { mutableStateOf(initialValue) }
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) Color.White else Color.Gray,
            supportingColor = Color.Gray
        ),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = {
            Switch(
                enabled = enabled,
                checked = checked,
                onCheckedChange = {
                    checked = it
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF333333),
                    uncheckedBorderColor = Color.Transparent,
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledUncheckedTrackColor = Color(0xFF333333).copy(alpha = 0.5f)
                )
            )
        }
    )
}

@Composable
private fun SettingItem(title: String, subtitle: String, onClick: () -> Unit = {}) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent, 
            headlineColor = Color.White, 
            supportingColor = Color.White
        ),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)) }
    )
}
