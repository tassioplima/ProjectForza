package com.forzagallery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process store for photos discovered by the WebView API interceptor.
 *
 * [MainActivity] writes here (via [addPhotos]) from the JavascriptInterface callback.
 * [GalleryActivity] observes [photos] to update the RecyclerView in real-time.
 */
object PhotoStore {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    /** Append [incoming] photos, skipping duplicates (matched by [Photo.id]). */
    fun addPhotos(incoming: List<Photo>) {
        if (incoming.isEmpty()) return
        val existingIds = _photos.value.map { it.id }.toHashSet()
        val fresh = incoming.filter { it.id !in existingIds }
        if (fresh.isNotEmpty()) _photos.value = _photos.value + fresh
    }

    /** Clear all photos (called when the user explicitly reloads). */
    fun clear() {
        _photos.value = emptyList()
    }
}
