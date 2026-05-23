package com.forzagallery

import android.app.Activity
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * JavaScript ↔ Android bridge.
 *
 * Methods annotated with @JavascriptInterface are the ONLY ones accessible
 * from web-page JavaScript. No other methods are exposed to the web layer.
 */
class ForzaJsInterface(private val activity: Activity) {

    /**
     * Called by the injected JS when the user taps a "⬇ Baixar" button.
     *
     * @param url         Full HTTPS URL of the photo.
     * @param isPortrait  True when JS detected naturalHeight > naturalWidth.
     *                    Passed as a hint to [DownloadHelper]; EXIF takes precedence.
     */
    @JavascriptInterface
    fun downloadPhoto(url: String, isPortrait: Boolean) {
        if (url.isBlank() || !url.startsWith("https://")) {
            activity.runOnUiThread {
                Toast.makeText(activity, "URL inválido para download.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // DownloadHelper dispatches internally to IO and calls back on Main thread
        DownloadHelper.downloadWithOrientationFix(
            context      = activity,
            url          = url,
            jsIsPortrait = isPortrait,
            onSuccess    = { fileName, portrait ->
                val msg = if (portrait)
                    activity.getString(R.string.saved_portrait, fileName)
                else
                    activity.getString(R.string.saved_landscape, fileName)
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
                Toast.makeText(
                    activity,
                    activity.getString(R.string.download_error, err),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    /**
     * Convenience overload used by [android.webkit.WebView.setDownloadListener]
     * (no orientation info available from that callback).
     */
    @JavascriptInterface
    fun downloadPhoto(url: String) = downloadPhoto(url, false)

    /**
     * Called by the injected JS when the user taps a "↗ Partilhar" button.
     * Downloads the photo to the app cache and opens the system share sheet,
     * with WhatsApp, Telegram, and other popular apps listed first.
     *
     * @param url  Full HTTPS URL of the photo.
     */
    @JavascriptInterface
    fun sharePhoto(url: String) {
        if (url.isBlank() || !url.startsWith("https://")) {
            activity.runOnUiThread {
                Toast.makeText(activity, "URL inválido para partilha.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        ShareHelper.share(activity, url)
    }
}
