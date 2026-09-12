package com.example.wavemuzix.repository

import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.channel.ChannelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoDetails(
    val videoId: String,
    val channelName: String,
    val channelThumbnailUrl: String,
    val subscriberCountText: String? = null,
    val description: String,
    val tags: List<String> = emptyList(),
    val category: String? = null
)

class YtDescriptionRepository {

    private val ytService: YoutubeService by lazy {
        NewPipe.getService(0) as YoutubeService
    }

    /**
     * LOCAL EXTRACTION:
     * Migrated to NewPipeExtractor. Fetches video metadata locally.
     */
    suspend fun fetchVideoDetails(videoId: String, cookies: String? = null): VideoDetails? {
        Log.d("YtDescriptionRepo", "Fetching details via NewPipe for $videoId")
        return withContext(Dispatchers.IO) {
            try {
                // Use standard YouTube URL for better metadata extraction compatibility
                val url = "https://www.youtube.com/watch?v=$videoId"
                val streamInfo = StreamInfo.getInfo(ytService, url)
                
                // Fetch Channel Profile Picture (Real Fallback for Artist Avatar)
                val channelInfo = try {
                    if (!streamInfo.uploaderUrl.isNullOrEmpty()) {
                        ChannelInfo.getInfo(ytService, streamInfo.uploaderUrl)
                    } else null
                } catch (e: Exception) {
                    Log.w("YtDescriptionRepo", "Failed to fetch channel info for avatar", e)
                    null
                }

                VideoDetails(
                    videoId = videoId,
                    channelName = streamInfo.uploaderName ?: "Unknown Artist",
                    channelThumbnailUrl = channelInfo?.avatars?.maxByOrNull { it.width }?.url
                                         ?: streamInfo.thumbnails.maxByOrNull { it.width }?.url ?: "",
                    subscriberCountText = channelInfo?.subscriberCount?.takeIf { it > 0 }?.let { count ->
                        when {
                            count >= 1_000_000 -> "%.1fM subscribers".format(count / 1_000_000.0)
                            count >= 1_000 -> "%.1fK subscribers".format(count / 1_000.0)
                            else -> "$count subscribers"
                        }
                    },
                    description = cleanDescription(streamInfo.description?.content ?: ""),
                    tags = streamInfo.tags ?: emptyList(),
                    category = streamInfo.category
                )
            } catch (e: Exception) {
                Log.e("YtDescriptionRepo", "Error fetching details locally", e)
                null
            }
        }
    }

    private fun cleanDescription(raw: String): String {
        if (raw.isEmpty()) return "No description available."
        
        var text = raw
        
        // Handle cases where description might be JSON-wrapped or contain escaped chars
        // This is common in some extractors that return raw string data
        text = text.replace("\\n", "\n")
                   .replace("\\r", "")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\")

        // Decode HTML entities
        val decoded = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()

        return decoded
            .replace(Regex("\n{3,}"), "\n\n") // Standardize paragraph spacing
            .trim()
    }
}
