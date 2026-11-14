# AnimeHub TV — notas para quien continúe el proyecto

App Android (TV/Google TV + tablet), Kotlin, **interfaz y comentarios en español**. Uso personal (no Google Play).
Agrega 4 sitios: Latanime, Anime Ninja, AnimeFLV (animeflv.or.at), JKAnime (jkanime.org).

## Reglas del dueño (no negociables)
- **No borres funciones ni estructura sin avisar antes.** Si algo debe quitarse, se comunica primero.
- En uso normal **no se ve de qué sitio viene nada**: ni sitios, ni URLs, ni nombres de servidor. Todo es "AnimeHub TV". Solo el **modo administrador** (Ajustes → pulsar "Versión de la app" → PIN **041992**, 6 dígitos) los muestra.
- **Los errores no se muestran** al usuario; solo se guardan (`ErrorLogRepository.logIssue`, solo los accionables) y se leen en modo admin.
- En el reproductor sí se elige **versión por idioma** (Latino / Subtitulado / Castellano…), sin nombrar el servidor.
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
- Inicio: `ui/home/*` (con `Intro.kt`); Explorar incluye la barra de búsqueda y 🎤 (`ui/search/ExploreScreen.kt`, `VoiceUi.kt`). No existe pantalla de Búsqueda aparte.

## Rendimiento (lecciones)
- Unir títulos (`TitleMatcher.group`) es lo más caro de las listas: usa caché por título y un índice por palabras; nunca llamarlo en getters que se evalúan en cada recomposición (en `HomeUiState` va con `by lazy`) ni en el hilo principal con listas grandes (Explorar usa `Dispatchers.Default`).
- La ficha se muestra en cuanto llega la versión principal; las otras versiones se mezclan después.

## Servidores del reproductor (cómo funciona)
- `ServerRepository.serversFlow`: abre los agregadores y comprueba los servidores **de a 3**, el preferido (o el primero de la lista) con 3 s de ventaja; emite la lista cada vez que uno responde. **Los que no responden o están caídos se descartan** (nunca se muestran ni se prueban). Plazos: 6 s por comprobación.
- `PlaybackController` arranca con el primero que sirve y deja que la lista siga creciendo; si falla el que suena salta al siguiente (o espera si aún llegan más). Lo que el usuario **elige a mano no se cambia solo**: tiene 3× de paciencia y, si no carga, se vuelve a la lista. Atrás en la lista vuelve al video (`cancelChoosing`).
- La preferencia se guarda como servidor + idioma (`encodePreferred`); los episodios siguientes arrancan con lo mismo.
- Atrás en el reproductor: una vez oculta los controles y avisa; la segunda (en 3 s) sale. Nunca sale a la primera.
- Al salir del reproductor se cancela la comprobación (`onExit`). La ficha completa del anime se pide en paralelo (`setEpisodes`).

## Pruebas y publicación
- Pruebas: Robolectric + Roborazzi (`app/src/test/.../ui/*`, base `TvUiTest`), `./gradlew --offline :app:testDebugUnitTest`. En pruebas de UI sin modo admin, nombres de sitio/servidor no existen: usar `admin.unlock(AdminMode.PIN)` si se necesitan.
- Robolectric: hacer `shadowOf(Looper.getMainLooper()).idle()` en los `waitUntil`; emitir eventos de navegación en Main.
- Publicar: push a la rama y ejecutar `android.yml` (workflow_dispatch, ref de la rama, `version` = x.y.z). Verificar el APK: firma SHA-256 `0499d0707c044fc33ca6f73b00734a16994e97bfd6575522d45f796f0954ad87` y `versionCode` mayor que el anterior (v0.10.2 = 71) con `apksigner`/`aapt2` de `/root/android-sdk/build-tools/37.0.0`.

## Pendiente / ideas aprobadas
Agregar sitio por URL (admin), chequeo semanal de salud de sitios, precarga del siguiente episodio, respaldo entre sitios si falla un servidor, más extractores de video directo (Voe, Filemoon, Streamwish, Mixdrop, dsvplay), búsqueda local del catálogo de Latanime, sincronizar historial, pantalla de bienvenida, selección automática de TVs con AnimeHub.
Plataformas futuras (solo análisis en `docs/otras-plataformas.md`): Windows primero, iOS después, Samsung Tizen no recomendado.
