# AnimeHub TV — notas para quien continúe el proyecto

App Android (TV/Google TV + tablet), Kotlin, **interfaz y comentarios en español**. Uso personal (no Google Play).
Agrega 4 sitios: Latanime, Anime Ninja, AnimeFLV (animeflv.or.at), JKAnime (jkanime.org).

## Reglas del dueño (no negociables)
- **No borres funciones ni estructura sin avisar antes.** Si algo debe quitarse, se comunica primero.
- **Todas las preguntas para avanzar se hacen con el cuadro de selección (AskUserQuestion), nunca como texto al final de la respuesta.** Primero el informe corto de lo hecho y luego el cuadro con las opciones (máx. 4 preguntas por cuadro; si hay más, en cuadros seguidos). El dueño marca la opción.
- En uso normal **no se ve de qué sitio viene nada**: ni sitios, ni URLs, ni nombres de servidor. Todo es "AnimeHub TV". Solo el **modo administrador** (Ajustes → pulsar "Versión de la app" → PIN **041992**, 6 dígitos) los muestra.
- **Los errores no se muestran** al usuario; solo se guardan (`ErrorLogRepository.logIssue`, solo los accionables) y se leen en modo admin.
- En el reproductor sí se elige **versión por idioma** (Latino / Subtitulado / Castellano…), sin nombrar el servidor. Cada servidor lleva un número fijo por episodio, **"Reproductor N"** (`PlayerState.playerNumbers`): aparece como aviso breve al empezar o cambiar de servidor y en la lista (con "▶ En uso" en el que suena), sin nombrar el servidor.
- Botones con **iconos y pocas palabras**; nada de pantallas tipo "dashboard".
- El mismo anime de varios sitios/idiomas es **una sola tarjeta y una sola ficha**, pero **años o temporadas distintas son series distintas** (Ranma ½ 2024 ≠ Ranma ½ 1989; "Overlord 3" ≠ "Overlord 2").
- Las páginas con varias temporadas conservan su selector de temporadas.
- Tablet: todo táctil (gestos incluidos); TV: todo con D-pad. Los componentes de tv-material solo manejan D-pad, por eso existe `ui/components/Touch.kt`.
- Trabajar y publicar solo en la rama `ccr-56fde5aa-dflt5m`. No crear PR si no se pide. Sin identificadores de modelo en commits/código. Commits terminan con las líneas `Co-Authored-By` / `Claude-Session` que indique el sistema.
- No eludir Cloudflare/verificaciones de bots; no sondear hosts fuera de la lista permitida. La clave de firma del repo es la aprobada (no agregar secretos ANDROID_KEYSTORE_*).

