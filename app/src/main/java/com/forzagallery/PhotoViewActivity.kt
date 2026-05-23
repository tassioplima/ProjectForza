package com.forzagallery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.load
import coil.request.CachePolicy
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Full-screen photo viewer.
 * Loads the full-resolution image from [Photo.fullUrl] and exposes
 * download and share actions.
 */
class PhotoViewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PHOTO = "extra_photo"

        fun start(context: Context, photo: Photo) {
            context.startActivity(
                Intent(context, PhotoViewActivity::class.java)
                    .putExtra(EXTRA_PHOTO, photo)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge, hide system bars for immersive experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_photo_view)

        @Suppress("DEPRECATION")
        val photo: Photo = intent.getParcelableExtra(EXTRA_PHOTO)
            ?: run { finish(); return }

        PhotoHistoryStore.init(this)

        val toolbar      = findViewById<MaterialToolbar>(R.id.toolbar)
        val fullImage    = findViewById<TouchImageView>(R.id.fullImage)
        val progressBar  = findViewById<CircularProgressIndicator>(R.id.progressBar)
        val btnRotate    = findViewById<MaterialButton>(R.id.btnRotate)
        val btnDownload  = findViewById<MaterialButton>(R.id.btnDownload)
        val btnShare     = findViewById<MaterialButton>(R.id.btnShare)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        // Load full-resolution image
        fullImage.load(photo.fullUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_photos)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            listener(
                onStart   = { progressBar.visibility = View.VISIBLE  },
                onSuccess = { _, _ -> progressBar.visibility = View.GONE },
                onError   = { _, _ -> progressBar.visibility = View.GONE }
            )
        }

        // Each tap rotates 90° clockwise; zoom is reset automatically inside TouchImageView
        btnRotate.setOnClickListener { fullImage.rotateBy90() }

        btnDownload.setOnClickListener {
            DownloadHelper.downloadWithOrientationFix(
                context               = this,
                url                   = photo.fullUrl,
                jsIsPortrait          = false,
                extraRotationDegrees  = fullImage.manualRotation,
                onSuccess             = { name, _ ->
                    PhotoHistoryStore.markDownloaded(this, photo.id)
                    Toast.makeText(this, getString(R.string.saved_landscape, name), Toast.LENGTH_SHORT).show()
                },
                onError = { msg ->
                    Toast.makeText(this, getString(R.string.download_error, msg), Toast.LENGTH_LONG).show()
                }
            )
        }

        btnShare.setOnClickListener {
            PhotoHistoryStore.markShared(this, photo.id)
            ShareHelper.share(this, photo.fullUrl, fullImage.manualRotation)
        }
    }
}
