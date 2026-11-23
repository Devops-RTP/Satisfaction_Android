package ca.rtp.satisfaction

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val app get() = application as KioskApp
    private lateinit var cfg: KioskConfig
    private val handler = Handler(Looper.getMainLooper())

    private var facesPanel: View? = null
    private var thanksPanel: View? = null
    private var locked = false

    private var cornerTaps = 0
    private var firstCornerTap = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        cfg = app.reloadConfig()
        applyConfig()
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUi()
    }

    // ---- Kiosk behaviour --------------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* swallow: kiosk */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK) true else super.onKeyDown(keyCode, event)

    private fun applyConfig() {
        requestedOrientation = when (cfg.orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (cfg.ini.bool("general", "pin_screen", false)) startPinning()
        buildUi()
    }

    private fun startPinning() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try { startLockTask() } catch (_: Exception) {}
        }
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    // ---- UI ---------------------------------------------------------------------------------

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun text(s: String, sp: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(cfg.textColor)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun buildUi() {
        if (!::cfg.isInitialized) return
        val dm = resources.displayMetrics
        val landscape = dm.widthPixels >= dm.heightPixels
        val faceSize = minOf(dm.widthPixels / 6.2f, dm.heightPixels * (if (landscape) 0.38f else 0.16f)).toInt()
        val baseSp = if (landscape) 1f else 0.85f

        val root = FrameLayout(this).apply { setBackgroundColor(cfg.backgroundColor) }

        // Question + faces
        val faces = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
            addView(text(cfg.question, 40f * baseSp, bold = true))
            if (cfg.questionSecondary.isNotBlank()) addView(text(cfg.questionSecondary, 28f * baseSp).apply {
                setPadding(0, dp(8f), 0, 0)
            })
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(48f), 0, 0)
            }
            for (level in 1..5) {
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val face = SmileyView(context, level).apply { setOnClickListener { onRated(level) } }
                cell.addView(face, LinearLayout.LayoutParams(faceSize, faceSize))
                if (cfg.showLabels && cfg.labels[level - 1].isNotBlank()) {
                    cell.addView(text(cfg.labels[level - 1], 18f * baseSp).apply {
                        setPadding(0, dp(12f), 0, 0)
                    }, LinearLayout.LayoutParams(faceSize + dp(16f), LinearLayout.LayoutParams.WRAP_CONTENT))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val thanks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(SmileyView(context, 5), LinearLayout.LayoutParams(faceSize, faceSize))
            addView(text(cfg.thankYou, 44f * baseSp, bold = true).apply { setPadding(0, dp(32f), 0, 0) })
            if (cfg.thankYouSecondary.isNotBlank()) addView(text(cfg.thankYouSecondary, 30f * baseSp).apply {
                setPadding(0, dp(8f), 0, 0)
            })
        }

        // Invisible admin hotspot (top-right corner)
        val hotspot = View(this).apply { setOnClickListener { onCornerTap() } }

        val match = FrameLayout.LayoutParams.MATCH_PARENT
        root.addView(faces, FrameLayout.LayoutParams(match, match))
        root.addView(thanks, FrameLayout.LayoutParams(match, match))
        root.addView(hotspot, FrameLayout.LayoutParams(dp(110f), dp(110f), Gravity.TOP or Gravity.END))

        if (cfg.loadError != null) {
            root.addView(text(cfg.loadError!!, 14f).apply {
                setTextColor(android.graphics.Color.RED)
            }, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }

        setContentView(root)
        facesPanel = faces
        thanksPanel = thanks
        showFaces(!locked)
    }

    private fun showFaces(show: Boolean) {
        facesPanel?.visibility = if (show) View.VISIBLE else View.GONE
        thanksPanel?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun onRated(level: Int) {
        if (locked) return
        locked = true
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        app.record(level)
        showFaces(false)
        handler.postDelayed({
            locked = false
            showFaces(true)
        }, cfg.thankYouSeconds * 1000L)
    }

    // ---- Admin menu -------------------------------------------------------------------------

    private fun onCornerTap() {
        val now = System.currentTimeMillis()
        if (now - firstCornerTap > 3000) { firstCornerTap = now; cornerTaps = 0 }
        if (++cornerTaps >= 5) {
            cornerTaps = 0
            askPin()
        }
    }

    private fun askPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Admin")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == cfg.adminPin) adminMenu() else hideSystemUi()
            }
            .setNegativeButton("Cancel") { _, _ -> hideSystemUi() }
            .setOnDismissListener { hideSystemUi() }
            .show()
    }

    private fun adminMenu() {
        val items = arrayOf("Status", "Reload settings (kiosk.ini)", "Send queued responses now",
            "Open Android settings", "Exit kiosk")
        AlertDialog.Builder(this)
            .setTitle("Satisfaction Kiosk ${BuildConfigInfo.version(this)}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showStatus()
                    1 -> { cfg = app.reloadConfig(); applyConfig(); hideSystemUi() }
                    2 -> { app.uploader.poke(); hideSystemUi() }
                    3 -> exitKiosk(openSettings = true)
                    4 -> exitKiosk(openSettings = false)
                }
            }
            .setOnDismissListener { hideSystemUi() }
            .show()
    }

    private fun showStatus() {
        val pending = app.queue.count()
        val msg = """
            Config file:
            ${KioskConfig.configFile(this).absolutePath}

            Site: ${cfg.siteId}
            Device: ${cfg.deviceId}
            Destination: ${cfg.dbType}${if (cfg.dbType in setOf("http", "https", "rest", "api")) " → " + cfg.url else if (cfg.host.isNotBlank() && cfg.dbType != "none") " → " + cfg.host + "/" + cfg.database else ""}
            Waiting to upload: $pending
            Last upload: ${app.uploader.lastStatus}
            ${cfg.loadError ?: ""}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Status")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setOnDismissListener { hideSystemUi() }
            .show()
    }

    private fun exitKiosk(openSettings: Boolean) {
        try { stopLockTask() } catch (_: Exception) {}
        if (openSettings) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            finishAndRemoveTask()
        }
    }
}

private object BuildConfigInfo {
    fun version(a: Activity): String = try {
        "v" + a.packageManager.getPackageInfo(a.packageName, 0).versionName
    } catch (_: Exception) { "" }
}
