package com.example.wavemuzix.uiresource.screens

import com.example.wavemuzix.utils.AppLogger
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.uiresource.components.StandardLoader
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.example.wavemuzix.data.AppNotification
import com.example.wavemuzix.data.AppNotificationManager
import com.example.wavemuzix.data.NotificationType
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.uiresource.components.GlassIconButton
import com.example.wavemuzix.utils.ImageUtils
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun NotificationPushScreen(
    onBack: () -> Unit,
    isLiquidGlass: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var externalImageUrl by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) externalImageUrl = "" // Clear URL if file is picked
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Push", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        isLiquidGlass = isLiquidGlass,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = DeepBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject", color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Text("Image Options", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            OutlinedTextField(
                value = externalImageUrl,
                onValueChange = { 
                    externalImageUrl = it
                    if (it.isNotBlank()) selectedImageUri = null // Clear file if URL is entered
                },
                label = { Text("External Image URL (e.g. https://...)", color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Text("OR", color = Color.White.copy(alpha = 0.3f), modifier = Modifier.align(Alignment.CenterHorizontally))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    com.bumptech.glide.integration.compose.GlideImage(
                        model = selectedImageUri,
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (externalImageUrl.isNotBlank()) {
                    com.bumptech.glide.integration.compose.GlideImage(
                        model = externalImageUrl,
                        contentDescription = "URL Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                        Text("Upload from Gallery", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isBlank() || subject.isBlank()) {
                        Toast.makeText(context, "Title and Subject are required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        isUploading = true
                        try {
                            var finalImageUrl = externalImageUrl
                            
                            // EXTREME COMPRESSION & BASE64 (Option 1)
                            if (selectedImageUri != null) {
                                val base64String = ImageUtils.compressAndEncodeToBase64(context, selectedImageUri!!)
                                if (base64String != null) {
                                    finalImageUrl = base64String
                                } else {
                                    throw Exception("Image compression failed")
                                }
                            }

                            val notificationId = UUID.randomUUID().toString()
                            val notificationData = hashMapOf(
                                "id" to notificationId,
                                "title" to title,
                                "description" to subject,
                                "imageUrl" to finalImageUrl,
                                "type" to "INFO",
                                "timestamp" to System.currentTimeMillis()
                            )

                            Firebase.firestore.collection("notifications")
                                .document(notificationId)
                                .set(notificationData)
                                .await()

                            AppLogger.info("Admin pushed notification: $title")
                            Toast.makeText(context, "Notification Sent Successfully", Toast.LENGTH_LONG).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isUploading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isUploading
            ) {
                if (isUploading) {
                    StandardLoader(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Push Notification", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
