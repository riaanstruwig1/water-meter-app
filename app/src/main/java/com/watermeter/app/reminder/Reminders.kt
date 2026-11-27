package com.watermeter.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.watermeter.app.MainActivity
import com.watermeter.app.R
import com.watermeter.app.data.MeterDatabase
import com.watermeter.app.data.Schedule
import com.watermeter.app.data.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

object Reminders {
    const val CHANNEL_ID = "submission_reminder"
    private const val NOTIFICATION_ID = 1001
    private const val REQUEST_CODE = 2001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reading submission reminder",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Tells you when the monthly water meter reading must be submitted"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** (Re)schedules the next reminder from the current settings and submission state. */
    fun scheduleNext(context: Context) {
        val settings = Settings(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context)
        am.cancel(pi)

        val submitted = MeterDatabase(context).isSubmitted(Schedule.monthKey(YearMonth.now()))
        val next = Schedule.nextReminder(
            now = LocalDateTime.now(),
            day = settings.submissionDay,
            time = LocalTime.of(settings.reminderHour, settings.reminderMinute),
            submittedThisMonth = submitted,
        ) ?: return

        // Inexact while-idle alarm: no special permission needed, fires within a few minutes.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Schedule.toMillis(next), pi)
    }

    /** True if the reading for this month should be submitted now. */
    fun isDueNow(context: Context): Boolean {
        val settings = Settings(context)
        val submitted = MeterDatabase(context).isSubmitted(Schedule.monthKey(YearMonth.now()))
        return Schedule.isDue(LocalDate.now(), settings.submissionDay, submitted)
    }

    fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_SUBMIT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Submit your water meter reading")
            .setContentText("Today is the day to submit this month's reading. Tap to open.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.createChannel(context)
        if (Reminders.isDueNow(context)) Reminders.showNotification(context)
        Reminders.scheduleNext(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.scheduleNext(context)
    }
}
