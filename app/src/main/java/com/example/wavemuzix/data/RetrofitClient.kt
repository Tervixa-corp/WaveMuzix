package com.example.wavemuzix.data

import com.example.wavemuzix.repository.ArtistMetadataRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ARCHITECTURAL FINALIZATION:
 * 
 * 1. MIGRATION TO CLIENT-SIDE EXTRACTION: The app now uses NewPipeExtractor locally.
 * 2. NO EXTERNAL BACKEND: The Cloudflare worker and Piped instances have been removed.
 * 3. LOCAL SCRAPING: NewPipeExtractor handles YouTube's structural changes locally on the device.
 */
object RetrofitClient {
    private const val SPONSORBLOCK_BASE_URL = "https://sponsor.ajay.app/"
    private const val DEEZER_API_URL = "https://api.deezer.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            
            // Critical for Piped APIs: Set a custom User-Agent to avoid 403 Forbidden errors
            // Public instances often block standard browser strings to prevent scraping.
            if (request.header("User-Agent") == null) {
                builder.header("User-Agent", "WaveMuzixAndroid/1.0 (Mobile; Android)")
            }
            if (request.header("Accept") == null) {
                builder.header("Accept", "application/json")
            }
            
            chain.proceed(builder.build())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val sponsorBlockInstance: SponsorBlockApiService by lazy {
        Retrofit.Builder()
            .baseUrl(SPONSORBLOCK_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SponsorBlockApiService::class.java)
    }

    val emailInstance: EmailApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.sendgrid.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EmailApiService::class.java)
    }

    val deezerInstance: DeezerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEEZER_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeezerApiService::class.java)
    }

    val spotifyInstance: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api-partner.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(SpotifyApiService::class.java)
    }

    val artistMetadataRepository: ArtistMetadataRepository by lazy {
        ArtistMetadataRepository(deezerInstance, spotifyInstance)
    }

    fun getOkHttpClient(): OkHttpClient = okHttpClient
}
