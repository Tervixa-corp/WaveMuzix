package com.example.wavemuzix.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AppNotification(
    val id: String,
    val title: String,
    val description: String,
    val type: NotificationType,
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    UPDATE, INFO
}

object AppNotificationManager {
    val notifications = mutableStateListOf<AppNotification>()
    var triggerUpdateDialog by mutableStateOf(false)
    var hasUnread by mutableStateOf(false)

    fun addNotification(notification: AppNotification) {
        if (notifications.none { it.id == notification.id }) {
            notifications.add(0, notification)
            hasUnread = true
        }
    }

    fun removeNotification(id: String) {
        notifications.removeIf { it.id == id }
    }
}
