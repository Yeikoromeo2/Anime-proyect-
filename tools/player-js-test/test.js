// Prueba del script del reproductor (app/src/main/assets/player/inject.js) en Chromium.
// Dos orígenes locales: el "sitio" (8711) con un anuncio y un iframe pequeño, y el "servidor
// de video" (8712) con el <video> y otra capa de anuncio. Uso: node test.js
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const script = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/player/inject.js'), 'utf8');
const video = fs.readFileSync(path.join(__dirname, 'video.webm'));

const site = `<!doctype html><html><body style="margin:0;background:#fff">
  <div id="anuncio" style="position:fixed;inset:0;z-index:999999;background:rgba(255,0,0,.5)">ANUNCIO</div>
  <h1>Episodio 1</h1>
  <iframe id="player" src="http://127.0.0.1:8712/embed" width="400" height="225"></iframe>
  <iframe id="banner" src="about:blank" width="120" height="60"></iframe>
</body></html>`;
const embed = `<!doctype html><html><body style="margin:0">
  <div class="capa" style="position:absolute;inset:0;z-index:99999;background:rgba(0,0,255,.5)">CLIC AQUÍ</div>
  <video src="/video.webm" width="200" height="112"></video>
  <div class="jw-captions" style="position:absolute;left:0;right:0;bottom:10px;z-index:2147483647;color:#fff">Subtítulo</div>
  <script>window.__popup = window.open('http://anuncio.example/');</script>
  <script>
    // Como algunos servidores: tras unos segundos preguntan si continuar donde se dejó.
    setTimeout(function () {
      var box = document.createElement('div');
      box.id = 'resume';
      box.className = 'resume-box';
      box.style.cssText = 'position:fixed;left:30%;top:35%;width:40%;z-index:2147483647;background:#222;color:#fff;padding:20px';
      box.innerHTML = '<p>¿Quieres continuar viendo desde 05:12?</p><button id="no">Empezar de nuevo</button> <button id="si">Continuar</button>';
      document.body.appendChild(box);
      document.getElementById('si').onclick = function () { window.__resumed = true; box.remove(); };
      document.getElementById('no').onclick = function () { window.__restarted = true; box.remove(); };
      // Otro aviso sin botones.
      var note = document.createElement('div');
      note.id = 'nota';
      note.style.cssText = 'position:absolute;left:10px;top:10px;z-index:99999;background:#000;color:#fff';
      note.textContent = 'Retomando la reproducción desde donde lo dejaste';
      document.body.appendChild(note);
    }, 2500);
    // Una capa pegajosa que no es un aviso de continuar (se quita con "Quitar recuadros").
    setTimeout(function () {
      var sticky = document.createElement('div');
      sticky.id = 'pegajoso';
      sticky.style.cssText = 'position:fixed;inset:20% 20%;z-index:2147483647;background:rgba(255,255,0,.6)';
      sticky.textContent = 'Publicidad';
      document.body.appendChild(sticky);
    }, 3000);
  </script>
</body></html>`;

function serve(port, routes) {
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      const r = routes[req.url];
      if (!r) { res.writeHead(404); return res.end(); }
      const body = Buffer.isBuffer(r[1]) ? r[1] : Buffer.from(r[1]);
      // Peticiones parciales (Range), como cualquier servidor de video real: necesarias para saltar.
      const range = /bytes=(\d+)-(\d*)/.exec(req.headers.range || '');
      if (range) {
        const start = +range[1];
        const end = range[2] ? +range[2] : body.length - 1;
        res.writeHead(206, { 'Content-Type': r[0], 'Accept-Ranges': 'bytes', 'Content-Length': end - start + 1,
          'Content-Range': `bytes ${start}-${end}/${body.length}` });
        return res.end(body.subarray(start, end + 1));
      }
      res.writeHead(200, { 'Content-Type': r[0], 'Accept-Ranges': 'bytes', 'Content-Length': body.length });
      res.end(body);
    });
    srv.listen(port, '127.0.0.1', () => resolve(srv));
  });
}

let fails = 0;
function check(name, ok, extra = '') {
  console.log(`${ok ? 'OK  ' : 'FALLO'} ${name}${extra ? ' — ' + extra : ''}`);
  if (!ok) fails++;
}

