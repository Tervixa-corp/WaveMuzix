package com.example.wavemuzix.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SponsorSegment(
    val category: String,
    val segment: List<Float>, // [start, end]
    val UUID: String
)

interface SponsorBlockApiService {
    @GET("api/skipSegments")
    suspend fun getSkipSegments(
        @Query("videoID") videoId: String,
        @Query("categories") categories: String
    ): Response<List<SponsorSegment>>
}
