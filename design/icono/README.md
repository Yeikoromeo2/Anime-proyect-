# Icono de AnimeHub

"Kitsu": un zorrito kitsune original, de ojos grandes al estilo anime, con una marca de "play" en la frente, sobre un fondo violeta con líneas de velocidad de manga y destellos.

- `mascota.js`: los dibujos en SVG (figura, fondo, icono clásico y banner de TV).
- `render.js`: genera los PNG de la app. Desde la raíz del repo:

  ```bash
  NODE_PATH=$(npm root -g) node design/icono/render.js app/src/main/res vista.png
  ```

Qué se genera:
- **Tablet y teléfono:** icono adaptable (`mipmap-anydpi-v26`, capas `ic_launcher_background` y `ic_launcher_foreground`). Cada lanzador le da su forma: círculo, cuadrado redondeado, etc. En Android 7 o anterior se usa `ic_launcher.png`, redondo.
- **Android TV / Google TV:** el banner `drawable-xhdpi/banner.png` (320×180 dp).
- **Dentro de la app:** `drawable-nodpi/mascota.png`, en la cabecera de Inicio.
