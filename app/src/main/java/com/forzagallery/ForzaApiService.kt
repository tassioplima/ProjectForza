package com.forzagallery

import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown when the Forza session has expired (HTTP 401 / 403 or no cookies). */
class AuthException(message: String = "Sessão expirada – inicia sessão novamente") : Exception(message)

/**
 * Fetches the authenticated user's Forza Horizon gallery from the official API.
 * Authentication is provided by forwarding the cookies that were set in the
 * WebView during the Microsoft OAuth login flow.
 *
 * @throws AuthException if cookies are missing or the server returns 401/403.
 * @throws IOException for network / HTTP errors.
 */
object ForzaApiService {

    private const val TAG = "FZG_Api"
    private const val GALLERY_URL = "https://api.forza.net/api/v4/me/gallery/FH6"

    /**
     * Bearer token captured from the WebView's outbound API requests during login.
     * Cleared on logout. Allows native HTTP calls to authenticate with the same token
     * that the forza.net SPA uses, which may differ from cookie-based auth.
     */
    var capturedAuthHeader: String? = null

    private val FULL_URL_FIELDS = listOf(
        "photoCdnPath", "screenshotCdnPath", "imageCdnPath", "cdnPath",
        "screenshotUri", "screenshotUrl", "photoUri", "photoUrl",
        "originalUri", "originalUrl", "fullUri", "fullUrl",
        "imageUri", "imageUrl", "contentUri", "mediaUri", "uri", "url"
    )
    private val THUMB_URL_FIELDS = listOf(
        "thumbnailCdnPath", "thumbCdnPath", "previewCdnPath",
        "thumbnailUri", "thumbnailUrl", "thumbUri", "thumbUrl",
        "previewUri", "previewUrl", "smallUri", "smallUrl"
    )
    private val ID_FIELDS = listOf(
        "id", "ugcId", "photoId", "screenshotId", "itemId", "contentId", "shareCode"
    )

    /**
     * Returns the gallery photos.
     *
     * @throws AuthException if no session cookies are present or server returns 401/403.
     * @throws IOException for other network/HTTP errors.
     */
    suspend fun fetchGallery(mobileUa: String): List<Photo> = withContext(Dispatchers.IO) {
        val cookies = buildCookieHeader()
        val token   = capturedAuthHeader
        Log.d(TAG, "fetchGallery: hasCookies=${cookies.isNotBlank()} hasToken=${!token.isNullOrBlank()}")
        if (cookies.isBlank() && token.isNullOrBlank()) throw AuthException()

        val conn = (URL(GALLERY_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", token)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", mobileUa)
            setRequestProperty("Referer", "https://forza.net/")
            setRequestProperty("Origin", "https://forza.net")
            connectTimeout = 15_000
            readTimeout    = 20_000
            connect()
        }
        try {
            val code = conn.responseCode
            Log.d(TAG, "fetchGallery: HTTP $code")
            when (code) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().readText()
                    Log.d(TAG, "fetchGallery: body[0..300]=${body.take(300)}")
                    val photos = parsePhotos(body)
                    Log.d(TAG, "fetchGallery: parsed ${photos.size} photos")
                    photos
                }
                401, 403 -> throw AuthException()
                else     -> throw IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun buildCookieHeader(): String {
        val cm = CookieManager.getInstance()
        val c1 = cm.getCookie("https://forza.net") ?: ""
        val c2 = cm.getCookie("https://api.forza.net") ?: ""
        return listOf(c1, c2)
            .filter { it.isNotBlank() }
            .joinToString("; ")
    }

    private fun parsePhotos(json: String): List<Photo> {
        val photos = mutableListOf<Photo>()

        val array: JSONArray = when {
            json.trimStart().startsWith('[') -> JSONArray(json)
            else -> {
                val obj = JSONObject(json)
                listOf("data", "items", "results", "photos", "screenshots", "gallery", "content")
                    .firstNotNullOfOrNull { key ->
                        runCatching { obj.getJSONArray(key) }.getOrNull()
                    } ?: JSONArray()
            }
        }

        for (i in 0 until array.length()) {
            val item = runCatching { array.getJSONObject(i) }.getOrNull() ?: continue

            val full = FULL_URL_FIELDS.firstNotNullOfOrNull { key ->
                item.optString(key).takeIf { it.startsWith("http") }
            } ?: continue

            val thumb = THUMB_URL_FIELDS.firstNotNullOfOrNull { key ->
                item.optString(key).takeIf { it.startsWith("http") }
            } ?: full

            val id = ID_FIELDS.firstNotNullOfOrNull { key ->
                item.optString(key).takeIf { it.isNotBlank() }
            } ?: full.hashCode().toString()

            photos.add(Photo(id = id, thumbnailUrl = thumb, fullUrl = toMaxQuality(full)))
        }
        return photos
    }

    /** Strip CDN resize/quality query params to get the original full-res file. */
    private fun toMaxQuality(url: String) =
        url.replace(Regex("[?&](width|height|w|h|size|resize|quality|q|thumb)=[^&]*"), "")
           .trimEnd('?', '&')
}
