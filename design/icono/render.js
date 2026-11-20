// Convierte los SVG de mascota.js en los PNG de la app (Chromium vía Playwright).
// Uso: node design/icono/render.js <carpeta res> [carpeta de vista previa]
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const svgs = require('./mascota');

const res = process.argv[2];
const preview = process.argv[3];
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

async function png(page, svg, w, h, file) {
  await page.setViewportSize({ width: w, height: h });
  await page.setContent(`<html><body style="margin:0;background:transparent">${svg.replace(/<svg /, `<svg style="display:block;width:${w}px;height:${h}px" `)}</body></html>`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  await page.screenshot({ path: file, omitBackground: true, clip: { x: 0, y: 0, width: w, height: h } });
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [d, k] of Object.entries(DENSITIES)) {
    const layer = Math.round(108 * k);
    const icon = Math.round(48 * k);
    await png(page, svgs.adaptiveBackground, layer, layer, `${res}/mipmap-${d}/ic_launcher_background.png`);
    await png(page, svgs.adaptiveForeground, layer, layer, `${res}/mipmap-${d}/ic_launcher_foreground.png`);
    await png(page, svgs.legacy, icon, icon, `${res}/mipmap-${d}/ic_launcher.png`);
    await png(page, svgs.legacy, icon, icon, `${res}/mipmap-${d}/ic_launcher_round.png`);
  }
  await png(page, svgs.banner, 640, 360, `${res}/drawable-xhdpi/banner.png`);
  // La mascota dentro de la app (cabecera de Inicio).
  await png(page, svgs.legacy, 192, 192, `${res}/drawable-nodpi/mascota.png`);
  if (preview) {
    // Cómo se ve con las formas de distintos lanzadores (círculo, cuadrado redondeado, "squircle").
    const fg = svgs.adaptiveForeground.replace(/<svg[^>]*>/, '').replace('</svg>', '');
    const bg = svgs.adaptiveBackground.replace(/<svg[^>]*>/, '').replace('</svg>', '');
    const masks = [
      '<circle cx="216" cy="216" r="144"/>',
      '<rect x="72" y="72" width="288" height="288" rx="64"/>',
      '<path d="M216,72 C352,72 360,80 360,216 C360,352 352,360 216,360 C80,360 72,352 72,216 C72,80 80,72 216,72 Z"/>',
    ];
    const tiles = masks.map((m, i) => `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240" viewBox="72 72 288 288">
      <defs><clipPath id="m${i}">${m}</clipPath></defs><g clip-path="url(#m${i})">${bg}${fg}</g></svg>`).join('');
    const html = `<div style="display:flex;gap:28px;align-items:center;padding:24px;background:#ECEAF3;font-family:sans-serif">${tiles}
      <div style="background:#111;padding:18px;border-radius:12px">${svgs.banner.replace(/<svg /, '<svg style="width:480px;height:270px;display:block" ')}</div></div>`;
    await page.setViewportSize({ width: 1340, height: 330 });
    await page.setContent(`<html><body style="margin:0">${html}</body></html>`);
    await page.screenshot({ path: preview });
  }
  await browser.close();
})();
