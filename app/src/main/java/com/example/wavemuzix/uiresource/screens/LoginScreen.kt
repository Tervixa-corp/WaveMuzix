package com.example.wavemuzix.uiresource.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wavemuzix.R
import com.example.wavemuzix.ui.theme.*
import com.example.wavemuzix.uiresource.components.StandardLoader
import com.example.wavemuzix.uiresource.components.glassContainer
import com.example.wavemuzix.uiresource.components.GlassIconButton

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    isLiquidGlass: Boolean,
    viewModel: LoginViewModel = viewModel()
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isForgotMode by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val user by viewModel.user.collectAsState()
    val showOtpScreen by viewModel.showOtpScreen.collectAsState()
    val isForgotPasswordMode by viewModel.isForgotPasswordMode.collectAsState()
    val canResetPassword by viewModel.canResetPassword.collectAsState()
    val resendTimer by viewModel.resendTimer.collectAsState()
    
    var otpValue by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(user) {
        if (user != null) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        // Decorative Blobs
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .background(AccentPrimary.copy(alpha = 0.2f), CircleShape)
                .blur(25.dp)
        )
        
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-50).dp, y = 100.dp)
                .background(AccentSecondary.copy(alpha = 0.2f), CircleShape)
                .blur(25.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = {
                        if (showOtpScreen) {
                            viewModel.cancelOtp()
                        } else if (isForgotMode) {
                            isForgotMode = false
                        } else {
                            onBack()
                        }
                    },
                    isLiquidGlass = isLiquidGlass,
                    contentDescription = "Back"
                )
            }

            if (showOtpScreen) {
                // OTP SCREEN UI
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = if (isForgotPasswordMode) "Verify Identity" else "Enter OTP",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter the OTP sent to '${if (isForgotPasswordMode) email.ifBlank { forgotEmail } else email}' To verify your account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = AccentPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .border(2.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "IMPORTANT",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CHECK YOUR SPAM FOLDER! IF NOT IN INBOX",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isLiquidGlass) {
                                    Modifier.glassContainer(RoundedCornerShape(28.dp))
                                } else {
                                    Modifier
                                        .background(SurfaceVariant, RoundedCornerShape(28.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
                                }
                            ),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LoginTextField(
                                value = otpValue,
                                onValueChange = { if (it.length <= 6) otpValue = it },
                                label = "Verification Code",
                                icon = Icons.Default.VpnKey,
                                keyboardType = KeyboardType.Number,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        if (otpValue.length >= 4) {
                                            if (isForgotPasswordMode) {
                                                if (viewModel.verifyOtp(otpValue)) {
                                                    viewModel.resetPasswordWithFirebase(context) {
                                                        android.widget.Toast.makeText(context, "Password reset email sent. Please check your inbox.", android.widget.Toast.LENGTH_LONG).show()
                                                        isForgotMode = false
                                                    }
                                                }
                                            } else {
                                                viewModel.verifyOtpAndSignUp(context, otpValue) {
                                                    android.widget.Toast.makeText(context, "Account verified and created!", android.widget.Toast.LENGTH_SHORT).show()
                                                    onLoginSuccess()
                                                }
                                            }
                                        }
                                    }
                                )
                            )

                            if (error != null) {
                                Text(
                                    text = error!!,
                                    color = Color(0xFFFF453A),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    if (isForgotPasswordMode) {
                                        if (viewModel.verifyOtp(otpValue)) {
                                            viewModel.resetPasswordWithFirebase(context) {
                                                android.widget.Toast.makeText(context, "Password reset email sent. Please check your inbox.", android.widget.Toast.LENGTH_LONG).show()
                                                isForgotMode = false
                                            }
                                        }
                                    } else {
                                        viewModel.verifyOtpAndSignUp(context, otpValue) {
                                            android.widget.Toast.makeText(context, "Account verified and created!", android.widget.Toast.LENGTH_SHORT).show()
                                            onLoginSuccess()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                enabled = !isLoading && otpValue.length >= 4
                            ) {
                                if (isLoading) {
                                    StandardLoader(size = 24.dp)
                                } else {
                                    Text(if (isForgotPasswordMode) "Verify & Reset" else "Verify & Sign Up", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }

                            TextButton(
                                onClick = { viewModel.resendOtp(context) },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                enabled = resendTimer == 0
                            ) {
                                Text(
                                    text = if (resendTimer > 0) "Resend OTP in ${resendTimer}s" else "Resend OTP",
                                    color = if (resendTimer > 0) TextSecondary else AccentPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Scrollable Content (Original Login/SignUp Form)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Logo
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.wave_muzix_icon),
                        contentDescription = "Wave Muzix Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isForgotMode) "Reset Password" else if (isSignUp) "Create Account" else "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = if (isForgotMode) "Enter your email to receive a reset code" else if (isSignUp) "Start your musical journey today" else "Sign in to continue to WaveMuzix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Form Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLiquidGlass) {
                                Modifier.glassContainer(RoundedCornerShape(28.dp))
                            } else {
                                Modifier
                                    .background(SurfaceVariant, RoundedCornerShape(28.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
                            }
                        ),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isForgotMode) {
                            LoginTextField(
                                value = forgotEmail,
                                onValueChange = { forgotEmail = it },
                                label = "Email Address",
                                icon = Icons.Default.Email,
                                keyboardType = KeyboardType.Email
                            )
                        } else {
                            if (isSignUp) {
                                LoginTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = "Full Name",
                                    icon = Icons.Default.Person
                                )

                                LoginTextField(
                                    value = phoneNumber,
                                    onValueChange = { 
                                        if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                                            phoneNumber = it
                                        }
                                    },
                                    label = "Phone Number",
                                    icon = Icons.Default.Phone,
                                    keyboardType = KeyboardType.Phone
                                )
                            }

                            LoginTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = "Email",
                                icon = Icons.Default.Email,
                                keyboardType = KeyboardType.Email
                            )

                            LoginTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Password",
                                icon = Icons.Default.Lock,
                                isPassword = true,
                                passwordVisible = passwordVisible,
                                onPasswordToggle = { passwordVisible = !passwordVisible }
                            )

                            if (!isSignUp) {
                                TextButton(
                                    onClick = { isForgotMode = true },
                                    modifier = Modifier.align(Alignment.End),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Forgot Password?", color = AccentPrimary, fontSize = 14.sp)
                                }
                            }
                        }

                        if (error != null) {
                            Text(
                                text = error!!,
                                color = Color(0xFFFF453A),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isForgotMode) {
                                    viewModel.initiateForgotPassword(context, forgotEmail)
                                } else if (isSignUp) {
                                    if (phoneNumber.length != 10 || !phoneNumber.all { it.isDigit() }) {
                                        android.widget.Toast.makeText(context, "Please enter a valid 10-digit phone number", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.signUpWithEmail(context, email, password, name, phoneNumber)
                                    }
                                } else {
                                    viewModel.signInWithEmail(context, email, password) {
                                        android.widget.Toast.makeText(context, "Login successful!", android.widget.Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            enabled = !isLoading && (
                                if (isForgotMode) forgotEmail.isNotBlank() 
                                else if (isSignUp) email.isNotBlank() && password.isNotBlank() && name.isNotBlank() && phoneNumber.length == 10 
                                else email.isNotBlank() && password.isNotBlank()
                            )
                        ) {
                            if (isLoading) {
                                StandardLoader(size = 24.dp)
                            } else {
                                Text(if (isForgotMode) "Next" else if (isSignUp) "Sign Up" else "Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Social Login
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("or continue with", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onGoogleSignInClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Sign in with Google",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = Color(0xFF1F1F1F),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(
                    text = if (isSignUp) "Already have an account?" else "Don't have an account?",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { 
                    isSignUp = !isSignUp 
                    viewModel.clearError()
                }) {
                    Text(
                        text = if (isSignUp) "Sign In" else "Sign Up",
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
            
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        singleLine = true
    )
}
