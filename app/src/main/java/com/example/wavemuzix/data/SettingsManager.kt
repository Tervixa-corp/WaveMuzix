package com.example.wavemuzix.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wave_settings", Context.MODE_PRIVATE)

    var isLiquidGlass: Boolean
        get() = prefs.getBoolean("liquid_glass", true)
        set(value) { prefs.edit().putBoolean("liquid_glass", value).commit() }

    var contentCountry: String
        get() = prefs.getString("content_country", "US") ?: "US"
        set(value) { prefs.edit().putString("content_country", value).commit() }

    var isPreferHighQuality: Boolean
        get() = prefs.getBoolean("prefer_high_quality", false)
        set(value) { prefs.edit().putBoolean("prefer_high_quality", value).commit() }

    var isNormalizeVolume: Boolean
        get() = prefs.getBoolean("normalize_volume", true)
        set(value) { prefs.edit().putBoolean("normalize_volume", value).commit() }

    var isSavePlaybackState: Boolean
        get() = prefs.getBoolean("save_playback_state", true)
        set(value) { prefs.edit().putBoolean("save_playback_state", value).commit() }

    var isSaveLastPlayed: Boolean
        get() = prefs.getBoolean("save_last_played", true)
        set(value) { prefs.edit().putBoolean("save_last_played", value).commit() }

    var isKeepServiceAlive: Boolean
        get() = prefs.getBoolean("keep_service_alive", true)
        set(value) { prefs.edit().putBoolean("keep_service_alive", value).commit() }

    var isCrossfadeEnabled: Boolean
        get() = prefs.getBoolean("crossfade_enabled", false)
        set(value) { prefs.edit().putBoolean("crossfade_enabled", value).commit() }

    var crossfadeThresholdDb: Float
        get() = prefs.getFloat("crossfade_threshold_db", -10f)
        set(value) { prefs.edit().putFloat("crossfade_threshold_db", value).commit() }

    var isSponsorBlockEnabled: Boolean
        get() = prefs.getBoolean("sponsor_block_enabled", true)
        set(value) { prefs.edit().putBoolean("sponsor_block_enabled", value).commit() }

    var accentColor: Int
        get() = prefs.getInt("accent_color", 0xFFFF0000.toInt()) // Default Red
        set(value) { prefs.edit().putInt("accent_color", value).commit() }

    var lastPlayedSongId: String?
        get() = prefs.getString("last_played_song_id", null)
        set(value) { prefs.edit().putString("last_played_song_id", value).commit() }

    var lyricsProvider: String
        get() = prefs.getString("lyrics_provider", "Alsong") ?: "Alsong"
        set(value) { prefs.edit().putString("lyrics_provider", value).commit() }

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) { prefs.edit().putBoolean("onboarding_complete", value).commit() }

    var userLocation: String?
        get() = prefs.getString("user_location", null)
        set(value) { prefs.edit().putString("user_location", value).commit() }

    var selectedLanguages: Set<String>
        get() {
            val set = prefs.getStringSet("selected_languages", null)
            return if (set.isNullOrEmpty()) setOf("hindi", "english") else set
        }
        set(value) { prefs.edit().putStringSet("selected_languages", value).commit() }

    var selectedArtists: Set<String>
        get() = prefs.getStringSet("selected_artists", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("selected_artists", value).commit() }

    var phoneNumber: String
        get() = prefs.getString("user_phone_number", "") ?: ""
        set(value) { prefs.edit().putString("user_phone_number", value).commit() }

    var isKeepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", false)
        set(value) { prefs.edit().putBoolean("keep_screen_on", value).commit() }

    var downloadQuality: String
        get() = prefs.getString("download_quality", "Standard (OPUS 160kbps)") ?: "Standard (OPUS 160kbps)"
        set(value) { prefs.edit().putString("download_quality", value).commit() }

    var isSkipSilenceEnabled: Boolean
        get() = prefs.getBoolean("skip_silence_enabled", false)
        set(value) { prefs.edit().putBoolean("skip_silence_enabled", value).commit() }

    var isVisualizerEnabled: Boolean
        get() = prefs.getBoolean("visualizer_enabled", true)
        set(value) { prefs.edit().putBoolean("visualizer_enabled", value).commit() }

    var lastIgnoredVersion: Int
        get() = prefs.getInt("last_ignored_version", 0)
        set(value) { prefs.edit().putInt("last_ignored_version", value).commit() }

    var downloadThreads: Int
        get() = prefs.getInt("download_threads", 4)
        set(value) { prefs.edit().putInt("download_threads", value).commit() }

    var shouldShowLoginPrompt: Boolean
        get() = prefs.getBoolean("should_show_login_prompt", true)
        set(value) { prefs.edit().putBoolean("should_show_login_prompt", value).commit() }

    // Next-Gen Audio Tech
    var isPeakLimiterEnabled: Boolean
        get() = prefs.getBoolean("peak_limiter_enabled", false)
        set(value) { prefs.edit().putBoolean("peak_limiter_enabled", value).commit() }

    var isGaplessRenderingEnabled: Boolean
        get() = prefs.getBoolean("gapless_rendering_enabled", true)
        set(value) { prefs.edit().putBoolean("gapless_rendering_enabled", value).commit() }

    var surroundMode: String
        get() = prefs.getString("surround_mode", "None") ?: "None"
        set(value) { prefs.edit().putString("surround_mode", value).commit() }

    var isDirectVolumeControlEnabled: Boolean
        get() = prefs.getBoolean("direct_volume_control_enabled", false)
        set(value) { prefs.edit().putBoolean("direct_volume_control_enabled", value).commit() }

    // Extreme Control (The Cockpit)
    var networkBufferSizeMs: Long
        get() = prefs.getLong("network_buffer_size_ms", 0L) // Default 0L = AI Auto
        set(value) { prefs.edit().putLong("network_buffer_size_ms", value).commit() }

    var skipSponsorMentions: Boolean
        get() = prefs.getBoolean("skip_sponsor_mentions", true)
        set(value) { prefs.edit().putBoolean("skip_sponsor_mentions", value).commit() }

    var skipIntroOutro: Boolean
        get() = prefs.getBoolean("skip_intro_outro", true)
        set(value) { prefs.edit().putBoolean("skip_intro_outro", value).commit() }

    var skipNonMusicSkits: Boolean
        get() = prefs.getBoolean("skip_non_music_skits", true)
        set(value) { prefs.edit().putBoolean("skip_non_music_skits", value).commit() }

    var isDataSaverEnabled: Boolean
        get() = prefs.getBoolean("is_data_saver_enabled", false)
        set(value) { prefs.edit().putBoolean("is_data_saver_enabled", value).commit() }

    var isPlayVideoIfAvailable: Boolean
        get() = prefs.getBoolean("is_play_video_if_available", true)
        set(value) { prefs.edit().putBoolean("is_play_video_if_available", value).commit() }

    var preferredVideoQuality: String
        get() = prefs.getString("preferred_video_quality", "auto") ?: "auto"
        set(value) { prefs.edit().putString("preferred_video_quality", value).commit() }

    var isAmbientBackgroundEnabled: Boolean
        get() = prefs.getBoolean("is_ambient_background_enabled", true)
        set(value) { prefs.edit().putBoolean("is_ambient_background_enabled", value).commit() }

    var userVolume: Float
        get() = prefs.getFloat("user_volume", 1.0f)
        set(value) { prefs.edit().putFloat("user_volume", value).commit() }

    var hasSeenPlayerTutorial: Boolean
        get() = prefs.getBoolean("has_seen_player_tutorial", false)
        set(value) { prefs.edit().putBoolean("has_seen_player_tutorial", value).commit() }

    var hasSeenLibraryTutorial: Boolean
        get() = prefs.getBoolean("has_seen_library_tutorial", false)
        set(value) { prefs.edit().putBoolean("has_seen_library_tutorial", value).commit() }

    var hasSeenSettingsTutorial: Boolean
        get() = prefs.getBoolean("has_seen_settings_tutorial", false)
        set(value) { prefs.edit().putBoolean("has_seen_settings_tutorial", value).commit() }

    var volumeBoost: Float
        get() = 50.0f // Fixed 5000%
        set(value) { /* No longer changeable */ }

    var playerType: String
        get() = prefs.getString("player_type", "Classic") ?: "Classic"
        set(value) { prefs.edit().putString("player_type", value).commit() }

    // Offline Cache for Home Feed
    var homeFeedCache: String?
        get() = prefs.getString("home_feed_cache", null)
        set(value) { prefs.edit().putString("home_feed_cache", value).apply() }
}
