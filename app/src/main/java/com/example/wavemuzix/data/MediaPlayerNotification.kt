package com.example.wavemuzix.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.wavemuzix.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MediaPlayerNotification(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if channel already exists with different settings
            val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null && existingChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active music playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        mediaSession: MediaSession,
        title: String,
        artist: String,
        largeIcon: Bitmap? = null
    ): Notification {
        val intent = mediaSession.sessionActivity ?: PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.example.wavemuzix.MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setContentIntent(intent)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    private var lastThumbnailUrl: String? = null
    private var currentGlideTarget: CustomTarget<Bitmap>? = null

    fun updateNotification(
        mediaSession: MediaSession,
        title: String,
        artist: String,
        thumbnailUrl: String? = null
    ) {
        // 1. Update text immediately to avoid UI lag/freeze
        val initialNotification = buildNotification(mediaSession, title, artist, null)
        notificationManager.notify(NOTIFICATION_ID, initialNotification)

        if (thumbnailUrl.isNullOrEmpty()) {
            lastThumbnailUrl = null
            currentGlideTarget?.let { Glide.with(context).clear(it) }
            return
        }

        // If it's the same URL, we already updated text, no need to reload same image
        if (thumbnailUrl == lastThumbnailUrl) return
        
        // Cancel previous load if any
        currentGlideTarget?.let { Glide.with(context).clear(it) }
        lastThumbnailUrl = thumbnailUrl

        // 2. Load the artwork in background and update again when ready
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (lastThumbnailUrl == thumbnailUrl) {
                    // Re-fetch current title/artist in case they changed during image load
                    val currentMetadata = mediaSession.player.mediaMetadata
                    val currentTitle = currentMetadata.title?.toString() ?: title
                    val currentArtist = currentMetadata.artist?.toString() ?: artist
                    
                    val updatedNotification = buildNotification(mediaSession, currentTitle, currentArtist, resource)
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (lastThumbnailUrl == thumbnailUrl) lastThumbnailUrl = null
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (lastThumbnailUrl == thumbnailUrl) lastThumbnailUrl = null
            }
        }
        currentGlideTarget = target

        Glide.with(context)
            .asBitmap()
            .load(thumbnailUrl)
            .into(target)
    }
}
