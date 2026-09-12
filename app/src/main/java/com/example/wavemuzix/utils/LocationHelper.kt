package com.example.wavemuzix.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.util.Locale

object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun getCountryCode(context: Context): String? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()

            location?.let {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                val countryCode = addresses?.firstOrNull()?.countryCode
                Log.d("LocationHelper", "Detected Country Code: $countryCode")
                countryCode
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "Error getting location", e)
            null
        }
    }

    suspend fun getRegionName(context: Context): String? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()

            location?.let {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                val adminArea = addresses?.firstOrNull()?.adminArea // State/Region
                Log.d("LocationHelper", "Detected Region: $adminArea")
                adminArea
            }
        } catch (e: Exception) {
            null
        }
    }
}
