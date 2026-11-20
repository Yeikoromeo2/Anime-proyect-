# JKAnime — análisis

- **Fecha:** 2026-11-09 (sobre jkanime.org).
- **Fixtures:** `app/src/test/resources/fixtures/jkanime/`.
- **Proveedor:** `providers/jkanime/` (`"engine": "jkanime"`).

## 1. Dominio y protección

| URL | Resultado |
|---|---|
| `https://jkanime.net/` | **403** · `cf-mitigated: challenge` (verificación interactiva de Cloudflare) |
| `https://jkanime.org/` | **200**, sin verificación. Mismo catálogo, mismo inicio de sesión |
| `https://jkanime.bz/` | **200**, la misma página que jkanime.org |

Según una comprobación pública del 4 de noviembre de 2026, jkanime.org y jkanime.bz son del mismo sitio y abren sin la verificación. La app usa **jkanime.org**; si un día pide verificación, el dominio se cambia en Ajustes o en la configuración remota. **No se intenta saltar la verificación de jkanime.net.**

## 2. Portada (`/`)

| Sección | Selector | Notas |
|---|---|---|
| Últimos episodios | `#animes div.card > a` | Solo la pestaña "Animes" de "Programación" (las de Donghuas, OVAs y Hentai **no** se leen). Título `h5.card-title`, imagen `img.card-img-top`, número `span.badge-primary` ("Ep 6"), fecha `span.badge-secondary` ("Ayer"). La ficha es la URL sin el número: `/kanojo-no-tomodachi/6/` → `/kanojo-no-tomodachi/` |
| Destacados (carrusel) | `div.hero__items` | Imagen en `data-setbg` de la propia tarjeta, título `h2`, **reseña** `div.hero__text p`, enlace `a.slider-show` |
| Animes recientes | `div.trending_div:has(h4:containsOwn(Animes recientes)) div.mode3 > div` | Enlace y título `h5 a`, portada `img`, fecha de emisión `p.card-text.ep` |

## 3. Búsqueda

`GET /buscar/{texto}/` (el formulario `?q=` redirige ahí). Hasta 30 resultados, **sin más páginas** (`/buscar/x/2/` da 404). Tarjeta `div.page_directorio div.anime__item`: enlace y título `h5 a`, portada en el atributo `data-setbg` de `div.anime__item__pic`, tipo `li.anime`.

## 4. Directorio y filtros

`GET /directorio?genero=accion&fecha=2025&tipo=…&estado=…&p=2`: los filtros **se combinan**. Los resultados vienen en la página como JSON: `var animes = {"current_page":1,"last_page":4,"data":[{title, url, image, tipo, synopsis…}]}` (30 por página).

Filtros (`<select>` del formulario de `/directorio`):

| Filtro | `name` | Opciones |
|---|---|---|
| Género | `genero` | 45 (accion, aventura…, latino = "Español Latino", isekai) |
| Tipo | `tipo` | animes, peliculas, especiales, ovas, onas |
| Estado | `estado` | emision, finalizados, estrenos |
| Año | `fecha` | 2026…; las opciones **no tienen `value`** (se usa el texto) |
| Temporada | `temporada` | invierno, primavera… |
| Categoría | `categoria` | donghua, latino |
| Letra | `letra` | A–Z… |
| Orden | `filtro` | por fecha, nombre, popularidad |

## 5. Ficha (`/{slug}/`)

Título `div.anime_info h3`, sinopsis `div.anime_info p.scroll`, portada `div.anime_pic img`, géneros `div.anime_data a[href*='/genero/']`, estado `div.anime_data div.enemision` (clase `currently` = en emisión, `finished` = concluido), tipo `li[rel=tipo]`, fecha `Emitido:`.

**Episodios:** la lista se carga con JavaScript (POST con token a `/ajax/episodes/{id}/{página}`, 16 por página). No hace falta: cada episodio es `/{slug}/{número}/`, así que se arma del 1 al último. El último sale de "Último episodio" (`a#uep`, en emisión) o del campo `Episodios: N` (finalizados). One Piece: 1180.

## 6. Episodio (`/{slug}/{n}/`): servidores

En el script de la página:
- `video[0] = '<iframe src="https://jkanime.org/jkplayer/um?e=…">'`: reproductores propios. Su nombre está en el botón `a.servers.btn-show[data-id=0]` (Desu, Magi).
- `var servers = [{"remote":"<URL en Base64>","server":"VOE","lang":1,…}]`: servidores externos (Mega, Streamwish, Vidhide, VOE, Filemoon, Mixdrop, Streamtape, Doodstream). Se omiten Mediafire y 1Fichier, que solo son de descarga (la página también los omite).
- Idioma: `lang` → texto de `#deflang option[value=lang]` ("Japones Sub. Español" → Subtitulado).
