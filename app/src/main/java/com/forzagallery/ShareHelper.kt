package com.forzagallery

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads a Forza photo to the app's private cache and opens the system
 * share sheet, listing WhatsApp, Telegram, and other popular apps first.
 */
object ShareHelper {

    /**
     * Ordered list of app packages that appear as "initial intents" at the
     * top of the chooser.  Only installed apps are included.
     */
    private val PRIORITY_PACKAGES = listOf(
        "com.whatsapp",               // WhatsApp
        "org.telegram.messenger",     // Telegram
        "org.telegram.messenger.web", // Telegram X
        "com.instagram.android",      // Instagram
        "com.snapchat.android",       // Snapchat
        "com.twitter.android",        // Twitter / X
        "com.facebook.orca",          // Messenger
        "com.facebook.katana",        // Facebook
        "com.google.android.apps.photos", // Google Photos
    )

    /**
     * @param activity             The calling Activity (used for context + startActivity).
     * @param url                  Full HTTPS URL of the photo to share.
     * @param extraRotationDegrees Additional clockwise rotation to apply before sharing
     *                             (matches the rotation shown in the full-screen viewer).
     */
    fun share(activity: Activity, url: String, extraRotationDegrees: Float = 0f) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Download raw bytes (uses session cookies, same as DownloadHelper)
                val bytes    = DownloadHelper.fetchBytes(url)
                val fileName = DownloadHelper.buildFileName(url)

                // Write to a private cache folder — never exposed to the public gallery
                val cacheDir = File(activity.cacheDir, "share").also { it.mkdirs() }
                val file     = File(cacheDir, fileName)

                if (extraRotationDegrees != 0f) {
                    // Decode, rotate, re-encode so the shared image matches what user sees
                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val matrix = Matrix().apply { postRotate(extraRotationDegrees) }
                    val rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    bitmap.recycle()
                    bitmap = rotated
                    FileOutputStream(file).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    }
                    bitmap.recycle()
                } else {
                    file.writeBytes(bytes)
                }

                // FileProvider converts the private path to a grantable content URI
                val contentUri: Uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    file
                )

                withContext(Dispatchers.Main) {
                    launchShareSheet(activity, contentUri)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.share_error, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun launchShareSheet(activity: Activity, contentUri: Uri) {
        val pm = activity.packageManager

        // Base ACTION_SEND intent for the image
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type  = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Build priority intents for each installed app in the priority list
        val priorityIntents: Array<Intent> = PRIORITY_PACKAGES.mapNotNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0) // throws if not installed
                Intent(Intent.ACTION_SEND).apply {
                    type    = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage(pkg)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.toTypedArray()

        // createChooser + EXTRA_INITIAL_INTENTS = priority apps appear at the top
        val chooser = Intent.createChooser(
            shareIntent,
            activity.getString(R.string.share_via)
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (priorityIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, priorityIntents)
            }
        }

        activity.startActivity(chooser)
    }
}
