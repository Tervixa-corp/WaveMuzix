package com.example.wavemuzix.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import com.example.wavemuzix.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var notificationProvider: MediaPlayerNotification

    private fun wrapPlayer(player: androidx.media3.common.Player): androidx.media3.common.Player {
        return object : ForwardingPlayer(player) {
            override fun seekToPrevious() {
                MusicPlayer.playPreviousInQueue(this@PlaybackService)
            }

            override fun seekToNext() {
                MusicPlayer.playNextInQueue(this@PlaybackService, isAuto = false)
            }

            override fun seekToPreviousMediaItem() {
                MusicPlayer.playPreviousInQueue(this@PlaybackService)
            }

            override fun seekToNextMediaItem() {
                MusicPlayer.playNextInQueue(this@PlaybackService, isAuto = false)
            }

            override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "playback_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("PlaybackService", "onCreate: Service starting")
        
        notificationProvider = MediaPlayerNotification(this)
        
        val player = MusicPlayer.getInstance(this)

        val intent = Intent(this, com.example.wavemuzix.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Force-refresh listener to keep notification in sync during automatic transitions
        val sessionListener = object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                Log.d("PlaybackService", "onMediaItemTransition: Reason=$reason")
                mediaSession?.let { session ->
                    // Force framework to call onUpdateNotification
                    onUpdateNotification(session, false)
                    
                    // Small trick to 'nudge' the MediaSession UI to broadcast new data
                    try {
                        session.setCustomLayout(listOf())
                    } catch (e: Exception) {
                        Log.e("PlaybackService", "Failed to nudge session layout", e)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                mediaSession?.let { onUpdateNotification(it, false) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mediaSession?.let { onUpdateNotification(it, false) }
            }
        }
        player.addListener(sessionListener)

        val wrappedPlayer = wrapPlayer(player)

        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(this)))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
                        .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                        .build()
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<androidx.media3.common.MediaItem>
                ): com.google.common.util.concurrent.ListenableFuture<MutableList<androidx.media3.common.MediaItem>> {
                    val updatedItems = mediaItems.map { item ->
                        if (item.mediaMetadata.title == null) {
                            val song = MusicPlayer.queue.find { it.videoId == item.mediaId }
                            if (song != null) {
                                item.buildUpon()
                                    .setMediaMetadata(MusicPlayer.createMediaMetadata(song))
                                    .build()
                            } else item
                        } else item
                    }.toMutableList()
                    return com.google.common.util.concurrent.Futures.immediateFuture(updatedItems)
                }
            })
            .build()

        // Sync with MusicPlayer crossfade/player swaps
        MusicPlayer.onPlayerChanged = { newPlayer: ExoPlayer ->
            Log.d("PlaybackService", "onPlayerChanged: Updating session player to $newPlayer")
            mediaSession?.setPlayer(wrapPlayer(newPlayer))
            newPlayer.addListener(sessionListener)
            mediaSession?.let { onUpdateNotification(it, false) }
        }

        // Explicitly set the notification provider to use our channel
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = MusicPlayer.getInstance(this)
        
        if (mediaSession != null) {
            val metadata = player.mediaMetadata
            val notification = notificationProvider.buildNotification(
                mediaSession!!,
                metadata.title?.toString() ?: "WaveMuzix",
                metadata.artist?.toString() ?: "Ready to play"
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(1001, notification)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "startForeground failed", e)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForeground: Boolean) {
        val player = session.player
        val metadata = player.mediaMetadata
        
        var title = metadata.title?.toString()
        var artist = metadata.artist?.toString()
        var artUri = metadata.artworkUri?.toString()
        
        // Fallback to MusicPlayer state if player metadata is temporarily empty (common during transitions)
        if (title.isNullOrEmpty()) {
            val currentSong = MusicPlayer.currentSong
            if (currentSong != null) {
                title = currentSong.title
                artist = currentSong.artist
                artUri = currentSong.thumbnailUrl
            }
        }
        
        notificationProvider.updateNotification(
            session,
            title ?: "WaveMuzix",
            artist ?: "Ready to play",
            artUri
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        MusicPlayer.release()
        MusicPlayer.onPlayerChanged = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            stopSelf()
        }
    }
}
