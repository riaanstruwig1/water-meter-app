package com.watermeter.app

import com.watermeter.app.data.IdSource
import com.watermeter.app.data.Meter
import com.watermeter.app.data.MeterMatcher
import com.watermeter.app.data.OcrChar
import com.watermeter.app.data.OcrWord
import com.watermeter.app.data.ReadingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingParserTest {

    /** Builds a word of evenly spaced characters starting at x, y. */
    private fun word(
        text: String,
        x: Float,
        y: Float,
        h: Float = 60f,
        w: Float = 40f,
        gap: Float = 10f,
        red: (Int) -> Boolean = { false },
    ) = OcrWord(text.mapIndexed { i, c ->
        val left = x + i * (w + gap)
        OcrChar(c, left, y, left + w, y + h, red(i))
    })

    @Test fun readsBlackAndRedWheelsBySplitColour() {
        // Like the first sample photo: label "7.B", serial under the barcode, then the counter.
        val words = listOf(
            word("7.B", 0f, 300f, h = 90f, w = 50f),
            word("53016945", 150f, 330f, h = 30f, w = 20f, gap = 4f),
            word("01107", 0f, 600f),
            word("4322", 260f, 600f, red = { true }),
        )
        val g = ReadingParser.guess(words, wholeDigits = 5, decimalDigits = 4, serials = listOf("8053016945"))!!
        assertEquals("01107.4322", g.text)
        assertEquals(1107.4322, g.value, 1e-9)
        assertTrue(g.splitByColour)
    }

    @Test fun fallsBackToConfiguredDigitCountWithoutColour() {
        val words = listOf(word("002505950", 0f, 100f))
        val g = ReadingParser.guess(words, wholeDigits = 5, decimalDigits = 4)!!
        assertEquals("00250.5950", g.text)
        assertEquals(250.595, g.value, 1e-9)
    }

    @Test fun mapsLookalikeLettersToDigits() {
        val words = listOf(word("OI1O7", 0f, 100f), word("4322", 260f, 100f, red = { true }))
        assertEquals("01107.4322", ReadingParser.guess(words, 5, 4)!!.text)
    }

    @Test fun ignoresPrintedSerialLabel() {
        val words = listOf(
            word("SN25", 0f, 0f, h = 30f, w = 20f, gap = 4f),
            word("1037", 200f, 0f, h = 30f, w = 20f, gap = 4f),
            word("00250", 0f, 200f),
            word("5950", 260f, 200f, red = { true }),
        )
        val g = ReadingParser.guess(words, 5, 4, serials = listOf("SN2512341037"))!!
        assertEquals("00250.5950", g.text)
    }

    @Test fun prefersCounterOverSmallUnrelatedNumbers() {
        val words = listOf(
            word("15", 0f, 0f, h = 20f, w = 10f),
            word("2024", 0f, 50f, h = 20f, w = 10f, gap = 2f),
            word("01107", 0f, 200f),
            word("4322", 260f, 200f, red = { true }),
        )
        assertEquals(1107.4322, ReadingParser.guess(words, 5, 4)!!.value, 1e-9)
    }

    @Test fun nothingReadable() {
        assertNull(ReadingParser.guess(listOf(word("ABC", 0f, 0f)), 5, 4))
        assertNull(ReadingParser.guess(emptyList(), 5, 4))
    }

    @Test fun serialFragments() {
        val f = ReadingParser.serialFragments(listOf("SN25 1037", "8053016945"))
        assertTrue(f.contains("8053016945"))
        assertTrue(f.contains("251037"))
    }

    private val house = Meter(id = 1, name = "House", serial = "8053016945")
    private val cottage = Meter(id = 2, name = "Cottage", serial = "SN2512341037")

    @Test fun matchesMeterByBarcode() {
        val m = MeterMatcher.match(listOf(house, cottage), listOf("8053016945"), emptyList())!!
        assertEquals(1L, m.meter.id)
        assertEquals(IdSource.BARCODE, m.source)
    }

    @Test fun barcodeWithPrefixStillMatches() {
        val m = MeterMatcher.match(listOf(house, cottage), listOf("SN2512341037"), emptyList())!!
        assertEquals(2L, m.meter.id)
    }

    @Test fun fallsBackToPartOfPrintedSerial() {
        // Barcode covered by tape; only the end of the printed serial is readable.
        val m = MeterMatcher.match(listOf(house, cottage), emptyList(), listOf("1037"))!!
        assertEquals(2L, m.meter.id)
        assertEquals(IdSource.PRINTED_SERIAL, m.source)
    }

    @Test fun unknownBarcodeMatchesNothing() {
        assertNull(MeterMatcher.match(listOf(house, cottage), listOf("99999999"), listOf("4322")))
    }
}
