package com.yetvplus.nativeapp.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("yetvplus_native", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BASE = "https://mtv.ffurry.cc.cd"
        /** 首页缓存有效期：24 小时 */
        const val CACHE_MS = 24 * 60 * 60 * 1000L
        private val GSON = com.google.gson.Gson()
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

    // ---------- 观看记录（列表，供「我的」页展示）----------

    /** 记录一部片：标题、海报、看到第几集、时间戳。同一部片覆盖更新。 */
    fun putWatchRecord(videoKey: String, title: String, poster: String, episode: Int) {
        val map = LinkedHashMap<String, String>()
        map["t"] = title
        map["p"] = poster
        map["e"] = episode.toString()
        map["ts"] = System.currentTimeMillis().toString()
        sp.edit().putString("wr_$videoKey", GSON.toJson(map)).apply()
    }

    /** 按最近观看排序 */
    fun watchRecords(): List<WatchRecord> {
        val out = ArrayList<WatchRecord>()
        for ((k, v) in sp.all) {
            if (!k.startsWith("wr_")) continue
            if (v !is String) continue
            val m = runCatching {
                GSON.fromJson(v, com.google.gson.reflect.TypeToken.getParameterized(
                    Map::class.java, String::class.java, String::class.java).type) as Map<String, String>
            }.getOrNull() ?: continue
            out.add(
                WatchRecord(
                    key = k.removePrefix("wr_"),
                    title = m["t"] ?: "",
                    poster = m["p"] ?: "",
                    episode = m["e"]?.toIntOrNull() ?: 0,
                    ts = m["ts"]?.toLongOrNull() ?: 0L
                )
            )
        }
        return out.sortedByDescending { it.ts }
    }

    fun removeWatchRecord(videoKey: String) {
        sp.edit().remove("wr_$videoKey").apply()
    }

    fun clearWatchRecords() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("wr_") }.forEach { e.remove(it) }
        e.apply()
    }

    // ---------- 搜索历史 ----------

    var searchHistory: List<String>
        get() {
            val raw = sp.getString("search_history", "") ?: ""
            if (raw.isEmpty()) return emptyList()
            return raw.split("\u0001").filter { it.isNotBlank() }
        }
        set(value) = sp.edit().putString(
            "search_history",
            value.take(20).joinToString("\u0001")
        ).apply()

    fun addSearchHistory(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        val list = searchHistory.toMutableList()
        list.remove(t)
        list.add(0, t)
        searchHistory = list
    }

    fun removeSearchHistory(q: String) {
        searchHistory = searchHistory.filter { it != q }
    }

    fun clearSearchHistory() {
        sp.edit().remove("search_history").apply()
    }

    // ---------- 个性化 ----------

    /** 主题色 ARGB；0 表示用默认 Brand */
    var themeColor: Int
        get() = sp.getInt("theme_color", 0)
        set(value) = sp.edit().putInt("theme_color", value).apply()

    // ---------- 首页缓存（24 小时）----------

    private fun cacheKey(k: String) = "cache_$k"

    /** 24 小时内返回缓存，过期返回 null */
    fun getCache(k: String): String? {
        val ts = sp.getLong("${cacheKey(k)}_ts", 0L)
        if (System.currentTimeMillis() - ts > CACHE_MS) return null
        return sp.getString(cacheKey(k), null)
    }

    fun putCache(k: String, json: String) {
        sp.edit()
            .putString(cacheKey(k), json)
            .putLong("${cacheKey(k)}_ts", System.currentTimeMillis())
            .apply()
    }

    fun clearCache() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("cache_") }.forEach { e.remove(it) }
        e.apply()
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
