package com.example.wavemuzix.utils

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOGS_COLLECTION = "server_logs"

    enum class Level {
        INFO, WARN, ERROR, DEBUG
    }

    fun log(message: String, level: Level = Level.INFO) {
        val user = Firebase.auth.currentUser
        val logData = hashMapOf(
            "message" to message,
            "level" to level.name,
            "timestamp" to System.currentTimeMillis(),
            "userEmail" to user?.email,
            "userId" to user?.uid
        )

        // Log to Logcat
        when (level) {
            Level.INFO -> Log.i(TAG, message)
            Level.WARN -> Log.w(TAG, message)
            Level.ERROR -> Log.e(TAG, message)
            Level.DEBUG -> Log.d(TAG, message)
        }

        // Log to Firestore (Async)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Firebase.firestore.collection(LOGS_COLLECTION).add(logData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log to Firestore", e)
            }
        }
    }

    fun info(message: String) = log(message, Level.INFO)
    fun warn(message: String) = log(message, Level.WARN)
    fun error(message: String) = log(message, Level.ERROR)
    fun debug(message: String) = log(message, Level.DEBUG)
}
