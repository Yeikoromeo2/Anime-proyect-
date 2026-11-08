package com.animehub.tv.domain.model

/** Grupo de filtros que ofrece un sitio (leído de su HTML o de la configuración remota). */
data class FilterGroup(
    val id: String,
    val label: String,
    val multiSelect: Boolean,
    val options: List<FilterOption>,
)

data class FilterOption(val value: String, val label: String)

/** Selección actual del usuario: id del grupo → valores elegidos. */
data class FilterSelection(val values: Map<String, Set<String>> = emptyMap()) {

    /** Cantidad de filtros activos (para el contador del panel). */
    val activeCount: Int get() = values.values.sumOf { it.size }

    val isEmpty: Boolean get() = activeCount == 0

    fun valuesOf(groupId: String): Set<String> = values[groupId].orEmpty()

    /** Activa/desactiva un valor; en grupos de selección simple reemplaza el valor anterior. */
    fun toggle(group: FilterGroup, value: String): FilterSelection {
        val current = valuesOf(group.id)
        val updated = when {
            value in current -> current - value
            group.multiSelect -> current + value
            else -> setOf(value)
        }
        val map = values.toMutableMap()
        if (updated.isEmpty()) map.remove(group.id) else map[group.id] = updated
        return FilterSelection(map)
    }

    fun clear(): FilterSelection = FilterSelection()

    companion object {
        val NONE = FilterSelection()
    }
}
