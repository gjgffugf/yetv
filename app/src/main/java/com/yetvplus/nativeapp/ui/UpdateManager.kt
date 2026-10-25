package com.yetvplus.nativeapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import com.yetvplus.nativeapp.BuildConfig

/**
 * 应用内更新。
 *
 * 流程：请求更新 JSON → 对比版本号 → 提示 → 在应用内下载 APK → 调系统安装器安装。
 * 全程不依赖浏览器。
 *
 * JSON 地址走 GitHub raw，直连不通时依次尝试多个代理。
 *
 * 你需要做的：把下面的 OWNER / REPO / BRANCH 改成自己的仓库，并放一个 update.json：
 *
 * ```json
 * {
 *   "versionCode": 4,
 *   "versionName": "1.0.3",
 *   "apkUrl": "https://github.com/OWNER/REPO/releases/download/v1.0.3/app-release.apk",
 *   "changelog": "修复若干问题",
 *   "force": false
 * }
 * ```
 */
object UpdateManager {

    // ==================== 更新源 ====================
    private const val OWNER = "gjgffugf"
    private const val REPO = "update"
    private const val BRANCH = "main"
    private const val FILE = "yetvplusupdate.json"
    // ================================================

    private const val RAW = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$FILE"

    /** 代理候选：直连 raw 不通时依次尝试 */
    private fun candidates(): List<String> = listOf(
        RAW,
        "https://ghproxy.net/$RAW",
        "https://gh-proxy.com/$RAW",
        "https://gh.llkk.cc/$RAW",
        "https://raw.gitmirror.com/$OWNER/$REPO/$BRANCH/$FILE",
        "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/$FILE"
    )

    fun currentCode(): Int = BuildConfig.VERSION_CODE
    fun currentName(): String = BuildConfig.VERSION_NAME

    data class UpdateInfo(
        val versionCode: Int = 0,
        val versionName: String = "",
        val apkUrl: String = "",
        val changelog: String = "",
        val force: Boolean = false
    ) {
        /** 是否比当前版本新 */
        fun isNewer(): Boolean {
            if (versionCode > currentCode()) return true
            if (versionCode == 0 && versionName.isNotBlank()) {
                return compareNames(versionName, currentName()) > 0
            }
            return false
        }
    }

    /** 比较 1.0.2 这类点分版本号 */
    private fun compareNames(a: String, b: String): Int {
        val pa = a.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** 拉更新信息；全部失败返回 null */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in candidates()) {
            val got = fetchOnce(url)
            if (got != null) return@withContext got
        }
        null
    }

    private fun fetchOnce(url: String): UpdateInfo? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "YETVPlus/${currentName()}")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }
            val body = resp.body?.string().orEmpty()
            resp.close()
            if (!body.trimStart().startsWith("{")) return null
            val o = JSONObject(body)
            UpdateInfo(
                versionCode = o.optInt("versionCode", 0),
                versionName = o.optString("versionName", ""),
                apkUrl = o.optString("apkUrl", "").ifBlank { o.optString("url", "") },
                changelog = o.optString("changelog", "").ifBlank { o.optString("desc", "") },
                force = o.optBoolean("force", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 下载进度回调（0~100，-1 表示失败） */
    fun interface Progress {
        fun onProgress(percent: Int)
    }

    /** 下载 APK 到应用私有目录；成功返回文件，失败返回 null */
    suspend fun download(context: Context, info: UpdateInfo, progress: Progress): File? =
        withContext(Dispatchers.IO) {
            if (info.apkUrl.isBlank()) {
                progress.onProgress(-1)
                return@withContext null
            }
            val base: java.io.File? = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val dir: java.io.File = java.io.File(base ?: context.cacheDir, "update")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "YETVPlus-v${info.versionName}.apk")
            doDownload(info.apkUrl, file, progress)
        }

    private fun doDownload(url: String, file: File, progress: Progress): File? {
        return try {
            if (file.exists()) file.delete()
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                progress.onProgress(-1)
                return null
            }
            val body = resp.body
            if (body == null) {
                resp.close()
                progress.onProgress(-1)
                return null
            }
            val total = body.contentLength()
            var downloaded = 0L
            var last = -1
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                        downloaded += r
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            if (pct != last) {
                                last = pct
                                progress.onProgress(pct)
                            }
                        }
                    }
                }
            }
            resp.close()
            progress.onProgress(100)
            file
        } catch (e: Exception) {
            progress.onProgress(-1)
            null
        }
    }

    /** 调起系统安装器安装 APK */
    fun install(context: Context, file: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 兜底：跳到「允许安装未知应用」设置页
            try {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(i)
            } catch (ignored: Exception) {
            }
        }
    }
}
