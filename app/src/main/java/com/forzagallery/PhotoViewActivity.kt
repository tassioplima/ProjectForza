package com.forzagallery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * Full-screen photo viewer with swipe-left/right navigation between all gallery photos.
 * Each page hosts a [TouchImageView] that supports pinch-to-zoom, pan, and rotation.
 * When zoomed in, swipe gestures pan the image; at 1× zoom they navigate pages.
 */
class PhotoViewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_INDEX = "extra_index"

        /** Launch the viewer. Set [PhotoSessionStore.currentPhotos] before calling. */
        fun start(context: Context, initialIndex: Int) {
            context.startActivity(
                Intent(context, PhotoViewActivity::class.java)
                    .putExtra(EXTRA_INDEX, initialIndex)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_photo_view)

        val photos = PhotoSessionStore.currentPhotos
        if (photos.isEmpty()) { finish(); return }

        val rawIndex   = intent.getIntExtra(EXTRA_INDEX, 0)
        val initialIdx = rawIndex.coerceIn(0, photos.size - 1)

        PhotoHistoryStore.init(this)

        val toolbar     = findViewById<MaterialToolbar>(R.id.toolbar)
        val photoPager  = findViewById<ViewPager2>(R.id.photoPager)
        val btnRotate   = findViewById<MaterialButton>(R.id.btnRotate)
        val btnDownload = findViewById<MaterialButton>(R.id.btnDownload)
        val btnShare    = findViewById<MaterialButton>(R.id.btnShare)

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val pagerAdapter = PhotoPagerAdapter(photos)
        photoPager.adapter = pagerAdapter
        photoPager.setCurrentItem(initialIdx, false)

        // Show "current / total" counter in the toolbar
        fun updateTitle(index: Int) {
            supportActionBar?.title = "${index + 1} / ${photos.size}"
        }
        updateTitle(initialIdx)

        photoPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateTitle(position)
        })

        btnRotate.setOnClickListener {
            pagerAdapter.rotateCurrent(photoPager.currentItem)
        }

        btnDownload.setOnClickListener {
            val photo    = pagerAdapter.getPhotoAt(photoPager.currentItem)
            val rotation = pagerAdapter.getManualRotation(photoPager.currentItem)
            DownloadHelper.downloadWithOrientationFix(
                context              = this,
                url                  = photo.fullUrl,
                jsIsPortrait         = false,
                extraRotationDegrees = rotation,
                onSuccess            = { name, _ ->
                    PhotoHistoryStore.markDownloaded(this, photo.id)
                    Toast.makeText(this, getString(R.string.saved_landscape, name), Toast.LENGTH_SHORT).show()
                },
                onError = { msg ->
                    Toast.makeText(this, getString(R.string.download_error, msg), Toast.LENGTH_LONG).show()
                }
            )
        }

        btnShare.setOnClickListener {
            val photo    = pagerAdapter.getPhotoAt(photoPager.currentItem)
            val rotation = pagerAdapter.getManualRotation(photoPager.currentItem)
            PhotoHistoryStore.markShared(this, photo.id)
            ShareHelper.share(this, photo.fullUrl, rotation)
        }
    }
}
