package com.animehub.tv.domain

import com.animehub.tv.domain.model.Canonical
import com.animehub.tv.domain.model.FilterGroup
import com.animehub.tv.domain.model.FilterOption
import com.animehub.tv.domain.model.FilterSelection
import com.animehub.tv.domain.model.FilterTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTranslatorTest {

    // Valores tal como los leen los proveedores de los HTML reales.
    private val ninja = listOf(
        FilterGroup("type", "Tipo", false, listOf(FilterOption("/anime/", "Anime (TV)", "tv"), FilterOption("/pelicula/", "Película", "pelicula"))),
        FilterGroup("genre", "Género", false, listOf(FilterOption("accion", "Accion", "accion"), FilterOption("ciencia-ficcion", "Ciencia Ficción", "cienciaficcion"))),
    )
    private val latanime = listOf(
        FilterGroup("year", "Año", false, listOf(FilterOption("2026", "2026"), FilterOption("2024", "2024"))),
        FilterGroup("genre", "Género", false, listOf(FilterOption("accion", "Acción"), FilterOption("ciencia-ficcion", "Ciencia Ficción"))),
        FilterGroup("type", "Categoría", false, listOf(FilterOption("Película", "Película", "pelicula"), FilterOption("anime", "Anime", "tv"))),
        FilterGroup("letter", "Letra", false, listOf(FilterOption("A", "A"))),
        FilterGroup("status", "Estado", false, listOf(FilterOption("/emision", "En emisión", "emision"))),
    )

    @Test
    fun `los filtros comunes unen generos con y sin tilde y omiten los propios de un sitio`() {
        val common = FilterTranslator.commonGroups(listOf(ninja, latanime)).associateBy { it.id }
        assertEquals(listOf("genre", "year", "type", "status"), common.keys.toList())
        assertEquals(listOf("accion", "cienciaficcion"), common.getValue("genre").options.map { it.value })
        assertEquals(listOf("pelicula", "tv"), common.getValue("type").options.map { it.value }.sorted())
        assertEquals(listOf("2026", "2024"), common.getValue("year").options.map { it.value })
    }

    @Test
    fun `accion y pelicula se traducen al parametro de cada sitio`() {
        val selection = FilterSelection(mapOf("genre" to setOf("accion"), "type" to setOf(Canonical.TYPE_MOVIE)))
        val toNinja = FilterTranslator.toSite(selection, ninja)
        assertEquals(setOf("accion"), toNinja.selection.valuesOf("genre"))
        assertEquals(setOf("/pelicula/"), toNinja.selection.valuesOf("type"))
        assertTrue(toNinja.unsupported.isEmpty())
        val toLat = FilterTranslator.toSite(selection, latanime)
        assertEquals(setOf("Película"), toLat.selection.valuesOf("type"))
    }

    @Test
    fun `un sitio sin filtro de anio lo informa`() {
        val selection = FilterSelection(mapOf("year" to setOf("2024")))
        assertEquals(listOf("Año"), FilterTranslator.toSite(selection, ninja).unsupported)
        assertTrue(FilterTranslator.toSite(selection, latanime).unsupported.isEmpty())
    }

    @Test
    fun `accesos rapidos solo si el sitio los permite`() {
        val lat = FilterTranslator.quickFilters(latanime, currentYear = 2026).associateBy { it.label }
        assertEquals(setOf("/emision"), lat.getValue("Emisión de esta temporada").selection.valuesOf("status"))
        assertEquals(setOf("2026"), lat.getValue("Estrenos del año").selection.valuesOf("year"))
        assertTrue("Mejor valorados" !in lat)
        assertTrue(FilterTranslator.quickFilters(ninja, 2026).isEmpty())
    }

    @Test
    fun `contador y resumen de filtros activos`() {
        val selection = FilterSelection().toggle(latanime[1], "accion").toggle(latanime[0], "2024")
        assertEquals(2, selection.activeCount)
        assertEquals("Acción · 2024", FilterTranslator.summary(selection, latanime))
        // En grupos de selección simple, elegir otro valor reemplaza al anterior.
        assertEquals(setOf("2026"), selection.toggle(latanime[0], "2026").valuesOf("year"))
    }
}
