package com.animehub.tv.domain.model

/** Una señal de un canal: la misma emisora puede venir en varias calidades (HD, Full HD…). */
data class ChannelVariant(
    val url: String,
    /** Líneas de resolución (480, 720, 1080, 2160); 0 si la lista no lo dice. */
    val lines: Int,
    val referer: String? = null,
    val userAgent: String? = null,
) {
    /** "Full HD", "HD"… tal como se muestra. */
    val label: String
        get() = when {
            lines >= 2160 -> "4K"
            lines >= 1080 -> "Full HD"
            lines >= 720 -> "HD"
            lines > 0 -> "SD"
            else -> "Auto"
        }
}

/** Canal (o, en las listas de dibujos y películas, un título) con todas sus señales distintas. */
data class Channel(
    /** Clave estable (nombre normalizado): sirve para favoritos y para unir repetidos. */
    val key: String,
    val name: String,
    val logo: String?,
    /** Categoría ya traducida ("Noticias", "Deportes", "Chile"…). */
    val group: String,
    val variants: List<ChannelVariant>,
    /** Lista de películas o capítulos (no es una emisión en vivo). */
    val onDemand: Boolean = false,
) {
    /** Mejor señal primero. */
    val best: ChannelVariant get() = variants.first()
    val qualities: List<String> get() = variants.map { it.label }.distinct()
}

/** Una lista M3U ya leída, con sus canales únicos. */
data class IptvList(val id: String, val name: String, val channels: List<Channel>, val onDemand: Boolean = false) {
    /** Categorías con sus canales, en el orden en que aparecen. */
    val groups: List<Pair<String, List<Channel>>> by lazy(LazyThreadSafetyMode.NONE) {
        channels.groupBy { it.group }.toList().sortedBy { (g, _) -> if (g == Iptv.OTHERS) "~" else g }
    }
}

object Iptv {
    const val OTHERS = "Otros"
}
