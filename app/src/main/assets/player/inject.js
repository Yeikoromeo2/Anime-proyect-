/*
 * AnimeHub TV — script del reproductor web. Se inyecta al inicio de cada documento, también
 * dentro de los iframes (WebViewCompat.addDocumentStartJavaScript).
 *  - Busca el <video>, lo pone a pantalla completa (por encima de anuncios y capas) y le da play.
 *  - En los marcos sin video agranda el iframe principal para que el video ocupe la pantalla.
 *  - Envía a la app la posición y la duración cada 5 s y los eventos (reproduciendo, pausa, fin, error).
 *  - Recibe órdenes del control remoto (play/pausa, saltos) y las reenvía a los iframes.
 */
(function () {
  if (window.__animehub) return;
  window.__animehub = true;

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
  document.addEventListener('DOMContentLoaded', scan);
  if (document.readyState !== 'loading') scan();
  send({ type: 'frame', url: location.href });
})();
