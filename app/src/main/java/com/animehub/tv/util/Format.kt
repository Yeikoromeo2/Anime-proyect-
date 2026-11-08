package com.animehub.tv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    /** 75000 → "1:15"; 3725000 → "1:02:05". */
    fun time(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** 12.0 → "12"; 12.5 → "12.5". */
    fun episode(number: Float): String =
        if (number % 1f == 0f) number.toInt().toString() else number.toString()

    fun date(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es")).format(Date(timestamp))
}
