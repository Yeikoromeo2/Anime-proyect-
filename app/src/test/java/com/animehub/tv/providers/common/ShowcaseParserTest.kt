package com.animehub.tv.providers.common

import com.animehub.tv.domain.model.ShowcaseKind
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.fixtures.TestSites
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recomendados y series nuevas de la portada de cada sitio, con los selectores de config/sites.json. */
class ShowcaseParserTest {

    private suspend fun showcase(siteId: String, base: String, fixture: String) =
        TestSites.site(siteId).let { site ->
            ShowcaseParser(SiteSupport(site)).load(site.config.showcase, siteId) { path ->
                assertEquals("/", path)
                Jsoup.parse(Fixtures.read(fixture), "$base/")
            }
        }

    @Test
    fun `anime ninja - recomendados con valoracion y animes recientes con fecha`() = runTest {
        val items = showcase("animeninja", "https://veranime.ninja", "animeninja/inicio.html")
        val recommended = items.filter { it.kind == ShowcaseKind.RECOMENDADO }
        val new = items.filter { it.kind == ShowcaseKind.NUEVO }

        assertEquals(8, recommended.size)
        with(recommended.first()) {
            assertEquals("Jigokuraku", card.title)
            assertEquals("https://veranime.ninja/anime/jigokuraku/", card.url)
            assertEquals("https://image.tmdb.org/t/p/w342/jfPqPQy2EiS8TwDlbvU9hgyxCdF.jpg", card.coverUrl)
            assertEquals(2023, card.year)
            assertEquals("10", rating)
            assertNull("DooPlay no trae reseña en la portada", synopsis)
        }
        assertEquals("9.3", recommended[1].rating)

        assertEquals(18, new.size)
        with(new.first()) {
            assertEquals("Demons’ Crest", card.title)
            assertEquals("https://veranime.ninja/anime/demons-crest/", card.url)
            assertEquals("Nov. 06, 2026", dateLabel)
            assertEquals(2026, card.year)
            assertNull("Una valoración de 0 no se muestra", rating)
        }
        assertEquals("5.2", new[1].rating)
        // Las secciones de películas y temporadas no se cuelan.
        assertTrue(new.none { it.card.url.contains("/pelicula/") || it.card.title.startsWith("Temporada") })
    }

    @Test
    fun `latanime - carrusel con resena y series recientes`() = runTest {
        val items = showcase("latanime", "https://latanime.org", "latanime/inicio.html")
        val recommended = items.filter { it.kind == ShowcaseKind.RECOMENDADO }
        val new = items.filter { it.kind == ShowcaseKind.NUEVO }

        assertEquals(16, recommended.size)
        with(recommended.first()) {
            assertEquals("Black Clover Latino", card.title)
            assertEquals("https://latanime.org/anime/black-clover-latino", card.url)
            assertEquals("https://latanime.org/assets/img/serie/imagen/black-clover-latino-1792864570.jpg", card.coverUrl)
            assertTrue(synopsis!!.startsWith("Asta y Yuno son huérfanos que fueron criados juntos"))
        }
        // El título sale completo del atributo alt (el texto del carrusel viene recortado).
        assertEquals("Tokyo Revengers: Santen Sensou-hen Latino", recommended[2].card.title)
        assertNull(recommended[2].synopsis)

        assertTrue("Series recientes: ${new.size}", new.size >= 10)
        with(new.first()) {
            assertEquals("Tensei Kizoku, Kantei Skill de Nariagaru S1 Latino", card.title)
            assertEquals("https://latanime.org/anime/as-a-reincarnated-aristocrat-ill-use-my-appraisal-skill-to-rise-in-the-new-world", card.url)
            assertTrue(card.coverUrl!!.startsWith("https://latanime.org/thumbs/imagen/"))
        }
        assertEquals("Devils' Crest Castellano", new[1].card.title)
    }
}
