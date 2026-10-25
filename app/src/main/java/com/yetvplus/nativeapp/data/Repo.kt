package com.yetvplus.nativeapp.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 业务仓库：所有网络请求必须切到 IO 线程。
 * 接口路径集中在此，抓包校准时主要看这里 + Models.Mapping。
 */
class Repo(private val api: ApiClient, private val prefs: Prefs) {

    // ---------- 内容 ----------

    suspend fun search(q: String): List<VideoItem> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        Mapping.parseSearch(api.get("/api/search", mapOf("q" to q)))
    }

    /**
     * 搜索并整合：把不同源里同名（标题归一化后）的条目合并成一组，
     * 这样列表里一部片只出现一次，进详情后可横向切源。
     *
     * @param merge false 时直接返回原始结果（每个源各占一个卡片）
     */
    suspend fun searchGrouped(q: String, merge: Boolean): List<SearchGroup> =
        withContext(Dispatchers.IO) {
            val list = search(q)
            if (!merge) {
                return@withContext list.map { v ->
                    SearchGroup(v.title, v.poster, listOf(v))
                }
            }
            val buckets = LinkedHashMap<String, MutableList<VideoItem>>()
            for (v in list) {
                val k = normalize(v.title)
                if (k.isEmpty()) continue
                buckets.getOrPut(k) { ArrayList() }.add(v)
            }
            buckets.values.map { vs ->
                // 集数多的源排前面，默认用资源最全的
                val sorted = vs.sortedByDescending { it.episodes.size }
                SearchGroup(sorted.first().title, sorted.first().poster, sorted)
            }
        }

    /** 标题归一化：去空格、标点、全角符号，避免同一部片因标点差异分成两组 */
    private fun normalize(t: String): String = t
        .replace(Regex("[\\s　:：·・\\-—_()（）【】\\[\\]《》<>「」『』,，.。!！?？'\"’“”]"), "")
        .lowercase()

    suspend fun detail(source: String, id: String): DetailInfo = withContext(Dispatchers.IO) {
        Mapping.parseDetail(api.get("/api/detail", mapOf("source" to source, "id" to id)))
    }

    /**
     * 首页榜单。
     * 注意：抓包显示 /api/douban-recommendations 在你的站点返回 500（大概率没配 TMDB Key），
     * 所以这里走 /api/douban，它是稳定的分类榜单接口。
     */
    suspend fun douban(type: String, tag: String, pageSize: Int = 24, start: Int = 0): List<DoubanItem> =
        withContext(Dispatchers.IO) {
            val key = "douban:$type:$tag:$pageSize:$start"
            // 24 小时缓存：Cloudflare 部署经常超时，缓存能显著减少等待
            prefs.getCache(key)?.let { cached ->
                val el = runCatching {
                    Gson().fromJson(cached, JsonElement::class.java)
                }.getOrNull()
                if (el != null) {
                    val list = Mapping.parseDouban(el)
                    if (list.isNotEmpty()) return@withContext list
                }
            }
            val el = api.get(
                "/api/douban",
                mapOf(
                    "type" to type,
                    "tag" to tag,
                    "pageSize" to pageSize.toString(),
                    "pageStart" to start.toString()
                )
            )
            if (el != null) runCatching { prefs.putCache(key, el.toString()) }
            Mapping.parseDouban(el)
        }

    /** 豆瓣推荐（你的站点目前 500，保留但不作为首页数据源） */
    suspend fun doubanRecommendations(id: String): List<DoubanItem> = withContext(Dispatchers.IO) {
        Mapping.parseDouban(api.get("/api/douban-recommendations", mapOf("id" to id)))
    }

    // ---------- 弹幕（danmaku）----------
    // 抓包确认 /api/danmaku/search 返回 200，站点已配好弹幕服务

    suspend fun danmakuSearch(keyword: String): List<DanmakuAnime> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        Mapping.parseDanmakuSearch(api.get("/api/danmaku/search", mapOf("keyword" to keyword)))
    }

    suspend fun danmakuEpisodes(animeId: String): List<DanmakuEpisode> = withContext(Dispatchers.IO) {
        Mapping.parseDanmakuEpisodes(api.get("/api/danmaku/episodes", mapOf("animeId" to animeId)))
    }

    suspend fun danmakuComments(episodeId: String): List<DanmakuComment> = withContext(Dispatchers.IO) {
        Mapping.parseDanmakuComments(api.get("/api/danmaku/comment", mapOf("episodeId" to episodeId)))
    }

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        // /api/config/custom_category 实测 404，改从管理配置读 Config.CustomCategories
        Mapping.parseCategories(api.get("/api/admin/config"))
    }

    /**
     * 视频源列表。
     * 原实现只打 /api/config/sources，该接口在多数部署里并不存在（404 被静默吞成空列表），
     * 导致"已配置却显示 0 个源"。改为依次尝试多个路径，取第一个非空结果。
     */
    suspend fun sources(): List<SourceItem> = withContext(Dispatchers.IO) {
        val paths = listOf(
            "/api/admin/config",   // 管理面板配置，含 Config.SourceConfig（最可能）
            "/api/config/sources", // 旧路径，保留兜底
            "/api/sources"
        )
        for (p in paths) {
            val el = runCatching { api.get(p) }.getOrNull() ?: continue
            val list = Mapping.parseSources(el)
            if (list.isNotEmpty()) return@withContext list
        }
        emptyList()
    }

    // ---------- 用户 ----------

    /**
     * 登录。
     * localstorage 模式只提交 password；数据库模式提交 username + password。
     */
    suspend fun login(username: String, password: String, singlePasswordMode: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val body = JsonObject()
            if (singlePasswordMode) {
                body.addProperty("password", password)
            } else {
                body.addProperty("username", username)
                body.addProperty("password", password)
            }
            val res = api.post("/api/login", body)
            if (api.isOk(res)) {
                // 服务端 Set-Cookie 的 auth 里带 role，取出来供管理面板判断权限
                api.saveRoleFromCookie()
                ""
            } else {
                api.errorOf(res)
            }
        }

    suspend fun register(username: String, password: String): String = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
        }
        val res = api.post("/api/register", body)
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    suspend fun logout(): String = withContext(Dispatchers.IO) {
        val res = api.post("/api/logout", JsonObject())
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    suspend fun favorites(): List<VideoItem> = withContext(Dispatchers.IO) {
        Mapping.parseSearch(api.get("/api/favorites"))
    }

    suspend fun playRecords(): List<VideoItem> = withContext(Dispatchers.IO) {
        Mapping.parseSearch(api.get("/api/playrecords"))
    }

    // ---------- 站点配置 ----------

    suspend fun serverConfig(): SiteConfig = withContext(Dispatchers.IO) {
        Mapping.parseSiteConfig(api.get("/api/server-config"))
    }

    /**
     * 管理面板用的站点配置。
     * 公开接口 /api/server-config 通常不含 Announcement，所以优先读
     * /api/admin/config（Config.SiteConfig.Announcement），读不到再回落旧接口。
     */
    suspend fun adminSiteConfig(): SiteConfig = withContext(Dispatchers.IO) {
        val fromAdmin = runCatching { api.get("/api/admin/config") }.getOrNull()
        val parsed = Mapping.parseSiteConfig(fromAdmin)
        if (parsed.siteName.isNotEmpty() || parsed.announcement.isNotEmpty()) return@withContext parsed
        runCatching { serverConfig() }.getOrDefault(SiteConfig())
    }

    suspend fun adminConfig(): com.google.gson.JsonElement? = withContext(Dispatchers.IO) {
        api.get("/api/admin/config")
    }

    // ---------- 管理面板 ----------

    suspend fun adminUsers(): List<UserItem> = withContext(Dispatchers.IO) {
        val res = api.get("/api/admin/config")
        Mapping.parseUsers(res)
    }

    suspend fun saveSite(payload: JsonObject): String = withContext(Dispatchers.IO) {
        val res = api.post("/api/admin/site", payload)
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    /**
     * 视频源操作。
     *
     * 抓包发现的关键点：这个接口**必须带 action**，否则服务端直接报「参数错误」。
     * 官方支持：add / delete / enable / disable / sort
     * —— 之前只是把整个源对象 POST 过去，没有 action，所以新增和删除都失败。
     */
    suspend fun sourceAction(action: String, payload: JsonObject): String =
        withContext(Dispatchers.IO) {
            val body = payload.deepCopy().apply { addProperty("action", action) }
            val res = api.post("/api/admin/source", body)
            if (api.isOk(res)) "" else api.errorOf(res)
        }

    /** 保留旧签名，内部转成 add */
    suspend fun saveSource(payload: JsonObject): String = sourceAction("add", payload)

    suspend fun saveCategory(payload: JsonObject): String = withContext(Dispatchers.IO) {
        val res = api.post("/api/admin/category", payload)
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    /**
     * 用户操作。
     *
     * 关键点有两个：
     * 1. 必须带 action：add / deleteUser / ban / unban / changePassword /
     *    setAdmin / cancelAdmin / setAllowRegister
     * 2. 用户名/密码字段是 **targetUsername / targetPassword**（不是 username / password）
     *    —— 之前用错字段名，服务端校验不过就报「参数错误」。
     */
    suspend fun userAction(action: String, payload: JsonObject): String =
        withContext(Dispatchers.IO) {
            val body = payload.deepCopy().apply { addProperty("action", action) }
            val res = api.post("/api/admin/user", body)
            if (api.isOk(res)) "" else api.errorOf(res)
        }

    /** 添加用户：字段名必须是 targetUsername / targetPassword，且带 action=add */
    suspend fun addUser(username: String, password: String, role: String): String =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply {
                addProperty("targetUsername", username)
                addProperty("targetPassword", password)
                addProperty("role", role)
            }
            val res = api.post("/api/admin/user", body.apply { addProperty("action", "add") })
            if (api.isOk(res)) "" else api.errorOf(res)
        }

    /** 保留旧签名，内部转成 add（并把字段换成 targetXxx） */
    suspend fun saveUser(payload: JsonObject): String {
        val body = JsonObject().apply {
            addProperty("targetUsername", payload.get("username")?.asString ?: "")
            addProperty("targetPassword", payload.get("password")?.asString ?: "")
            val r = payload.get("role")?.asString
            if (!r.isNullOrBlank()) addProperty("role", r)
        }
        return userAction("add", body)
    }

    suspend fun saveConfigFile(text: String): String = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("config", text) }
        val res = api.post("/api/admin/config_file", payload)
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    suspend fun pullSubscription(url: String, autoUpdate: Boolean): String = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("url", url)
            addProperty("autoUpdate", autoUpdate)
        }
        val res = api.post("/api/admin/subscription", payload)
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        api.get("/api/server-config") != null
    }
}
