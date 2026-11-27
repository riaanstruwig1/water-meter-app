package com.watermeter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.watermeter.app.reminder.Reminders
import com.watermeter.app.ui.AppRoot
import com.watermeter.app.ui.AppState
import com.watermeter.app.ui.WaterMeterTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHOW_SUBMIT = "show_submit"
    }

    /** Set when the app is opened from the reminder notification. */
    private val openedFromReminder = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.createChannel(this)
        Reminders.scheduleNext(this)
        openedFromReminder.value = intent?.getBooleanExtra(EXTRA_SHOW_SUBMIT, false) == true

        val state = AppState(applicationContext)
        setContent {
            WaterMeterTheme {
                AppRoot(state = state, openedFromReminder = openedFromReminder)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_SUBMIT, false)) openedFromReminder.value = true
    }
}
