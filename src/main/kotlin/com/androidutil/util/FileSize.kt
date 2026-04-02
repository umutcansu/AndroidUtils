package com.androidutil.util

object FileSize {

    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return "%.1f %s".format(value, units[unitIndex])
    }

    fun formatDetailed(bytes: Long): String {
        return "${format(bytes)} ($bytes bytes)"
    }

    fun percentage(part: Long, total: Long): String {
        if (total == 0L) return "0.0%"
        return "%.1f%%".format(part.toDouble() / total * 100)
    }
}
