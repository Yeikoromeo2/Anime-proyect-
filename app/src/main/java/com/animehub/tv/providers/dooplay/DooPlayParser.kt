package com.animehub.tv.providers.dooplay

import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.AnimeDetail
import com.animehub.tv.domain.model.AnimeStatus
import com.animehub.tv.domain.model.AnimeType
import com.animehub.tv.domain.model.Episode
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.FilterOption
import com.animehub.tv.domain.model.PagedResult
import com.animehub.tv.providers.common.SiteSupport
import com.animehub.tv.providers.common.SiteSupport.Companion.clean
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Opción de reproductor de DooPlay (se resuelve con admin-ajax.php). */
data class DooPlayOption(val label: String, val post: String, val nume: String, val type: String)

/**
 * Lectura del HTML de sitios WordPress con el tema DooPlay (Anime Ninja).
 * Todos los selectores vienen de la configuración remota (ver docs/sites/animeninja.md).
 */
class DooPlayParser(private val s: SiteSupport) {

    private val siteId get() = s.site.id

    fun latest(doc: Document): List<EpisodeCard> {
        val pattern = s.optional(s.site.config.latest, "animeUrlPattern")?.let(::Regex)
        val replacement = s.optional(s.site.config.latest, "animeUrlReplacement")
        return doc.select(s.latest("card")).mapNotNull { card ->
            val link = card.selectFirst(s.latest("link"))?.attr("href")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val episodeUrl = s.absolute(link)
            EpisodeCard(
                siteId = siteId,
                animeTitle = clean(card.selectFirst(s.latest("title"))?.text()),
                episodeNumber = card.selectFirst(s.latest("episode"))?.text()?.let(::firstNumber),
                episodeUrl = episodeUrl,
                imageUrl = s.cover(card.selectFirst(s.latest("image"))),
                animeUrl = if (pattern != null && replacement != null && pattern.containsMatchIn(episodeUrl)) {
                    episodeUrl.replace(pattern, replacement)
                } else null,
                language = card.selectFirst(s.latest("language"))?.text()?.let(::clean)?.ifEmpty { null },
                dateLabel = s.optional(s.site.config.latest, "date")?.let { card.selectFirst(it)?.text() }?.let(::clean),
            )
        }
    }

    /** Resultados de `/?s=`. */
    fun search(doc: Document, page: Int): PagedResult<AnimeCard> {
        val items = doc.select(s.list("searchCard")).mapNotNull { card ->
            val link = card.selectFirst(s.list("searchTitle")) ?: return@mapNotNull null
            AnimeCard(
                siteId = siteId,
                title = clean(link.text()),
                url = s.absolute(link.attr("href")),
                coverUrl = s.cover(card.selectFirst(s.list("searchCover"))),
                type = typeFromLabel(card.selectFirst(s.list("searchType"))?.let { it.className() + " " + it.text() }),
                year = SiteSupport.year(card.selectFirst(s.list("searchYear"))?.text()),
            )
        }
        return PagedResult(items, page, hasNextPage(doc, page))
    }

    /** Páginas de archivo (`/anime/`, `/pelicula/`, `/genero/…`). */
    fun archive(doc: Document, page: Int): PagedResult<AnimeCard> {
        val items = doc.select(s.list("card")).mapNotNull { card ->
            val link = card.selectFirst(s.list("link")) ?: return@mapNotNull null
            AnimeCard(
                siteId = siteId,
                title = clean(link.text()),
                url = s.absolute(link.attr("href")),
                coverUrl = s.cover(card.selectFirst(s.list("cover"))),
                type = typeFromLabel(card.className()),
                year = SiteSupport.year(s.optional(s.site.config.selectors, "year")?.let { card.selectFirst(it)?.text() }),
            )
        }
        return PagedResult(items, page, hasNextPage(doc, page))
    }

    fun detail(doc: Document, url: String): AnimeDetail {
        val d = s.site.config.detail
        val numberRegex = s.optional(d, "episodeNumberRegex")?.let(::Regex)
        val episodes = doc.select(s.detail("seasons")).flatMap { seasonEl ->
            val season = seasonEl.selectFirst(s.detail("seasonNumber"))?.text()?.trim()?.toIntOrNull()
            seasonEl.select(s.detail("episodes")).mapNotNull { li -> episode(li, season, numberRegex) }
        }
        val fields = s.optional(d, "fields")?.let { sel ->
            doc.select(sel).associate { f ->
                clean(f.selectFirst(s.detail("fieldName"))?.text()) to clean(f.selectFirst(s.detail("fieldValue"))?.text())
            }
        }.orEmpty()
        val isMovie = url.contains("/pelicula/")
        return AnimeDetail(
            siteId = siteId,
            url = url,
            title = clean(doc.selectFirst(s.detail("title"))?.text()),
            coverUrl = s.cover(doc.selectFirst(s.detail("cover"))),
            synopsis = doc.selectFirst(s.detail("synopsis"))?.text()?.let(::clean)?.ifEmpty { null },
            genres = doc.select(s.detail("genres")).map { clean(it.text()) }.filter { it.isNotEmpty() }.distinct(),
            status = status(fields[s.optional(d, "lastAiredField")]),
            type = if (isMovie) AnimeType.PELICULA else AnimeType.TV,
            year = SiteSupport.year(s.optional(d, "date")?.let { doc.selectFirst(it)?.text() }),
            // Una película no tiene lista: su propia página es el "episodio" a reproducir.
            episodes = if (isMovie && episodes.isEmpty()) listOf(Episode(1f, "Película", url)) else episodes,
        )
    }

