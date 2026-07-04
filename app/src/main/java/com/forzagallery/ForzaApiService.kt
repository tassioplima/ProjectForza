package com.forzagallery

import android.content.Context
import android.util.Base64
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
    private const val PREFS_NAME  = "fzg_prefs"
    private const val KEY_TOKEN   = "auth_token"
    private const val KEY_TOKEN_TS = "auth_token_ts"

    /** Application context set once by [init]. */
    private var appContext: Context? = null

    /**
     * Bearer token captured from the WebView's outbound API requests during login.
     * Cleared on logout. Allows native HTTP calls to authenticate with the same token
     * that the forza.net SPA uses, which may differ from cookie-based auth.
     */
    var capturedAuthHeader: String? = null

    /** Must be called once (e.g. in [MainActivity.onCreate]) before any token operations. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Saves [capturedAuthHeader] to SharedPreferences with a timestamp.
     * Call after a successful login so the token survives process death.
     */
    fun persistToken() {
        val token = capturedAuthHeader ?: return
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_TOKEN, token)
            ?.putLong(KEY_TOKEN_TS, System.currentTimeMillis())
            ?.apply()
        Log.d(TAG, "persistToken: saved (${token.length} chars)")
    }

    /**
     * Loads a previously persisted token and, if it has not expired, restores
     * [capturedAuthHeader]. Returns true when a valid token was restored.
     *
     * For JWT tokens the `exp` claim is checked directly. For opaque tokens a
     * 1-hour window from the save timestamp is used as a conservative fallback.
     */
    fun restoreToken(): Boolean {
        val ctx   = appContext ?: return false
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null) ?: return false
        val ts    = prefs.getLong(KEY_TOKEN_TS, 0L)
        if (!isTokenValid(token, ts)) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_TS).apply()
            Log.d(TAG, "restoreToken: token expired, cleared")
            return false
        }
        capturedAuthHeader = token
        Log.d(TAG, "restoreToken: OK")
        return true
    }

    /** Clears the token from memory and from SharedPreferences. Called on logout. */
    fun clearToken() {
        capturedAuthHeader = null
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.remove(KEY_TOKEN)?.remove(KEY_TOKEN_TS)?.apply()
        Log.d(TAG, "clearToken")
    }

    private fun isTokenValid(token: String, savedAt: Long): Boolean {
        return try {
            val jwt   = token.removePrefix("Bearer ")
            val parts = jwt.split(".")
            if (parts.size < 3) {
                // Opaque token — trust it for up to 1 hour from when it was saved.
                return savedAt > 0 && System.currentTimeMillis() - savedAt < 3_600_000L
            }
            val padded  = parts[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
            val payload = JSONObject(String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)))
            val exp     = payload.optLong("exp", 0L)
            if (exp == 0L) return true          // No exp claim — treat as valid.
            exp * 1000L > System.currentTimeMillis() + 300_000L  // 5-min buffer.
        } catch (_: Exception) { true }
    }

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
