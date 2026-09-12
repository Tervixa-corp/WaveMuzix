package com.example.wavemuzix.data

import android.content.Context
import android.util.Log
import com.example.wavemuzix.data.database.AppDatabase
import com.example.wavemuzix.data.database.FavoriteSongEntity
import com.example.wavemuzix.data.database.PlaylistEntity
import com.example.wavemuzix.data.database.PlaylistSongEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

object DataBackupManager {
    private const val BACKUP_DIR = "app_metadata"
    private const val FAVORITES_FILE = "favorites.json"
    private const val PLAYLISTS_FILE = "playlists.json"
    private const val PLAYLIST_SONGS_FILE = "playlist_songs.json"

    private fun getBackupDir(context: Context): File {
        val dir = File(context.filesDir, BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun backupAll(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val songDao = db.songDao()
                val playlistDao = db.playlistDao()
                val gson = Gson()
                val dir = getBackupDir(context)

                // Backup Favorites
                val favorites = songDao.getAllFavorites().first()
                File(dir, FAVORITES_FILE).writeText(gson.toJson(favorites))

                // Backup Playlists
                val playlists = playlistDao.getAllPlaylists().first()
                File(dir, PLAYLISTS_FILE).writeText(gson.toJson(playlists))

                // Backup Playlist Songs (Flattened for simplicity)
                val allPlaylistSongs = mutableListOf<PlaylistSongEntity>()
                playlists.forEach { playlist ->
                    val songs = playlistDao.getSongsInPlaylist(playlist.id).first()
                    allPlaylistSongs.addAll(songs)
                }
                File(dir, PLAYLIST_SONGS_FILE).writeText(gson.toJson(allPlaylistSongs))
                
                // Also sync to Firebase if user is logged in
                FirebaseSyncManager.syncToFirebase(context)
                
                Log.d("DataBackupManager", "All data backed up successfully to internal storage and Firebase.")
            } catch (e: Exception) {
                Log.e("DataBackupManager", "Backup failed", e)
            }
        }
    }

    suspend fun restoreAll(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val songDao = db.songDao()
                val playlistDao = db.playlistDao()
                val gson = Gson()
                val dir = getBackupDir(context)

                // 1. Restore Favorites
                val favFile = File(dir, FAVORITES_FILE)
                if (favFile.exists()) {
                    val favorites = gson.fromJson(favFile.readText(), Array<FavoriteSongEntity>::class.java)
                    favorites.forEach { songDao.insertFavorite(it) }
                }

                // 2. Restore Playlists
                val pFile = File(dir, PLAYLISTS_FILE)
                if (pFile.exists()) {
                    val playlists = gson.fromJson(pFile.readText(), Array<PlaylistEntity>::class.java)
                    playlists.forEach { playlistDao.createPlaylist(it) }
                }

                // 3. Restore Playlist Songs
                val sFile = File(dir, PLAYLIST_SONGS_FILE)
                if (sFile.exists()) {
                    val songs = gson.fromJson(sFile.readText(), Array<PlaylistSongEntity>::class.java)
                    songs.forEach { playlistDao.addSongToPlaylist(it) }
                }
                
                // 4. Restore Downloads (Syncing with files)
                DownloadManager.syncWithStorage(context)
                
                Log.d("DataBackupManager", "All data restored from internal storage backup.")
            } catch (e: Exception) {
                Log.e("DataBackupManager", "Restore failed", e)
            }
        }
    }
}
