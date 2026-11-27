package com.watermeter.app.data

import kotlin.math.abs

/** One recognised character with its box in image pixels and whether it sits on a red wheel. */
data class OcrChar(
    val c: Char,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val red: Boolean = false,
) {
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
    val w get() = right - left
    val h get() = bottom - top
}

/** One recognised word, split into characters. [chars] keep the word's original text. */
data class OcrWord(val chars: List<OcrChar>) {
    val text get() = chars.joinToString("") { it.c.toString() }
}

/** A suggested meter reading read from a photo. */
data class ReadingGuess(
    val value: Double,
    /** Display form, e.g. "01107.4322". */
    val text: String,
    /** Higher is better; only meaningful for comparing guesses of the same photo. */
    val score: Double,
    /** True if the black/red split came from wheel colours rather than the configured digit count. */
    val splitByColour: Boolean,
)

/**
 * Turns OCR output from a photo of a drum-style water meter into a reading.
 *
 * Pure Kotlin so it can be unit tested; the Android side feeds it words from ML Kit.
 */
object ReadingParser {

    private val digitLookalikes = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'U' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1', '!' to '1', 'L' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'G' to '6', 'b' to '6',
        'T' to '7',
        'B' to '8',
        'g' to '9', 'q' to '9',
    )

    /**
     * Words that look like a serial-number label, e.g. "SN25…", or that are long digit strings
     * found in [serials] (barcode values, known meter serials). These are not readings.
     */
    private fun isSerialWord(text: String, serials: Collection<String>): Boolean {
        val t = text.uppercase()
        if (t.startsWith("SN") || t.startsWith("S/N") || t.startsWith("NO")) return true
        val digits = t.filter { it.isDigit() }
        if (digits.length >= 4 && serials.any { s -> s.filter { it.isDigit() }.contains(digits) }) return true
        return false
    }

    /** Maps lookalike letters to digits when the word is mostly digits already. */
    private fun normalise(word: OcrWord): List<OcrChar>? {
        val chars = word.chars.filter { !it.c.isWhitespace() }
        if (chars.isEmpty()) return null
        val digitCount = chars.count { it.c.isDigit() }
        val mappable = chars.count { it.c.isDigit() || it.c in digitLookalikes }
        // Needs a real digit, at most one unconvertible character, and either mostly digits
        // or entirely convertible (drum digits are often misread as O, I, S, B...).
        if (digitCount == 0 || mappable < chars.size - 1) return null
        if (digitCount * 2 < chars.size && mappable < chars.size) return null
        return chars.mapNotNull { ch ->
            when {
                ch.c.isDigit() -> ch
                ch.c in digitLookalikes -> ch.copy(c = digitLookalikes.getValue(ch.c))
                else -> null // drop stray punctuation such as '.' between wheels
            }
        }
    }

    /** Groups characters into horizontal runs (same text line, no big gaps). */
    private fun runs(chars: List<OcrChar>): List<List<OcrChar>> {
        if (chars.isEmpty()) return emptyList()
        val medianH = chars.map { it.h }.sorted()[chars.size / 2].coerceAtLeast(1f)
        val rows = mutableListOf<MutableList<OcrChar>>()
        for (ch in chars.sortedBy { it.cy }) {
            val row = rows.firstOrNull { r -> abs(r.map { it.cy }.average() - ch.cy) < medianH * 0.5 }
            if (row != null) row += ch else rows += mutableListOf(ch)
        }
        val out = mutableListOf<List<OcrChar>>()
        for (row in rows) {
            val sorted = row.sortedBy { it.cx }
            var current = mutableListOf(sorted.first())
            for (ch in sorted.drop(1)) {
                val prev = current.last()
                val gap = ch.left - prev.right
                val width = maxOf(prev.w, ch.w, 1f)
                if (gap > width * 1.6) {
                    out += current
                    current = mutableListOf(ch)
                } else {
                    current += ch
                }
            }
            out += current
        }
        return out
    }

    /**
     * Best reading guess from one orientation of the photo, or null.
     *
     * @param serials barcode values and known meter serials, so the serial label isn't read as the reading.
     */
    fun guess(
        words: List<OcrWord>,
        wholeDigits: Int,
        decimalDigits: Int,
        serials: Collection<String> = emptyList(),
    ): ReadingGuess? {
        val chars = words
            .filterNot { isSerialWord(it.text, serials) }
            .mapNotNull { normalise(it) }
            .flatten()
        val expected = wholeDigits + decimalDigits
        val maxHeight = chars.maxOfOrNull { it.h } ?: return null

        return runs(chars).mapNotNull { run ->
            if (run.size < 3) return@mapNotNull null
            val digits = run.joinToString("") { it.c.toString() }
            if (serials.any { s -> s.filter { it.isDigit() }.contains(digits) }) return@mapNotNull null

            // Colour split: black wheels followed by red wheels.
            val firstRed = run.indexOfFirst { it.red }
            val colourSplit = firstRed > 0 && run.drop(firstRed).all { it.red }

            val (whole, fraction) = when {
                colourSplit -> digits.substring(0, firstRed) to digits.substring(firstRed)
                digits.length > wholeDigits -> digits.substring(0, wholeDigits) to digits.substring(wholeDigits)
                else -> digits to ""
            }
            val frac = fraction.take(decimalDigits)
            val value = (whole + if (frac.isEmpty()) "" else ".$frac").toDoubleOrNull() ?: return@mapNotNull null

            val avgH = run.map { it.h }.average()
            var score = 0.0
            score -= abs(digits.length - expected) * 2.0 // right number of wheels
            score += avgH / maxHeight * 3.0              // big digits are the counter
            if (colourSplit) score += 3.0
            if (colourSplit && whole.length == wholeDigits) score += 2.0
            if (digits.length < wholeDigits) score -= 4.0

            ReadingGuess(
                value = value,
                text = if (frac.isEmpty()) whole else "$whole.$frac",
                score = score,
                splitByColour = colourSplit,
            )
        }.maxByOrNull { it.score }
    }

    /**
     * Digit groups from OCR lines that look like a serial-number label: lines with "SN" / "S/N",
     * or a single long number (7+ digits). Used to learn a meter's serial.
     */
    fun labelFragments(lines: List<String>): List<String> =
        lines.filter { line ->
            val compact = line.replace(" ", "")
            Regex("S/?N", RegexOption.IGNORE_CASE).containsMatchIn(compact) ||
                compact.count { it.isDigit() } >= 7 && compact.all { it.isDigit() }
        }.let { serialFragments(it) }

    /** Digit strings in OCR text that could be (part of) a printed serial number. */
    fun serialFragments(lines: List<String>): List<String> =
        lines.flatMap { line ->
            Regex("[0-9OIl]{4,}").findAll(line.replace(" ", "")).map { m ->
                m.value.map { digitLookalikes[it] ?: it }.joinToString("")
            }.toList()
        }.distinct()
}

