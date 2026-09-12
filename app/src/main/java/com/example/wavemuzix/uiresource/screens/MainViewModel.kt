package com.example.wavemuzix.uiresource.screens

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wavemuzix.data.CookieManager
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.repository.HomeResponse
import com.example.wavemuzix.repository.HomeShelf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.wavemuzix.repository.UnifiedMusicRepository
import com.example.wavemuzix.model.Song
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.example.wavemuzix.data.AppNotification
import com.example.wavemuzix.data.AppNotificationManager
import com.example.wavemuzix.data.NotificationType
import com.example.wavemuzix.data.UpdateManager
import com.bumptech.glide.Glide
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    private val _homeData = MutableStateFlow<HomeResponse?>(null)
    val homeData: StateFlow<HomeResponse?> = _homeData

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Persistent UI State to prevent transition lag
    val shelves = mutableStateListOf<HomeShelf>()
    val quickPicks = mutableStateListOf<Song>()
    val trending = mutableStateListOf<Song>()
    var continuationToken by mutableStateOf<String?>(null)
    var isLoadingMore by mutableStateOf(false)

    private var userLocation: String? = null

    fun setUserLocation(context: Context, location: String?) {
        userLocation = location
        if (location != null) {
            SettingsManager(context).userLocation = location
        }
    }

    var currentAccentColor by mutableStateOf(Color(0xFFFF0000))
    var isLiquidGlass by mutableStateOf(true)
    var isAmbientBackgroundEnabled by mutableStateOf(true)
    var isOnboardingComplete by mutableStateOf(false)
    var isUpdatingSettings by mutableStateOf(false)
    
    // Persistent Metadata state
    var userName by mutableStateOf<String?>(null)
    var userAvatarUrl by mutableStateOf<String?>(null)
    private var isFirebaseListenersInitialized = false
    
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        // STAGE 1: Immediate UI-critical settings
        val settingsManager = SettingsManager(context)
        currentAccentColor = Color(settingsManager.accentColor)
        isLiquidGlass = settingsManager.isLiquidGlass
        isAmbientBackgroundEnabled = settingsManager.isAmbientBackgroundEnabled
        isOnboardingComplete = settingsManager.isOnboardingComplete
        userLocation = settingsManager.userLocation

        // OFFLINE CACHE: Load previous Home data immediately to eliminate "white screen" lag
        settingsManager.homeFeedCache?.let { cachedJson ->
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val gson = Gson()
                    val cachedResponse = gson.fromJson(cachedJson, HomeResponse::class.java)
                    
                    // Critical: When deserializing List<Any>, Gson turns them into Maps.
                    // We need to keep the UI safe by ensuring the persistent lists are populated correctly.
                    val fixedShelves = cachedResponse.shelves.map { shelf ->
                        if (shelf.type == "songs" || shelf.type == "videos" || shelf.type == "trending") {
                            val fixedItems = shelf.items.map { item ->
                                if (item is Map<*, *>) {
                                    try {
                                        val songJson = gson.toJson(item)
                                        gson.fromJson(songJson, Song::class.java)
                                    } catch (e: Exception) { item }
                                } else item
                            }
                            shelf.copy(items = fixedItems)
                        } else shelf
                    }

                    withContext(Dispatchers.Main) {
                        quickPicks.clear()
                        quickPicks.addAll(cachedResponse.quickPicks)
                        trending.clear()
                        trending.addAll(cachedResponse.trending)
                        shelves.clear()
                        shelves.addAll(fixedShelves)
                        continuationToken = cachedResponse.continuation
                        _homeData.value = cachedResponse.copy(shelves = fixedShelves)
                        Log.d("MainViewModel", "Loaded Home Feed from Offline Cache (Refined Deserialization)")
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to parse Home cache", e)
                }
            }
        }

        viewModelScope.launch {
            // STAGE 2: Firebase Auth & Sync (Deferred slightly to prioritize first frame)
            kotlinx.coroutines.delay(800)
            
            val auth = Firebase.auth
            userName = auth.currentUser?.displayName
            userAvatarUrl = auth.currentUser?.photoUrl?.toString()

            if (auth.currentUser != null) {
                FirebaseSyncManager.syncFromFirebase(context)
                // Refresh local state after sync
                currentAccentColor = Color(settingsManager.accentColor)
                isLiquidGlass = settingsManager.isLiquidGlass
                isAmbientBackgroundEnabled = settingsManager.isAmbientBackgroundEnabled
                isOnboardingComplete = settingsManager.isOnboardingComplete
                userLocation = settingsManager.userLocation
                
                Firebase.firestore.collection("users").document(auth.currentUser!!.uid).get()
                    .addOnSuccessListener { doc ->
                        userName = doc.getString("displayName") ?: auth.currentUser?.displayName
                        userAvatarUrl = doc.getString("photoUrl") ?: auth.currentUser?.photoUrl?.toString()
                    }
            }
            
            // STAGE 3: Heavy Listeners (Deferred further)
            kotlinx.coroutines.delay(2000)
            initFirebaseListeners(context)
        }
    }

    private fun initFirebaseListeners(context: Context) {
        if (isFirebaseListenersInitialized) return
        isFirebaseListenersInitialized = true

        // 1. Version Check Logic
        val database = Firebase.database("https://wavemuzix-d4e151a6-default-rtdb.firebaseio.com")
        val versionRef = database.getReference("app_update")
        
        versionRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Update checking logic is handled via UpdateManager in MainActivity, 
                // but we can keep basic metadata here if needed.
            }
        }

        // 2. Real-time Notifications Check
        val db = Firebase.firestore
        db.collection("notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                snapshot?.documentChanges?.forEach { dc ->
                    val doc = dc.document
                    val id = doc.getString("id") ?: doc.id
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val notification = AppNotification(
                            id = id,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            type = try { NotificationType.valueOf(doc.getString("type") ?: "INFO") } catch(ex: Exception) { NotificationType.INFO },
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                        if (notification.id.isNotEmpty()) {
                            AppNotificationManager.addNotification(notification)
                        }
                    } else if (dc.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                        AppNotificationManager.removeNotification(id)
                    }
                }
            }
    }

    fun preWarmHomeData(context: Context, unifiedRepository: UnifiedMusicRepository) {
        if (_homeData.value != null || _isLoadingHome.value) return

        viewModelScope.launch {
            _isLoadingHome.value = true
            try {
                // Initialize settings first to ensure correct languages and accent colors are used
                val settingsManager = SettingsManager(context)
                val cookieManager = CookieManager(context)
                
                // Warm up heavy UI-related resources in background
                withContext(Dispatchers.Default) {
                    // Forces the initialization of heavy singleton components non-blockingly
                    val player = MusicPlayer
                    val streamRepo = unifiedRepository.audioStreamRepository
                    
                    // Pre-initialize internal states to avoid first-use lag
                    player.isLoading
                    
                    // Artificial delay to allow the Splash animation to breathe
                    kotlinx.coroutines.delay(300)
                }

                val response = withContext(Dispatchers.IO) {
                    unifiedRepository.fetchHome(
                        cookieManager.getCookies(),
                        settingsManager.selectedLanguages,
                        userLocation ?: settingsManager.userLocation
                    )
                }
                
                // Update persistent lists directly
                quickPicks.clear()
                quickPicks.addAll(response.quickPicks)
                trending.clear()
                trending.addAll(response.trending)
                shelves.clear()
                shelves.addAll(response.shelves)
                continuationToken = response.continuation
                
                if (userName == null) userName = response.userName
                if (userAvatarUrl == null) userAvatarUrl = response.userAvatarUrl
                
                _homeData.value = response
                
                // Final Performance Optimization: Pre-load top thumbnails into memory cache
                preloadImages(context, response)

                // Cache the response to disk for the next startup
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        settingsManager.homeFeedCache = Gson().toJson(response)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to cache Home data", e)
                    }
                }

                // Signal that we are ready for the transition
                Log.d("MainViewModel", "Pre-warming complete: ${response.shelves.size} shelves loaded.")
            } catch (e: Exception) {
                // Handle error or silent fail for pre-warming
            } finally {
                _isLoadingHome.value = false
            }
        }
    }

    fun refreshFeed(context: Context, unifiedRepository: UnifiedMusicRepository) {
        viewModelScope.launch {
            _isRefreshing.value = true
            Log.d("MainViewModel", "Refreshing feed...")
            try {
                val settingsManager = SettingsManager(context)
                val cookieManager = CookieManager(context)
                
                val response = withContext(Dispatchers.IO) {
                    Log.d("MainViewModel", "Fetching home data from Unified Repository...")
                    unifiedRepository.fetchHome(
                        cookieManager.getCookies(),
                        settingsManager.selectedLanguages,
                        userLocation ?: settingsManager.userLocation
                    )
                }
                
                quickPicks.clear()
                quickPicks.addAll(response.quickPicks)
                trending.clear()
                trending.addAll(response.trending)
                shelves.clear()
                shelves.addAll(response.shelves)
                continuationToken = response.continuation
                
                if (userName == null) userName = response.userName
                if (userAvatarUrl == null) userAvatarUrl = response.userAvatarUrl
                
                Log.d("MainViewModel", "Feed refreshed. Shelves: ${response.shelves.size}")
                _homeData.value = response

                // Refresh memory cache for new thumbnails
                preloadImages(context, response)

                // Update Offline Cache
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        SettingsManager(context).homeFeedCache = Gson().toJson(response)
                    } catch (e: Exception) { /* Silent fail for cache */ }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Refresh failed: ${e.message}", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore(context: Context, repository: UnifiedMusicRepository) {
        if (continuationToken == null || isLoadingMore || _isRefreshing.value) return
        
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val settingsManager = SettingsManager(context)
                val cookieManager = CookieManager(context)
                val response = withContext(Dispatchers.IO) {
                    repository.fetchHomeContinuation(
                        continuationToken!!,
                        cookieManager.getCookies(),
                        settingsManager.selectedLanguages,
                        userLocation ?: settingsManager.userLocation
                    )
                }
                shelves.addAll(response.shelves)
                continuationToken = response.continuation
            } catch (e: Exception) {
                Log.e("MainViewModel", "Load more failed: ${e.message}")
            } finally {
                isLoadingMore = false
            }
        }
    }

    private var updateJob: kotlinx.coroutines.Job? = null
    fun triggerUpdate(context: Context) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            isUpdatingSettings = true
            
            // Re-sync reactive states from SettingsManager
            val settingsManager = SettingsManager(context)
            currentAccentColor = Color(settingsManager.accentColor)
            isLiquidGlass = settingsManager.isLiquidGlass
            isAmbientBackgroundEnabled = settingsManager.isAmbientBackgroundEnabled

            kotlinx.coroutines.delay(1000)
            isUpdatingSettings = false
        }
    }

    private fun preloadImages(context: Context, response: HomeResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            val songsToPreload = mutableListOf<Song>()
            songsToPreload.addAll(response.quickPicks)
            songsToPreload.addAll(response.trending)
            response.shelves.forEach { shelf ->
                songsToPreload.addAll(shelf.items.filterIsInstance<Song>())
            }

            // Target the top 20 images to ensure the first few scrolls are "butter-smooth"
            songsToPreload.distinctBy { it.videoId }.take(20).forEach { song ->
                if (song.thumbnailUrl.isNotBlank()) {
                    try {
                        Glide.with(context.applicationContext)
                            .load(song.thumbnailUrl)
                            .preload()
                    } catch (e: Exception) {
                        // Fail silently if Glide initialization isn't ready
                    }
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Ensure any heavy sync or data fetch is cancelled
        FirebaseSyncManager.resetSyncStatus()
    }
}
