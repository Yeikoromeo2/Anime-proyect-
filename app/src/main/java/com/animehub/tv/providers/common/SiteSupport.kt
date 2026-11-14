package com.animehub.tv.providers.common

import com.animehub.tv.data.config.ConfigException
import com.animehub.tv.data.config.EffectiveSite
import com.animehub.tv.util.CoverUrlNormalizer
import com.animehub.tv.util.UrlRewrite
import org.jsoup.nodes.Element
import java.net.URI

/** Ayudas comunes para leer los selectores de la configuración y normalizar datos. */
class SiteSupport(val site: EffectiveSite) {

    val baseUrl: String get() = site.baseUrl
    private val config get() = site.config
    private val rewrites = config.coverRewrites.map { UrlRewrite(it.pattern, it.replacement) }

    fun selector(map: Map<String, String>, key: String, section: String): String =
        map[key]?.takeIf { it.isNotBlank() }
            ?: throw ConfigException("Falta el selector '$section.$key' en la configuración de ${config.name}")

    fun latest(key: String) = selector(config.latest, key, "latest")
    fun list(key: String) = selector(config.selectors, key, "selectors")
    fun detail(key: String) = selector(config.detail, key, "detail")
    fun server(key: String) = selector(config.servers, key, "servers")

    fun optional(map: Map<String, String>, key: String): String? = map[key]?.takeIf { it.isNotBlank() }

    /** URL de la portada de un `<img>` (carga diferida, placeholders y reescrituras). */
    fun cover(img: Element?): String? = img?.let {
        CoverUrlNormalizer.fromAttributes(it::attr, baseUrl, config.coverPlaceholders, rewrites)
    }

    /** Convierte un href (relativo o absoluto) en URL absoluta del sitio actual. */
    fun absolute(href: String): String = CoverUrlNormalizer.resolve(href, "$baseUrl/") ?: href

    /** Construye una URL con el dominio actual (respeta el dominio editado en Ajustes). */
    fun url(path: String): String = baseUrl + (if (path.startsWith("/")) path else "/$path")

    /**
     * Cambia el dominio de una URL guardada por el dominio actual del sitio
     * (útil cuando el sitio se muda y había favoritos con el dominio viejo).
     */
    fun rebase(url: String): String = try {
        val uri = URI(url)
        val path = (uri.rawPath ?: "") + (uri.rawQuery?.let { "?$it" } ?: "")
        if (uri.host == null) absolute(url) else baseUrl + path
    } catch (e: Exception) {
        url
    }

    companion object {
        private val YEAR = Regex("""\b(19|20)\d{2}\b""")
        fun year(text: String?): Int? = text?.let { YEAR.find(it)?.value?.toIntOrNull() }
        fun clean(text: String?): String = text.orEmpty().replace(Regex("\\s+"), " ").trim()
    }
}
