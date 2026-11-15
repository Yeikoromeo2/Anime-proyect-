package com.animehub.tv.data.iptv

import com.animehub.tv.domain.model.Canonical
import com.animehub.tv.domain.model.Channel
import com.animehub.tv.domain.model.ChannelVariant
import com.animehub.tv.domain.model.Iptv
import com.animehub.tv.domain.model.IptvList
import java.net.URLDecoder

/**
 * Lee listas M3U/M3U8 (`#EXTINF` + dirección) y las deja sin repetidos:
 * - el mismo canal aparece una sola vez; si lo hay en varias calidades (SD, HD, Full HD) se
 *   conservan todas, y si hay dos iguales en la misma calidad se usa una (mejor https que http);
 * - las categorías se traducen al español y las listas sin categoría se agrupan por país.
 */
object M3uParser {

    /** Una entrada tal como viene en la lista. */
    data class Entry(val name: String, val url: String, val logo: String?, val group: String?, val referer: String?, val userAgent: String?)

    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private val QUALITY_TAG = Regex("""[(\[]\s*(\d{3,4})\s*[pi]\s*[)\]]""", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("""\[[^\]]*]""")
    private val STAR_TAIL = Regex("""\s*✪.*$""")
    private val COUNTRY_TAIL = Regex("""\s*\|\s*([A-Za-z]{2})\s*$""")
    private val QUALITY_WORD = Regex("""\s+(?:FHD|UHD|4K|HD|SD)\s*$""", RegexOption.IGNORE_CASE)
    private val BAD_GROUPS = setOf("xxx", "adult", "adultos")

    fun parseEntries(text: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var attrs: Map<String, String>? = null
        var title = ""
        var referer: String? = null
        var userAgent: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val comma = line.lastIndexOf(',')
                    attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    referer = null
                    userAgent = null
                }
                line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) -> referer = decode(line.substringAfter('='))
                line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> userAgent = line.substringAfter('=')
                line.isEmpty() || line.startsWith("#") -> Unit
                attrs != null && (line.startsWith("http://") || line.startsWith("https://")) -> {
                    val a = attrs
                    val name = title.ifBlank { a["tvg-name"].orEmpty() }
                    if (name.isNotBlank()) out += Entry(name, stableUrl(line), a["tvg-logo"]?.takeIf { it.startsWith("http") }, a["group-title"]?.trim()?.ifEmpty { null }, referer, userAgent)
                    attrs = null
                }
            }
        }
        return out
    }

    /** Nombre sin calidad, país ni etiquetas: "Latina (720p) [Not 24/7]" → "Latina". */
    fun baseName(name: String): String = name
        .replace(STAR_TAIL, "").replace(COUNTRY_TAIL, "").replace(QUALITY_TAG, "").replace(BRACKETS, "")
        .replace(QUALITY_WORD, "").replace(Regex("""\s+"""), " ").trim().trimEnd('-', '|', ' ')

    /** Líneas de resolución que dice el nombre ("(1080p)", "FHD", "HD"…); 0 si no dice nada. */
    fun linesOf(name: String): Int {
        QUALITY_TAG.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        val upper = name.uppercase()
        return when {
            Regex("""\b(4K|UHD)\b""").containsMatchIn(upper) -> 2160
            Regex("""\bFHD\b""").containsMatchIn(upper) -> 1080
            Regex("""\bHD\b""").containsMatchIn(upper) -> 720
            Regex("""\bSD\b""").containsMatchIn(upper) -> 480
            else -> 0
        }
    }

    private fun tier(lines: Int) = when {
        lines >= 2160 -> 2160
        lines >= 1080 -> 1080
        lines >= 720 -> 720
        lines > 0 -> 480
        else -> 0
    }

    /** Direcciones de archive.org atadas a un servidor concreto ("ia802200…/26/items/x/f.mp4") se cambian por la estable ("archive.org/download/x/f.mp4"): el servidor cambia y el enlace viejo deja de servir. */
    private val ARCHIVE_NODE = Regex("""^https?://ia\d+\.[a-z]+\.archive\.org/\d+/items/([^/]+)/(.+)$""")
    fun stableUrl(url: String): String = ARCHIVE_NODE.find(url)?.let { "https://archive.org/download/${it.groupValues[1]}/${it.groupValues[2]}" } ?: url

    /**
     * Arma una lista sin repetidos.
     * - "live": un canal una sola vez (aunque venga en varias categorías o calidades; las calidades distintas se conservan).
     * - "grouped": el submenú es el group-title (país, categoría o idioma); un canal puede estar en varios, pero una sola vez en cada uno.
     * - "vod": cada título (película o capítulo) tal cual.
     * Si dos son iguales en la misma calidad se usa una (mejor https que http).
     */
    fun buildList(id: String, name: String, entries: List<Entry>, kind: String = "live", order: List<String> = emptyList(), translate: String? = null): IptvList {
        val onDemand = kind == "vod"
        val grouped = kind == "grouped"
        val channels = LinkedHashMap<String, MutableChannel>()
        for (e in entries) {
            val group = groupOf(e, onDemand, translate) ?: continue
            val display = if (onDemand) e.name.trim() else baseName(e.name).ifEmpty { e.name.trim() }
            val key = if (onDemand) "$id:" + Canonical.of(display) else Canonical.of(display)
            if (key.isEmpty() || key == "$id:") continue
            val variant = ChannelVariant(e.url, linesOf(e.name), e.referer, e.userAgent)
            val slot = if (grouped) "$group|$key" else key
            val existing = channels[slot]
            if (existing != null) existing.add(variant) else channels[slot] = MutableChannel(key, display, e.logo, group, onDemand).also { it.add(variant) }
        }
        return IptvList(id, name, channels.values.map { it.freeze() }, onDemand, order)
    }

    /** Canal en construcción: una señal por calidad, la mejor primero. */
    private class MutableChannel(val key: String, val name: String, val logo: String?, val group: String, val onDemand: Boolean) {
        private val variants = mutableListOf<ChannelVariant>()
        fun add(v: ChannelVariant) {
            val same = variants.indexOfFirst { tier(it.lines) == tier(v.lines) }
            when {
                same < 0 -> variants += v
                // Dos iguales en la misma calidad: se queda una (mejor cifrada que sin cifrar).
                variants[same].url.startsWith("http://") && v.url.startsWith("https://") -> variants[same] = v
            }
        }
        fun freeze() = Channel(key, name, logo, group, variants.sortedByDescending { it.lines }, onDemand)
    }

    private fun groupOf(e: Entry, onDemand: Boolean, translate: String? = null): String? {
        val raw = e.group
        if (raw != null) {
            if (raw.lowercase() in BAD_GROUPS) return null
            return when (translate) {
                "country" -> COUNTRY_NAMES[raw] ?: raw
                "language" -> LANGUAGE_NAMES[raw] ?: raw
                else -> GROUPS[raw.lowercase()] ?: raw
            }
        }
        if (onDemand) return "Películas"
        // Sin categoría: el país que trae el nombre ("Rewind ✪ | CL") o "Otros".
        val code = COUNTRY_TAIL.find(e.name.replace(STAR_TAIL, ""))?.groupValues?.get(1)?.uppercase()
            ?: Regex("""\|\s*([A-Za-z]{2})\s*$""").find(e.name)?.groupValues?.get(1)?.uppercase()
        return code?.let { COUNTRIES[it] } ?: Iptv.OTHERS
    }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private val GROUPS = mapOf(
        "general" to "General", "news" to "Noticias", "sports" to "Deportes", "music" to "Música", "entertainment" to "Entretenimiento",
        "religious" to "Religión", "kids" to "Infantil", "undefined" to Iptv.OTHERS, "movies" to "Películas", "documentary" to "Documentales",
        "culture" to "Cultura", "education" to "Educación", "comedy" to "Comedia", "animation" to "Animación", "series" to "Series",
        "lifestyle" to "Estilo de vida", "business" to "Negocios", "weather" to "Clima", "legislative" to "Gobierno", "science" to "Ciencia",
        "outdoor" to "Aire libre", "cooking" to "Cocina", "travel" to "Viajes", "family" to "Familia", "interactive" to "Interactivo",
        "classic" to "Clásicos", "relax" to "Relax", "shop" to "Compras", "auto" to "Autos", "public" to "Públicos",
    )

    private val COUNTRIES = mapOf(
        "AR" to "Argentina", "BO" to "Bolivia", "BR" to "Brasil", "CL" to "Chile", "CO" to "Colombia", "CR" to "Costa Rica", "CU" to "Cuba",
        "DO" to "Rep. Dominicana", "EC" to "Ecuador", "SV" to "El Salvador", "ES" to "España", "GT" to "Guatemala", "HN" to "Honduras",
        "MX" to "México", "NI" to "Nicaragua", "PA" to "Panamá", "PE" to "Perú", "PY" to "Paraguay", "PR" to "Puerto Rico", "UY" to "Uruguay",
        "VE" to "Venezuela", "US" to "Estados Unidos", "IL" to "Israel", "PS" to "Palestina", "FR" to "Francia", "IT" to "Italia",
        "DE" to "Alemania", "GB" to "Reino Unido", "UK" to "Reino Unido", "PT" to "Portugal", "CA" to "Canadá", "JP" to "Japón",
    )

    private val COUNTRY_NAMES = mapOf(
        "Peru" to "Perú", "Mexico" to "México", "Argentina" to "Argentina", "Chile" to "Chile", "Colombia" to "Colombia", "Ecuador" to "Ecuador",
        "Bolivia" to "Bolivia", "Venezuela" to "Venezuela", "Uruguay" to "Uruguay", "Paraguay" to "Paraguay", "Spain" to "España",
        "United States" to "Estados Unidos", "Costa Rica" to "Costa Rica", "Cuba" to "Cuba", "Dominican Republic" to "Rep. Dominicana",
        "El Salvador" to "El Salvador", "Guatemala" to "Guatemala", "Honduras" to "Honduras", "Nicaragua" to "Nicaragua", "Panama" to "Panamá",
        "Puerto Rico" to "Puerto Rico", "Brazil" to "Brasil", "Canada" to "Canadá", "France" to "Francia", "Germany" to "Alemania",
        "Italy" to "Italia", "Portugal" to "Portugal", "United Kingdom" to "Reino Unido", "Russia" to "Rusia", "India" to "India",
        "Japan" to "Japón", "China" to "China", "South Korea" to "Corea del Sur", "Turkey" to "Turquía", "Israel" to "Israel",
        "International" to "Internacional", "Netherlands" to "Países Bajos", "Belgium" to "Bélgica", "Switzerland" to "Suiza",
        "Poland" to "Polonia", "Ukraine" to "Ucrania", "Greece" to "Grecia", "Egypt" to "Egipto", "Morocco" to "Marruecos",
        "Philippines" to "Filipinas", "Indonesia" to "Indonesia", "Thailand" to "Tailandia", "Vietnam" to "Vietnam", "Australia" to "Australia",
    )

    private val LANGUAGE_NAMES = mapOf(
        "Spanish" to "Español", "English" to "Inglés", "Portuguese" to "Portugués", "French" to "Francés", "Italian" to "Italiano",
        "German" to "Alemán", "Russian" to "Ruso", "Arabic" to "Árabe", "Hindi" to "Hindi", "Chinese" to "Chino", "Japanese" to "Japonés",
        "Korean" to "Coreano", "Turkish" to "Turco", "Dutch" to "Neerlandés", "Polish" to "Polaco", "Catalan" to "Catalán", "Quechua" to "Quechua",
        "Guarani" to "Guaraní", "Aymara" to "Aimara", "Greek" to "Griego", "Hebrew" to "Hebreo", "Ukrainian" to "Ucraniano",
    )
}
