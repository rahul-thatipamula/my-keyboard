package com.caxone.my_keyboard

import com.caxone.my_keyboard.prediction.EditDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditDistanceTest {
    @Test
    fun identicalWordsHaveZeroDistance() {
        assertEquals(0.0, EditDistance.distance("hello", "hello", 2.0), 0.0001)
    }

    @Test
    fun neighbouringKeyTypoIsCheaperThanRandomSubstitution() {
        val neighbour = EditDistance.distance("hwllo", "hello", 2.0) // w is next to e
        val random = EditDistance.distance("hzllo", "hello", 2.0)   // z is far from e
        assertTrue(neighbour < random)
        assertEquals(0.6, neighbour, 0.0001)
        assertEquals(1.0, random, 0.0001)
    }

    @Test
    fun transpositionCostsLessThanTwoEdits() {
        assertEquals(0.8, EditDistance.distance("teh", "the", 2.0), 0.0001)
    }

    @Test
    fun insertionAndDeletionCostOne() {
        assertEquals(1.0, EditDistance.distance("helo", "hello", 2.0), 0.0001)
        assertEquals(1.0, EditDistance.distance("helllo", "hello", 2.0), 0.0001)
    }

    @Test
    fun bailsOutEarlyWhenOverMax() {
        assertTrue(EditDistance.distance("keyboard", "banana", 1.0) > 1.0)
    }

    @Test
    fun adjacency() {
        assertTrue(EditDistance.adjacent('e', 'r'))
        assertTrue(EditDistance.adjacent('e', 'd'))
        assertTrue(!EditDistance.adjacent('q', 'p'))
    }
}
