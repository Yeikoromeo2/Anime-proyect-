// Icono de AnimeHub: "Kitsu", un zorrito kitsune original de ojos grandes al estilo anime, con una
// marca de "play" en la frente. Genera los SVG del icono adaptable (fondo y figura), el icono
// clásico redondo y el banner de TV. Se convierten a PNG con render.js.

/** La cara del zorro, centrada en (0,0), unos 230 px de ancho. */
function fox() {
  return `
  <defs>
    <linearGradient id="pelo" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFB15C"/><stop offset="1" stop-color="#FF7A2F"/>
    </linearGradient>
    <linearGradient id="iris" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#3B1D7A"/><stop offset=".55" stop-color="#7C5CFF"/><stop offset="1" stop-color="#00D4BE"/>
    </linearGradient>
    <linearGradient id="marca" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#FF7EB6"/><stop offset="1" stop-color="#C13BFF"/>
    </linearGradient>
  </defs>
  <!-- orejas -->
  <path d="M-92,-22 L-70,-128 L-16,-60 Z" fill="url(#pelo)" stroke="#2A1747" stroke-width="7" stroke-linejoin="round"/>
  <path d="M92,-22 L70,-128 L16,-60 Z" fill="url(#pelo)" stroke="#2A1747" stroke-width="7" stroke-linejoin="round"/>
  <path d="M-74,-38 L-64,-100 L-34,-62 Z" fill="#FFD6E8"/>
  <path d="M74,-38 L64,-100 L34,-62 Z" fill="#FFD6E8"/>
  <!-- cabeza con mechones laterales -->
  <path d="M-108,10 C-112,-40 -70,-78 0,-78 C70,-78 112,-40 108,10 C118,34 112,52 96,58
           C92,84 52,104 0,104 C-52,104 -92,84 -96,58 C-112,52 -118,34 -108,10 Z"
        fill="url(#pelo)" stroke="#2A1747" stroke-width="7" stroke-linejoin="round"/>
  <!-- hocico y mejillas blancas -->
  <path d="M-96,54 C-70,40 -44,46 -26,58 C-14,40 14,40 26,58 C44,46 70,40 96,54
           C88,82 50,100 0,100 C-50,100 -88,82 -96,54 Z" fill="#FFF6EE"/>
  <!-- marca de "play" en la frente -->
  <path d="M-12,-62 L18,-46 L-12,-30 Z" fill="url(#marca)" stroke="#FFF6EE" stroke-width="3" stroke-linejoin="round"/>
  <!-- ojos grandes con brillos -->
  <g>
    <ellipse cx="-44" cy="6" rx="27" ry="33" fill="#2A1747"/>
    <ellipse cx="-44" cy="12" rx="20" ry="25" fill="url(#iris)"/>
    <circle cx="-53" cy="-6" r="10" fill="#FFFFFF"/>
    <circle cx="-34" cy="24" r="4.5" fill="#FFFFFF"/>
    <ellipse cx="44" cy="6" rx="27" ry="33" fill="#2A1747"/>
    <ellipse cx="44" cy="12" rx="20" ry="25" fill="url(#iris)"/>
    <circle cx="35" cy="-6" r="10" fill="#FFFFFF"/>
    <circle cx="54" cy="24" r="4.5" fill="#FFFFFF"/>
  </g>
  <!-- rubor -->
  <ellipse cx="-78" cy="42" rx="15" ry="7" fill="#FF7EB6" opacity=".8"/>
  <ellipse cx="78" cy="42" rx="15" ry="7" fill="#FF7EB6" opacity=".8"/>
  <!-- nariz y boca "ω" -->
  <path d="M-8,50 L8,50 L0,59 Z" fill="#2A1747" stroke="#2A1747" stroke-width="3" stroke-linejoin="round"/>
  <path d="M-16,64 Q-8,74 0,64 Q8,74 16,64" fill="none" stroke="#2A1747" stroke-width="4" stroke-linecap="round"/>`;
}

/** Destello de cuatro puntas, estilo anime. */
function sparkle(x, y, r, color = '#FFFFFF', opacity = 1) {
  const k = r * 0.28;
  return `<path d="M${x},${y - r} Q${x + k},${y - k} ${x + r},${y} Q${x + k},${y + k} ${x},${y + r} Q${x - k},${y + k} ${x - r},${y} Q${x - k},${y - k} ${x},${y - r} Z" fill="${color}" opacity="${opacity}"/>`;
}

