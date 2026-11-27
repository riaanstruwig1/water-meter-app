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
}
