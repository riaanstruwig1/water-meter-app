package com.watermeter.app.ui

import java.time.format.DateTimeFormatter
import java.util.Locale
import com.watermeter.app.data.Schedule

val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

fun fmtReading(kl: Double): String = String.format(Locale.getDefault(), "%.4f kL", kl)

/** Usage in kL plus litres, e.g. "12.345 kL (12 345 L)". */
fun fmtUsage(kl: Double): String {
    val litres = kl * 1000
    return String.format(Locale.getDefault(), "%.3f kL (%,.0f L)", kl, litres)
}

fun fmtWhen(millis: Long): String = Schedule.toLocal(millis).format(dateTimeFmt)

/** Accepts both "1234.5" and "1234,5". */
fun parseReading(text: String): Double? =
    text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

/** "3d 4h", "5h 20m" or "12m". */
fun fmtDuration(millis: Long): String {
    val totalMin = millis / 60_000
    val d = totalMin / (60 * 24)
    val h = (totalMin / 60) % 24
    val m = totalMin % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

fun fmtMoney(amount: Double, currency: String): String =
    currency + String.format(Locale.getDefault(), " %,.2f", amount)

fun fmtKl(kl: Double): String = String.format(Locale.getDefault(), "%.3f", kl)
