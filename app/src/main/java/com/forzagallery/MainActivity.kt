package com.forzagallery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Login gateway.
 *
 * • If there are existing Forza session cookies → launches [GalleryActivity] immediately.
 * • Otherwise → shows the native login screen with "Entrar com Microsoft".
 * • Tapping the button → opens [LoginWebViewActivity] (OAuth WebView only).
 * • On successful login → launches [GalleryActivity].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var loginButton: MaterialButton
    private lateinit var loginProgress: CircularProgressIndicator

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            openGallery()
        } else {
            // User cancelled the OAuth WebView — restore the login button
            loginButton.visibility = View.VISIBLE
            loginProgress.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loginButton = findViewById(R.id.loginButton)
        loginProgress = findViewById(R.id.loginProgress)

        // Skip login screen if a session is already cached
        if (hasSession()) {
            openGallery()
            return
        }

        loginButton.setOnClickListener {
            loginButton.visibility = View.GONE
            loginProgress.visibility = View.VISIBLE
            loginLauncher.launch(Intent(this, LoginWebViewActivity::class.java))
        }
    }

    private fun openGallery() {
        startActivity(Intent(this, GalleryActivity::class.java))
        finish()
    }

    /** True when the WebView CookieManager holds any cookie for forza.net. */
    private fun hasSession(): Boolean =
        CookieManager.getInstance()
            .getCookie("https://forza.net")
            ?.isNotBlank() == true
}
