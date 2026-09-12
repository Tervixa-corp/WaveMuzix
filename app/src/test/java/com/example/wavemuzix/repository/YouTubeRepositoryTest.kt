package com.example.wavemuzix.repository

import android.util.Log
import com.example.wavemuzix.data.YouTubeApiService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class YouTubeRepositoryTest {

    @MockK
    lateinit var apiService: YouTubeApiService

    private lateinit var repository: YouTubeRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        repository = YouTubeRepository(apiService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `searchSongs returns list of songs on success`() = runBlocking {
        // Given
        val query = "test query"
        val mockResponseMap = mapOf(
            "contents" to mapOf(
                "tabbedSearchResultsRenderer" to mapOf(
                    "tabs" to listOf(
                        mapOf(
                            "tabRenderer" to mapOf(
                                "content" to mapOf(
                                    "sectionListRenderer" to mapOf(
                                        "contents" to listOf(
                                            mapOf(
                                                "musicShelfRenderer" to mapOf(
                                                    "contents" to listOf(
                                                        mapOf(
                                                            "musicResponsiveListItemRenderer" to mapOf(
                                                                "flexColumns" to listOf(
                                                                    mapOf(
                                                                        "musicResponsiveListItemFlexColumnRenderer" to mapOf(
                                                                            "text" to mapOf("runs" to listOf(mapOf("text" to "Song Title")))
                                                                        )
                                                                    ),
                                                                    mapOf(
                                                                        "musicResponsiveListItemFlexColumnRenderer" to mapOf(
                                                                            "text" to mapOf("runs" to listOf(mapOf("text" to "Artist Name")))
                                                                        )
                                                                    )
                                                                ),
                                                                "playlistItemData" to mapOf("videoId" to "v123"),
                                                                "thumbnail" to mapOf(
                                                                    "musicThumbnailRenderer" to mapOf(
                                                                        "thumbnail" to mapOf("thumbnails" to listOf(mapOf("url" to "http://thumb.url")))
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        
        coEvery { apiService.searchSongs(any(), any(), any(), any(), any(), any(), any()) } returns Response.success(mockResponseMap)

        // When
        val result = repository.searchSongs(query, null)

        // Then
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Song Title", result[0].title)
        assertEquals("Artist Name", result[0].artist)
        assertEquals("v123", result[0].videoId)
        assertEquals("http://thumb.url", result[0].thumbnailUrl)
    }

    @Test
    fun `searchSongs returns empty list on API error`() = runBlocking {
        // Given
        coEvery { apiService.searchSongs(any(), any(), any(), any(), any(), any(), any()) } returns Response.error(500, okhttp3.ResponseBody.create(null, ""))

        // When
        val result = repository.searchSongs("query", null)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchHome returns HomeResponse on success`() = runBlocking {
        // Given
        val mockResponseMap = mapOf(
            "contents" to mapOf(
                "singleColumnBrowseResultsRenderer" to mapOf(
                    "tabs" to listOf(
                        mapOf(
                            "tabRenderer" to mapOf(
                                "content" to mapOf(
                                    "sectionListRenderer" to mapOf(
                                        "contents" to listOf(
                                            mapOf(
                                                "musicCarouselShelfRenderer" to mapOf(
                                                    "header" to mapOf(
                                                        "musicCarouselShelfBasicHeaderRenderer" to mapOf(
                                                            "title" to mapOf("runs" to listOf(mapOf("text" to "Quick picks")))
                                                        )
                                                    ),
                                                    "contents" to listOf(
                                                        mapOf(
                                                            "musicResponsiveListItemRenderer" to mapOf(
                                                                "flexColumns" to listOf(
                                                                    mapOf("musicResponsiveListItemFlexColumnRenderer" to mapOf("text" to mapOf("runs" to listOf(mapOf("text" to "Home Song"))))),
                                                                    mapOf("musicResponsiveListItemFlexColumnRenderer" to mapOf("text" to mapOf("runs" to listOf(mapOf("text" to "Home Artist")))))
                                                                ),
                                                                "playlistItemData" to mapOf("videoId" to "h123"),
                                                                "thumbnail" to mapOf("musicThumbnailRenderer" to mapOf("thumbnail" to mapOf("thumbnails" to listOf(mapOf("url" to "hthumb")))))
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        
        coEvery { apiService.getHomeData(any(), any(), any(), any(), any(), any(), any()) } returns Response.success(mockResponseMap)

        // When
        val result = repository.fetchHome(null)

        // Then
        assertNotNull(result)
        assertEquals(1, result.quickPicks.size)
        assertEquals("Home Song", result.quickPicks[0].title)
    }

    @Test
    fun `getStreamingUrl returns url on success`() = runBlocking {
        // Given
        val videoId = "test_video"
        val mockResponseMap = mapOf(
            "playabilityStatus" to mapOf("status" to "OK"),
            "streamingData" to mapOf(
                "adaptiveFormats" to listOf(
                    mapOf("url" to "http://stream.url", "mimeType" to "audio/mp4", "itag" to 140)
                )
            )
        )
        
        coEvery { apiService.getPlayerData(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Response.success(mockResponseMap)

        // When
        val result = repository.getStreamingUrl(videoId, null, emptyList())

        // Then
        assertNotNull(result)
        assertTrue(result?.url?.startsWith("http://stream.url") == true)
    }
}
