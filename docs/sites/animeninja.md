# Anime Ninja (veranime.ninja) — análisis (Fase 0)

- **Fecha del análisis:** 2026-11-08
- **Método:** peticiones HTTP normales (curl, User-Agent de Chrome de escritorio). Todo se leyó del HTML/JS/JSON real.
- **Fixtures:** `app/src/test/resources/fixtures/animeninja/`

> ⚠️ **Confirmar identidad del sitio.** Hay dos sitios con "Ninja" en el nombre:
> - **`veranime.ninja`** — título *"Anime Ninja | Ver Anime Online Gratis"*. Es el que se analiza aquí. Curiosamente, `animelhd.com` redirige (301) a este dominio.
> - **`animeonline.ninja`** → 301 a `ver2.animeonline.ninja` — *"AnimeOnline Ninja"*, protegido con desafío **interactivo** de Cloudflare en la portada (no analizable sin el HTML guardado).
>
> Si "AnimeNinja" es el segundo, necesito su HTML guardado.

## 1. Dominio

| URL | Resultado |
|---|---|
| `https://animelhd.com/` | 301 → `https://veranime.ninja/` (la cabecera trae un `)` sobrante al final: `https://veranime.ninja/)`) |
| `https://veranime.ninja/` | 200 |

- WordPress con el tema **DooPlay** (variables `dtAjax`, `dtGonza`, `admin-ajax.php`, `wp-json/dooplayer/v2/`), caché LiteSpeed, detrás de Cloudflare.

## 2. Protección

- `server: cloudflare`, **sin desafío**: inicio, búsqueda, archivos, detalle, episodio y `admin-ajax.php` responden 200 a una petición normal.

## 3. Búsqueda

```
GET https://veranime.ninja/?s=<texto>
GET https://veranime.ninja/page/<n>/?s=<texto>     (página 2 en adelante)
```

- Verificado: `?s=dragon` → 30 resultados; `/page/2/?s=dragon` → 19 resultados. Enlaces de paginación en `.pagination a`.
- Búsqueda en vivo del tema: `https://veranime.ninja/wp-json/dooplay/search/` con `nonce` (en `dtGonza`) — no se usó.

Resultado de búsqueda:

```html
<div class="result-item"><article>
  <div class="image"><div class="thumbnail animation-2">
    <a href="https://veranime.ninja/anime/one-piece/">
      <img src="https://image.tmdb.org/t/p/w92/1g9xCTVrwAwszsdQpLi0lY8dX2a.jpg" alt="One Piece"/>
      <span class="tvshows">TV</span></a></div></div>
  <div class="details">
    <div class="title"><a href="https://veranime.ninja/anime/one-piece/">One Piece</a></div>
    <div class="meta"><span class="rating">IMDb 8.748</span><span class="year">1999</span></div>
    <div class="contenido"><p>Sinopsis…</p></div>
  </div>
</article></div>
```

| Dato | Selector |
|---|---|
| Tarjeta | `div.result-item article` |
| Título / enlace | `div.details div.title a` (texto / `href`) |
| Portada | `div.thumbnail img` → `src` (en búsqueda **no** es lazy) |
| Tipo | `div.thumbnail span` → `tvshows` = "TV", `movies` = "Película" |
| Año | `div.meta span.year` |
| Sinopsis corta | `div.contenido p` |

**Portadas de TMDB:** todas vienen de `image.tmdb.org/t/p/<tamaño>/…`. En la búsqueda el tamaño es `w92` (muy pequeño para TV): reescribir el segmento a `w342`/`w500` (p. ej. `/w92/` → `/w342/`). La app necesita acceso a `image.tmdb.org`.

## 4. Filtros

Anime Ninja no tiene formulario de filtros. Los "filtros" son **archivos de WordPress** (un solo valor cada vez, no combinables):

