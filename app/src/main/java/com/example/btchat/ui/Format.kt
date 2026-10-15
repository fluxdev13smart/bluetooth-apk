package com.example.btchat.ui

import java.util.Locale

fun fmtSize(n: Long): String {
    if (n < 1024) return "$n B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = n / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

fun fmtEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds.coerceAtLeast(1)} s left"
    seconds < 3600 -> "${seconds / 60} min left"
    else -> "${seconds / 3600} h left"
}
