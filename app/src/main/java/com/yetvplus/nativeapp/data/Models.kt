package com.yetvplus.nativeapp.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * 数据模型 + 字段映射
 *
 * ⚠️ 字段映射集中在本文件的 Mapping 对象里。
 * 抓包拿到真实响应后，只需要改 Mapping 中的键名即可，UI 层不用动。
 * 解析全部走「取不到就给默认值」的宽松策略，不会因为站点改字段而崩溃。
 */

// ---------------- 模型 ----------------

data class VideoItem(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val source: String = "",
    val sourceName: String = "",
    val year: String = "",
    val typeName: String = "",
    val note: String = "",
    val desc: String = "",
    val rate: String = "",
    val doubanId: String = "",
    /** 搜索结果自带的 m3u8 分集，非空时详情页无需再请求 detail */
    val episodes: List<Episode> = emptyList()
) {
    val key: String get() = "$source+$id"
}

data class Episode(
    val name: String = "",
    val url: String = ""
)

data class DetailInfo(
    val item: VideoItem = VideoItem(),
    val episodes: List<Episode> = emptyList()
)

/**
 * 搜索整合后的一组：同一部片在不同源下的条目。
 * 列表里只占一个卡片，进详情 / 播放后可在集数下方横向切源。
 */
data class SearchGroup(
    val title: String = "",
    val poster: String = "",
    /** 同一部片在各源下的条目，已按集数从多到少排序 */
    val variants: List<VideoItem> = emptyList()
) {
    /** 默认选中集数最多的那个源 */
    val best: VideoItem get() = variants.firstOrNull() ?: VideoItem()
    val key: String get() = best.key
}

data class DoubanItem(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val rate: String = "",
    val year: String = ""
)

data class Category(
    val name: String = "",
    val type: String = "movie",
    val query: String = ""
)

data class SourceItem(
    val key: String = "",
    val name: String = "",
    val api: String = "",
    val detail: String = "",
    val enabled: Boolean = true
)

data class UserItem(
    val username: String = "",
    val role: String = "user",
    val banned: Boolean = false
)

// ---------------- 字段映射 ----------------

object Mapping {

