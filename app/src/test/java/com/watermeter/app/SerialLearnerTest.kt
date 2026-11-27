package com.watermeter.app

import com.watermeter.app.data.IdSource
import com.watermeter.app.data.Meter
import com.watermeter.app.data.MeterMatcher
import com.watermeter.app.data.ReadingParser
import com.watermeter.app.data.SerialLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialLearnerTest {
    private val house = Meter(id = 1, name = "House")
    private val garden = Meter(id = 2, name = "Garden", serial = "8053016945")

    @Test fun firstBarcodeBecomesSerial() {
        val learned = SerialLearner.learn(house, listOf("SN2512341037"), emptyList(), "2505950", listOf(house, garden))
        assertEquals("SN2512341037", learned.serial)
        assertTrue(learned.serialHints.isEmpty())
    }

    @Test fun keepsLabelTextAsHintButNotTheReading() {
        val m = house.copy(serial = "SN2512341037")
        val learned = SerialLearner.learn(
            m,
            barcodes = emptyList(),
            labelFragments = listOf("251037", "002505950", "1037"),
            readingDigits = "2505950",
            otherMeters = listOf(m, garden),
        )
        // "1037" is already part of the serial; "002505950" is the reading.
        assertEquals(listOf("251037"), learned.serialHints)
    }

    @Test fun doesNotLearnAnotherMetersSerial() {
        val learned = SerialLearner.learn(house, emptyList(), listOf("53016945"), "11074322", listOf(house, garden))
        assertTrue(learned.serialHints.isEmpty())
    }

    @Test fun learnedHintRecognisesMeterLater() {
        val m = house.copy(serial = "SN2512341037", serialHints = listOf("251037"))
        val match = MeterMatcher.match(listOf(m, garden), emptyList(), listOf("251037"))!!
        assertEquals(1L, match.meter.id)
        assertEquals(IdSource.PRINTED_SERIAL, match.source)
    }

    @Test fun learnedBarcodeHintMatchesAsBarcode() {
        val m = house.copy(serial = "SN2512341037", serialHints = listOf("998877665"))
        assertEquals(1L, MeterMatcher.match(listOf(m, garden), listOf("998877665"), emptyList())!!.meter.id)
    }

    @Test fun readingLineIsNotALabel() {
        assertTrue(SerialLearner.looksLikeReading("011074322", "11074322"))
        assertFalse(SerialLearner.looksLikeReading("8053016945", "11074322"))
    }

    @Test fun labelFragmentsOnlyFromSerialLines() {
        val f = ReadingParser.labelFragments(listOf("SN25 1037", "7.B", "8053016945", "01107 4322"))
        assertTrue(f.contains("251037"))
        assertTrue(f.contains("8053016945"))
    }
}
