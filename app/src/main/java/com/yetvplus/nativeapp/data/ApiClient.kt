package com.yetvplus.nativeapp.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.toHttpUrl
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 极简 HTTP 客户端：只依赖 OkHttp + Gson。
 * Cookie 自动持久化到 Prefs，登录态可跨重启保持。
 */
class ApiClient(private val context: Context) {

    private val gson = Gson()
    private val prefs = Prefs(context)

    var baseUrl: String = prefs.baseUrl
        set(value) {
            field = value
            prefs.baseUrl = value
            prefs.remember(value)
        }

    var cookie: String
        get() = prefs.cookie
        set(value) {
            prefs.cookie = value
        }

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = prefs.cookie
            if (raw.isBlank()) return emptyList()
            val list = ArrayList<Cookie>()
            for (part in raw.split(";")) {
                val kv = part.trim()
                if (kv.isEmpty()) continue
                val idx = kv.indexOf('=')
                if (idx <= 0) continue
                val n = kv.substring(0, idx).trim()
                val v = kv.substring(idx + 1).trim()
                list.add(
                    Cookie.Builder()
                        .name(n)
                        .value(v)
                        .domain(url.host)
                        .build()
                )
            }
            return list
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val sb = StringBuilder()
            for (c in cookies) {
                if (sb.isNotEmpty()) sb.append("; ")
                sb.append(c.name).append("=").append(c.value)
            }
            if (sb.isNotEmpty()) {
                prefs.cookie = sb.toString()
            }
        }
    }

    private fun client(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
        if (prefs.ignoreSsl) {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val ssl = SSLContext.getInstance("SSL")
            ssl.init(null, trustAll, SecureRandom())
            b.sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            b.hostnameVerifier { _, _ -> true }
        }
        return b.build()
    }

    private fun url(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        val prefix = prefs.apiPrefix
        return if (prefix.isEmpty()) baseUrl + p else baseUrl + prefix + p
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ---------------- GET ----------------

    fun get(path: String, params: Map<String, String> = emptyMap()): JsonElement? {
        // OkHttp 4 用扩展函数 toHttpUrl()，HttpUrl.parse() 已废弃为 error
        val httpUrl = runCatching { url(path).toHttpUrl() }.getOrNull() ?: return null
        val builder = httpUrl.newBuilder()
        for ((k, v) in params) builder.addQueryParameter(k, v)

        val req = Request.Builder()
            .url(builder.build())
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Referer", baseUrl + "/")
            .get()
            .build()

        client().newCall(req).execute().use { resp ->
            lastUnauthorized = resp.code == 401
            val body = resp.body?.string() ?: return null
            if (!resp.isSuccessful) return null
            return try {
                gson.fromJson(body, JsonElement::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 上一次请求是否被 middleware 判为未登录（401） */
    var lastUnauthorized: Boolean = false
        private set

    // ---------------- POST ----------------

    fun post(path: String, body: JsonObject): JsonElement? {
        val req = Request.Builder()
            .url(url(path))
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json")
            .header("Referer", baseUrl + "/")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        client().newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: return null
            return try {
                gson.fromJson(text, JsonElement::class.java)
            } catch (e: Exception) {
                // 某些接口只返回 ok 字符串
                val o = JsonObject()
                o.addProperty("raw", text)
                o.addProperty("ok", resp.isSuccessful)
                o
            }
        }
    }

    /** 判断接口返回是否成功（兼容 {ok:true} / {error:...}） */
    fun isOk(res: JsonElement?): Boolean {
        if (res == null) return false
        if (!res.isJsonObject) return false
        val o = res.asJsonObject
        if (o.has("error")) return false
        if (o.has("ok")) return o.get("ok").asBoolean
        if (o.has("success")) return o.get("success").asBoolean
        return true
    }

    fun errorOf(res: JsonElement?): String {
        if (res == null) return "请求失败"
        if (!res.isJsonObject) return "未知响应"
        val o = res.asJsonObject
        return if (o.has("error")) o.get("error").asString else "操作失败"
    }

    /**
     * auth cookie 是 URL 编码的 JSON，里面有 role / username。
     * 取出来存到 Prefs，管理面板据此判断是否放行。
     */
    fun saveRoleFromCookie() {
        val raw = prefs.cookie
        if (raw.isBlank()) return
        try {
            val json = java.net.URLDecoder.decode(raw, "UTF-8")
            val o = gson.fromJson(json, JsonObject::class.java) ?: return
            if (o.has("role")) prefs.role = o.get("role").asString
            if (o.has("username")) {
                val u = o.get("username").asString
                if (u.isNotEmpty()) prefs.username = u
            }
        } catch (e: Exception) {
            // cookie 可能是 key=value 形式，忽略即可
        }
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
