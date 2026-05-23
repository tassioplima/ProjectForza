package com.forzagallery

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for the filename extraction logic used by DownloadHelper.buildFileName().
 *
 * buildFileName() uses android.net.Uri which is not available on the JVM, so
 * these tests replicate the same algorithm with java.net.URI to verify the
 * expected behaviour of each edge case.
 */
class FileNameTest {

    // ── Mirrors DownloadHelper.buildFileName() using java.net.URI ─────────────
    private fun buildFileName(url: String): String {
        return try {
            val path = java.net.URI(url).path ?: ""
            val name = path.substringAfterLast('/').substringBefore('?').trim()
            if (name.isNotEmpty() && name.contains('.')) name
            else "forza_fallback.jpg"  // stable substitute for System.currentTimeMillis()
        } catch (_: Exception) {
            "forza_fallback.jpg"
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `extracts filename from clean URL`() {
        val url = "https://forza.net/media/user/abc/my_photo.jpg"
        assertEquals("my_photo.jpg", buildFileName(url))
    }

    @Test
    fun `extracts filename when URL has query parameters`() {
        val url = "https://cdn.forza.net/photo.jpeg?width=1920&token=abc"
        assertEquals("photo.jpeg", buildFileName(url))
    }

    @Test
    fun `preserves png extension`() {
        val url = "https://forza.net/screenshot.png"
        assertEquals("screenshot.png", buildFileName(url))
    }

    @Test
    fun `falls back for URL with no file extension in path`() {
        val url = "https://forza.net/media/userphoto"
        // "userphoto" contains no dot → falls back to timestamp placeholder
        assertTrue(buildFileName(url).endsWith(".jpg"))
    }

    @Test
    fun `falls back for blank URL`() {
        assertTrue(buildFileName("").endsWith(".jpg"))
    }

    @Test
    fun `handles URL with deep path segments`() {
        val url = "https://forza.net/a/b/c/d/e/racing_shot.jpg"
        assertEquals("racing_shot.jpg", buildFileName(url))
    }

    @Test
    fun `filename with underscore and numbers is preserved`() {
        val url = "https://forza.net/gallery/photo_001.jpg"
        assertEquals("photo_001.jpg", buildFileName(url))
    }
}
