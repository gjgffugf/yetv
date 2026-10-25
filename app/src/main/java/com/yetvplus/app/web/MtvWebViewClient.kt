package com.yetvplus.app.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yetvplus.app.util.Prefs
import java.io.ByteArrayInputStream
import java.util.Locale

class MtvWebViewClient(
    private val context: Context,
    private val prefs: Prefs,
    private val listener: Listener
) : WebViewClient() {

    interface Listener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onUrlChanged(url: String)
        fun onMainFrameError(url: String, code: Int)
        fun onExternalFailed()
    }

    private fun blockedHosts(): List<String> =
        prefs.adblockHosts.lineSequence()
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotEmpty() }
            .toList()

    private fun isBlocked(uri: Uri): Boolean {
        val host = (uri.host ?: "").lowercase(Locale.getDefault())
        if (host.isEmpty()) return false
        for (rule in blockedHosts()) {
            if (host == rule || host.endsWith(".$rule")) return true
        }
        return false
    }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        return handleUri(uri)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handleUri(Uri.parse(url))
    }

    /**
     * http(s) 交给 WebView 正常加载；
     * potplayer://、vlc://、intent:// 等第三方协议交给系统/外部播放器。
     */
    private fun handleUri(uri: Uri): Boolean {
        val scheme = (uri.scheme ?: "").lowercase(Locale.getDefault())
        return when (scheme) {
            "http", "https", "about", "blob", "data", "file", "javascript" -> false
            else -> openExternal(uri)
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            listener.onExternalFailed()
            true
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (prefs.adblockEnabled && isBlocked(request.url)) {
            return emptyResponse()
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        listener.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        listener.onPageFinished(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        listener.onUrlChanged(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            listener.onMainFrameError(request.url.toString(), error.errorCode)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            listener.onMainFrameError(request.url.toString(), errorResponse.statusCode)
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
        if (prefs.ignoreSsl) {
            handler.proceed()
        } else {
            super.onReceivedSslError(view, handler, error)
        }
    }
}
