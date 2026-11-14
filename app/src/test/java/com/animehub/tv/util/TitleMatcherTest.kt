package com.animehub.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.animehub.tv.domain.model.AnimeCard
import org.junit.Test

class TitleMatcherTest {
    private fun same(a: String, b: String) = TitleMatcher.same(a, b)

    @Test
    fun `mismo titulo escrito distinto`() {
        assertTrue(same("Ragna Crimson", "ragna crimson"))
        assertTrue(same("Ōkami-san to Shichinin no Nakama-tachi", "Okami san to Shichinin no Nakama tachi"))
        assertTrue(same("Dr. Stone: New World", "Dr Stone New World"))
        assertTrue(same("Tank Chair Latino", "TANK CHAIR Castellano"))
        assertTrue(same("Black Clover (Sub Español)", "Black Clover Latino"))
        assertTrue(same("Boku no Hero Academia", "Bokuno Hero Academia"))
    }

    @Test
    fun `temporadas escritas de varias formas son la misma`() {
        assertTrue(same("Tougen Anki Temporada 2", "Tougen Anki S2 Latino"))
        assertTrue(same("Tougen Anki 2nd Season", "Tougen Anki Season 2"))
        assertTrue(same("Overlord III", "Overlord 3"))
        assertTrue(same("Link Click S1", "Link Click"))
    }

    @Test
    fun `otra temporada u otro anime no se une`() {
        assertFalse(same("Overlord 2", "Overlord 3"))
        assertFalse(same("Link Click S3", "Link Click"))
        assertFalse(same("Black Clover", "Black Cat"))
        assertFalse(same("Dragon Ball Super", "Dragon Ball Daima"))
        assertFalse(same("One Piece", "One Punch Man"))
        assertFalse(same("", "One Piece"))
    }

    @Test
    fun `uno es el otro con palabras de mas`() {
        assertTrue(same("Sousou no Frieren", "Frieren: Sousou no Frieren Beyond Journey's End".replace("Frieren: ", "")))
    }

    @Test
    fun `agrupa conservando el orden y a quien aparece primero`() {
        val items = listOf("Ragna Crimson|a", "One Piece|a", "Ragna Crimson Latino|b", "ONE PIECE|c", "Frieren|a")
        val merged = TitleMatcher.group(items, { it.substringBefore('|') }) { main, others -> main + "+" + others.joinToString("+") { it.substringAfter('|') } }
        assertEquals(listOf("Ragna Crimson|a+b", "One Piece|a+c", "Frieren|a"), merged)
    }

    @Test
    fun `el mismo nombre con otro año es otra serie`() {
        assertFalse(same("Ranma ½ (2024)", "Ranma ½"))
        assertFalse(same("Ranma ½ (2024)", "Ranma ½ (1989)"))
        assertTrue(same("Ranma ½ (2024) Latino", "RANMA ½ (2024) Castellano"))
    }

    @Test
    fun `tarjetas con años distintos no se unen`() {
        val a = AnimeCard("animeninja", "Ranma ½", "u1", null, year = 1989)
        val b = AnimeCard("jkanime", "Ranma ½", "u2", null, year = 2024)
        assertEquals(2, unifyCards(listOf(a, b)).size)
    }
}
