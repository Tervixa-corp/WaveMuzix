package com.example.wavemuzix.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val videoId: String = "",
    val artistId: String? = null,
    val thumbnailPath: String? = null,
    val lyrics: String? = null,
    val durationText: String? = null,
    val streamUrl: String? = null
)
