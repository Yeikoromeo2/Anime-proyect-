# AnimeHub TV

App para Android TV / Google TV que reúne varios sitios de anime en una interfaz nativa pensada para el control remoto y la voz.
**Uso personal:** se instala a mano; no se publica en Google Play ni se redistribuye.

## Estado

| Fase | Estado |
|---|---|
| 0 · Análisis de sitios | Latanime, Anime Ninja, JKAnime y AnimeFLV (copia animeflv.or.at; el original cerró en 2026) ([resumen](docs/fase0-resumen.md), [JKAnime](docs/sites/jkanime.md), [AnimeFLV](docs/sites/animeflv.md)) |
| 1 · Esqueleto (Inicio, navegación con el control, Ajustes, configuración remota, Room, APK en GitHub Actions) | ✅ |
| 2 · Primer proveedor: Anime Ninja (novedades, búsqueda con portadas, ficha y episodios) | ✅ |
| 3 · Filtros dinámicos + búsqueda por voz (+ proveedor de Latanime) | ✅ |
| 4 · Servidores que funcionan + reproductor WebView a pantalla completa + continuidad + historial con minuto | ✅ |
| Inicio sin filas repetidas + Recomendados con reseña + Explorar el catálogo por categorías | ✅ |
| JKAnime como tercer sitio + elegir qué sitio ver en Inicio + respuesta automática a los recuadros de "continuar viendo" | ✅ |
| AnimeFLV (animeflv.or.at) como cuarto sitio + bloqueo de rastreadores, ubicación y WebRTC | ✅ |
| 5 · Reproductor nativo (Media3/ExoPlayer) para los servidores con video directo: HLS de zilla, Desu/Magi de JKAnime y MP4Upload ([detalle](docs/reproductor-nativo.md)) | ✅ |
| Actualizaciones encima (firma fija) + "Buscar actualización" desde la app | ✅ |

## Requisitos

- Android Studio reciente (con soporte para AGP 9.5) o solo la línea de comandos con JDK 17+.
- SDK de Android con la plataforma 37 (Gradle la descarga sola si aceptaste las licencias).
- El dispositivo necesita Android 6.0 (API 23) o superior.

## Compilar

```bash
./gradlew testDebugUnitTest   # tests: parsers con los HTML de fixtures + navegación con el D-pad (Robolectric)
./gradlew testDebugUnitTest -Pcapturas   # además guarda capturas de pantalla en app/build/capturas
./gradlew testDebugUnitTest -Psalud --tests '*SaludSitiosTest*'   # "salud de sitios": prueba EN VIVO cada sitio (búsqueda de "one piece")
node tools/player-js-test/test.js   # prueba el script del reproductor web en Chromium (Playwright)
./gradlew assembleDebug       # APK de depuración → app/build/outputs/apk/debug/
./gradlew assembleRelease     # APK de release   → app/build/outputs/apk/release/
```

### APK desde GitHub Actions

Cada push compila el APK y lo deja en la pestaña **Actions** → la ejecución → **Artifacts** (`AnimeHubTV-<versión>`).
Para publicar una versión en **Releases** (con enlace directo al APK):
- desde GitHub: **Actions → Compilar APK → Run workflow**, elige la rama y escribe la versión (por ejemplo `0.2.0`); o
- con git: `git tag v0.2.0 && git push origin v0.2.0`.

El enlace directo queda así: `https://github.com/Yeikoromeo2/Anime-proyect-/releases/download/v0.2.0/AnimeHubTV-0.2.0.apk`. Desde la 0.7.0, la app instalada avisa sola de cada versión nueva (ver "Actualizaciones").

## Actualizaciones (sin desinstalar)

Todas las versiones se firman con **la misma clave**, la del proyecto en [`signing/`](signing/). Por eso Android instala cada versión nueva **encima** de la anterior y conserva favoritos, historial y ajustes.

