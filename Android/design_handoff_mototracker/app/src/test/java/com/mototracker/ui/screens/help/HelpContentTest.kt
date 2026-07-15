package com.mototracker.ui.screens.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {

    @Test
    fun `topics is not empty`() {
        assertTrue(HelpContent.topics.isNotEmpty())
    }

    @Test
    fun `every topic has a non-zero titleRes`() {
        HelpContent.topics.forEach { topic ->
            assertTrue("titleRes should be non-zero for topic $topic", topic.titleRes != 0)
        }
    }

    @Test
    fun `every topic has a non-zero bodyRes`() {
        HelpContent.topics.forEach { topic ->
            assertTrue("bodyRes should be non-zero for topic $topic", topic.bodyRes != 0)
        }
    }

    @Test
    fun `no duplicate titleRes values`() {
        val seen = mutableSetOf<Int>()
        HelpContent.topics.forEach { topic ->
            assertFalse(
                "Duplicate titleRes 0x${topic.titleRes.toString(16)} found in HelpContent.topics",
                topic.titleRes in seen,
            )
            seen.add(topic.titleRes)
        }
    }

    @Test
    fun `no duplicate bodyRes values`() {
        val seen = mutableSetOf<Int>()
        HelpContent.topics.forEach { topic ->
            assertFalse(
                "Duplicate bodyRes 0x${topic.bodyRes.toString(16)} found in HelpContent.topics",
                topic.bodyRes in seen,
            )
            seen.add(topic.bodyRes)
        }
    }

    @Test
    fun `topics covers all seven expected feature areas`() {
        assertEquals(7, HelpContent.topics.size)
    }
}
