package com.animehub.tv.data.config

import kotlinx.serialization.Serializable

/**
 * Configuración remota: dominios, rutas, selectores y filtros de cada sitio.
 * Se descarga de GitHub al iniciar; si no hay conexión se usa la caché o la copia incluida.
 * Todos los campos opcionales permiten que el JSON evolucione sin romper versiones viejas de la app.
 */
@Serializable
data class RemoteConfig(
    val version: Int,
    val updated: String? = null,
    /** User-Agent opcional para todas las peticiones (si no, se usa el de la app). */
    val userAgent: String? = null,
    val sites: List<SiteConfig> = emptyList(),
    /** Páginas "multiservidor" que agrupan reproductores (se abren en sus servidores reales). */
    val aggregators: List<AggregatorConfig> = emptyList(),
    /** Textos que indican que un video fue borrado (para marcar el servidor como no disponible). */
    val deadVideoMarkers: List<String> = emptyList(),
    /**
     * Servidores de los que se puede sacar el video directo para el reproductor nativo
     * (tipo de extractor → dominios). Si un servidor cambia de dominio se corrige aquí.
     */
    val directStreams: List<AggregatorConfig> = emptyList(),
    /** Dominios de anuncios y de rastreo que el reproductor web bloquea (también sus subdominios). */
    val adBlockHosts: List<String> = emptyList(),
)

/**
 * @param type "msp-xor" (botones con URL en Base64 cifrada con la clave "pk" de la página) o
 *  "embeds-object" (objeto JS `embeds:{SUB:[{server,url}],DUB:[…]}`).
 */
@Serializable
data class AggregatorConfig(val type: String, val hosts: List<String>)

@Serializable
data class SiteConfig(
    val id: String,
    val name: String,
    /** Motor/proveedor que interpreta este sitio ("dooplay", "latanime"…); varios sitios pueden compartirlo. */
    val engine: String? = null,
    val baseUrl: String,
    /** Dominios anteriores o alternativos que redirigen al principal. */
    val aliases: List<String> = emptyList(),
    /** Activado por defecto (el usuario puede cambiarlo en Ajustes). */
    val enabled: Boolean = true,
    /** "ok" si hay proveedor; "pendiente" si aún no se analizó; "cerrado" si el sitio dejó de existir. */
    val status: String = STATUS_OK,
    val notes: String? = null,
    val search: SearchConfig? = null,
    val browse: BrowseConfig? = null,
    val latest: Map<String, String> = emptyMap(),
    val selectors: Map<String, String> = emptyMap(),
    val detail: Map<String, String> = emptyMap(),
    val servers: Map<String, String> = emptyMap(),
    val filters: List<FilterConfig> = emptyList(),
    val coverPlaceholders: List<String> = emptyList(),
    val coverRewrites: List<RewriteConfig> = emptyList(),
    /** Secciones de la portada del sitio que se muestran en Inicio (recomendados, series nuevas). */
    val showcase: List<ShowcaseConfig> = emptyList(),
    /** Logo del sitio (se muestra redondo en Inicio). */
    val logo: String? = null,
    /** Para logos alargados: qué parte se ve en el círculo (-1 izquierda, 0 centro, 1 derecha). */
    val logoAlign: Float = 0f,
    /** Color del sitio ("#RRGGBB"): fondo del círculo si el logo no carga (se ven las iniciales). */
    val color: String? = null,
    /**
     * Cuando el mismo anime está en varios sitios se usa la versión del sitio con más prioridad
     * (más episodios, miniaturas y video directo). Más alto = antes.
     */
    val priority: Int = 0,
) {
    val isPending: Boolean get() = status != STATUS_OK
    val isClosed: Boolean get() = status == STATUS_CLOSED

    companion object {
        const val STATUS_OK = "ok"
        const val STATUS_PENDING = "pendiente"
        const val STATUS_CLOSED = "cerrado"
    }
}

@Serializable
data class SearchConfig(
    val path: String,
    val queryParam: String,
    val pageParam: String? = null,
    /** Plantilla de ruta para páginas ≥ 2, p. ej. "/page/{page}/". */
    val pagePath: String? = null,
    /** La búsqueda está tras un desafío de Cloudflare (necesita verificación del usuario). */
    val requiresChallenge: Boolean = false,
)

@Serializable
data class BrowseConfig(
    val path: String,
    val pageParam: String? = null,
    /** Valor que el sitio usa para "sin filtro" (Latanime usa "false"). */
    val emptyValue: String? = null,
)

@Serializable
data class FilterConfig(
    val id: String,
    val label: String,
    val param: String? = null,
    val multi: Boolean = false,
    /** "html" (se lee del sitio), "path" (cada opción es una ruta) o "static" (opciones fijas). */
    val source: String = "html",
    /** Selector del `<select>` cuando source = html. */
    val select: String? = null,
    /** Selector de enlaces cuando los valores salen de `<a href>`. */
    val links: String? = null,
    /** Página donde leer las opciones. */
    val page: String? = null,
    val pathTemplate: String? = null,
    val options: List<FilterOptionConfig> = emptyList(),
    /** Equivalencias valor del sitio → valor común (p. ej. "Película" → "pelicula") para la búsqueda multisitio. */
    val canonicalMap: Map<String, String> = emptyMap(),
)

@Serializable
data class FilterOptionConfig(val value: String, val label: String, val canonical: String? = null)

/**
 * Una sección de la portada del sitio (p. ej. "🔥 Recomendados" o "Series recientes").
 * Los selectores son relativos a cada tarjeta; los vacíos se ignoran.
 * @param kind "recommended" (lo que el sitio recomienda o destaca) o "new" (series recién agregadas).
 * @param link enlace a la ficha; vacío = la propia tarjeta si es un `<a>`, o su primer `a[href]`.
 * @param titleAttr atributo del que sale el título (p. ej. "alt" de la imagen); vacío = el texto.
 */
@Serializable
data class ShowcaseConfig(
    val kind: String,
    val path: String = "/",
    val card: String,
    val link: String = "",
    val title: String,
    val titleAttr: String = "",
    val image: String,
    val synopsis: String = "",
    val year: String = "",
    val rating: String = "",
    val date: String = "",
    val limit: Int = 20,
)

@Serializable
data class RewriteConfig(val pattern: String, val replacement: String)
