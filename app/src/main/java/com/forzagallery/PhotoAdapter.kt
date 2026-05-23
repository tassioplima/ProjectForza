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

class PhotoAdapter(
    private val onDownload: (Photo) -> Unit,
    private val onShare: (Photo) -> Unit
) : ListAdapter<Photo, PhotoAdapter.PhotoViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Photo>() {
            override fun areItemsTheSame(a: Photo, b: Photo) = a.id == b.id
            override fun areContentsTheSame(a: Photo, b: Photo) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        // Width = half screen (2 columns); height is wrap_content, adapts to image ratio
        val columnWidth = parent.measuredWidth / 2
        view.layoutParams = RecyclerView.LayoutParams(columnWidth, RecyclerView.LayoutParams.WRAP_CONTENT)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), onDownload, onShare)
    }

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.photoImage)
        private val btnDownload: MaterialButton = view.findViewById(R.id.btnDownload)
        private val btnShare: MaterialButton = view.findViewById(R.id.btnShare)

        fun bind(photo: Photo, onDownload: (Photo) -> Unit, onShare: (Photo) -> Unit) {
            imageView.load(photo.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_photos)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
            }
            btnDownload.setOnClickListener { onDownload(photo) }
            btnShare.setOnClickListener { onShare(photo) }
        }
    }
}
