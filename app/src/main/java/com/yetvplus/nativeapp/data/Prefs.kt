package com.yetvplus.nativeapp.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("yetvplus_native", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BASE = "https://mtv.ffurry.cc.cd"
    }

    var baseUrl: String
        get() = sp.getString("base_url", DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) {
            var s = value.trim()
            if (s.isEmpty()) s = DEFAULT_BASE
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            while (s.endsWith("/")) s = s.dropLast(1)
            sp.edit().putString("base_url", s).apply()
        }

    val history: List<String>
        get() = (sp.getStringSet("history", emptySet()) ?: emptySet()).toList().reversed()

    fun remember(url: String) {
        val set = (sp.getStringSet("history", emptySet()) ?: emptySet()).toMutableSet()
        set.remove(url)
        set.add(url)
        if (set.size > 8) set.remove(set.first())
        sp.edit().putStringSet("history", set).apply()
    }

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(value) = sp.edit().putString("username", value).apply()

    /** 登录后的 Cookie 串，直接回传给服务端 */
    var cookie: String
        get() = sp.getString("cookie", "") ?: ""
        set(value) = sp.edit().putString("cookie", value).apply()

    var role: String
        get() = sp.getString("role", "") ?: ""
        set(value) = sp.edit().putString("role", value).apply()

    // ---------- 登录页 ----------

    /** 「记住密码」勾选后保存的密码（明文存本地，仅自用场景） */
    var savedPassword: String
        get() = sp.getString("saved_pwd", "") ?: ""
        set(value) = sp.edit().putString("saved_pwd", value).apply()

    var rememberPassword: Boolean
        get() = sp.getBoolean("remember_pwd", false)
        set(value) {
            sp.edit().putBoolean("remember_pwd", value).apply()
            if (!value) sp.edit().putString("saved_pwd", "").apply()
        }

    var lastUsername: String
        get() = sp.getString("last_username", "") ?: ""
        set(value) = sp.edit().putString("last_username", value).apply()

    /** 站点存储类型：localstorage 时为单密码模式 */
    var storageType: String
        get() = sp.getString("storage_type", "") ?: ""
        set(value) = sp.edit().putString("storage_type", value).apply()

    var siteName: String
        get() = sp.getString("site_name", "") ?: ""
        set(value) = sp.edit().putString("site_name", value).apply()

    var announcement: String
        get() = sp.getString("announcement", "") ?: ""
        set(value) = sp.edit().putString("announcement", value).apply()

    var registerEnabled: Boolean
        get() = sp.getBoolean("register_enabled", false)
        set(value) = sp.edit().putBoolean("register_enabled", value).apply()

    // ---------- 观看进度（纯本地，不上传）----------

    private fun progKey(videoKey: String, index: Int) = "wp_${videoKey}_$index"
    private fun finKey(videoKey: String, index: Int) = "wf_${videoKey}_$index"

    /** 0f ~ 1f，未看过返回 0f */
    fun getProgress(videoKey: String, index: Int): Float =
        sp.getFloat(progKey(videoKey, index), 0f)

    /** 记录进度；进度 >= 0.95 自动标记为已看完 */
    fun setProgress(videoKey: String, index: Int, p: Float) {
        val v = p.coerceIn(0f, 1f)
        sp.edit().putFloat(progKey(videoKey, index), v).apply()
        if (v >= 0.95f) setFinished(videoKey, index, true)
    }

    fun isFinished(videoKey: String, index: Int): Boolean =
        sp.getBoolean(finKey(videoKey, index), false)

    fun setFinished(videoKey: String, index: Int, v: Boolean) {
        sp.edit().putBoolean(finKey(videoKey, index), v).apply()
    }

    /** 上次观看到第几集，用于「继续播放」 */
    var lastEpisode: Int
        get() = sp.getInt("last_episode", -1)
        set(value) = sp.edit().putInt("last_episode", value).apply()

    /** 清除本机全部观看进度 */
    fun clearProgress() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("wp_") || it.startsWith("wf_") }.forEach { e.remove(it) }
        e.remove("last_episode").apply()
    }

    fun isLoggedIn(): Boolean = cookie.isNotEmpty()

    fun logout() {
        sp.edit().putString("cookie", "").putString("role", "").apply()
    }

    // ---------- 显示 ----------

    var bgEnabled: Boolean
        get() = sp.getBoolean("bg_enabled", false)
        set(value) = sp.edit().putBoolean("bg_enabled", value).apply()

    var bgPath: String
        get() = sp.getString("bg_path", "") ?: ""
        set(value) = sp.edit().putString("bg_path", value).apply()

    var bgDim: Float
        get() = sp.getFloat("bg_dim", 0.55f)
        set(value) = sp.edit().putFloat("bg_dim", value).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(value) = sp.edit().putBoolean("keep_screen_on", value).apply()

    var autoLandscape: Boolean
        get() = sp.getBoolean("auto_landscape", false)
        set(value) = sp.edit().putBoolean("auto_landscape", value).apply()

    var useExternalPlayer: Boolean
        get() = sp.getBoolean("external_player", false)
        set(value) = sp.edit().putBoolean("external_player", value).apply()

    var externalPlayerPkg: String
        get() = sp.getString("external_pkg", "") ?: ""
        set(value) = sp.edit().putString("external_pkg", value).apply()

    var ignoreSsl: Boolean
        get() = sp.getBoolean("ignore_ssl", false)
        set(value) = sp.edit().putBoolean("ignore_ssl", value).apply()

    /**
     * 接口路径前缀。某些部署把 API 挂在子路径下（例如 /dl/api/search），
     * 默认留空即 /api/...；填 "/dl" 则请求 /dl/api/...
     */
    var apiPrefix: String
        get() = sp.getString("api_prefix", "") ?: ""
        set(value) {
            var s = value.trim()
            while (s.endsWith("/")) s = s.dropLast(1)
            if (s == "api" || s == "/api") s = ""
            if (s.isNotEmpty() && !s.startsWith("/")) s = "/$s"
            sp.edit().putString("api_prefix", s).apply()
        }
}
