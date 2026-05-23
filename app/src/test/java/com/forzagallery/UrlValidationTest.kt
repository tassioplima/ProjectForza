package com.forzagallery

import org.junit.Test
import org.junit.Assert.*
import java.net.URI

/**
 * Unit tests for URL validation rules applied throughout the app.
 *
 * The JS bridge (ForzaJsInterface) rejects any URL that is blank or does not
 * start with "https://". These tests verify that contract without needing an
 * Android device or emulator.
 */
class UrlValidationTest {

    // ── Helper mirrors the validation used in ForzaJsInterface ───────────────
    private fun isValidPhotoUrl(url: String): Boolean =
        url.isNotBlank() && url.startsWith("https://")

    // ── Valid URLs ────────────────────────────────────────────────────────────

    @Test
    fun `https forza photo URL is valid`() {
        val url = "https://forza.net/media/user/abc123/photo.jpg"
        assertTrue(isValidPhotoUrl(url))
    }

    @Test
    fun `https URL with query string is valid`() {
        val url = "https://cdn.forza.net/photo.jpg?size=large&token=xyz"
        assertTrue(isValidPhotoUrl(url))
    }

    // ── Invalid URLs ──────────────────────────────────────────────────────────

    @Test
    fun `blank URL is invalid`() {
        assertFalse(isValidPhotoUrl(""))
    }

    @Test
    fun `whitespace-only URL is invalid`() {
        assertFalse(isValidPhotoUrl("   "))
    }

    @Test
    fun `http URL is invalid (no cleartext allowed)`() {
        assertFalse(isValidPhotoUrl("http://forza.net/photo.jpg"))
    }

    @Test
    fun `ftp URL is invalid`() {
        assertFalse(isValidPhotoUrl("ftp://files.example.com/photo.jpg"))
    }

    @Test
    fun `javascript injection attempt is rejected`() {
        assertFalse(isValidPhotoUrl("javascript:alert('xss')"))
    }

    @Test
    fun `data URI is rejected`() {
        assertFalse(isValidPhotoUrl("data:image/jpeg;base64,/9j/4AAQ..."))
    }
}