## Arquitectura (resumen)
- Hilt, Room, DataStore, OkHttp, Jsoup, Coil 3, kotlinx.serialization, Media3 (ExoPlayer/HLS), Google Cast, NSD, DLNA (SSDP/SOAP).
- Configuración remota `config/sites.json` (versión actual 8): por sitio motor, selectores, filtros, escaparate, logo, color y `priority` (animeninja 30 > jkanime 20 > animeflv 10 > latanime 0; elige la versión principal). Global: agregadores, directStreams, adBlockHosts.
- Unificación: `util/TitleMatcher.kt` (normaliza tildes/macrones/idioma/temporada/romanos, `yearTag`, `same`, `group`), `util/Unify.kt` (`SameAnimeIndex`, `unifyCards/Showcase/Episodes`). `AnimeViewModel.mergeVersions` junta episodios de las otras versiones (no mezcla si la página principal ya tiene varias temporadas).
- Modo admin: `ui/Admin.kt` (`AdminMode`, `LocalAdmin`). Errores: `data/log/ErrorLogRepository.kt`.
- Reproductor: `playback/PlaybackController.kt` (silencioso, `tryAnother()`), `ui/player/*` (OSD, táctil, ExoPlayer nativo + WebView con `inject.js`).
- Enviar a otra pantalla: `remote/*` (servidor HTTP en la TV, descubrimiento NSD, `TargetDirectory`/`PlayTarget`), `cast/*`, `dlna/*`, `stream/StreamProxy.kt`.
- Inicio (diseño tomado de la arquitectura de XUPER-HYDRA-PORT-WEBOS, solo distribución): barra lateral de pestañas (hoy solo Anime, por lo que no se muestra; al enfocar una pestaña se cambia de página; en pantalla estrecha pasa a fila superior), accesos Explorar/Historial/Ajustes/versión arriba a la derecha (`HomeChrome.kt`, `LocalSidebarLeft`), mosaicos de tamaños mezclados (`Mosaic.kt`; foco = escala 1.05 + borde #3A66FE y título solo con foco). Anime conserva sus filas. `ui/home/*` (con `Intro.kt`); Explorar incluye la barra de búsqueda y 🎤 (`ui/search/ExploreScreen.kt`, `VoiceUi.kt`). No existe pantalla de Búsqueda aparte.

## Secciones retiradas (22-nov-2026)
- Por decisión del dueño se **eliminaron Retro, Pelis y Series y la TV en vivo anterior (listas iptv-org)** (fuentes lacartoons, gnula, serieslan, listas iptv-org, motores `generic-html` y `videoapi`, `data/iptv`, `ui/tv`, páginas de mosaicos de sección, sus pruebas y fixtures). La app es **solo anime**; la mecánica nueva (películas/series/TV) se diseña aparte.
- Se conservó lo genérico: `SiteConfig.section` y `ProviderRegistry.activeProvidersOf(section)`, `SiteConfig.tag` (menú Origen con 2+ fuentes etiquetadas), `ExploreRoute(section)`, `ui/home/Mosaic.kt`, `adBlockHosts/adBlockPatterns`, y `HomeTab`/`HomeSidebar`: con **una sola pestaña no hay barra lateral** (el logo va arriba a la izquierda); al añadir más pestañas la barra reaparece sola.
- El catálogo indú (Cinehindi) también se retiró por fallas de reproducción: no volver a añadirlo.
- Las credenciales de listas de terceros **nunca** se guardan en el repositorio ni en `config/sites.json` (es público): irían en Ajustes, solo en el dispositivo.

## TV en vivo (lista M3U del usuario, desde 0.16.0)
- Una sola lista: `http://<servidor>/get.php?username=…&password=…&type=m3u&output=ts|m3u8` (`IptvUrl`, `data/iptv/IptvRepository.kt`) con cabecera `User-Agent: VLC/3.0.20 LibVLC/3.0.20` y tiempos límite (15/30/45 s). Si el formato elegido falla se prueba el otro, y si `http` no responde, `https`. Copia en disco **6 h** (`TTL_MS`) y recarga manual en Ajustes → "Recargar lista de TV".
- **Credenciales**: servidor/usuario/contraseña se escriben en Ajustes (modo admin) y quedan solo en el dispositivo (DataStore). Para pruebas locales pueden ir en `local.properties` (`IPTV_HOST/USER/PASS`, ignorado por git) y solo entran en la compilación **debug**; la release y la de GitHub Actions salen sin credenciales. Nunca en el repositorio ni en `config/sites.json`.
- Reproducción (`ui/tv/LiveTvViewModel.markDead`): reconexión una vez si ya sonaba, luego formato alternativo de la señal (`.ts` ↔ `.m3u8`, `IptvUrl.swapStream`), y solo después "Sin señal".
- Pestaña **TV en vivo** del Inicio (`TvPage`): OK en la pestaña abre la TV con el último canal; la barra lateral reaparece porque hay 2 pestañas.
- Estado del servidor del dueño el 22-nov-2026: `get.php` respondía 404 (no es Xtream estándar); sí respondían `player_api.php?action=get_vod_categories|get_vod_streams|get_series|get_live_categories`. Sin verificar contra la lista real.

## Rendimiento (lecciones)
- Unir títulos (`TitleMatcher.group`) es lo más caro de las listas: usa caché por título y un índice por palabras; nunca llamarlo en getters que se evalúan en cada recomposición (en `HomeUiState` va con `by lazy`) ni en el hilo principal con listas grandes (Explorar usa `Dispatchers.Default`).
- La ficha se muestra en cuanto llega la versión principal; las otras versiones se mezclan después.

## Servidores del reproductor (cómo funciona)
- `ServerRepository.serversFlow`: abre los agregadores y comprueba los servidores **de a 3**, el preferido (o el primero de la lista) con 3 s de ventaja; emite la lista cada vez que uno responde. **Los que no responden o están caídos se descartan** (nunca se muestran ni se prueban). Plazos: 6 s por comprobación.
- `PlaybackController` arranca con el primero que sirve y deja que la lista siga creciendo; si falla el que suena salta al siguiente (o espera si aún llegan más). Lo que el usuario **elige a mano no se cambia solo**: tiene 3× de paciencia y, si no carga, se vuelve a la lista. Atrás en la lista vuelve al video (`cancelChoosing`).
- La preferencia se guarda como servidor + idioma (`encodePreferred`); los episodios siguientes arrancan con lo mismo.
- Reproductores web que exigen un toque real: `PlayerWebView.tap` inyecta un MotionEvent (los clics de un script no cuentan); `PlayerScreen` ya no toca nada solo: OK×3 abre el puntero (flechas mueven, OK toca, Atrás cierra).
- Atrás en el reproductor: una vez oculta los controles y avisa; la segunda (en 3 s) sale. Nunca sale a la primera.
- Al salir del reproductor se cancela la comprobación (`onExit`). La ficha completa del anime se pide en paralelo (`setEpisodes`).

## Pruebas y publicación
- Pruebas: Robolectric + Roborazzi (`app/src/test/.../ui/*`, base `TvUiTest`), `./gradlew --offline :app:testDebugUnitTest`. En pruebas de UI sin modo admin, nombres de sitio/servidor no existen: usar `admin.unlock(AdminMode.PIN)` si se necesitan.
- Robolectric: hacer `shadowOf(Looper.getMainLooper()).idle()` en los `waitUntil`; emitir eventos de navegación en Main.
- Publicar: push a la rama y ejecutar `android.yml` (workflow_dispatch, ref de la rama, `version` = x.y.z). Verificar el APK: firma SHA-256 `0499d0707c044fc33ca6f73b00734a16994e97bfd6575522d45f796f0954ad87` y `versionCode` mayor que el anterior (v0.15.0 = 112) con `apksigner`/`aapt2` de `/root/android-sdk/build-tools/37.0.0`.

## Pendiente / ideas aprobadas
Agregar sitio por URL (admin), chequeo semanal de salud de sitios, precarga del siguiente episodio, respaldo entre sitios si falla un servidor, más extractores de video directo (Voe, Filemoon, Streamwish, Mixdrop, dsvplay), búsqueda local del catálogo de Latanime, sincronizar historial, pantalla de bienvenida, selección automática de TVs con AnimeHub.
Plataformas futuras (solo análisis en `docs/otras-plataformas.md`): Windows primero, iOS después, Samsung Tizen no recomendado.
