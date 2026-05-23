package com.forzagallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a Forza photo, normalises EXIF rotation, and saves it to
 * Pictures/ForzaGallery/ so it appears correctly in every gallery app.
 *
 * Flow:
 *   1. Fetch raw bytes from [url] with session cookies.
 *   2. Read EXIF TAG_ORIENTATION from the byte stream.
 *   3. If rotation != 0°, decode the bitmap, apply a Matrix rotation,
 *      and compress back to JPEG (quality 95).
 *   4. Reset EXIF TAG_ORIENTATION to NORMAL so the file is self-consistent.
 *   5. Notify MediaStore so the photo appears in the gallery immediately.
 */
object DownloadHelper {

    /**
     * @param url           Full HTTPS URL of the photo.
     * @param jsIsPortrait  Orientation hint from JavaScript (naturalHeight > naturalWidth).
     *                      Used only when no EXIF data is present.
     * @param onSuccess     Called on the main thread with (fileName, isPortrait).
     * @param onError       Called on the main thread with an error message.
     */
    fun downloadWithOrientationFix(
        context: Context,
        url: String,
        jsIsPortrait: Boolean,
        onSuccess: (fileName: String, isPortrait: Boolean) -> Unit,
        onError: (message: String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Fetch bytes (includes session cookies for auth)
                val bytes = fetchBytes(url)

                // 2. Read EXIF rotation from the downloaded bytes
                val exifDegrees = readExifRotation(bytes)

                // 3. Decode bitmap
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("Não foi possível descodificar a imagem.")

                // 4. Apply rotation if EXIF says the image is stored rotated
                val finalIsPortrait: Boolean
                if (exifDegrees != 0f) {
                    val matrix = Matrix().apply { postRotate(exifDegrees) }
                    val rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    bitmap.recycle()
                    bitmap = rotated
                    // After rotation, re-check portrait/landscape from actual dimensions
                    finalIsPortrait = bitmap.height > bitmap.width
                } else {
                    // No EXIF rotation — trust JS hint or raw dimensions
                    finalIsPortrait = if (bitmap.width > 0 && bitmap.height > 0)
                        bitmap.height > bitmap.width
                    else
                        jsIsPortrait
                }

                // 5. Save to Pictures/ForzaGallery/
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "ForzaGallery"
                )
                dir.mkdirs()

                val fileName = buildFileName(url)
                val file = File(dir, fileName)

                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
                bitmap.recycle()

                // 6. Reset EXIF orientation to NORMAL so viewers don't rotate again
                ExifInterface(file.absolutePath).apply {
                    setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString()
                    )
                    saveAttributes()
                }

                // 7. Register in MediaStore → photo appears immediately in gallery
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                withContext(Dispatchers.Main) { onSuccess(fileName, finalIsPortrait) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Erro desconhecido ao processar a foto.")
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Download bytes from [url], passing WebView session cookies. */
    internal fun fetchBytes(url: String): ByteArray {
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            if (cookies.isNotEmpty()) setRequestProperty("Cookie", cookies)
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Referer", "https://forza.net/")
            connectTimeout          = 30_000
            readTimeout             = 90_000
            instanceFollowRedirects = true
        }

        return conn.inputStream.use { it.readBytes() }
    }

    /**
     * Returns the clockwise rotation in degrees needed to display the image
     * upright, based on the EXIF TAG_ORIENTATION tag.
     *
     * | EXIF value         | Rotation |
     * |--------------------|---------|
     * | ORIENTATION_NORMAL | 0°      |
     * | ROTATE_90          | 90°     |
     * | ROTATE_180         | 180°    |
     * | ROTATE_270         | 270°    |
     * | TRANSPOSE          | 90°     |
     * | TRANSVERSE         | 270°    |
     */
    private fun readExifRotation(bytes: ByteArray): Float {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            when (
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90   ->  90f
                ExifInterface.ORIENTATION_ROTATE_180  -> 180f
                ExifInterface.ORIENTATION_ROTATE_270  -> 270f
                ExifInterface.ORIENTATION_TRANSPOSE   ->  90f
                ExifInterface.ORIENTATION_TRANSVERSE  -> 270f
                else                                  ->   0f
            }
        } catch (_: Exception) {
            0f // If EXIF cannot be read, assume no rotation needed
        }
    }

    /** Build a clean filename from the photo URL, fallback to timestamp. */
    internal fun buildFileName(url: String): String {
        return try {
            val path = Uri.parse(url).path ?: ""
            val name = path.substringAfterLast('/').substringBefore('?').trim()
            if (name.isNotEmpty() && name.contains('.')) name
            else "forza_${System.currentTimeMillis()}.jpg"
        } catch (_: Exception) {
            "forza_${System.currentTimeMillis()}.jpg"
        }
    }
}
