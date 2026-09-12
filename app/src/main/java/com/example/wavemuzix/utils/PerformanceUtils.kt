package com.example.wavemuzix.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.wavemuzix.data.PerformanceConfig

object PerformanceUtils {
    fun getPerformanceConfig(context: Context): PerformanceConfig {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        val isLowRam = activityManager.isLowRamDevice
        val sdk = Build.VERSION.SDK_INT
        
        /**
         * Flagship Detection Helper
         * Identifies true modern flagship hardware.
         */
        val isModernFlagship = cores >= 8 && sdk >= Build.VERSION_CODES.TIRAMISU

        return when {
            // 1. VERY LOW: < 3.2GB RAM or legacy OS (Android 9-).
            totalRamGb < 3.2 || isLowRam || sdk < Build.VERSION_CODES.Q -> {
                PerformanceConfig.VeryLow
            }
            
            // 2. LOW: Targets 4GB phones (~3.7GB reported). 
            // Threshold lowered to 5.0GB and condition refined to ensure 6GB phones (~5.4-5.8GB reported) are excluded.
            totalRamGb < 5.0 || (totalRamGb < 5.3 && sdk < Build.VERSION_CODES.R) -> {
                PerformanceConfig.Low
            }
            
            // 3. MID: Targets 6GB physical devices.
            // Correctly captures 6GB phones even if they have virtual RAM expansion up to 13.8GB,
            // as long as they aren't a modern flagship (8 cores + API 33+).
            totalRamGb < 7.5 || (totalRamGb < 13.8 && !isModernFlagship) -> {
                PerformanceConfig.Mid
            }
            
            // 4. BALANCED: Targets 8GB physical devices.
            totalRamGb < 10.5 || (totalRamGb < 17.5 && sdk < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) -> {
                PerformanceConfig.Balanced
            }
            
            // 5. HIGH: Targets 12GB physical flagships.
            totalRamGb < 15.8 -> {
                PerformanceConfig.High
            }
            
            // 6. BEAST: 16GB+ Physical RAM, Android 14+, and 8+ Cores.
            else -> {
                PerformanceConfig.Beast
            }
        }
    }
}
