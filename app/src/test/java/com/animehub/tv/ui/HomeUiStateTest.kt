package com.animehub.tv.ui

import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.ShowcaseItem
import com.animehub.tv.domain.model.ShowcaseKind
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.ui.home.HomeUiState
import com.animehub.tv.ui.home.Review
import com.animehub.tv.util.titleWithoutLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Filas de Inicio: sin repetir títulos por idioma ni series que ya están en "Recomendados". */
class HomeUiStateTest {

    private val sites: List<EffectiveSite> = listOf(TestSites.site("latanime"), TestSites.site("animeninja"))

    private fun item(site: String, title: String, kind: ShowcaseKind, synopsis: String? = null) =
        ShowcaseItem(AnimeCard(site, title, "https://$site/anime/${title.lowercase().replace(' ', '-')}", null), kind, synopsis)

    @Test
    fun `titulo sin idioma`() {
        assertEquals("Devils' Crest", titleWithoutLanguage("Devils' Crest Castellano"))
        assertEquals("Helluva Boss S3", titleWithoutLanguage("Helluva Boss S3 Latino AMZ"))
        assertEquals("Latino", titleWithoutLanguage("Latino"))
    }

    @Test
    fun `una version por titulo, alternando sitios y sin repetir recomendados`() {
        val state = HomeUiState(
            activeSites = sites,
            showcase = mapOf(
                "latanime" to listOf(
                    item("latanime", "Black Clover Latino", ShowcaseKind.RECOMENDADO, "Asta y Yuno…"),
                    item("latanime", "Devils' Crest Castellano", ShowcaseKind.NUEVO),
                    item("latanime", "Devils' Crest Latino", ShowcaseKind.NUEVO),
                    item("latanime", "Black Clover Latino", ShowcaseKind.NUEVO),
                ),
                "animeninja" to listOf(
                    item("animeninja", "Jigokuraku", ShowcaseKind.RECOMENDADO),
                    item("animeninja", "Demons' Crest", ShowcaseKind.NUEVO),
                ),
            ),
            reviews = mapOf("animeninjahttps://animeninja/anime/jigokuraku" to Review("Un ninja…", listOf("Acción"))),
        )
        assertEquals(listOf("Black Clover Latino", "Jigokuraku"), state.recommended.map { it.card.title })
        // Devils' Crest queda una vez (en latino, en el lugar del primero); Black Clover ya está en Recomendados.
        assertEquals(listOf("Devils' Crest", "Demons' Crest"), state.newSeries.map { it.card.title })
        assertEquals("Asta y Yuno…", state.reviewOf(state.recommended[0])?.synopsis)
        assertEquals(listOf("Acción"), state.reviewOf(state.recommended[1])?.genres)
        assertNull(state.reviewOf(state.newSeries[1]))
    }
}
