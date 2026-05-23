package com.forzagallery

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single Forza screenshot captured from the forza.net API.
 *
 * [thumbnailUrl] is the smaller CDN variant used for the grid.
 * [fullUrl]      is the max-quality version used for download and share.
 */
@Parcelize
data class Photo(
    val id: String,
    val thumbnailUrl: String,
    val fullUrl: String
) : Parcelable
