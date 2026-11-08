# JKAnime — análisis (Fase 0) · ⛔ BLOQUEADO POR CLOUDFLARE

- **Fecha del análisis:** 2026-11-08
- **Estado:** solo se pudo ver la página de verificación de Cloudflare. **No hay selectores ni filtros documentados.**
- **Fixture:** `app/src/test/resources/fixtures/cloudflare/jkanime_inicio_interactive.html` (página de desafío, útil para probar la detección de Cloudflare).

## 1. Dominio

| URL | Resultado |
|---|---|
| `https://jkanime.net/` | **403** · `cf-mitigated: challenge` · `<title>Just a moment...</title>` |

## 2. Protección

- **Cloudflare con desafío interactivo** (`cType: 'interactive'`, Turnstile) **ya en la portada**. Cualquier petición directa (OkHttp) recibirá esta página.
- No intenté saltar el desafío con un navegador automatizado: ese tipo de evasión queda fuera de lo que voy a hacer desde aquí.
- **Consecuencia para la app:** JKAnime necesitará siempre la sesión de un WebView en el que **el usuario haya pasado la verificación** (cookie `cf_clearance` + exactamente el mismo User-Agent en OkHttp y WebView). Como el desafío es interactivo, el WebView oculto puede no bastar: hará falta mostrar un WebView visible ("Verificar sitio") manejable con el control remoto.

## 3–8. Búsqueda, filtros, selectores, novedades, detalle, episodio

**Pendiente.** La pista del enunciado (`/directorio` con filtros) **no está verificada**.

## 9. Lo que necesito (HTML guardado)

Desde un navegador donde JKAnime cargue, guarda con **Ctrl+S → "Página web, solo HTML"** y deja los archivos en `app/src/test/resources/fixtures/jkanime/`:

| Archivo | Página |
|---|---|
| `inicio.html` | Portada (últimos episodios) |
| `busqueda_one_piece.html` | Resultado de buscar "one piece" |
| `directorio_filtros.html` | `/directorio` con al menos un género y un año seleccionados |
| `anime.html` | Ficha de un anime con lista de episodios |
| `episodio.html` | Página de un episodio (lista de servidores) |

Indica también la URL exacta de cada una.
