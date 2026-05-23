package com.forzagallery

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch

/**
 * Fully native photo gallery.
 *
 * Fetches photos directly from [ForzaApiService] using the session cookies
 * that were set by the OAuth WebView.  No WebView here at all.
 *
 * States:
 *  • Loading  → full-screen [CircularProgressIndicator]
 *  • Success  → [SwipeRefreshLayout] + 3-column [RecyclerView]
 *  • Empty    → icon + message + retry button
 *  • Auth err → icon + "session expired" message + login button
 *  • Error    → icon + error message + retry button
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var emptyContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhotoAdapter

    private var isFirstLoad = true

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        progressBar    = findViewById(R.id.progressBar)
        emptyContainer = findViewById(R.id.emptyContainer)
        emptyView      = findViewById(R.id.emptyView)
        retryButton    = findViewById(R.id.retryButton)
        swipeRefresh   = findViewById(R.id.swipeRefresh)
        recyclerView   = findViewById(R.id.recyclerView)

        adapter = PhotoAdapter(
            onDownload = { photo ->
                DownloadHelper.downloadWithOrientationFix(
                    context   = this,
                    url       = photo.fullUrl,
                    jsIsPortrait = false,
                    onSuccess = { name, _ ->
                        Toast.makeText(this, getString(R.string.saved_landscape, name), Toast.LENGTH_SHORT).show()
                    },
                    onError = { msg ->
                        Toast.makeText(this, getString(R.string.download_error, msg), Toast.LENGTH_LONG).show()
                    }
                )
            },
            onShare = { photo -> ShareHelper.share(this, photo.fullUrl) }
        )

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        swipeRefresh.setColorSchemeResources(com.google.android.material.R.color.m3_ref_palette_primary40)
        swipeRefresh.setOnRefreshListener { loadGallery(isRefresh = true) }

        loadGallery()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_gallery, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reload -> { loadGallery(isRefresh = true); true }
        R.id.action_logout -> { logout(); true }
        else               -> super.onOptionsItemSelected(item)
    }

    // ── Load / refresh ────────────────────────────────────────────────────────

    private fun loadGallery(isRefresh: Boolean = false) {
        lifecycleScope.launch {
            if (!isRefresh) {
                showLoading()
            } else {
                swipeRefresh.isRefreshing = true
            }

            try {
                val photos = ForzaApiService.fetchGallery(MOBILE_UA)
                if (photos.isEmpty()) {
                    showEmpty(getString(R.string.gallery_empty_no_photos), forLogin = false)
                } else {
                    showPhotos(photos)
                }
            } catch (e: AuthException) {
                showEmpty(getString(R.string.gallery_session_expired), forLogin = true)
            } catch (e: Exception) {
                val msg = e.message ?: getString(R.string.gallery_error_unknown)
                showEmpty(getString(R.string.gallery_error, msg), forLogin = false)
            }

            swipeRefresh.isRefreshing = false
            isFirstLoad = false
        }
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private fun showLoading() {
        progressBar.visibility    = View.VISIBLE
        emptyContainer.visibility = View.GONE
        swipeRefresh.visibility   = View.GONE
    }

    private fun showPhotos(photos: List<Photo>) {
        progressBar.visibility    = View.GONE
        emptyContainer.visibility = View.GONE
        swipeRefresh.visibility   = View.VISIBLE
        supportActionBar?.subtitle = getString(R.string.gallery_count, photos.size)
        adapter.submitList(photos)
    }

    private fun showEmpty(message: String, forLogin: Boolean) {
        progressBar.visibility    = View.GONE
        swipeRefresh.visibility   = View.GONE
        emptyView.text            = message
        retryButton.text = if (forLogin) getString(R.string.login_again) else getString(R.string.gallery_retry)
        retryButton.setOnClickListener {
            if (forLogin) logout() else loadGallery()
        }
        emptyContainer.visibility = View.VISIBLE
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    private fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
