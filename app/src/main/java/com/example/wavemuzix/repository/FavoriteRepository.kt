package com.example.wavemuzix.repository

import com.example.wavemuzix.data.DataBackupManager
import com.example.wavemuzix.data.FirebaseSyncManager
import com.example.wavemuzix.data.database.FavoriteSongEntity
import com.example.wavemuzix.data.database.SongDao
import com.example.wavemuzix.model.Song
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FavoriteRepository(private val songDao: SongDao, private val context: Context? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val favorites: Flow<List<Song>> = songDao.getAllFavorites().map { entities ->
        entities.map { it.toSong() }
    }

    suspend fun toggleFavorite(song: Song) {
        if (songDao.isFavorite(song.videoId)) {
            songDao.deleteFavorite(song.toEntity())
        } else {
            songDao.insertFavorite(song.toEntity())
        }
        context?.let { 
            DataBackupManager.backupAll(it)
            scope.launch { FirebaseSyncManager.syncToFirebase(it) }
        }
    }

    suspend fun isFavorite(videoId: String): Boolean {
        return songDao.isFavorite(videoId)
    }

    private fun Song.toEntity() = FavoriteSongEntity(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        artistId = artistId
    )

    private fun FavoriteSongEntity.toSong() = Song(
        id = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        videoId = videoId,
        artistId = artistId
    )
}
