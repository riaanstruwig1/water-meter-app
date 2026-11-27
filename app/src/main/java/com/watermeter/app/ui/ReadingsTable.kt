package com.watermeter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermeter.app.data.IdSource
import com.watermeter.app.data.Interval
import com.watermeter.app.data.RangeUsage
import com.watermeter.app.data.Reading

private val colDate = 118.dp
private val colReading = 96.dp
private val colUsed = 76.dp
private val colTime = 72.dp
private val colRate = 72.dp
private val colCost = 84.dp

@Composable
private fun Cell(text: String, width: Dp, bold: Boolean = false, end: Boolean = true, colour: androidx.compose.ui.graphics.Color? = null) {
    Text(
        text,
        modifier = Modifier.width(width).padding(horizontal = 4.dp, vertical = 8.dp),
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        fontWeight = if (bold) FontWeight.Bold else null,
        style = MaterialTheme.typography.bodySmall,
        color = colour ?: MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Table of readings, newest first: date and time, meter reading, kL used since the previous
 * reading, time between them, kL per day, and cost. A totals row for the period goes on top.
 */
@Composable
fun ReadingsTable(
    intervals: List<Interval>,
    total: RangeUsage,
    unitPrice: Double,
    currency: String,
    onOpen: (Reading) -> Unit,
) {
    val priced = unitPrice > 0
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            Cell("Date / time", colDate, bold = true, end = false)
            Cell("Reading kL", colReading, bold = true)
            Cell("Used kL", colUsed, bold = true)
            Cell("Time", colTime, bold = true)
            Cell("kL/day", colRate, bold = true)
            if (priced) Cell("Cost", colCost, bold = true)
        }
        Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
            Cell("Period total", colDate, bold = true, end = false)
            Cell(total.latest?.let { fmtKl(it.value) } ?: "–", colReading, bold = true)
            Cell(total.usedKl?.let { fmtKl(it) } ?: "–", colUsed, bold = true)
            Cell(if (total.usedKl != null) fmtDuration(total.spanMillis) else "–", colTime, bold = true)
            Cell(total.perDayKl?.let { fmtKl(it) } ?: "–", colRate, bold = true)
            if (priced) Cell(total.usedKl?.let { fmtMoney(it * unitPrice, currency) } ?: "–", colCost, bold = true)
        }
        if (intervals.isEmpty()) {
            Text(
                "No readings in this period.",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        intervals.sortedByDescending { it.reading.timestamp }.forEach { iv ->
            val negative = (iv.usedKl ?: 0.0) < 0
            val warn = if (negative) MaterialTheme.colorScheme.error else null
            HorizontalDivider()
            Row(Modifier.clickable { onOpen(iv.reading) }) {
                Cell(
                    fmtWhen(iv.reading.timestamp) + if (iv.reading.idSource == IdSource.BARCODE) " ✓" else "",
                    colDate, end = false,
                )
                Cell(fmtKl(iv.reading.value), colReading)
                Cell(iv.usedKl?.let { fmtKl(it) } ?: "–", colUsed, colour = warn)
                Cell(iv.spanMillis?.let { fmtDuration(it) } ?: "–", colTime)
                Cell(iv.perDayKl?.let { fmtKl(it) } ?: "–", colRate, colour = warn)
                if (priced) Cell(iv.usedKl?.let { fmtMoney(it * unitPrice, currency) } ?: "–", colCost, colour = warn)
            }
        }
    }
}