/** Fondo: degradado violeta → rosa con líneas de velocidad de manga y destellos. */
function background(w, h, cx, cy) {
  let lines = '';
  const n = 36;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 + (i % 2 ? 0.03 : 0);
    const r1 = Math.min(w, h) * (0.33 + (i % 3) * 0.04);
    const r2 = Math.max(w, h);
    const x1 = cx + Math.cos(a) * r1, y1 = cy + Math.sin(a) * r1;
    const x2 = cx + Math.cos(a) * r2, y2 = cy + Math.sin(a) * r2;
    lines += `<line x1="${x1.toFixed(1)}" y1="${y1.toFixed(1)}" x2="${x2.toFixed(1)}" y2="${y2.toFixed(1)}" stroke="#FFFFFF" stroke-opacity="${i % 3 === 0 ? 0.16 : 0.08}" stroke-width="${i % 2 ? 2 : 4}"/>`;
  }
  return `
  <defs>
    <radialGradient id="fondo" cx="${cx / w}" cy="${cy / h}" r="0.75">
      <stop offset="0" stop-color="#9A6BFF"/><stop offset=".55" stop-color="#5B2DB8"/><stop offset="1" stop-color="#25124F"/>
    </radialGradient>
    <linearGradient id="rosa" x1="0" y1="1" x2="1" y2="0">
      <stop offset="0" stop-color="#FF7EB6" stop-opacity=".45"/><stop offset=".5" stop-color="#FF7EB6" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <rect width="${w}" height="${h}" fill="url(#fondo)"/>
  <rect width="${w}" height="${h}" fill="url(#rosa)"/>
  ${lines}`;
}

const S = 432; // 108 dp × 4 (xxxhdpi)

/** Capa de fondo del icono adaptable (el lanzador recorta la forma: círculo, cuadrado redondeado…). */
const adaptiveBackground = `<svg xmlns="http://www.w3.org/2000/svg" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}">
  ${background(S, S, S / 2, S / 2)}
  ${sparkle(96, 110, 14, '#FFFFFF', 0.9)}${sparkle(340, 96, 10, '#FFD6E8', 0.9)}${sparkle(352, 330, 12, '#FFFFFF', 0.7)}${sparkle(84, 320, 8, '#9FF5EA', 0.8)}
</svg>`;

/** Capa de la figura: dentro de la zona segura (66 dp de 108). */
const adaptiveForeground = `<svg xmlns="http://www.w3.org/2000/svg" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}">
  <g transform="translate(216 238) scale(0.88)">${fox()}</g>
</svg>`;

/** Icono clásico (Android 7 o anterior): redondo, todo junto. */
const legacy = `<svg xmlns="http://www.w3.org/2000/svg" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}">
  <defs><clipPath id="c"><circle cx="216" cy="216" r="200"/></clipPath></defs>
  <g clip-path="url(#c)">${background(S, S, 216, 216)}
    ${sparkle(110, 120, 14)}${sparkle(330, 104, 10, '#FFD6E8')}${sparkle(340, 330, 11, '#FFFFFF', 0.7)}
    <g transform="translate(216 236) scale(1.22)">${fox()}</g>
  </g>
  <circle cx="216" cy="216" r="198" fill="none" stroke="#FFFFFF" stroke-opacity=".35" stroke-width="4"/>
</svg>`;

/** Banner de Android TV / Google TV: 320×180 dp (640×360 en xhdpi). */
const banner = `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">
  ${background(640, 360, 150, 180)}
  ${sparkle(54, 60, 12)}${sparkle(262, 52, 9, '#FFD6E8')}${sparkle(250, 300, 10, '#9FF5EA', .8)}${sparkle(600, 46, 10, '#FFFFFF', .7)}${sparkle(584, 312, 8, '#FFD6E8', .8)}
  <g transform="translate(150 196) scale(1.08)">${fox()}</g>
  <text x="300" y="186" font-family="'Noto Sans','DejaVu Sans',Arial,sans-serif" font-size="76" font-weight="900" fill="#FFFFFF"
        stroke="#2A1747" stroke-width="10" paint-order="stroke" letter-spacing="-1">Anime</text>
  <text x="300" y="262" font-family="'Noto Sans','DejaVu Sans',Arial,sans-serif" font-size="76" font-weight="900" fill="#FFFFFF"
        stroke="#2A1747" stroke-width="10" paint-order="stroke" letter-spacing="-1">Hub</text>
  <g transform="translate(492 212)">
    <rect x="0" y="0" width="88" height="54" rx="14" fill="#00D4BE" stroke="#2A1747" stroke-width="6"/>
    <text x="44" y="42" text-anchor="middle" font-family="'Noto Sans','DejaVu Sans',Arial,sans-serif" font-size="40" font-weight="900" fill="#2A1747">TV</text>
  </g>
</svg>`;

module.exports = { adaptiveBackground, adaptiveForeground, legacy, banner };
