package com.example.wavemuzix.repository

class AudioStreamRepository(
    private val youtubeRepository: YouTubeRepository
) {
    suspend fun getStreamingUrl(
        videoId: String,
        cookies: String?,
        failedClients: List<String> = emptyList(),
        targetBitrate: Int? = null,
        priority: String = "Force OPUS 160kbps",
        preferredVideoQuality: String = "auto"
    ): StreamingResult? {
        return youtubeRepository.getStreamingUrl(videoId, cookies, failedClients, targetBitrate, priority, preferredVideoQuality)
    }
}
