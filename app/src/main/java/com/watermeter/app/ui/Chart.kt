package com.watermeter.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.watermeter.app.data.BillingPeriod
import com.watermeter.app.data.Meter
import com.watermeter.app.data.Reading
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Distinct colours for meter lines, in order of the meters list. */
val meterColours = listOf(
    Color(0xFF1E88E5), // blue
    Color(0xFFF4511E), // orange
    Color(0xFF43A047), // green
    Color(0xFF8E24AA), // purple
    Color(0xFFE53935), // red
    Color(0xFF00897B), // teal
    Color(0xFFFDD835), // yellow
    Color(0xFF6D4C41), // brown
)

fun meterColour(index: Int) = meterColours[index % meterColours.size]

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * Cumulative water used during [period], one coloured line per meter.
 * Each line starts at 0 from the meter's last reading before the period (or its first reading in it).
 */
@Composable
fun UsageChart(
    meters: List<Meter>,
    readingsFor: (Long) -> List<Reading>,
    period: BillingPeriod,
    modifier: Modifier = Modifier,
) {
    val start = period.startMillis()
    val end = period.endMillisExclusive()
    val now = System.currentTimeMillis()

    // Points per meter: (time, kL used since baseline).
    val series = meters.mapIndexed { i, m ->
        val sorted = readingsFor(m.id).sortedBy { it.timestamp }
        val inPeriod = sorted.filter { it.timestamp in start until end }
        val baseline = sorted.lastOrNull { it.timestamp < start } ?: inPeriod.firstOrNull()
        val pts: List<Pair<Long, Double>> = if (baseline == null) emptyList() else buildList {
            add(maxOf(baseline.timestamp, start) to 0.0)
            inPeriod.filter { it.id != baseline.id }.forEach { add(it.timestamp to it.value - baseline.value) }
        }
        Triple(m, meterColour(i), pts)
    }
    val maxY = series.flatMap { it.third }.maxOfOrNull { it.second }?.coerceAtLeast(0.1) ?: 1.0
    val minY = series.flatMap { it.third }.minOfOrNull { it.second }?.coerceAtMost(0.0) ?: 0.0
    val axisColour = MaterialTheme.colorScheme.outline
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val left = 56.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            val top = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val w = right - left
            val h = bottom - top
            val yRange = (maxY - minY) * 1.1
            fun x(t: Long) = left + (t - start).toFloat() / (end - start) * w
            fun y(v: Double) = bottom - ((v - minY) / yRange * h).toFloat()

            val paint = Paint().apply {
                color = labelColour
                textSize = 11.dp.toPx()
                isAntiAlias = true
            }

            // Horizontal grid with kL labels.
            for (i in 0..4) {
                val v = minY + yRange * i / 4
                val yy = y(v)
                drawLine(axisColour.copy(alpha = 0.3f), Offset(left, yy), Offset(right, yy), 1f)
                paint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.getDefault(), "%.2f", v), left - 6.dp.toPx(), yy + 4.dp.toPx(), paint,
                )
            }
            // Date labels: start, middle, end.
            paint.textAlign = Paint.Align.CENTER
            val mid = period.start.plusDays(period.lengthDays / 2)
            listOf(period.start, mid, period.endInclusive).forEachIndexed { i, d ->
                val xx = when (i) { 0 -> left + 16.dp.toPx(); 2 -> right - 16.dp.toPx(); else -> (left + right) / 2 }
                drawContext.canvas.nativeCanvas.drawText(d.format(dayFmt), xx, size.height - 4.dp.toPx(), paint)
            }
            drawLine(axisColour, Offset(left, top), Offset(left, bottom), 1.5f)
            drawLine(axisColour, Offset(left, bottom), Offset(right, bottom), 1.5f)

            // Today marker.
            if (now in start until end) {
                val xx = x(now)
                drawLine(axisColour.copy(alpha = 0.6f), Offset(xx, top), Offset(xx, bottom), 1f)
            }

            for ((_, colour, pts) in series) {
                if (pts.isEmpty()) continue
                val path = Path()
                pts.forEachIndexed { i, (t, v) ->
                    if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
                }
                drawPath(path, colour, style = Stroke(width = 3.dp.toPx()))
                pts.forEach { (t, v) -> drawCircle(colour, 4.dp.toPx(), Offset(x(t), y(v))) }
            }
        }

        // Legend
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            series.forEach { (m, colour, pts) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(colour, CircleShape))
                    val total = pts.lastOrNull()?.second
                    Text(
                        m.name + (total?.let { " · ${fmtKl(it)} kL" } ?: " · no data"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
