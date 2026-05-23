package com.forzagallery

import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.google.android.material.progressindicator.CircularProgressIndicator

class PhotoPagerAdapter(
    private val photos: List<Photo>
) : RecyclerView.Adapter<PhotoPagerAdapter.PageHolder>() {

    // Tracks currently bound holders by adapter position, so the activity can
    // ask for the current page's TouchImageView (rotate/download/share).
    private val activeHolders = SparseArray<PageHolder>()

    override fun getItemCount(): Int = photos.size

    fun getPhotoAt(position: Int): Photo = photos[position]

    fun getManualRotation(position: Int): Float =
        activeHolders[position]?.touchImageView?.manualRotation ?: 0f

    fun rotateCurrent(position: Int) {
        activeHolders[position]?.touchImageView?.rotateBy90()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_page, parent, false)
        return PageHolder(view)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.boundPosition = position
        activeHolders.put(position, holder)
        holder.bind(photos[position])
    }

    override fun onViewRecycled(holder: PageHolder) {
        val pos = holder.boundPosition
        if (pos != RecyclerView.NO_POSITION) activeHolders.remove(pos)
        holder.boundPosition = RecyclerView.NO_POSITION
        super.onViewRecycled(holder)
    }

    class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
        var boundPosition: Int = RecyclerView.NO_POSITION
        val touchImageView: TouchImageView = view.findViewById(R.id.pageImage)
        private val progressBar: CircularProgressIndicator = view.findViewById(R.id.pageProgress)

        fun bind(photo: Photo) {
            // Apply saved rotation (or 0° if none) before loading the image
            touchImageView.resetTransform(PhotoRotationStore.getRotation(photo.id))
            touchImageView.load(photo.fullUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_photos)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                listener(
                    onStart   = { progressBar.visibility = View.VISIBLE },
                    onSuccess = { _, _ -> progressBar.visibility = View.GONE },
                    onError   = { _, _ -> progressBar.visibility = View.GONE }
                )
            }
        }
    }
}
