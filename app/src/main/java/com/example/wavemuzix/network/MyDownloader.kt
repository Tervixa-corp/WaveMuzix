package com.example.wavemuzix.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class MyDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        var body: RequestBody? = null
        if (dataToSend != null && dataToSend.isNotEmpty()) {
            body = dataToSend.toRequestBody()
        } else if (httpMethod == "POST" || httpMethod == "PUT") {
            body = "".toRequestBody()
        }

        val okHttpRequestBuilder = Request.Builder()
            .url(url)
            .method(httpMethod, body)

        headers?.forEach { (key, values) ->
            values.forEach { value ->
                okHttpRequestBuilder.addHeader(key, value)
            }
        }

        val okHttpRequest = okHttpRequestBuilder.build()
        val okHttpResponse = client.newCall(okHttpRequest).execute()

        val responseBody = okHttpResponse.body?.string()
        val responseCode = okHttpResponse.code
        val responseMessage = okHttpResponse.message
        val responseHeaders = okHttpResponse.headers.toMultimap()

        return Response(responseCode, responseMessage, responseHeaders, responseBody, url)
    }

    companion object {
        private var instance: MyDownloader? = null

        fun getInstance(): MyDownloader {
            if (instance == null) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                instance = MyDownloader(client)
            }
            return instance!!
        }
    }
}
