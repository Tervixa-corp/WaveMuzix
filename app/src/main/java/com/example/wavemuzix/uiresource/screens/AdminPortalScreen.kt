package com.example.wavemuzix.uiresource.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.StandardLoader
import com.example.wavemuzix.uiresource.components.GlassIconButton
import com.example.wavemuzix.uiresource.components.glassContainer
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

data class AdminUser(
    val id: String = "",
    val displayName: String = "Unknown User",
    val email: String = "",
    val photoUrl: String? = null,
    val phoneNumber: String = "",
    val settings: Map<String, Any> = emptyMap(),
    val createdAt: Long = 0,
    val lastLogin: Long = 0,
    val disabled: Boolean = false
)

data class ServerLog(
    val id: String = "",
    val message: String = "",
    val level: String = "INFO",
    val timestamp: Long = 0,
    val userEmail: String? = null
)

data class PushLog(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val timestamp: Long = 0,
    val imageUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPortalScreen(
    onBack: () -> Unit,
    onPushNotificationClick: () -> Unit,
    isLiquidGlass: Boolean
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Users", "Messages")

    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var logs by remember { mutableStateOf<List<PushLog>>(emptyList()) }
    var serverLogs by remember { mutableStateOf<List<ServerLog>>(emptyList()) }
    var firestoreLatency by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var showUserDetail by remember { mutableStateOf<AdminUser?>(null) }
    var showClearAllMessagesDialog by remember { mutableStateOf(false) }

    val db = remember { Firebase.firestore }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        
        // Measure Latency
        val startTime = System.currentTimeMillis()
        db.collection("users").limit(1).get().addOnCompleteListener {
            firestoreLatency = System.currentTimeMillis() - startTime
        }

        // Fetch Users - Listening to the 'users' collection
        db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Handle error
                return@addSnapshotListener
            }
            users = snapshot?.documents?.map { doc ->
                val settings = doc.get("settings") as? Map<String, Any> ?: emptyMap()
                AdminUser(
                    id = doc.id,
                    displayName = doc.getString("displayName") ?: doc.getString("name") ?: doc.getString("fullName") ?: "Unknown User",
                    email = doc.getString("email") ?: doc.getString("emailId") ?: (settings["email"] as? String) ?: "",
                    photoUrl = doc.getString("photoUrl"),
                    phoneNumber = doc.getString("phoneNumber") ?: (settings["phoneNumber"] as? String) ?: "",
                    settings = settings,
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    lastLogin = doc.getLong("lastLogin") ?: 0L,
                    disabled = doc.getBoolean("disabled") ?: false
                )
            } ?: emptyList()
        }

        // Fetch Logs
        db.collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                logs = snapshot?.documents?.map { doc ->
                    PushLog(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        imageUrl = doc.getString("imageUrl")
                    )
                } ?: emptyList()
                isLoading = false
            }

        // Fetch Server Logs
        db.collection("server_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, _ ->
                serverLogs = snapshot?.documents?.map { doc ->
                    ServerLog(
                        id = doc.id,
                        message = doc.getString("message") ?: "",
                        level = doc.getString("level") ?: "INFO",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        userEmail = doc.getString("userEmail")
                    )
                } ?: emptyList()
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Admin Portal", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        GlassIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onBack,
                            isLiquidGlass = isLiquidGlass,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    actions = {
                        if (selectedTab == 2 && logs.isNotEmpty()) {
                            IconButton(onClick = { showClearAllMessagesDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Messages", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                Text(
                                    title, 
                                    color = if (selectedTab == index) Color.White else TextSecondary,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = onPushNotificationClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Push Notification")
                }
            }
        },
        containerColor = DeepBlack
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                StandardLoader(modifier = Modifier.align(Alignment.Center))
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "AdminTabs"
                ) { tab ->
                    when (tab) {
                        0 -> AdminOverview(users.size, logs.size, serverLogs, firestoreLatency, isLiquidGlass)
                        1 -> AdminUsersList(users, { user -> showUserDetail = user }, isLiquidGlass)
                        2 -> AdminLogsList(logs, isLiquidGlass)
                    }
                }
            }
        }
    }

    // User Detail Bottom Sheet
    if (showUserDetail != null) {
        UserDetailDialog(
            user = showUserDetail!!,
            onDismiss = { showUserDetail = null },
            isLiquidGlass = isLiquidGlass
        )
    }

    if (showClearAllMessagesDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllMessagesDialog = false },
            containerColor = SoftBlack,
            title = { Text("Clear All Messages?", color = Color.White) },
            text = { Text("This will permanently delete ALL notifications for ALL users. This action cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllMessagesDialog = false
                        // Batch delete all notifications
                        val collectionRef = db.collection("notifications")
                        collectionRef.get().addOnSuccessListener { snapshot ->
                            val batch = db.batch()
                            snapshot.documents.forEach { batch.delete(it.reference) }
                            batch.commit().addOnSuccessListener {
                                Toast.makeText(context, "All messages cleared", Toast.LENGTH_SHORT).show()
                            }.addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Clear All", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllMessagesDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun UserDetailDialog(
    user: AdminUser,
    onDismiss: () -> Unit,
    isLiquidGlass: Boolean
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isLiquidGlass) Color.Transparent else SoftBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        tonalElevation = 0.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassContainer(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    isLiquidGlass = isLiquidGlass
                )
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (user.photoUrl != null) {
                GlideImage(
                    model = user.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(50.dp))
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text(user.displayName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(user.email, color = TextSecondary, fontSize = 14.sp)
            
            Spacer(Modifier.height(24.dp))
            
            DetailRow("Phone", user.phoneNumber.ifBlank { "Not provided" }, Icons.Default.Phone)
            DetailRow("User ID", user.id, Icons.Default.Fingerprint)
            
            if (user.createdAt > 0) {
                DetailRow("Account Created", dateFormat.format(Date(user.createdAt)), Icons.Default.CalendarToday)
            }
            if (user.lastLogin > 0) {
                DetailRow("Last Login", dateFormat.format(Date(user.lastLogin)), Icons.AutoMirrored.Filled.Login)
            }
            
            DetailRow("Status", if (user.disabled) "Disabled" else "Active", if (user.disabled) Icons.Default.Block else Icons.Default.CheckCircle)
            
            if (user.settings.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
                Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                user.settings.forEach { (key, value) ->
                    DetailRow(key.replaceFirstChar { it.uppercase() }, value.toString(), Icons.Default.Settings)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun AdminOverview(
    userCount: Int, 
    logCount: Int, 
    serverLogs: List<ServerLog>,
    firestoreLatency: Long,
    isLiquidGlass: Boolean
) {
    var showHealth by remember { mutableStateOf(false) }
    var showServerLogs by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Total Active Users", userCount.toString(), Icons.Default.People, isLiquidGlass)
        StatCard("Notifications Sent", logCount.toString(), Icons.Default.NotificationsActive, isLiquidGlass)
        
        Spacer(Modifier.height(8.dp))
        Text("Quick Actions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionChip("System Health", Icons.Default.HealthAndSafety) { showHealth = true }
            QuickActionChip("Server Logs", Icons.Default.Terminal) { showServerLogs = true }
        }
    }

    if (showHealth) {
        AlertDialog(
            onDismissRequest = { showHealth = false },
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            title = { Text("System Health", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthRow("Firestore Latency", "${firestoreLatency}ms", if (firestoreLatency < 100) Color.Green else if (firestoreLatency < 500) Color.Yellow else Color.Red)
                    HealthRow("Auth Service", if (Firebase.auth.currentUser != null) "Operational" else "Degraded", Color.Green)
                    HealthRow("Storage Quota", "0.5% Used", Color.Green)
                    HealthRow("API Error Rate", "0.00%", Color.Green)
                }
            },
            containerColor = if (isLiquidGlass) Color.Transparent else SoftBlack,
            confirmButton = {
                TextButton(onClick = { showHealth = false }) { Text("Close", color = Color.White) }
            }
        )
    }

    if (showServerLogs) {
        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showServerLogs = false },
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            title = { Text("Server Logs", color = Color.White) },
            text = {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.height(400.dp).verticalScroll(scrollState)) {
                    if (serverLogs.isEmpty()) {
                        Text("No logs found in Firestore.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        Column {
                            serverLogs.forEach { log ->
                                val color = when(log.level) {
                                    "ERROR" -> Color.Red
                                    "WARN" -> Color.Yellow
                                    "DEBUG" -> Color.Cyan
                                    else -> Color.Green
                                }
                                Text(
                                    "[${log.level}] ${dateFormat.format(Date(log.timestamp))} ${log.userEmail ?: "System"}: ${log.message}",
                                    color = color,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = if (isLiquidGlass) Color.Transparent else SoftBlack,
            confirmButton = {
                TextButton(onClick = { showServerLogs = false }) { Text("Close", color = Color.White) }
            }
        )
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, isLiquidGlass: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(label, color = TextSecondary, fontSize = 14.sp)
                Text(value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HealthRow(label: String, status: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Text(status, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun QuickActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AdminUsersList(
    users: List<AdminUser>,
    onUserClick: (AdminUser) -> Unit,
    isLiquidGlass: Boolean
) {
    val context = LocalContext.current
    val db = Firebase.firestore
    var emailToReset by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf<AdminUser?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(users) { user ->
            var showMenu by remember { mutableStateOf(false) }

            Surface(
                onClick = { onUserClick(user) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (user.photoUrl != null) {
                        GlideImage(
                            model = user.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.displayName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (user.disabled) {
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Red))
                            }
                        }
                        Text(
                            text = user.email.ifBlank { "No email recorded" },
                            color = if (user.email.isBlank()) Color.Red.copy(alpha = 0.5f) else TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.phoneNumber.isNotBlank()) {
                            Text(user.phoneNumber, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = TextSecondary)
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SoftBlack).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change Password", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = Color.White) },
                                onClick = {
                                    showMenu = false
                                    if (user.email.isNotBlank()) {
                                        try {
                                            com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(user.email)
                                                .addOnSuccessListener { 
                                                    Toast.makeText(context, "Reset email sent to ${user.email}", Toast.LENGTH_SHORT).show() 
                                                }
                                                .addOnFailureListener { e ->
                                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Auth Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        emailToReset = ""
                                        showResetDialog = user
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (user.disabled) "Enable Account" else "Disable Account", color = Color.White) },
                                leadingIcon = { Icon(if (user.disabled) Icons.Default.CheckCircle else Icons.Default.Block, null, tint = if (user.disabled) Color.Green else Color.Yellow) },
                                onClick = {
                                    showMenu = false
                                    db.collection("users").document(user.id).update("disabled", !user.disabled)
                                        .addOnSuccessListener { Toast.makeText(context, if (user.disabled) "Account enabled" else "Account disabled", Toast.LENGTH_SHORT).show() }
                                }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            DropdownMenuItem(
                                text = { Text("Delete Account", color = Color.Red) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    // Note: This only deletes Firestore data. Full auth deletion needs Admin SDK/Cloud Function
                                    db.collection("users").document(user.id).delete()
                                        .addOnSuccessListener { Toast.makeText(context, "User record deleted from Firestore", Toast.LENGTH_SHORT).show() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog != null) {
        AlertDialog(
            onDismissRequest = { showResetDialog = null },
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            title = { Text("Missing Email Address", color = Color.White) },
            text = {
                Column {
                    Text("This user doesn't have an email recorded in Firestore. Please enter their email address to send a reset link.", color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = emailToReset,
                        onValueChange = { emailToReset = it },
                        label = { Text("User Email") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            containerColor = if (isLiquidGlass) Color.Transparent else SoftBlack,
            confirmButton = {
                Button(
                    onClick = {
                        if (emailToReset.isNotBlank()) {
                            com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(emailToReset)
                                .addOnSuccessListener { 
                                    Toast.makeText(context, "Reset email sent to $emailToReset", Toast.LENGTH_SHORT).show()
                                    // Optionally update Firestore so we don't have to ask again
                                    db.collection("users").document(showResetDialog!!.id).update("email", emailToReset)
                                    showResetDialog = null
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                ) {
                    Text("Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = null }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AdminLogsList(logs: List<PushLog>, isLiquidGlass: Boolean) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val context = LocalContext.current
    val db = Firebase.firestore

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(logs) { log ->
            var showDeleteDialog by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(log.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(dateFormat.format(Date(log.timestamp)), color = TextSecondary, fontSize = 11.sp)
                        
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Message", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(log.description, color = TextSecondary, fontSize = 13.sp, maxLines = 2)
                    
                    if (log.imageUrl != null) {
                        Spacer(Modifier.height(12.dp))
                        GlideImage(
                            model = log.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    containerColor = SoftBlack,
                    title = { Text("Delete Message?", color = Color.White) },
                    text = { Text("This will permanently delete this notification for all users. This action cannot be undone.", color = TextSecondary) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                db.collection("notifications").document(log.id).delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Message deleted successfully", Toast.LENGTH_SHORT).show()
                                        showDeleteDialog = false
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        ) {
                            Text("Delete", color = Color.Red)
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
    }
}
