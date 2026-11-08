# Fase 0 — Resumen del análisis de sitios

Fecha: 2026-11-08. Detalle de cada sitio en [`docs/sites/`](sites/).

## Estado por sitio

| Sitio | Dominio actual | Analizado | Motivo si no |
|---|---|---|---|
| AnimeFLV | `www3.animeflv.net` → posiblemente `www4.animeflv.net` | ⛔ No | Error 522: el servidor de origen no responde a Cloudflare |
| JKAnime | `jkanime.net` | ⛔ No | Desafío **interactivo** de Cloudflare en todas las páginas |
| Anime Ninja | `veranime.ninja` (también vía `animelhd.com`) | ✅ Completo | — |
| AnimeLHD | ¿`animelhd.com` (= Anime Ninja) o `www.animelatinohd.com`? | ❓ | Dominio por confirmar; animelatinohd tiene desafío *managed* |
| Latanime | `latanime.org` | ✅ Casi completo | Solo la búsqueda por texto tiene desafío interactivo |

## Tabla comparativa: sitio × filtros × Cloudflare × servidores

| | Latanime | Anime Ninja | AnimeFLV | JKAnime | AnimeLHD |
|---|---|---|---|---|---|
| **Búsqueda por texto** | `/buscar?q=` (desafío) | `/?s=` + `/page/N/` | ? | ? | ? |
| **Género** | ✅ `genero` (48) | ✅ `/genero/<slug>/` (61) | ? | ? | ? |
| **Año** | ✅ `fecha` (1982–2026) | ❌ | ? | ? | ? |
| **Tipo** | ✅ `categoria` (anime, ova, película, especial, corto, ona, donghua…) | ✅ `/anime/` (TV) · `/pelicula/` | ? | ? | ? |
| **Estado** | ⚠️ solo página `/emision` | ❌ (aprox. "Última transmisión") | ? | ? | ? |
| **Temporada** | ❌ | ❌ | ? | ? | ? |
| **Orden** | ❌ | ❌ | ? | ? | ? |
| **Idioma** | ⚠️ mezclado en `categoria`/`genero` (latino, castellano, catalán) | ⚠️ etiqueta en tarjetas; SUB/DUB en el agregador animeav1 | ? | ? | ? |
| **Letra** | ✅ `letra` (0-9, A–Z) | ❌ | ? | ? | ? |
| **¿Filtros combinables?** | Sí (4 a la vez, 1 valor cada uno) | No (un archivo a la vez) | ? | ? | ? |
| **Cloudflare** | Sí · solo `/buscar` y `/buscar_ajax` con desafío | Sí · sin desafío | Sí · origen caído (522) | Sí · desafío interactivo en todo | animelatinohd: desafío *managed* |
| **Episodios en** | HTML | HTML (todas las temporadas) | ? | ? | ? |
| **Servidores en** | HTML, URL en **Base64** (`data-player`) | AJAX `admin-ajax.php` → iframe de agregador (darkanimes.com: **Base64+XOR**; animeav1.com: objeto JS) | ? | ? | ? |
| **Servidores encontrados** | dsvplay (playmogo.com), hexload, savefiles, mega, mixdrop, voe, mp4upload, byse (bysewihe.com) | Mp4Upload, Mega, Zilla-networks (HLS), Uns, Terabox, Voe, Byse (byselapuix.com) | ? | ? | ? |
| **Portadas** | `latanime.org/thumbs/imagen/…` (lazy `data-src`) | `image.tmdb.org/t/p/wNNN/…` (lazy `data-src`; tamaño reescribible) | ? | ? | ? |

## Servidores de video vistos (candidatos para extractores, Fase 5)

| Servidor | Dominio(s) | Sitios | Nota |
|---|---|---|---|
| Voe | `voe.sx` | Latanime, Anime Ninja | |
| MP4Upload | `www.mp4upload.com` | Latanime, Anime Ninja | Probable MP4 directo |
| Zilla-networks (HLS) | `player.zilla-networks.com` (`/m3u8/<id>`, `/play/<id>`) | Anime Ninja | La ruta `/m3u8/` sugiere HLS directo → buen candidato para ExoPlayer |
| Byse | `bysewihe.com`, `byselapuix.com` | Latanime, Anime Ninja | Cambia de dominio |
| Mega | `mega.nz` | Latanime, Anime Ninja | Cifrado en el cliente → WebView |
| Mixdrop | `mixdrop.top` | Latanime | |
| dsvplay | `playmogo.com` | Latanime | |
| Hexload | `hexload.com` | Latanime | |
| Savefiles | `savefiles.com` | Latanime | |
| Terabox | `terabox.com` | Anime Ninja | |
| Uns | `animeav1.uns.bio` | Anime Ninja | |

Ningún extractor está verificado todavía: se analizará cada reproductor en la Fase 5.

## Decisiones de diseño que salen de este análisis

1. **Cloudflare interactivo es habitual** (JKAnime, animeonline.ninja, búsqueda de Latanime). Un WebView oculto no lo resuelve solo: la app necesita una pantalla **"Verificar sitio"** con WebView visible, manejable con el control remoto, donde el usuario pase la verificación. Luego se copian las cookies (`cf_clearance`) a OkHttp con el mismo User-Agent.
2. **Detección de desafío**: estado 403/503 + cabecera `cf-mitigated: challenge` o `<title>Just a moment...</title>`. Fixtures en `fixtures/cloudflare/`.
3. **Los dominios cambian** (AnimeFLV www3→www4, animelhd.com→veranime.ninja, animeonline.ninja→ver2): todo dominio va en la configuración remota.
4. **Decodificadores de URL de servidor** reutilizables: `base64` (Latanime), `base64+xor(clave de la página)` (darkanimes), objeto JS `embeds` (animeav1). Se declaran en la configuración; el código solo implementa los decodificadores.
5. **Filtros heterogéneos**: Latanime usa parámetros de consulta combinables; Anime Ninja usa rutas (un filtro a la vez). El modelo `FilterGroup` debe admitir `source: "query"` y `source: "path"`, y la búsqueda multisitio solo podrá ofrecer **género** y **tipo** como filtros comunes (año solo en Latanime de momento).
6. **Normalización de portadas**: usar `data-src` → `src`, descartar placeholders (`/img/anime.png`, `/img/capblank.png`, GIF `data:`), y reescribir el tamaño de TMDB (`/w92/` → `/w342/`).
7. **Idioma**: Latanime publica cada anime dos veces ("… Latino" / "… Castellano"); la agrupación de duplicados debe quitar esos sufijos al normalizar títulos y mostrarlos como variantes de idioma.

## Pendiente (necesito tu ayuda)

| # | Qué | Para qué |
|---|---|---|
| 1 | HTML guardado de **AnimeFLV** (y si ves `www3` o `www4`) | Documentar búsqueda, filtros, detalle y servidores |
| 2 | HTML guardado de **JKAnime** | Ídem |
| 3 | Confirmar la URL de **AnimeLHD** (y su HTML si no es `animelhd.com`) | Saber si es un sitio aparte |
| 4 | Confirmar que **AnimeNinja = `veranime.ninja`** y no `animeonline.ninja` | Ídem |
| 5 | HTML de `https://latanime.org/buscar?q=one+piece` | Selectores de los resultados de búsqueda |
