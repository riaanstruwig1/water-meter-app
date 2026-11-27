package com.watermeter.app.data

/**
 * A physical water meter on the property.
 *
 * @param serial serial number printed on the meter label / encoded in its barcode.
 * @param wholeDigits number of black (kilolitre) wheels.
 * @param decimalDigits number of red (fraction of a kilolitre) wheels.
 */
data class Meter(
    val id: Long = 0,
    val name: String,
    val serial: String = "",
    val wholeDigits: Int = 5,
    val decimalDigits: Int = 4,
    val accountNumber: String = "",
)

/** How the meter for a reading was identified. */
enum class IdSource { BARCODE, PRINTED_SERIAL, MANUAL }

/**
 * One meter reading.
 *
 * @param value meter value in kilolitres (kL = m³), as shown on the meter dial.
 * @param timestamp when the reading was taken, epoch millis.
 * @param photoPath absolute path of the photo inside app storage, or null.
 */
data class Reading(
    val id: Long = 0,
    val meterId: Long = 1,
    val timestamp: Long,
    val value: Double,
    val photoPath: String?,
    val note: String? = null,
    val idSource: IdSource = IdSource.MANUAL,
)

/** A record that the user submitted the readings for a given month ("2026-11"). */
data class Submission(
    val id: Long = 0,
    val month: String,
    val submittedAt: Long,
    val readingId: Long? = null,
)
