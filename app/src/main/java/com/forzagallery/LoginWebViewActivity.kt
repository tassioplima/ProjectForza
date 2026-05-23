package com.forzagallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Minimal WebView used only for Microsoft OAuth login via forza.net.
 *
 * After the OAuth dance completes and the URL lands back on forza.net, we inject
 * JavaScript to extract the MSAL Bearer token from sessionStorage/localStorage
 * (where MSAL caches it after the OAuth code exchange). If found we store it in
 * [ForzaApiService.capturedAuthHeader] before finishing with RESULT_OK.
 *
 * [shouldInterceptRequest] also acts as an earlier opportunistic capture: if the
 * page's own JS makes an authenticated call to api.forza.net before we evaluate,
 * we grab the token there instead.
 */
class LoginWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator

    /** Guard so we only call finish() once. */
    private var loginComplete = false

    companion object {
        private const val LOGIN_URL = "https://forza.net/myforza"

        // Desktop UA gives a more stable OAuth flow than mobile on some tenants.
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

        /** Trusted domains allowed to load inside the WebView. */
        private val TRUSTED = listOf(
            "forza.net", "microsoft.com", "microsoftonline.com",
            "live.com", "xbox.com", "windows.net", "xboxlive.com"
        )

        /**
         * JavaScript that searches sessionStorage and localStorage for an MSAL
         * access-token entry and returns the raw JWT string (or empty string).
         *
         * MSAL Browser v2 stores tokens with keys ending in "-accesstoken-â€¦"
         * and the value is a JSON object whose [secret] field holds the JWT.
         */
        private val JS_EXTRACT_TOKEN = """
            (function() {
                try {
                    var stores = [sessionStorage, localStorage];
                    for (var i = 0; i < stores.length; i++) {
                        var s = stores[i];
                        var keys = Object.keys(s);
                        for (var j = 0; j < keys.length; j++) {
                            var k = keys[j];
                            if (k.toLowerCase().indexOf('accesstoken') >= 0) {
                                try {
                                    var v = JSON.parse(s.getItem(k));
                                    var t = v && (v.secret || v.access_token || v.value || v.token);
                                    if (t && typeof t === 'string' && t.length > 50) return t;
                                } catch(e) {}
                            }
                        }
                    }
                } catch(e2) {}
                return '';
            })();
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_webview)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        progressBar = findViewById(R.id.progressBar)
        webView     = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            databaseEnabled      = true
            userAgentString      = DESKTOP_UA
            mixedContentMode     = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.isIndeterminate = true
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                if (!loginComplete && isOnForzaSite(url)) {
                    // MSAL stores the token asynchronously after the OAuth code
                    // exchange â€” give it 1.5 s to settle, then extract via JS.
                    webView.postDelayed({ extractMsalToken(attempt = 1) }, 1500)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return !TRUSTED.any { url.contains(it) }
            }

            /**
             * Opportunistic capture: if the page JS makes an authenticated call
             * to any forza.net domain before we evaluate JS, grab the token here.
             */
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host ?: return null
                if (host.contains("forza.net")) {
                    request.requestHeaders["Authorization"]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { ForzaApiService.capturedAuthHeader = it }
                }
                return null
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.isIndeterminate = false
                    progressBar.progress = newProgress
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(LOGIN_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    /**
     * Evaluates [JS_EXTRACT_TOKEN] in the WebView context.
     * If a token is returned it is stored and we close.
     * If not, we retry up to 3 times (spaced 1.5 s apart).
     * After the last attempt we close regardless so the user isn't stuck.
     */
    private fun extractMsalToken(attempt: Int) {
        if (loginComplete) return
        webView.evaluateJavascript(JS_EXTRACT_TOKEN) { rawResult ->
            if (loginComplete) return@evaluateJavascript
            // evaluateJavascript wraps strings in quotes â€” strip them.
            val token = rawResult
                ?.trim('"', '\'', ' ')
                ?.takeIf { it.isNotBlank() && it != "null" && it.length > 50 }

            if (token != null) {
                // Got the MSAL Bearer token.
                ForzaApiService.capturedAuthHeader = "Bearer $token"
                completeLogin()
            } else if (attempt < 3) {
                // Not ready yet â€” retry after another 1.5 s.
                webView.postDelayed({ extractMsalToken(attempt + 1) }, 1500)
            } else {
                // Give up â€” close with whatever cookies/token we have.
                completeLogin()
            }
        }
    }

    private fun completeLogin() {
        if (loginComplete) return
        loginComplete = true
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun isOnForzaSite(url: String): Boolean {
        val lc = url.lowercase()
        return lc.contains("forza.net") &&
                !lc.contains("login") &&
                !lc.contains("microsoftonline") &&
                !lc.contains("live.com")
    }
}
