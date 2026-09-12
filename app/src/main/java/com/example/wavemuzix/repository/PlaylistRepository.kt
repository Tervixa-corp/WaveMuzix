package com.example.wavemuzix.repository

import com.example.wavemuzix.data.DataBackupManager
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.data.database.PlaylistDao
import com.example.wavemuzix.data.database.PlaylistEntity
import com.example.wavemuzix.data.database.PlaylistSongEntity
import com.example.wavemuzix.model.Song
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PlaylistRepository(private val playlistDao: PlaylistDao, private val context: Context? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun createPlaylist(name: String): Long {
        val id = playlistDao.createPlaylist(PlaylistEntity(name = name))
        context?.let { 
            DataBackupManager.backupAll(it)
            scope.launch { FirebaseSyncManager.syncToFirebase(it) }
        }
        return id
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        playlistDao.addSongToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                artistId = song.artistId
            )
        )
        context?.let { 
            DataBackupManager.backupAll(it)
            scope.launch { FirebaseSyncManager.syncToFirebase(it) }
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, videoId: String) {
        playlistDao.removeSongFromPlaylist(playlistId, videoId)
        context?.let { 
            DataBackupManager.backupAll(it)
            scope.launch { FirebaseSyncManager.syncToFirebase(it) }
        }
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deletePlaylist(playlist)
        context?.let { 
            DataBackupManager.backupAll(it)
            scope.launch { FirebaseSyncManager.syncToFirebase(it) }
        }
    }
}
