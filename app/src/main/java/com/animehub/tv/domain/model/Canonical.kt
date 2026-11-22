package com.animehub.tv.domain.model

import java.text.Normalizer

/** Valores comunes entre sitios para traducir filtros (la "tabla de equivalencias"). */
object Canonical {
    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** "Acción" → "accion", "Ciencia Ficción" → "cienciaficcion". */
    fun of(label: String): String =
        Normalizer.normalize(label, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(NON_ALNUM, "")

    const val TYPE_TV = "tv"
    const val TYPE_MOVIE = "pelicula"
    const val STATUS_AIRING = "emision"
}
