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
 * Minimal WebView activity used only for Microsoft OAuth login.
 *
 * Loads [LOGIN_URL] and monitors page navigation. As soon as the URL lands on
 * forza.net (i.e. OAuth is complete and the session cookies have been set),
 * it calls [CookieManager.flush], sets [RESULT_OK], and finishes — handing
 * control back to [MainActivity] which then launches [GalleryActivity].
 *
 * No JavaScript injection, no download buttons, no bottom bar.
 */
class LoginWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator

    companion object {
        private const val LOGIN_URL = "https://forza.net/myforza"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

        /** Trusted domains allowed to load inside the WebView. */
        private val TRUSTED = listOf(
            "forza.net", "microsoft.com", "microsoftonline.com",
            "live.com", "xbox.com", "windows.net", "xboxlive.com"
        )
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
            userAgentString      = MOBILE_UA
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
                if (isOnForzaSite(url)) {
                    // Delay slightly so the page's JavaScript has time to:
                    //  1. Set session cookies via Set-Cookie response headers / JS
                    //  2. Make initial API calls (letting shouldInterceptRequest capture the Bearer token)
                    webView.postDelayed({
                        CookieManager.getInstance().flush()
                        setResult(RESULT_OK)
                        finish()
                    }, 2000)
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
             * Intercept every outbound request from the WebView.
             * If the page's JavaScript calls api.forza.net with an Authorization header,
             * capture it so [ForzaApiService] can reuse the same Bearer token for
             * native HTTP calls — without needing cookie-based auth.
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
                return null // let WebView handle normally
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
