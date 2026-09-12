package com.example.wavemuzix.repository

import android.util.Log
import com.example.wavemuzix.model.Song
import kotlinx.coroutines.async
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.Serializable

// --- MODELS ---

data class StreamingResult(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long,
    val audioQuality: String?,
    val expiresInSeconds: Int?,
    val isAudioOnly: Boolean,
    val userAgent: String,
    val loudnessDb: Double?,
    val videoUrl: String?,
    val videoMimeType: String?,
    val clientName: String
) : Serializable

data class ArtistDetail(
    val name: String,
    val description: String,
    val thumbnailUrl: String,
    val songs: List<Song>,
    val items: List<Map<String, Any>> = emptyList(),
    val browseId: String,
    val songsBrowseId: String? = null,
    val songsParams: String? = null
) : Serializable

data class PlaylistDetail(
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val songs: List<Song>,
    val browseId: String,
    val type: String? = "playlist",
    val continuation: String? = null
) : Serializable

data class HomeShelf(
    val title: String,
    val items: List<Any>,
    val type: String = "shelf"
) : Serializable

data class HomeResponse(
    val userName: String? = null,
    val userAvatarUrl: String? = null,
    val quickPicks: List<Song> = emptyList(),
    val trending: List<Song> = emptyList(),
    val shelves: List<HomeShelf> = emptyList(),
    val continuation: String? = null
) : Serializable

// --- REPOSITORY IMPLEMENTATION ---

class YouTubeRepository {
    private val ytService: YoutubeService by lazy {
        NewPipe.getService(0) as YoutubeService
    }

    private fun isAudioSong(title: String, artist: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerArtist = artist.lowercase()
        
        // Topic channels are almost always official audio releases
        if (lowerArtist.contains("topic") || lowerArtist.endsWith("- topic")) return true
        
        // Obvious video-only keywords
        val videoKeywords = listOf(
            "official video", "music video", "official music video", 
            "(video)", "[video]", "(mv)", "[mv]", "official mv",
            "lyric video", "visualizer", "live performance", "live at",
            "full video", "teaser", "trailer", "behind the scenes"
        )
        
        return videoKeywords.none { lowerTitle.contains(it) }
    }

    private fun cleanSongTitle(title: String): String {
        var cleaned = title
        if (cleaned.contains("|")) {
            cleaned = cleaned.substringBefore("|")
        }
        val lower = cleaned.lowercase()
        val keywords = listOf("- video song", "- video", "viral", "song", " video", "4k", "full", "Video ")
        for (kw in keywords) {
            val idx = lower.indexOf(kw)
            if (idx != -1) {
                cleaned = cleaned.substring(0, idx)
                break
            }
        }
        return cleaned.trim()
    }

    suspend fun getStreamingUrl(
        videoId: String,
        cookies: String? = null,
        failedClients: List<String> = emptyList(),
        targetBitrate: Int? = null,
        priority: String = "Force OPUS 160kbps",
        preferredVideoQuality: String = "auto"
    ): StreamingResult? = withContext(Dispatchers.IO) {
        try {
            // Priority: YouTube Music URL (Better for Audio) -> Fallback to Standard YouTube
            val urls = listOf(
                "https://music.youtube.com/watch?v=$videoId",
                "https://www.youtube.com/watch?v=$videoId"
            )
            
            var lastException: Exception? = null
            for (url in urls) {
                try {
                    val streamInfo = StreamInfo.getInfo(ytService, url)
                    val audioStreams = streamInfo.audioStreams
                    
                    if (audioStreams.isEmpty()) continue

                    val bestStream = if (priority == "Data Saver") {
                        audioStreams.minByOrNull { it.bitrate }
                    } else {
                        audioStreams.maxByOrNull { it.bitrate }
                    }
                    
                    bestStream?.let {
                        return@withContext StreamingResult(
                            url = it.content,
                            mimeType = it.format?.mimeType ?: "audio/webm",
                            bitrate = it.bitrate,
                            contentLength = -1L,
                            audioQuality = it.quality,
                            expiresInSeconds = 3600,
                            isAudioOnly = true,
                            userAgent = "WaveMuzixAndroid/1.0",
                            loudnessDb = null,
                            videoUrl = null,
                            videoMimeType = null,
                            clientName = "NewPipe"
                        )
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.w("YouTubeRepository", "Stream extraction failed for $url", e)
                }
            }
            throw lastException ?: Exception("No audio streams found")
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "All stream extraction attempts failed for $videoId", e)
            null
        }
    }