    /** 从任意 JSON 元素中按候选键依次取值，第一个非空的有效 */
    private fun pick(obj: JsonObject?, vararg keys: String): String {
        if (obj == null) return ""
        for (k in keys) {
            val el = obj.get(k) ?: continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty() && s != "null") return s
            } else if (el.isJsonArray && el.asJsonArray.size() > 0) {
                val first = el.asJsonArray[0]
                if (first.isJsonPrimitive) return first.asString.trim()
            }
        }
        return ""
    }

    private fun asBool(obj: JsonObject?, vararg keys: String, default: Boolean): Boolean {
        if (obj == null) return default
        for (k in keys) {
            val el = obj.get(k) ?: continue
            if (el.isJsonPrimitive) {
                return when {
                    el.asBoolean -> true
                    el.asString == "1" -> true
                    el.asString == "0" -> false
                    el.asString.equals("false", true) -> false
                    el.asString.equals("true", true) -> true
                    else -> default
                }
            }
        }
        return default
    }

    // ---- 搜索结果 / 详情 ----

    /**
     * 分集列表。抓包确认：搜索结果里就带完整的 .m3u8 分集数组，
     * 所以搜索和详情共用这一套解析。
     * episodes 可能是 ["url", "url"]，也可能是 [{name,url}]。
     */
    fun parseEpisodes(o: JsonObject): List<Episode> {
        val eps = ArrayList<Episode>()
        for (key in listOf("episodes", "episode", "urls", "m3u8List", "playUrl", "sources", "videoList")) {
            val el = o.get(key) ?: continue
            if (!el.isJsonArray) continue
            for (e in el.asJsonArray) {
                when {
                    e.isJsonPrimitive -> {
                        val u = e.asString.trim()
                        if (u.isNotEmpty()) eps.add(Episode("第${eps.size + 1}集", u))
                    }
                    e.isJsonObject -> {
                        val eo = e.asJsonObject
                        val u = pick(eo, "url", "playUrl", "link", "m3u8", "src")
                        val n = pick(eo, "name", "title", "episode", "text")
                        if (u.isNotEmpty()) {
                            eps.add(Episode(n.ifEmpty { "第${eps.size + 1}集" }, u))
                        }
                    }
                }
            }
            if (eps.isNotEmpty()) break
        }
        return eps
    }

    fun parseVideo(o: JsonObject): VideoItem = VideoItem(
        id = pick(o, "id", "vod_id", "videoId", "ids"),
        title = pick(o, "title", "name", "vod_name", "videoName"),
        poster = pick(o, "poster", "pic", "vod_pic", "cover", "image", "img"),
        source = pick(o, "source", "source_key", "sourceKey", "from"),
        sourceName = pick(o, "source_name", "sourceName", "sourceNameText", "source_label"),
        year = pick(o, "year", "vod_year", "releaseDate"),
        typeName = pick(o, "type_name", "typeName", "vod_class", "class"),
        note = pick(o, "note", "vod_remarks", "remarks", "state"),
        desc = pick(o, "desc", "description", "vod_content", "content", "plot"),
        rate = pick(o, "rate", "rating", "score", "vod_score", "douban_rating"),
        doubanId = pick(o, "douban_id", "doubanId", "doubanId"),
        // 搜索结果自带 m3u8 分集，直接带上，详情页可省掉一次请求
        episodes = parseEpisodes(o)
    )

    /** 搜索响应：兼容 {results:[...]} / {data:[...]} / 直接数组 */
    fun parseSearch(root: JsonElement?): List<VideoItem> {
        val arr = when {
            root == null || root.isJsonNull -> return emptyList()
            root.isJsonArray -> root.asJsonArray
            else -> {
                val o = root.asJsonObject
                when {
                    o.has("results") && o.get("results").isJsonArray -> o.getAsJsonArray("results")
                    o.has("data") && o.get("data").isJsonArray -> o.getAsJsonArray("data")
                    o.has("list") && o.get("list").isJsonArray -> o.getAsJsonArray("list")
                    else -> return emptyList()
                }
            }
        }
        val out = ArrayList<VideoItem>(arr.size())
        for (e in arr) {
            if (!e.isJsonObject) continue
            val v = parseVideo(e.asJsonObject)
            if (v.title.isNotEmpty() || v.id.isNotEmpty()) out.add(v)
        }
        return out
    }

    /** 详情响应 */
    fun parseDetail(root: JsonElement?): DetailInfo {
        if (root == null || !root.isJsonObject) return DetailInfo()
        val o = root.asJsonObject
        val item = parseVideo(o)
        return DetailInfo(item, item.episodes)
    }

    // ---- 豆瓣分类 ----

    /**
     * 豆瓣分类榜单。抓包确认的真实结构：
     * {"code":..., "message":..., "list":[{"id","title","poster","rate","year"}]}
     */
    fun parseDouban(root: JsonElement?): List<DoubanItem> {
        val arr = when {
            root == null || root.isJsonNull -> return emptyList()
            root.isJsonArray -> root.asJsonArray
            else -> {
                val o = root.asJsonObject
                // code 非 0 视为失败（常见于代理没配好）
                val code = pick(o, "code")
                if (code.isNotEmpty() && code != "0" && code != "200" && code != "1") {
                    val msg = pick(o, "message", "msg")
                    if (msg.contains("失败", true) || msg.contains("error", true)) return emptyList()
                }
                when {
                    o.has("list") && o.get("list").isJsonArray -> o.getAsJsonArray("list")
                    o.has("results") && o.get("results").isJsonArray -> o.getAsJsonArray("results")
                    o.has("data") && o.get("data").isJsonArray -> o.getAsJsonArray("data")
                    o.has("items") && o.get("items").isJsonArray -> o.getAsJsonArray("items")
                    else -> return emptyList()
                }
            }
        }
        val out = ArrayList<DoubanItem>(arr.size())
        for (e in arr) {
            if (!e.isJsonObject) continue
            val o = e.asJsonObject
            out.add(
                DoubanItem(
                    id = pick(o, "id", "douban_id", "doubanId"),
                    title = pick(o, "title", "name"),
                    poster = pick(o, "poster", "pic", "cover", "image"),
                    rate = pick(o, "rate", "rating", "score"),
                    year = pick(o, "year", "release_date", "date")
                )
            )
        }
        return out
    }

    // ---- 视频源配置 ----

    /**
     * 在 JSON 树里按候选键名找节点（广度优先，限深）。
     * 管理面板各部署返回结构差异很大（有的包 Config、有的包 data），
     * 逐层猜太脆弱，直接全树搜索最稳。
     */
    private fun findNode(root: JsonElement?, names: Set<String>, deep: Int = 6): JsonElement? {
        if (root == null || deep <= 0) return null
        if (!root.isJsonObject) return null
        val o = root.asJsonObject
        for (n in names) {
            val v = o.get(n)
            if (v != null && !v.isJsonNull) return v
        }
        for ((_, v) in o.entrySet()) {
            if (v.isJsonObject || v.isJsonArray) {
                val hit = findNode(v, names, deep - 1)
                if (hit != null) return hit
            }
        }
        return null
    }

    fun parseSources(root: JsonElement?): List<SourceItem> {
        if (root == null) return emptyList()
        // 全树找 sources / SourceConfig / api_site，适配不同部署的包裹层级
        val node = findNode(root, setOf("sources", "Sources", "SourceConfig", "api_site", "apiSite"))
            ?: root
        val out = ArrayList<SourceItem>()
        if (node != null && node.isJsonArray) {
            for (e in node.asJsonArray) {
                if (!e.isJsonObject) continue
                val so = e.asJsonObject
                out.add(
                    SourceItem(
                        key = pick(so, "key", "id"),
                        name = pick(so, "name", "title"),
                        api = pick(so, "api", "url"),
                        detail = pick(so, "detail"),
                        enabled = asBool(so, "enabled", "enable", default = true)
                    )
                )
            }
        } else if (node != null && node.isJsonObject) {
            // api_site 形式：{ "dyttzy": {api,name,detail}, ... }
            for ((k, v) in node.asJsonObject.entrySet()) {
                if (!v.isJsonObject) continue
                out.add(
                    SourceItem(
                        key = k,
                        name = pick(v.asJsonObject, "name"),
                        api = pick(v.asJsonObject, "api"),
                        detail = pick(v.asJsonObject, "detail")
                    )
                )
            }
        }
        return out
    }

    fun parseCategories(root: JsonElement?): List<Category> {
        val arr = when {
            root == null || root.isJsonNull -> return emptyList()
            root.isJsonArray -> root.asJsonArray
            else -> {
                val o = root.asJsonObject
                when {
                    o.has("custom_category") -> o.get("custom_category")
                    o.has("categories") -> o.get("categories")
                    o.has("data") -> o.get("data")
                    else -> return emptyList()
                }
            }
        }
        if (!arr.isJsonArray) return emptyList()
        val out = ArrayList<Category>()
        for (e in arr.asJsonArray) {
            if (!e.isJsonObject) continue
            val co = e.asJsonObject
            out.add(
                Category(
                    name = pick(co, "name", "title"),
                    type = pick(co, "type").ifEmpty { "movie" },
                    query = pick(co, "query", "name", "title")
                )
            )
        }
        return out
    }

    // ---- 用户 ----

    fun parseUsers(root: JsonElement?): List<UserItem> {
        val arr = when {
            root == null || root.isJsonNull -> return emptyList()
            root.isJsonArray -> root.asJsonArray
            else -> {
                // 全树找 Users / users（常见结构：Config.UserConfig.Users）
                val hit = findNode(root, setOf("Users", "users", "userList"))
                if (hit != null && hit.isJsonArray) hit.asJsonArray else return emptyList()
            }
        }
        if (!arr.isJsonArray) {
            val o2 = arr.asJsonObject
            val out2 = ArrayList<UserItem>()
            for ((k, v) in o2.entrySet()) {
                if (!v.isJsonObject) continue
                val uo = v.asJsonObject
                out2.add(
                    UserItem(
                        username = pick(uo, "username", "name").ifEmpty { k },
                        role = pick(uo, "role").ifEmpty { "user" },
                        banned = asBool(uo, "banned", default = false)
                    )
                )
            }
            return out2
        }
        val out = ArrayList<UserItem>()
        for (e in arr.asJsonArray) {
            if (!e.isJsonObject) continue
            val uo = e.asJsonObject
            out.add(
                UserItem(
                    username = pick(uo, "username", "name", "user"),
                    role = pick(uo, "role").ifEmpty { "user" },
                    banned = asBool(uo, "banned", default = false)
                )
            )
        }
        return out
    }

    // ---- 弹幕（danmaku） ----
    // 抓包确认：GET /api/danmaku/search?keyword=xx 返回 200（无需登录即可访问）
    // 响应结构按官方 DanmakuPanel 的约定：{ success, animes[], errorMessage }

    fun parseDanmakuSearch(root: JsonElement?): List<DanmakuAnime> {
        if (root == null || !root.isJsonObject) return emptyList()
        val o = root.asJsonObject
        val arr = when {
            o.has("animes") && o.get("animes").isJsonArray -> o.getAsJsonArray("animes")
            o.has("results") && o.get("results").isJsonArray -> o.getAsJsonArray("results")
            o.has("data") && o.get("data").isJsonArray -> o.getAsJsonArray("data")
            else -> return emptyList()
        }
        val out = ArrayList<DanmakuAnime>(arr.size())
        for (e in arr) {
            if (!e.isJsonObject) continue
            val ao = e.asJsonObject
            out.add(
                DanmakuAnime(
                    animeId = pick(ao, "animeId", "anime_id", "id"),
                    animeTitle = pick(ao, "animeTitle", "anime_title", "title", "name"),
                    type = pick(ao, "type", "typeName"),
                    year = pick(ao, "year", "date")
                )
            )
        }
        return out
    }

    /** /api/danmaku/episodes?animeId= → { success, bangumi: { episodes: [...] } } */
    fun parseDanmakuEpisodes(root: JsonElement?): List<DanmakuEpisode> {
        if (root == null || !root.isJsonObject) return emptyList()
        val o = root.asJsonObject
        val node = when {
            o.has("bangumi") && o.get("bangumi").isJsonObject -> o.getAsJsonObject("bangumi")
            else -> o
        }
        val arr = when {
            node.has("episodes") && node.get("episodes").isJsonArray -> node.getAsJsonArray("episodes")
            node.has("data") && node.get("data").isJsonArray -> node.getAsJsonArray("data")
            else -> return emptyList()
        }
        val out = ArrayList<DanmakuEpisode>(arr.size())
        for (e in arr) {
            if (!e.isJsonObject) continue
            val eo = e.asJsonObject
            out.add(
                DanmakuEpisode(
                    episodeId = pick(eo, "episodeId", "episode_id", "id"),
                    episodeTitle = pick(eo, "episodeTitle", "episode_title", "title", "name")
                )
            )
        }
        return out
    }

    /** /api/danmaku/comment?episodeId= → 弹幕数组，元素含 p（属性串）与 m（文本） */
    fun parseDanmakuComments(root: JsonElement?): List<DanmakuComment> {
        val arr = when {
            root == null || root.isJsonNull -> return emptyList()
            root.isJsonArray -> root.asJsonArray
            else -> {
                val o = root.asJsonObject
                when {
                    o.has("comments") && o.get("comments").isJsonArray -> o.getAsJsonArray("comments")
                    o.has("data") && o.get("data").isJsonArray -> o.getAsJsonArray("data")
                    else -> return emptyList()
                }
            }
        }
        val out = ArrayList<DanmakuComment>(arr.size())
        for (e in arr) {
            if (!e.isJsonObject) continue
            val co = e.asJsonObject
            val p = pick(co, "p", "attrs")
            val m = pick(co, "m", "text", "content")
            if (m.isEmpty()) continue
            val time = parseDanmakuTime(p)
            out.add(DanmakuComment(time = time, text = m, mode = parseDanmakuMode(p)))
        }
        return out
    }

    /** p 属性串格式：时间,模式,字号,颜色,时间戳,池,用户ID,数据库ID */
    private fun parseDanmakuTime(p: String): Float {
        if (p.isEmpty()) return 0f
        return p.split(",").getOrNull(0)?.trim()?.toFloatOrNull() ?: 0f
    }

    private fun parseDanmakuMode(p: String): Int {
        if (p.isEmpty()) return 0
        return p.split(",").getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    }

    /** 站点配置：站点名、公告、注册开关、存储类型等 */
    fun parseSiteConfig(root: JsonElement?): SiteConfig {
        if (root == null || !root.isJsonObject) return SiteConfig()
        val top = root.asJsonObject
        val sc = if (top.has("SiteConfig") && top.get("SiteConfig").isJsonObject) {
            top.getAsJsonObject("SiteConfig")
        } else {
            top
        }
        // StorageType 是平铺在顶层的，登录表单要靠它决定要不要用户名
        val storageType = pick(top, "StorageType", "storageType", "NEXT_PUBLIC_STORAGE_TYPE")
        return SiteConfig(
            siteName = pick(sc, "SiteName", "siteName", "name", "SITE_NAME"),
            announcement = pick(sc, "Announcement", "announcement"),
            enableRegister = asBool(
                sc, "EnableRegistration", "enableRegistration",
                "AllowRegister", "allowRegister", default = false
            ),
            enableComments = asBool(sc, "EnableComments", "enableComments", default = false),
            doubanProxy = pick(sc, "DoubanProxy", "doubanProxy"),
            imageProxy = pick(sc, "DoubanImageProxy", "imageProxy", "ImageProxy"),
            storageType = storageType,
            // localstorage 模式是「单密码」模式，不需要用户名
            singlePasswordMode = storageType.equals("localstorage", true)
        )
    }
}

data class SiteConfig(
    val siteName: String = "",
    val announcement: String = "",
    val enableRegister: Boolean = false,
    val enableComments: Boolean = false,
    val doubanProxy: String = "",
    val imageProxy: String = "",
    val storageType: String = "",
    val singlePasswordMode: Boolean = false
)

// ---------------- 弹幕 ----------------

data class DanmakuAnime(
    val animeId: String = "",
    val animeTitle: String = "",
    val type: String = "",
    val year: String = ""
)

data class DanmakuEpisode(
    val episodeId: String = "",
    val episodeTitle: String = ""
)

data class DanmakuComment(
    /** 出现时间（秒） */
    val time: Float = 0f,
    val text: String = "",
    /** 0 滚动 / 1 顶部 / 2 底部 */
    val mode: Int = 0
)

/** 「我的」页的本地观看记录条目 */
data class WatchRecord(
    val key: String = "",
    val title: String = "",
    val poster: String = "",
    val episode: Int = 0,
    val ts: Long = 0L
)
