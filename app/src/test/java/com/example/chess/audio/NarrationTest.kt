package com.example.chess.audio

import com.example.chess.checkers.CheckersLessonCatalog
import com.example.chess.lessons.LessonCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationTest {

    @Test
    fun picturesAndArrowsAreNotRead() {
        assertEquals("How about this: Knight to f3?", Narration.clean("💡 How about this: Knight to f3?"))
        assertEquals("You have a double jump! Can you find it?", Narration.clean("✨ You have a double jump! Can you find it?"))
        assertEquals("Next", Narration.clean("Next ➔"))
        assertEquals("Tip: Control the center!", Narration.clean("Tip: Control the center! ⭐"))
        assertEquals("Got it! 2 more stars to go.", Narration.clean("Got it! 2 more stars to go."))
    }

    @Test
    fun combinedEmojiVanishToo() {
        assertEquals("Coach tips", Narration.clean("🧑‍🏫 Coach tips"))
        assertEquals("Warning!", Narration.clean("⚠️ Warning!"))
        assertEquals("", Narration.clean("🎉"))
    }

    @Test
    fun wordsAndPunctuationAreKept() {
        val line = "Let's go! A Knight's jump looks like an \"L\" — 3 squares."
        assertEquals(line, Narration.clean(line))
        assertEquals("one two", Narration.clean("  one \n  two  "))
    }

    @Test
    fun everyLessonLineHasSomethingToSay() {
        val lines = ArrayList<String>()
        for (lesson in LessonCatalog.lessons) {
            lines += lesson.intro
            for (c in lesson.challenges) { lines += c.instruction; c.wrongText?.let { lines += it } }
        }
        for (lesson in CheckersLessonCatalog.lessons) {
            lines += lesson.intro
            for (c in lesson.challenges) { lines += c.instruction; c.wrongText?.let { lines += it } }
        }
        assertTrue(lines.size > 100)
        for (line in lines) {
            val spoken = Narration.clean(line)
            assertTrue("nothing left to say in: $line", spoken.isNotEmpty())
            assertFalse("symbols left in: $spoken", spoken.any { Character.getType(it) == Character.OTHER_SYMBOL.toInt() })
        }
    }
}
