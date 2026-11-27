package com.watermeter.app.data

import android.content.Context

/** User settings, kept in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Day of month (1–31) the reading must be submitted. 0 = not set yet. */
    var submissionDay: Int
        get() = prefs.getInt("submission_day", 0)
        set(v) = prefs.edit().putInt("submission_day", v).apply()

    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 8)
        set(v) = prefs.edit().putInt("reminder_hour", v).apply()

    var reminderMinute: Int
        get() = prefs.getInt("reminder_minute", 0)
        set(v) = prefs.edit().putInt("reminder_minute", v).apply()

    /** Optional municipal account / meter number, included when sharing a reading. */
    var accountNumber: String
        get() = prefs.getString("account_number", "") ?: ""
        set(v) = prefs.edit().putString("account_number", v).apply()

    /** Billing period start day (1–31); the period runs to [periodEndDay] of the next month. */
    var periodStartDay: Int
        get() = prefs.getInt("period_start_day", 8)
        set(v) = prefs.edit().putInt("period_start_day", v).apply()

    var periodEndDay: Int
        get() = prefs.getInt("period_end_day", 9)
        set(v) = prefs.edit().putInt("period_end_day", v).apply()

    /** Price per kilolitre, 0 = not set. */
    var unitPrice: Double
        get() = prefs.getString("unit_price", "0")?.toDoubleOrNull() ?: 0.0
        set(v) = prefs.edit().putString("unit_price", v.toString()).apply()

    var currency: String
        get() = prefs.getString("currency", "R") ?: "R"
        set(v) = prefs.edit().putString("currency", v).apply()
}
