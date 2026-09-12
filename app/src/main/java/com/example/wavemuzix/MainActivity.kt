@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
package com.example.wavemuzix

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.materialSharedAxisZIn
import soup.compose.material.motion.animation.materialSharedAxisZOut
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle

import com.example.wavemuzix.ui.theme.WaveMuzixTheme
import com.example.wavemuzix.uiresource.components.MiniPlayerBar
import com.example.wavemuzix.uiresource.components.BottomNavBar
import com.example.wavemuzix.uiresource.components.DownloadNotificationOverlay
import com.example.wavemuzix.uiresource.components.AmbientFluidBackground
import com.example.wavemuzix.uiresource.components.LocalBackdrop
import com.example.wavemuzix.uiresource.components.LocalHazeState
import com.example.wavemuzix.uiresource.components.LocalPreferHaze
import com.example.wavemuzix.uiresource.components.UpdateDialog as UIUpdateDialog

import com.example.wavemuzix.uiresource.screens.SplashScreen
import com.example.wavemuzix.uiresource.screens.HomeScreen
import com.example.wavemuzix.uiresource.screens.SearchScreen
import com.example.wavemuzix.uiresource.screens.LibraryScreen
import com.example.wavemuzix.uiresource.screens.SettingsScreen
import com.example.wavemuzix.uiresource.screens.NotificationsScreen
import com.example.wavemuzix.uiresource.screens.PlaylistScreen
import com.example.wavemuzix.uiresource.screens.ArtistScreen
import com.example.wavemuzix.uiresource.screens.PlayerScreen
import com.example.wavemuzix.uiresource.screens.QueueScreen
import com.example.wavemuzix.uiresource.screens.LoginScreen
import com.example.wavemuzix.uiresource.screens.AdminPortalScreen
import com.example.wavemuzix.uiresource.screens.NotificationPushScreen
import com.example.wavemuzix.uiresource.screens.ManageAccountScreen
import com.example.wavemuzix.uiresource.screens.LegalDocsScreen
import com.example.wavemuzix.uiresource.screens.LegalConsentScreen
import com.example.wavemuzix.uiresource.screens.UpdatingScreen
import com.example.wavemuzix.uiresource.screens.LanguageSelectionScreen
import com.example.wavemuzix.uiresource.screens.ArtistSelectionScreen
import com.example.wavemuzix.uiresource.screens.LoginViewModel
import com.example.wavemuzix.uiresource.screens.MainViewModel

import com.example.wavemuzix.data.MusicPlayer
import com.example.wavemuzix.data.CookieManager
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.data.UpdateManager
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.data.RetrofitClient
import com.example.wavemuzix.data.LocalPerformanceConfig
import com.example.wavemuzix.utils.PerformanceUtils
import com.example.wavemuzix.repository.YouTubeRepository
import com.example.wavemuzix.repository.AudioStreamRepository
import com.example.wavemuzix.repository.UnifiedMusicRepository