| Filtro | URL | Valores |
|---|---|---|
| Tipo: Anime (TV) | `/anime/` y `/anime/page/<n>/` | — (74 páginas) |
| Tipo: Película | `/pelicula/` y `/pelicula/page/<n>/` | — |
| Género | `/genero/<slug>/` y `/genero/<slug>/page/<n>/` | 61 géneros (abajo) |
| Año | **no existe** (`/lanzamiento/2024/` → 404) | — |
| Estado, temporada, orden, idioma, letra | **no existen** | — |

Géneros (slug → nombre, leídos del menú de `/anime/`): `accion` Accion, `antropomorfico` Antropomórfico, `artes-marciales` Artes Marciales, `aventura` Aventura, `bl` BL, `carreras` Carreras, `ciencia-ficcion` Ciencia Ficción, `comedia` Comedia, `cosas-de-la-vida` Cosas de la vida, `crime` Crime, `deportes` Deportes, `detectives` Detectives, `drama` Drama, `ecchi` Ecchi, `elenco-adulto` Elenco Adulto, `erotico` Erótico, `escolares` Escolares, `espacial` Espacial, `familia` Familia, `fantasia` Fantasia, `gl` GL, `gore` Gore, `gourmet` Gourmet, `guerra-y-politica` Guerra y política, `harem` Harem, `hentai` Hentai, `historico` Histórico, `idol` Idol, `idols-hombre` Idols (Hombre), `idols-mujer` Idols (Mujer), `infantil` Infantil, `isekai` Isekai, `josei` Josei, `juegos-estrategia` Juegos Estrategia, `kids` Kids, `mahou-shoujo` Mahou Shoujo, `mecha` Mecha, `militar` Militar, `misterio` Misterio, `mitologia` Mitología, `musica` Musica, `parodia` Parodia, `psicologico` Psicológico, `recuentos-de-la-vida` Recuentos de la Vida, `romance` Romance, `samurai` Samurai, `sci-fi` Sci-Fi, `seinen` Seinen, `shoujo` Shoujo, `shoujo-ai` Shoujo Ai, `shounen` Shounen, `shounen-ai` Shounen Ai, `slice-of-life` Slice of Life, `sobrenatural` Sobrenatural, `superpoderes` Superpoderes, `suspenso` Suspenso, `terror` Terror, `thriller` Thriller, `vampiros` Vampiros, `ver-anime` Ver Anime, `western` Western.

Selector para leerlos dinámicamente: `a[href*="/genero/"]` en cualquier página de archivo.

**Idioma:** no hay filtro, pero las tarjetas de episodios recientes llevan etiqueta en `span.quality`: "Audio Latino", "Multi Audio", "Audio Castellano", "Sin Censura", "Audio Latino | Final", "Blu-Ray | Multi Audio". El título de la página de episodio dice "Sub Español".

### Tarjeta en archivos (`/anime/`, `/pelicula/`, `/genero/…`)

```html
<article class="item tvshows" id="post-17179">
  <div class="poster">
    <img data-src="https://image.tmdb.org/t/p/w185/6ZJAOEP5ua4chPO0KEkYu3uU2OX.jpg" src="data:image/gif;base64,…" alt="Ranma ½ (2024)"/>
    <div class="rating">8.155</div>
    <a href="https://veranime.ninja/anime/ranma-%c2%bd-2024/">…</a>
  </div>
  <div class="data"><h3><a href="…">Ranma ½ (2024)</a></h3> <span>Oct. 06, 2024</span></div>
</article>
```

| Dato | Selector |
|---|---|
| Tarjeta | `div.items.full > article.item.tvshows, div.items.full > article.item.movies` (evita los carruseles `#featured-titles` y `.slider`) |
| Título / enlace | `div.data h3 a` |
| Portada | `div.poster img` → `data-src` (lazy; `src` es un GIF `data:` de relleno) — tamaño `w185` |
| Tipo | clase del `article`: `tvshows` / `movies` |
| Año | `div.data span` (fecha "Oct. 06, 2024" → año) |

## 5. Novedades

Inicio `/` → sección **"EPISODIOS RECIENTES"** (30 episodios):

