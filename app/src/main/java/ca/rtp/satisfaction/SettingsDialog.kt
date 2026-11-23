package ca.rtp.satisfaction

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

/**
 * In-app editor for kiosk.ini. The form is generated from the file itself (plus any keys that newer
 * app versions added to the built-in defaults), so new INI settings appear here automatically.
 */
class SettingsDialog(
    private val activity: Activity,
    private val onSaved: () -> Unit,
    private val onClosed: () -> Unit,
) {
    private val choices = mapOf(
        "database.type" to listOf("none", "http", "mssql", "mysql", "postgresql"),
        "general.orientation" to listOf("landscape", "portrait", "auto"),
    )
    private val secretWords = listOf("password", "pin", "api_key")

    /** Reads the current value from whichever widget was built for an entry. */
    private val readers = linkedMapOf<String, () -> String>()

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics).toInt()

    fun show() {
        val file = KioskConfig.configFile(activity)
        val current = try { IniFile.load(file) } catch (e: Exception) { IniFile.parse("") }
        val defaults = activity.assets.open(KioskConfig.FILE_NAME).use {
            IniFile.parse(it.readBytes().toString(Charsets.UTF_8))
        }
        // File entries first, then defaults the file doesn't have yet (in default order).
        val have = current.entries.map { "${it.section}.${it.key}" }.toSet()
        val entries = current.entries + defaults.entries.filter { "${it.section}.${it.key}" !in have }
        val helpFor = defaults.entries.associate { "${it.section}.${it.key}" to it.help }

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        }
        var lastSection: String? = null
        for (e in entries) {
            val id = "${e.section}.${e.key}"
            if (e.section != lastSection) {
                lastSection = e.section
                form.addView(TextView(activity).apply {
                    text = e.section.uppercase()
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(0, dp(18f), 0, dp(4f))
                })
            }
            form.addView(TextView(activity).apply {
                text = e.key
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(8f), 0, 0)
            })
            val help = e.help.ifBlank { helpFor[id].orEmpty() }
            if (help.isNotBlank()) form.addView(TextView(activity).apply {
                text = help
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                alpha = 0.7f
            })
            form.addView(widgetFor(id, e.value), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        form.addView(TextView(activity).apply {
            text = "File: ${file.absolutePath}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            alpha = 0.6f
            setPadding(0, dp(16f), 0, 0)
        })

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Kiosk settings")
            .setView(ScrollView(activity).apply { addView(form) })
            .setPositiveButton("Save", null)          // listeners set below so dialog stays open on error
            .setNeutralButton("Test connection", null)
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { onClosed() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = current.withValues(collect())
                try {
                    KioskConfig.save(activity, text)
                    dialog.dismiss()
                    onSaved()
                } catch (ex: Exception) {
                    message("Save failed", ex.message ?: ex.toString())
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { testConnection(current) }
        }
        dialog.show()
        // Wide dialog on tablets
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.8).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun widgetFor(id: String, value: String): View {
        val key = id.substringAfter('.')
        choices[id]?.let { opts ->
            val all = if (value.lowercase() in opts) opts else opts + value
            val sp = Spinner(activity)
            sp.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, all)
            sp.setSelection(all.indexOf(value.lowercase()).coerceAtLeast(0))
            readers[id] = { sp.selectedItem.toString() }
            return sp
        }
        if (value.lowercase() in setOf("true", "false")) {
            val sw = Switch(activity).apply { isChecked = value.equals("true", true) }
            readers[id] = { if (sw.isChecked) "true" else "false" }
            return sw
        }
        val et = EditText(activity).apply {
            setText(value)
            isSingleLine = true
            inputType = when {
                secretWords.any { key.contains(it) } ->
                    if (value.all { it.isDigit() } && key.contains("pin"))
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                value.isNotEmpty() && value.all { it.isDigit() } -> InputType.TYPE_CLASS_NUMBER
                key == "url" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                else -> InputType.TYPE_CLASS_TEXT
            }
        }
        readers[id] = { et.text.toString().trim() }
        return et
    }

    private fun collect(): Map<String, String> = readers.mapValues { it.value() }

    private fun testConnection(current: IniFile) {
        val cfg = KioskConfig.fromText(activity, current.withValues(collect()))
        val progress = AlertDialog.Builder(activity).setTitle("Testing ${cfg.dbType}…")
            .setMessage("Connecting…").setCancelable(false).show()
        Thread {
            val result = try {
                val sink = Sinks.create(cfg)
                if (sink == null) "type = none: nothing to connect to (responses stay on the tablet)."
                else { sink.test(); "Success - connected to ${cfg.dbType}." }
            } catch (e: Throwable) {
                "Failed: ${e.javaClass.simpleName}: ${e.message}"
            }
            activity.runOnUiThread {
                progress.dismiss()
                message("Connection test", result)
            }
        }.start()
    }

    private fun message(title: String, msg: String) {
        AlertDialog.Builder(activity).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }
}
