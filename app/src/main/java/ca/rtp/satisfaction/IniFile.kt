package ca.rtp.satisfaction

import java.io.File

/** Minimal INI reader: [sections], key = value, ';' or '#' comments. Keys are case-insensitive. */
class IniFile(private val data: Map<String, Map<String, String>>) {

    fun get(section: String, key: String, default: String = ""): String =
        data[section.lowercase()]?.get(key.lowercase())?.takeIf { it.isNotEmpty() } ?: default

    fun int(section: String, key: String, default: Int): Int =
        get(section, key).toIntOrNull() ?: default

    fun bool(section: String, key: String, default: Boolean): Boolean =
        when (get(section, key).lowercase()) {
            "true", "yes", "1", "on" -> true
            "false", "no", "0", "off" -> false
            else -> default
        }

    /** All keys in a section, for future feature flags. */
    fun section(section: String): Map<String, String> = data[section.lowercase()] ?: emptyMap()

    companion object {
        fun parse(text: String): IniFile {
            val result = linkedMapOf<String, MutableMap<String, String>>()
            var current = result.getOrPut("") { linkedMapOf() }
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) return@forEach
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = result.getOrPut(line.substring(1, line.length - 1).trim().lowercase()) { linkedMapOf() }
                    return@forEach
                }
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                val key = line.substring(0, eq).trim().lowercase()
                var value = line.substring(eq + 1).trim()
                if (value.length >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length - 1)
                }
                current[key] = value
            }
            return IniFile(result)
        }

        fun load(file: File): IniFile = parse(file.readText(Charsets.UTF_8).removePrefix("﻿"))
    }
}
