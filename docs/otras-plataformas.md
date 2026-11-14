# AnimeHub en Windows, iPhone/iPad y Samsung (Tizen): análisis

Estado: **solo análisis, sin desarrollar** (2026-11-10).

## Qué se puede aprovechar de la app actual

La app está hecha en Kotlin. Dos tercios del código no dependen de Android:
- los proveedores de los sitios (lectura de páginas con selectores);
- la configuración remota;
- la lógica del reproductor (elegir servidor, minuto, siguiente episodio);
- los extractores de video directo.

Lo que sí es de Android:
- la interfaz para TV (Compose for TV);
- el reproductor web (WebView de Android, con el script que quita anuncios);
- el reproductor nativo (ExoPlayer);
- la base de datos (Room) y los ajustes.

Con **Kotlin Multiplatform** y **Compose Multiplatform** la lógica se comparte y solo se rehace lo específico de cada sistema.

## 1. Windows — factible (recomendado como primero)

- **Cómo:** Compose Multiplatform para escritorio genera un instalador `.msi`/`.exe`. Se comparte la lógica y la interfaz se adapta a ratón y teclado (Compose Multiplatform usa Material 3 normal; Compose for TV no existe en escritorio).
- **Reproductor web:** un navegador incrustado (JCEF, el Chromium de escritorio). Permite inyectar el mismo script que quita anuncios y responde los avisos de "continuar".
- **Reproductor nativo:** VLC incrustado (vlcj), que reproduce HLS y MP4 con cabeceras.
- **Base de datos y ajustes:** Room y DataStore ya funcionan en escritorio.
- **Instalación:** libre, sin tienda ni pagos. GitHub Actions puede generar el instalador junto con el APK.
- **Esfuerzo:** medio-alto, unas 3 fases:
  1. separar la lógica en un módulo compartido;
  2. interfaz de escritorio;
  3. reproductores (JCEF + VLC).
- **Atajo que no recomiendo:** emuladores de Android (BlueStacks). Funcionan, pero pesan y se ven mal. El "Subsistema de Windows para Android" de Microsoft dejó de existir en 2025.

## 2. iPhone / iPad (iOS) — técnicamente factible; lo difícil es instalarla

- **Cómo:** Compose Multiplatform también funciona en iOS. La lógica se comparte; las piezas de Android se cambian por las de Apple:
  - **Leer páginas:** Jsoup (solo Java) → Ksoup.
  - **Red:** OkHttp → Ktor.
  - **Reproductor web:** WKWebView, que también permite inyectar el script en todos los marcos.
  - **Reproductor nativo:** AVPlayer, que reproduce HLS de forma nativa.
- **El problema es instalarla:** Apple no deja instalar apps fuera del App Store. Una app que reúne sitios de anime no pasaría su revisión. Opciones:
  - **Cuenta de desarrollador de Apple (99 USD al año):** instalación "ad hoc" en tus propios aparatos, registrados por su identificador, hasta 100. Es lo más estable.
  - **Cuenta gratuita + Sideloadly/AltStore:** gratis, pero la app caduca cada 7 días y hay que volver a firmarla con una computadora.
  - Las tiendas alternativas de iOS solo existen en la Unión Europea.
- **Para compilar** hace falta macOS. Sirven los Mac de GitHub Actions, sin comprar uno.
- **Esfuerzo:** alto, más la cuenta de Apple.

## 3. Samsung (Tizen) — posible pero muy limitado; no lo recomiendo

- **Cómo:** las apps de Tizen son páginas web empaquetadas (`.wgt`), escritas en HTML/JavaScript. No se puede reutilizar el código de Kotlin: sería una app nueva.
- **Instalación:** posible para uso personal, sin la tienda de Samsung. Hay que activar el "Modo desarrollador" en la TV y firmar con un certificado gratuito de Samsung (Tizen Studio desde una PC). Esa firma caduca y hay que renovarla de vez en cuando.
- **El límite principal:** en Tizen no hay un "WebView" en el que la app pueda inyectar su script en las páginas de los servidores. Sin eso:
  - no se pueden quitar los anuncios ni las ventanas emergentes;
  - no se pueden responder los avisos de "continuar";
  - no se puede manejar el reproductor del servidor con el control.

  Solo funcionarían los servidores de **video directo** (Desu y Magi de JKAnime, MP4Upload…). Es lo mismo que ya hace **DLNA desde la tablet** (0.9.0), sin instalar nada en la TV.
- **Esfuerzo:** alto (una app nueva) para un resultado parcial.

## Recomendación

1. **Samsung y LG:** usar lo que ya existe. DLNA desde la tablet para los episodios con video directo y "Duplicar pantalla" (Smart View) para el resto.
2. **Siguiente plataforma:** **Windows**, con Kotlin/Compose Multiplatform. Reutiliza la mayor parte y se instala libremente.
3. **iOS** después, si se acepta pagar la cuenta de Apple. Con lo hecho para Windows, la parte compartida ya estaría lista.
