package com.animehub.tv.util

import java.text.Normalizer

/**
 * Decide si dos títulos de sitios distintos son el mismo anime, aunque estén escritos distinto:
 * mayúsculas, tildes y macrones ("Ōkami" = "Okami"), puntuación, idioma al final ("Latino",
 * "Castellano"), "Temporada 2" = "Season 2" = "S2" = "2nd Season", números romanos (III = 3).
 *
 * Una temporada distinta es otro anime: "Overlord 2" no se une con "Overlord 3".
 * No traduce: "Frieren" y "Sousou no Frieren" sí (comparten casi todo), pero un título en inglés
 * y otro en japonés romanizado sin palabras en común no se pueden unir sin un diccionario.
 */
object TitleMatcher {
    private val MARKS = Regex("\\p{M}+")
    private val BRACKETS = Regex("""[(\[][^)\]]*[)\]]""")
    private val TOKEN = Regex("[a-z0-9]+")
    private val LANGUAGE = setOf("latino", "castellano", "espanol", "subtitulado", "sub", "dub", "doblado", "japones", "amz", "hd", "online", "completo")
    private val ROMAN = mapOf("ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10")
    private val SEASON_WORDS = setOf("temporada", "season", "temp", "cour", "part", "parte")
    private val ORDINAL = Regex("""^(\d+)(st|nd|rd|th|ra|da|era|a)$""")

    /** Palabras del título ya normalizadas, sin idioma ni relleno; la temporada queda como "s2". */
    fun tokens(title: String): List<String> = parse(title).tokens

    /** Todo lo que hace falta de un título, calculado una sola vez (comparar listas largas lo repite miles de veces). */
    private class Parsed(val tokens: List<String>, val key: String, val numbers: Set<String>, val words: List<String>, val wordsJoined: String, val year: Int?)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Parsed>()

    private fun parse(title: String): Parsed {
        cache[title]?.let { return it }
        if (cache.size > 6_000) cache.clear()
        val tokens = computeTokens(title)
        val numbers = numbers(tokens)
        val words = tokens.filterNot { it in numbers }
        val parsed = Parsed(tokens, tokens.joinToString(""), numbers, words, words.joinToString(""), yearTag(title))
        cache[title] = parsed
        return parsed
    }

