package com.forzagallery

/**
 * In-memory store used to pass the current gallery list to [PhotoViewActivity]
 * without putting a large parcelable list in the Intent.
 */
object PhotoSessionStore {
    var currentPhotos: List<Photo> = emptyList()
}