```html
<article class="item se episodes" id="post-70463">
  <div class="poster">
    <a href="https://veranime.ninja/ver/<slug>-episodio-6/"><img data-src="https://image.tmdb.org/t/p/w300/….jpg" alt="… Episodio 6"/></a>
    <span class="quality">Sin Censura</span>
    <div class="info"><div class="ep-meta"><span class="ep-badge"><span class="ep-text"></span> 6</span></div>
    <span class="time">hace 14 minutos</span></div>
  </div>
  <div class="data"><h3>Título del anime</h3></div>
</article>
```

| Dato | Selector |
|---|---|
| Tarjeta | `article.item.se.episodes` |
| Título | `div.data h3` |
| Nº episodio | `span.ep-badge` (texto) |
| Imagen | `div.poster img` → `data-src` (`w300`) |
| Enlace | `div.poster > a` |
| Idioma/etiqueta | `span.quality` |
| Hace cuánto | `span.time` |

Inicio también tiene "temporadas recientes" (`#dt-seasons article.item.se.seasons`), animes y películas recientes. Existe `/horario-animeninja/` (horario de emisión, no analizado).

## 6. Detalle `/anime/<slug>/`

| Dato | Selector | Ejemplo (One Piece) |
|---|---|---|
| Portada | `div.sheader div.poster img` → `data-src` | `image.tmdb.org/t/p/w185/…` |
| Título | `div.sheader div.data h1` | One Piece |
| Fecha | `div.sheader div.extra span.date` | Oct. 20, 1999 |
| Géneros | `div.sgeneros a` | Accion, … |
| Sinopsis | `#info div.wp-content` | — |
| Ficha | `div.custom_fields` (pares `b.variante` / `span.valor`) | Título original, TMDb Rating, Fecha de primera emisión, **Última transmisión**, Temporadas (23), Episodios (1181), Duración media |
| Temporadas | `#seasons div.se-c`; nº en `div.se-q span.se-t` | 23 |
| Episodios | `#seasons div.se-c ul.episodios li` | 1180 |
| ↳ nº | `div.numerando` → "1 - 1" (temporada - episodio) | |
| ↳ título / enlace | `div.episodiotitle a` → `/ver/one-piece-episodio-1/` | |
| ↳ fecha | `div.episodiotitle span.date` | |
| ↳ miniatura | `div.imagen img` → `data-src` (`w154`) | |

- La lista de episodios **viene completa en el HTML** (página de 600 KB para One Piece).
- **Estado (emisión/finalizado): no hay campo explícito.** Se puede aproximar con "Última transmisión" (fecha reciente = en emisión).

## 7. Episodio `/ver/<slug>-episodio-<n>/` — servidores

Anime Ninja **no lista los servidores en su HTML**. Hay una sola opción "MULTISERVER" que carga un iframe de **otro sitio agregador**:

```html
<ul id="playeroptionsul">
  <li id="player-option-1" class="dooplay_player_option" data-type="tv" data-post="70455" data-nume="1">
    MULTISERVER animeav1.com
  </li>
</ul>
```

1. `POST https://veranime.ninja/wp-admin/admin-ajax.php` con `action=doo_player_ajax&post=<data-post>&nume=<data-nume>&type=<data-type>` (así lo hace el tema: `dtAjax.play_method = "admin_ajax"`).
2. Respuesta: `{"embed_url":"https://animeav1.com/media/tank-chair/6","type":"iframe"}`.
3. El `embed_url` apunta a uno de estos agregadores (según el anime):

**a) `darkanimes.com`** (One Piece 1, Dragon Ball Daima 16)
- Página con un `<script>` JSON de configuración (`{"ok":true, …, "pk":"bd2ea17dccea", …}`) y botones `button[data-url]`.
- `data-url` = Base64 de la URL **cifrada con XOR** usando la clave `pk` del JSON (verificado: el resultado son URLs válidas).
- Servidores encontrados: **Mp4Upload** (`mp4upload.com/embed-…`), **Mega** (`mega.nz/embed/…`), **Zilla-networks** (`player.zilla-networks.com/m3u8/<id>` — HLS), **Uns** (`animeav1.uns.bio/#…`), **Terabox** (`terabox.com/sharing/embed?…`). Etiquetas como "Con Anuncios" en el texto del botón.

