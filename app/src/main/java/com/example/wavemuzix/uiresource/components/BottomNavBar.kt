package com.example.wavemuzix.uiresource.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.wavemuzix.data.LocalPerformanceConfig
import com.example.wavemuzix.data.MusicPlayer
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun BottomNavBar(navController: NavController, isLiquidGlass: Boolean) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val accentColor = MusicPlayer.accentColors.firstOrNull() ?: Color(0xFF1A1A1A)
    val scope = rememberCoroutineScope()
    val performance = LocalPerformanceConfig.current

    fun safeNavigate(route: String, builder: androidx.navigation.NavOptionsBuilder.() -> Unit) {
        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
            navController.navigate(route, builder)
        }
    }

    val pillHeight = 82.dp
    val bubbleSize = 68.dp
    val iconSize = 35.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isLiquidGlass) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.height(pillHeight)
                ) {
                    val pillNavItems = remember { listOf("home", "library") }
                    val selectedIndex = pillNavItems.indexOf(currentRoute)
                    val itemWidth = 90.dp
                    val density = LocalDensity.current
                    val itemWidthPx = with(density) { itemWidth.toPx() }

                    val indicatorOffset = remember { Animatable(selectedIndex.coerceAtLeast(0) * itemWidthPx) }
                    var isDragging by remember { mutableStateOf(false) }
                    var isPressed by remember { mutableStateOf(false) }

                    // Reactive "Blum Blum" liquid wave state (Horizontal ripple)
                    val liquidWave = remember { Animatable(0f) }
                    var lastOffsetValue by remember { mutableStateOf(indicatorOffset.value) }

                    LaunchedEffect(indicatorOffset.value) {
                        val currentOffset = indicatorOffset.value
                        val delta = currentOffset - lastOffsetValue
                        lastOffsetValue = currentOffset
                        
                        // "Kick" the liquid droplet based on movement velocity
                        // Divisor decreased to 0.4f for more reactive liquid kick
                        val velocityKick = (delta / 0.4f).coerceIn(-30f, 30f)
                        
                        if (Math.abs(delta) > 0.05f && !performance.isLowSpec) {
                            // Oscillate around 0 (the droplet's resting shape)
                            liquidWave.animateTo(
                                targetValue = 0f,
                                initialVelocity = velocityKick,
                                animationSpec = spring(
                                    dampingRatio = 0.15f, // More "jiggly" fluid feel
                                    stiffness = 800f      // Faster response
                                )
                            )
                        }
                    }

                    LaunchedEffect(selectedIndex) {
                        if (selectedIndex >= 0 && !isDragging) {
                            indicatorOffset.animateTo(
                                targetValue = selectedIndex * itemWidthPx,
                                animationSpec = spring(
                                    dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.3f,
                                    stiffness = if (performance.isLowSpec) performance.springStiffness else 700f
                                )
                            )
                        }
                    }

                    val isAnimating = indicatorOffset.isRunning
                    val indicatorAlpha by animateFloatAsState(
                        targetValue = if (selectedIndex >= 0 || isDragging) 1f else 0f,
                        animationSpec = tween(400),
                        label = "indicator_alpha"
                    )
                    val indicatorScale by animateFloatAsState(
                        targetValue = when {
                            isPressed -> 1.3f // Optimized for 82dp height
                            isDragging -> 1.02f
                            isAnimating -> 1.15f // Reduced to 1.15f to optimize GPU refraction load
                            else -> 1f
                        },
                        animationSpec = spring(
                            dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.4f,
                            stiffness = if (performance.isLowSpec) performance.springStiffness else 600f
                        ),
                        label = "indicator_scale"
                    )

                    // Stretch factor for "flowing" animation, replacing expensive indicatorWidthExtra Dp animations
                    val stretchFactor by animateFloatAsState(
                        targetValue = if (isAnimating && !isDragging) 0.15f else 0f,
                        animationSpec = spring(
                            dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.5f,
                            stiffness = if (performance.isLowSpec) performance.springStiffness else 1000f
                        ),
                        label = "indicator_stretch"
                    )

                    Box(
                        modifier = Modifier
                            .glassContainer(
                                shape = CircleShape,
                                isLiquidGlass = isLiquidGlass,
                                alphaTop = 0.08f, // Reduced from 0.12f
                                alphaBottom = 0.04f, // Reduced from 0.06f
                                borderAlphaTop = 0.25f,
                                borderAlphaBottom = 0.1f,
                                backgroundColor = accentColor
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Sliding Indicator
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .graphicsLayer {
                                    alpha = indicatorAlpha
                                    translationX = indicatorOffset.value

                                    if (!performance.isLowSpec) {
                                        // Lagging "Tail" offset to make it look like it's stretching behind
                                        val velocity = indicatorOffset.velocity
                                        val dragVelocity = (indicatorOffset.value - lastOffsetValue) * 60f
                                        val combinedVel = if (isAnimating) velocity else dragVelocity

                                        val tailLag = (combinedVel / 500f).coerceIn(-25f, 25f)
                                        translationX -= tailLag

                                        // Volume Preservation Logic (Physics-based liquid)
                                        // As it stretches horizontally (scaleX), it thins vertically (scaleY)
                                        val totalScaleX = indicatorScale * (1f + liquidWave.value * 0.8f + stretchFactor)
                                        scaleX = totalScaleX

                                        // Invert scaleY based on scaleX to preserve perceived volume
                                        scaleY = indicatorScale / (1f + (totalScaleX - 1f) * 0.4f)

                                        // Subtle fluid slosh rotation
                                        rotationZ = (combinedVel / 2000f).coerceIn(-5f, 5f)
                                        
                                        // Slight translation Y for floating feel
                                        translationY = if (isPressed || isDragging) -3f else 0f
                                    } else {
                                        // Simplified path for Low-Spec: Uniform scaling only, no complex physics
                                        scaleX = indicatorScale
                                        scaleY = indicatorScale
                                    }

                                    // Enforce oval clipping and offscreen strategy for the blur effect
                                    this.compositingStrategy = if (performance.useCompositingStrategyOffscreen) 
                                        CompositingStrategy.Offscreen else CompositingStrategy.Auto
                                    this.clip = true
                                    this.shape = CircleShape
                                }
                                .blur((0.1.dp * performance.blurRadiusMultiplier).coerceAtLeast(0.dp))
                                .size(width = itemWidth, height = pillHeight - 16.dp)
                                .glassContainer(
                                    shape = CircleShape,
                                    isLiquidGlass = isLiquidGlass,
                                    borderWidth = 1.2.dp,
                                    alphaTop = 0.12f,
                                    alphaBottom = 0.05f,
                                    borderAlphaTop = 0.4f,
                                    borderAlphaBottom = 0.2f,
                                    backgroundColor = accentColor.copy(alpha = 0.25f),
                                    clip = false
                                )
                        )

                        Row(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .height(pillHeight)
                                .pointerInput(pillNavItems) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        isPressed = true
                                        
                                        var dragTriggered = false
                                        val velocityTracker = VelocityTracker()
                                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                                        
                                        drag(down.id) { change ->
                                            dragTriggered = true
                                            isDragging = true
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val dragAmount = change.position.x - change.previousPosition.x
                                            
                                            scope.launch {
                                                val maxOffset = (pillNavItems.size - 1) * itemWidthPx
                                                val target = indicatorOffset.value + dragAmount
                                                
                                                // Strong rubber banding (Lotus leaf surface tension)
                                                val elastic = if (target < 0) {
                                                    target * 0.4f
                                                } else if (target > maxOffset) {
                                                    maxOffset + (target - maxOffset) * 0.4f
                                                } else {
                                                    target
                                                }
                                                indicatorOffset.snapTo(elastic)
                                            }
                                            change.consume()
                                        }
                                        
                                        isDragging = false
                                        isPressed = false
                                        
                                        if (dragTriggered) {
                                            val velocity = velocityTracker.calculateVelocity().x
                                            val currentVal = indicatorOffset.value
                                            val targetIndex = (currentVal / itemWidthPx).roundToInt()
                                                .coerceIn(0, pillNavItems.size - 1)
                                            val targetRoute = pillNavItems[targetIndex]
                                            
                                            scope.launch {
                                                indicatorOffset.animateTo(
                                                    targetValue = targetIndex * itemWidthPx,
                                                    initialVelocity = velocity,
                                                    animationSpec = spring(
                                                        dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.4f,
                                                        stiffness = if (performance.isLowSpec) performance.springStiffness else 900f
                                                    )
                                                )
                                                if (targetRoute != currentRoute) {
                                                    safeNavigate(targetRoute) {
                                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            NavItemWithLabel(
                                icon = Icons.Default.Home,
                                label = "Home",
                                isSelected = currentRoute == "home",
                                iconSize = iconSize,
                                enabled = true,
                                modifier = Modifier.width(itemWidth)
                            ) {
                                if (currentRoute != "home") {
                                    safeNavigate("home") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            NavItemWithLabel(
                                icon = Icons.AutoMirrored.Filled.List,
                                label = "Library",
                                isSelected = currentRoute == "library",
                                iconSize = iconSize,
                                enabled = true,
                                modifier = Modifier.width(itemWidth)
                            ) {
                                if (currentRoute != "library") {
                                    safeNavigate("library") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier.size(bubbleSize),
                    color = Color.Transparent,
                    shape = CircleShape
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassContainer(
                                shape = CircleShape,
                                isLiquidGlass = isLiquidGlass,
                                alphaTop = 0.12f,
                                alphaBottom = 0.06f,
                                borderAlphaTop = 0.3f,
                                borderAlphaBottom = 0.15f,
                                backgroundColor = accentColor
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                safeNavigate("search") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val searchIconColor by animateColorAsState(
                            targetValue = if (currentRoute == "search") MaterialTheme.colorScheme.primary else Color.White,
                            animationSpec = tween(500),
                            label = "search_icon_color"
                        )
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = searchIconColor,
                            modifier = Modifier.size(iconSize + 2.dp)
                        )
                    }
                }
            }
        } else {
            Surface(
                color = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .height(pillHeight)
                    .fillMaxWidth(),
                shadowElevation = 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f)), // Reduced from solid #1A1A1A
                    contentAlignment = Alignment.Center
                ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            NavItemWithLabel(
                                icon = Icons.Default.Home,
                                label = "Home",
                                isSelected = currentRoute == "home",
                                iconSize = iconSize,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                safeNavigate("home") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            NavItemWithLabel(
                                icon = Icons.AutoMirrored.Filled.List,
                                label = "Library",
                                isSelected = currentRoute == "library",
                                iconSize = iconSize,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                safeNavigate("library") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            NavItemWithLabel(
                                icon = Icons.Default.Search,
                                label = "Search",
                                isSelected = currentRoute == "search",
                                iconSize = iconSize,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                safeNavigate("search") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun NavItemWithLabel(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val performance = LocalPerformanceConfig.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.6f,
            stiffness = if (performance.isLowSpec) performance.springStiffness else 400f
        ),
        label = "nav_item_scale"
    )

    val animatedColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
        animationSpec = tween(500),
        label = "nav_item_color"
    )

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = if (performance.isLowSpec) performance.springDampingRatio else 0.5f,
            stiffness = if (performance.isLowSpec) performance.springStiffness else 400f
        ),
        label = "nav_item_selection_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale * selectionScale
                scaleY = scale * selectionScale
            }
            .clip(CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onClick() }
                )
            }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = animatedColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
