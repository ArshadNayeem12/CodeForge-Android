package com.codeforge.android

import android.app.Activity
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var connectionPanel: LinearLayout

    private val editorUrl = "http://127.0.0.1:8080/"

    private val backgroundColor = Color.rgb(24, 24, 32)
    private val accentColor = Color.rgb(190, 150, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val root = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
        }

        webView = WebView(this).apply {
            setBackgroundColor(backgroundColor)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    showConnectionPanel("Connecting to CodeForge backend...")
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    connectionPanel.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        showConnectionPanel(
                            "Cannot connect to code-server.\n\n" +
                            "Make sure the backend is running at 127.0.0.1:8080, " +
                            "then tap Retry."
                        )
                    }
                }
            }

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        connectionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 24, 36, 24)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "CodeForge"
            textSize = 34f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Preparing editor..."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        retryButton = Button(this).apply {
            text = "Retry Connection"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(115, 65, 190))
            setOnClickListener {
                showConnectionPanel("Connecting to CodeForge backend...")
                webView.loadUrl(editorUrl)
            }
        }

        connectionPanel.addView(title)
        connectionPanel.addView(statusText)
        connectionPanel.addView(retryButton)

        root.addView(webView)
        root.addView(
            connectionPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        if (savedInstanceState == null) {
            webView.loadUrl(editorUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun showConnectionPanel(message: String) {
        statusText.text = message
        connectionPanel.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