    /** Opciones de reproductor de la página de un episodio o película (sin el tráiler). */
    fun playerOptions(doc: Document): List<DooPlayOption> {
        val skip = s.optional(s.site.config.servers, "skipNume")
        return doc.select(s.server("item")).mapNotNull { li ->
            val post = li.attr("data-post").ifBlank { return@mapNotNull null }
            val nume = li.attr("data-nume").ifBlank { return@mapNotNull null }
            if (nume == skip) return@mapNotNull null
            DooPlayOption(label = optionLabel(li), post = post, nume = nume, type = li.attr("data-type").ifBlank { "tv" })
        }
    }

    /** Géneros leídos de los enlaces `/genero/<slug>/` de una página de archivo. */
    fun genres(doc: Document, linksSelector: String): List<FilterOption> =
        doc.select(linksSelector).mapNotNull { a ->
            val slug = GENRE_SLUG.find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            FilterOption(slug, clean(a.text()))
        }.distinctBy { it.value }.sortedBy { it.label.lowercase() }

    private fun episode(li: Element, season: Int?, numberRegex: Regex?): Episode? {
        val link = li.selectFirst(s.detail("episodeLink")) ?: return null
        val numberText = li.selectFirst(s.detail("episodeNumber"))?.text().orEmpty()
        val match = numberRegex?.find(numberText)
        val number = match?.groupValues?.getOrNull(2)?.toFloatOrNull() ?: firstNumber(link.text()) ?: return null
        return Episode(
            number = number,
            title = clean(link.text()),
            url = s.absolute(link.attr("href")),
            imageUrl = s.optional(s.site.config.detail, "episodeImage")?.let { s.cover(li.selectFirst(it)) },
            season = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: season,
        )
    }

    /** El nombre va en varios `<span>` sin espacios ("MULTISERVER" + "animeav1.com"). */
    private fun optionLabel(li: Element): String =
        li.children().map { clean(it.text()) }.filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { clean(li.text()) }

    private fun hasNextPage(doc: Document, page: Int): Boolean =
        doc.select(s.list("nextPage")).any { a ->
            PAGE_NUMBER.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()?.let { it > page } == true
        }

    /**
     * DooPlay no publica el estado. Solo se afirma "En emisión" si la "Última transmisión" es
     * reciente (≤ 21 días); si no, se deja desconocido (una serie en pausa no está finalizada).
     */
    private fun status(lastAired: String?): AnimeStatus {
        val date = lastAired?.let(::parseEnglishDate) ?: return AnimeStatus.DESCONOCIDO
        val days = (System.currentTimeMillis() - date) / 86_400_000L
        return if (days in 0..21) AnimeStatus.EMISION else AnimeStatus.DESCONOCIDO
    }

    companion object {
        private val PAGE_NUMBER = Regex("""/page/(\d+)""")
        private val GENRE_SLUG = Regex("""/genero/([^/?#]+)""")
        private val NUMBER = Regex("""\d+(?:\.\d+)?""")
        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        fun firstNumber(text: String): Float? = NUMBER.find(text)?.value?.toFloatOrNull()

        fun typeFromLabel(label: String?): AnimeType? {
            val l = label?.lowercase() ?: return null
            return when {
                "movie" in l || "pel" in l -> AnimeType.PELICULA
                "tvshow" in l || Regex("""\btv\b""").containsMatchIn(l) -> AnimeType.TV
                else -> null
            }
        }

        /** "Jul. 19, 2026" → milisegundos (UTC). */
        fun parseEnglishDate(text: String): Long? {
            val m = Regex("""([A-Za-z]{3})[a-z]*\.?\s+(\d{1,2}),\s*(\d{4})""").find(text) ?: return null
            val month = MONTHS.indexOf(m.groupValues[1].lowercase()).takeIf { it >= 0 } ?: return null
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())
            }
            return cal.timeInMillis
        }
    }
}
