package com.yetvplus.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import com.yetvplus.nativeapp.data.ApiClient
import com.yetvplus.nativeapp.data.Prefs
import com.yetvplus.nativeapp.data.Repo

class YETVApp : Application(), ImageLoaderFactory {

    lateinit var prefs: Prefs
        private set
    lateinit var api: ApiClient
        private set
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = ApiClient(this)
        repo = Repo(api)
    }

    /** 图片加载：放宽 SSL 选项，避免自建证书站点图片加载失败 */
    override fun newImageLoader(): ImageLoader {
        val builder = OkHttpClient.Builder()
        if (prefs.ignoreSsl) {
            builder.hostnameVerifier { _, _ -> true }
        }
        return ImageLoader.Builder(this)
            .okHttpClient(builder.build())
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
