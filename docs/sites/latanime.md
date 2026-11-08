# Latanime — análisis (Fase 0)

- **Fecha del análisis:** 2026-11-08
- **Método:** peticiones HTTP normales (curl, User-Agent de Chrome de escritorio). Todo lo de abajo se leyó del HTML/JS real del sitio, salvo lo marcado como **pendiente**.
- **Fixtures:** `app/src/test/resources/fixtures/latanime/`

## 1. Dominio

| URL | Resultado |
|---|---|
| `https://latanime.org/` | 200, sin redirección |

- Backend Laravel (cookies `latanime_session` y `XSRF-TOKEN`, `<meta name="csrf-token">`).
- Detrás de Cloudflare (`server: cloudflare`, Rocket Loader).

## 2. Protección (Cloudflare)

| Ruta | Respuesta sin navegador |
|---|---|
| `/`, `/animes`, `/emision`, `/anime/<slug>`, `/ver/<slug>-episodio-<n>` | **200** directo |
| `GET /buscar?q=…` | **403** `cf-mitigated: challenge`, desafío **interactivo** (`cType: 'interactive'`) |
| `POST /buscar_ajax` | **403**, desafío **managed** (`cType: 'managed'`) |

Consecuencia: el catálogo, el detalle y los episodios se pueden leer con OkHttp. **La búsqueda por texto solo funciona después de que el usuario pase la verificación de Cloudflare en un WebView visible** (cookie `cf_clearance` + mismo User-Agent). Alternativa sin desafío: directorio `/animes` con filtro de letra.

## 3. Búsqueda

- **Página de resultados:** `GET /buscar?q=<texto>` (formulario `form#search.form_search_ajax`, campo `q`).
  - **Pendiente:** no se pudo ver el HTML de resultados por el desafío. Necesito el HTML guardado de `https://latanime.org/buscar?q=one+piece`.
- **Autocompletado del sitio:** `POST /buscar_ajax` con cuerpo JSON `{"q": "<texto>"}` y cabecera `X-CSRF-TOKEN` (valor de `<meta name="csrf-token">`). Según `/js/index.js` del sitio, la respuesta es un array `[{ "url", "nombre", "imagen" }]` y se lanza con más de 2 caracteres. (Formato leído del JS; la respuesta real no se pudo ver por el desafío.)
- Paginación de la búsqueda: **pendiente** (depende del HTML de resultados).

## 4. Filtros — directorio `/animes`

```
GET https://latanime.org/animes?fecha=<año>&genero=<slug>&letra=<letra>&categoria=<valor>&p=<página>
```

- Es un `<form method="get" action="/animes">` con 4 `<select>` y botón "Buscar".
- El valor `false` significa "sin filtro" (el formulario lo envía así). Se pueden combinar los 4.
- **Un solo valor por filtro** (select simple, no múltiple).
- 30 tarjetas por página; parámetro de página **`p`** (el directorio completo tiene 117 páginas).
- Verificado en vivo: `fecha=2024&genero=accion` → animes de acción de 2024 (paginado hasta `p=4`); `categoria=ova` → OVAs.
- Valores con espacios/acentos (`Película`, `Película Latino`) → codificar en la URL.

| Filtro | Parámetro | Múltiple | Valores (leídos del HTML) |
|---|---|---|---|
| Año | `fecha` | No | 2026 … 1982 (cada año) |
| Género | `genero` | No | `accion` Acción, `aventura` Aventura, `carreras` Carreras, `ciencia-ficcion` Ciencia Ficción, `comedia` Comedia, `cyberpunk` Cyberpunk, `deportes` Deportes, `drama` Drama, `ecchi` Ecchi, `escolares` Escolares, `fantasia` Fantasía, `gore` Gore, `harem` Harem, `horror` Horror, `josei` Josei, `lucha` Lucha, `magia` Magia, `mecha` Mecha, `militar` Militar, `misterio` Misterio, `musica` Música, `parodias` Parodias, `psicologico` Psicológico, `recuerdos-de-la-vida` Recuerdos de la vida, `seinen` Seinen, `shojo` Shojo, `shonen` Shonen, `sobrenatural` Sobrenatural, `vampiros` Vampiros, `yaoi` Yaoi, `yuri` Yuri, `latino` Latino, `espacial` Espacial, `historico` Histórico, `samurai` Samurai, `artes-marciales` Artes Marciales, `demonios` Demonios, `romance` Romance, `dementia` Dementia, `policia` Policía, `castellano` Castellano, `historia-paralela` Historia paralela, `aenime` Aenime, `donghua` Donghua, `blu-ray` Blu-ray, `monogatari` Monogatari, `suspenso` Suspenso, `isekai` Isekai |
| Letra | `letra` | No | `09` (0-9), `A` … `Z` |
| Categoría (tipo + idioma) | `categoria` | No | `anime` Anime, `ova` Ova, `Película` Película, `especial` Especial, `corto` Corto, `ona` Ona, `donghua` Donghua, `sin-censura` Sin Censura, `preestreno` PREESTRENO, `pelicula-1080p` Pelicula 1080p, `latino` Latino, `Película Latino` Pelicula Latino, `castellano` Castellano, `Película Castellano` Pelicula Castellano, `ova-latino` Ova Latino, `ova-castellano` Ova Castellano, `latino-sin-censura` Latino Sin Censura, `live-action` Live Action, `Cartoon` Cartoon, `catalan` Catalán |

