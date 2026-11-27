package com.watermeter.app.ui

import java.time.format.DateTimeFormatter
import java.util.Locale
import com.watermeter.app.data.Schedule

val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

fun fmtReading(kl: Double): String = String.format(Locale.getDefault(), "%.3f kL", kl)

/** Usage in kL plus litres, e.g. "12.345 kL (12 345 L)". */
fun fmtUsage(kl: Double): String {
    val litres = kl * 1000
    return String.format(Locale.getDefault(), "%.3f kL (%,.0f L)", kl, litres)
}

fun fmtWhen(millis: Long): String = Schedule.toLocal(millis).format(dateTimeFmt)

/** Accepts both "1234.5" and "1234,5". */
fun parseReading(text: String): Double? =
    text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
