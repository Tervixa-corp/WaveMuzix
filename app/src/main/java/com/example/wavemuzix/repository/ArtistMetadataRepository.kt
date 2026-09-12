package com.example.wavemuzix.repository

import com.example.wavemuzix.data.DeezerApiService
import com.example.wavemuzix.data.DeezerArtist
import com.example.wavemuzix.data.SpotifyApiService
import com.example.wavemuzix.data.SpotifySearchResponse
import com.example.wavemuzix.repository.ArtistDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.google.gson.Gson

class ArtistMetadataRepository(
    private val deezerApiService: DeezerApiService,
    private val spotifyApiService: SpotifyApiService
) {

    private val gson = Gson()

    fun cleanArtistName(name: String): String {
        return name.replace(Regex("- Topic$", RegexOption.IGNORE_CASE), "").trim()
    }

    suspend fun getSpotifyArtistMetadata(artistName: String): ArtistDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanedName = cleanArtistName(artistName)
            Log.d("ArtistMetadataRepo", "Searching Spotify for: $cleanedName")
            
            // Spotify GraphQL variables for searchV2
            val variables = mapOf(
                "searchTerm" to cleanedName,
                "count" to 1,
                "includeAudiobooks" to false
            )
            val extensions = mapOf(
                "persistedQuery" to mapOf(
                    "version" to 1,
                    "sha256Hash" to "17242270914d454641c88147d3c0137d6e87f85b854a558a74e300962383808b"
                )
            )

            // We attempt the search. If it fails with 401, the interceptor or a token refresh should handle it.
            // For now, we ensure we are not passing the broken placeholder token.
            val response = spotifyApiService.searchArtist(
                variables = gson.toJson(variables),
                extensions = gson.toJson(extensions)
            )

            if (response.isSuccessful) {
                val spotifyArtist = response.body()?.data?.searchV2?.artists?.items?.firstOrNull()?.data
                if (spotifyArtist != null) {
                    Log.d("ArtistMetadataRepo", "Spotify match found: ${spotifyArtist.profile?.name}")
                    val avatar = spotifyArtist.visuals?.avatarImage?.sources?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
                    val header = spotifyArtist.visuals?.headerImage?.sources?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
                    
                    if (avatar != null) {
                        Log.d("ArtistMetadataRepo", "Spotify avatar found: $avatar")
                    }

                    return@withContext ArtistDetail(
                        name = spotifyArtist.profile?.name ?: cleanedName,
                        description = "Artist on Spotify",
                        thumbnailUrl = avatar ?: "",
                        songs = emptyList(),
                        items = if (header != null) listOf(mapOf("type" to "header", "url" to header)) else emptyList(),
                        browseId = spotifyArtist.uri?.substringAfterLast(":") ?: "",
                        songsBrowseId = null,
                        songsParams = null
                    )
                } else {
                    Log.d("ArtistMetadataRepo", "No Spotify artist found for: $cleanedName")
                }
            } else {
                Log.e("ArtistMetadataRepo", "Spotify API error: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("ArtistMetadataRepo", "Spotify search failed", e)
        }
        null
    }

    suspend fun getArtistsByLanguage(language: String, limit: Int = 20): List<ArtistDetail> = withContext(Dispatchers.IO) {
        try {
            val response = deezerApiService.searchArtist(language, limit)
            if (response.isSuccessful) {
                response.body()?.data?.map { it.toArtistDetail() } ?: emptyList()
            } else {
                Log.e("ArtistMetadataRepo", "Deezer search failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ArtistMetadataRepo", "Error fetching artists from Deezer", e)
            emptyList()
        }
    }

    suspend fun getArtistMetadata(artistName: String): ArtistDetail? = withContext(Dispatchers.IO) {
        try {
            val response = deezerApiService.searchArtist(artistName, 1)
            if (response.isSuccessful) {
                response.body()?.data?.firstOrNull()?.toArtistDetail()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ArtistMetadataRepo", "Error fetching artist metadata from Deezer", e)
            null
        }
    }

    private fun DeezerArtist.toArtistDetail(): ArtistDetail {
        return ArtistDetail(
            name = name,
            description = "Artist on Deezer",
            thumbnailUrl = pictureXl, // HD 1000x1000 is default here
            songs = emptyList(),
            items = emptyList(),
            browseId = id.toString(),
            songsBrowseId = null,
            songsParams = null
        )
    }
}
