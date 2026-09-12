package com.example.wavemuzix.data

import com.example.wavemuzix.utils.AppLogger
import android.content.Context
import android.util.Log
import com.example.wavemuzix.data.database.AppDatabase
import com.example.wavemuzix.data.database.FavoriteSongEntity
import com.example.wavemuzix.data.database.PlaylistEntity
import com.example.wavemuzix.data.database.PlaylistSongEntity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private const val SYNC_TIMEOUT_MS = 15000L // 15 seconds timeout
    
    // Global tracking to prevent redundant refreshes during a single app session
    @Volatile
    private var hasSyncedThisSession = false
    private var lastSyncedUserId: String? = null

    fun needsSync(userId: String?): Boolean {
        if (userId == null) return false // Don't sync for guests
        return !hasSyncedThisSession || lastSyncedUserId != userId
    }

    fun markSynced(userId: String?) {
        hasSyncedThisSession = true
        lastSyncedUserId = userId
    }

    fun resetSyncStatus() {
        hasSyncedThisSession = false
        lastSyncedUserId = null
    }
    private const val USERS_COLLECTION = "users"
    private const val FAVORITES_COLLECTION = "library" // Renamed to 'library' for clarity in Firebase Console
    private const val PLAYLISTS_COLLECTION = "playlists"
    private const val MOST_PLAYED_COLLECTION = "most_played"
    private const val USER_DATA_DOCUMENT = "user_data"
    
    // Removed init block with firestoreSettings to prevent IllegalStateException 
    // as Firestore might be initialized elsewhere (like in UpdateManager) first.

    // Add empty constructors or handle mapping manually for Firestore
    data class PlaylistFirestore(
        val id: Long = 0,
        val name: String = "",
        val createdAt: Long = 0
    )

    data class PlaylistSongFirestore(
        val playlistId: Long = 0,
        val videoId: String = "",
        val title: String = "",
        val artist: String = "",
        val thumbnailUrl: String = "",
        val artistId: String? = null,
        val addedAt: Long = 0
    )

    data class FavoriteSongFirestore(
        val videoId: String = "",
        val title: String = "",
        val artist: String = "",
        val thumbnailUrl: String = "",
        val artistId: String? = null,
        val timestamp: Long = 0
    )

    data class MostPlayedFirestore(
        val videoId: String = "",
        val title: String = "",
        val artist: String = "",
        val thumbnailUrl: String = "",
        val artistId: String? = null,
        val playCount: Int = 0,
        val lastPlayedTimestamp: Long = 0
    )

    data class UserDataFirestore(
        val email: String? = null,
        val displayName: String? = null,
        val phoneNumber: String? = null,
        val isLiquidGlass: Boolean? = null,
        val contentCountry: String? = null,
        val userLocation: String? = null,
        val isPreferHighQuality: Boolean? = null,
        val isNormalizeVolume: Boolean? = null,
        val isSavePlaybackState: Boolean? = null,
        val isSaveLastPlayed: Boolean? = null,
        val lastPlayedSongId: String? = null,
        val isKeepServiceAlive: Boolean? = null,
        val isCrossfadeEnabled: Boolean? = null,
        val isSponsorBlockEnabled: Boolean? = null,
        val accentColor: Int? = null,
        val lyricsProvider: String? = null,
        val isOnboardingComplete: Boolean? = null,
        val selectedLanguages: List<String>? = null,
        val selectedArtists: List<String>? = null,
        val isKeepScreenOn: Boolean? = null,
        val downloadQuality: String? = null,
        val isSkipSilenceEnabled: Boolean? = null,
        val isVisualizerEnabled: Boolean? = null,
        val lastIgnoredVersion: Int? = null,
        val downloadThreads: Int? = null,
        val shouldShowLoginPrompt: Boolean? = null,
        val isPeakLimiterEnabled: Boolean? = null,
        val isGaplessRenderingEnabled: Boolean? = null,
        val surroundMode: String? = null,
        val isDirectVolumeControlEnabled: Boolean? = null,
        val networkBufferSizeMs: Long? = null,
        val skipSponsorMentions: Boolean? = null,
        val skipIntroOutro: Boolean? = null,
        val skipNonMusicSkits: Boolean? = null,
        val isDataSaverEnabled: Boolean? = null,
        val isAmbientBackgroundEnabled: Boolean? = null,
        val hasSeenPlayerTutorial: Boolean? = null,
        val hasSeenLibraryTutorial: Boolean? = null,
        val hasSeenSettingsTutorial: Boolean? = null,
        val totalMinutesPlayed: Double? = null,
        val totalSongsPlayed: Long? = null,
        val lastLogin: Long? = null
    )

    // Direct push methods for "Instant" updates in the console
    suspend fun pushFavorite(context: Context, song: FavoriteSongEntity) {
        val user = Firebase.auth.currentUser ?: return
        val db = Firebase.firestore
        val firestoreSong = FavoriteSongFirestore(
            song.videoId, song.title, song.artist, song.thumbnailUrl, song.artistId, song.timestamp
        )
        try {
            db.collection(USERS_COLLECTION).document(user.uid)
                .collection(FAVORITES_COLLECTION).document(song.videoId)
                .set(firestoreSong).await()
            Log.d(TAG, "Favorite pushed to library collection")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push favorite", e)
        }
    }

    suspend fun removeFavorite(videoId: String) {
        val user = Firebase.auth.currentUser ?: return
        val db = Firebase.firestore
        try {
            db.collection(USERS_COLLECTION).document(user.uid)
                .collection(FAVORITES_COLLECTION).document(videoId)
                .delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove favorite", e)
        }
    }

    suspend fun incrementPlayCount(context: Context, videoId: String, title: String, artist: String, thumbnailUrl: String, artistId: String?, durationMs: Long) {
        val user = Firebase.auth.currentUser ?: return
        val db = Firebase.firestore
        val userRef = db.collection(USERS_COLLECTION).document(user.uid)
        
        try {
            // 1. Update the 'most_played' sub-collection for full metadata
            val mostPlayedRef = userRef.collection(MOST_PLAYED_COLLECTION).document(videoId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(mostPlayedRef)
                if (snapshot.exists()) {
                    val currentCount = snapshot.getLong("playCount") ?: 0
                    transaction.update(mostPlayedRef, "playCount", currentCount + 1)
                    transaction.update(mostPlayedRef, "lastPlayedTimestamp", System.currentTimeMillis())
                } else {
                    val newEntry = MostPlayedFirestore(videoId, title, artist, thumbnailUrl, artistId, 1, System.currentTimeMillis())
                    transaction.set(mostPlayedRef, newEntry)
                }
                null
            }.await()

            // 2. Also update stats in the main document. 
            // We add the actual minutes played to totalMinutesPlayed.
            val minutesPlayed = (durationMs / 60000.0)
            userRef.update(
                "mostPlayedSummary.$videoId", com.google.firebase.firestore.FieldValue.increment(1),
                "totalSongsPlayed", com.google.firebase.firestore.FieldValue.increment(1),
                "totalMinutesPlayed", com.google.firebase.firestore.FieldValue.increment(minutesPlayed)
            ).await()
            
            Log.d(TAG, "Play count and minutes ($minutesPlayed) incremented in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment play count", e)
        }
    }

    suspend fun syncToFirebase(context: Context) {
        val user = Firebase.auth.currentUser ?: return
        val userId = user.uid
        val db = Firebase.firestore
        val localDb = AppDatabase.getDatabase(context)

        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            try {
                withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                    val settings = SettingsManager(context)
                    
                    // 1. Sync User Settings (Merge)
                    val userDataRef = db.collection(USERS_COLLECTION).document(userId)
                    
                    // Explicitly use a Map to guarantee field names match AdminPortal expectations
                    val userDataMap = mutableMapOf<String, Any?>(
                        "email" to user.email,
                        "displayName" to (user.displayName ?: settings.phoneNumber),
                        "phoneNumber" to settings.phoneNumber.ifEmpty { user.phoneNumber ?: "" },
                        "userLocation" to settings.userLocation,
                        "isLiquidGlass" to settings.isLiquidGlass,
                        "contentCountry" to settings.contentCountry,
                        "isPreferHighQuality" to settings.isPreferHighQuality,
                        "isNormalizeVolume" to settings.isNormalizeVolume,
                        "isSavePlaybackState" to settings.isSavePlaybackState,
                        "isSaveLastPlayed" to settings.isSaveLastPlayed,
                        "lastPlayedSongId" to settings.lastPlayedSongId,
                        "isKeepServiceAlive" to settings.isKeepServiceAlive,
                        "isCrossfadeEnabled" to settings.isCrossfadeEnabled,
                        "isSponsorBlockEnabled" to settings.isSponsorBlockEnabled,
                        "accentColor" to settings.accentColor,
                        "lyricsProvider" to settings.lyricsProvider,
                        "isOnboardingComplete" to settings.isOnboardingComplete,
                        "selectedLanguages" to settings.selectedLanguages.toList(),
                        "selectedArtists" to settings.selectedArtists.toList(),
                        "isKeepScreenOn" to settings.isKeepScreenOn,
                        "downloadQuality" to settings.downloadQuality,
                        "isSkipSilenceEnabled" to settings.isSkipSilenceEnabled,
                        "isVisualizerEnabled" to settings.isVisualizerEnabled,
                        "lastIgnoredVersion" to settings.lastIgnoredVersion,
                        "downloadThreads" to settings.downloadThreads,
                        "shouldShowLoginPrompt" to settings.shouldShowLoginPrompt,
                        "isPeakLimiterEnabled" to settings.isPeakLimiterEnabled,
                        "isGaplessRenderingEnabled" to settings.isGaplessRenderingEnabled,
                        "surroundMode" to settings.surroundMode,
                        "isDirectVolumeControlEnabled" to settings.isDirectVolumeControlEnabled,
                        "networkBufferSizeMs" to settings.networkBufferSizeMs,
                        "skipSponsorMentions" to settings.skipSponsorMentions,
                        "skipIntroOutro" to settings.skipIntroOutro,
                        "skipNonMusicSkits" to settings.skipNonMusicSkits,
                        "isDataSaverEnabled" to settings.isDataSaverEnabled,
                        "isAmbientBackgroundEnabled" to settings.isAmbientBackgroundEnabled,
                        "hasSeenPlayerTutorial" to settings.hasSeenPlayerTutorial,
                        "hasSeenLibraryTutorial" to settings.hasSeenLibraryTutorial,
                        "hasSeenSettingsTutorial" to settings.hasSeenSettingsTutorial,
                        "lastLogin" to System.currentTimeMillis()
                    )
                    
                    userDataRef.set(userDataMap, com.google.firebase.firestore.SetOptions.merge()).await()
                    AppLogger.debug("User data synced to cloud: ${user.email}")

                    // 2. Sync Favorites
                    val favorites = localDb.songDao().getAllFavorites().first()
                    val favoritesRef = db.collection(USERS_COLLECTION).document(userId).collection(FAVORITES_COLLECTION)
                    
                    // Firestore batches have a limit of 500 operations.
                    favorites.chunked(400).forEach { chunk ->
                        val batch = db.batch()
                        for (song in chunk) {
                            val firestoreSong = FavoriteSongFirestore(
                                song.videoId, song.title, song.artist, song.thumbnailUrl, song.artistId, song.timestamp
                            )
                            batch.set(favoritesRef.document(song.videoId), firestoreSong)
                        }
                        batch.commit().await()
                    }

                    // 3. Sync Playlists
                    val playlists = localDb.playlistDao().getAllPlaylists().first()
                    for (playlist in playlists) {
                        val playlistRef = db.collection(USERS_COLLECTION).document(userId)
                            .collection(PLAYLISTS_COLLECTION).document(playlist.id.toString())
                        
                        val firestorePlaylist = PlaylistFirestore(playlist.id, playlist.name, playlist.createdAt)
                        db.runBatch { batch ->
                            batch.set(playlistRef, firestorePlaylist)
                        }.await()

                        val songs = localDb.playlistDao().getSongsInPlaylist(playlist.id).first()
                        val songsRef = playlistRef.collection("songs") // Using literal string to ensure consistency
                        
                        songs.chunked(400).forEach { chunk ->
                            val batch = db.batch()
                            for (song in chunk) {
                                val firestoreSong = PlaylistSongFirestore(
                                    song.playlistId, song.videoId, song.title, song.artist, song.thumbnailUrl, song.artistId, song.addedAt
                                )
                                batch.set(songsRef.document(song.videoId), firestoreSong)
                            }
                            batch.commit().await()
                        }
                    }

                    // 4. Sync Most Played
                    val mostPlayed = localDb.songDao().getMostPlayedSongs().first()
                    val mostPlayedRef = db.collection(USERS_COLLECTION).document(userId).collection(MOST_PLAYED_COLLECTION)
                    mostPlayed.chunked(400).forEach { chunk ->
                        val batch = db.batch()
                        for (song in chunk) {
                            val firestoreSong = MostPlayedFirestore(
                                song.videoId, song.title, song.artist, song.thumbnailUrl, song.artistId, song.playCount, song.lastPlayedTimestamp
                            )
                            batch.set(mostPlayedRef.document(song.videoId), firestoreSong)
                        }
                        batch.commit().await()
                    }
                    
                    Log.d(TAG, "Data synced to Firebase successfully")
                } ?: Log.w(TAG, "Sync to Firebase timed out")
            } catch (e: Exception) {
                Log.e(TAG, "Sync to Firebase failed", e)
            }
        }
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun syncFromFirebase(context: Context) {
        val user = Firebase.auth.currentUser ?: return
        val userId = user.uid
        val db = Firebase.firestore
        val localDb = AppDatabase.getDatabase(context)

        withContext(Dispatchers.IO) {
            try {
                // READ-FIRST LOGIC: Check if cloud profile exists before doing anything
                val userDataSnapshot = db.collection(USERS_COLLECTION).document(userId).get().await()
                
                if (!userDataSnapshot.exists()) {
                    Log.d(TAG, "New user profile detected. Initializing cloud with local defaults.")
                    // If document doesn't exist, this is likely a first-time signup or a user with no cloud data.
                    // We push local data (if any) to cloud.
                    syncToFirebase(context)
                    return@withContext
                }

                Log.d(TAG, "Existing user profile found. Restoring settings and library from cloud.")

                // Check if email or displayName is missing in Firestore (Retroactive Sync)
                val cloudEmail = userDataSnapshot.getString("email")
                if (cloudEmail.isNullOrBlank()) {
                    Log.d(TAG, "Metadata missing in Firestore. Triggering retroactive sync...")
                    AppLogger.warn("Missing Firestore metadata for ${user.email}. Retroactive sync triggered.")
                    syncToFirebase(context)
                }

                val userData = userDataSnapshot.toObject(UserDataFirestore::class.java)
                if (userData != null) {
                    val settings = SettingsManager(context)
                    
                    // SELECTIVE RESTORE: Only overwrite local settings if cloud has an explicit value
                    // This prevents cloud defaults from wiping out user's local manual changes.
                    userData.isLiquidGlass?.let { settings.isLiquidGlass = it }
                    userData.contentCountry?.let { settings.contentCountry = it }
                    userData.userLocation?.let { settings.userLocation = it }
                    userData.isPreferHighQuality?.let { settings.isPreferHighQuality = it }
                    userData.isNormalizeVolume?.let { settings.isNormalizeVolume = it }
                    userData.isSavePlaybackState?.let { settings.isSavePlaybackState = it }
                    userData.isSaveLastPlayed?.let { settings.isSaveLastPlayed = it }
                    userData.lastPlayedSongId?.let { settings.lastPlayedSongId = it }
                    userData.isKeepServiceAlive?.let { settings.isKeepServiceAlive = it }
                    userData.isCrossfadeEnabled?.let { settings.isCrossfadeEnabled = it }
                    userData.isSponsorBlockEnabled?.let { settings.isSponsorBlockEnabled = it }
                    userData.accentColor?.let { settings.accentColor = it }
                    userData.lyricsProvider?.let { settings.lyricsProvider = it }
                    userData.phoneNumber?.let { settings.phoneNumber = it }

                    // ONBOARDING PROTECTION: If local is already complete, do NOT let cloud revert it to false.
                    if (userData.isOnboardingComplete == true) {
                        settings.isOnboardingComplete = true
                    }

                    if (!userData.selectedLanguages.isNullOrEmpty()) {
                        settings.selectedLanguages = userData.selectedLanguages.toSet()
                    }
                    if (!userData.selectedArtists.isNullOrEmpty()) {
                        settings.selectedArtists = userData.selectedArtists.toSet()
                    }
                    
                    userData.isKeepScreenOn?.let { settings.isKeepScreenOn = it }
                    userData.downloadQuality?.let { settings.downloadQuality = it }
                    userData.isSkipSilenceEnabled?.let { settings.isSkipSilenceEnabled = it }
                    userData.isVisualizerEnabled?.let { settings.isVisualizerEnabled = it }
                    userData.lastIgnoredVersion?.let { settings.lastIgnoredVersion = it }
                    userData.downloadThreads?.let { settings.downloadThreads = it }
                    userData.shouldShowLoginPrompt?.let { settings.shouldShowLoginPrompt = it }
                    userData.isPeakLimiterEnabled?.let { settings.isPeakLimiterEnabled = it }
                    userData.isGaplessRenderingEnabled?.let { settings.isGaplessRenderingEnabled = it }
                    userData.surroundMode?.let { settings.surroundMode = it }
                    userData.isDirectVolumeControlEnabled?.let { settings.isDirectVolumeControlEnabled = it }
                    userData.networkBufferSizeMs?.let { settings.networkBufferSizeMs = it }
                    userData.skipSponsorMentions?.let { settings.skipSponsorMentions = it }
                    userData.skipIntroOutro?.let { settings.skipIntroOutro = it }
                    userData.skipNonMusicSkits?.let { settings.skipNonMusicSkits = it }
                    userData.isDataSaverEnabled?.let { settings.isDataSaverEnabled = it }
                    userData.isAmbientBackgroundEnabled?.let { settings.isAmbientBackgroundEnabled = it }
                    userData.hasSeenPlayerTutorial?.let { settings.hasSeenPlayerTutorial = it }
                    userData.hasSeenLibraryTutorial?.let { settings.hasSeenLibraryTutorial = it }
                    userData.hasSeenSettingsTutorial?.let { settings.hasSeenSettingsTutorial = it }
                    
                    // Mark as synced as soon as settings are restored to unblock UI reactivity
                    markSynced(userId)
                    Log.d(TAG, "Settings restored. UI unblocked. Backgrounding library sync...")
                }

                // PARALLEL SYNC: Offload heavy library data to background coroutine
                syncScope.launch {
                    try {
                        withTimeoutOrNull(30.seconds) {
                            // 2. Sync Favorites (Library) from Firebase
                            val favoritesSnapshot = db.collection(USERS_COLLECTION).document(userId)
                                .collection(FAVORITES_COLLECTION).get().await()
                            
                            favoritesSnapshot.documents.forEach { doc ->
                                val fSong = doc.toObject(FavoriteSongFirestore::class.java)
                                if (fSong != null) {
                                    localDb.songDao().insertFavorite(FavoriteSongEntity(
                                        fSong.videoId, fSong.title, fSong.artist, fSong.thumbnailUrl, fSong.artistId, fSong.timestamp
                                    ))
                                }
                            }

                            // 3. Sync Playlists from Firebase
                            val playlistsSnapshot = db.collection(USERS_COLLECTION).document(userId)
                                .collection(PLAYLISTS_COLLECTION).get().await()

                            playlistsSnapshot.documents.forEach { doc ->
                                val fPlaylist = doc.toObject(PlaylistFirestore::class.java)
                                if (fPlaylist != null) {
                                    // Check for existing playlist to avoid duplicates
                                    var localId = localDb.playlistDao().getPlaylistByNameAndTimestamp(fPlaylist.name, fPlaylist.createdAt)?.id
                                    
                                    if (localId == null) {
                                        localId = localDb.playlistDao().createPlaylist(PlaylistEntity(
                                            name = fPlaylist.name, createdAt = fPlaylist.createdAt
                                        ))
                                    }
                                    
                                    // Sync Songs for this Playlist
                                    val songsSnapshot = doc.reference.collection("songs").get().await()
                                    songsSnapshot.documents.forEach { songDoc ->
                                        val fSong = songDoc.toObject(PlaylistSongFirestore::class.java)
                                        if (fSong != null) {
                                            localDb.playlistDao().addSongToPlaylist(PlaylistSongEntity(
                                                playlistId = localId,
                                                videoId = fSong.videoId,
                                                title = fSong.title,
                                                artist = fSong.artist,
                                                thumbnailUrl = fSong.thumbnailUrl,
                                                artistId = fSong.artistId,
                                                addedAt = fSong.addedAt
                                            ))
                                        }
                                    }
                                }
                            }

                            // 4. Sync Most Played from Firebase
                            val mostPlayedSnapshot = db.collection(USERS_COLLECTION).document(userId)
                                .collection(MOST_PLAYED_COLLECTION).get().await()
                            mostPlayedSnapshot.documents.forEach { doc ->
                                val fSong = doc.toObject(MostPlayedFirestore::class.java)
                                if (fSong != null) {
                                    localDb.songDao().insertMostPlayed(com.example.wavemuzix.data.database.MostPlayedSongEntity(
                                        fSong.videoId, fSong.title, fSong.artist, fSong.thumbnailUrl, fSong.artistId, fSong.playCount, fSong.lastPlayedTimestamp
                                    ))
                                }
                            }
                            Log.d(TAG, "Library restoration completed in background")
                        } ?: Log.w(TAG, "Library sync timed out")
                    } catch (e: Exception) {
                        Log.e(TAG, "Background library sync failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restoration from Firebase failed", e)
            }
        }
    }

}
