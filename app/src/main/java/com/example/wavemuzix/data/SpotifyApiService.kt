package com.example.wavemuzix.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SpotifyApiService {
    @GET("pathfinder/v1/query")
    suspend fun searchArtist(
        @Query("operationName") operationName: String = "searchDesktop",
        @Query("variables") variables: String,
        @Query("extensions") extensions: String,
        @Header("authorization") auth: String? = null,
        @Header("client-token") clientToken: String? = null
    ): Response<SpotifySearchResponse>
}

data class SpotifySearchResponse(
    @SerializedName("data") val data: SpotifyData?
)

data class SpotifyData(
    @SerializedName("searchV2") val searchV2: SpotifySearchV2?
)

data class SpotifySearchV2(
    @SerializedName("artists") val artists: SpotifyArtists?
)

data class SpotifyArtists(
    @SerializedName("items") val items: List<SpotifyArtistItem>?
)

data class SpotifyArtistItem(
    @SerializedName("data") val data: SpotifyArtistData?
)

data class SpotifyArtistData(
    @SerializedName("uri") val uri: String?,
    @SerializedName("profile") val profile: SpotifyProfile?,
    @SerializedName("visuals") val visuals: SpotifyVisuals?
)

data class SpotifyProfile(
    @SerializedName("name") val name: String?
)

data class SpotifyVisuals(
    @SerializedName("avatarImage") val avatarImage: SpotifyImage?,
    @SerializedName("headerImage") val headerImage: SpotifyImage?
)

data class SpotifyImage(
    @SerializedName("sources") val sources: List<SpotifyImageSource>?
)

data class SpotifyImageSource(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)
