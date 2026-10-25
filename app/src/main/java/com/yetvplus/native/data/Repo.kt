package com.yetvplus.native.data

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 业务仓库：所有网络请求必须切到 IO 线程。
 * 接口路径集中在此，抓包校准时主要看这里 + Models.Mapping。
 */
class Repo(private val api: ApiClient) {

    // ---------- 内容 ----------

    suspend fun search(q: String): List<VideoItem> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        Mapping.parseSearch(api.get("/api/search", mapOf("q" to q)))
    }

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
            Mapping.parseDouban(
                api.get(
                    "/api/douban",
                    mapOf(
                        "type" to type,
                        "tag" to tag,
                        "pageSize" to pageSize.toString(),
                        "pageStart" to start.toString()
                    )
                )
            )
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
        Mapping.parseCategories(api.get("/api/config/custom_category"))
    }

    suspend fun sources(): List<SourceItem> = withContext(Dispatchers.IO) {
        Mapping.parseSources(api.get("/api/config/sources"))
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

    suspend fun saveSource(payload: JsonObject): String = withContext(Dispatchers.IO) {
        val res = api.post("/api/admin/source", payload)
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    suspend fun saveCategory(payload: JsonObject): String = withContext(Dispatchers.IO) {
        val res = api.post("/api/admin/category", payload)
        if (api.isOk(res)) "" else api.errorOf(res)
    }

    suspend fun saveUser(payload: JsonObject): String = withContext(Dispatchers.IO) {
        val res = api.post("/api/admin/user", payload)
        if (api.isOk(res)) "" else api.errorOf(res)
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
