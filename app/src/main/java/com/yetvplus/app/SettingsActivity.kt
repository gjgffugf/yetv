package com.yetvplus.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.yetvplus.app.util.ImageUtil
import com.yetvplus.app.util.Prefs
import com.yetvplus.app.util.showToast

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var switchBg: MaterialSwitch
    private lateinit var etBgUrl: TextInputEditText
    private lateinit var etServer: TextInputEditText
    private lateinit var etUa: TextInputEditText

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val dataUri = ImageUtil.toDataUri(this, uri)
            if (dataUri == null) {
                showToast(this, getString(R.string.toast_bg_pick_failed))
                return@registerForActivityResult
            }
            prefs.bgImage = dataUri
            prefs.bgEnabled = true
            switchBg.isChecked = true
            showToast(this, getString(R.string.toast_bg_saved))
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        etServer = findViewById(R.id.etServer)
        etUa = findViewById(R.id.etUa)
        etBgUrl = findViewById(R.id.etBgUrl)
        switchBg = findViewById(R.id.switchBg)

        bindServer()
        bindDisplay()
        bindBackground()
        bindAdvanced()
        bindAbout()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    // ---------------- 服务器 ----------------

    private fun bindServer() {
        etServer.setText(prefs.homeUrl())

        findViewById<View>(R.id.btnSaveServer).setOnClickListener {
            val input = etServer.text?.toString()?.trim().orEmpty()
            if (!input.startsWith("http://") && !input.startsWith("https://")) {
                showToast(this, getString(R.string.toast_url_invalid))
                return@setOnClickListener
            }
            prefs.baseUrl = input
            prefs.rememberServer(input)
            showToast(this, getString(R.string.toast_url_saved))
            finish()
        }

        findViewById<View>(R.id.btnHistory).setOnClickListener {
            val history = prefs.serverHistory
            if (history.isEmpty()) {
                showToast(this, getString(R.string.toast_no_history))
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.server_history)
                .setItems(history.toTypedArray()) { _, which ->
                    etServer.setText(history[which])
                }
                .show()
        }

        findViewById<View>(R.id.btnOpenAdmin).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("yetvplus://admin"), this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    // ---------------- 显示与交互 ----------------

    private fun bindDisplay() {
        val switchDesktop = findViewById<MaterialSwitch>(R.id.switchDesktop)
        val switchImmersive = findViewById<MaterialSwitch>(R.id.switchImmersive)
        val switchKeep = findViewById<MaterialSwitch>(R.id.switchKeepScreen)
        val switchLandscape = findViewById<MaterialSwitch>(R.id.switchAutoLandscape)
        val switchFab = findViewById<MaterialSwitch>(R.id.switchFab)
        val switchPull = findViewById<MaterialSwitch>(R.id.switchPullRefresh)
        val switchLongPress = findViewById<MaterialSwitch>(R.id.switchLongPress)

        switchDesktop.isChecked = prefs.desktopMode
        switchImmersive.isChecked = prefs.immersive
        switchKeep.isChecked = prefs.keepScreenOn
        switchLandscape.isChecked = prefs.autoLandscape
        switchFab.isChecked = prefs.showFab
        switchPull.isChecked = prefs.pullRefresh
        switchLongPress.isChecked = prefs.longPressSave

        switchDesktop.setOnCheckedChangeListener { _, checked -> prefs.desktopMode = checked }
        switchImmersive.setOnCheckedChangeListener { _, checked -> prefs.immersive = checked }
        switchKeep.setOnCheckedChangeListener { _, checked -> prefs.keepScreenOn = checked }
        switchLandscape.setOnCheckedChangeListener { _, checked -> prefs.autoLandscape = checked }
        switchFab.setOnCheckedChangeListener { _, checked -> prefs.showFab = checked }
        switchPull.setOnCheckedChangeListener { _, checked -> prefs.pullRefresh = checked }
        switchLongPress.setOnCheckedChangeListener { _, checked -> prefs.longPressSave = checked }

        etUa.setText(prefs.customUa)
        etUa.hint = getString(R.string.custom_ua_hint)
        findViewById<View>(R.id.btnSaveUa).setOnClickListener {
            prefs.customUa = etUa.text?.toString().orEmpty()
            showToast(this, getString(R.string.toast_ua_saved))
            finish()
        }
    }

    // ---------------- 背景图 ----------------

    private fun bindBackground() {
        val spinner = findViewById<Spinner>(R.id.spinnerScope)
        val sliderDim = findViewById<Slider>(R.id.sliderDim)
        val sliderBlur = findViewById<Slider>(R.id.sliderBlur)

        switchBg.isChecked = prefs.bgEnabled
        if (!prefs.bgImage.startsWith("data:")) {
            etBgUrl.setText(prefs.bgImage)
        }

        val entries = resources.getStringArray(R.array.bg_scope_entries)
        val values = resources.getStringArray(R.array.bg_scope_values)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            entries
        )
        val index = values.indexOf(prefs.bgScope)
        spinner.setSelection(if (index >= 0) index else 0)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                prefs.bgScope = values.getOrNull(position) ?: Prefs.SCOPE_ALL
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        sliderDim.value = (prefs.bgDim * 100f)
        sliderBlur.value = prefs.bgBlur.toFloat()

        sliderDim.addOnChangeListener { _, value, fromUser ->
            if (fromUser) prefs.bgDim = (value / 100f).coerceIn(0f, 0.85f)
        }
        sliderBlur.addOnChangeListener { _, value, fromUser ->
            if (fromUser) prefs.bgBlur = value.toInt().coerceIn(0, 20)
        }

        switchBg.setOnCheckedChangeListener { _, checked ->
            prefs.bgEnabled = checked
        }

        findViewById<View>(R.id.btnPickBg).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        findViewById<View>(R.id.btnApplyBgUrl).setOnClickListener {
            val url = etBgUrl.text?.toString()?.trim().orEmpty()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                showToast(this, getString(R.string.toast_url_invalid))
                return@setOnClickListener
            }
            prefs.bgImage = url
            prefs.bgEnabled = true
            switchBg.isChecked = true
            showToast(this, getString(R.string.toast_bg_saved))
            finish()
        }

        findViewById<View>(R.id.btnClearBg).setOnClickListener {
            prefs.bgImage = ""
            prefs.bgEnabled = false
            switchBg.isChecked = false
            etBgUrl.setText("")
            finish()
        }
    }

    // ---------------- 高级 ----------------

    private fun bindAdvanced() {
        val switchAdblock = findViewById<MaterialSwitch>(R.id.switchAdblock)
        val switchSsl = findViewById<MaterialSwitch>(R.id.switchIgnoreSsl)
        val switchMedia = findViewById<MaterialSwitch>(R.id.switchMediaHook)

        switchAdblock.isChecked = prefs.adblockEnabled
        switchSsl.isChecked = prefs.ignoreSsl
        switchMedia.isChecked = prefs.mediaHook

        switchAdblock.setOnCheckedChangeListener { _, checked -> prefs.adblockEnabled = checked }
        switchSsl.setOnCheckedChangeListener { _, checked -> prefs.ignoreSsl = checked }
        switchMedia.setOnCheckedChangeListener { _, checked -> prefs.mediaHook = checked }

        findViewById<View>(R.id.btnAdblockRules).setOnClickListener {
            val edit = EditText(this).apply {
                setText(prefs.adblockHosts)
                setTextColor(resources.getColor(R.color.text_primary, theme))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setSingleLine(false)
                setLines(8)
            }
            val container = FrameLayout(this)
            val padding = (24 * resources.displayMetrics.density).toInt()
            container.addView(
                edit,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = padding
                    marginEnd = padding
                }
            )
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.adblock_rules)
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ ->
                    prefs.adblockHosts = edit.text.toString()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        findViewById<View>(R.id.btnClearCache).setOnClickListener {
            try {
                WebView(this).apply {
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                    destroy()
                }
                showToast(this, getString(R.string.toast_cache_cleared))
            } catch (e: Exception) {
                showToast(this, getString(R.string.toast_cache_cleared))
            }
        }
    }

    // ---------------- 关于 ----------------

    private fun bindAbout() {
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val tvSite = findViewById<TextView>(R.id.tvSite)

        tvVersion.text = "${getString(R.string.about_version)}：${versionName()}"
        tvSite.text = "${getString(R.string.about_site)}：${prefs.homeUrl()}"

        findViewById<View>(R.id.btnOpenBrowser).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prefs.homeUrl())))
        }
    }

    private fun versionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
