package com.animehub.tv.data.net

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object Http {
    /**
     * User-Agent de navegador real. Debe ser el MISMO en OkHttp y en el WebView para que
     * las cookies de Cloudflare (cf_clearance) sirvan en ambos. Es el que se usó en la Fase 0.
     */
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    const val ACCEPT_LANGUAGE = "es-419,es;q=0.9"
}

/** Añade User-Agent e idioma a todas las peticiones. */
class UserAgentInterceptor(private val userAgent: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header("User-Agent", userAgent())
        if (request.header("Accept-Language") == null) builder.header("Accept-Language", Http.ACCEPT_LANGUAGE)
        return chain.proceed(builder.build())
    }
}

/** Reintenta GET/HEAD ante errores de red o 5xx, con espera creciente (500 ms, 1 s, 2 s…). */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 500,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val idempotent = request.method == "GET" || request.method == "HEAD"
        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                val retryable = idempotent && response.code in RETRY_CODES && attempt < maxRetries
                if (!retryable) return response
                response.close()
            } catch (e: IOException) {
                if (!idempotent || attempt >= maxRetries || chain.call().isCanceled()) throw e
            }
            Thread.sleep(baseDelayMs shl attempt)
            attempt++
        }
    }

    private companion object {
        // 52x de Cloudflare no se reintenta: el origen está caído y solo alarga la espera.
        val RETRY_CODES = setOf(500, 502, 503, 504)
    }
}

/** Comparte las cookies entre OkHttp y el WebView (necesario para Cloudflare). */
class WebViewCookieJar : CookieJar {
    private val manager: CookieManager? by lazy { runCatching { CookieManager.getInstance() }.getOrNull() }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = manager ?: return
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = manager?.getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }
}
