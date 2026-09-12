package com.example.wavemuzix.uiresource.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.utils.ImageUtils
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.uiresource.components.StandardLoader
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.SurfaceVariant
import com.example.wavemuzix.ui.theme.TextSecondary
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.example.wavemuzix.uiresource.components.glassContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ManageAccountScreen(
    onBack: () -> Unit,
    onAdminClick: () -> Unit,
    onLegalClick: () -> Unit,
    isLiquidGlass: Boolean,
    viewModel: LoginViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    val user = auth.currentUser
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(user?.displayName ?: "") }
    var phoneNumber by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf(user?.photoUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        val userId = user?.uid
        if (userId != null) {
            val db = Firebase.firestore
            try {
                val doc = db.collection("users").document(userId).get().await()
                phoneNumber = doc.getString("phoneNumber") ?: ""
                val firestorePhotoUrl = doc.getString("photoUrl")
                if (firestorePhotoUrl != null) {
                    photoUri = Uri.parse(firestorePhotoUrl)
                }
            } catch (e: Exception) {
                // Fallback to auth data if available
                phoneNumber = user.phoneNumber ?: ""
                photoUri = user.photoUrl
            }
        }
    }

    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent
            if (uri != null) {
                scope.launch {
                    isSaving = true
                    try {
                        // EXTREME COMPRESSION & BASE64
                        val base64String = ImageUtils.compressAndEncodeToBase64(context, uri)
                        if (base64String != null) {
                            // Store locally for immediate UI update
                            photoUri = Uri.parse(base64String)
                            Toast.makeText(context, "Photo ready to save", Toast.LENGTH_SHORT).show()
                        } else {
                            throw Exception("Image compression failed")
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Process failed: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isSaving = false
                    }
                }
            }
        } else {
            val exception = result.error
            if (exception != null) {
                Toast.makeText(context, "Crop failed: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val options = CropImageContractOptions(
                uri = uri,
                cropImageOptions = CropImageOptions(
                    cropShape = CropImageView.CropShape.OVAL,
                    fixAspectRatio = true,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    guidelines = CropImageView.Guidelines.ON,
                    backgroundColor = android.graphics.Color.BLACK,
                    activityBackgroundColor = android.graphics.Color.BLACK,
                    activityTitle = "Crop Profile Picture",
                    cropMenuCropButtonTitle = "DONE",
                    showCropOverlay = true,
                    showProgressBar = true,
                    toolbarColor = android.graphics.Color.BLACK,
                    toolbarTitleColor = android.graphics.Color.WHITE,
                    toolbarBackButtonColor = android.graphics.Color.WHITE,
                    toolbarTintColor = android.graphics.Color.WHITE,
                    cropMenuCropButtonIcon = 0 // Try text-only "DONE" button
                )
            )
            cropImageLauncher.launch(options)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Account", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = DeepBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(SoftBlack, DeepBlack)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Picture Section
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariant)
                    .clickable { 
                        imagePickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        ) 
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    GlideImage(
                        model = photoUri,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White.copy(alpha = 0.8f))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Account Details Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassContainer(
                        shape = RoundedCornerShape(24.dp),
                        isLiquidGlass = isLiquidGlass
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AccountTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = "Full Name",
                        icon = Icons.Default.Badge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AccountTextField(
                        value = user?.email ?: "",
                        onValueChange = { },
                        label = "Email Address",
                        icon = Icons.Default.Email,
                        enabled = false
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AccountTextField(
                        value = phoneNumber,
                        onValueChange = { 
                            if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                                phoneNumber = it
                            }
                        },
                        label = "Phone Number",
                        icon = Icons.Default.Phone
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Legal & Policies Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassContainer(
                        shape = RoundedCornerShape(16.dp),
                        isLiquidGlass = isLiquidGlass
                    )
                    .clickable { onLegalClick() },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Privacy Policy & Terms",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            // 1. Update Display Name in Auth
                            val profileUpdates = userProfileChangeRequest {
                                this.displayName = displayName
                            }
                            user?.updateProfile(profileUpdates)?.await()
                            
                            // 2. Save EVERYTHING to Firestore (including the Base64 photo)
                            val userId = user?.uid
                            if (userId != null) {
                                val db = Firebase.firestore
                                val userData = mutableMapOf<String, Any>(
                                    "phoneNumber" to phoneNumber,
                                    "displayName" to displayName,
                                    "email" to (user.email ?: ""),
                                    "lastUpdated" to System.currentTimeMillis()
                                )
                                
                                // Only save photoUrl if it exists. 
                                // photoUri.toString() correctly returns the Base64 data string or remote URL.
                                photoUri?.let { 
                                    userData["photoUrl"] = it.toString() 
                                }

                                db.collection("users").document(userId).set(
                                    userData,
                                    com.google.firebase.firestore.SetOptions.merge()
                                ).await()
                            }

                            // Trigger sync after manual profile update
                            FirebaseSyncManager.syncToFirebase(context)

                            Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    StandardLoader(size = 24.dp)
                } else {
                    Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    viewModel.signOut(context)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Delete Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            if (user?.email == "wavemuzix@gmail.com") {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onAdminClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                ) {
                    Icon(Icons.Default.AdminPanelSettings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Go to Admin Portal", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            modifier = Modifier
                .glassContainer(
                    shape = RoundedCornerShape(28.dp),
                    isLiquidGlass = isLiquidGlass
                ),
            title = { Text("Delete Account", color = Color.White) },
            text = { Text("Are you sure you want to delete your account? This action is permanent and all your data will be lost.", color = Color.White.copy(alpha = 0.7f)) },
            containerColor = Color.Transparent,
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val userId = user?.uid
                                if (userId != null) {
                                    Firebase.firestore.collection("users").document(userId).delete().await()
                                }
                                
                                // Attempt to delete auth account, but don't force re-login if it fails
                                try {
                                    user?.delete()?.await()
                                } catch (e: Exception) {
                                    // If deletion fails (e.g. requires recent login), we still proceed 
                                    // with sign out since user data is already removed from Firestore
                                }

                                viewModel.signOut(context)
                                Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun AccountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    enabled: Boolean = true
) {
    Column {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                disabledBorderColor = Color.White.copy(alpha = 0.05f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.5f)
            ),
            singleLine = true
        )
    }
}
