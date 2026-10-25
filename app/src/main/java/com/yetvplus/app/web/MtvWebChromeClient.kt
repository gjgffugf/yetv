package com.yetvplus.app.web

import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class MtvWebChromeClient(
    private val host: Host
) : WebChromeClient() {

    interface Host {
        fun onProgressChanged(newProgress: Int)
        fun onTitleReceived(title: String)
        fun onEnterFullscreen(view: View, callback: CustomViewCallback)
        fun onExitFullscreen()
        fun onChooseFile(callback: ValueCallback<Array<Uri>>, params: FileChooserParams)
        fun onJsAlert(message: String)
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        host.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!title.isNullOrEmpty()) {
            host.onTitleReceived(title)
        }
    }

    // HTML5 播放器全屏
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        host.onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        host.onExitFullscreen()
    }

    // window.open 复用当前 WebView，避免多窗口导致外部播放器链接丢失
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message
    ): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = view
        resultMsg.sendToTarget()
        return true
    }

    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: android.webkit.JsResult
    ): Boolean {
        host.onJsAlert(message)
        result.confirm()
        return true
    }

    // 管理面板等场景的上传文件
    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        host.onChooseFile(filePathCallback, fileChooserParams)
        return true
    }
}
