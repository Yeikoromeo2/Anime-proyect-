package com.animehub.tv.data.net

/** Resultado de analizar una respuesta HTTP en busca de protecciones de Cloudflare. */
enum class ProtectionState {
    /** Respuesta normal. */
    NINGUNA,
    /** Desafío que requiere interacción del usuario (Turnstile). Hay que mostrar un WebView visible. */
    DESAFIO_INTERACTIVO,
    /** Desafío "managed": puede resolverse solo o pedir interacción. */
    DESAFIO_MANAGED,
    /** Desafío JS no interactivo: suele resolverse en un WebView oculto. */
    DESAFIO_JS,
    /** Errores 52x de Cloudflare: el servidor del sitio no responde. */
    ORIGEN_CAIDO,
    /** Bloqueo por firewall ("Attention Required"). */
    BLOQUEADO;

    val isChallenge: Boolean get() = this == DESAFIO_INTERACTIVO || this == DESAFIO_MANAGED || this == DESAFIO_JS
}

/** Detecta desafíos y errores de Cloudflare (ver docs/fase0-resumen.md). */
object CloudflareDetector {

    private val CTYPE = Regex("""cType:\s*'([a-z-]+)'""")

    fun detect(statusCode: Int, header: (String) -> String?, body: String?): ProtectionState {
        val server = header("server")?.lowercase().orEmpty()
        val mitigated = header("cf-mitigated")?.lowercase()
        val html = body.orEmpty()

        val looksLikeChallenge = mitigated == "challenge" ||
            ((statusCode == 403 || statusCode == 503 || statusCode == 429) &&
                (html.contains("/cdn-cgi/challenge-platform/") && html.contains("<title>Just a moment...</title>")))

        if (looksLikeChallenge) {
            return when (CTYPE.find(html)?.groupValues?.get(1)) {
                "interactive" -> ProtectionState.DESAFIO_INTERACTIVO
                "non-interactive" -> ProtectionState.DESAFIO_JS
                else -> ProtectionState.DESAFIO_MANAGED
            }
        }
        if (statusCode in 520..527 && (server.contains("cloudflare") || html.isEmpty())) {
            return ProtectionState.ORIGEN_CAIDO
        }
        if (statusCode == 403 && (html.contains("Attention Required! | Cloudflare") || html.contains("cf-error-details"))) {
            return ProtectionState.BLOQUEADO
        }
        return ProtectionState.NINGUNA
    }
}

/** Error que lanzan los proveedores cuando un sitio está protegido o caído. */
class SiteProtectedException(val siteId: String, val state: ProtectionState) :
    Exception(
        when (state) {
            ProtectionState.ORIGEN_CAIDO -> "Este sitio no responde"
            ProtectionState.BLOQUEADO -> "El sitio bloqueó la conexión"
            else -> "El sitio pide una verificación"
        },
    )
