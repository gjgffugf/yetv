package com.yetvplus.app.util

import android.content.Context

/**
 * 应用配置读写（SharedPreferences）
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("yetvplus_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BASE = "https://mtv.ffurry.cc.cd"

        private const val UA_MOBILE =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        private const val UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val UA_TAG = " YETVPlusApp/1.0"

        const val SCOPE_ALL = "all"
        const val SCOPE_HOME = "home"
        const val SCOPE_LOGIN = "login"
    }

    // ---------- 服务器 ----------

    var baseUrl: String
        get() = sp.getString("base_url", DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) {
            sp.edit().putString("base_url", normalize(value)).apply()
        }

    val serverHistory: List<String>
        get() = (sp.getStringSet("server_history", emptySet()) ?: emptySet()).toList().reversed()

    fun rememberServer(url: String) {
        val n = normalize(url)
        val set = (sp.getStringSet("server_history", emptySet()) ?: emptySet()).toMutableSet()
        set.remove(n)
        set.add(n)
        if (set.size > 8) {
            set.remove(set.first())
        }
        sp.edit().putStringSet("server_history", set).apply()
    }

    fun normalize(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return DEFAULT_BASE
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://$s"
        }
        while (s.endsWith("/")) {
            s = s.dropLast(1)
        }
        return s
    }

    fun homeUrl(): String = baseUrl

    fun page(path: String): String = baseUrl + if (path.startsWith("/")) path else "/$path"

    fun adminUrl(): String = page("/admin")

    fun isOwnHost(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host
            !host.isNullOrEmpty() && host == android.net.Uri.parse(baseUrl).host
        } catch (e: Exception) {
            false
        }
    }

    // ---------- 显示 ----------

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop_mode", false)
        set(value) = sp.edit().putBoolean("desktop_mode", value).apply()

    var customUa: String
        get() = sp.getString("custom_ua", "") ?: ""
        set(value) = sp.edit().putString("custom_ua", value.trim()).apply()

    fun userAgent(): String {
        val custom = customUa
        if (custom.isNotEmpty()) return custom
        return (if (desktopMode) UA_DESKTOP else UA_MOBILE) + UA_TAG
    }

    var immersive: Boolean
        get() = sp.getBoolean("immersive", false)
        set(value) = sp.edit().putBoolean("immersive", value).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(value) = sp.edit().putBoolean("keep_screen_on", value).apply()

    var autoLandscape: Boolean
        get() = sp.getBoolean("auto_landscape", false)
        set(value) = sp.edit().putBoolean("auto_landscape", value).apply()

    var showFab: Boolean
        get() = sp.getBoolean("show_fab", true)
        set(value) = sp.edit().putBoolean("show_fab", value).apply()

    var pullRefresh: Boolean
        get() = sp.getBoolean("pull_refresh", true)
        set(value) = sp.edit().putBoolean("pull_refresh", value).apply()

    var longPressSave: Boolean
        get() = sp.getBoolean("long_press_save", true)
        set(value) = sp.edit().putBoolean("long_press_save", value).apply()

    // ---------- 背景图 ----------

    var bgEnabled: Boolean
        get() = sp.getBoolean("bg_enabled", false)
        set(value) = sp.edit().putBoolean("bg_enabled", value).apply()

    /** data:image/...base64 或 http(s) 图片地址 */
    var bgImage: String
        get() = sp.getString("bg_image", "") ?: ""
        set(value) = sp.edit().putString("bg_image", value).apply()

    /** 暗化程度 0f ~ 0.85f */
    var bgDim: Float
        get() = sp.getFloat("bg_dim", 0.45f)
        set(value) = sp.edit().putFloat("bg_dim", value).apply()

    /** 模糊 px 0 ~ 20 */
    var bgBlur: Int
        get() = sp.getInt("bg_blur", 0)
        set(value) = sp.edit().putInt("bg_blur", value).apply()

    var bgScope: String
        get() = sp.getString("bg_scope", SCOPE_ALL) ?: SCOPE_ALL
        set(value) = sp.edit().putString("bg_scope", value).apply()

    fun hasBackground(): Boolean = bgEnabled && bgImage.isNotEmpty()

    // ---------- 高级 ----------

    var adblockEnabled: Boolean
        get() = sp.getBoolean("adblock_enabled", false)
        set(value) = sp.edit().putBoolean("adblock_enabled", value).apply()

    var adblockHosts: String
        get() = sp.getString(
            "adblock_hosts",
            "ad.doubleclick.net\npagead2.googlesyndication.com\ngoogleads.g.doubleclick.net\nanalytics.google.com\ngstatic-adssl.l.google.com"
        ) ?: ""
        set(value) = sp.edit().putString("adblock_hosts", value).apply()

    var ignoreSsl: Boolean
        get() = sp.getBoolean("ignore_ssl", false)
        set(value) = sp.edit().putBoolean("ignore_ssl", value).apply()

    var mediaHook: Boolean
        get() = sp.getBoolean("media_hook", true)
        set(value) = sp.edit().putBoolean("media_hook", value).apply()
}
