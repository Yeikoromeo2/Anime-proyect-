package com.animehub.tv.domain

import com.animehub.tv.domain.model.EpisodePager
import com.animehub.tv.domain.model.EpisodeSection
import com.animehub.tv.fixtures.Fixtures
import com.animehub.tv.fixtures.TestSites
import com.animehub.tv.providers.common.SiteSupport
import com.animehub.tv.providers.dooplay.DooPlayParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

/** One Piece real (1180 episodios en 23 temporadas) organizado en temporadas y tramos. */
class EpisodePagerTest {

    private val episodes = DooPlayParser(SiteSupport(TestSites.site("animeninja")))
        .detail(Jsoup.parse(Fixtures.read("animeninja/anime_one_piece.html")), "https://veranime.ninja/anime/one-piece/")
        .episodes

    @Test
    fun `23 temporadas y tramos de 50 en la temporada larga`() {
        assertEquals((1..23).toList(), EpisodePager.seasons(episodes))
        val season21 = EpisodePager.episodesOf(episodes, 21)
        assertEquals(197, season21.size)
        assertEquals(listOf("892–941", "942–991", "992–1041", "1042–1088"), EpisodePager.chunks(season21).map(EpisodePager::chunkLabel))
    }

    @Test
    fun `ir al episodio 1015 abre la temporada 21 en el tramo 992-1041`() {
        val ep = EpisodePager.findByNumber(episodes, 1015f, preferSeason = 1)!!
        assertEquals("https://veranime.ninja/ver/one-piece-episodio-1015/", ep.url)
        assertEquals(EpisodeSection(21, 2), EpisodePager.sectionOf(episodes, ep))
        assertEquals(EpisodeSection(21, 2), EpisodePager.locate(episodes, "https://veranime.ninja/ver/one-piece-episodio-1015"))
    }

    @Test
    fun `temporada corta tiene un solo tramo`() {
        assertEquals(1, EpisodePager.chunks(EpisodePager.episodesOf(episodes, 23)).size)
    }
}
