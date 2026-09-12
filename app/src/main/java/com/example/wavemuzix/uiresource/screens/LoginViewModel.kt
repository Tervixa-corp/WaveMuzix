package com.example.wavemuzix.uiresource.screens

import com.example.wavemuzix.utils.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.data.SettingsManager
import com.example.wavemuzix.data.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user = _user.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isEmailVerificationSent = MutableStateFlow(false)
    val isEmailVerificationSent = _isEmailVerificationSent.asStateFlow()

    private val _showOtpScreen = MutableStateFlow(false)
    val showOtpScreen = _showOtpScreen.asStateFlow()

    private val _resendTimer = MutableStateFlow(0)
    val resendTimer = _resendTimer.asStateFlow()

    private val _isForgotPasswordMode = MutableStateFlow(false)
    val isForgotPasswordMode = _isForgotPasswordMode.asStateFlow()

    private val _canResetPassword = MutableStateFlow(false)
    val canResetPassword = _canResetPassword.asStateFlow()

    private var generatedOtp: String? = null
    private var pendingUserData: PendingUser? = null
    private var resetEmail: String? = null
    private var resendTimerJob: kotlinx.coroutines.Job? = null

    data class PendingUser(
        val email: String,
        val password: String,
        val name: String,
        val phoneNumber: String
    )

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            _user.value = currentUser
        } else {
            _user.value = null
            FirebaseSyncManager.resetSyncStatus()
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        // Initial check
        _user.value = auth.currentUser
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    fun signInWithEmail(context: Context, email: String, password: String, onSuccess: () -> Unit) {
        val trimmedEmail = email.trim()
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                val user = result.user
                
                if (user != null) {
                    AppLogger.info("User signed in: ${user.email}")
                    // Update Firestore with email if it's missing (for Admin Portal visibility)
                    val db = com.google.firebase.ktx.Firebase.firestore
                    db.collection("users").document(user.uid).set(
                        mapOf(
                            "email" to (user.email ?: trimmedEmail),
                            "displayName" to (user.displayName ?: ""),
                            "lastLogin" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()

                    FirebaseSyncManager.syncFromFirebase(context)
                    onSuccess()
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Login failed"
                auth.signOut()
                _user.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUpWithEmail(context: Context, email: String, password: String, name: String, phoneNumber: String) {
        val trimmedEmail = email.trim()
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Phone number validation: must be exactly 10 digits
                if (phoneNumber.length != 10 || !phoneNumber.all { it.isDigit() }) {
                    _error.value = "Please enter a valid 10-digit phone number."
                    return@launch
                }

                // Check if account already exists
                val result = auth.fetchSignInMethodsForEmail(trimmedEmail).await()
                if (!result.signInMethods.isNullOrEmpty()) {
                    _error.value = "An account with this email already exists. Please sign in instead."
                    return@launch
                }

                generatedOtp = (100000..999999).random().toString()
                pendingUserData = PendingUser(trimmedEmail, password, name, phoneNumber)
                
                // SEND OTP EMAIL
                sendOtpEmail(trimmedEmail, generatedOtp!!)
                
                _showOtpScreen.value = true
                startResendTimer()
                AppLogger.info("OTP sent for signup: $trimmedEmail")
                android.widget.Toast.makeText(context, "OTP sent to $email", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to send OTP"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun initiateForgotPassword(context: Context, email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _error.value = "Please enter a valid email address."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // To bypass security rule restrictions for guests (who can't query the 'users' collection)
                // and avoid issues with Firebase Auth's Email Enumeration Protection, 
                // we proceed directly with sending the reset OTP.
                // The final Firebase password reset call will naturally only work for valid accounts.
                
                generatedOtp = (100000..999999).random().toString()
                resetEmail = trimmedEmail
                _isForgotPasswordMode.value = true
                
                sendOtpEmail(trimmedEmail, generatedOtp!!)
                
                _showOtpScreen.value = true
                startResendTimer()
                AppLogger.info("OTP sent for password reset: $trimmedEmail")
                android.widget.Toast.makeText(context, "Reset OTP sent to $trimmedEmail", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to send reset OTP"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun sendOtpEmail(email: String, otp: String) {
        try {
            com.example.wavemuzix.utils.GmailSender.sendOtpEmail(email, otp)
        } catch (e: Exception) {
            android.util.Log.e("EmailError", "Error: ${e.message}")
            throw e
        }
    }

    fun verifyOtp(enteredOtp: String): Boolean {
        return if (enteredOtp == generatedOtp) {
            if (_isForgotPasswordMode.value) {
                _canResetPassword.value = true
            }
            true
        } else {
            _error.value = "Invalid OTP. Please try again."
            false
        }
    }

    fun verifyOtpAndSignUp(context: Context, enteredOtp: String, onSuccess: () -> Unit) {
        if (!verifyOtp(enteredOtp)) return

        val data = pendingUserData ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = auth.createUserWithEmailAndPassword(data.email, data.password).await()
                val user = result.user
                
                user?.updateProfile(userProfileChangeRequest {
                    displayName = data.name
                })?.await()
                
                // Save metadata to Firestore
                val db = Firebase.firestore
                user?.uid?.let { uid ->
                    db.collection("users").document(uid).set(
                        mapOf(
                            "phoneNumber" to data.phoneNumber,
                            "email" to data.email,
                            "displayName" to data.name,
                            "createdAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                }

                SettingsManager(context).phoneNumber = data.phoneNumber
                AppLogger.info("New user registered: ${data.email}")
                _showOtpScreen.value = false
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Registration failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPasswordWithFirebase(context: Context, onSuccess: () -> Unit) {
        val email = resetEmail ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                auth.sendPasswordResetEmail(email).await()
                AppLogger.info("Password reset email sent via Firebase: $email")
                _isForgotPasswordMode.value = false
                _showOtpScreen.value = false
                _canResetPassword.value = false
                resetEmail = null
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to send reset email"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resendOtp(context: Context) {
        if (_resendTimer.value > 0) return
        
        val email = if (_isForgotPasswordMode.value) resetEmail else pendingUserData?.email
        if (email == null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                generatedOtp = (100000..999999).random().toString()
                sendOtpEmail(email, generatedOtp!!)
                android.widget.Toast.makeText(context, "New OTP sent to $email", android.widget.Toast.LENGTH_LONG).show()
                startResendTimer()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to resend OTP"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            _resendTimer.value = 60
            while (_resendTimer.value > 0) {
                kotlinx.coroutines.delay(1000)
                _resendTimer.value -= 1
            }
        }
    }

    fun cancelOtp() {
        _showOtpScreen.value = false
        _isForgotPasswordMode.value = false
        _canResetPassword.value = false
        pendingUserData = null
        resetEmail = null
        generatedOtp = null
        _error.value = null
        resendTimerJob?.cancel()
        _resendTimer.value = 0
    }

    fun signInWithGoogle(context: Context, idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                
                // Save/Update user info in Firestore for Admin visibility
                if (user != null) {
                    AppLogger.info("Google Sign-In successful: ${user.email}")
                    val db = Firebase.firestore
                    db.collection("users").document(user.uid).set(
                        mapOf(
                            "email" to (user.email ?: ""),
                            "displayName" to (user.displayName ?: "Google User"),
                            "photoUrl" to (user.photoUrl?.toString() ?: ""),
                            "lastLogin" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                }

                FirebaseSyncManager.syncFromFirebase(context)
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Google sign-in failed"
                auth.signOut()
                _user.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
        _isEmailVerificationSent.value = false
    }

    fun signOut(context: Context) {
        auth.signOut()
        
        // Also sign out from Google to allow account switching
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, gso).signOut()

        _user.value = null
        _error.value = null
        _isEmailVerificationSent.value = false
        FirebaseSyncManager.resetSyncStatus()
    }
}
