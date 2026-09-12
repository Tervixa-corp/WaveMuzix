package com.example.wavemuzix.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.wavemuzix.model.Song
import com.example.wavemuzix.data.database.AppDatabase
import com.example.wavemuzix.data.database.DownloadedSongEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.mpatric.mp3agic.ID3v23Tag
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

data class DownloadProgress(
    val videoId: String,
    val title: String,
    val progress: Int,
    val isFinished: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object DownloadManager {
    val activeDownloads = mutableStateListOf<DownloadProgress>()
    val latestNotification = mutableStateOf<DownloadProgress?>(null)
    
    // For the Notifications screen history/active list
    val downloadHistory = mutableStateListOf<DownloadProgress>()
    val ignoredNotifications = mutableSetOf<String>()

    fun updateProgress(videoId: String, title: String, progress: Int) {
        if (ignoredNotifications.contains(videoId)) return

        val update = DownloadProgress(videoId, title, progress)
        
        // Update active list
        val existingIndex = activeDownloads.indexOfFirst { it.videoId == videoId }
        if (existingIndex != -1) {
            activeDownloads[existingIndex] = update
        } else {
            activeDownloads.add(update)
        }

        // Update latest notification if it's the same song or if nothing is showing
        if (latestNotification.value == null || latestNotification.value?.videoId == videoId) {
            latestNotification.value = update
        }
    }

    fun finishDownload(videoId: String, title: String) {
        activeDownloads.removeIf { it.videoId == videoId }
        val finished = DownloadProgress(videoId, title, 100, isFinished = true)
        
        if (!ignoredNotifications.contains(videoId)) {
            latestNotification.value = finished
        }
        
        downloadHistory.add(0, finished)
    }
    
    fun ignoreNotification(videoId: String) {
        ignoredNotifications.add(videoId)
        if (latestNotification.value?.videoId == videoId) {
            latestNotification.value = null
        }
    }

    fun startNewDownload(videoId: String) {
        ignoredNotifications.remove(videoId)
    }
    
    fun cancelDownload(context: Context, videoId: String) {
        activeDownloads.removeIf { it.videoId == videoId }
        if (latestNotification.value?.videoId == videoId) {
            latestNotification.value = null
        }
        // Actually cancel the WorkManager job
        androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(videoId)
    }

    fun cancelAllDownloads(context: Context) {
        activeDownloads.clear()
        latestNotification.value = null
        androidx.work.WorkManager.getInstance(context).cancelAllWork()
    }
    
    fun clearLatestNotification() {
        latestNotification.value = null
    }

    fun downloadSong(context: android.content.Context, song: com.example.wavemuzix.model.Song, repository: com.example.wavemuzix.repository.UnifiedMusicRepository?, cookies: String?) {
        if (repository == null) return
        
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        

        scope.launch {
            val result = repository.getStreamingUrl(
                videoId = song.videoId,
                cookies = cookies
            )
            if (result != null) {
                val request = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(androidx.work.workDataOf(
                        "url" to result.url,
                        "videoId" to song.videoId,
                        "title" to song.title,
                        "artist" to song.artist,
                        "thumbnailUrl" to song.thumbnailUrl,
                        "artistId" to song.artistId,
                        "userAgent" to result.userAgent,
                        "cookies" to cookies
                    ))
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueue(request)
            }
        }
    }

    suspend fun syncWithStorage(context: Context) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val dao = db.songDao()
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) return@withContext

            val files = downloadsDir.listFiles() ?: return@withContext
            files.filter { it.extension == "json" }.forEach { jsonFile ->
                try {
                    val videoId = jsonFile.nameWithoutExtension
                    val existing = dao.getDownloadByVideoId(videoId)
                    if (existing == null) {
                        val metadata = Gson().fromJson(jsonFile.readText(), DownloadedSongEntity::class.java)
                        // Verify the mp3 still exists before restoring
                        val mp3File = File(downloadsDir, "$videoId.mp3")
                        if (mp3File.exists()) {
                            dao.insertDownload(metadata)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DownloadManager", "Failed to restore metadata for ${jsonFile.name}", e)
                }
            }
        }
    }

    suspend fun exportSongToStorage(context: Context, song: Song, filePath: String, mimeType: String? = null, showToast: Boolean = true): Boolean {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            if (showToast) withContext(Dispatchers.Main) { Toast.makeText(context, "Source file not found", Toast.LENGTH_SHORT).show() }
            return false
        }

        // Determine actual format. If it's not MP3, we shouldn't use mp3agic.
        val resolver = context.contentResolver
        val fileName = "${song.title} - ${song.artist}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        
        // Use provided mimeType or infer from file extension
        val finalMimeType = mimeType ?: when (sourceFile.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "audio/webm"
            "ogg" -> "audio/ogg"
            else -> "audio/mpeg"
        }

        val extension = when (finalMimeType) {
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/webm" -> "webm"
            "audio/ogg" -> "ogg"
            else -> sourceFile.extension.ifEmpty { "mp3" }
        }

        val audioContentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.$extension")
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            put(MediaStore.Audio.Media.MIME_TYPE, finalMimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Wave Muzix")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        var audioUri = try {
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioContentValues)
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("Export", "MIME type $finalMimeType rejected by MediaStore. Falling back to audio/mpeg.")
            // Fallback for unsupported MIME types (like audio/webm on some devices)
            audioContentValues.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            try {
                resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioContentValues)
            } catch (e2: Exception) {
                android.util.Log.e("Export", "Fallback MIME type also failed", e2)
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("Export", "Failed to insert into MediaStore", e)
            null
        }

        if (audioUri == null) {
            if (showToast) withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show() }
            return false
        }

        return try {
            withContext(Dispatchers.IO) {
                // Try tagging only if it's an MP3. mp3agic is strict and will fail on AAC/Opus.
                var exportedSuccessfully = false
                if (finalMimeType == "audio/mpeg") {
                    val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}_${song.id}.mp3")
                    try {
                        val mp3file = Mp3File(sourceFile.absolutePath)
                        val id3v2Tag = if (mp3file.hasId3v2Tag()) mp3file.id3v2Tag else ID3v23Tag()
                        
                        id3v2Tag.title = song.title
                        id3v2Tag.artist = song.artist
                        id3v2Tag.album = "Wave Muzix"
                        
                        var imageData: ByteArray? = null
                        song.thumbnailPath?.let { path ->
                            val file = File(path)
                            if (file.exists()) imageData = file.readBytes()
                        }
                        
                        if (imageData == null && song.thumbnailUrl.isNotEmpty()) {
                            try {
                                val url = URL(song.thumbnailUrl)
                                val connection = url.openConnection() as HttpURLConnection
                                connection.doInput = true
                                connection.connect()
                                imageData = connection.inputStream.readBytes()
                            } catch (e: Exception) {
                                android.util.Log.e("Export", "Failed to download thumbnail for ID3 tag", e)
                            }
                        }

                        if (imageData != null) {
                            id3v2Tag.setAlbumImage(imageData, "image/jpeg")
                        }
                        
                        mp3file.id3v2Tag = id3v2Tag
                        mp3file.save(tempFile.absolutePath)

                        resolver.openOutputStream(audioUri)?.use { outputStream ->
                            Files.copy(tempFile.toPath(), outputStream)
                        }
                        tempFile.delete()
                        exportedSuccessfully = true
                    } catch (e: Exception) {
                        android.util.Log.w("Export", "Tagging failed for ${song.title}, falling back to simple copy: ${e.message}")
                        // Fallback to simple copy if mp3agic fails (e.g. "No mpegs frames found")
                    }
                }

                // If tagging failed or it's not an MP3, do a direct copy
                if (!exportedSuccessfully) {
                    resolver.openOutputStream(audioUri)?.use { outputStream ->
                        Files.copy(sourceFile.toPath(), outputStream)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    audioContentValues.clear()
                    audioContentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(audioUri, audioContentValues, null, null)
                }
            }
            
            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Exported: ${song.title}", Toast.LENGTH_SHORT).show()
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("Export", "Failed to export song", e)
            resolver.delete(audioUri, null, null)
            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            false
        }
    }

    suspend fun exportAllSongsToStorage(context: Context, songs: List<DownloadedSongEntity>) {
        val totalSongs = songs.size
        if (totalSongs == 0) return

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Exporting $totalSongs songs to Music folder...", Toast.LENGTH_SHORT).show()
        }

        val semaphore = Semaphore(3)

        val results = withContext(Dispatchers.IO) {
            songs.map { song ->
                async {
                    semaphore.withPermit {
                        val songModel = Song(
                            id = song.videoId,
                            videoId = song.videoId,
                            title = song.title,
                            artist = song.artist,
                            thumbnailUrl = song.thumbnailUrl,
                            artistId = song.artistId,
                            thumbnailPath = song.thumbnailPath
                        )
                        try {
                            exportSongToStorage(context, songModel, song.filePath, song.mimeType, showToast = false)
                        } catch (e: Exception) {
                            android.util.Log.e("DownloadManager", "Failed to export ${song.title}", e)
                            false
                        }
                    }
                }
            }.awaitAll()
        }

        val successCount = results.count { it }
        val failCount = results.size - successCount

        withContext(Dispatchers.Main) {
            val message = if (failCount == 0) {
                "Successfully exported all $successCount songs!"
            } else {
                "Export complete: $successCount successful, $failCount failed."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
