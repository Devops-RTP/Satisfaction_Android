package ca.rtp.satisfaction

import java.io.File

/** One key = value line, with the comment lines directly above it as help text. */
data class IniEntry(val section: String, val key: String, val value: String, val help: String)

/**
 * Minimal INI reader/writer: [sections], key = value, ';' or '#' comments.
 * Keys are case-insensitive. Writing preserves the original layout and comments.
 */
class IniFile(
    private val data: Map<String, Map<String, String>>,
    val entries: List<IniEntry>,
    val raw: String,
) {

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

    /**
     * Returns the file text with the given values replaced in place (comments and order kept).
     * Keys are "section.key" in lowercase. Keys not present in the file are appended to their section.
     */
    fun withValues(updates: Map<String, String>): String {
        val remaining = updates.toMutableMap()
        val out = StringBuilder()
        var section = ""
        val lines = raw.removePrefix("﻿").lines()

        fun flushMissing(sec: String) {
            val prefix = "$sec."
            remaining.keys.filter { it.startsWith(prefix) }.forEach { k ->
                out.append(k.removePrefix(prefix)).append(" = ").append(remaining.remove(k)).append('\n')
            }
        }

        lines.forEachIndexed { idx, line ->
            val t = line.trim()
            if (t.startsWith("[") && t.endsWith("]")) {
                flushMissing(section)
                section = t.substring(1, t.length - 1).trim().lowercase()
            } else if (t.isNotEmpty() && !t.startsWith(";") && !t.startsWith("#")) {
                val eq = line.indexOf('=')
                if (eq > 0) {
                    val key = line.substring(0, eq).trim().lowercase()
                    val id = "$section.$key"
                    if (remaining.containsKey(id)) {
                        out.append(line.substring(0, eq).trimEnd()).append(" = ")
                            .append(remaining.remove(id)).append('\n')
                        return@forEachIndexed
                    }
                }
            }
            out.append(line)
            if (idx < lines.size - 1) out.append('\n')
        }
        if (remaining.isNotEmpty()) {
            if (!out.endsWith("\n")) out.append('\n')
            flushMissing(section)
            remaining.keys.map { it.substringBefore('.') }.distinct().forEach { sec ->
                out.append("\n[").append(sec).append("]\n")
                flushMissing(sec)
            }
        }
        return out.toString()
    }

    companion object {
        fun parse(text: String): IniFile {
            val clean = text.removePrefix("﻿")
            val result = linkedMapOf<String, MutableMap<String, String>>()
            val entries = ArrayList<IniEntry>()
            var sectionName = ""
            var current = result.getOrPut("") { linkedMapOf() }
            val help = ArrayList<String>()
            clean.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.isEmpty() -> help.clear()
                    line.startsWith(";") || line.startsWith("#") ->
                        help += line.trimStart(';', '#').trim()
                    line.startsWith("[") && line.endsWith("]") -> {
                        sectionName = line.substring(1, line.length - 1).trim().lowercase()
                        current = result.getOrPut(sectionName) { linkedMapOf() }
                        help.clear()
                    }
                    else -> {
                        val eq = line.indexOf('=')
                        if (eq > 0) {
                            val key = line.substring(0, eq).trim().lowercase()
                            var value = line.substring(eq + 1).trim()
                            if (value.length >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
                                        (value.startsWith("'") && value.endsWith("'")))) {
                                value = value.substring(1, value.length - 1)
                            }
                            current[key] = value
                            entries += IniEntry(sectionName, key, value, help.joinToString("\n"))
                        }
                        help.clear()
                    }
                }
            }
            return IniFile(result, entries, clean)
        }

        fun load(file: File): IniFile = parse(file.readText(Charsets.UTF_8))
    }
}
