package com.example.wavemuzix.data

import android.content.Context
import android.content.SharedPreferences

class CookieManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("youtube_prefs", Context.MODE_PRIVATE)

    fun saveCookies(cookies: String) {
        prefs.edit().putString("user_cookies", cookies).apply()
    }

    fun getCookies(): String? {
        return prefs.getString("user_cookies", null)
    }

    fun clearCookies() {
        prefs.edit().remove("user_cookies").apply()
    }

    fun isLoggedIn(): Boolean {
        return getCookies() != null
    }
}
