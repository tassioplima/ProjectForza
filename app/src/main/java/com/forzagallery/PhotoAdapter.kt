package com.forzagallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.google.android.material.button.MaterialButton

/**
 * Gallery grid adapter.
 *
 * Two modes:
 * • **Normal**: tap opens the photo; bottom bar shows download/share.
 * • **Select**: tap toggles selection (max [MAX_SELECTION] items); bottom bar
 *   is hidden; a circle icon at the top-right corner reflects the selected state.
 *
 * [onToggleRequest] is called when the user taps a photo in select mode.
 * The Activity is responsible for calling [toggleSelection] and then
 * [notifyItemChanged] / [notifyDataSetChanged] as appropriate.
 */
class PhotoAdapter(
    private val onOpen: (Photo) -> Unit,
    private val onDownload: (Photo) -> Unit,
    private val onShare: (Photo) -> Unit,
    val onToggleRequest: (Photo) -> Unit = {},
    private val onLongPress: (Photo) -> Unit = {}
) : ListAdapter<Photo, PhotoAdapter.PhotoViewHolder>(DIFF) {

    // ── Selection state ───────────────────────────────────────────────────────

    companion object {
        const val MAX_SELECTION = 10

        private val DIFF = object : DiffUtil.ItemCallback<Photo>() {
            override fun areItemsTheSame(a: Photo, b: Photo) = a.id == b.id
            override fun areContentsTheSame(a: Photo, b: Photo) = a == b
        }
    }

    enum class ToggleResult { SELECTED, DESELECTED, AT_MAX }

    var isSelectMode = false
        private set

    /** Insertion-ordered set so [getSelectedPhotos] preserves tap order. */
    private val selectedIds = LinkedHashSet<String>()

    fun enterSelectMode() {
        if (isSelectMode) return
        isSelectMode = true
        notifyDataSetChanged()
    }

    fun exitSelectMode() {
        isSelectMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    /**
     * Toggles the selection state of [photo].
     * Does NOT call notify — the caller must do that after calling this.
     */
    fun toggleSelection(photo: Photo): ToggleResult {
        val id = photo.id
        return if (id in selectedIds) {
            selectedIds.remove(id)
            ToggleResult.DESELECTED
        } else if (selectedIds.size < MAX_SELECTION) {
            selectedIds.add(id)
            ToggleResult.SELECTED
        } else {
            ToggleResult.AT_MAX
        }
    }

    fun selectAll() {
        selectedIds.clear()
        currentList.take(MAX_SELECTION).forEach { selectedIds.add(it.id) }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun selectedCount() = selectedIds.size

    fun getSelectedPhotos(): List<Photo> = currentList.filter { it.id in selectedIds }

    // ── Adapter ───────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        val columns = parent.context.resources.getInteger(R.integer.gallery_columns)
        val columnWidth = parent.measuredWidth / columns
        view.layoutParams = RecyclerView.LayoutParams(
            columnWidth, RecyclerView.LayoutParams.WRAP_CONTENT
        )
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo      = getItem(position)
        val isSelected = photo.id in selectedIds
        val atMax      = selectedIds.size >= MAX_SELECTION && !isSelected
        holder.bind(photo, isSelectMode, isSelected, atMax, onOpen, onDownload, onShare, onToggleRequest, onLongPress)
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView:       ImageView     = view.findViewById(R.id.photoImage)
        private val selectionOverlay: View         = view.findViewById(R.id.selectionOverlay)
        private val selectionIcon:   ImageView     = view.findViewById(R.id.selectionIcon)
        private val bottomBar:       View          = view.findViewById(R.id.bottomBar)
        private val btnDownload:     MaterialButton = view.findViewById(R.id.btnDownload)
        private val btnShare:        MaterialButton = view.findViewById(R.id.btnShare)

        fun bind(
            photo: Photo,
            isSelectMode: Boolean,
            isSelected: Boolean,
            atMax: Boolean,
            onOpen: (Photo) -> Unit,
            onDownload: (Photo) -> Unit,
            onShare: (Photo) -> Unit,
            onToggleRequest: (Photo) -> Unit,
            onLongPress: (Photo) -> Unit
        ) {
            imageView.load(photo.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_photos)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
            }

            if (isSelectMode) {
                // ── Select mode ────────────────────────────────────────────
                bottomBar.visibility       = View.GONE
                selectionIcon.visibility   = View.VISIBLE
                selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
                selectionIcon.setImageResource(
                    if (isSelected) R.drawable.ic_check_circle
                    else            R.drawable.ic_check_circle_outline
                )
                // Dim items that can't be selected (max reached)
                imageView.alpha = if (atMax) 0.35f else 1f
                imageView.setOnClickListener { onToggleRequest(photo) }
            } else {
                // ── Normal mode ────────────────────────────────────────────
                bottomBar.visibility       = View.VISIBLE
                selectionIcon.visibility   = View.GONE
                selectionOverlay.visibility = View.GONE
                imageView.alpha            = 1f
                imageView.setOnClickListener { onOpen(photo) }
                imageView.setOnLongClickListener { onLongPress(photo); true }
                btnDownload.setOnClickListener { onDownload(photo) }
                btnShare.setOnClickListener    { onShare(photo) }
            }
        }
    }
}