- **Desde la TV:** al abrir la app, si hay una versión nueva en Releases aparece **"⬆ Versión X"** al final de la cabecera de Inicio. También está en **Ajustes → Actualizaciones → Buscar actualización**. La app descarga el APK y abre el instalador de Android; confirmas **Actualizar** y listo.
- **La primera vez**, Android pide permitir que AnimeHub TV instale apps. La app abre ese ajuste: activa "Permitir de esta fuente", vuelve con Atrás y elige **Instalar**.
- **Una sola vez**, al pasar de la 0.6.0 o anterior a la 0.7.0, hay que **desinstalar** la versión vieja, porque esas versiones se firmaron con claves temporales. Desde la 0.7.0 ya no hace falta nunca.
- **La clave está en el repositorio a propósito** (el repo es público): así no hay que configurar nada. El riesgo es que alguien podría firmar un APK falso con ella, pero solo te afectaría si instalaras un APK que no saliera de tu propia Release.
- **No agregues** los Secrets `ANDROID_KEYSTORE_*` en GitHub: si existen, el workflow firma con esa otra clave y la siguiente instalación volvería a pedir desinstalar.

## Instalar en la TV

**Con Downloader (sin cables):**
1. En la TV: Ajustes → Preferencias del dispositivo → Seguridad → permitir "Fuentes desconocidas" para *Downloader*.
2. Instala *Downloader* desde la tienda de la TV.
3. Abre en Downloader el enlace directo al APK (por ejemplo, el de la Release en GitHub) e instálalo.

