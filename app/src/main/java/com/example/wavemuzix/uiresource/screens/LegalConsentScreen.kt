package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavemuzix.ui.theme.DeepBlack
import com.example.wavemuzix.ui.theme.SoftBlack
import com.example.wavemuzix.ui.theme.TextSecondary
import com.example.wavemuzix.uiresource.components.glassContainer

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun LegalConsentScreen(
    onConfirm: () -> Unit,
    onViewTerms: () -> Unit,
    onViewPrivacy: () -> Unit,
    isLiquidGlass: Boolean
) {
    var termsAccepted by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SoftBlack, DeepBlack)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 160.dp), // Increased bottom padding for Nav Bar
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "One Last Step",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Text(
            text = "Please review and accept our policies to continue using Wave Muzix.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Consent Cards
        ConsentItem(
            title = "Terms and Conditions",
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            onExpand = onViewTerms,
            icon = Icons.Default.Gavel,
            isLiquidGlass = isLiquidGlass
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConsentItem(
            title = "Privacy Policy",
            checked = privacyAccepted,
            onCheckedChange = { privacyAccepted = it },
            onExpand = onViewPrivacy,
            icon = Icons.Default.VerifiedUser,
            isLiquidGlass = isLiquidGlass
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = termsAccepted && privacyAccepted
        ) {
            Text("Confirm & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun ConsentItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onExpand: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLiquidGlass: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassContainer(
                shape = RoundedCornerShape(24.dp),
                isLiquidGlass = isLiquidGlass
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = Color.White.copy(alpha = 0.5f),
                    checkmarkColor = Color.White
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))

            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onExpand) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View Details",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
