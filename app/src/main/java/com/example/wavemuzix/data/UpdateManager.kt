package com.example.wavemuzix.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wavemuzix.uiresource.components.glassContainer
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object UpdateManager {
    private const val CONFIG_COLLECTION = "app_config"
    private const val UPDATE_DOCUMENT = "update_info"

    fun getUpdateFlow(currentVersion: Int): Flow<UpdateInfo?> = callbackFlow {
        val registration = Firebase.firestore.collection(CONFIG_COLLECTION).document(UPDATE_DOCUMENT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val latestVersion = when (val v = snapshot.get("latest_version")) {
                        is Long -> v.toInt()
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        else -> 0
                    }
                    val updateUrl = snapshot.getString("update_url") ?: "https://github.com/yourusername/WaveMuzix/releases/latest"
                    
                    android.util.Log.d("UpdateManager", "Firestore version: $latestVersion, Current: $currentVersion")
                    if (latestVersion > currentVersion) {
                        trySend(UpdateInfo(latestVersion, updateUrl))
                    } else {
                        trySend(null)
                    }
                } else {
                    android.util.Log.d("UpdateManager", "Snapshot does not exist or is null")
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    data class UpdateInfo(
        val version: Int,
        val url: String
    )
}

@Composable
fun UpdateDialog(updateInfo: UpdateManager.UpdateInfo, isLiquidGlass: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var dontShowAgain by remember { mutableStateOf(false) }
    val settingsManager = remember { SettingsManager(context) }
    // Note: This UpdateDialog is a secondary implementation. 
    // The main one is in com.example.wavemuzix.uiresource.components.UpdateDialog
    // We'll keep this one simple but add a basic glass effect if needed, 
    // though the main app uses the other one.

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.then(
            if (isLiquidGlass) {
                // for refraction contact the glasscomponents.kt
                Modifier.glassContainer(shape = RoundedCornerShape(28.dp))
            } else Modifier
        ),
        title = { Text("Update Available", color = androidx.compose.ui.graphics.Color.White) },
        text = {
            Column {
                Text("A new version of WaveMuzix is available. Would you like to update now?", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            uncheckedColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                        )
                    )
                    Text("Don't show again", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                }
            }
        },
        containerColor = if (isLiquidGlass) androidx.compose.ui.graphics.Color.Transparent else com.example.wavemuzix.ui.theme.SoftBlack,
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.url))
                context.startActivity(intent)
                onDismiss()
            }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (dontShowAgain) {
                    settingsManager.lastIgnoredVersion = updateInfo.version
                }
                onDismiss()
            }) {
                Text("Cancel", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    )
}
