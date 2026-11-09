package com.animehub.tv.util

import com.animehub.tv.domain.model.Canonical

/** Mezcla varias listas alternándolas (1º de cada una, 2º de cada una…), sin perder el orden de cada sitio. */
fun <T> interleave(lists: List<List<T>>): List<T> {
    val max = lists.maxOfOrNull { it.size } ?: 0
    return (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
}

/** Palabras de idioma que algunos sitios agregan al final del título ("… Latino", "… Castellano"). */
private val LANGUAGE_SUFFIX = Regex("""(?i)\s*[(\[]?\b(latino|castellano|español|espanol|sub español|subtitulado|japonés|japones|amz)\b[)\]]?\s*$""")

/** Título sin el idioma del final: "Devils' Crest Castellano" → "Devils' Crest". */
fun titleWithoutLanguage(title: String): String {
    var current = title.trim()
    while (true) {
        val next = LANGUAGE_SUFFIX.replace(current, "").trim()
        if (next == current || next.isEmpty()) return current
        current = next
    }
}

/**
 * Deja una sola versión de cada título cuando el sitio publica el mismo anime en varios idiomas
 * (prefiere la versión en latino). Conserva el orden de la primera aparición.
 */
fun <T> oneVersionPerTitle(items: List<T>, title: (T) -> String): List<T> =
    // groupBy conserva el orden de la primera aparición de cada título.
    items.groupBy { Canonical.of(titleWithoutLanguage(title(it))) }
        .values
        .map { versions -> versions.firstOrNull { title(it).contains("latino", ignoreCase = true) } ?: versions.first() }