**Con ADB (desde la PC, misma red Wi-Fi):**
1. En la TV: Ajustes → Preferencias del dispositivo → Información → pulsa 7 veces "Compilación" para activar el modo desarrollador; luego activa "Depuración por red/USB".
2. Busca la IP de la TV (Ajustes → Red).
3. En la PC:
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r AnimeHubTV-0.1.0.apk
   ```

La app aparece en la fila de apps de la TV con su banner.

## Capturas (TV 1080p simulada)

| Inicio | Ajustes (elemento enfocado) | Confirmación al salir |
|---|---|---|
| ![Inicio](docs/capturas/fase1/01_inicio.png) | ![Ajustes](docs/capturas/fase1/03_ajustes.png) | ![Salir](docs/capturas/fase1/05_confirmar_salida.png) |

| Capítulo nuevo elegido | Búsqueda (cuadrícula de portadas) | Ficha del anime |
|---|---|---|
| ![Capítulo nuevo](docs/capturas/fase2/15_ficha_capitulo_nuevo.png) | ![Búsqueda](docs/capturas/fase2/09_busqueda_resultados.png) | ![Ficha](docs/capturas/fase2/11_ficha_one_piece.png) |

| Búsqueda por voz | Panel de filtros | Filtrado por género y año |
|---|---|---|
| ![Voz](docs/capturas/fase3/12_busqueda_voz.png) | ![Filtros](docs/capturas/fase3/13_panel_filtros.png) | ![Resultados](docs/capturas/fase3/14_resultados_filtrados.png) |

| Temporadas en un desplegable | Tramos de 50 episodios | Ir al episodio (teclado numérico) |
|---|---|---|
| ![Temporadas](docs/capturas/fase4/16_desplegable_temporadas.png) | ![Tramos](docs/capturas/fase4/17_temporada_21_tramos.png) | ![Teclado](docs/capturas/fase4/18_teclado_numerico.png) |

| Servidores que funcionan | Reproduciendo | Siguiente episodio |
|---|---|---|
| ![Servidores](docs/capturas/fase4/20_servidores.png) | ![Reproduciendo](docs/capturas/fase4/21_reproduciendo.png) | ![Siguiente](docs/capturas/fase4/22_siguiente_episodio.png) |

| Inicio viendo solo JKAnime | Explorar empieza en ese sitio | |
|---|---|---|
| ![Solo JKAnime](docs/capturas/explorar/29_inicio_solo_jkanime.png) | ![Explorar JKAnime](docs/capturas/explorar/30_explorar_jkanime.png) | |

| Recomendados con reseña | Explorar: menú de categorías | Lista de géneros reales |
|---|---|---|
| ![Recomendados](docs/capturas/explorar/23_inicio_recomendados.png) | ![Explorar](docs/capturas/explorar/26_explorar_menu.png) | ![Géneros](docs/capturas/explorar/27_explorar_lista_generos.png) |

Se generan en las pruebas de navegación, que recorren la app solo con flechas, OK y Atrás. En las pruebas no hay red, así que las portadas aparecen como bloques de color; en la TV se ven las imágenes reales de cada sitio.

## Inicio: capítulos nuevos de frente

Debajo de la cabecera, **"Viendo: Todos los sitios · Latanime · Anime Ninja · AnimeFLV · JKAnime"**: con un sitio elegido, todas las filas (y los géneros) son solo de ese sitio, sin mezclar. La elección se recuerda y Buscar y Explorar empiezan en ese sitio.

Al abrir la app, de arriba abajo:
1. **Últimos capítulos**: lo que cada web acaba de publicar, mezclando todos los sitios activos (el foco va directo al capítulo más reciente). Es una sola fila: ya no se repite una fila por sitio. Si un sitio falla, debajo aparece su aviso con **Reintentar**.
2. **Continuar viendo** y **Favoritos**.
3. **Recomendados**: lo que destaca cada sitio en su portada (el carrusel de Latanime, "🔥 Recomendados" de Anime Ninja), con una **reseña corta**, géneros y valoración. Si la portada no trae la reseña, se toma de la ficha solo de las tarjetas que se ven.
4. **Series nuevas**: estrenos recién agregados a cada sitio ("Series recientes", "Animes recientes"), con la fecha en que se agregaron. Si un sitio publica el mismo anime en varios idiomas, se muestra una sola vez (la versión en latino).
5. **Explorar por género**: un botón por género que abre Explorar con ese género ya elegido.

Al elegir un capítulo nuevo se abre su ficha con ese capítulo marcado como **NUEVO** y enfocado.

## Explorar: todo el catálogo por categorías

Botón **Explorar** en Inicio (o un género de la fila "Explorar por género"). A la izquierda, un menú con las categorías **reales** de cada sitio:
- **Sitio**: todos a la vez o uno solo.
- **Todo el catálogo**: sin filtros.
- **Género**, **Tipo**, **Estado**, **Año** y **Letra** (según lo que ofrezca el sitio). OK abre la lista de opciones; al elegir una, la cuadrícula de la derecha cambia al instante y carga más al bajar.

En "Todos los sitios" se usan los géneros comunes, traducidos a cada sitio. Anime Ninja no combina filtros: si eliges género y tipo, usa solo el género y lo avisa.

## Ficha: series con muchos episodios

- **Temporada**: el botón "Temporada N · X episodios ▾" abre la lista de todas las temporadas (por ejemplo, las 21 de One Piece) y salta a la elegida.
- **Tramos**: si la temporada tiene más de 50 episodios, aparecen chips "1–50", "51–100"… para ir directo a ese bloque.
- **Ir al episodio…**: teclado numérico grande (sirve también con los números del control); escribe por ejemplo `1015` y el foco queda en ese episodio, sin importar la temporada.

## Reproducción y continuidad

1. Al pulsar un episodio se buscan sus servidores (incluidos los que vienen dentro de agregadores como *darkanimes* o *animeav1*) y **se comprueban todos a la vez** (10 s máximo): solo aparecen arriba los que **funcionan**; los caídos o borrados quedan en "Ver no disponibles".
2. Si ya elegiste un servidor antes en ese sitio (el **preferido**), arranca directo con él. Si no, eliges uno con OK y queda recordado.
3. El video se pone **a pantalla completa** solo (se ocultan anuncios y ventanas emergentes). Si un servidor no arranca en 25 s, se prueba el siguiente que funcione, con un aviso.
4. Si ya habías visto parte del episodio, **continúa en el minuto guardado** (5 s antes).
5. Al terminar, cuenta atrás de 10 s y **pasa al siguiente episodio** (OK = "Ver ahora", o "Cancelar"). En el último disponible avisa que no hay más.

Algunos servidores recuerdan el minuto y muestran un recuadro de **"¿continuar viendo desde…?"** que no se puede tocar con el control. El reproductor lo responde solo (pulsa "Continuar") o, si no tiene botones, lo oculta. Si aparece otra capa que tapa el video, en el menú **↑** está **"Quitar recuadros"**.

Control remoto en el reproductor: **OK** pausa/reanuda · **← →** retrocede/avanza (los segundos de Ajustes) · **↑** menú (cambiar servidor, quitar recuadros, siguiente/anterior episodio, salir) · **↓** muestra la información · **Atrás** sale guardando el minuto. "Continuar viendo" (Inicio) e Historial abren el reproductor directamente.

## Búsqueda, filtros y voz

- **Voz**: en Búsqueda el botón 🎤 tiene el foco al entrar; lo dictado (español latino, `es-419`) se escribe en el campo y se busca al instante. En Inicio, el botón "🎤 Voz" abre el dictado directamente. Si la TV no tiene reconocimiento de voz, los botones no aparecen.
- **Sitios**: "Todos los sitios" busca en paralelo (15 s máximo por sitio) o elige uno solo con su chip.
- **Filtros** (botón "Filtros"): con un sitio elegido se ven **sus filtros reales**, leídos de su web. En "Todos los sitios" solo los comunes (género, año, tipo, estado), traducidos al parámetro de cada sitio; si un sitio no puede aplicar un filtro se omite con un aviso.
- **Accesos rápidos**: "Emisión de esta temporada" y "Estrenos del año" aparecen solo si el sitio los permite (hoy, Latanime).
- La búsqueda por texto de **Latanime** está protegida por Cloudflare: por ahora muestra un aviso; su directorio con filtros sí funciona.
- **AnimeFLV**: el original cerró en 2026; se usa la copia **animeflv.or.at**, que no tiene filtros (solo búsqueda y "Todo el catálogo") y solo guarda los episodios recientes.
- **JKAnime** se usa por **jkanime.org** (mismo catálogo), porque jkanime.net pide la verificación de Cloudflare. Su directorio combina todos los filtros (género, tipo, estado, año, temporada, categoría, letra y orden).

## Anuncios, rastreo y ubicación

- **Anuncios y rastreadores:** el reproductor bloquea los dominios de `adBlockHosts` (en `config/sites.json`, editable sin reinstalar) y sus subdominios. Ahí están las redes de anuncios y ventanas emergentes de los servidores y rastreadores como Google Analytics/Tag Manager, Facebook, Yandex Metrica, Histats, Hotjar, Clarity y las estadísticas de los propios sitios.
- **Ubicación:**
  - la app no tiene permiso de ubicación;
  - en el reproductor, la geolocalización está desactivada y cualquier pedido de ubicación, cámara o micrófono de una página se rechaza.
- **WebRTC:** desactivado en todas las páginas del reproductor, para que no puedan ver la IP local de tu red.
- **"No rastrear":** todas las peticiones envían las señales `DNT: 1` y `Sec-GPC: 1`. Respetarlas es decisión de cada sitio.
- **Lo que la app no puede ocultar:** tu **IP pública**. Con ella cualquier sitio calcula tu ciudad o país aproximados. Para ocultarla hace falta una VPN instalada en la TV, que afecta a todo el aparato, no solo a la app.

## Reproductor nativo (Fase 5)

Cuando el servidor entrega el video directo, la app lo reproduce con el **reproductor nativo de Android (Media3/ExoPlayer)** en vez de abrir la página del servidor:
- **sin** anuncios, ventanas ni recuadros del servidor;
- arranca más rápido y responde al control como una app de TV;
- respeta la **calidad máxima** de Ajustes y retoma el minuto exacto.

- **Servidores con reproductor nativo:** el **HLS** de Anime Ninja y AnimeFLV, **Desu** y **Magi** de JKAnime, y **MP4Upload**.
- **En la lista:** cada servidor dice "Reproductor nativo" o "Reproductor web"; los nativos van primero, después del preferido.
- **Si el nativo falla:** el mismo servidor se abre en el reproductor web, desde el mismo minuto, con un aviso.
- **Detalle:** en [docs/reproductor-nativo.md](docs/reproductor-nativo.md).

| Servidores (nativo / web) | Reproductor nativo | Respaldo web si el nativo falla |
|---|---|---|
| ![Servidores](docs/capturas/fase5/20_servidores.png) | ![Nativo](docs/capturas/fase5/21_reproduciendo.png) | ![Respaldo](docs/capturas/fase5/23_respaldo_web.png) |

## Configuración remota (cuando un sitio cambia)

Los dominios, rutas, selectores CSS y filtros de cada sitio están en [`config/sites.json`](config/sites.json). La app:
1. al abrir, usa la copia guardada (o la incluida en el APK si no hay otra);
2. descarga la versión publicada desde la URL de Ajustes (por defecto `https://raw.githubusercontent.com/Yeikoromeo2/Anime-proyect-/main/config/sites.json`);
3. si la descarga falla, sigue con la que tenía.

