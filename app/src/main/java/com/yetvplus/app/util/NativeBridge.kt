package com.yetvplus.app.util

import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * 网页 -> 原生 的桥接对象，挂载为 window.MTVNative
 */
class NativeBridge(private val host: Host) {

    interface Host {
        fun onMediaPlayState(playing: Boolean)
        fun onWebFullscreen(fullscreen: Boolean)
        fun toast(message: String)
    }

    @JavascriptInterface
    fun onMediaPlay(state: Int) {
        host.onMediaPlayState(state == 1)
    }

    @JavascriptInterface
    fun onFullscreen(state: Int) {
        host.onWebFullscreen(state == 1)
    }

    @JavascriptInterface
    fun toast(message: String?) {
        if (message.isNullOrEmpty()) return
        host.toast(message)
    }

    companion object {

        /** 监听 video 播放/暂停与网页全屏，用于自动横屏与常亮 */
        const val MEDIA_HOOK_JS: String = """
(function () {
  try {
    if (window.__yetvplus_media_hooked) { return; }
    window.__yetvplus_media_hooked = true;

    function native() { return window.MTVNative; }

    var proto = (window.HTMLMediaElement && HTMLMediaElement.prototype) || null;
    if (proto) {
      var op = proto.play;
      var opa = proto.pause;
      proto.play = function () {
        var r = op.apply(this, arguments);
        try { if (native()) { native().onMediaPlay(1); } } catch (e) {}
        return r;
      };
      proto.pause = function () {
        var r = opa.apply(this, arguments);
        try { if (native()) { native().onMediaPlay(0); } } catch (e) {}
        return r;
      };
    }

    function onFsChange() {
      var el = document.fullscreenElement || document.webkitFullscreenElement;
      try { if (native()) { native().onFullscreen(el ? 1 : 0); } } catch (e) {}
    }
    document.addEventListener("fullscreenchange", onFsChange, true);
    document.addEventListener("webkitfullscreenchange", onFsChange, true);
  } catch (e) {}
})();
"""
    }
}

fun showToast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
