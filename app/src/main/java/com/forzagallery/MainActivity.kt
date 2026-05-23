package com.forzagallery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var toolbar: MaterialToolbar

    companion object {
        private const val TARGET_URL        = "https://forza.net/myforza"
        private const val PERM_REQUEST_CODE = 1001
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView     = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        toolbar     = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)

        requestStoragePermissionIfNeeded()
        setupWebView()
        setupBottomBar()
        setupBackHandler()

        if (savedInstanceState == null) {
            webView.loadUrl(TARGET_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERM_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            val denied = grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED
            if (denied) {
                Toast.makeText(
                    this,
                    "Permissão de armazenamento necessária para guardar fotos.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            databaseEnabled      = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(true)
            builtInZoomControls  = true
            displayZoomControls  = false
            userAgentString      = MOBILE_UA
            mixedContentMode     = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Enable system autofill (Google Password Manager, Samsung Pass, etc.)
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        webView.addJavascriptInterface(ForzaJsInterface(this), "ForzaApp")

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
                // Show the current host in the toolbar subtitle
                supportActionBar?.subtitle = Uri.parse(url).host ?: url
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                injectDownloadButtons(view)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                val trusted = listOf(
                    "forza.net", "microsoft.com", "microsoftonline.com",
                    "live.com", "xbox.com", "windows.net", "xboxlive.com"
                )
                return if (trusted.any { url.contains(it) }) {
                    false // let WebView handle trusted domains
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = false
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // Handle browser-triggered direct downloads
        webView.setDownloadListener { url, _, _, _, _ ->
            ForzaJsInterface(this).downloadPhoto(url)
        }
    }

    // ── JavaScript injection ──────────────────────────────────────────────────
    //
    // Injects into the page:
    //   • CSS styles for "⬇ Baixar", "↗ Partilhar" buttons and orientation badge
    //   • scan() — finds photo <img> and background-image elements
    //   • Orientation badge: "↔ Horizontal" or "↕ Vertical"
    //   • MutationObserver — re-runs scan() when the React SPA loads new content
    //   • Download button  → ForzaApp.downloadPhoto(url, isPortrait)
    //   • Share button     → ForzaApp.sharePhoto(url)
    // ─────────────────────────────────────────────────────────────────────────

    private fun injectDownloadButtons(view: WebView) {
        // language=JavaScript
        val js = """
(function () {
  'use strict';

  /* ── Inject styles once ─────────────────────────────────────────────── */
  if (!document.getElementById('fz-style')) {
    var s = document.createElement('style');
    s.id = 'fz-style';
    s.textContent = [
      /* Download button — M3 Filled Tonal style */
      '.fz-dl-btn {',
      '  position: absolute !important;',
      '  bottom: 8px !important; right: 8px !important;',
      '  display: inline-flex !important;',
      '  align-items: center !important; gap: 5px !important;',
      '  background: rgba(0,96,172,0.92) !important;',
      '  color: #fff !important; border: none !important;',
      '  border-radius: 20px !important;',
      '  padding: 6px 14px !important;',
      '  font-size: 12px !important; font-weight: 500 !important;',
      '  font-family: "Google Sans",Roboto,sans-serif !important;',
      '  letter-spacing: 0.1px !important;',
      '  cursor: pointer !important; z-index: 99999 !important;',
      '  box-shadow: 0 1px 2px rgba(0,0,0,.3),0 2px 6px rgba(0,0,0,.15) !important;',
      '  touch-action: manipulation !important;',
      '  -webkit-tap-highlight-color: transparent !important;',
      '  transition: opacity .1s !important;',
      '}',
      '.fz-dl-btn:active { opacity: .72 !important; }',
      /* Share button — M3 Tonal style, green accent to visually separate */
      '.fz-share-btn {',
      '  position: absolute !important;',
      '  bottom: 8px !important; right: 8px !important;',
      '  display: inline-flex !important;',
      '  align-items: center !important; gap: 5px !important;',
      '  background: rgba(19,109,84,0.92) !important;',
      '  color: #fff !important; border: none !important;',
      '  border-radius: 20px !important;',
      '  padding: 6px 14px !important;',
      '  font-size: 12px !important; font-weight: 500 !important;',
      '  font-family: "Google Sans",Roboto,sans-serif !important;',
      '  letter-spacing: 0.1px !important;',
      '  cursor: pointer !important; z-index: 99999 !important;',
      '  box-shadow: 0 1px 2px rgba(0,0,0,.3),0 2px 6px rgba(0,0,0,.15) !important;',
      '  touch-action: manipulation !important;',
      '  -webkit-tap-highlight-color: transparent !important;',
      '  transition: opacity .1s !important;',
      '}',
      '.fz-share-btn:active { opacity: .72 !important; }',
      /* When both buttons are present, push share btn to the left of download btn */
      '.fz-has-share .fz-dl-btn    { right: 8px !important; }',
      '.fz-has-share .fz-share-btn { right: calc(8px + 90px + 6px) !important; }',
      /* Orientation badge — M3 Chip style */
      '.fz-orient {',
      '  position: absolute !important;',
      '  top: 8px !important; left: 8px !important;',
      '  display: inline-flex !important;',
      '  align-items: center !important; gap: 3px !important;',
      '  background: rgba(26,28,30,0.7) !important;',
      '  color: #E2E2E6 !important;',
      '  border-radius: 8px !important;',
      '  padding: 3px 8px !important;',
      '  font-size: 10px !important; font-weight: 500 !important;',
      '  font-family: "Google Sans",Roboto,sans-serif !important;',
      '  letter-spacing: .4px !important;',
      '  z-index: 99998 !important; pointer-events: none !important;',
      '}'
    ].join('');
    document.head.appendChild(s);
  }

  /* ── Try to strip thumb/size params to get the best resolution ──────── */
  function hiRes(url) {
    try {
      var u = new URL(url);
      ['w','h','width','height','size','q','quality','s'].forEach(function (p) {
        u.searchParams.delete(p);
      });
      return u.toString()
        .replace(/_thumb\./gi, '.')
        .replace(/\/thumb\//gi, '/full/')
        .replace(/\/small\//gi, '/original/')
        .replace(/\/medium\//gi, '/original/');
    } catch (e) { return url; }
  }

  /* ── Attach badge + buttons to a container element ─────────────────── */
  function attach(container, src, isPortrait) {
    if (!src || src.startsWith('data:') || src.startsWith('blob:')) return;
    if (container.querySelector('.fz-dl-btn')) return;   // already done

    var cs = window.getComputedStyle(container);
    if (cs.position === 'static') container.style.position = 'relative';
    if (cs.overflow   === 'hidden')  container.style.overflow = 'visible';

    /* Mark container so CSS can offset the two buttons side-by-side */
    container.classList.add('fz-has-share');

    /* Orientation badge */
    var badge = document.createElement('span');
    badge.className = 'fz-orient';
    badge.textContent = isPortrait ? '\u2195 Vertical' : '\u2194 Horizontal';
    container.appendChild(badge);

    /* ── Share button (left of download button) ─────────────────────── */
    var shareBtn = document.createElement('button');
    shareBtn.className = 'fz-share-btn';
    shareBtn.innerHTML =
      '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14"' +
      ' viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">' +
      '<path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7' +
      's-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34' +
      '-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0' +
      '-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08' +
      '.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92' +
      '-2.92z"/></svg> Partilhar';
    shareBtn.title = 'Partilhar foto via WhatsApp, Telegram\u2026';

    shareBtn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      try {
        window.ForzaApp.sharePhoto(hiRes(src));
      } catch (err) {
        alert('Erro ao partilhar: ' + err);
      }
    }, true);

    container.appendChild(shareBtn);

    /* ── Download button with inline SVG download arrow ─────────────── */
    var btn = document.createElement('button');
    btn.className = 'fz-dl-btn';
    btn.innerHTML =
      '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14"' +
      ' viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">' +
      '<path d="M19 9h-4V3H9v6H5l7 7 7-7zm-8 2V5h2v6h1.17' +
      'L12 13.17 9.83 11H11zm-6 7v2h14v-2H5z"/></svg> Baixar';
    btn.title = isPortrait
      ? 'Guardar foto vertical na galeria'
      : 'Guardar foto horizontal na galeria';

    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      try {
        window.ForzaApp.downloadPhoto(hiRes(src), isPortrait);
      } catch (err) {
        alert('Erro ao iniciar download: ' + err);
      }
    }, true);

    container.appendChild(btn);
  }

  /* ── Scan the page for photo elements ───────────────────────────────── */
  function scan() {
    /* Strategy 1 — <img> elements */
    document.querySelectorAll('img').forEach(function (img) {
      if (!img.src || !img.offsetParent) return;
      var w = img.naturalWidth  || img.clientWidth  || img.offsetWidth;
      var h = img.naturalHeight || img.clientHeight || img.offsetHeight;
      if (w < 80 || h < 80) return;

      var lo = img.src.toLowerCase();
      if (lo.includes('icon') || lo.includes('logo') ||
          lo.includes('avatar') || lo.includes('sprite') ||
          lo.includes('banner')) return;

      /* isPortrait from actual pixel dimensions */
      var portrait = h > w;

      var wrap = img.closest(
        '[class*="photo"],[class*="Photo"],' +
        '[class*="card"],[class*="Card"],' +
        '[class*="gallery"],[class*="Gallery"],' +
        '[class*="thumb"],[class*="Thumb"],' +
        '[class*="screenshot"],[class*="Screenshot"],' +
        '[class*="item"],[class*="Item"],' +
        '[class*="tile"],[class*="Tile"]'
      ) || img.parentElement;

      if (wrap && wrap !== document.body) attach(wrap, img.src, portrait);
    });

    /* Strategy 2 — CSS background-image */
    document.querySelectorAll(
      '[class*="photo"],[class*="Photo"],' +
      '[class*="gallery"],[class*="Gallery"],' +
      '[class*="screenshot"],[class*="Screenshot"],' +
      '[class*="thumb"],[class*="Thumb"]'
    ).forEach(function (el) {
      var bg = window.getComputedStyle(el).backgroundImage;
      var m  = bg && bg.match(/url\(["']?([^"')]+)["']?\)/);
      if (!m || !m[1] || m[1].startsWith('data:')) return;
      var portrait = el.offsetHeight > el.offsetWidth;
      attach(el, m[1], portrait);
    });
  }

  /* ── Run scan immediately and after lazy-load delays ───────────────── */
  scan();
  setTimeout(scan,  800);
  setTimeout(scan, 2200);
  setTimeout(scan, 4500);

  /* ── Watch for SPA route changes / infinite scroll ─────────────────── */
  if (!window.__fzObs) {
    window.__fzObs = new MutationObserver(function (muts) {
      if (muts.some(function (m) { return m.addedNodes.length > 0; })) {
        clearTimeout(window.__fzT);
        window.__fzT = setTimeout(scan, 350);
      }
    });
    window.__fzObs.observe(document.body, { childList: true, subtree: true });
  }
}());
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    // ── Bottom toolbar ────────────────────────────────────────────────────────

    private fun setupBottomBar() {
        // ← Back: navigate to previous WebView page
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
            else Toast.makeText(this, getString(R.string.no_previous_page), Toast.LENGTH_SHORT).show()
        }

        // 🏠 Home: load myForza
        findViewById<MaterialButton>(R.id.btnHome).setOnClickListener {
            webView.loadUrl(TARGET_URL)
        }

        // ↺ Refresh: reload page (JS injection re-runs on page finish)
        findViewById<MaterialButton>(R.id.btnRefresh).setOnClickListener {
            webView.reload()
        }

        // 🖼 Gallery: open the device gallery showing saved Forza photos
        findViewById<MaterialButton>(R.id.btnGallery).setOnClickListener {
            openLocalGallery()
        }
    }

    private fun openLocalGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/external/images/media"), "image/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(intent) }.onFailure {
            val chooser = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivity(Intent.createChooser(chooser, getString(R.string.open_gallery)))
        }
    }

    // ── Back gesture / hardware button ────────────────────────────────────────

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
