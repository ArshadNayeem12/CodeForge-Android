package com.codeforge.android

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : Activity(), InputManager.InputDeviceListener {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var connectionPanel: LinearLayout
    private lateinit var inputManager: InputManager

    private val editorUrl = "http://127.0.0.1:8080/"
    private val backgroundColor = Color.rgb(24, 24, 32)
    private val accentColor = Color.rgb(190, 150, 255)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var setupRunning = false
    @Volatile private var backendReady = false
    private var pageLoaded = false
    private var physicalKeyboardConnected = false
    private var physicalMouseConnected = false
    private var backendProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, mainHandler)

        val root = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
        }

        webView = object : WebView(this) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                val isTouchscreen =
                    (event.source and InputDevice.SOURCE_TOUCHSCREEN) ==
                        InputDevice.SOURCE_TOUCHSCREEN

                // Reject touchscreen taps/swipes but allow physical mouse events.
                if (isTouchscreen) return true
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            setBackgroundColor(backgroundColor)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true

            // Keep desktop-like layout; disable browser-style zoom gestures.
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    pageLoaded = false
                    updateGate()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageLoaded = true
                    updateGate()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        backendReady = false
                        showPanel(
                            "Cannot load the CodeForge editor. " +
                                "Check the backend log and retry."
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
            text = "Retry Setup"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(115, 65, 190))
            setOnClickListener {
                startSetup()
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

        checkPhysicalDevices()
        updateGate()
        startSetup()
    }

    private fun checkPhysicalDevices() {
        val devices: List<InputDevice> = InputDevice.getDeviceIds()
            .mapNotNull { deviceId -> InputDevice.getDevice(deviceId) }
            .filter { device -> !device.isVirtual }

        physicalKeyboardConnected = devices.any { device ->
            (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
        }

        physicalMouseConnected = devices.any { device ->
            (device.sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
        }

        updateGate()
    }

    private fun updateGate() {
        if (!::connectionPanel.isInitialized) return

        val devicesReady = physicalKeyboardConnected && physicalMouseConnected

        webView.visibility =
            if (backendReady && pageLoaded && devicesReady) View.VISIBLE
            else View.INVISIBLE

        when {
            !physicalKeyboardConnected && !physicalMouseConnected -> {
                showPanel("Connect a physical keyboard AND a physical mouse to use CodeForge.")
            }

            !physicalKeyboardConnected -> {
                showPanel("Physical keyboard not detected. Connect a keyboard to continue.")
            }

            !physicalMouseConnected -> {
                showPanel("Physical mouse not detected. Connect a mouse to continue.")
            }

            !backendReady -> {
                showPanel("Preparing CodeForge runtime and starting the editor backend...")
            }

            !pageLoaded -> {
                showPanel("Opening the CodeForge editor...")
            }

            else -> {
                connectionPanel.visibility = View.GONE
            }
        }
    }

    private fun showPanel(message: String) {
        if (!::connectionPanel.isInitialized) return
        statusText.text = message
        retryButton.visibility = if (setupRunning) View.GONE else View.VISIBLE
        connectionPanel.visibility = View.VISIBLE
    }

    private fun startSetup() {
        if (setupRunning) return

        setupRunning = true
        backendReady = false
        retryButton.visibility = View.GONE
        showPanel("Preparing CodeForge runtime...")

        executor.execute {
            try {
                val rootfs = RuntimeInstaller.install(applicationContext) { message ->
                    mainHandler.post {
                        statusText.text = message
                        connectionPanel.visibility = View.VISIBLE
                    }
                }

                val serverLog = File(filesDir, "codeforge-backend.log")
                val proot = File(applicationInfo.nativeLibraryDir, "libproot.so")

                if (!proot.exists()) {
                    throw IllegalStateException(
                        "Bundled PRoot executable is missing: ${proot.absolutePath}"
                    )
                }

                val node = File(
                    rootfs,
                    "root/code-server-4.141.0-linux-arm64/lib/node"
                )
                val serverScript = File(
                    rootfs,
                    "root/code-server-4.141.0-linux-arm64/bin/code-server"
                )

                if (!node.exists() || !serverScript.exists()) {
                    throw IllegalStateException(
                        "The runtime is missing code-server or its Node executable."
                    )
                }

                proot.setExecutable(true, false)

                val command = listOf(
                    proot.absolutePath,
                    "--link2symlink",
                    "-0",
                    "-r", rootfs.absolutePath,
                    "-b", "/dev",
                    "-b", "/proc",
                    "-b", "${rootfs.absolutePath}/root:/dev/shm",
                    "-w", "/root",
                    "/usr/bin/env", "-i",
                    "HOME=/root",
                    "PATH=/usr/local/sbin:/usr/local/bin:/bin:/usr/bin:/sbin:/usr/sbin:/usr/games:/usr/local/games",
                    "TERM=xterm-256color",
                    "LANG=C.UTF-8",
                    "TMPDIR=/tmp",
                    "/bin/bash", "-lc",
                    "mkdir -p /root/web; " +
                        "exec /root/code-server-4.141.0-linux-arm64/bin/code-server " +
                        "--bind-addr 127.0.0.1:8080 " +
                        "--auth none --disable-telemetry /root/web"
                )

                // If a previous backend is already answering, reuse it.
                if (!isServerListening()) {
                    val builder = ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(serverLog))

                    builder.environment()["LD_LIBRARY_PATH"] =
                        applicationInfo.nativeLibraryDir

                    backendProcess = builder.start()
                }

                var ready = false
                for (attempt in 0 until 180) {
                    if (isServerListening()) {
                        ready = true
                        break
                    }

                    if (backendProcess?.isAlive == false) {
                        break
                    }

                    Thread.sleep(500)
                }

                if (!ready) {
                    val tail = if (serverLog.exists()) {
                        serverLog.readText().takeLast(2500)
                    } else {
                        "No backend log was created."
                    }

                    throw IllegalStateException(
                        "The backend did not start. Log:\n$tail"
                    )
                }

                mainHandler.post {
                    setupRunning = false
                    backendReady = true
                    retryButton.visibility = View.GONE

                    if (!pageLoaded) {
                        webView.loadUrl(editorUrl)
                    }
                    updateGate()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    setupRunning = false
                    backendReady = false
                    showPanel(
                        "CodeForge startup failed:\n${e.message ?: "Unknown error"}\n\n" +
                            "Tap Retry Setup to try again."
                    )
                    retryButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun isServerListening(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 8080), 400)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        checkPhysicalDevices()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        checkPhysicalDevices()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        checkPhysicalDevices()
    }

    override fun onResume() {
        super.onResume()
        if (::inputManager.isInitialized) checkPhysicalDevices()

        // Keep the software keyboard hidden when returning to the editor.
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
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
        if (::inputManager.isInitialized) {
            inputManager.unregisterInputDeviceListener(this)
        }

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }

        executor.shutdownNow()
        super.onDestroy()
    }
}