Filtros que **no existen** en Latanime: estado (solo la página `/emision`), temporada, orden. No hay filtro de idioma dedicado: el idioma va mezclado en `categoria`/`genero` (`latino`, `castellano`, `catalan`) y en el propio título ("… Latino", "… Castellano").

También existe `GET /genero/<slug>` (enlazado desde el detalle).

## 5. Selectores — tarjeta de anime (`/animes`, `/emision`)

```html
<div class="col-md-4 col-lg-3 col-xl-2 col-6 my-3">
  <a href="https://latanime.org/anime/boku-no-hero-academia-s7-castellano">
    <div class="series">
      <div class="serieimg shadown">
        <img class="lozad img-fluid2 …" src="https://latanime.org/img/anime.png"
             data-src="https://latanime.org/thumbs/imagen/boku-no-hero-academia-s7-castellano-1791330479.jpg?v=1.5" alt="…">
      </div>
      <div class="seriedetails">
        <h3 class="my-1">Boku no Hero Academia S7 Castellano</h3>
        <div><span class="opacity-75">Castellano</span><span style="color:#ffc119;"><svg…/> 2024</span></div>
      </div>
    </div>
  </a>
</div>
```

| Dato | Selector (Jsoup) |
|---|---|
| Tarjeta | `a:has(> div.series)` |
| Título | `div.seriedetails h3` |
| Portada | `div.serieimg img` → atributo `data-src`; si no hay, `src` (en `/emision` la imagen **no** es lazy y viene en `src`) |
| Enlace | `href` de la tarjeta |
| Idioma/categoría | `div.seriedetails span.opacity-75` |
| Año | `div.seriedetails span:last-child` (texto, p. ej. `2024`) |

- Placeholders a descartar como portada: `/img/anime.png`, `/img/capblank.png`.
- Portada vertical: `/thumbs/imagen/<slug>-<id>.(jpg|webp)?v=1.5`. Imagen horizontal (episodios): `/thumbs/portada/…`.
- No hay "tipo" (TV/película/OVA) en la tarjeta; solo idioma y año.

## 6. Novedades

**Inicio `/` → sección "Añadidos recientemente"** (36 episodios, horizontales):

```html
<a href="https://latanime.org/ver/<slug>-episodio-12">
  <div class="imgrec">
    <img class="lozad nxtmainimg" src=".../img/capblank.png" data-src="https://latanime.org/thumbs/portada/<slug>-<id>.jpg?v=1.5" alt="… capitulo 12">
    <div class="info_cap"><span>Latino</span></div>
  </div>
  <div class="info">
    <h2 class="mt-3">Episodio 12 - Tensei Kizoku, Kantei Skill de Nariagaru S1 Latino</h2>
    <span class="span-tiempo">08 Nov 2026</span>
  </div>
</a>
```

| Dato | Selector |
|---|---|
| Tarjeta | `a[href*="/ver/"]:has(div.imgrec)` |
| Título + nº | `div.info h2` → regex `^Episodio (\d+) - (.+)$` |
| Imagen | `div.imgrec img` → `data-src` |
| Idioma | `div.info_cap span` |
| Fecha | `span.span-tiempo` |

- URL del anime: en los ejemplos vistos es la URL del episodio sin `-episodio-<n>` y con `/ver/` → `/anime/` (confirmar con tests).
- Inicio también tiene "Series recientes" (`h3.title_cap`).
- `/emision` = "Animes de estreno" (30 tarjetas, misma estructura que el directorio).
- `/calendario` existe (no analizado).

## 7. Detalle `/anime/<slug>`

