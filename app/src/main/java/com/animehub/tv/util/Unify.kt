package com.animehub.tv.util

import com.animehub.tv.domain.model.AnimeCard
import com.animehub.tv.domain.model.EpisodeCard
import com.animehub.tv.domain.model.ShowcaseItem
import java.util.concurrent.ConcurrentHashMap

/**
 * El mismo anime suele estar en varios sitios (y en varios idiomas). En las listas se muestra una
 * sola vez; este índice recuerda las otras versiones para que la ficha junte sus episodios.
 */
object SameAnimeIndex {
    /** Prioridad de cada sitio (de la configuración): la versión del que más tenga es la principal. */
    @Volatile var priorities: Map<String, Int> = emptyMap()

    private val others = ConcurrentHashMap<String, List<Pair<String, String>>>()
    private fun key(siteId: String, url: String) = "$siteId|$url"

    /** La primera versión es la principal. */
    fun register(group: List<Pair<String, String>>) {
        group.forEach { me -> others[key(me.first, me.second)] = group.filter { it != me } }
    }

    /** (sitio, dirección de la ficha) de las otras versiones del mismo anime. */
    fun alternatesOf(siteId: String, url: String): List<Pair<String, String>> = others[key(siteId, url)].orEmpty()
}

/** Quien manda entre las versiones: el sitio con más prioridad; a igualdad, la latina; luego el orden de aparición. */
private fun <T> principal(all: List<T>, title: (T) -> String, site: (T) -> String): List<T> {
    val sorted = all.withIndex().sortedWith(
        compareBy<IndexedValue<T>>(
            { -(SameAnimeIndex.priorities[site(it.value)] ?: 0) },
            { if (title(it.value).contains("latino", ignoreCase = true)) 0 else 1 },
            { it.index },
        ),
    ).map { it.value }
    return sorted
}

/** Título para mostrar: sin el idioma del final y, si hay, uno que no esté todo en mayúsculas. */
private fun <T> displayTitle(all: List<T>, title: (T) -> String): String {
    val clean = all.map { titleWithoutLanguage(title(it)) }
    return clean.firstOrNull { it != it.uppercase() } ?: clean.first()
}

/** Une los títulos que son el mismo anime (otro sitio, otro idioma, otra escritura). */
fun unifyCards(cards: List<AnimeCard>): List<AnimeCard> =
    TitleMatcher.group(cards, { it.title }) { first, rest ->
        val ordered = principal(listOf(first) + rest, { it.title }, { it.siteId })
        SameAnimeIndex.register(ordered.map { it.siteId to it.url })
        val main = ordered.first()
        main.copy(
            title = displayTitle(ordered) { it.title },
            coverUrl = main.coverUrl ?: ordered.firstNotNullOfOrNull { it.coverUrl },
            year = main.year ?: ordered.firstNotNullOfOrNull { it.year },
            type = main.type ?: ordered.firstNotNullOfOrNull { it.type },
        )
    }

fun unifyShowcase(items: List<ShowcaseItem>): List<ShowcaseItem> =
    TitleMatcher.group(items, { it.card.title }) { first, rest ->
        val ordered = principal(listOf(first) + rest, { it.card.title }, { it.card.siteId })
        SameAnimeIndex.register(ordered.map { it.card.siteId to it.card.url })
        val main = ordered.first()
        main.copy(
            card = main.card.copy(
                title = displayTitle(ordered) { it.card.title },
                coverUrl = main.card.coverUrl ?: ordered.firstNotNullOfOrNull { it.card.coverUrl },
            ),
            synopsis = main.synopsis ?: ordered.firstNotNullOfOrNull { it.synopsis },
            rating = main.rating ?: ordered.firstNotNullOfOrNull { it.rating },
        )
    }

/** Un mismo episodio publicado en varios sitios sale una sola vez. */
fun unifyEpisodes(cards: List<EpisodeCard>): List<EpisodeCard> {
    val groups = mutableListOf<MutableList<EpisodeCard>>()
    for (c in cards) {
        val home = groups.firstOrNull { g -> g.first().episodeNumber == c.episodeNumber && g.any { TitleMatcher.same(it.animeTitle, c.animeTitle) } }
        if (home != null) home += c else groups += mutableListOf(c)
    }
    return groups.map { g ->
        if (g.size == 1) return@map g.first()
        val ordered = principal(g, { it.animeTitle }, { it.siteId })
        val pairs = ordered.mapNotNull { e -> e.animeUrl?.let { e.siteId to it } }
        if (pairs.size > 1) SameAnimeIndex.register(pairs)
        val main = ordered.first()
        main.copy(animeTitle = displayTitle(ordered) { it.animeTitle }, imageUrl = main.imageUrl ?: ordered.firstNotNullOfOrNull { it.imageUrl })
    }
}
