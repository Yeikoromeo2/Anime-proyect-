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

    /** Fechas en inglés de algunos sitios ("Nov. 06, 2026") → "06 nov 2026"; el resto se deja igual. */
    fun siteDate(text: String): String {
        val match = ENGLISH_DATE.find(text.trim()) ?: return text
        val (month, day, year) = match.destructured
        val es = MONTHS[month.lowercase()] ?: return text
        return "${day.padStart(2, '0')} $es $year"
    }

    private val ENGLISH_DATE = Regex("""^([A-Za-z]{3})[a-z]*\.?\s+(\d{1,2}),\s*(\d{4})$""")
    private val MONTHS = mapOf(
        "jan" to "ene", "feb" to "feb", "mar" to "mar", "apr" to "abr", "may" to "may", "jun" to "jun",
        "jul" to "jul", "aug" to "ago", "sep" to "sep", "oct" to "oct", "nov" to "nov", "dec" to "dic",
    )

    fun date(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es")).format(Date(timestamp))
}
