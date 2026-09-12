package com.example.wavemuzix.repository

import com.example.wavemuzix.model.Song
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    data class LyricsResult(val lyrics: String, val provider: String)

    private val lyricsCache = java.util.concurrent.ConcurrentHashMap<String, LyricsResult>()
    private val ongoingRequests = java.util.concurrent.ConcurrentHashMap<String, Deferred<LyricsResult?>>()
    
    private var lastFetchedSongId: String? = null
    fun getLastSongId(): String? = lastFetchedSongId

    fun getCachedLyrics(videoId: String): String? = lyricsCache[videoId]?.lyrics
    fun getCachedProvider(videoId: String): String? = lyricsCache[videoId]?.provider

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(official (music )?video\\)"), "")
            .replace(Regex("(?i)\\[official (music )?video\\]"), "")
            .replace(Regex("(?i)official (music )?video"), "")
            .replace(Regex("(?i)lyric video"), "")
            .replace(Regex("(?i)official audio"), "")
            .replace(Regex("(?i)high quality"), "")
            .replace(Regex("(?i)full song"), "")
            .replace(Regex("(?i)video song"), "")
            .replace(Regex("(?i)MV"), "")
            .replace(Regex("(?i)HD"), "")
            .replace(Regex("(?i)4K"), "")
            .replace(Regex("(?i)\\(lyrics\\)"), "")
            .replace(Regex("(?i)\\[lyrics\\]"), "")
            .replace(Regex("(?i)\\(feat\\.? .*?\\)"), "")
            .replace(Regex("(?i)\\[feat\\.? .*?\\]"), "")
            .replace(Regex("(?i)feat\\.? .*"), "")
            .replace(Regex("\\|.*"), "") 
            .trim()
    }

    suspend fun fetchLyrics(song: Song, preferredProvider: String = "Auto", dao: com.example.wavemuzix.data.database.SongDao? = null): LyricsResult? = coroutineScope {
        lastFetchedSongId = song.videoId
        lyricsCache[song.videoId]?.let { return@coroutineScope it }
        
        // 1. Check Database Cache
        if (dao != null) {
            try {
                val dbResult = dao.getLyricsByVideoId(song.videoId)
                if (dbResult != null) {
                    val result = LyricsResult(dbResult.lyrics, dbResult.provider)
                    lyricsCache[song.videoId] = result
                    return@coroutineScope result
                }
            } catch (e: Exception) {
                android.util.Log.e("LyricsRepo", "DB read error", e)
            }
        }

        ongoingRequests[song.videoId]?.let { return@coroutineScope it.await() }

        val deferred = async(Dispatchers.IO) {
            try {
                val cleanedTitle = cleanTitle(song.title)
                // Extraction of primary artist (e.g., "Artist 1, Artist 2" -> "Artist 1")
                val primaryArtist = song.artist
                    .split(Regex("(?i),|feat\\.?|&|/| x "))[0]
                    .replace(Regex("(?i)- Topic$"), "")
                    .trim()

                android.util.Log.d("LyricsRepo", "Fetching lyrics ($preferredProvider) for: $primaryArtist - $cleanedTitle (Full Artist: ${song.artist})")

                val providers = mutableListOf<String>()
                if (preferredProvider == "Auto" || preferredProvider.isBlank()) {
                    providers.addAll(listOf("Alsong", "Netease", "LRCLIB"))
                } else {
                    providers.add(preferredProvider)
                    // Add fallbacks
                    if (preferredProvider != "Alsong") providers.add("Alsong")
                    if (preferredProvider != "Netease") providers.add("Netease")
                    if (preferredProvider != "LRCLIB") providers.add("LRCLIB")
                }

                // Prepare variations for search
                val searchVariations = mutableListOf<Pair<String, String>>()
                searchVariations.add(primaryArtist to cleanedTitle)
                
                // If title contains " - ", it's likely "Artist - Song" or "Song - Artist"
                if (cleanedTitle.contains(" - ")) {
                    val parts = cleanedTitle.split(" - ", limit = 2)
                    searchVariations.add(parts[0].trim() to parts[1].trim())
                    searchVariations.add(parts[1].trim() to parts[0].trim())
                }
                
                // Add version with full artist name as fallback
                if (song.artist != primaryArtist) {
                    searchVariations.add(song.artist to cleanedTitle)
                }
                
                // Add version with only title (useful for very unique song titles)
                searchVariations.add("" to cleanedTitle)

                for (provider in providers) {
                    for ((artistQuery, titleQuery) in searchVariations) {
                        if (artistQuery.isBlank() && titleQuery.isBlank()) continue
                        
                        val result = try {
                            when (provider) {
                                "LRCLIB" -> fetchLrcLibLyrics(artistQuery, titleQuery)
                                "Netease" -> fetchNeteaseLyrics(artistQuery, titleQuery)
                                "Alsong" -> fetchAlsongLyrics(artistQuery, titleQuery)
                                else -> null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LyricsRepo", "Error fetching from $provider with $artistQuery - $titleQuery", e)
                            null
                        }

                        if (result != null) {
                            android.util.Log.d("LyricsRepo", "SUCCESS: Found lyrics from $provider for $artistQuery - $titleQuery")
                            val lyricsResult = LyricsResult(result, provider)
                            
                            // 2. Save to Database Cache
                            if (dao != null) {
                                try {
                                    dao.insertLyrics(com.example.wavemuzix.data.database.LyricsCacheEntity(
                                        videoId = song.videoId,
                                        lyrics = result,
                                        provider = provider
                                    ))
                                } catch (e: Exception) {
                                    android.util.Log.e("LyricsRepo", "DB write error", e)
                                }
                            }
                            
                            return@async lyricsResult
                        }
                    }
                }

                android.util.Log.d("LyricsRepo", "FAILURE: No lyrics found for ${song.title}")
                null
            } catch (e: Exception) {
                android.util.Log.e("LyricsRepo", "Global error in fetchLyrics", e)
                null
            } finally {
                ongoingRequests.remove(song.videoId)
            }
        }

        ongoingRequests[song.videoId] = deferred
        val result = deferred.await()
        if (result != null) lyricsCache[song.videoId] = result
        result
    }

    private suspend fun fetchAlsongLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("LyricsRepo", "Alsong: searching for '$artist' - '$title'")
            
            val escapedTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            val escapedArtist = artist.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            
            // Standard Alsong SOAP request for GetResembleLyricList2
            val xml = """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetResembleLyricList2 xmlns="ALSongWebServer">
      <stQuery>
        <strTitle>$escapedTitle</strTitle>
        <strArtistName>$escapedArtist</strArtistName>
        <nCurPageNum>0</nCurPageNum>
      </stQuery>
    </GetResembleLyricList2>
  </soap:Body>
</soap:Envelope>"""
            
            val mediaType = "text/xml; charset=utf-8".toMediaTypeOrNull()
            val body = xml.toRequestBody(mediaType)
            val request = Request.Builder()
                .url("http://lyrics.alsong.co.kr/alsongwebservice/service1.asmx")
                .post(body)
                .addHeader("SOAPAction", "ALSongWebServer/GetResembleLyricList2")
                .addHeader("User-Agent", "Alsong Android")
                .build()
            
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: return@use
                
                // Find all strLyric tags and take the first non-empty one that contains timestamps
                val lyricRegex = Regex("<strLyric>(.*?)</strLyric>", RegexOption.DOT_MATCHES_ALL)
                val matches = lyricRegex.findAll(responseBody)
                
                for (match in matches) {
                    val lrc = match.groupValues[1]
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                        .replace("<br>", "\n")
                        .replace("&#xD;", "")
                        .replace("&#xA;", "\n")
                        .trim()
                    
                    if (lrc.isNotBlank() && lrc != "null" && lrc.contains("[")) {
                        android.util.Log.d("LyricsRepo", "Alsong: Found valid lyrics (Length: ${lrc.length})")
                        return@withContext lrc
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsRepo", "Alsong error", e)
        }
        null
    }

    private suspend fun fetchNeteaseLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = if (artist.isBlank()) title else "$artist $title"
            android.util.Log.d("LyricsRepo", "Netease: searching for '$query'")
            
            // Using PC cloudsearch API which is more robust
            val searchUrl = "https://music.163.com/api/cloudsearch/get/web?s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&total=true&limit=5"
            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", userAgent)
                .addHeader("Referer", "https://music.163.com")
                .addHeader("Cookie", "os=pc; osver=Microsoft-Windows-10-Professional-build-10586-64bit; appver=2.0.3.131777")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val jsonResponse = JSONObject(body)
                val resultObj = jsonResponse.optJSONObject("result") ?: return@use
                val songs = resultObj.optJSONArray("songs") ?: return@use
                
                if (songs.length() > 0) {
                    // Try top 3 results
                    for (i in 0 until minOf(songs.length(), 3)) {
                        val id = songs.getJSONObject(i).getLong("id")
                        val lyricUrl = "https://music.163.com/api/song/lyric?id=$id&lv=1&kv=1&tv=-1"
                        val lyricRequest = Request.Builder()
                            .url(lyricUrl)
                            .addHeader("User-Agent", userAgent)
                            .addHeader("Referer", "https://music.163.com")
                            .build()

                        val lrc = client.newCall(lyricRequest).execute().use { lyrResponse ->
                            val lyrBody = lyrResponse.body?.string() ?: ""
                            val lyrJson = JSONObject(lyrBody)
                            val lrcObj = lyrJson.optJSONObject("lrc")
                            lrcObj?.optString("lyric")
                        }

                        if (!lrc.isNullOrBlank() && lrc != "null") {
                            // Filter out instrumentals
                            if (lrc.contains("纯音乐") || lrc.contains("None") || lrc.length < 10) {
                                continue
                            }
                            android.util.Log.d("LyricsRepo", "Netease: Found lyrics for ID $id")
                            return@withContext lrc
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsRepo", "Netease error", e)
        }
        null
    }

    private suspend fun fetchLrcLibLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("LyricsRepo", "LRCLIB: searching for '$artist' - '$title'")
            // 1. Try 'get' (exact match)
            val getUrl = "https://lrclib.net/api/get?artist_name=${URLEncoder.encode(artist, "UTF-8")}&track_name=${URLEncoder.encode(title, "UTF-8")}"
            client.newCall(Request.Builder().url(getUrl).header("User-Agent", userAgent).build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotEmpty() && body != "null") {
                        val json = JSONObject(body)
                        val lyrics = json.optString("syncedLyrics").takeIf { !it.isNullOrBlank() && it != "null" }
                            ?: json.optString("plainLyrics").takeIf { !it.isNullOrBlank() && it != "null" }
                        if (lyrics != null) return@withContext lyrics
                    }
                }
            }

            // 2. Try 'search' (fuzzy match)
            val searchUrl = "https://lrclib.net/api/search?q=${URLEncoder.encode("$artist $title", "UTF-8")}"
            client.newCall(Request.Builder().url(searchUrl).header("User-Agent", userAgent).build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotEmpty() && body != "null") {
                        val results = JSONArray(body)
                        for (i in 0 until results.length()) {
                            val match = results.getJSONObject(i)
                            val lyrics = match.optString("syncedLyrics").takeIf { !it.isNullOrBlank() && it != "null" }
                                ?: match.optString("plainLyrics").takeIf { !it.isNullOrBlank() && it != "null" }
                            if (lyrics != null) return@withContext lyrics
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsRepo", "LRCLIB error", e)
        }
        null
    }

    private fun stripLrc(lrc: String): String {
        return lrc.replace(Regex("\\[\\d{2}:\\d{2}[:.]\\d{2,3}\\]"), "").trim()
    }
}
