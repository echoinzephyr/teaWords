package com.teameow.teawords.data

import com.google.gson.JsonParser

internal object BingWallpaperSource {
    /** The archive's default landscape image loses detail when cropped to a tall phone screen. */
    fun imageUrl(payload: String): String? {
        val images = JsonParser.parseString(payload).asJsonObject.getAsJsonArray("images") ?: return null
        val image = images.firstOrNull()?.asJsonObject ?: return null
        val base = image.get("urlbase")?.takeUnless { it.isJsonNull }?.asString
        if (base?.startsWith("/th?id=") == true) return "https://www.bing.com${base}_UHD.jpg"
        val fallback = image.get("url")?.takeUnless { it.isJsonNull }?.asString ?: return null
        return fallback.takeIf { it.startsWith("/th?id=") }?.let { "https://www.bing.com$it" }
    }
}
