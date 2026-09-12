package com.example.wavemuzix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {

    /**
     * Extreme compressor: Resizes and compresses image to be under 1MB (Firestore limit)
     * and returns a Base64 string.
     */
    fun compressAndEncodeToBase64(context: Context, uri: Uri, maxWidth: Int = 800, quality: Int = 70): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            
            // 1. Calculate new dimensions while maintaining aspect ratio
            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val (width, height) = if (ratio > 1) {
                // Landscape
                maxWidth to (maxWidth / ratio).toInt()
            } else {
                // Portrait or Square
                (maxWidth * ratio).toInt() to maxWidth
            }
            
            // 2. Scale bitmap
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

            // 3. Compress as JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val byteArray = outputStream.toByteArray()

            // 4. Convert to Base64 String
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
