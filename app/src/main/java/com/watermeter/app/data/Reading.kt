package com.watermeter.app.data

/**
 * One meter reading.
 *
 * @param value meter value in kilolitres (kL = m³), as shown on the meter dial.
 * @param timestamp when the reading was taken, epoch millis.
 * @param photoPath absolute path of the photo inside app storage, or null.
 */
data class Reading(
    val id: Long = 0,
    val timestamp: Long,
    val value: Double,
    val photoPath: String?,
    val note: String? = null,
)

/** A record that the user submitted the reading for a given month ("2026-11"). */
data class Submission(
    val id: Long = 0,
    val month: String,
    val submittedAt: Long,
    val readingId: Long?,
)
