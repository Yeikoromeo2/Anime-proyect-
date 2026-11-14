# AnimeFLV — análisis

- **Fecha:** 2026-11-09.
- **Proveedor:** `providers/animeflvwp/` (`"engine": "animeflv-wp"`), sobre **animeflv.or.at**.
- **Fixtures:** `app/src/test/resources/fixtures/animeflv/`.

## 1. El original cerró; esta es una copia

| URL | Resultado (2026-11-08 y 2026-11-09) |
|---|---|
| `https://animeflv.net/`, `www3.`, `www4.` | **522**: el servidor de origen ya no responde |
| `https://animeflv.or.at/` | **200**, sin verificación de Cloudflare (`www.` redirige aquí) |

- **El AnimeFLV original:** dejó de servir video en junio de 2026 y después cerró ([TorrentFreak](https://torrentfreak.com/anime-piracy-giant-animeflv-mysteriously-stopped-serving-video/), [TorrentFreak](https://torrentfreak.com/mpa-takes-credit-for-shutting-down-piracy-giants-animeflv-and-cineby/)).
- **animeflv.or.at:** el usuario lo indicó como el sitio actualizado. Es una **copia no oficial**: WordPress 7 con un tema propio que imita a AnimeFLV, de dueños desconocidos.
  - Un escáner automático lo considera "generalmente seguro" con advertencias ([Gridinsoft](https://gridinsoft.com/online-virus-scanner/url/animeflv-or-at)).
  - En la app solo se lee su HTML y se reproducen los mismos servidores externos que usa Anime Ninja, con el bloqueo de anuncios y rastreadores activo.

## 2. Portada (`/`)

| Sección | Selector | Notas |
|---|---|---|
| Últimos episodios | `section.home-recent-episodes div.ht_grid_1_4` | Enlace `a.thumbnail-link`, título `h2.entry-title` ("Tank Chair Episodio 6"), imagen `img`. La ficha sale de la clase `category-{serie}` de la tarjeta → `/anime/{serie}/` |
| Lista completa de Animes (portada) | `div.listCatsHome div.ht_grid_1_5` | Con **reseña** en `div.Description > p:last-of-type` → "Series nuevas" |

## 3. Búsqueda y catálogo

- **Búsqueda:** `/?s=texto` → tarjetas `div.search-series-card` (título `h2.entry-title`, portada `img.anime-image`), sin más páginas.
- **Catálogo completo:** `/anime/` y `/anime/page/N/` (18 por página); hay más mientras exista `a.next.page-numbers`.
- **Filtros:** **no tiene filtros que funcionen**. Las páginas `/genre/{género}/` salen vacías, como una ficha sin episodios, así que en Explorar solo aparece "Todo el catálogo".

## 4. Ficha (`/anime/{serie}/`)

- **Datos:** título `h1.anime-title`, portada `img.poster-image`, valoración `div.rating-score`, géneros `span.genre-tag`, sinopsis `div.anime-synopsis > p`.
- **Episodios:** en `<script type="application/json" class="animeflv-episodes-data">`, como `[{"permalink": ".../2026/11/08/tank-chair-episodio-6/", "number": 6}]`.
- **Catálogo incompleto:** la copia solo tiene episodios recientes; de One Piece, del 1167 al 1180.

## 5. Episodio: servidores

Bloque `div.server-frames div.button-container`:
- **Nombre:** en `span.tooltip-text` (HLS, Voe, Byse, MP4Upload).
- **URL:** en Base64 en `button[data-src]`.
- **Idioma:** en `app_type` ("Sub").

Son los mismos servidores que en Anime Ninja.