**b) `animeav1.com`** (Tank Chair 6)
- Los datos van en un objeto JS dentro de la página (SvelteKit): `embeds:{SUB:[{server:"HLS",url:"…"}, …], DUB:[…]}` y `downloads:{SUB:[…], DUB:[…]}`.
- Separa **SUB** (subtitulado) y **DUB** (doblado) → útil para el filtro de idioma.
- Servidores: **HLS** (`player.zilla-networks.com/play/<id>`), **Voe** (`voe.sx/e/…`), **Byse** (`byselapuix.com/e/…`), **MP4Upload** (`mp4upload.com/embed-…`). Descargas: TransferIt, Mega, 1Fichier, MP4Upload.

Otros datos de la página de episodio: navegación `.pag_episodes a` (ANTERIOR / TODO / SIGUIENTE; "SIGUIENTE" = `#` si no hay más), descargas `?dla_go=…`.

## 8. Anuncios detectados

- `pl28966574.profitablecpmratenetwork.com` (script), enlaces a `verhentai.ninja`, `hentaionline.net`, `veranimex.com`.
- En animeav1.com: `aarems.org`.

## 9. Borrador para la configuración remota

```json
{
  "id": "animeninja",
  "baseUrl": "https://veranime.ninja",
  "search": { "path": "/", "queryParam": "s", "pagePath": "/page/{page}/" },
  "latest": { "path": "/", "card": "article.item.se.episodes", "title": "div.data h3", "episode": "span.ep-badge", "cover": "div.poster img", "link": "div.poster > a", "lang": "span.quality" },
  "selectors": {
    "searchCard": "div.result-item article", "searchTitle": "div.details div.title a", "searchCover": "div.thumbnail img",
    "searchType": "div.thumbnail span", "searchYear": "div.meta span.year",
    "card": "div.items.full > article.item.tvshows, div.items.full > article.item.movies",
    "title": "div.data h3 a", "cover": "div.poster img", "link": "div.data h3 a"
  },
  "detail": {
    "cover": "div.sheader div.poster img", "title": "div.sheader div.data h1", "synopsis": "#info div.wp-content",
    "genres": "div.sgeneros a", "seasons": "#seasons div.se-c", "episodes": "ul.episodios li",
    "episodeNumber": "div.numerando", "episodeLink": "div.episodiotitle a"
  },
  "servers": { "item": "#playeroptionsul li.dooplay_player_option", "ajax": "/wp-admin/admin-ajax.php", "action": "doo_player_ajax" },
  "filters": [
    { "id": "type", "source": "path", "values": { "tv": "/anime/", "movie": "/pelicula/" } },
    { "id": "genre", "source": "html", "path": "/genero/{value}/", "links": "a[href*='/genero/']" }
  ],
  "coverRewrite": { "pattern": "/t/p/w\\d+/", "replace": "/t/p/w342/" }
}
```

## Portada: recomendados y series nuevas (2026-11-09)

Analizado sobre `fixtures/animeninja/inicio.html` y verificado en vivo (salud de sitios).

| Sección | Tarjeta | Datos |
|---|---|---|
| 🔥 RECOMENDADOS (8) | `#featured-titles article.item` | título y enlace `div.data h3 a`, portada `div.poster img` (`data-src`), año `div.data > span`, valoración `div.rating` |
| ANIMES RECIENTES (18) | `#tvload + div.items > article.item` | igual, con la fecha en `div.data > span` ("Nov. 06, 2026") |

La portada no trae sinopsis: la reseña se toma de la ficha (`#info div.wp-content`) solo de las tarjetas visibles. Una valoración de 0 se oculta.
