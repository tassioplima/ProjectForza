package com.forzagallery

import android.content.Context

/**
 * Tracks which photos have been downloaded or shared in this installation.
 *
 * State is persisted to SharedPreferences and loaded into memory on first
 * access, so badge queries in [onBindViewHolder] are fast (no I/O).
 */
object PhotoHistoryStore {

    private const val PREFS_NAME     = "photo_history"
    private const val KEY_DOWNLOADED = "downloaded"
    private const val KEY_SHARED     = "shared"

    private val downloaded = mutableSetOf<String>()
    private val shared     = mutableSetOf<String>()
    private var initialized = false

    /** Call once from Activity.onCreate() before any badge queries. */
    fun init(context: Context) {
        if (initialized) return
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        downloaded.addAll(p.getStringSet(KEY_DOWNLOADED, emptySet()) ?: emptySet())
        shared    .addAll(p.getStringSet(KEY_SHARED,     emptySet()) ?: emptySet())
        initialized = true
    }

    fun markDownloaded(context: Context, photoId: String) {
        init(context)
        downloaded.add(photoId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_DOWNLOADED, downloaded.toSet()).apply()
    }

    fun markShared(context: Context, photoId: String) {
        init(context)
        shared.add(photoId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_SHARED, shared.toSet()).apply()
    }

    /** Fast in-memory check — no SharedPreferences I/O. */
    fun isDownloaded(photoId: String): Boolean = downloaded.contains(photoId)

    /** Fast in-memory check — no SharedPreferences I/O. */
    fun isShared(photoId: String): Boolean = shared.contains(photoId)
}
