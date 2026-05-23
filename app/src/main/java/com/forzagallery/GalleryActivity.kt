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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
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

    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var emptyContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var batchBar: LinearLayout
    private lateinit var selectionCount: TextView
    private lateinit var btnBatchDownload: MaterialButton
    private lateinit var btnBatchShare: MaterialButton
    private lateinit var adapter: PhotoAdapter

    private var isSelectMode = false
    private var isFirstLoad = true

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

        /** Bottom padding added to the RecyclerView while the batch bar is visible (~64 dp). */
        private const val BATCH_BAR_PADDING_DP = 64
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        PhotoHistoryStore.init(this)
        PhotoRotationStore.init(this)
        toolbar          = findViewById(R.id.toolbar)
        progressBar      = findViewById(R.id.progressBar)
        emptyContainer  = findViewById(R.id.emptyContainer)
        emptyView       = findViewById(R.id.emptyView)
        retryButton     = findViewById(R.id.retryButton)
        swipeRefresh    = findViewById(R.id.swipeRefresh)
        recyclerView    = findViewById(R.id.recyclerView)
        batchBar        = findViewById(R.id.batchBar)
        selectionCount  = findViewById(R.id.selectionCount)
        btnBatchDownload = findViewById(R.id.btnBatchDownload)
        btnBatchShare   = findViewById(R.id.btnBatchShare)

        setSupportActionBar(toolbar)

        // ── Adapter ───────────────────────────────────────────────────────────
        adapter = PhotoAdapter(
            onOpen = { photo ->
                val index = adapter.currentList.indexOfFirst { it.id == photo.id }
                PhotoSessionStore.currentPhotos = adapter.currentList
                PhotoViewActivity.start(this, index.coerceAtLeast(0))
            },
            onDownload = { photo ->
                DownloadHelper.downloadWithOrientationFix(
                    context      = this,
                    url          = photo.fullUrl,
                    jsIsPortrait = false,
                    onSuccess    = { name, _ ->
                        PhotoHistoryStore.markDownloaded(this, photo.id)
                        val pos = adapter.currentList.indexOfFirst { it.id == photo.id }
                        if (pos != -1) adapter.notifyItemChanged(pos)
                        Toast.makeText(this, getString(R.string.saved_landscape, name), Toast.LENGTH_SHORT).show()
                    },
                    onError      = { msg ->
                        Toast.makeText(this, getString(R.string.download_error, msg), Toast.LENGTH_LONG).show()
                    }
                )
            },
            onShare = { photo ->
                PhotoHistoryStore.markShared(this, photo.id)
                val pos = adapter.currentList.indexOfFirst { it.id == photo.id }
                if (pos != -1) adapter.notifyItemChanged(pos)
                ShareHelper.share(this, photo.fullUrl)
            },
            onToggleRequest = { photo -> handleToggle(photo) },
            onLongPress = { photo ->
                if (!isSelectMode) {
                    enterSelectMode()
                    handleToggle(photo)
                }
            }
        )

        val galleryColumns = resources.getInteger(R.integer.gallery_columns)
        recyclerView.layoutManager =
            StaggeredGridLayoutManager(galleryColumns, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        swipeRefresh.setColorSchemeResources(com.google.android.material.R.color.m3_ref_palette_primary40)
        swipeRefresh.setOnRefreshListener { loadGallery(isRefresh = true) }

        // ── Batch bar actions ─────────────────────────────────────────────────
        btnBatchDownload.setOnClickListener { batchDownload() }
        btnBatchShare.setOnClickListener    { batchShare()    }

        // ── Back press exits select mode before finishing ─────────────────────
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectMode) {
                    exitSelectMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        loadGallery()
    }

    // ── Options menu ──────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // Refresh status badges when returning from PhotoViewActivity
        if (!isFirstLoad) adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_gallery, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_select)?.isVisible     = !isSelectMode
        menu.findItem(R.id.action_reload)?.isVisible     = !isSelectMode
        menu.findItem(R.id.action_logout)?.isVisible     = !isSelectMode
        menu.findItem(R.id.action_select_all)?.isVisible = isSelectMode
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_select     -> { enterSelectMode(); true }
        R.id.action_reload     -> { loadGallery(isRefresh = true); true }
        R.id.action_logout     -> { logout(); true }
        R.id.action_select_all -> {
            adapter.selectAll()
            updateSelectionUi(adapter.selectedCount())
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Select mode ───────────────────────────────────────────────────────────

    private fun enterSelectMode() {
        if (isSelectMode) return
        isSelectMode = true
        adapter.enterSelectMode()
        toolbar.setNavigationIcon(R.drawable.ic_close)
        toolbar.setNavigationOnClickListener { exitSelectMode() }
        supportActionBar?.title    = getString(R.string.select_count, 0, PhotoAdapter.MAX_SELECTION)
        supportActionBar?.subtitle = null
        batchBar.visibility        = View.VISIBLE
        swipeRefresh.isEnabled     = false
        // Add padding so last row isn't hidden behind the batch bar
        val pad = (BATCH_BAR_PADDING_DP * resources.displayMetrics.density).toInt()
        recyclerView.setPadding(
            recyclerView.paddingLeft,
            recyclerView.paddingTop,
            recyclerView.paddingRight,
            pad
        )
        invalidateOptionsMenu()
    }

    private fun exitSelectMode() {
        isSelectMode = false
        adapter.exitSelectMode()
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener(null)
        supportActionBar?.title    = getString(R.string.gallery_title)
        supportActionBar?.subtitle =
            if (adapter.currentList.isNotEmpty())
                getString(R.string.gallery_count, adapter.currentList.size)
            else null
        batchBar.visibility    = View.GONE
        swipeRefresh.isEnabled = true
        recyclerView.setPadding(
            recyclerView.paddingLeft,
            recyclerView.paddingTop,
            recyclerView.paddingRight,
            (1 * resources.displayMetrics.density).toInt()
        )
        invalidateOptionsMenu()
    }

    private fun handleToggle(photo: Photo) {
        val prevCount = adapter.selectedCount()
        val result = adapter.toggleSelection(photo)
        val newCount = adapter.selectedCount()

        when (result) {
            PhotoAdapter.ToggleResult.AT_MAX -> {
                Toast.makeText(
                    this,
                    getString(R.string.select_max, PhotoAdapter.MAX_SELECTION),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            else -> {}
        }

        // If crossing the MAX boundary, ALL items need alpha refresh.
        // Otherwise only the tapped item needs a rebind (avoids full-list flicker).
        val crossedBoundary = (prevCount < PhotoAdapter.MAX_SELECTION && newCount >= PhotoAdapter.MAX_SELECTION) ||
                              (prevCount >= PhotoAdapter.MAX_SELECTION && newCount < PhotoAdapter.MAX_SELECTION)

        if (crossedBoundary) {
            adapter.notifyDataSetChanged()
        } else {
            val pos = adapter.currentList.indexOfFirst { it.id == photo.id }
            if (pos != -1) adapter.notifyItemChanged(pos)
        }
        updateSelectionUi(newCount)
    }

    private fun updateSelectionUi(count: Int) {
        supportActionBar?.title = getString(R.string.select_count, count, PhotoAdapter.MAX_SELECTION)
        selectionCount.text = getString(R.string.select_count, count, PhotoAdapter.MAX_SELECTION)
        btnBatchDownload.isEnabled = count > 0
        btnBatchShare.isEnabled    = count > 0
    }

    // ── Batch actions ─────────────────────────────────────────────────────────

    private fun batchDownload() {
        val selected = adapter.getSelectedPhotos()
        if (selected.isEmpty()) return
        setBatchButtonsEnabled(false)
        BatchHelper.downloadAll(
            context    = this,
            photos     = selected,
            onProgress = { current, total ->
                selectionCount.text = getString(R.string.batch_progress, current, total)
            },
            onComplete = { saved, failed ->
                setBatchButtonsEnabled(true)
                selected.forEach { PhotoHistoryStore.markDownloaded(this, it.id) }
                adapter.notifyDataSetChanged()
                selectionCount.text = getString(R.string.select_count, adapter.selectedCount(), PhotoAdapter.MAX_SELECTION)
                val msg = if (failed == 0)
                    getString(R.string.batch_saved_all, saved)
                else
                    getString(R.string.batch_saved_partial, saved, failed)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun batchShare() {
        val selected = adapter.getSelectedPhotos()
        if (selected.isEmpty()) return
        setBatchButtonsEnabled(false)
        selectionCount.text = getString(R.string.batch_preparing)
        BatchHelper.shareAll(
            activity  = this,
            photos    = selected,
            onReady   = {
                setBatchButtonsEnabled(true)
                selected.forEach { PhotoHistoryStore.markShared(this, it.id) }
                adapter.notifyDataSetChanged()
                selectionCount.text = getString(R.string.select_count, adapter.selectedCount(), PhotoAdapter.MAX_SELECTION)
            },
            onError   = { msg ->
                setBatchButtonsEnabled(true)
                selectionCount.text = getString(R.string.select_count, adapter.selectedCount(), PhotoAdapter.MAX_SELECTION)
                Toast.makeText(this, getString(R.string.share_error, msg), Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun setBatchButtonsEnabled(enabled: Boolean) {
        val hasSelection = adapter.selectedCount() > 0
        btnBatchDownload.isEnabled = enabled && hasSelection
        btnBatchShare.isEnabled    = enabled && hasSelection
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
        ForzaApiService.capturedAuthHeader = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}