    suspend fun getSearchSuggestions(query: String, cookies: String?): List<String> = withContext(Dispatchers.IO) {
        try {
            ytService.suggestionExtractor.suggestionList(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchAll(
        query: String, 
        cookies: String?, 
        filterVideos: Boolean = true, 
        filterOnlyVideos: Boolean = false,
        filterOnlyAudio: Boolean = false,
        filters: List<String> = emptyList()
    ): Triple<List<Song>, List<PlaylistDetail>, List<ArtistDetail>> = withContext(Dispatchers.IO) {
        try {
            val finalFilters = when {
                filters.isNotEmpty() -> filters
                filterOnlyVideos -> listOf("music_videos")
                filterOnlyAudio -> listOf("music_songs")
                else -> listOf("music_songs") // Default to YTM Songs for high fidelity
            }
            val searchInfo = SearchInfo.getInfo(ytService, ytService.searchQHFactory.fromQuery(query, finalFilters, ""))
            val items = searchInfo.relatedItems
            
            val songs = items.filterIsInstance<StreamInfoItem>().map {
                Song(
                    id = it.url,
                    videoId = it.url.substringAfter("v="),
                    title = cleanSongTitle(it.name ?: ""),
                    artist = it.uploaderName ?: "",
                    thumbnailUrl = it.thumbnails.firstOrNull()?.url ?: "",
                    durationText = it.duration.toString()
                )
            }
            
            val playlists = items.filterIsInstance<PlaylistInfoItem>().map {
                PlaylistDetail(
                    title = cleanSongTitle(it.name ?: ""),
                    description = it.uploaderName ?: "",
                    thumbnailUrl = it.thumbnails.firstOrNull()?.url ?: "",
                    songs = emptyList(),
                    browseId = it.url.substringAfter("list="),
                    type = "playlist"
                )
            }

            val artists = items.filterIsInstance<ChannelInfoItem>().map {
                ArtistDetail(
                    name = it.name ?: "",
                    description = "",
                    thumbnailUrl = it.thumbnails.firstOrNull()?.url ?: "",
                    songs = emptyList(),
                    browseId = it.url.substringAfter("/channel/").substringAfter("/browse/").substringAfter("/c/").substringAfter("/@")
                )
            }
            
            val cleanedSongs = songs.map { it.copy(title = cleanSongTitle(it.title)) }
            val cleanedPlaylists = playlists.map { it.copy(title = cleanSongTitle(it.title)) }
            
            Triple(cleanedSongs, cleanedPlaylists, artists)
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Search failed for $query with filters $filters", e)
            Triple(emptyList(), emptyList(), emptyList())
        }
    }

    suspend fun fetchPlaylist(browseId: String, cookies: String?, selectedLanguages: Set<String>): PlaylistDetail? = withContext(Dispatchers.IO) {
        try {
            val url = if (browseId.startsWith("http")) {
                browseId.replace("www.youtube.com", "music.youtube.com")
            } else {
                "https://music.youtube.com/playlist?list=$browseId"
            }
            val playlistInfo = PlaylistInfo.getInfo(ytService, url)
            PlaylistDetail(
                title = cleanSongTitle(playlistInfo.name ?: ""),
                description = cleanDescription(playlistInfo.description?.content ?: ""),
                thumbnailUrl = playlistInfo.thumbnails.firstOrNull()?.url ?: "",
                songs = playlistInfo.relatedItems.filterIsInstance<StreamInfoItem>().map {
                    Song(
                        id = it.url,
                        videoId = it.url.substringAfter("v="),
                        title = cleanSongTitle(it.name ?: ""),
                        artist = it.uploaderName ?: "",
                        thumbnailUrl = it.thumbnails.firstOrNull()?.url ?: ""
                    )
                }.filter { isAudioSong(it.title, it.artist) },
                browseId = browseId
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchNextSongs(videoId: String, playlistId: String?, cookies: String?, continuation: String?, filterAudioOnly: Boolean = false): Pair<List<Song>, String?> = withContext(Dispatchers.IO) {
        try {
            val url = if (playlistId != null) {
                "https://music.youtube.com/watch?v=$videoId&list=$playlistId"
            } else {
                "https://music.youtube.com/watch?v=$videoId&list=RD$videoId"
            }
            val streamInfo = StreamInfo.getInfo(ytService, url)
            val songs = streamInfo.relatedItems.filterIsInstance<StreamInfoItem>().map {
                Song(
                    id = it.url,
                    videoId = it.url.substringAfter("v="),
                    title = cleanSongTitle(it.name ?: ""),
                    artist = it.uploaderName ?: "",
                    thumbnailUrl = it.thumbnails.firstOrNull()?.url ?: ""
                )
            }.filter { !filterAudioOnly || isAudioSong(it.title, it.artist) }
            Pair(songs, null)
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }

    suspend fun searchSongs(query: String, cookies: String?, filterVideos: Boolean, filterOnlyVideos: Boolean, filterOnlyAudio: Boolean = false): List<Song> = 
        searchAll(query, cookies, filterVideos, filterOnlyVideos, filterOnlyAudio).first

    suspend fun searchPlaylists(query: String, cookies: String?): List<PlaylistDetail> =
        searchAll(query, cookies, filters = listOf("playlists")).second

    suspend fun searchArtists(query: String, cookies: String?, hl: String = "en"): List<ArtistDetail> =
        searchAll(query, cookies, filters = listOf("artists")).third

    suspend fun searchOfficialArtist(artistName: String, cookies: String?): ArtistDetail? = withContext(Dispatchers.IO) {
        try {
            // Specifically search for artists on YTM
            val searchInfo = SearchInfo.getInfo(ytService, ytService.searchQHFactory.fromQuery(artistName, listOf("artists"), ""))
            val artistItem = searchInfo.relatedItems.filterIsInstance<ChannelInfoItem>().firstOrNull() ?: return@withContext null
            
            // Return basic details from search item first to avoid extra network hop if possible
            // but for full metadata (bio, etc), fetchArtist is better.
            fetchArtist(artistItem.url.substringAfterLast("/"), cookies)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchHome(cookies: String?, selectedLanguages: Set<String>, location: String? = null): HomeResponse = withContext(Dispatchers.IO) {
        supervisorScope {
            val shelves = mutableListOf<HomeShelf>()
            
            // Helper to fetch songs safely
            suspend fun safeSearchSongs(q: String, v: Boolean = true, ov: Boolean = false, oa: Boolean = false) = try {
                searchSongs(q, cookies, v, ov, oa)
            } catch (e: Exception) { emptyList<Song>() }

            val languageSuffix = selectedLanguages.joinToString(" ")
            val locationSuffix = location ?: ""
            val queryModifier = "$languageSuffix $locationSuffix".trim()

            // Parallel fetching of all sections
            val trendingTask = async {
                try {
                    // Try local trending if location is available, otherwise global
                    val playlistId = if (location != null) "PL4fGSI1pDJn5tq8aM079Z5J2d9n7j3G7-" else "PL4fGSI1pDJn5tq8aM079Z5J2d9n7j3G7-" 
                    // Actually, YouTube Music has specific charts for countries. 
                    // For now, let's just enhance search queries with location.
                    
                    val trendingQuery = if (queryModifier.isNotEmpty()) "Trending Music $queryModifier" else "Trending Music Global"
                    safeSearchSongs(trendingQuery, oa = true)
                } catch (e: Exception) {
                    safeSearchSongs("Trending Music Global", oa = true)
                }
            }

            val newReleasesTask = async { 
                val q = if (queryModifier.isNotEmpty()) "New Music Releases $queryModifier" else "New Music Releases"
                safeSearchSongs(q, oa = true) 
            }
            val videoSongsTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Official Music Video $queryModifier" else "Official Music Video"
                safeSearchSongs(q, true, true) 
            }
            
            // Playlists & Artists
            val playlistsTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Top Music Playlists $queryModifier" else "Top Music Playlists"
                try { searchPlaylists(q, cookies) } catch(e: Exception) { emptyList() } 
            }
            val rainTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Rain Therapy & Deep Sleep Music $queryModifier" else "Rain Therapy & Deep Sleep Music"
                try { searchPlaylists(q, cookies) } catch(e: Exception) { emptyList() } 
            }
            val communityPlaylistsTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Trending Community Playlists $queryModifier" else "Trending Community Playlists"
                try { searchPlaylists(q, cookies) } catch(e: Exception) { emptyList() } 
            }
            val videoChartsTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Music Video Charts $queryModifier" else "Global Music Video Charts"
                try { searchPlaylists(q, cookies) } catch(e: Exception) { emptyList() } 
            }
            val artistsTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Popular Music Artists $queryModifier" else "Top Popular Music Artists"
                try { searchArtists(q, cookies, "en") } catch(e: Exception) { emptyList() } 
            }
            val livePerformancesTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Live Music Performance $queryModifier" else "Live Music Performance"
                safeSearchSongs(q, true, true) 
            }
            val albumsTask = async { 
                val q = if (queryModifier.isNotEmpty()) "Top Music Albums $queryModifier" else "Top Music Albums"
                try {
                    val searchResult = searchAll(q, cookies, filters = listOf("albums"))
                    searchResult.second.map { it.copy(title = cleanSongTitle(it.title)) } // Returns PlaylistDetail list which includes albums
                } catch(e: Exception) { emptyList<PlaylistDetail>() }
            }

            val trendingSongs = trendingTask.await()
            val newReleases = newReleasesTask.await()
            val videoSongs = videoSongsTask.await()
            
            val playlists = playlistsTask.await()
            val rainPlaylists = rainTask.await()
            val communityPlaylists = communityPlaylistsTask.await()
            val videoChartPlaylists = videoChartsTask.await()
            val artists = artistsTask.await()
            val livePerformances = livePerformancesTask.await()
            val albums = albumsTask.await()

            // Interleaved Order (Playlist -> Video -> Song -> Artist)
            if (playlists.isNotEmpty()) {
                shelves.add(HomeShelf("Featured Playlists", playlists.map {
                    mapOf("title" to it.title, "thumbnail" to it.thumbnailUrl, "browseId" to it.browseId, "type" to "playlist", "author" to it.description)
                }, "playlists"))
            }

            if (videoSongs.isNotEmpty()) shelves.add(HomeShelf("Official Video Songs", videoSongs, "videos"))

            if (trendingSongs.isNotEmpty()) shelves.add(HomeShelf("Trending Now", trendingSongs, "trending"))

            if (communityPlaylists.isNotEmpty()) {
                shelves.add(HomeShelf("Trending Community", communityPlaylists.map {
                    mapOf(
                        "title" to it.title, 
                        "thumbnail" to it.thumbnailUrl, 
                        "browseId" to it.browseId, 
                        "type" to "playlist", 
                        "author" to it.description
                    )
                }, "playlists"))
            }

            if (artists.isNotEmpty()) {
                shelves.add(HomeShelf("Discover Artists", artists.map {
                    mapOf("title" to it.name, "thumbnail" to it.thumbnailUrl, "browseId" to it.browseId, "type" to "artist")
                }, "artists"))
            }

            if (rainPlaylists.isNotEmpty()) {
                shelves.add(HomeShelf("Rain Therapy", rainPlaylists.map {
                    mapOf("title" to it.title, "thumbnail" to it.thumbnailUrl, "browseId" to it.browseId, "type" to "playlist", "author" to it.description)
                }, "playlists"))
            }

            if (newReleases.isNotEmpty()) shelves.add(HomeShelf("New Releases", newReleases, "songs"))

            if (videoChartPlaylists.isNotEmpty()) {
                shelves.add(HomeShelf("Video Charts", videoChartPlaylists.map {
                    mapOf("title" to it.title, "thumbnail" to it.thumbnailUrl, "browseId" to it.browseId, "type" to "playlist", "author" to it.description)
                }, "playlists"))
            }

            if (livePerformances.isNotEmpty()) {
                shelves.add(HomeShelf("Live Performances", livePerformances, "videos"))
            }

            if (albums.isNotEmpty()) {
                shelves.add(HomeShelf("Albums for You", albums.map {
                    mapOf("title" to it.title, "thumbnail" to it.thumbnailUrl, "browseId" to it.browseId, "type" to "album", "author" to it.description)
                }, "playlists"))
            }

            HomeResponse(
                quickPicks = trendingSongs.shuffled().take(12),
                trending = emptyList(), // Handled in shelves
                shelves = shelves
            )
        }
    }

    suspend fun fetchHomeContinuation(continuation: String, cookies: String?, selectedLanguages: Set<String>, location: String? = null): HomeResponse = withContext(Dispatchers.IO) {
        val languageSuffix = selectedLanguages.joinToString(" ")
        val locationSuffix = location ?: ""
        val queryModifier = "$languageSuffix $locationSuffix".trim()
        
        // Since we don't have a direct continuation for the mixed home feed yet,
        // we simulate it by fetching more relevant content based on user context.
        val q = if (queryModifier.isNotEmpty()) "Recommended Music $queryModifier" else "Recommended Music"
        val songs = try { searchSongs(q, cookies, true, false, true) } catch(e: Exception) { emptyList<Song>() }
        
        HomeResponse(
            shelves = listOf(HomeShelf("More for You", songs, "songs"))
        )
    }

    suspend fun fetchArtist(artistId: String, cookies: String?): ArtistDetail? = withContext(Dispatchers.IO) {
        try {
            val url = if (artistId.startsWith("UC")) {
                "https://music.youtube.com/channel/$artistId"
            } else if (artistId.startsWith("FMa")) {
                "https://music.youtube.com/browse/$artistId"
            } else {
                "https://music.youtube.com/browse/$artistId"
            }
            val channelInfo = ChannelInfo.getInfo(ytService, url)
            val name = channelInfo.name ?: ""
            
            // Parallel fetching for diverse artist content
            val songsTask = async { searchSongs(name, cookies, true, false) }
            val albumsTask = async { 
                try {
                    val searchResult = searchAll(name, cookies, filters = listOf("albums"))
                    searchResult.second.map { it.copy(title = cleanSongTitle(it.title)) } // Returns PlaylistDetail list which includes albums
                } catch(e: Exception) { emptyList<PlaylistDetail>() }
            }
            
            val songs = songsTask.await()
            val albums = albumsTask.await()
            
            val items = mutableListOf<Map<String, Any>>()
            albums.take(8).forEach {
                items.add(mapOf(
                    "title" to cleanSongTitle(it.title),
                    "thumbnail" to it.thumbnailUrl,
                    "browseId" to it.browseId,
                    "type" to "album"
                ))
            }

            ArtistDetail(
                name = name,
                description = cleanDescription(channelInfo.description ?: ""),
                thumbnailUrl = channelInfo.avatars?.maxByOrNull { it.width }?.url ?: "",
                songs = songs.take(20),
                browseId = artistId,
                songsBrowseId = artistId,
                items = items
            )
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Error fetching artist $artistId", e)
            null
        }
    }

    suspend fun getArtistChannel(artistName: String, cookies: String?): ArtistDetail? = withContext(Dispatchers.IO) {
        try {
            val searchInfo = SearchInfo.getInfo(ytService, ytService.searchQHFactory.fromQuery(artistName, listOf("channels"), ""))
            val channelItem = searchInfo.relatedItems.filterIsInstance<ChannelInfoItem>().firstOrNull() ?: return@withContext null
            fetchArtist(channelItem.url.substringAfterLast("/"), cookies)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchArtistSongs(browseId: String, params: String?, cookies: String?): List<Song> = withContext(Dispatchers.IO) {
        try {
            // If params are provided, they might be for a specific tab
            // For now, using search fallback as it's more reliable across different channel types
            searchSongs(browseId, cookies, true, false, filterOnlyAudio = true)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTopMusicVideos(cookies: String?): List<Song> = emptyList()

    private fun cleanDescription(raw: String): String {
        if (raw.isEmpty()) return ""
        
        var text = raw
        
        // Handle cases where description might be JSON-wrapped or contain escaped chars
        text = text.replace("\\n", "\n")
                   .replace("\\r", "")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\")

        // Decode HTML entities
        val decoded = try {
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: Exception) {
            text
        }

        return decoded
            .replace(Regex("\n{3,}"), "\n\n") // Standardize paragraph spacing
            .trim()
    }
}
