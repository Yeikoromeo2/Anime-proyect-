package com.animehub.tv.domain.model

/** Resultado de traducir los filtros comunes a un sitio. */
data class SiteFilters(
    val selection: FilterSelection,
    /** Grupos que el sitio no puede aplicar (p. ej. Anime Ninja no tiene "Año"). */
    val unsupported: List<String>,
)

/** Acceso rápido: una combinación de filtros con nombre. */
data class QuickFilter(val label: String, val selection: FilterSelection)

/**
 * Tabla de equivalencias entre sitios. En "Todos los sitios" solo se muestran los filtros
 * comunes (género, año, tipo, estado) y cada valor se traduce al parámetro de cada sitio
 * comparando su valor canónico (etiqueta normalizada o el mapa de la configuración).
 */
object FilterTranslator {

    private val SUMMARY_ORDER = listOf("genre", "year", "type", "status", "letter")

    /** Grupos comunes en orden de aparición. */
    val COMMON = linkedMapOf("genre" to "Género", "year" to "Año", "type" to "Tipo", "status" to "Estado")

    /** Une las opciones de todos los sitios por valor canónico (el valor de la opción común ES el canónico). */
    fun commonGroups(perSite: Collection<List<FilterGroup>>): List<FilterGroup> = COMMON.mapNotNull { (id, label) ->
        val options = perSite.flatMap { groups -> groups.firstOrNull { it.id == id }?.options.orEmpty() }
            .mapNotNull { o -> canonicalOf(o).takeIf { it.isNotEmpty() }?.let { FilterOption(it, o.label, it) } }
            .distinctBy { it.value }
            .let { list -> if (id == "year") list.sortedByDescending { it.value } else list.sortedBy { it.label.lowercase() } }
        options.takeIf { it.isNotEmpty() }?.let { FilterGroup(id, label, multiSelect = false, options = it) }
    }

    /** Traduce una selección común (valores canónicos) a los valores reales de un sitio. */
    fun toSite(common: FilterSelection, siteGroups: List<FilterGroup>): SiteFilters {
        val unsupported = mutableListOf<String>()
        val values = mutableMapOf<String, Set<String>>()
        common.values.forEach { (groupId, canonicalValues) ->
            val group = siteGroups.firstOrNull { it.id == groupId }
            val mapped = canonicalValues.mapNotNull { c -> group?.options?.firstOrNull { canonicalOf(it) == c }?.value }.toSet()
            if (mapped.size < canonicalValues.size) unsupported += COMMON[groupId] ?: groupId
            if (mapped.isNotEmpty()) values[groupId] = mapped
        }
        return SiteFilters(FilterSelection(values), unsupported)
    }

    /**
     * Accesos rápidos que el sitio (o los sitios) permiten con sus filtros:
     * "Emisión de esta temporada", "Estrenos del año" y "Mejor valorados".
     */
    fun quickFilters(groups: List<FilterGroup>, currentYear: Int): List<QuickFilter> {
        fun preset(groupId: String, canonical: String, label: String): QuickFilter? {
            val option = groups.firstOrNull { it.id == groupId }?.options?.firstOrNull { canonicalOf(it) == canonical } ?: return null
            return QuickFilter(label, FilterSelection(mapOf(groupId to setOf(option.value))))
        }
        return listOfNotNull(
            preset("status", Canonical.STATUS_AIRING, "Emisión de esta temporada"),
            preset("year", currentYear.toString(), "Estrenos del año"),
            preset("order", "rating", "Mejor valorados"),
        )
    }

    fun canonicalOf(option: FilterOption): String = option.canonical ?: Canonical.of(option.label)

    /** Texto corto de los filtros activos, siempre en el mismo orden ("Acción · 2024"). */
    fun summary(selection: FilterSelection, groups: List<FilterGroup>): String =
        selection.values.entries.sortedBy { SUMMARY_ORDER.indexOf(it.key).let { i -> if (i < 0) 99 else i } }.flatMap { (groupId, values) ->
            val group = groups.firstOrNull { it.id == groupId }
            values.map { v -> group?.options?.firstOrNull { it.value == v }?.label ?: v }
        }.joinToString(" · ")
}