import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val loginViewModel: LoginViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { token ->
                    loginViewModel.signInWithGoogle(this, token) {
                        android.widget.Toast.makeText(this, "Login successful!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }

        val repository = YouTubeRepository()
        val audioStreamRepository = AudioStreamRepository(repository)
        val unifiedRepository = UnifiedMusicRepository(repository, audioStreamRepository, RetrofitClient.artistMetadataRepository)
        
        MusicPlayer.unifiedRepository = unifiedRepository
        MusicPlayer.cookies = CookieManager(this).getCookies()

        mainViewModel.initialize(this)

        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager(context) }
            val performanceConfig = remember { PerformanceUtils.getPerformanceConfig(context) }
            
            var showSplash by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()

            val keepScreenOn = settingsManager.isKeepScreenOn
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val backdrop = rememberLayerBackdrop()
            val hazeState = remember { HazeState() }
            
            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalHazeState provides hazeState,
                LocalPerformanceConfig provides performanceConfig
            ) {
                WaveMuzixTheme(accentColor = mainViewModel.currentAccentColor) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        AnimatedContent(
                            targetState = showSplash,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(1000)) togetherWith
                                fadeOut(animationSpec = tween(800))
                            },
                            label = "SplashToMainTransition"
                        ) { isShowingSplash ->
                            val density = LocalDensity.current
                            val slideDistance = with(density) { 80.dp.roundToPx() }

                            if (isShowingSplash) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Ghost Warmup Layer: Placed behind the splash to ensure rendering
                                    Box(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.01f)) {
                                        AmbientFluidBackground(
                                            accentColors = listOf(mainViewModel.currentAccentColor),
                                            amplitudeProvider = { 0f },
                                            isPlayingProvider = { false }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .hazeSource(hazeState)
                                                .layerBackdrop(backdrop)
                                        )
                                    }

                                    SplashScreen(
                                        onFinished = { showSplash = false },
                                        onPreloadTrigger = { 
                                            mainViewModel.preWarmHomeData(context, unifiedRepository)
                                        }
                                    )
                                }
                            } else if (!mainViewModel.isOnboardingComplete) {
                                val onboardingNavController = rememberNavController()
                                NavHost(
                                    navController = onboardingNavController,
                                    startDestination = "language_selection",
                                    enterTransition = { materialSharedAxisXIn(forward = true, slideDistance = slideDistance, durationMillis = 700) },
                                    exitTransition = { materialSharedAxisXOut(forward = true, slideDistance = slideDistance, durationMillis = 700) },
                                    popEnterTransition = { materialSharedAxisXIn(forward = false, slideDistance = slideDistance, durationMillis = 700) },
                                    popExitTransition = { materialSharedAxisXOut(forward = false, slideDistance = slideDistance, durationMillis = 700) }
                                ) {
                                    composable("language_selection") {
                                        LanguageSelectionScreen(
                                            settingsManager = settingsManager,
                                            onContinue = { onboardingNavController.navigate("artist_selection") }
                                        )
                                    }
                                    composable("artist_selection") {
                                        ArtistSelectionScreen(
                                            unifiedRepository = unifiedRepository,
                                            settingsManager = settingsManager,
                                            onFinish = { 
                                                mainViewModel.isOnboardingComplete = true
                                                scope.launch {
                                                    if (com.google.firebase.ktx.Firebase.auth.currentUser != null) {
                                                        FirebaseSyncManager.syncToFirebase(context)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                            val navController = rememberNavController()
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route

                            val updateInfoState = produceState<UpdateManager.UpdateInfo?>(initialValue = null) {
                                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    packageInfo.longVersionCode.toInt()
                                } else {
                                    packageInfo.versionCode
                                }
                                
                                UpdateManager.getUpdateFlow(currentVersionCode).collect { info ->
                                    if (info != null && settingsManager.lastIgnoredVersion != info.version) {
                                        value = info
                                    } else {
                                        value = null
                                    }
                                }
                            }

                            var showUpdateDialog by remember { mutableStateOf(false) }
                            LaunchedEffect(updateInfoState.value) {
                                if (updateInfoState.value != null) {
                                    showUpdateDialog = true
                                }
                            }

                            if (showUpdateDialog && updateInfoState.value != null) {
                                UIUpdateDialog(
                                    newVersionName = updateInfoState.value!!.version.toString(),
                                    updateNotes = "New features and bug fixes",
                                    onDismiss = { showUpdateDialog = false },
                                    onUpdateClick = {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updateInfoState.value!!.url))
                                        context.startActivity(intent)
                                        showUpdateDialog = false
                                    },
                                    onIgnoreClick = {
                                        settingsManager.lastIgnoredVersion = updateInfoState.value!!.version
                                        showUpdateDialog = false
                                    },
                                    isLiquidGlass = mainViewModel.isLiquidGlass
                                )
                            }
                            
                            /* SharedTransitionLayout {
                                val sharedScope = this */
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .hazeSource(hazeState)
                                            .layerBackdrop(backdrop)
                                    ) {
                                        CompositionLocalProvider(
                                            LocalBackdrop provides null,
                                            LocalPreferHaze provides true
                                        ) {
                                            val tabs = listOf("home", "library", "search", "settings")

                                            NavHost(
                                                navController = navController,
                                                startDestination = "home",
                                                modifier = Modifier.fillMaxSize().graphicsLayer(clip = true),
                                                enterTransition = {
                                                    val initial = initialState.destination.route
                                                    val target = targetState.destination.route
                                                    val initialIndex = tabs.indexOf(initial)
                                                    val targetIndex = tabs.indexOf(target)

                                                    if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
                                                        materialSharedAxisXIn(
                                                            forward = targetIndex > initialIndex,
                                                            slideDistance = slideDistance,
                                                            durationMillis = 700
                                                        )
                                                    } else {
                                                        materialSharedAxisZIn(
                                                            forward = true,
                                                            durationMillis = 700
                                                        )
                                                    }
                                                },
                                                exitTransition = {
                                                    val initial = initialState.destination.route
                                                    val target = targetState.destination.route
                                                    val initialIndex = tabs.indexOf(initial)
                                                    val targetIndex = tabs.indexOf(target)

                                                    if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
                                                        materialSharedAxisXOut(
                                                            forward = targetIndex > initialIndex,
                                                            slideDistance = slideDistance,
                                                            durationMillis = 700
                                                        )
                                                    } else {
                                                        materialSharedAxisZOut(
                                                            forward = true,
                                                            durationMillis = 700
                                                        )
                                                    }
                                                },
                                                popEnterTransition = {
                                                    val initial = initialState.destination.route
                                                    val target = targetState.destination.route
                                                    val initialIndex = tabs.indexOf(initial)
                                                    val targetIndex = tabs.indexOf(target)

                                                    if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
                                                        materialSharedAxisXIn(
                                                            forward = targetIndex > initialIndex,
                                                            slideDistance = slideDistance,
                                                            durationMillis = 700
                                                        )
                                                    } else {
                                                        materialSharedAxisZIn(
                                                            forward = false,
                                                            durationMillis = 700
                                                        )
                                                    }
                                                },
                                                popExitTransition = {
                                                    val initial = initialState.destination.route
                                                    val target = targetState.destination.route
                                                    val initialIndex = tabs.indexOf(initial)
                                                    val targetIndex = tabs.indexOf(target)

                                                    if (initialIndex != -1 && targetIndex != -1 && initialIndex != targetIndex) {
                                                        materialSharedAxisXOut(
                                                            forward = targetIndex > initialIndex,
                                                            slideDistance = slideDistance,
                                                            durationMillis = 700
                                                        )
                                                    } else {
                                                        materialSharedAxisZOut(
                                                            forward = false,
                                                            durationMillis = 700
                                                        )
                                                    }
                                                }
                                            ) {
                                                composable("home") { 
                                                    HomeScreen(
                                                        repository = unifiedRepository,
                                                        onNotifyClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("notifications") },
                                                        onSettingsClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("settings") },
                                                        onPlaylistClick = { browseId -> if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("playlist/$browseId") },
                                                        onArtistClick = { artistId -> if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("artist/$artistId") },
                                                        onSongClick = { song ->
                                                            if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("player")
                                                            MusicPlayer.playSong(context, song, cookies = CookieManager(context).getCookies(), isFromFixedList = false)
                                                        },
                                                        onLoginClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("login") },
                                                        onManageAccountClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("manage_account") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass,
                                                        viewModel = mainViewModel
                                                    )
                                                }
                                                composable("search") { 
                                                    SearchScreen(
                                                        unifiedRepository = unifiedRepository,
                                                        cookies = CookieManager(context).getCookies(),
                                                        onPlaylistClick = { browseId -> if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("playlist/$browseId") },
                                                        onArtistClick = { artistId -> if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("artist/$artistId") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                                composable("library") { 
                                                    LibraryScreen(
                                                        unifiedRepository = unifiedRepository,
                                                        onPlaylistClick = { id ->
                                                            val route = if (id.startsWith("local_")) "playlist/${id.removePrefix("local_")}?isLocal=true" else "playlist/$id"
                                                            if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate(route)
                                                        },
                                                        onSearchClick = {
                                                            if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                                                                navController.navigate("search") {
                                                                    popUpTo("home") { saveState = true }
                                                                    launchSingleTop = true
                                                                    restoreState = true
                                                                }
                                                            }
                                                        },
                                                        onLoginClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("login") },
                                                        onManageAccountClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("manage_account") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    ) 
                                                }
                                                composable("settings") { 
                                                    SettingsScreen(
                                                        onAccentColorChange = { newColor ->
                                                            mainViewModel.currentAccentColor = newColor
                                                            mainViewModel.triggerUpdate(context)
                                                        },
                                                        onLiquidGlassChange = { enabled ->
                                                            mainViewModel.isLiquidGlass = enabled
                                                            mainViewModel.triggerUpdate(context)
                                                        },
                                                        onUpdateTrigger = { mainViewModel.triggerUpdate(context) },
                                                        onManageAccountClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("manage_account") },
                                                        onNavigateToPlayer = { if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navController.navigate("player") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    ) 
                                                }
                                                composable("notifications") { 
                                                    NotificationsScreen(
                                                        onBack = { if (!navController.popBackStack()) navController.navigate("home") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                                composable(
                                                    route = "playlist/{browseId}?isLocal={isLocal}",
                                                    arguments = listOf(
                                                        androidx.navigation.navArgument("browseId") { type = androidx.navigation.NavType.StringType },
                                                        androidx.navigation.navArgument("isLocal") { 
                                                            type = androidx.navigation.NavType.BoolType
                                                            defaultValue = false
                                                        }
                                                    )
                                                ) { backStackEntry ->
                                                    val browseId = backStackEntry.arguments?.getString("browseId") ?: ""
                                                    val isLocal = backStackEntry.arguments?.getBoolean("isLocal") ?: false
                                                    PlaylistScreen(
                                                        browseId = browseId,
                                                        isLocal = isLocal,
                                                        repository = unifiedRepository,
                                                        onBack = { if (!navController.popBackStack()) navController.navigate("home") },
                                                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                                composable("artist/{artistId}") { backStackEntry ->
                                                    val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                                                    ArtistScreen(
                                                        artistId = artistId,
                                                        repository = unifiedRepository,
                                                        onBack = { if (!navController.popBackStack()) navController.navigate("home") },
                                                        onSongClick = { song ->
                                                            navController.navigate("player")
                                                            MusicPlayer.playSong(context, song, cookies = CookieManager(context).getCookies(), isFromFixedList = false)
                                                        },
                                                        onAlbumClick = { albumId -> navController.navigate("playlist/$albumId") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                                composable("player") { 
                                                    PlayerScreen(
                                                        onBack = { if (!navController.popBackStack()) navController.navigate("home") },
                                                        onQueueClick = { navController.navigate("queue") },
                                                        onSearchClick = {
                                                            navController.navigate("search") {
                                                                popUpTo("home") { saveState = false }
                                                                launchSingleTop = true
                                                                restoreState = false
                                                            }
                                                        },
                                                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass,
                                                        backdrop = backdrop,
                                                        sharedTransitionScope = null,
                                                        animatedVisibilityScope = this
                                                    )
                                                }
                                                composable(
                                                    "queue",
                                                    enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(700, easing = EaseInOutQuart)) },
                                                    exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(700, easing = EaseInOutQuart)) },
                                                    popEnterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(700, easing = EaseInOutQuart)) },
                                                    popExitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(700, easing = EaseInOutQuart)) }
                                                ) {
                                                    QueueScreen(
                                                        isLiquidGlass = mainViewModel.isLiquidGlass,
                                                        onBack = { if (!navController.popBackStack()) navController.navigate("home") },
                                                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                                                        onPlaylistClick = { song ->
                                                            // Usually used for song selection in queue, renamed in parameter to follow usage
                                                            navController.navigate("player")
                                                            MusicPlayer.playSong(context, song, cookies = CookieManager(context).getCookies(), isFromFixedList = false)
                                                        },
                                                        sharedTransitionScope = null,
                                                        animatedVisibilityScope = this
                                                    )
                                                }
                                                composable("login") {
                                                    LoginScreen(
                                                        onLoginSuccess = { navController.navigate("legal_consent") { popUpTo("login") { inclusive = true } } },
                                                        onBack = { navController.popBackStack() },
                                                        onGoogleSignInClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass,
                                                        viewModel = loginViewModel
                                                    )
                                                }
                                                composable("admin_portal") {
                                                    AdminPortalScreen(
                                                        onBack = { navController.navigate("home") { popUpTo("admin_portal") { inclusive = true } } },
                                                        onPushNotificationClick = { navController.navigate("notification_push") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                                composable("notification_push") {
                                                    NotificationPushScreen(
                                                        onBack = { navController.popBackStack() },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                                composable("manage_account") {
                                                    ManageAccountScreen(
                                                        onBack = { navController.popBackStack() },
                                                        onAdminClick = { navController.navigate("admin_portal") },
                                                        onLegalClick = { navController.navigate("legal_docs/0") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass,
                                                        viewModel = loginViewModel
                                                    )
                                                }
                                                composable(
                                                    route = "legal_docs/{initialTab}",
                                                    arguments = listOf(androidx.navigation.navArgument("initialTab") { type = androidx.navigation.NavType.IntType; defaultValue = 0 })
                                                ) { backStackEntry ->
                                                    LegalDocsScreen(
                                                        onBack = { navController.popBackStack() },
                                                        initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0,
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                                composable("legal_consent") {
                                                    LegalConsentScreen(
                                                        onConfirm = {
                                                            val currentUser = com.google.firebase.ktx.Firebase.auth.currentUser
                                                            if (currentUser?.email == "wavemuzix@gmail.com") {
                                                                navController.navigate("admin_portal") { popUpTo("legal_consent") { inclusive = true } }
                                                            } else {
                                                                navController.navigate("home") { popUpTo("legal_consent") { inclusive = true } }
                                                                scope.launch {
                                                                    if (com.google.firebase.ktx.Firebase.auth.currentUser != null) {
                                                                        FirebaseSyncManager.syncToFirebase(context)
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onViewTerms = { navController.navigate("legal_docs/1") },
                                                        onViewPrivacy = { navController.navigate("legal_docs/0") },
                                                        isLiquidGlass = mainViewModel.isLiquidGlass
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = currentRoute != "player" && 
                                                 currentRoute != "queue" && 
                                                 currentRoute != "notifications" && 
                                                 currentRoute != "login" && 
                                                 currentRoute != "manage_account" && 
                                                 currentRoute != "admin_portal" && 
                                                 currentRoute != "notification_push",
                                        enter = fadeIn(tween(800, easing = EaseInOutQuart)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(800, easing = EaseInOutQuart)),
                                        exit = fadeOut(tween(800, easing = EaseInOutQuart)) + slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(800, easing = EaseInOutQuart)),
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            MiniPlayerBar(
                                                isLiquidGlass = mainViewModel.isLiquidGlass,
                                                sharedTransitionScope = null,
                                                animatedVisibilityScope = this@AnimatedVisibility,
                                                onClick = { navController.navigate("player") { launchSingleTop = true } }
                                            )
                                            BottomNavBar(
                                                navController = navController,
                                                isLiquidGlass = mainViewModel.isLiquidGlass
                                            )
                                        }
                                    }

                                    DownloadNotificationOverlay(
                                        onClick = { navController.navigate("notifications") { launchSingleTop = true } }
                                    )

                                    if (mainViewModel.isUpdatingSettings) {
                                        UpdatingScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        }
        return super.onKeyDown(keyCode, event)
    }
}
