package com.forzagallery

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists per-photo manual rotation (0 / 90 / 180 / 270°) across app sessions.
 * Call [init] once (e.g. in Activity.onCreate) before using [getRotation] or [setRotation].
 */
object PhotoRotationStore {
    private const val PREFS_NAME = "photo_rotations"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** Returns the saved rotation in degrees for [photoId], or 0f if none saved. */
    fun getRotation(photoId: String): Float = prefs?.getFloat(photoId, 0f) ?: 0f

    /** Persists [degrees] for [photoId]. Removes the entry when degrees == 0f. */
    fun setRotation(context: Context, photoId: String, degrees: Float) {
        init(context)
        val editor = prefs?.edit() ?: return
        if (degrees == 0f) editor.remove(photoId) else editor.putFloat(photoId, degrees)
        editor.apply()
    }
}
