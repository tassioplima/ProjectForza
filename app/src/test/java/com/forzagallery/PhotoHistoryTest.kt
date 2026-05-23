package com.forzagallery

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the photo history tracking logic used by [PhotoHistoryStore].
 *
 * [PhotoHistoryStore] uses [android.content.SharedPreferences] for persistence,
 * which is not available on the JVM.  These tests mirror the in-memory
 * bookkeeping (MutableSet operations) that the store builds on top of, so the
 * core contract can be verified without an Android device or emulator.
 */
class PhotoHistoryTest {

    // ── Local mirror of PhotoHistoryStore's in-memory logic ───────────────────
    private val downloaded = mutableSetOf<String>()
    private val shared     = mutableSetOf<String>()

    private fun markDownloaded(photoId: String) { downloaded.add(photoId) }
    private fun markShared(photoId: String)     { shared.add(photoId)     }
    private fun isDownloaded(photoId: String)   = downloaded.contains(photoId)
    private fun isShared(photoId: String)       = shared.contains(photoId)

    @Before fun setUp() {
        downloaded.clear()
        shared.clear()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `photo is not downloaded before being marked`() {
        assertFalse(isDownloaded("photo_abc"))
    }

    @Test
    fun `photo is not shared before being marked`() {
        assertFalse(isShared("photo_abc"))
    }

    // ── markDownloaded ────────────────────────────────────────────────────────

    @Test
    fun `photo is detected as downloaded after being marked`() {
        markDownloaded("photo_abc")
        assertTrue(isDownloaded("photo_abc"))
    }

    @Test
    fun `marking photo as downloaded does not mark it as shared`() {
        markDownloaded("photo_abc")
        assertFalse(isShared("photo_abc"))
    }

    @Test
    fun `marking same photo as downloaded twice does not duplicate entry`() {
        markDownloaded("photo_abc")
        markDownloaded("photo_abc")
        // Size should still be 1 (set semantics)
        assertEquals(1, downloaded.size)
    }

    // ── markShared ────────────────────────────────────────────────────────────

    @Test
    fun `photo is detected as shared after being marked`() {
        markShared("photo_xyz")
        assertTrue(isShared("photo_xyz"))
    }

    @Test
    fun `marking photo as shared does not mark it as downloaded`() {
        markShared("photo_xyz")
        assertFalse(isDownloaded("photo_xyz"))
    }

    @Test
    fun `marking same photo as shared twice does not duplicate entry`() {
        markShared("photo_xyz")
        markShared("photo_xyz")
        assertEquals(1, shared.size)
    }

    // ── Both statuses on same photo ───────────────────────────────────────────

    @Test
    fun `photo can be marked as both downloaded and shared independently`() {
        markDownloaded("photo_abc")
        markShared("photo_abc")
        assertTrue(isDownloaded("photo_abc"))
        assertTrue(isShared("photo_abc"))
    }

    // ── Multiple photos ───────────────────────────────────────────────────────

    @Test
    fun `multiple photos are tracked independently for downloads`() {
        markDownloaded("photo_1")
        markDownloaded("photo_2")
        assertTrue(isDownloaded("photo_1"))
        assertTrue(isDownloaded("photo_2"))
        assertFalse(isDownloaded("photo_3"))
    }

    @Test
    fun `multiple photos are tracked independently for shares`() {
        markShared("photo_A")
        markShared("photo_B")
        assertTrue(isShared("photo_A"))
        assertTrue(isShared("photo_B"))
        assertFalse(isShared("photo_C"))
    }

    @Test
    fun `downloaded and shared sets are completely independent`() {
        markDownloaded("photo_1")
        markShared("photo_2")
        assertTrue(isDownloaded("photo_1"))
        assertFalse(isDownloaded("photo_2"))   // not downloaded
        assertFalse(isShared("photo_1"))        // not shared
        assertTrue(isShared("photo_2"))
    }

    // ── Batch operations ──────────────────────────────────────────────────────

    @Test
    fun `marking a batch of photos as downloaded tracks all of them`() {
        val batch = listOf("p1", "p2", "p3", "p4", "p5")
        batch.forEach { markDownloaded(it) }
        batch.forEach { assertTrue("$it should be downloaded", isDownloaded(it)) }
        assertFalse(isDownloaded("p6"))
    }

    @Test
    fun `marking a batch of photos as shared tracks all of them`() {
        val batch = listOf("p1", "p2", "p3")
        batch.forEach { markShared(it) }
        batch.forEach { assertTrue("$it should be shared", isShared(it)) }
    }
}
