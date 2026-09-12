package com.example.wavemuzix.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val artistId: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "most_played_songs")
data class MostPlayedSongEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val artistId: String?,
    val playCount: Int = 1,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val artistId: String?,
    val filePath: String,
    val mimeType: String? = null,
    val thumbnailPath: String? = null,
    val lyrics: String? = null,
    val downloadTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val videoId: String,
    val lyrics: String,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis()
)
