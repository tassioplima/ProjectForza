package com.forzagallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
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

    /** True once the user has been redirected to a Microsoft login page. */
    private var hasSeenMsLogin = false

    /** True once the WebView navigated AWAY from forza.net to the MS OAuth page. */
    private var hasLeftForzaSite = false

    /**
     * JavaScript interface injected into the WebView.
     * Receives Bearer tokens from the fetch/XHR interceptor script.
     */
    private inner class NativeBridge {
        @android.webkit.JavascriptInterface
        fun onToken(authHeader: String) {
            Log.d(TAG, "NativeBridge.onToken called, length=${authHeader.length}")
            if (!loginComplete && authHeader.isNotBlank() && authHeader.length >= 20) {
                ForzaApiService.capturedAuthHeader = authHeader
                runOnUiThread { completeLogin() }
            }
        }
    }

    companion object {
        private const val TAG = "FZG_Login"
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
         * Injected into forza.net on page load.
         * Automatically finds and clicks the "Sign in" button so the user goes
         * straight to the Microsoft login page without any interaction on forza.net.
         */
        private val JS_AUTO_CLICK_SIGNIN = """
            (function() {
                if (window.__fgAutoClicked) return;
                function tryClick() {
                    var els = document.querySelectorAll('a, button');
                    for (var i = 0; i < els.length; i++) {
                        var t = (els[i].textContent || els[i].innerText || '').trim().toLowerCase();
                        if (t === 'sign in' || t === 'entrar' || t === 'signin') {
                            window.__fgAutoClicked = true;
                            els[i].click();
                            return true;
                        }
                    }
                    var link = document.querySelector(
                        'a[href*="signin"], a[href*="login"], a[href*="auth"], ' +
                        '[class*="signin"], [class*="login"], [id*="signin"], [id*="login"]'
                    );
                    if (link) {
                        window.__fgAutoClicked = true;
                        link.click();
                        return true;
                    }
                    return false;
                }
                if (!tryClick()) {
                    setTimeout(tryClick, 600);
                    setTimeout(tryClick, 1500);
                    setTimeout(tryClick, 3000);
                }
            })();
        """.trimIndent()

        /**
         * Injected into every forza.net page on load.
         * Wraps fetch and XHR so any Authorization: Bearer header is sent to
         * [NativeBridge.onToken] immediately, before we even look at storage.
         */
        private val JS_SETUP_INTERCEPTOR = """
            (function() {
                if (window.__fgIntercepted) return;
                window.__fgIntercepted = true;
                function send(a) { try { window.NativeBridge.onToken(a); } catch(e) {} }
                var oF = window.fetch;
                if (oF) window.fetch = function(r, o) {
                    try {
                        var h = (o || {}).headers || {};
                        var a = (h instanceof Headers) ? h.get('Authorization') : (h['Authorization'] || h['authorization'] || '');
                        if (a && a.indexOf('Bearer ') === 0) send(a);
                    } catch(e) {}
                    return oF.apply(this, arguments);
                };
                var oS = XMLHttpRequest.prototype.setRequestHeader;
                XMLHttpRequest.prototype.setRequestHeader = function(n, v) {
                    if ((n || '').toLowerCase() === 'authorization' && (v || '').indexOf('Bearer ') === 0) send(v);
                    return oS.apply(this, arguments);
                };
            })();
        """.trimIndent()

        /**
         * Scans sessionStorage and localStorage for a Bearer token:
         *  1. URL hash (implicit OAuth: #access_token=...)
         *  2. Keys containing auth/token/bearer/accesstoken
         *  3. Broad scan — JWT-shaped OR opaque (non-JWT) tokens
         * Returns "Bearer <token>" or empty string.
         */
        private val JS_EXTRACT_TOKEN = """
            (function() {
                function isToken(t) {
                    return t && typeof t === 'string' && t.length >= 20
                        && !t.includes(' ') && !t.includes('<') && !t.includes('{');
                }
                try {
                    var hash = window.location.hash;
                    if (hash && hash.indexOf('access_token=') >= 0) {
                        var m = hash.match(/access_token=([^&]+)/);
                        if (m && m[1] && isToken(decodeURIComponent(m[1]))) return 'Bearer ' + decodeURIComponent(m[1]);
                    }
                    var stores = [sessionStorage, localStorage];
                    for (var i = 0; i < stores.length; i++) {
                        var s = stores[i];
                        for (var j = 0; j < s.length; j++) {
                            var k = s.key(j);
                            var kl = (k || '').toLowerCase();
                            if (kl.indexOf('auth') >= 0 || kl.indexOf('token') >= 0 ||
                                kl.indexOf('bearer') >= 0 || kl.indexOf('accesstoken') >= 0 ||
                                kl.indexOf('access_token') >= 0) {
                                try {
                                    var raw = s.getItem(k);
                                    if (isToken(raw)) return 'Bearer ' + raw;
                                    var v = JSON.parse(raw);
                                    var props = ['secret','access_token','token','value','credential','bearer','accessToken','Authorization'];
                                    for (var p = 0; p < props.length; p++) {
                                        var t = v && v[props[p]];
                                        if (isToken(t)) return 'Bearer ' + t;
                                    }
                                } catch(e) {}
                            }
                        }
                    }
                    for (var i2 = 0; i2 < stores.length; i2++) {
                        var s2 = stores[i2];
                        for (var j2 = 0; j2 < s2.length; j2++) {
                            var k2 = s2.key(j2);
                            try {
                                var raw2 = s2.getItem(k2);
                                if (!raw2) continue;
                                if (isToken(raw2) && raw2.length > 20) return 'Bearer ' + raw2;
                                var v2 = JSON.parse(raw2);
                                if (v2 && typeof v2 === 'object') {
                                    var props2 = ['secret','access_token','token','value','credential','bearer','accessToken'];
                                    for (var p2 = 0; p2 < props2.length; p2++) {
                                        var t2 = v2[props2[p2]];
                                        if (isToken(t2) && t2.length > 20) return 'Bearer ' + t2;
                                    }
                                }
                            } catch(e2) {}
                        }
                    }
                } catch(e3) {}
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

        webView.addJavascriptInterface(NativeBridge(), "NativeBridge")

        // Hidden until the Microsoft login page loads — forza.net loads silently in background.
        webView.visibility = View.INVISIBLE

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.isIndeterminate = true
                progressBar.visibility = View.VISIBLE
                Log.d(TAG, "onPageStarted: $url")
                val lc = url.lowercase()
                if (lc.contains("microsoftonline") || lc.contains("login.live.com") || lc.contains("login.microsoft")) {
                    hasSeenMsLogin = true
                    hasLeftForzaSite = true
                    Log.d(TAG, "hasSeenMsLogin = true")
                    // Microsoft page detected — reveal the WebView.
                    webView.visibility = View.VISIBLE
                } else if (!isOnForzaSite(url)) {
                    hasLeftForzaSite = true
                }
                // Inject interceptor early on forza.net so API calls during page load are caught
                if (isOnForzaSite(url)) {
                    view.evaluateJavascript(JS_SETUP_INTERCEPTOR, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                Log.d(TAG, "onPageFinished: $url | hasMs=$hasSeenMsLogin isForzaSite=${isOnForzaSite(url)}")
                if (isOnForzaSite(url)) {
                    // Inject fetch/XHR interceptor so any authenticated API call
                    // from the page is caught immediately via NativeBridge.
                    view.evaluateJavascript(JS_SETUP_INTERCEPTOR, null)
                    if (!hasSeenMsLogin) {
                        // Auto-click the Sign In button — skips the forza.net splash page.
                        view.evaluateJavascript(JS_AUTO_CLICK_SIGNIN, null)
                    }
                }
                if (!loginComplete && hasSeenMsLogin && hasLeftForzaSite && isOnForzaSite(url)) {
                    // Returned to forza.net after MS OAuth — scan storage for token.
                    webView.postDelayed({ extractMsalToken(attempt = 1) }, 1500)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                val lc = url.lowercase()
                if (lc.contains("microsoftonline") || lc.contains("login.live.com") || lc.contains("login.microsoft")) {
                    hasSeenMsLogin = true
                }
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
        Log.d(TAG, "extractMsalToken attempt=$attempt")
        webView.evaluateJavascript(JS_EXTRACT_TOKEN) { rawResult ->
            if (loginComplete) return@evaluateJavascript
            val token = rawResult
                ?.trim('"', '\'', ' ')
                ?.takeIf { it.isNotBlank() && it != "null" && it.length > 50 }
            Log.d(TAG, "JS result attempt=$attempt found=${token != null}")

            if (token != null) {
                // Got the MSAL Bearer token — token already prefixed by JS.
                ForzaApiService.capturedAuthHeader = token
                completeLogin()
            } else if (attempt < 6) {
                // Not ready yet — retry every 2 s (up to 12 s total).
                webView.postDelayed({ extractMsalToken(attempt + 1) }, 2000)
            } else {
                // Storage scan exhausted with no token — the Forza token is opaque
                // (not a JWT) so it may only be in memory, not storage.
                // Keep the WebView open and rely on the fetch interceptor to fire
                // when the page makes an authenticated API call.
                Log.d(TAG, "extractMsalToken exhausted — keeping WebView open, waiting for fetch interceptor")
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
