package com.forzagallery

import android.graphics.Bitmap
import android.graphics.Matrix
import coil.size.Size
import coil.transform.Transformation

/** Coil Transformation that rotates the decoded bitmap by [degrees] clockwise. */
class RotateTransformation(private val degrees: Float) : Transformation {
    override val cacheKey: String = "${RotateTransformation::class.java.name}-$degrees"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (degrees == 0f) return input
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
    }
}
