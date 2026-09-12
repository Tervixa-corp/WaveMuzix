package com.example.wavemuzix.uiresource.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.data.LocalPerformanceConfig
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.uiresource.components.glassContainer
import com.example.wavemuzix.utils.PerformanceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.util.Locale

@Composable
fun HardwareOptimizationScreen(
    onBack: () -> Unit,
    isLiquidGlass: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableFloatStateOf(0f) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var analysisStep by remember { mutableStateOf("") }
    
    val memoryInfo = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi
    }

    val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    val totalRam = String.format(Locale.US, "%.2f GB", totalRamGb)
    val availableRam = String.format(Locale.US, "%.2f GB", memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0))
    val cores = Runtime.getRuntime().availableProcessors()
    val model = Build.MODEL
    val manufacturer = Build.MANUFACTURER
    val androidVersion = Build.VERSION.RELEASE
    val performanceTier = PerformanceUtils.getPerformanceConfig(context)

    fun startOptimization() {
        scope.launch {
            isAnalyzing = true
            analysisProgress = 0f
            
            val steps = listOf(
                "Analyzing CPU Architecture...",
                "Benchmarking Memory Throughput...",
                "Evaluating GPU Shader Capability...",
                "Determining Optimal UI Tier...",
                "Configuring Liquid Glass Pipeline...",
                "Optimization Complete!"
            )

            steps.forEachIndexed { index, step ->
                analysisStep = step
                val targetProgress = (index + 1f) / steps.size
                animate(
                    initialValue = analysisProgress,
                    targetValue = targetProgress,
                    animationSpec = tween(800, easing = LinearEasing)
                ) { value, _ ->
                    analysisProgress = value
                }
                delay(400)
            }
            
            delay(500)
            isAnalyzing = false
            showRestartDialog = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Hardware Engine",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Analysis Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isAnalyzing) "OPTIMIZING ENGINE" else "SYSTEM STATUS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isAnalyzing) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                            CircularProgressIndicator(
                                progress = { analysisProgress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFF222222)
                            )
                            Text(
                                text = "${(analysisProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = analysisStep,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Improved tier detection label logic for expanded tiers
                        val tierName = when (performanceTier.maxVisualizerComplexity) {
                            in 0..20 -> "Very Low"
                            in 21..40 -> "Low"
                            in 41..60 -> "Mid"
                            in 61..80 -> "Balanced"
                            in 81..95 -> "High"
                            else -> "Beast"
                        }
                        val tierColor = when (tierName) {
                            "Beast" -> Color(0xFFFF00FF) // Magenta for Beast
                            "High" -> Color(0xFF00FF88)
                            "Balanced" -> Color(0xFF00CCFF)
                            "Mid" -> Color(0xFFFFCC00)
                            "Low" -> Color(0xFFFF8800)
                            else -> Color(0xFFFF4444)
                        }
                        
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = tierColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$tierName Performance Mode",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Hardware optimized for this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Device Specs
            Text(
                text = "Device Specifications",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )

            SpecItem("Model", "$manufacturer $model", Icons.Default.Smartphone)
            SpecItem("Android Version", "Android $androidVersion", Icons.Default.Android)
            SpecItem("Processor", "$cores Cores detected", Icons.Default.Memory)
            SpecItem("Total RAM", totalRam, Icons.Default.Storage)
            SpecItem("Available RAM", availableRam, Icons.Default.Refresh)

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = { startOptimization() },
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RUN HARDWARE CHECK",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showRestartDialog) {
            AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = { Text("Optimization Complete", color = Color.White) },
                text = { 
                    Text(
                        "Wave Muzix has been tailored to your device hardware. Please restart the app to apply all high-level engine changes.",
                        color = Color.White.copy(alpha = 0.7f)
                    ) 
                },
                confirmButton = {
                    TextButton(onClick = { 
                        // Restart app logic
                        val activity = context as? android.app.Activity
                        activity?.finishAffinity()
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        context.startActivity(intent)
                    }) {
                        Text("RESTART NOW", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartDialog = false }) {
                        Text("LATER", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
fun SpecItem(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF161616), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF222222), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
