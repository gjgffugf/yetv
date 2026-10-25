package com.yetvplus.app.util

import android.content.Context

/**
 * 背景图注入：把用户选择的图片作为网页底层背景，
 * 支持暗化 / 模糊 / 应用范围，并在 SPA 路由切换后自动重建。
 */
object BackgroundInjector {

    /** 页面内已安装脚本时的轻量重放调用 */
    const val REINSTALL_JS: String =
        "(function(){try{if(window.__yetvplus_bg_install){window.__yetvplus_bg_install();}}catch(e){}})();"

    /** 关闭背景图时清理残留样式与图层 */
    const val CLEAR_JS: String =
        "(function(){try{" +
            "var s=document.getElementById('__yetvplus_bg_style__');" +
            "if(s&&s.parentNode){s.parentNode.removeChild(s);}" +
            "var l=document.getElementById('__yetvplus_bg_layer__');" +
            "if(l&&l.parentNode){l.parentNode.removeChild(l);}" +
            "window.__yetvplus_bg_install=null;" +
            "}catch(e){}})();"

    fun buildInstallJs(context: Context): String? {
        val prefs = Prefs(context)
        if (!prefs.hasBackground()) return null

        val raw = prefs.bgImage.trim()
        if (raw.isEmpty()) return null

        val img = jsString(raw)
        val dim = prefs.bgDim.coerceIn(0f, 0.85f)
        val blur = prefs.bgBlur.coerceIn(0, 20)
        val scope = prefs.bgScope

        return JS
            .replace("__IMG__", img)
            .replace("__DIM__", dim.toString())
            .replace("__BLUR__", blur.toString())
            .replace("__SCOPE__", scope)
    }

    private fun jsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("\r", "")
        return "\"$escaped\""
    }

    private const val JS: String = """
(function () {
  try {
    var IMG = __IMG__;
    var DIM = __DIM__;
    var BLUR = __BLUR__;
    var SCOPE = "__SCOPE__";
    if (!IMG) { return; }

    var SID = "__yetvplus_bg_style__";
    var LID = "__yetvplus_bg_layer__";

    function inScope() {
      if (SCOPE === "all") { return true; }
      var p = (location.pathname || "/");
      var isLogin = (p.indexOf("/login") === 0 || p.indexOf("/register") === 0);
      if (SCOPE === "login") { return isLogin; }
      return !isLogin && p.indexOf("/admin") !== 0;
    }

    function styleText() {
      var b = (1 - DIM).toFixed(3);
      return "html{background-color:#0a0a10 !important}" +
        "html,body,body>div,#__next,#__next>div{background-color:transparent !important;background-image:none !important}" +
        "#" + LID + "{position:fixed;top:-40px;left:-40px;right:-40px;bottom:-40px;z-index:-1;" +
        "pointer-events:none;background-image:url(" + IMG + ");background-size:cover;" +
        "background-position:center;background-repeat:no-repeat;" +
        "filter:blur(" + BLUR + "px) brightness(" + b + ")}";
    }

    function install() {
      try {
        if (!document.body) { setTimeout(install, 300); return; }
        var old = document.getElementById(SID);
        if (old && old.parentNode) { old.parentNode.removeChild(old); }
        var layer = document.getElementById(LID);
        if (layer && layer.parentNode) { layer.parentNode.removeChild(layer); }
        if (!inScope()) { return; }
        var st = document.createElement("style");
        st.id = SID;
        st.textContent = styleText();
        (document.head || document.documentElement).appendChild(st);
        var dv = document.createElement("div");
        dv.id = LID;
        document.body.appendChild(dv);
      } catch (e) {}
    }

    install();
    window.__yetvplus_bg_install = install;

    if (!window.__yetvplus_bg_hooked) {
      window.__yetvplus_bg_hooked = true;
      var push = history.pushState;
      history.pushState = function () {
        var r = push.apply(history, arguments);
        setTimeout(function () { if (window.__yetvplus_bg_install) { window.__yetvplus_bg_install(); } }, 400);
        return r;
      };
      var rep = history.replaceState;
      history.replaceState = function () {
        var r = rep.apply(history, arguments);
        setTimeout(function () { if (window.__yetvplus_bg_install) { window.__yetvplus_bg_install(); } }, 400);
        return r;
      };
      window.addEventListener("popstate", function () {
        setTimeout(function () { if (window.__yetvplus_bg_install) { window.__yetvplus_bg_install(); } }, 400);
      });
      window.addEventListener("load", function () {
        setTimeout(function () { if (window.__yetvplus_bg_install) { window.__yetvplus_bg_install(); } }, 600);
      });
    }
  } catch (e) {}
})();
"""
}
