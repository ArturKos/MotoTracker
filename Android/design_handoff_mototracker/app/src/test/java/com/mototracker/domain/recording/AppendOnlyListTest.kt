package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppendOnlyListTest {

    @Test
    fun `snapshot keeps its contents while the list keeps growing past several resizes`() {
        val list = AppendOnlyList<Int>()
        repeat(10) { list.add(it) }
        val early = list.snapshot()

        repeat(1_000) { list.add(100 + it) }

        assertEquals((0 until 10).toList(), early)
        assertEquals(1_010, list.snapshot().size)
        assertEquals(1_099, list.snapshot().last())
    }

    @Test
    fun `snapshot is unaffected by clear and later appends`() {
        val list = AppendOnlyList<String>()
        list.add("a"); list.add("b")
        val before = list.snapshot()

        list.clear()
        list.add("x")

        assertEquals(listOf("a", "b"), before)
        assertEquals(listOf("x"), list.snapshot())
    }

    @Test
    fun `snapshot equals a regular list with the same elements and supports subList`() {
        val list = AppendOnlyList<Int>()
        list.addAll(listOf(1, 2, 3, 4))
        val snap = list.snapshot()

        assertEquals(listOf(1, 2, 3, 4), snap)
        assertEquals(listOf(1, 2, 3, 4).hashCode(), snap.hashCode())
        assertEquals(listOf(3, 4), snap.subList(2, 4))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `snapshot rejects reads past its own size`() {
        val list = AppendOnlyList<Int>()
        list.add(1)
        val snap = list.snapshot()
        list.add(2)
        snap[1]
    }

    @Test
    fun `empty snapshot is empty`() {
        assertTrue(AppendOnlyList<Int>().snapshot().isEmpty())
    }
}
