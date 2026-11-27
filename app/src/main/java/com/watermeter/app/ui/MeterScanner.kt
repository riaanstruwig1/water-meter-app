package com.watermeter.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.watermeter.app.data.Meter
import com.watermeter.app.data.MeterMatch
import com.watermeter.app.data.MeterMatcher
import com.watermeter.app.data.OcrChar
import com.watermeter.app.data.OcrWord
import com.watermeter.app.data.ReadingGuess
import com.watermeter.app.data.ReadingParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the app could work out from one photo. Everything is a suggestion for the user to check. */
data class ScanResult(
    val barcodes: List<String>,
    val serialFragments: List<String>,
    val match: MeterMatch?,
    /** Best reading guess per meter id (digit layout differs per meter). */
    val guesses: Map<Long, ReadingGuess>,
)

/**
 * Reads the barcode, printed serial and counter digits from a meter photo, on the device.
 *
 * Meters are often mounted sideways or upside down, so text recognition runs on all four
 * rotations and the best-scoring reading wins.
 */
object MeterScanner {

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun scan(photoPath: String, meters: List<Meter>): ScanResult = withContext(Dispatchers.Default) {
        val upright = Photos.decode(photoPath, 1600)
            ?: return@withContext ScanResult(emptyList(), emptyList(), null, emptyMap())

        val barcodes = runCatching {
            val scanner = BarcodeScanning.getClient()
            try {
                scanner.process(InputImage.fromBitmap(upright, 0)).await().mapNotNull { it.rawValue }
            } finally {
                scanner.close()
            }
        }.getOrDefault(emptyList())

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val wordsPerRotation = mutableListOf<List<OcrWord>>()
        val lines = mutableListOf<String>()
        try {
            for (degrees in listOf(0, 90, 180, 270)) {
                val bmp = rotate(upright, degrees)
                val text = runCatching { recognizer.process(InputImage.fromBitmap(bmp, 0)).await() }.getOrNull()
                    ?: continue
                val words = mutableListOf<OcrWord>()
                for (block in text.textBlocks) for (line in block.lines) {
                    lines += line.text
                    for (el in line.elements) {
                        val box = el.boundingBox ?: continue
                        words += OcrWord(splitChars(el.text, box, bmp))
                    }
                }
                wordsPerRotation += words
                if (bmp !== upright) bmp.recycle()
            }
        } finally {
            recognizer.close()
        }

        val fragments = ReadingParser.serialFragments(lines)
        val match = MeterMatcher.match(meters, barcodes, fragments)
        val knownSerials = barcodes + meters.map { it.serial }.filter { it.isNotBlank() }

        val guesses = meters.associate { m ->
            m.id to wordsPerRotation
                .mapNotNull { ReadingParser.guess(it, m.wholeDigits, m.decimalDigits, knownSerials) }
                .maxByOrNull { it.score }
        }.filterValues { it != null }.mapValues { it.value!! }

        ScanResult(barcodes, fragments, match, guesses)
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Splits a word's box evenly into characters and checks each for a red wheel background. */
    private fun splitChars(text: String, box: Rect, bmp: Bitmap): List<OcrChar> {
        if (text.isEmpty()) return emptyList()
        val w = box.width().toFloat() / text.length
        return text.mapIndexed { i, c ->
            val left = box.left + i * w
            val right = left + w
            OcrChar(
                c = c,
                left = left,
                top = box.top.toFloat(),
                right = right,
                bottom = box.bottom.toFloat(),
                red = isRed(bmp, left.toInt(), box.top, right.toInt(), box.bottom),
            )
        }
    }

    /** True when a good share of the pixels in the box are clearly red. */
    private fun isRed(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): Boolean {
        val left = l.coerceIn(0, bmp.width - 1)
        val right = r.coerceIn(left + 1, bmp.width)
        val top = t.coerceIn(0, bmp.height - 1)
        val bottom = b.coerceIn(top + 1, bmp.height)
        var red = 0
        var total = 0
        val stepX = maxOf(1, (right - left) / 8)
        val stepY = maxOf(1, (bottom - top) / 8)
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val p = bmp.getPixel(x, y)
                val rr = Color.red(p)
                val gg = Color.green(p)
                val bb = Color.blue(p)
                if (rr > 90 && rr > gg * 1.5 && rr > bb * 1.4) red++
                total++
                x += stepX
            }
            y += stepY
        }
        return total > 0 && red.toDouble() / total > 0.3
    }
}
