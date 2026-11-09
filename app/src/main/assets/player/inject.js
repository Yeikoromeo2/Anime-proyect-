/*
 * AnimeHub TV — script del reproductor web. Se inyecta al inicio de cada documento, también
 * dentro de los iframes (WebViewCompat.addDocumentStartJavaScript).
 *  - Busca el <video>, lo pone a pantalla completa (por encima de anuncios y capas) y le da play.
 *  - En los marcos sin video agranda el iframe principal para que el video ocupe la pantalla.
 *  - Envía a la app la posición y la duración cada 5 s y los eventos (reproduciendo, pausa, fin, error).
 *  - Recibe órdenes del control remoto (play/pausa, saltos) y las reenvía a los iframes.
 *  - Responde solo a los recuadros de "¿continuar donde lo dejaste?" de algunos servidores
 *    (pulsa "Continuar" o los oculta) y, con la orden "cleanup", quita las capas que tapan el video.
 *  - Privacidad: las páginas no pueden pedir la ubicación ni usar WebRTC (que revela la IP local),
 *    y los "beacons" de estadísticas no salen.
 */
(function () {
  if (window.__animehub) return;
  window.__animehub = true;

  /* ---- Privacidad (antes de que cargue el código de la página) ---- */
  try {
    if (navigator.geolocation) {
      var deny = function (ok, fail) { if (typeof fail === 'function') fail({ code: 1, message: 'denied', PERMISSION_DENIED: 1 }); };
      Object.defineProperty(navigator, 'geolocation', {
        value: { getCurrentPosition: deny, watchPosition: function (ok, fail) { deny(ok, fail); return 0; }, clearWatch: function () {} },
        configurable: false,
      });
    }
  } catch (e) { /* sin efecto */ }
  try {
    ['RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCDataChannel'].forEach(function (name) {
      if (name in window) Object.defineProperty(window, name, { value: undefined, configurable: false });
    });
  } catch (e) { /* sin efecto */ }
  try { navigator.sendBeacon = function () { return true; }; } catch (e) { /* sin efecto */ }

  var isTop = window.top === window;
  var video = null;
  var styled = false;

  function send(msg) {
    try {
      msg.frame = isTop ? 'top' : location.host;
      if (window.AnimeHub && window.AnimeHub.postMessage) window.AnimeHub.postMessage(JSON.stringify(msg));
    } catch (e) { /* sin puente: nada que hacer */ }
  }

  function addStyle(css) {
    var root = document.head || document.documentElement;
    if (!root) return;
    var s = document.createElement('style');
    s.setAttribute('data-animehub', '1');
    s.textContent = css;
    root.appendChild(s);
  }

  var VIDEO_CSS =
    'html,body{background:#000!important;overflow:hidden!important}' +
    'video[data-animehub="1"]{position:fixed!important;top:0!important;left:0!important;' +
    'width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;' +
    'z-index:2147483647!important;background:#000!important;object-fit:contain!important;display:block!important;' +
    'visibility:visible!important;opacity:1!important;transform:none!important}';

  var IFRAME_CSS =
    'html,body{background:#000!important;overflow:hidden!important}' +
    'iframe[data-animehub="1"]{position:fixed!important;top:0!important;left:0!important;' +
    'width:100vw!important;height:100vh!important;z-index:2147483647!important;border:0!important;' +
    'display:block!important;visibility:visible!important;opacity:1!important}';

  function area(el) {
    var r = el.getBoundingClientRect();
    return Math.max(0, r.width) * Math.max(0, r.height);
  }

  function findVideo() {
    var list = Array.prototype.slice.call(document.querySelectorAll('video'));
    if (!list.length) return null;
    // El que tiene fuente; entre ellos, el más grande.
    var withSrc = list.filter(function (v) { return v.currentSrc || v.src || v.querySelector('source'); });
    var pool = withSrc.length ? withSrc : list;
    pool.sort(function (a, b) { return area(b) - area(a); });
    return pool[0];
  }

  var PLAY_SELECTORS = [
    '.vjs-big-play-button', '.jw-icon-display', '.jw-display-icon-container', '.plyr__control--overlaid',
    '.fp-play', '.play-button', '.playbtn', '#play', 'button[aria-label*="Play" i]', 'button[title*="Play" i]',
    '[class*="play-btn"]', '[class*="big-play"]'
  ];

  function clickPlayButtons() {
    PLAY_SELECTORS.forEach(function (sel) {
      try {
        var el = document.querySelector(sel);
        if (el) el.click();
      } catch (e) { /* selector no soportado */ }
    });
  }

  function tryPlay() {
    if (!video) return;
    try {
      var p = video.play();
      if (p && p.catch) p.catch(function () { clickPlayButtons(); });
    } catch (e) { clickPlayButtons(); }
  }

  function status(type) {
    if (!video) return;
    send({ type: type, pos: video.currentTime || 0, dur: isFinite(video.duration) ? video.duration : 0, paused: video.paused });
  }

  function attach(v) {
    if (video === v) return;
    video = v;
    v.setAttribute('data-animehub', '1');
    v.autoplay = true;
    v.controls = false;
    if (!styled) { addStyle(VIDEO_CSS); styled = true; }
    ['playing', 'pause', 'ended', 'loadedmetadata'].forEach(function (ev) {
      v.addEventListener(ev, function () { status(ev); });
    });
    v.addEventListener('error', function () { send({ type: 'error', code: v.error ? v.error.code : 0 }); });
    send({ type: 'video' });
    tryPlay();
  }

  var iframeStyled = false;
  function enlargeIframe() {
    var frames = Array.prototype.slice.call(document.querySelectorAll('iframe'));
    if (!frames.length) return;
    frames.sort(function (a, b) { return area(b) - area(a); });
    var main = frames[0];
    if (main.getAttribute('data-animehub') === '1') return;
    frames.forEach(function (f) { f.removeAttribute('data-animehub'); });
    main.setAttribute('data-animehub', '1');
    main.setAttribute('allow', 'autoplay; fullscreen');
    if (!iframeStyled) { addStyle(IFRAME_CSS); iframeStyled = true; }
  }

  function scan() {
    if (!video || !document.contains(video)) {
      video = null;
      var v = findVideo();
      if (v) attach(v);
    }
    if (!video) enlargeIframe();
    else if (video.paused && !video.__ahUserPaused && video.readyState >= 2) tryPlay();
  }

  /* ---- Recuadros de "continuar viendo" ----
   * Algunos servidores recuerdan el minuto y preguntan si seguir desde ahí. Con el control remoto no se
   * puede tocar ese recuadro (el reproductor web no recibe el foco), así que se responde solo:
   * se pulsa "Continuar"/"Sí"/"Resume"; si no tiene botones, se oculta. La app retoma el minuto guardado.
   */
  var PROMPT_TEXT = /((continu(ar|e)|reanudar|resume|retomar|seguir|volver)[^.?!]{0,40}(viendo|watching|reproducci|playback|video|v[ií]deo|donde|where|desde|from|minuto|time))|(where you left( off)?)|(donde (lo )?(dejaste|te quedaste))|(desde (el )?(minuto|punto))/i;
  var ACCEPT_TEXT = /^(s[ií]|yes|ok|aceptar|continuar|continue|reanudar|resume|retomar|seguir( viendo)?|continuar viendo|continue watching)(?![a-z\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1])/i;
  var CAPTIONS = /caption|subtit|text-track|cue/i;

  function visible(el) {
    if (!el || !el.getBoundingClientRect) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    var st = getComputedStyle(el);
    return st.display !== 'none' && st.visibility !== 'hidden' && parseFloat(st.opacity || '1') > 0.05;
  }

  function textOf(el) {
    return (el.textContent || '').replace(/\s+/g, ' ').trim();
  }

  function isOverlay(el) {
    var st = getComputedStyle(el);
    var cls = (typeof el.className === 'string' ? el.className : '') + ' ' + (el.id || '');
    return st.position === 'fixed' || st.position === 'absolute' || el.tagName === 'DIALOG' ||
      el.getAttribute('role') === 'dialog' || /modal|popup|dialog|overlay|resume|continue|prompt/i.test(cls);
  }

  /**
   * Busca recuadros de "continuar viendo" entre los elementos dados (o en toda la página).
   * Primero lo barato (textContent, que no obliga a recalcular la página); solo los candidatos
   * pasan por las comprobaciones de visibilidad y posición.
   */
  function dismissPrompts(roots) {
    var handled = 0;
    var nodes = roots || document.querySelectorAll('div,section,aside,dialog,form,[role="dialog"]');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (!el || el.nodeType !== 1 || el.__ahDone || el === video) continue;
      var raw = el.textContent || '';
      if (raw.length < 8 || raw.length > 600) continue;
      var text = raw.replace(/\s+/g, ' ').trim();
      if (text.length < 8 || text.length > 300 || !PROMPT_TEXT.test(text)) continue;
      if ((video && el.contains(video)) || !visible(el) || !isOverlay(el)) continue;
      el.__ahDone = true;
      var buttons = el.querySelectorAll('button,a,[role="button"],input[type="button"],input[type="submit"],[class*="btn"],[class*="button"]');
      var accept = null;
      for (var j = 0; j < buttons.length && !accept; j++) {
        var label = (buttons[j].textContent || buttons[j].value || buttons[j].getAttribute('aria-label') || '').replace(/\s+/g, ' ').trim();
        if (label.length <= 30 && ACCEPT_TEXT.test(label)) accept = buttons[j];
      }
      if (accept) { try { accept.click(); } catch (e) { /* sin efecto */ } }
      // Si sigue a la vista (o no tenía botón), se oculta para que no tape el video.
      setTimeout(function (box) { return function () { if (visible(box)) box.style.setProperty('display', 'none', 'important'); }; }(el), accept ? 600 : 0);
      handled++;
      send({ type: 'prompt', accepted: !!accept, text: text.slice(0, 80) });
    }
    return handled;
  }

  /* Solo se revisa lo que la página agrega o cambia (no toda la página cada segundo). */
  var pending = [];
  var pendingTimer = null;
  function queue(el) {
    if (!el || el.nodeType !== 1) return;
    pending.push(el);
    if (el.querySelectorAll && pending.length < 400) {
      var inner = el.querySelectorAll('div,section,aside,dialog,[role="dialog"]');
      for (var k = 0; k < inner.length && pending.length < 400; k++) pending.push(inner[k]);
    }
    if (!pendingTimer) {
      pendingTimer = setTimeout(function () {
        var batch = pending;
        pending = [];
        pendingTimer = null;
        dismissPrompts(batch);
      }, 300);
    }
  }
  try {
    new MutationObserver(function (mutations) {
      for (var m = 0; m < mutations.length; m++) {
        var mu = mutations[m];
        if (mu.type === 'childList') {
          for (var n = 0; n < mu.addedNodes.length; n++) queue(mu.addedNodes[n]);
        } else {
          queue(mu.target);
        }
      }
    }).observe(document.documentElement || document, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class', 'hidden'],
    });
  } catch (e) { /* sin MutationObserver: queda la revisión lenta */ }

  /* Orden "cleanup" (menú ↑ → "Quitar recuadros"): oculta las capas fijas que tapan el centro del
   * video y que no son el video, sus contenedores ni los subtítulos. */
  function cleanup() {
    dismissPrompts();
    var cx = window.innerWidth / 2, cy = window.innerHeight / 2, hidden = 0;
    var all = document.querySelectorAll('body *');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el === video || el.tagName === 'VIDEO' || el.tagName === 'IFRAME' && el.getAttribute('data-animehub') === '1') continue;
      if ((video && el.contains(video)) || (video && video.contains && video.contains(el))) continue;
      if (el.querySelector && el.querySelector('iframe[data-animehub="1"],video[data-animehub="1"]')) continue;
      var cls = (typeof el.className === 'string' ? el.className : '') + ' ' + (el.id || '');
      if (CAPTIONS.test(cls) || !visible(el)) continue;
      var st = getComputedStyle(el);
      if (st.position !== 'fixed' && st.position !== 'absolute') continue;
      var r = el.getBoundingClientRect();
      var coversCenter = r.left <= cx && r.right >= cx && r.top <= cy && r.bottom >= cy;
      if (!coversCenter && r.width * r.height < window.innerWidth * window.innerHeight * 0.15) continue;
      el.style.setProperty('display', 'none', 'important');
      hidden++;
    }
    send({ type: 'cleanup', hidden: hidden });
  }

  // Si no hay video en este marco y se pulsa OK: clic en el centro (botones de "play" propios).
  function clickCenter() {
    var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
    if (el && el.tagName !== 'IFRAME') el.click();
  }

  function relay(cmd) {
    Array.prototype.forEach.call(document.querySelectorAll('iframe'), function (f) {
      try { f.contentWindow.postMessage({ __animehub: cmd }, '*'); } catch (e) { /* marco inaccesible */ }
    });
  }

  function handle(cmd) {
    if (cmd.type === 'cleanup') { cleanup(); relay(cmd); return; }
    if (video) {
      switch (cmd.type) {
        case 'toggle':
          if (video.paused) { video.__ahUserPaused = false; tryPlay(); } else { video.__ahUserPaused = true; video.pause(); }
          break;
        case 'play': video.__ahUserPaused = false; tryPlay(); break;
        case 'pause': video.__ahUserPaused = true; video.pause(); break;
        case 'seekBy': video.currentTime = Math.max(0, (video.currentTime || 0) + cmd.seconds); break;
        case 'seekTo': video.currentTime = Math.max(0, cmd.seconds); break;
      }
      status('status');
    } else if (cmd.type === 'toggle' || cmd.type === 'play') {
      clickPlayButtons();
      if (!document.querySelector('iframe')) clickCenter();
    }
    relay(cmd);
  }

  window.addEventListener('message', function (e) {
    if (e.data && e.data.__animehub) handle(e.data.__animehub);
  });
  window.__animehubCmd = handle;

  // Sin ventanas emergentes de anuncios.
  try { window.open = function () { return null; }; } catch (e) { /* sin efecto */ }

  setInterval(function () { if (video) status('progress'); }, 5000);
  setInterval(scan, 1000);
  // Revisión completa de respaldo, poco frecuente (la normal es la del MutationObserver).
  setInterval(function () { dismissPrompts(); }, 8000);
  document.addEventListener('DOMContentLoaded', scan);
  if (document.readyState !== 'loading') scan();
  send({ type: 'frame', url: location.href });
})();
