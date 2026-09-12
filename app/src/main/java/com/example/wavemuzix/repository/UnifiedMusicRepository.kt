package com.example.wavemuzix.repository

import com.example.wavemuzix.model.Song

class UnifiedMusicRepository(
    private val youtubeRepository: YouTubeRepository,
    val audioStreamRepository: AudioStreamRepository,
    private val artistMetadataRepository: ArtistMetadataRepository
) {

    /**
     * METADATA & UI NAVIGATION LAYER
     * All UI data fetching is routed directly to YouTubeRepository.
     */

    suspend fun getSearchSuggestions(query: String, cookies: String?): List<String> =
        youtubeRepository.getSearchSuggestions(query, cookies)

    suspend fun searchAll(
        query: String,
        cookies: String?,
        filterVideos: Boolean = true,
        filterOnlyVideos: Boolean = false,
        filterOnlyAudio: Boolean = false
    ): Triple<List<Song>, List<PlaylistDetail>, List<ArtistDetail>> =
        youtubeRepository.searchAll(query, cookies, filterVideos, filterOnlyVideos, filterOnlyAudio)

    suspend fun searchSongs(
        query: String,
        cookies: String?,
        filterVideos: Boolean = true,
        filterOnlyVideos: Boolean = false,
        filterOnlyAudio: Boolean = false
    ): List<Song> =
        youtubeRepository.searchSongs(query, cookies, filterVideos, filterOnlyVideos, filterOnlyAudio)

    suspend fun searchPlaylists(query: String, cookies: String?): List<PlaylistDetail> =
        youtubeRepository.searchPlaylists(query, cookies)

    suspend fun searchArtists(query: String, cookies: String?, hl: String = "en"): List<ArtistDetail> =
        youtubeRepository.searchArtists(query, cookies, hl)

    suspend fun fetchHome(cookies: String?, selectedLanguages: Set<String> = emptySet(), location: String? = null): HomeResponse =
        youtubeRepository.fetchHome(cookies, selectedLanguages, location)

    suspend fun fetchHomeContinuation(
        continuation: String,
        cookies: String?,
        selectedLanguages: Set<String> = emptySet(),
        location: String? = null
    ): HomeResponse =
        youtubeRepository.fetchHomeContinuation(continuation, cookies, selectedLanguages, location)

    suspend fun fetchArtist(artistId: String, cookies: String?): ArtistDetail? =
        youtubeRepository.fetchArtist(artistId, cookies)

    suspend fun getArtistChannel(artistName: String, cookies: String?): ArtistDetail? =
        youtubeRepository.getArtistChannel(artistName, cookies)

    suspend fun fetchArtistSongs(browseId: String, params: String?, cookies: String?): List<Song> =
        youtubeRepository.fetchArtistSongs(browseId, params, cookies)

    suspend fun fetchPlaylist(
        browseId: String,
        cookies: String?,
        selectedLanguages: Set<String> = emptySet()
    ): PlaylistDetail? =
        youtubeRepository.fetchPlaylist(browseId, cookies, selectedLanguages)

    suspend fun fetchNextSongs(videoId: String, playlistId: String?, cookies: String?, continuation: String? = null, filterAudioOnly: Boolean = false): Pair<List<Song>, String?> =
        youtubeRepository.fetchNextSongs(videoId, playlistId, cookies, continuation, filterAudioOnly)

    suspend fun fetchTopMusicVideos(cookies: String?): List<Song> =
        youtubeRepository.fetchTopMusicVideos(cookies)

    suspend fun discoverArtistsByLanguage(language: String, limit: Int = 20): List<ArtistDetail> {
        return artistMetadataRepository.getArtistsByLanguage(language, limit)
    }

    data class ArtistHDResult(
        val artist: ArtistDetail,
        val source: String
    )

    suspend fun getArtistHDMetadata(artistName: String, cookies: String? = null): ArtistHDResult? {
        val sanitizedName = artistMetadataRepository.cleanArtistName(artistName)
        
        // 1. Primary Fetch (Spotify)
        val spotifyMetadata = artistMetadataRepository.getSpotifyArtistMetadata(sanitizedName)
        if (spotifyMetadata != null && spotifyMetadata.thumbnailUrl.isNotEmpty()) {
            return ArtistHDResult(spotifyMetadata, "Spotify")
        }

        // 2. Secondary Fetch (Deezer) - High Quality 1000x1000
        val deezerMetadata = artistMetadataRepository.getArtistMetadata(sanitizedName)
        if (deezerMetadata != null && deezerMetadata.thumbnailUrl.isNotEmpty()) {
            return ArtistHDResult(deezerMetadata, "Deezer")
        }

        // 3. Fallback Fetch (YouTube Music Artist Search)
        val ytmArtist = youtubeRepository.searchOfficialArtist(sanitizedName, cookies)
        if (ytmArtist != null && ytmArtist.thumbnailUrl.isNotEmpty()) {
            return ArtistHDResult(ytmArtist, "YouTube")
        }

        // 4. Final Fallback (Safe return for original track/album thumbnail logic in UI)
        return null
    }

    /**
     * AUDIO STREAM RESOLUTION LAYER
     * Resolves audio stream via YouTube.
     */

    suspend fun getStreamingUrl(
        videoId: String,
        cookies: String?,
        failedClients: List<String> = emptyList(),
        targetBitrate: Int? = null,
        priority: String = "Force OPUS 160kbps",
        preferredVideoQuality: String = "auto"
    ): StreamingResult? = audioStreamRepository.getStreamingUrl(
        videoId = videoId,
        cookies = cookies,
        failedClients = failedClients,
        targetBitrate = targetBitrate,
        priority = priority,
        preferredVideoQuality = preferredVideoQuality
    )
}