(async () => {
  const s1 = await serve(8711, { '/': ['text/html', site] });
  const s2 = await serve(8712, { '/embed': ['text/html', embed], '/video.webm': ['video/webm', video] });
  const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  const messages = [];
  await page.exposeBinding('__ahReport', (source, m) => messages.push(JSON.parse(m)));
  // Igual que addWebMessageListener: el objeto AnimeHub existe en todos los marcos.
  await page.addInitScript(`window.AnimeHub = { postMessage: (m) => window.__ahReport(m) };`);
  await page.addInitScript(script);
  await page.goto('http://127.0.0.1:8711/');
  await page.waitForTimeout(6500);

  const frame = page.frames().find((f) => f.url().includes('8712/embed'));
  check('el iframe del reproductor existe', !!frame);
  const box = await page.locator('#player').boundingBox();
  check('el iframe ocupa toda la pantalla', box && box.width === 1280 && box.height === 720, JSON.stringify(box));
  const v = await frame.evaluate(() => {
    const el = document.querySelector('video');
    const r = el.getBoundingClientRect();
    return { marked: el.getAttribute('data-animehub'), w: r.width, h: r.height, z: getComputedStyle(el).zIndex,
      t: el.currentTime, paused: el.paused, popup: window.__popup };
  });
  check('el video se marca y ocupa todo su marco', v.marked === '1' && v.w === 1280 && v.h === 720, JSON.stringify(v));
  check('el video queda por encima de las capas', v.z === '2147483647');
  check('el video se reproduce solo', !v.paused && v.t > 1, `t=${v.t}`);
  check('window.open queda bloqueado', v.popup === null);
  const types = messages.filter((m) => m.frame === '127.0.0.1:8712').map((m) => m.type);
  check('envía "video", "playing" y "progress" desde el iframe', ['video', 'playing', 'progress'].every((t) => types.includes(t)), types.join(','));
  const prog = messages.filter((m) => m.type === 'progress').pop();
  check('el progreso trae posición y duración', prog && prog.dur > 19 && prog.pos > 0, JSON.stringify(prog));

  // Órdenes del control remoto: se mandan al marco principal y llegan al iframe.
  await page.evaluate(() => window.__animehubCmd({ type: 'pause' }));
  await page.waitForTimeout(400);
  check('pausa a través del iframe', await frame.evaluate(() => document.querySelector('video').paused));
  const before = await frame.evaluate(() => document.querySelector('video').currentTime);
  await page.evaluate(() => window.__animehubCmd({ type: 'seekBy', seconds: 5 }));
  await page.waitForTimeout(400);
  const after = await frame.evaluate(() => document.querySelector('video').currentTime);
  check('salto de +5 s', Math.abs(after - before - 5) < 0.6, `${before.toFixed(1)} → ${after.toFixed(1)}`);
  await page.evaluate(() => window.__animehubCmd({ type: 'seekTo', seconds: 2 }));
  await page.evaluate(() => window.__animehubCmd({ type: 'toggle' }));
  await page.waitForTimeout(1200);
  const resumed = await frame.evaluate(() => ({ p: document.querySelector('video').paused, t: document.querySelector('video').currentTime }));
  check('ir al segundo 2 y reanudar con OK', !resumed.p && resumed.t >= 2 && resumed.t < 5, JSON.stringify(resumed));
  // Que no reanude solo si el usuario pausó.
  await page.evaluate(() => window.__animehubCmd({ type: 'toggle' }));
  await page.waitForTimeout(2500);
  check('una pausa del usuario se respeta', await frame.evaluate(() => document.querySelector('video').paused));

  // Recuadro "¿continuar viendo?": se pulsa "Continuar" solo y el aviso sin botones se oculta.
  const prompt = await frame.evaluate(() => ({
    resumed: window.__resumed === true, restarted: window.__restarted === true,
    box: !!document.getElementById('resume'),
    note: getComputedStyle(document.getElementById('nota')).display,
  }));
  check('el recuadro de continuar se responde con "Continuar"', prompt.resumed && !prompt.restarted && !prompt.box, JSON.stringify(prompt));
  check('el aviso sin botones se oculta', prompt.note === 'none', prompt.note);
  check('avisa a la app del recuadro respondido', messages.some((m) => m.type === 'prompt' && m.accepted));

  // "Quitar recuadros": la capa pegajosa se va; el video y los subtítulos se quedan.
  check('la capa pegajosa sigue antes de pedir quitarla', await frame.evaluate(() => getComputedStyle(document.getElementById('pegajoso')).display !== 'none'));
  await page.evaluate(() => window.__animehubCmd({ type: 'cleanup' }));
  await page.waitForTimeout(500);
  const clean = await frame.evaluate(() => ({
    sticky: getComputedStyle(document.getElementById('pegajoso')).display,
    video: getComputedStyle(document.querySelector('video')).display,
    captions: getComputedStyle(document.querySelector('.jw-captions')).display,
  }));
  check('"Quitar recuadros" oculta la capa y deja video y subtítulos', clean.sticky === 'none' && clean.video !== 'none' && clean.captions !== 'none', JSON.stringify(clean));
  // Privacidad: sin ubicación, sin WebRTC y sin beacons de estadísticas, en todos los marcos.
  const privacy = await frame.evaluate(() => new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      () => resolve({ geo: 'permitida' }),
      (err) => resolve({ geo: err.code, rtc: typeof window.RTCPeerConnection, beacon: navigator.sendBeacon('http://127.0.0.1:8712/beacon', 'x') }),
    );
  }));
  check('la ubicación se deniega en el iframe', privacy.geo === 1, JSON.stringify(privacy));
  check('WebRTC no está disponible (no revela la IP local)', privacy.rtc === 'undefined');
  const topRtc = await page.evaluate(() => typeof window.RTCPeerConnection);
  check('tampoco en el marco principal', topRtc === 'undefined', topRtc);

  const outer = await page.evaluate(() => getComputedStyle(document.getElementById('player')).display);
  check('el iframe del reproductor no se oculta', outer !== 'none', outer);
  await page.screenshot({ path: path.join(__dirname, 'captura.png') });

  await browser.close();
  s1.close(); s2.close();
  console.log(fails ? `\n${fails} comprobaciones fallaron` : '\nTodas las comprobaciones pasaron');
  process.exit(fails ? 1 : 0);
})();
