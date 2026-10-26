package com.yetvplus.nativeapp.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView

/**
 * 开屏页。
 *
 * 素材放在资源目录里即可自动生效，**不需要改代码**：
 * - 开屏图片：`app/src/main/res/drawable-nodpi/splash_image.png`（或 .jpg）
 * - 开屏视频：`app/src/main/res/raw/splash_video.mp4`
 *
 * 两个都放 → 优先播视频；都没放 → 立即进主页，不做停留。
 *
 * 资源是通过 `getIdentifier` **动态查找**的，所以目录为空时也能正常编译运行，
 * 你随时把素材丢进去重启一次就会生效。
 */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val goRunnable = Runnable { goMain() }

    /** 开屏最长停留时间（毫秒） */
    private val maxDurationMs = 5000L

    /** 只有图片时的停留时间 */
    private val imageDurationMs = 1800L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = packageName
        val videoId = resources.getIdentifier(SPLASH_VIDEO, "raw", pkg)
        val imageId = resources.getIdentifier(SPLASH_IMAGE, "drawable", pkg)

        // 没有素材就直接进主页，不额外停留
        if (videoId == 0 && imageId == 0) {
            goMain()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        if (videoId != 0) {
            // ---- 开屏视频 ----
            val video = VideoView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
                // 不裁切：保持原始比例居中显示
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    mp.setVideoScalingMode(
                        android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    )
                }
                setVideoURI(Uri.parse("android.resource://$pkg/$videoId"))
                setOnCompletionListener { goMain() }
                setOnErrorListener { _, _, _ -> goMain(); true }
            }
            root.addView(video)
            video.start()
            handler.postDelayed(goRunnable, maxDurationMs)
        } else {
            // ---- 开屏图片 ----
            val image = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
                // 不裁切、不拉伸，按原比例完整呈现
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setImageResource(imageId)
            }
            root.addView(image)
            handler.postDelayed(goRunnable, imageDurationMs)
        }

        setContentView(root)
    }

    /** 点一下就跳过开屏 */
    override fun onBackPressed() {
        super.onBackPressed()
        goMain()
    }

    override fun onDestroy() {
        handler.removeCallbacks(goRunnable)
        super.onDestroy()
    }

    private var going = false

    private fun goMain() {
        if (going || isFinishing) return
        going = true
        handler.removeCallbacks(goRunnable)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        // 开屏到主页不要切换动画，观感更连贯
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val SPLASH_VIDEO = "splash_video"
        const val SPLASH_IMAGE = "splash_image"
    }
}
