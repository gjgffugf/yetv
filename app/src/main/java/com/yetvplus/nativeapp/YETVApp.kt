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

private const val IMG_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
private const val IMG_REFERER = "https://movie.douban.com/"

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
        repo = Repo(api, prefs)
    }

    /**
     * 图片加载。
     * 关键：豆瓣（doubanio.com）等图床有防盗链，不带 UA/Referer 会返回 403，
     * 表现就是「首页海报一片空白」。这里统一补上请求头。
     */
    override fun newImageLoader(): ImageLoader {
        val builder = OkHttpClient.Builder()
        if (prefs.ignoreSsl) {
            builder.hostnameVerifier { _, _ -> true }
        }
        builder.addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", IMG_UA)
                .header("Referer", IMG_REFERER)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .build()
            chain.proceed(req)
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