/** How a meter was matched from a photo. */
data class MeterMatch(val meter: Meter, val source: IdSource)

object MeterMatcher {

    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Everything known about a meter's identity: its serial plus learned hints. */
    private fun ids(m: Meter): List<String> =
        (listOf(m.serial) + m.serialHints).map(::digits).filter { it.length >= 4 }.distinct()

    /**
     * Finds which meter a photo shows.
     *
     * A barcode is trusted when its digits end with the meter serial's digits or vice versa
     * (labels often print a prefix such as "SN"), or equal a barcode learned earlier.
     * Printed-serial text from OCR is weaker: a fragment of at least 4 digits matching the
     * start or end of a serial or learned hint is enough, since part of the label may be covered.
     */
    fun match(meters: List<Meter>, barcodes: List<String>, textFragments: List<String>): MeterMatch? {
        for (code in barcodes.map(::digits).filter { it.length >= 4 }) {
            meters.firstOrNull { m ->
                ids(m).any { s -> code.endsWith(s) || s.endsWith(code) }
            }?.let { return MeterMatch(it, IdSource.BARCODE) }
        }
        val frags = textFragments.map(::digits).filter { it.length >= 4 }
        val scored = meters.map { m ->
            val best = ids(m).maxOfOrNull { s ->
                frags.maxOfOrNull { f ->
                    when {
                        s == f -> 100
                        s.endsWith(f) || s.startsWith(f) -> f.length
                        f.endsWith(s) || f.startsWith(s) -> s.length
                        else -> 0
                    }
                } ?: 0
            } ?: 0
            m to best
        }.filter { it.second >= 4 }
        val top = scored.maxByOrNull { it.second } ?: return null
        // Ambiguous when two meters match equally well.
        if (scored.count { it.second == top.second } > 1) return null
        return MeterMatch(top.first, IdSource.PRINTED_SERIAL)
    }
}

/**
 * Learns a meter's serial from photos the user has confirmed by saving a reading.
 *
 * - The first barcode seen becomes the meter's serial if it has none.
 * - Other barcodes and serial-label text fragments are kept as hints, so a later photo with
 *   the barcode covered can still be recognised from whatever part of the label is readable.
 */
object SerialLearner {
    private const val MAX_HINTS = 12

    /** True if [fragment] is probably the meter reading rather than part of the serial. */
    fun looksLikeReading(fragment: String, readingDigits: String): Boolean {
        val f = fragment.trimStart('0')
        val r = readingDigits.trimStart('0')
        if (f.isEmpty() || r.isEmpty()) return true
        return r.contains(f) || f.contains(r.take(4))
    }

    fun learn(
        meter: Meter,
        barcodes: List<String>,
        labelFragments: List<String>,
        readingDigits: String,
        otherMeters: List<Meter>,
    ): Meter {
        val code = barcodes.firstOrNull { b -> b.count { it.isDigit() } >= 4 }
        var serial = meter.serial
        if (serial.isBlank() && code != null) serial = code

        val serialDigits = serial.filter { it.isDigit() }
        val othersIds = otherMeters.filter { it.id != meter.id }
            .flatMap { listOf(it.serial) + it.serialHints }
            .map { s -> s.filter { it.isDigit() } }
            .filter { it.length >= 4 }

        val candidates = (barcodes + labelFragments)
            .map { s -> s.filter { it.isDigit() } }
            .filter { it.length >= 4 }
            // Not the reading itself, nothing already covered by the serial, nothing that belongs to another meter.
            .filterNot { looksLikeReading(it, readingDigits) }
            .filterNot { serialDigits.contains(it) }
            .filterNot { f -> othersIds.any { o -> o.contains(f) || f.contains(o) } }

        val hints = (meter.serialHints + candidates).distinct().takeLast(MAX_HINTS)
        return meter.copy(serial = serial, serialHints = hints)
    }
}
