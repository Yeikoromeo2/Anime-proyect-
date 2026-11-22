package com.animehub.tv.domain.model

/** Qué apartado del catálogo de la lista: películas o series. */
enum class CatalogKind(val key: String, val title: String) {
    MOVIES("movies", "Películas"),
    SERIES("series", "Series");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: MOVIES
    }
}

data class CatalogCategory(val id: String, val name: String)

/** Una película o serie de la lista (solo datos para mostrar; la reproducción se conecta aparte). */
data class CatalogItem(
    val id: String,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val year: String?,
    /** Valoración de 0 a 5 (0 = sin dato). */
    val rating: Float,
    val plot: String?,
    val categoryIds: List<String>,
)

/** Todo un apartado: sus categorías (ya sin las vacías) y sus títulos. */
data class Catalog(val kind: CatalogKind, val categories: List<CatalogCategory>, val items: List<CatalogItem>) {
    /** Títulos de una categoría, en el orden de la lista. */
    fun itemsOf(category: CatalogCategory): List<CatalogItem> = items.filter { category.id in it.categoryIds }

    /** Categorías que tienen al menos un título, con su cantidad. */
    val shelves: List<Pair<CatalogCategory, List<CatalogItem>>> by lazy(LazyThreadSafetyMode.NONE) {
        categories.map { it to itemsOf(it) }.filter { it.second.isNotEmpty() }
    }

    fun find(id: String): CatalogItem? = items.firstOrNull { it.id == id }
}
