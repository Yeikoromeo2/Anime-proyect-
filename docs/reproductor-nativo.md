# Reproductor nativo (Fase 5)

Cuando un servidor entrega el video directo (lista HLS `.m3u8` o archivo `.mp4`), la app lo reproduce con **Media3/ExoPlayer**, el reproductor nativo de Android, en vez de abrir la página del servidor:
- no aparecen anuncios, ventanas ni recuadros del servidor;
- arranca más rápido y responde al control como una app de TV;
- respeta la calidad máxima de Ajustes y retoma el minuto exacto.

## Qué servidores (2026-11-09)

| Servidor | Sitios | Cómo se saca el video | Comprobado |
|---|---|---|---|
| **HLS (zilla)** | Anime Ninja, AnimeFLV | `player.zilla-networks.com/play/{id}` → `/m3u8/{id}`; se comprueba que responda `#EXTM3U` | La lista responde. Los segmentos los bloquea Cloudflare para la IP del servidor de pruebas; en una conexión normal cargan como en el navegador |
| **Desu / Magi** | JKAnime | En la página del reproductor propio: `atob('…')` (Desu) o el enlace en texto (Magi); se comprueba que la lista responda | Sí. En algunos episodios el enlace está roto en el propio sitio: entonces queda el reproductor web |
| **MP4Upload** | Anime Ninja, AnimeFLV | `src: "https://….mp4"` en la página del reproductor, con `Referer` de mp4upload | Se extrae; el servidor del archivo no está permitido en la red de pruebas |

Los demás (Voe, Streamwish, Vidhide, Filemoon, Streamtape, Doodstream, Mega…) siguen con el reproductor web. Qué extractor usa cada dominio está en `directStreams` de [`config/sites.json`](../config/sites.json): si un servidor cambia de dominio se corrige sin reinstalar.

## Si algo falla

1. Si el reproductor nativo da error o no arranca en 25 s, **el mismo servidor** se abre en el reproductor web; si ya se estaba viendo, desde el mismo minuto.
2. Si la página tampoco arranca, se prueba el siguiente servidor que funcione, como antes.

## Código

- `extractors/DirectStreamExtractor.kt`: saca y comprueba el enlace directo.
- `playback/ServerRepository.kt`: comprueba los servidores y saca el video directo a la vez, en segundo plano. Ordena primero el preferido y luego los nativos.
- `playback/PlaybackController.kt`: orden `PlayNative` y paso al reproductor web si el nativo falla.
- `nativeplayer/ExoNativePlayback.kt`: ExoPlayer con OkHttp, las cabeceras del servidor (Referer, Origin), la calidad máxima de Ajustes y los mismos mensajes que el script web (`playing`, `progress`, `ended`, `error`).
- Pruebas: `DirectStreamExtractorTest` (páginas guardadas en `fixtures/servidores/`), `PlaybackControllerTest`, `NavegacionReproductorTest` (con un reproductor nativo simulado) y `SaludSitiosTest.videoDirectoResponde` (en vivo, con `-Psalud`).
