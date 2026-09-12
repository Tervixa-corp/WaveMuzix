package com.example.wavemuzix.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DeezerApiService {
    @GET("search/artist")
    suspend fun searchArtist(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<DeezerArtistSearchResponse>

    @GET("artist/{id}")
    suspend fun getArtist(
        @Query("id") artistId: Long
    ): Response<DeezerArtist>
}

data class DeezerArtistSearchResponse(
    @SerializedName("data") val data: List<DeezerArtist>,
    @SerializedName("total") val total: Int
)

data class DeezerArtist(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("picture") val picture: String,
    @SerializedName("picture_small") val pictureSmall: String,
    @SerializedName("picture_medium") val pictureMedium: String,
    @SerializedName("picture_big") val pictureBig: String,
    @SerializedName("picture_xl") val pictureXl: String,
    @SerializedName("nb_album") val nbAlbum: Int?,
    @SerializedName("nb_fan") val nbFan: Int?,
    @SerializedName("tracklist") val tracklist: String?
)