    private fun computeTokens(title: String): List<String> {
        val plain = Normalizer.normalize(title, Normalizer.Form.NFD).replace(MARKS, "").lowercase()
            .replace("&", " and ")
            .replace(BRACKETS, " ")
        val raw = TOKEN.findAll(plain).map { it.value }.filter { it !in LANGUAGE }.toList()
        val out = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            val t = raw[i]
            val next = raw.getOrNull(i + 1)
            when {
                // "temporada 2", "season 2", "part 2"
                t in SEASON_WORDS && next?.all(Char::isDigit) == true -> { out += "s$next"; i += 2; continue }
                // "2nd season", "2da temporada"
                ORDINAL.matches(t) && next in SEASON_WORDS -> { out += "s${ORDINAL.find(t)!!.groupValues[1]}"; i += 2; continue }
                // "s2" ya viene bien; "ii" al final es 2
                t in ROMAN && i == raw.lastIndex && out.isNotEmpty() -> out += "s${ROMAN.getValue(t)}"
                t.length > 1 && t.startsWith("s") && t.drop(1).all(Char::isDigit) -> out += t
                // "Overlord 3" al final = temporada 3 (igual que "Overlord III" y "S3")
                i == raw.lastIndex && out.isNotEmpty() && t.all(Char::isDigit) && (t.toIntOrNull() ?: 99) in 2..20 -> out += "s$t"
                else -> out += t
            }
            i++
        }
        // La temporada 1 es la de siempre: "S1" no distingue nada.
        return out.filter { it != "s1" }
    }

    /** Clave estable de un título (palabras unidas): sirve para agrupar títulos idénticos. */
    fun key(title: String): String = parse(title).key

    /** Números y temporadas del título: tienen que coincidir para que sea el mismo anime. */
    private fun numbers(tokens: List<String>): Set<String> =
        tokens.filter { t -> t.all(Char::isDigit) || (t.startsWith("s") && t.length > 1 && t.drop(1).all(Char::isDigit)) }.toSet()

    private val YEAR_TAG = Regex("""[(\[]\s*((?:19|20)\d\d)\s*[)\]]""")

    /** Año escrito entre paréntesis en el título: "Ranma ½ (2024)" → 2024. */
    fun yearTag(title: String): Int? = YEAR_TAG.find(title)?.groupValues?.get(1)?.toIntOrNull()

    fun same(a: String, b: String): Boolean = same(parse(a), parse(b))

    private fun same(pa: Parsed, pb: Parsed): Boolean {
        // Dos versiones del mismo nombre con año distinto (o solo una lo trae) son series distintas:
        // "Ranma ½ (2024)" no es el "Ranma ½" de 1989. Preferimos no unir antes que esconder una.
        if (pa.year != pb.year) return false
        if (pa.tokens.isEmpty() || pb.tokens.isEmpty()) return false
        if (pa.key == pb.key) return true
        if (pa.numbers != pb.numbers) return false
        val wa = pa.words
        val wb = pb.words
        if (wa.isEmpty() || wb.isEmpty()) return false
        // Muy parecidos letra por letra ("Shingeki" con una errata, "Boku no Hero" / "Bokuno Hero").
        val sa = pa.wordsJoined
        val sb = pb.wordsJoined
        // Con más de 12 % de diferencia de largo no pueden parecerse tanto: se evita el cálculo caro.
        val longer = maxOf(sa.length, sb.length)
        if ((longer - minOf(sa.length, sb.length)) * 100 <= longer * 12 && similarity(sa, sb) >= 0.88) return true
        // Uno es el otro con palabras de más ("Frieren" / "Sousou no Frieren Beyond Journey's End").
        val (small, big) = if (wa.size <= wb.size) wa to wb else wb to wa
        if (!small.all { it in big }) return false
        val smallLen = small.sumOf { it.length }
        return smallLen >= 6 && smallLen.toDouble() / big.sumOf { it.length } >= 0.35
    }

    /** 1.0 = iguales, 0.0 = nada en común (distancia de Levenshtein normalizada). */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val max = maxOf(a.length, b.length)
        if (max == 0) return 1.0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return 1.0 - prev[b.length].toDouble() / max
    }

    /**
     * Agrupa los elementos que son el mismo anime y devuelve uno por grupo (el primero en aparecer
     * lleva a los demás). Conserva el orden de la primera aparición.
     * @param merge recibe el principal y las otras versiones del mismo anime.
     */
    fun <T> group(
        items: List<T>,
        title: (T) -> String,
        compatible: (T, T) -> Boolean = { _, _ -> true },
        merge: (main: T, others: List<T>) -> T,
    ): List<T> {
        val groups = mutableListOf<MutableList<T>>()
        // Índice: solo se compara con los grupos que comparten alguna palabra (o el mismo comienzo),
        // en vez de con todos (con el catálogo entero eran cientos de miles de comparaciones).
        val index = HashMap<String, MutableList<Int>>()
        fun keysOf(p: Parsed) = p.words.map { "w:$it" } + "p:" + p.key.take(4)
        for (item in items) {
            val t = title(item)
            val parsed = parse(t)
            val keys = keysOf(parsed)
            var home: Int? = null
            val seen = HashSet<Int>()
            search@ for (k in keys) {
                for (gi in index[k].orEmpty()) {
                    if (!seen.add(gi)) continue
                    if (groups[gi].any { same(parse(title(it)), parsed) && compatible(it, item) }) { home = gi; break@search }
                }
            }
            if (home != null) groups[home] += item else {
                groups += mutableListOf(item)
                val gi = groups.lastIndex
                keys.forEach { index.getOrPut(it) { mutableListOf() }.add(gi) }
            }
        }
        return groups.map { g -> if (g.size == 1) g.first() else merge(g.first(), g.drop(1)) }
    }
}
