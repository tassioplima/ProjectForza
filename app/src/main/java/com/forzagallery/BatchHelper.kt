package com.forzagallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Batch download and share operations for a list of [Photo] objects.
 *
 * • [downloadAll] saves each photo to Pictures/ForzaGallery/ sequentially.
 * • [shareAll]    downloads all to the private cache and fires
 *                 [Intent.ACTION_SEND_MULTIPLE] so the user can share them all at once.
 */
object BatchHelper {

    /**
     * Downloads [photos] one by one to the public gallery folder.
     *
     * @param onProgress Called on the main thread after each photo with (current, total).
     * @param onComplete Called on the main thread when finished with (saved, failed) counts.
     */
    fun downloadAll(
        context: android.content.Context,
        photos: List<Photo>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: (saved: Int, failed: Int) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var saved  = 0
            var failed = 0

            photos.forEachIndexed { index, photo ->
                // Wrap the fire-and-forget DownloadHelper into a suspending call
                val ok = suspendCancellableCoroutine<Boolean> { cont ->
                    DownloadHelper.downloadWithOrientationFix(
                        context      = context,
                        url          = photo.fullUrl,
                        jsIsPortrait = false,
                        onSuccess    = { _, _ -> if (cont.isActive) cont.resume(true) },
                        onError      = { _    -> if (cont.isActive) cont.resume(false) }
                    )
                }
                if (ok) saved++ else failed++

                withContext(Dispatchers.Main) { onProgress(index + 1, photos.size) }
            }

            withContext(Dispatchers.Main) { onComplete(saved, failed) }
        }
    }

    /**
     * Downloads [photos] to the app-private share cache and opens the system
     * chooser via [Intent.ACTION_SEND_MULTIPLE].
     *
     * @param onReady Called on the main thread just before launching the chooser.
     * @param onError Called on the main thread if something goes wrong.
     */
    fun shareAll(
        activity: Activity,
        photos: List<Photo>,
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheDir = File(activity.cacheDir, "share").also { it.mkdirs() }
                val uris = ArrayList<Uri>()

                photos.forEach { photo ->
                    val bytes    = DownloadHelper.fetchBytes(photo.fullUrl)
                    val fileName = DownloadHelper.buildFileName(photo.fullUrl)
                    // Prefix with nanoTime so concurrent/re-used file names don't clash
                    val file     = File(cacheDir, "${System.nanoTime()}_$fileName")
                    file.writeBytes(bytes)
                    uris.add(
                        FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.fileprovider",
                            file
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    onReady()
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/jpeg"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(
                        Intent.createChooser(intent, activity.getString(R.string.share_via))
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: activity.getString(R.string.gallery_error_unknown))
                }
            }
        }
    }
}
