# AnimeFLV — análisis (Fase 0) · ⛔ BLOQUEADO

- **Fecha del análisis:** 2026-11-08 (intentos entre 21:51 y 22:20 UTC)
- **Estado:** no se pudo leer ninguna página HTML. **No hay selectores ni filtros documentados**: no se inventa nada.

## 1. Dominio

| URL | Resultado |
|---|---|
| `https://animeflv.net/` | **522** |
| `https://www3.animeflv.net/` | **522** (también `/browse?q=one+piece`) |
| `https://www4.animeflv.net/` | **522** |
| `https://www3.animeflv.net/robots.txt` | 200 servido **desde la caché** de Cloudflare (`cf-cache-status: STALE`) con cabecera `location: https://www4.animeflv.net/robots.txt` |

- El **522** lo genera Cloudflare cuando **no logra conectar con el servidor de origen** del sitio. No es un bloqueo hacia nosotros ni un desafío anti-bots: el servidor de AnimeFLV no respondía (caída o cambio de servidor). Se repitió varias veces durante ~30 minutos con el mismo resultado.
- La cabecera `location` en la respuesta en caché sugiere que el sitio **se movió (o se está moviendo) de `www3` a `www4.animeflv.net`**. Por eso el dominio debe vivir en la configuración remota.
- Cabeceras del origen (de la respuesta en caché): `x-powered-by: PleskLin`, `x-turbo-charged-by: LiteSpeed`.

## 2. Protección

- **Cloudflare: sí** (`server: cloudflare`).
- `robots.txt` contiene "Cloudflare Managed Content" (señales de contenido y bloqueo de bots de IA).
- No se pudo comprobar si hay desafío JS en las páginas porque el origen no responde.

## 3–8. Búsqueda, filtros, selectores, novedades, detalle, episodio

**Pendiente.** Las pistas del enunciado (`/browse` con `genre[]`, `year[]`, `type[]`, `status[]`, `order`; episodios y servidores dentro de `<script>`) **no están verificadas**.

## 9. Lo que necesito (HTML guardado)

Desde un navegador donde AnimeFLV cargue, guarda estas páginas con **Ctrl+S → "Página web, solo HTML"** (conserva los `<script>`, que es donde suelen estar episodios y servidores) y súbelas al repo en `app/src/test/resources/fixtures/animeflv/`, o pásamelas como prefieras:

| Archivo | Página |
|---|---|
| `inicio.html` | Portada (últimos episodios) |
| `busqueda_one_piece.html` | Resultado de buscar "one piece" |
| `browse_filtros.html` | La página del directorio/búsqueda que tenga el formulario de filtros, con al menos un género y un año seleccionados |
| `anime.html` | Ficha de un anime en emisión (con lista de episodios) |
| `episodio.html` | Página de un episodio (con la lista de servidores) |

Dime también **la URL exacta** de cada una y el dominio que te muestra el navegador (¿`www3` o `www4`?).
