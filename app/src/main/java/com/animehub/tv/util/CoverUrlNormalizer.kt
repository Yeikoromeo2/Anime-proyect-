package com.animehub.tv.util

import java.net.URI

/** Regla de reescritura de URL de portada (p. ej. pedir a TMDB un tamaño mayor). */
data class UrlRewrite(val pattern: String, val replacement: String)

/**
 * Normaliza la URL de una portada:
 * - prueba los atributos de carga diferida (`data-src`, `data-lazy-src`, `srcset`…) antes que `src`;
 * - descarta imágenes de relleno (GIF `data:`, placeholders del sitio);
 * - convierte rutas relativas o `//host/…` en URL absolutas;
 * - aplica reescrituras configuradas.
 */
object CoverUrlNormalizer {

    /** Orden en que se buscan los atributos de la imagen. */
    val ATTRIBUTE_ORDER = listOf(
        "data-src", "data-lazy-src", "data-original", "data-srcset", "srcset", "src",
    )

    fun fromAttributes(
        attribute: (String) -> String?,
        baseUrl: String,
        placeholders: List<String> = emptyList(),
        rewrites: List<UrlRewrite> = emptyList(),
    ): String? {
        for (name in ATTRIBUTE_ORDER) {
            val raw = attribute(name)?.trim().orEmpty()
            if (raw.isEmpty()) continue
            val candidate = if (name.endsWith("srcset")) pickFromSrcset(raw) ?: continue else raw
            val absolute = resolve(candidate, baseUrl) ?: continue
            if (isPlaceholder(absolute, placeholders)) continue
            return applyRewrites(absolute, rewrites)
        }
        return null
    }

    /** Convierte a URL absoluta; devuelve null si no se puede. */
    fun resolve(raw: String, baseUrl: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.startsWith("data:") || value.startsWith("javascript:")) return null
        return try {
            when {
                value.startsWith("http://") || value.startsWith("https://") -> URI(encodeSpaces(value)).toString()
                value.startsWith("//") -> URI("https:" + encodeSpaces(value)).toString()
                else -> URI(encodeSpaces(baseUrl)).resolve(encodeSpaces(value)).toString()
            }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: java.net.URISyntaxException) {
            null
        }
    }

    /** Elige la imagen más grande de un `srcset` ("a.jpg 300w, b.jpg 600w" o "a.jpg 1x, b.jpg 2x"). */
    fun pickFromSrcset(srcset: String): String? =
        srcset.split(',')
            .mapNotNull { entry ->
                val parts = entry.trim().split(Regex("\\s+"))
                val url = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val descriptor = parts.getOrNull(1).orEmpty()
                val weight = descriptor.dropLast(1).toFloatOrNull() ?: 0f
                url to weight
            }
            .maxByOrNull { it.second }
            ?.first

    fun isPlaceholder(url: String, placeholders: List<String>): Boolean =
        url.startsWith("data:") || placeholders.any { it.isNotEmpty() && url.contains(it) }

    fun applyRewrites(url: String, rewrites: List<UrlRewrite>): String =
        rewrites.fold(url) { acc, rule -> acc.replace(Regex(rule.pattern), rule.replacement) }

    private fun encodeSpaces(value: String) = value.replace(" ", "%20")
}
