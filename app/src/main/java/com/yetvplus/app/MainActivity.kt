package com.yetvplus.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextMenu
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yetvplus.app.util.BackgroundInjector
import com.yetvplus.app.util.NativeBridge
import com.yetvplus.app.util.Prefs
import com.yetvplus.app.util.showToast
import com.yetvplus.app.web.MtvWebChromeClient
import com.yetvplus.app.web.MtvWebViewClient

class MainActivity : AppCompatActivity(),
    NativeBridge.Host,
    MtvWebViewClient.Listener,
    MtvWebChromeClient.Host {

    private lateinit var prefs: Prefs
    private lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var fab: FloatingActionButton
    private lateinit var errorView: View
    private lateinit var fullscreenContainer: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private var appliedUa = ""
    private var appliedBase = ""
    private var currentUrl = ""
    private var lastImageUrl = ""
    private var mediaPlaying = false
    private var doubleBackPressed = false

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            val uris = if (result.resultCode == RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            } else {
                null
            }
            callback.onReceiveValue(uris)
            filePathCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        web = findViewById(R.id.webview)
        swipe = findViewById(R.id.swipe)
        progressBar = findViewById(R.id.progress)
        fab = findViewById(R.id.fab)
        errorView = findViewById(R.id.errorView)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)

        setupWebView()
        setupUi()

        appliedBase = prefs.homeUrl()
        web.loadUrl(resolveStartUrl(intent))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        web.loadUrl(resolveStartUrl(intent))
    }

    // ---------------- WebView ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            appliedUa = prefs.userAgent()
            userAgentString = appliedUa
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.addJavascriptInterface(NativeBridge(this), "MTVNative")
        web.webViewClient = MtvWebViewClient(this, prefs, this)
        web.webChromeClient = MtvWebChromeClient(this)

        web.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            download(url, contentDisposition, mimeType)
        }

        if (prefs.longPressSave) {
            registerForContextMenu(web)
        }
    }

    private fun setupUi() {
        swipe.isEnabled = prefs.pullRefresh
        swipe.setOnRefreshListener { web.reload() }

        fab.visibility = if (prefs.showFab) View.VISIBLE else View.GONE
        fab.setOnClickListener { showMenu() }

        findViewById<View>(R.id.btnRetry).setOnClickListener {
            errorView.visibility = View.GONE
            web.reload()
        }
        findViewById<View>(R.id.btnChangeServer).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        applyImmersive()
    }

    private fun showMenu() {
        val popup = PopupMenu(this, fab, Gravity.END)
        if (customView != null) {
            popup.menu.add(0, ID_EXIT_FULLSCREEN, 0, getString(R.string.menu_exit_fullscreen))
        }
        popup.menu.add(0, ID_REFRESH, 0, getString(R.string.menu_refresh))
        popup.menu.add(0, ID_HOME, 0, getString(R.string.menu_home))
        popup.menu.add(0, ID_SEARCH, 0, getString(R.string.menu_search))
        popup.menu.add(0, ID_LIVE, 0, getString(R.string.menu_live))
        popup.menu.add(0, ID_ADMIN, 0, getString(R.string.menu_admin))
        popup.menu.add(0, ID_SETTINGS, 0, getString(R.string.menu_settings))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_EXIT_FULLSCREEN -> exitVideoFullscreen()
                ID_REFRESH -> web.reload()
                ID_HOME -> web.loadUrl(prefs.homeUrl())
                ID_SEARCH -> web.loadUrl(prefs.page("/search"))
                ID_LIVE -> web.loadUrl(prefs.page("/live"))
                ID_ADMIN -> web.loadUrl(prefs.adminUrl())
                ID_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun injectBackground() {
        val js = BackgroundInjector.buildInstallJs(this)
        if (js == null) {
            web.evaluateJavascript(BackgroundInjector.CLEAR_JS, null)
            return
        }
        web.evaluateJavascript(js, null)
        handler.postDelayed({
            web.evaluateJavascript(BackgroundInjector.REINSTALL_JS, null)
        }, 700)
    }

    private fun resolveStartUrl(intent: Intent?): String {
        val data = intent?.dataString
        if (data.isNullOrEmpty()) return prefs.homeUrl()
        if (data.startsWith("yetvplus://")) {
            val path = data.removePrefix("yetvplus://")
            return when (path) {
                "home", "" -> prefs.homeUrl()
                "admin" -> prefs.adminUrl()
                else -> prefs.page("/$path")
            }
        }
        if (data.startsWith("http://") || data.startsWith("https://")) return data
        return prefs.homeUrl()
    }

    // ---------------- 生命周期 ----------------

    override fun onResume() {
        super.onResume()

        val ua = prefs.userAgent()
        if (ua != appliedUa) {
            appliedUa = ua
            web.settings.userAgentString = ua
            web.reload()
            return
        }
        if (prefs.homeUrl() != appliedBase) {
            appliedBase = prefs.homeUrl()
            web.loadUrl(appliedBase)
            return
        }

        swipe.isEnabled = prefs.pullRefresh
        fab.visibility = if (prefs.showFab && customView == null) View.VISIBLE else View.GONE
        applyImmersive()
        injectBackground()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) {
            exitVideoFullscreen()
            return
        }
        if (web.canGoBack()) {
            web.goBack()
            return
        }
        if (doubleBackPressed) {
            super.onBackPressed()
            return
        }
        doubleBackPressed = true
        showToast(this, getString(R.string.toast_press_back_again))
        handler.postDelayed({ doubleBackPressed = false }, 2000)
    }

    override fun onDestroy() {
        web.stopLoading()
        fullscreenContainer.removeAllViews()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    // ---------------- 网页回调 ----------------

    override fun onPageStarted(url: String) {
        currentUrl = url
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        errorView.visibility = View.GONE
    }

    override fun onPageFinished(url: String) {
        currentUrl = url
        progressBar.visibility = View.GONE
        swipe.isRefreshing = false
        injectBackground()
        if (prefs.mediaHook) {
            web.evaluateJavascript(NativeBridge.MEDIA_HOOK_JS, null)
        }
    }

    override fun onUrlChanged(url: String) {
        currentUrl = url
        web.evaluateJavascript(BackgroundInjector.REINSTALL_JS, null)
    }

    override fun onMainFrameError(url: String, code: Int) {
        progressBar.visibility = View.GONE
        swipe.isRefreshing = false
        errorView.visibility = View.VISIBLE
    }

    override fun onExternalFailed() {
        showToast(this, getString(R.string.toast_no_app_for_link))
    }

    override fun onProgressChanged(newProgress: Int) {
        progressBar.progress = newProgress
        progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        if (newProgress >= 40) {
            web.evaluateJavascript(BackgroundInjector.REINSTALL_JS, null)
        }
    }

    override fun onTitleReceived(title: String) {
        // 网页标题由页面自身渲染，无需额外处理
    }

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        fullscreenContainer.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenContainer.visibility = View.VISIBLE
        web.visibility = View.GONE
        fab.visibility = View.GONE
        hideSystemBars(true)
        if (prefs.autoLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onExitFullscreen() {
        val view = customView ?: return
        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        web.visibility = View.VISIBLE
        customCallback?.onCustomViewHidden()
        customView = null
        customCallback = null
        fab.visibility = if (prefs.showFab) View.VISIBLE else View.GONE
        hideSystemBars(prefs.immersive)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        if (prefs.keepScreenOn && !mediaPlaying) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun exitVideoFullscreen() {
        customCallback?.onCustomViewHidden()
        onExitFullscreen()
    }

    override fun onChooseFile(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ) {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        try {
            fileChooserLauncher.launch(params.createIntent())
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            showToast(this@MainActivity, "无法打开文件选择器")
        }
    }

    // ---------------- JS 桥接 ----------------

    override fun onMediaPlayState(playing: Boolean) {
        runOnUiThread {
            mediaPlaying = playing
            if (prefs.keepScreenOn) {
                if (playing) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            if (prefs.autoLandscape && customView == null) {
                requestedOrientation = if (playing) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER
                }
            }
        }
    }

    override fun onWebFullscreen(fullscreen: Boolean) {
        runOnUiThread {
            if (fullscreen) {
                hideSystemBars(true)
            } else {
                hideSystemBars(prefs.immersive)
            }
            if (prefs.autoLandscape && customView == null) {
                requestedOrientation = if (fullscreen) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER
                }
            }
        }
    }

    override fun toast(message: String) {
        runOnUiThread { showToast(this, message) }
    }

    override fun onJsAlert(message: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                showToast(this, message)
                return@runOnUiThread
            }
            MaterialAlertDialogBuilder(this)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    // ---------------- 工具 ----------------

    private fun applyImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, !prefs.immersive)
        hideSystemBars(prefs.immersive)
    }

    private fun hideSystemBars(hide: Boolean) {
        val controller = WindowInsetsControllerCompat(window, findViewById(R.id.root))
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun download(url: String, contentDisposition: String?, mimeType: String?) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
            if (!mimeType.isNullOrEmpty()) {
                request.setMimeType(mimeType)
            }
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrEmpty()) {
                request.addRequestHeader("Cookie", cookie)
            }
            request.addRequestHeader("User-Agent", web.settings.userAgentString)
            if (currentUrl.isNotEmpty()) {
                request.addRequestHeader("Referer", currentUrl)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            showToast(this, getString(R.string.toast_download_started))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (ignored: Exception) {
            }
            showToast(this, getString(R.string.toast_download_failed))
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (!prefs.longPressSave) return
        val result = web.hitTestResult
        val type = result.type
        if (type == WebView.HitTestResult.IMAGE_TYPE ||
            type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        ) {
            val url = result.extra
            if (url.isNullOrEmpty()) return
            lastImageUrl = url
            menu.setHeaderTitle("图片")
            menu.add(0, CTX_DOWNLOAD_IMAGE, 0, "下载图片")
            menu.add(0, CTX_COPY_IMAGE_URL, 0, "复制图片地址")
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            CTX_DOWNLOAD_IMAGE -> {
                if (lastImageUrl.isNotEmpty()) {
                    download(lastImageUrl, null, "image/*")
                }
                return true
            }

            CTX_COPY_IMAGE_URL -> {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("image_url", lastImageUrl))
                showToast(this, getString(R.string.toast_copied))
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    companion object {
        private const val ID_EXIT_FULLSCREEN = 90
        private const val ID_REFRESH = 1
        private const val ID_HOME = 2
        private const val ID_SEARCH = 3
        private const val ID_LIVE = 4
        private const val ID_ADMIN = 5
        private const val ID_SETTINGS = 6

        private const val CTX_DOWNLOAD_IMAGE = 101
        private const val CTX_COPY_IMAGE_URL = 102
    }
}
