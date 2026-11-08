package com.animehub.tv.data.net

import com.animehub.tv.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudflareDetectorTest {

    private fun detect(code: Int, fixture: String?, headers: Map<String, String> = mapOf("server" to "cloudflare")) =
        CloudflareDetector.detect(code, { headers[it] }, fixture?.let(Fixtures::read))

    @Test
    fun `JKAnime muestra desafio interactivo`() {
        assertEquals(ProtectionState.DESAFIO_INTERACTIVO, detect(403, "cloudflare/jkanime_inicio_interactive.html"))
    }

    @Test
    fun `la busqueda de Latanime pide desafio interactivo`() {
        assertEquals(ProtectionState.DESAFIO_INTERACTIVO, detect(403, "cloudflare/latanime_buscar_interactive.html"))
    }

    @Test
    fun `Anime Latino HD muestra desafio managed`() {
        assertEquals(ProtectionState.DESAFIO_MANAGED, detect(403, "cloudflare/animelatinohd_inicio_managed.html"))
    }

    @Test
    fun `la cabecera cf-mitigated basta para detectar el desafio`() {
        val state = CloudflareDetector.detect(403, mapOf("cf-mitigated" to "challenge")::get, "")
        assertEquals(ProtectionState.DESAFIO_MANAGED, state)
    }

    @Test
    fun `el 522 de AnimeFLV es origen caido`() {
        assertEquals(ProtectionState.ORIGEN_CAIDO, detect(522, null))
    }

    @Test
    fun `paginas normales no se marcan`() {
        assertEquals(ProtectionState.NINGUNA, detect(200, "latanime/inicio.html"))
        assertEquals(ProtectionState.NINGUNA, detect(200, "animeninja/inicio.html"))
    }
}
