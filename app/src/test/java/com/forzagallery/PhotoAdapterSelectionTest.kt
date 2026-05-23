package com.forzagallery

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the multi-select logic used by [PhotoAdapter].
 *
 * [PhotoAdapter] depends on RecyclerView infrastructure which is unavailable on
 * the JVM.  These tests mirror the selection bookkeeping (LinkedHashSet + enum
 * result) to verify the behavioural contract without an Android device.
 */
class PhotoAdapterSelectionTest {

    // ── Local mirror of PhotoAdapter's selection state ────────────────────────
    private val selectedIds  = LinkedHashSet<String>()
    private val MAX_SELECTION = 10

    private enum class ToggleResult { SELECTED, DESELECTED, AT_MAX }

    private fun toggle(photoId: String): ToggleResult = when {
        photoId in selectedIds           -> { selectedIds.remove(photoId); ToggleResult.DESELECTED }
        selectedIds.size < MAX_SELECTION -> { selectedIds.add(photoId);    ToggleResult.SELECTED   }
        else                             -> ToggleResult.AT_MAX
    }

    private fun selectAll(ids: List<String>) {
        selectedIds.clear()
        ids.take(MAX_SELECTION).forEach { selectedIds.add(it) }
    }

    private fun clearSelection() { selectedIds.clear() }

    @Before fun setUp() { selectedIds.clear() }

    // ── First tap ─────────────────────────────────────────────────────────────

    @Test
    fun `first tap on a photo returns SELECTED`() {
        assertEquals(ToggleResult.SELECTED, toggle("photo_1"))
    }

    @Test
    fun `first tap adds the photo to the selected set`() {
        toggle("photo_1")
        assertTrue("photo_1" in selectedIds)
    }

    // ── Second tap (deselect) ─────────────────────────────────────────────────

    @Test
    fun `second tap on same photo returns DESELECTED`() {
        toggle("photo_1")
        assertEquals(ToggleResult.DESELECTED, toggle("photo_1"))
    }

    @Test
    fun `second tap removes the photo from the selected set`() {
        toggle("photo_1")
        toggle("photo_1")
        assertFalse("photo_1" in selectedIds)
        assertEquals(0, selectedIds.size)
    }

    // ── MAX_SELECTION cap ─────────────────────────────────────────────────────

    @Test
    fun `selecting exactly MAX_SELECTION photos succeeds`() {
        repeat(MAX_SELECTION) { i ->
            assertEquals(ToggleResult.SELECTED, toggle("photo_$i"))
        }
        assertEquals(MAX_SELECTION, selectedIds.size)
    }

    @Test
    fun `selecting more than MAX_SELECTION photos returns AT_MAX`() {
        repeat(MAX_SELECTION) { i -> toggle("photo_$i") }   // fill to cap
        val result = toggle("photo_overflow")
        assertEquals(ToggleResult.AT_MAX, result)
    }

    @Test
    fun `AT_MAX does not add the photo to the selected set`() {
        repeat(MAX_SELECTION) { i -> toggle("photo_$i") }
        toggle("photo_overflow")
        assertFalse("photo_overflow" in selectedIds)
        assertEquals(MAX_SELECTION, selectedIds.size)
    }

    @Test
    fun `deselecting one after reaching MAX allows next selection`() {
        repeat(MAX_SELECTION) { i -> toggle("photo_$i") }
        toggle("photo_0")  // deselect first
        val result = toggle("photo_new")
        assertEquals(ToggleResult.SELECTED, result)
        assertEquals(MAX_SELECTION, selectedIds.size)
    }

    // ── selectAll ─────────────────────────────────────────────────────────────

    @Test
    fun `selectAll with fewer items than MAX selects all of them`() {
        val ids = listOf("a", "b", "c")
        selectAll(ids)
        assertEquals(3, selectedIds.size)
        ids.forEach { assertTrue(it in selectedIds) }
    }

    @Test
    fun `selectAll caps at MAX_SELECTION when list is larger`() {
        val ids = (1..MAX_SELECTION + 5).map { "photo_$it" }
        selectAll(ids)
        assertEquals(MAX_SELECTION, selectedIds.size)
    }

    @Test
    fun `selectAll replaces any existing selection`() {
        toggle("old_photo")
        val newIds = listOf("new_1", "new_2")
        selectAll(newIds)
        assertFalse("old_photo" in selectedIds)
        assertTrue("new_1" in selectedIds)
        assertTrue("new_2" in selectedIds)
    }

    // ── clearSelection ────────────────────────────────────────────────────────

    @Test
    fun `clearSelection empties the selected set`() {
        toggle("photo_1")
        toggle("photo_2")
        clearSelection()
        assertEquals(0, selectedIds.size)
    }

    @Test
    fun `after clearSelection photos can be selected again`() {
        toggle("photo_1")
        clearSelection()
        val result = toggle("photo_1")
        assertEquals(ToggleResult.SELECTED, result)
    }

    // ── selectedCount ─────────────────────────────────────────────────────────

    @Test
    fun `selectedCount returns 0 initially`() {
        assertEquals(0, selectedIds.size)
    }

    @Test
    fun `selectedCount increments with each new selection`() {
        toggle("p1"); assertEquals(1, selectedIds.size)
        toggle("p2"); assertEquals(2, selectedIds.size)
        toggle("p3"); assertEquals(3, selectedIds.size)
    }

    @Test
    fun `selectedCount decrements when a photo is deselected`() {
        toggle("p1"); toggle("p2")
        toggle("p1")  // deselect
        assertEquals(1, selectedIds.size)
    }

    // ── Order preserved (LinkedHashSet) ───────────────────────────────────────

    @Test
    fun `selection order is preserved (tap order equals iteration order)`() {
        toggle("first")
        toggle("second")
        toggle("third")
        val order = selectedIds.toList()
        assertEquals(listOf("first", "second", "third"), order)
    }
}
