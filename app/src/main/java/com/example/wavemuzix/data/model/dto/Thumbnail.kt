package com.example.wavemuzix.data.model.dto

import com.google.gson.annotations.SerializedName

data class Thumbnail(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int
)

data class ThumbnailDetails(
    @SerializedName("thumbnails") val thumbnails: List<Thumbnail>
)
