package com.forzagallery

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView with pinch-to-zoom, pan, double-tap zoom, and integrated 90° rotation.
 *
 * - Pinch to zoom between 1× and 4×.
 * - Double-tap toggles between fit-to-screen and 2×.
 * - Drag to pan when zoomed in.
 * - [rotateBy90] rotates the content 90° clockwise, resetting zoom.
 * - Automatically re-fits the image when the view is resized (device rotation).
 */
class TouchImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    // ── State ─────────────────────────────────────────────────────────────────

    private val imgMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    /** Cumulative manual rotation in degrees (0, 90, 180, 270). */
    var manualRotation = 0f
        private set

    /** Current zoom level on top of the base fit-to-screen scale. */
    private var currentZoom = 1f
    private val maxZoom = 4f

    private var isScaling = false

    // ── Gesture detectors ─────────────────────────────────────────────────────

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                val newZoom = (currentZoom * d.scaleFactor).coerceIn(1f, maxZoom)
                val factor = newZoom / currentZoom
                currentZoom = newZoom
                imgMatrix.postScale(factor, factor, d.focusX, d.focusY)
                clamp()
                imageMatrix = imgMatrix
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                isScaling = false
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentZoom > 1f) {
                    currentZoom = 1f
                    initMatrix()
                } else {
                    val factor = 2f
                    currentZoom = 2f
                    imgMatrix.postScale(factor, factor, e.x, e.y)
                    clamp()
                    imageMatrix = imgMatrix
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distX: Float,
                distY: Float
            ): Boolean {
                if (!isScaling && currentZoom > 1f) {
                    imgMatrix.postTranslate(-distX, -distY)
                    clamp()
                    imageMatrix = imgMatrix
                    return true
                }
                return false
            }
        }
    )

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        scaleType = ScaleType.MATRIX
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Each tap rotates content 90° clockwise and resets zoom. */
    fun rotateBy90() {
        manualRotation = (manualRotation + 90f) % 360f
        currentZoom = 1f
        post { initMatrix() }
    }

    /** Resets rotation and zoom to the default fit-center state. */
    fun resetTransform() {
        manualRotation = 0f
        currentZoom = 1f
        post { initMatrix() }
    }

    /** Current manual rotation (0, 90, 180, 270) for use when saving/sharing. */
    // Note: Kotlin already generates getManualRotation() from the 'var' above.
    // Use the property directly: touchImageView.manualRotation

    // ── Overrides ─────────────────────────────────────────────────────────────

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        // Re-fit after the drawable changes (e.g. after Coil loads the image)
        currentZoom = 1f
        post { initMatrix() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Re-fit on device rotation or any layout change
        currentZoom = 1f
        initMatrix()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        // Prevent parent from stealing touch when zoomed in
        parent?.requestDisallowInterceptTouchEvent(currentZoom > 1f)
        return true
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resets [imgMatrix] so the image fills the view using fit-center logic,
     * applying [manualRotation] correctly (swapping w/h for 90° / 270°).
     */
    private fun initMatrix() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat().takeIf { it > 0 } ?: return
        val dh = d.intrinsicHeight.toFloat().takeIf { it > 0 } ?: return
        val vw = width.toFloat().takeIf { it > 0 } ?: return
        val vh = height.toFloat().takeIf { it > 0 } ?: return

        // For 90° / 270° the image's visible footprint swaps axes
        val isSwapped = manualRotation % 180f != 0f
        val fitW = if (isSwapped) vh else vw
        val fitH = if (isSwapped) vw else vh

        val scale = minOf(fitW / dw, fitH / dh)

        // Center the scaled image within the (fitW × fitH) logical frame
        val dx = (vw - dw * scale) / 2f
        val dy = (vh - dh * scale) / 2f

        imgMatrix.reset()
        imgMatrix.setScale(scale, scale)
        imgMatrix.postTranslate(dx, dy)

        // Rotate content around the view centre
        if (manualRotation != 0f) {
            imgMatrix.postRotate(manualRotation, vw / 2f, vh / 2f)
        }

        imageMatrix = imgMatrix
    }

    /**
     * Clamps [imgMatrix] so the image never leaves a gap at any edge when
     * zoomed in, and is always centred when it is smaller than the view.
     * Works for any rotation by computing the axis-aligned bounding box of the
     * four transformed corners.
     */
    private fun clamp() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()

        // Map the four image corners through the current matrix
        val corners = floatArrayOf(0f, 0f, dw, 0f, dw, dh, 0f, dh)
        imgMatrix.mapPoints(corners)

        val left   = minOf(corners[0], corners[2], corners[4], corners[6])
        val right  = maxOf(corners[0], corners[2], corners[4], corners[6])
        val top    = minOf(corners[1], corners[3], corners[5], corners[7])
        val bottom = maxOf(corners[1], corners[3], corners[5], corners[7])

        val imgW = right - left
        val imgH = bottom - top

        var tx = 0f
        var ty = 0f

        // Horizontal: centre if fits, otherwise clamp to avoid gaps
        tx = when {
            imgW <= width  -> (width  - imgW) / 2f - left
            left  > 0f    -> -left
            right < width  -> width  - right
            else           -> 0f
        }

        // Vertical: centre if fits, otherwise clamp to avoid gaps
        ty = when {
            imgH <= height  -> (height  - imgH) / 2f - top
            top    > 0f    -> -top
            bottom < height -> height - bottom
            else            -> 0f
        }

        if (tx != 0f || ty != 0f) {
            imgMatrix.postTranslate(tx, ty)
            imageMatrix = imgMatrix
        }
    }
}