| Dato | Selector | Ejemplo |
|---|---|---|
| Portada | `div.serieimgficha img.img-fluid2` → `src` | `/thumbs/imagen/devils-crest-castellano-….jpg` |
| Título | `div.col-lg-9 > h2` | Devils' Crest Castellano |
| Título alternativo | `div.col-lg-9 > h3.fs-6` | Demons' Crest Audio Castellano |
| Estreno | `div.col-lg-9 span.span-tiempo` | "Estreno: 06 de Noviembre de 2026" |
| Nº episodios | `div.col-lg-9 > p` que empieza por "Episodios:" | "Episodios: 2" |
| Géneros | `a[href*="/genero/"] div.btn` | Acción, Aventura, Fantasía |
| Sinopsis | `div.col-lg-9 > p.opacity-75` | — |
| Estado | `button.btn-estado.finalizado` → "Finalizado"; `button.btn-estado.estreno` → "Estreno" (en emisión). Ignorar `#follow_d` ("Seguir") | — |
| Episodios | `div.col-lg-9 a[href*="/ver/"]:has(div.cap-layout)`; texto "<Título> - Capitulo N"; miniatura `img[data-src]` | orden ascendente |

La lista de episodios **viene completa en el HTML** (Black Clover: 173 enlaces), no en un `<script>`.

Fixtures: `anime_finalizado.html` (Devils' Crest Castellano), `anime_estreno.html` (Devils' Crest Latino).

## 8. Episodio `/ver/<slug>-episodio-<n>` — servidores

Los servidores están en el HTML como enlaces con la URL del reproductor **en Base64**:

```html
<ul class="cap_repro d-flex flex-wrap">
  <li id="play-video"><a class="play-video repro-item cap" data-player="aHR0cHM6Ly92b2Uuc3gvZS95MWdnZnUxMGZjcW4=">voe</a></li>
  …
</ul>
```

- Selector: `ul.cap_repro a.play-video[data-player]` → nombre = texto, URL = `Base64.decode(data-player)`.
- No hay `<iframe>` en el HTML inicial (lo crea el JS al hacer clic).

Servidores encontrados (episodio 1 de Devils' Crest Castellano):

| Nombre en el sitio | Dominio del embed |
|---|---|
| dsvplay | `playmogo.com/e/…` |
| hexload | `hexload.com/embed-…` |
| savefiles | `savefiles.com/e/…` |
| mega | `mega.nz/embed/#!…` |
| mixdrop | `mixdrop.top/e/…` |
| voe | `voe.sx/e/…` |
| mp4upload | `www.mp4upload.com/embed-….html` |
| byse | `bysewihe.com/e/…` |

- Descargas (no reproductores): savefiles.com/d, mega.nz, gofile.io, 1cloudfile.com, mediafire.com.
- Navegación: enlaces "Lista" (`/anime/<slug>`), "Anterior" y "Siguiente" (`/ver/…`).

## 9. Anuncios detectados (para la lista de bloqueo)

- `var direct_link = "https://araplhn.org/…"` → popunder al hacer clic en enlaces.
- Scripts: `abscloud.org`, `ml.taigasginep.com`; enlace a `eroneko2.net`.

## 10. Borrador para la configuración remota

```json
{
  "id": "latanime",
  "baseUrl": "https://latanime.org",
  "search": { "path": "/buscar", "queryParam": "q", "requiresChallenge": true },
  "browse": { "path": "/animes", "pageParam": "p", "emptyValue": "false" },
  "latest": { "path": "/", "card": "a[href*='/ver/']:has(div.imgrec)", "title": "div.info h2", "cover": "div.imgrec img", "lang": "div.info_cap span" },
  "selectors": {
    "card": "a:has(> div.series)", "title": "div.seriedetails h3", "cover": "div.serieimg img",
    "link": "self", "lang": "div.seriedetails span.opacity-75", "year": "div.seriedetails span:last-child"
  },
  "detail": {
    "cover": "div.serieimgficha img.img-fluid2", "title": "div.col-lg-9 > h2", "synopsis": "div.col-lg-9 > p.opacity-75",
    "genres": "a[href*='/genero/'] div.btn", "status": "button.btn-estado.finalizado, button.btn-estado.estreno",
    "episodes": "div.col-lg-9 a[href*='/ver/']:has(div.cap-layout)"
  },
  "servers": { "item": "ul.cap_repro a.play-video[data-player]", "urlAttr": "data-player", "decode": "base64" },
  "filters": [
    { "id": "year", "param": "fecha", "multi": false, "source": "html", "select": "select[name=fecha]" },
    { "id": "genre", "param": "genero", "multi": false, "source": "html", "select": "select[name=genero]" },
    { "id": "letter", "param": "letra", "multi": false, "source": "html", "select": "select[name=letra]" },
    { "id": "type", "param": "categoria", "multi": false, "source": "html", "select": "select[name=categoria]" }
  ],
  "coverPlaceholders": ["/img/anime.png", "/img/capblank.png"]
}
```
