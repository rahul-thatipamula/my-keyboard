package com.caxone.my_keyboard

import com.caxone.my_keyboard.keyboard.KeyType
import com.caxone.my_keyboard.keyboard.LetterLayout
import com.caxone.my_keyboard.keyboard.Layouts
import com.caxone.my_keyboard.prediction.EditDistance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {

    @After
    fun restoreQwerty() {
        EditDistance.useLayout(Layouts.letterPositions(LetterLayout.QWERTY))
    }

    @Test
    fun everyLayoutHasAllTwentySixLetters() {
        for (layout in LetterLayout.entries) {
            val letters = layout.rows.joinToString("").filter { it.isLetter() }.toSet()
            assertEquals(layout.name, ('a'..'z').toSet(), letters)
        }
    }

    @Test
    fun lettersLayoutCarriesShiftDeleteAndBottomRow() {
        for (layout in LetterLayout.entries) {
            val built = Layouts.letters(layout)
            assertTrue(built.isLetters)
            assertEquals(layout.id, built.id)
            assertEquals(4, built.rows.size)
            assertEquals(KeyType.SHIFT, built.rows[2].first().type)
            assertEquals(KeyType.DELETE, built.rows[2].last().type)
            assertTrue(built.rows[3].any { it.type == KeyType.SPACE })
        }
        assertEquals(5, Layouts.letters(LetterLayout.QWERTY, numberRow = true).rows.size)
    }

    @Test
    fun qwertyPositionsMatchTheClassicOffsets() {
        val pos = Layouts.letterPositions(LetterLayout.QWERTY)
        assertEquals(0 to 0.5f, pos['q'])
        assertEquals(1 to 1.0f, pos['a'])
        assertEquals(2 to 2.0f, pos['z'])
    }

    @Test
    fun dvorakBottomRowIsShrunkToFit() {
        val pos = Layouts.letterPositions(LetterLayout.DVORAK)
        for ((_, p) in pos) assertTrue(p.second in 0f..Layouts.ROW_UNITS)
        assertTrue(pos.getValue('z').second > pos.getValue('v').second)
    }

    @Test
    fun adjacencyFollowsTheChosenLayout() {
        EditDistance.useLayout(Layouts.letterPositions(LetterLayout.DVORAK))
        assertTrue(EditDistance.adjacent('a', 'o'))
        assertTrue(EditDistance.adjacent('a', ','))
        assertFalse(EditDistance.adjacent('a', 's'))

        EditDistance.useLayout(Layouts.letterPositions(LetterLayout.COLEMAK))
        assertTrue(EditDistance.adjacent('a', 'r'))
        assertFalse(EditDistance.adjacent('a', 's'))
    }
}