Para corregir un sitio sin reinstalar: edita `config/sites.json`, **sube el número de `version`**, haz push a `main` y en la TV pulsa **Ajustes → Actualizar configuración**.

> Si el repositorio es **privado**, la URL `raw.githubusercontent.com` no funcionará desde la TV. En ese caso publica el JSON en un sitio público (por ejemplo un Gist público) y pon esa URL en Ajustes.

En el mismo archivo están las listas generales: `aggregators` (páginas que reúnen varios servidores), `deadVideoMarkers` (textos que indican un video borrado) y `adBlockHosts` (dominios de anuncios que el reproductor bloquea).

Cada dominio se puede corregir también a mano en **Ajustes → Sitios → (sitio) → Editar dominio**.

## Agregar un sitio nuevo

1. **Analizarlo** (como en la Fase 0): crea `docs/sites/<sitio>.md` con dominio, protección, búsqueda, filtros, selectores, detalle y servidores, y guarda páginas de ejemplo en `app/src/test/resources/fixtures/<sitio>/`.
2. **Configurarlo**: añade una entrada en `config/sites.json` (`id`, `name`, `baseUrl`, selectores, filtros) con `"status": "pendiente"` mientras no tenga proveedor.
3. **Proveedor**: si el sitio usa un motor ya soportado (por ejemplo WordPress con el tema DooPlay, como Anime Ninja), basta con poner `"engine": "dooplay"` y sus selectores en la configuración. Si no, crea `app/src/main/java/com/animehub/tv/providers/<motor>/` implementando `SiteProvider` (solo la lógica que no quepa en selectores) y regístralo en `ProviderRegistry`.
4. **Tests**: añade tests del proveedor contra los fixtures y cambia el estado a `"ok"`.

## Estructura

```
app/src/main/java/com/animehub/tv/
 ├─ ui/          Compose for TV: inicio, búsqueda, historial, ajustes, componentes
 ├─ domain/      modelos (AnimeCard, Episode, VideoServer, FilterGroup, WatchProgress…)
 ├─ data/
 │   ├─ db/      Room: favoritos, progreso, historial de búsquedas
 │   ├─ config/  configuración remota (descarga, caché, validación)
 │   ├─ settings/ DataStore (ajustes)
 │   ├─ net/     OkHttp, User-Agent, reintentos, cookies compartidas, detección de Cloudflare
 │   └─ log/     registro de los últimos 50 errores
 ├─ providers/   un proveedor por sitio (interfaz SiteProvider)
 ├─ extractors/  agregadores de servidores y comprobación de que el video existe
 ├─ playback/    servidores ordenados (preferido, funcionan) y control de la reproducción/continuidad
 ├─ webview/     reproductor web: bloqueo de anuncios y script inyectado (assets/player/inject.js)
 ├─ nativeplayer/ reproductor nativo (Media3/ExoPlayer) para los videos directos
 └─ util/        normalización de portadas, formatos
config/sites.json  configuración publicada (también se incluye en el APK)
docs/              análisis de cada sitio
```
