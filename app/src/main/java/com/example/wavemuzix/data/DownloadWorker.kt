package com.example.wavemuzix.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.wavemuzix.data.database.AppDatabase
import com.example.wavemuzix.data.database.DownloadedSongEntity
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var url = inputData.getString("url") ?: return Result.failure()
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown Title"
        val artist = inputData.getString("artist") ?: "Unknown Artist"
        val thumbnailUrl = inputData.getString("thumbnailUrl") ?: ""
        val artistId = inputData.getString("artistId")
        val userAgent = inputData.getString("userAgent") ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val cookies = inputData.getString("cookies")
        
        val downloadsDir = File(applicationContext.filesDir, "downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        
        val destinationFile = File(downloadsDir, "$videoId.mp3")
        var totalBytes = -1L
        var retryCount = 0
        val maxRetries = 5
        
        while (retryCount < maxRetries) {
            if (isStopped) return Result.retry()
            
            try {
                val downloadedBytes = if (destinationFile.exists()) destinationFile.length() else 0L
                Log.d("DownloadWorker", "Attempting download for $videoId. Current size: $downloadedBytes. Retry: $retryCount")
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Referer", "https://music.youtube.com/")
                connection.setRequestProperty("Origin", "https://music.youtube.com")
                connection.setRequestProperty("Accept", "*/*")
                if (!cookies.isNullOrEmpty()) {
                    connection.setRequestProperty("Cookie", cookies)
                }
                
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                
                val responseCode = connection.responseCode
                Log.d("DownloadWorker", "Response code: $responseCode for $videoId")
                
        // Handle expired URLs (Common with YouTube)
        if (responseCode == 403 || responseCode == 410 || responseCode == 401) {
            Log.w("DownloadWorker", "URL expired or unauthorized (HTTP $responseCode). Refreshing...")
            val newResult = MusicPlayer.unifiedRepository?.getStreamingUrl(
                videoId = videoId,
                cookies = cookies ?: MusicPlayer.cookies,
                failedClients = emptyList(),
                targetBitrate = null,
                priority = "Force OPUS 160kbps"
            )
            if (newResult != null) {
                url = newResult.url
                // We don't increment retryCount here, just try again with new URL
                continue 
            }
        }
                
                if (responseCode !in 200..299) {
                    throw IOException("HTTP $responseCode")
                }
                
                val isPartial = responseCode == 206
                
                // If we requested a range but got 200, the server doesn't support range or 
                // is sending the whole file. We must overwrite the existing file.
                val append = isPartial
                
                if (totalBytes == -1L) {
                    if (isPartial) {
                        val rangeHeader = connection.getHeaderField("Content-Range")
                        // Example: bytes 200-1000/1001
                        totalBytes = rangeHeader?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                    } else {
                        totalBytes = connection.contentLength.toLong()
                    }
                }
                
                val contentType = connection.contentType?.substringBefore(";")
                Log.d("DownloadWorker", "Content-Type: $contentType for $videoId, Append: $append")
                
                val useMultiThread = totalBytes > 512 * 1024 && isPartial && !isStopped
                
                if (useMultiThread) {
                    val settingsManager = SettingsManager(applicationContext)
                    val numThreads = settingsManager.downloadThreads
                    Log.d("DownloadWorker", "Starting multi-threaded download for $videoId ($totalBytes bytes) with $numThreads threads")
                    connection.disconnect()
                    val success = performMultiThreadedDownload(
                        url = url,
                        destinationFile = destinationFile,
                        totalBytes = totalBytes,
                        downloadedBytes = downloadedBytes,
                        numThreads = numThreads,
                        userAgent = userAgent,
                        cookies = cookies,
                        videoId = videoId,
                        title = title
                    )
                    if (!success) throw IOException("Multi-threaded download failed or was interrupted")
                } else {
                    try {
                        connection.inputStream.use { input ->
                            // Use append mode if it's a partial response
                            FileOutputStream(destinationFile, append).use { output ->
                                val buffer = ByteArray(65536) // 64KB buffer
                                var bytesRead: Int
                                var lastProgressUpdate = 0L
                                
                                while (true) {
                                    bytesRead = try {
                                        input.read(buffer)
                                    } catch (e: SocketException) {
                                        Log.w("DownloadWorker", "Socket reset during read for $videoId", e)
                                        throw e // Trigger retry
                                    }
                                    
                                    if (bytesRead == -1) break
                                    
                                    output.write(buffer, 0, bytesRead)
                                    
                                    val currentSize = destinationFile.length()
                                    if (totalBytes > 0) {
                                        val progress = (currentSize * 100 / totalBytes).toInt()
                                        val now = System.currentTimeMillis()
                                        if (now - lastProgressUpdate > 1000) {
                                            setProgress(workDataOf("progress" to progress, "title" to title))
                                            withContext(Dispatchers.Main) {
                                                DownloadManager.updateProgress(videoId, title, progress)
                                            }
                                            lastProgressUpdate = now
                                        }
                                    }
                                    if (isStopped) throw IOException("Worker stopped")
                                }
                                output.flush()
                                output.fd.sync()
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                
                // Final verification
                val finalSize = destinationFile.length()
                if (totalBytes > 0 && finalSize < totalBytes) {
                    throw IOException("Incomplete download: $finalSize / $totalBytes")
                }
                
                Log.d("DownloadWorker", "Download successful: $videoId. Fetching lyrics and thumbnails.")
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.songDao()

                // Download Thumbnail
                var localThumbnailPath: String? = null
                if (thumbnailUrl.isNotEmpty()) {
                    try {
                        val thumbFile = File(downloadsDir, "$videoId.jpg")
                        val highResUrl = MusicPlayer.getHighResThumbnailUrl(thumbnailUrl)
                        
                        // Try high-res first, then fallback to original thumbnailUrl if it differs
                        val urlsToTry = if (highResUrl != null && highResUrl != thumbnailUrl) {
                            listOf(highResUrl, thumbnailUrl)
                        } else {
                            listOf(thumbnailUrl)
                        }
                        var thumbSuccess = false
                        
                        for (thumbUrl in urlsToTry) {
                            try {
                                Log.d("DownloadWorker", "Trying thumbnail URL: $thumbUrl")
                                val thumbConn = URL(thumbUrl).openConnection() as HttpURLConnection
                                thumbConn.setRequestProperty("User-Agent", userAgent)
                                thumbConn.connectTimeout = 10000
                                thumbConn.readTimeout = 10000
                                val responseCode = thumbConn.responseCode
                                Log.d("DownloadWorker", "Thumbnail response code: $responseCode for $thumbUrl")
                                if (responseCode == 200) {
                                    thumbConn.inputStream.use { input ->
                                        FileOutputStream(thumbFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    localThumbnailPath = thumbFile.absolutePath
                                    Log.d("DownloadWorker", "Thumbnail saved to: $localThumbnailPath")
                                    thumbSuccess = true
                                    break
                                }
                            } catch (e: Exception) {
                                Log.w("DownloadWorker", "Failed to download thumbnail from $thumbUrl: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadWorker", "Failed to download thumbnail", e)
                    }
                }

                // Fetch Lyrics
                var lyrics: String? = null
                try {
                    val lyricsRepo = com.example.wavemuzix.repository.LyricsRepository()
                    val settingsManager = com.example.wavemuzix.data.SettingsManager(applicationContext)
                    lyrics = lyricsRepo.fetchLyrics(
                        com.example.wavemuzix.model.Song(
                            id = videoId,
                            videoId = videoId,
                            title = title,
                            artist = artist,
                            thumbnailUrl = thumbnailUrl,
                            artistId = artistId
                        ),
                        preferredProvider = settingsManager.lyricsProvider,
                        dao = dao
                    )?.lyrics
                } catch (e: Exception) {
                    Log.e("DownloadWorker", "Failed to fetch lyrics", e)
                }

                val entity = DownloadedSongEntity(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    thumbnailUrl = thumbnailUrl,
                    artistId = artistId,
                    filePath = destinationFile.absolutePath,
                    mimeType = contentType,
                    thumbnailPath = localThumbnailPath,
                    lyrics = lyrics,
                    downloadTimestamp = System.currentTimeMillis()
                )
                
                dao.insertDownload(entity)
                
                // Save metadata backup for offline recovery
                try {
                    val metadataFile = File(downloadsDir, "$videoId.json")
                    metadataFile.writeText(Gson().toJson(entity))
                } catch (e: Exception) {
                    Log.e("DownloadWorker", "Failed to save metadata backup", e)
                }

                withContext(Dispatchers.Main) {
                    DownloadManager.finishDownload(videoId, title)
                    android.widget.Toast.makeText(applicationContext, "Download complete: $title", android.widget.Toast.LENGTH_SHORT).show()
                }
                
                return Result.success()
                
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                
                retryCount++
                Log.e("DownloadWorker", "Error on attempt $retryCount for $videoId: ${e.message}")
                
                // If it's a network error, try to refresh the URL for the next attempt
                if (e is SocketException || e is IOException) {
                    Log.w("DownloadWorker", "Network error during download. Refreshing URL...")
                    try {
                        val newResult = MusicPlayer.unifiedRepository?.getStreamingUrl(
                            videoId = videoId,
                            cookies = cookies ?: MusicPlayer.cookies,
                            failedClients = emptyList(),
                            targetBitrate = null,
                            priority = "Force OPUS 160kbps"
                        )
                        if (newResult != null) {
                            url = newResult.url
                        }
                    } catch (refreshError: Exception) {
                        Log.e("DownloadWorker", "Failed to refresh URL: ${refreshError.message}")
                    }
                }
                
                if (retryCount >= maxRetries) {
                    withContext(Dispatchers.Main) {
                        DownloadManager.activeDownloads.removeIf { it.videoId == videoId }
                        android.widget.Toast.makeText(applicationContext, "Download failed: $title", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return Result.failure()
                }
                
                // Exponential backoff
                delay(2000L * retryCount)
            }
        }
        
        return Result.failure()
    }

    private suspend fun performMultiThreadedDownload(
        url: String,
        destinationFile: File,
        totalBytes: Long,
        downloadedBytes: Long,
        numThreads: Int,
        userAgent: String,
        cookies: String?,
        videoId: String,
        title: String
    ): Boolean = coroutineScope {
        val remainingBytes = totalBytes - downloadedBytes
        if (remainingBytes <= 0) return@coroutineScope true
        
        val chunkSize = remainingBytes / numThreads
        val lastProgressUpdate = AtomicLong(0)
        val totalDownloaded = AtomicLong(downloadedBytes)

        // Pre-allocate file space to avoid fragmentation and ensure capacity
        withContext(Dispatchers.IO) {
            RandomAccessFile(destinationFile, "rw").use { it.setLength(totalBytes) }
        }

        val deferreds = (0 until numThreads).map { i ->
            async(Dispatchers.IO) {
                val start = downloadedBytes + i * chunkSize
                val end = if (i == numThreads - 1) totalBytes - 1 else downloadedBytes + (i + 1) * chunkSize - 1
                
                var currentPos = start
                var chunkSuccess = false
                var chunkRetries = 0
                
                while (chunkRetries < 3 && !chunkSuccess) {
                    if (isStopped) return@async false
                    try {
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", userAgent)
                        conn.setRequestProperty("Range", "bytes=$currentPos-$end")
                        conn.setRequestProperty("Referer", "https://music.youtube.com/")
                        conn.setRequestProperty("Origin", "https://music.youtube.com")
                        if (!cookies.isNullOrEmpty()) conn.setRequestProperty("Cookie", cookies)
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        
                        val responseCode = conn.responseCode
                        if (responseCode == 206 || (currentPos == 0L && responseCode == 200)) {
                            RandomAccessFile(destinationFile, "rw").use { raf ->
                                raf.seek(currentPos)
                                conn.inputStream.use { input ->
                                    val buffer = ByteArray(16384)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        if (isStopped) throw IOException("Stopped")
                                        raf.write(buffer, 0, bytesRead)
                                        currentPos += bytesRead
                                        val total = totalDownloaded.addAndGet(bytesRead.toLong())
                                        
                                        val now = System.currentTimeMillis()
                                        val last = lastProgressUpdate.get()
                                        if (now - last > 1000) {
                                            if (lastProgressUpdate.compareAndSet(last, now)) {
                                                val progress = (total * 100 / totalBytes).toInt()
                                                setProgress(workDataOf("progress" to progress, "title" to title))
                                                withContext(Dispatchers.Main) {
                                                    DownloadManager.updateProgress(videoId, title, progress)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            chunkSuccess = true
                        } else if (responseCode == 403 || responseCode == 410 || responseCode == 401) {
                            return@async false // URL expired
                        } else {
                            throw IOException("Unexpected response code $responseCode")
                        }
                    } catch (e: Exception) {
                        chunkRetries++
                        if (e.message == "Stopped") return@async false
                        delay(500L * chunkRetries)
                    }
                }
                chunkSuccess
            }
        }
        deferreds.awaitAll().all { it }
    }
}

