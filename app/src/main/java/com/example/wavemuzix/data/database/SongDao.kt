package com.example.wavemuzix.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: FavoriteSongEntity)

    @Delete
    suspend fun deleteFavorite(song: FavoriteSongEntity)

    @Query("SELECT * FROM favorite_songs ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE videoId = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMostPlayed(song: MostPlayedSongEntity)

    @Query("SELECT * FROM most_played_songs WHERE videoId = :videoId")
    suspend fun getMostPlayedByVideoId(videoId: String): MostPlayedSongEntity?

    @Query("SELECT * FROM most_played_songs ORDER BY playCount DESC LIMIT 50")
    fun getMostPlayedSongs(): Flow<List<MostPlayedSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(song: DownloadedSongEntity)

    @Query("SELECT * FROM downloaded_songs ORDER BY downloadTimestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM downloaded_songs WHERE videoId = :videoId")
    suspend fun getDownloadByVideoId(videoId: String): DownloadedSongEntity?

    @Query("DELETE FROM downloaded_songs WHERE videoId = :videoId")
    suspend fun deleteDownload(videoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsCacheEntity)

    @Query("SELECT * FROM lyrics_cache WHERE videoId = :videoId")
    suspend fun getLyricsByVideoId(videoId: String): LyricsCacheEntity?

    @Query("DELETE FROM lyrics_cache WHERE timestamp < :expiryTime")
    suspend fun clearOldLyrics(expiryTime: Long)

    @Transaction
    suspend fun incrementPlayCount(videoId: String, title: String, artist: String, thumbnailUrl: String, artistId: String?) {
        val existing = getMostPlayedByVideoId(videoId)
        if (existing != null) {
            insertMostPlayed(existing.copy(
                playCount = existing.playCount + 1,
                lastPlayedTimestamp = System.currentTimeMillis()
            ))
        } else {
            // Title will be cleaned in repository before calling this, 
            // but we can add an extra safety check if needed via a utility.
            // For now, assume repository cleans it.
            insertMostPlayed(MostPlayedSongEntity(
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                artistId = artistId
            ))
        }
    }
}
