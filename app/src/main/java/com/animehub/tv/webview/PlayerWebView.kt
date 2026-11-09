package com.animehub.tv.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.animehub.tv.data.net.Http
import java.io.ByteArrayInputStream

/**
 * WebView del reproductor de respaldo (opción B):
 * - inyecta `assets/player/inject.js` al inicio de cada documento, también en los iframes;
 * - recibe sus mensajes (posición, duración, fin…) con addWebMessageListener;
 * - bloquea ventanas emergentes, dominios de anuncios y navegaciones a otros sitios;
 * - no toma el foco: las teclas del control las maneja la app y las manda como órdenes.
 */
@SuppressLint("SetJavaScriptEnabled")
class PlayerWebView(
    context: Context,
    private val adHosts: Set<String>,
    private val onMessage: (String) -> Unit,
) {
    /** Contenedor: la WebView y, encima, la vista de pantalla completa si el sitio la pide. */
    val container = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    private val script = context.assets.open("player/inject.js").bufferedReader().use { it.readText() }
    private var allowedHost: String? = null
    private var customView: View? = null
    private val injectsInFrames = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }.getOrDefault(false)
    private val hasMessageListener = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }.getOrDefault(false)

    val webView: WebView = WebView(context).apply {
        isFocusable = false
        isFocusableInTouchMode = false
        setBackgroundColor(Color.BLACK)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = Http.DEFAULT_USER_AGENT
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true) // y onCreateWindow devuelve false: no se abre ninguna
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Privacidad: las páginas de los servidores no pueden pedir la ubicación.
            setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        if (injectsInFrames) WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*"))
        if (hasMessageListener) {
            WebViewCompat.addWebMessageListener(this, "AnimeHub", setOf("*")) { _, message, _, _, _ ->
                message.data?.let(onMessage)
            }
        } else {
            addJavascriptInterface(LegacyBridge(), "AnimeHub")
        }
        webViewClient = Client()
        webChromeClient = Chrome()
    }

    init {
        container.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun load(url: String, referer: String?) {
        allowedHost = Uri.parse(url).host
        val headers = referer?.let { mapOf("Referer" to it) } ?: emptyMap()
        webView.loadUrl(url, headers)
    }

    /** Orden al script: {"type":"toggle"}, {"type":"seekBy","seconds":10}… */
    fun command(json: String) {
        webView.evaluateJavascript("window.__animehubCmd && window.__animehubCmd($json)", null)
    }

    fun stop() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        allowedHost = null
    }

    fun destroy() {
        runCatching {
            webView.stopLoading()
            container.removeAllViews()
            webView.destroy()
        }
    }

    /** Sin addWebMessageListener: puente clásico (solo el marco principal). */
    private inner class LegacyBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            webView.post { onMessage(message) }
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val host = request.url.host ?: return null
            return if (isAd(host)) WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme != "http" && uri.scheme != "https") return true // intent://, market://…
            if (!request.isForMainFrame) return false
            // Las redirecciones del propio servidor se permiten (algunos cambian de dominio);
            // los saltos que provoca un anuncio, no.
            if (request.isRedirect) return false
            val host = uri.host ?: return true
            return !sameSite(host, allowedHost)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            // Sin inyección en marcos: al menos el marco principal.
            if (!injectsInFrames && url != null && url != "about:blank") view.evaluateJavascript(script, null)
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false

        /** Ubicación: siempre denegada, sin preguntar. */
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }

        /** Cámara, micrófono u otros permisos que pida una página: denegados. */
        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.deny()
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView?.let { container.removeView(it) }
            customView = view
            container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        override fun onHideCustomView() {
            customView?.let { container.removeView(it) }
            customView = null
        }
    }

    private fun isAd(host: String): Boolean = adHosts.any { host == it || host.endsWith(".$it") }

    companion object {
        /** Mismo sitio = mismo dominio registrable (los dos últimos niveles). */
        fun sameSite(a: String, b: String?): Boolean {
            if (b == null) return false
            fun base(h: String) = h.lowercase().split('.').takeLast(2).joinToString(".")
            return base(a) == base(b)
        }
    }
}
