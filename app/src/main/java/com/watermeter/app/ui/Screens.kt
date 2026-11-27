@file:OptIn(ExperimentalMaterial3Api::class)

package com.watermeter.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.watermeter.app.data.Reading
import com.watermeter.app.data.Schedule
import com.watermeter.app.data.Usage
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private sealed interface Screen {
    data object Home : Screen
    data class Add(val photoPath: String?) : Screen
    data class Detail(val readingId: Long) : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(state: AppState, openedFromReminder: MutableState<Boolean>) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showSubmit by remember { mutableStateOf(false) }
    var dueDialogShown by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Ask for notification permission (Android 13+), needed for the reminder pop-up.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Pop-up when opened from the notification, or on opening the app while a submission is due.
    LaunchedEffect(openedFromReminder.value) {
        if (openedFromReminder.value || (!dueDialogShown && state.isDueNow())) {
            showSubmit = true
            dueDialogShown = true
            openedFromReminder.value = false
        }
    }

    // Camera / gallery
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhoto
        pendingPhoto = null
        if (ok && path != null) screen = Screen.Add(path) else path?.let { File(it).delete() }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Photos.importFrom(context, uri)?.let { screen = Screen.Add(it.absolutePath) }
    }
    val launchCamera = {
        val f = Photos.newFile(context)
        pendingPhoto = f.absolutePath
        try {
            takePicture.launch(Photos.uriFor(context, f))
        } catch (e: Exception) {
            pendingPhoto = null
            f.delete()
            // No camera app: fall back to picking a photo.
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    if (screen != Screen.Home) BackHandler { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            state = state,
            onTakePhoto = launchCamera,
            onPickPhoto = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onOpen = { screen = Screen.Detail(it.id) },
            onSettings = { screen = Screen.Settings },
            onSubmit = { showSubmit = true },
        )
        is Screen.Add -> AddReadingScreen(
            state = state,
            photoPath = s.photoPath,
            onRetake = {
                s.photoPath?.let { File(it).delete() }
                screen = Screen.Home
                launchCamera()
            },
            onCancel = {
                s.photoPath?.let { File(it).delete() }
                screen = Screen.Home
            },
            onSaved = { screen = Screen.Home },
        )
        is Screen.Detail -> {
            val r = state.readings.firstOrNull { it.id == s.readingId }
            if (r == null) {
                LaunchedEffect(s) { screen = Screen.Home }
            } else {
                DetailScreen(r, onBack = { screen = Screen.Home }, onDelete = {
                    screen = Screen.Home
                    state.deleteReading(r)
                })
            }
        }
        Screen.Settings -> SettingsScreen(state, onDone = { screen = Screen.Home })
    }

    if (showSubmit) SubmitDialog(state, onDismiss = { showSubmit = false })
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeScreen(
    state: AppState,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpen: (Reading) -> Unit,
    onSettings: () -> Unit,
    onSubmit: () -> Unit,
) {
    val readings = state.readings
    val deltas = remember(readings) { Usage.deltas(readings) }
    val month = YearMonth.now()
    val usage = remember(readings) { Usage.forMonth(readings, month) }
    val sinceSubmit = remember(readings, state.submissions) {
        Usage.sinceLastSubmission(readings, state.submissions)
    }
    @Suppress("UNUSED_VARIABLE") val v = state.settingsVersion
    val day = state.settings.submissionDay
    val submitted = state.isSubmittedThisMonth()
    val due = Schedule.isDue(LocalDate.now(), day, submitted)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water Meter") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onTakePhoto,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New reading") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (day == 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Set your submission date", fontWeight = FontWeight.Bold)
                            Text("Tap here to choose the day each month your reading must be submitted.")
                        }
                    }
                }
            } else if (due) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSubmit),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Reading due for submission", fontWeight = FontWeight.Bold)
                            Text("This month's reading must be submitted. Tap to submit or mark as done.")
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(month.format(monthFmt), style = MaterialTheme.typography.titleMedium)
                        Text(
                            usage.usedKl?.let { fmtUsage(it) } ?: "Add at least two readings to see usage",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        usage.perDayKl?.let {
                            Text("About ${fmtUsage(it)} per day")
                        }
                        usage.latest?.let { Text("Latest reading: ${fmtReading(it.value)}") }
                        sinceSubmit?.let { Text("Since last submitted reading: ${fmtUsage(it)}") }
                        if (day > 0) {
                            val dueDate = Schedule.dueDate(month, day)
                            Text(
                                if (submitted) "Submitted for this month ✓"
                                else "Submit by ${dueDate.format(dateFmt)}"
                            )
                        }
                        if (usage.usedKl != null && usage.usedKl < 0) {
                            Text(
                                "The latest reading is lower than an earlier one. Check for a typo.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickPhoto) { Text("Use photo from gallery") }
                    if (day > 0) OutlinedButton(onClick = onSubmit) { Text("Submit reading") }
                }
            }

            item { Text("Readings", style = MaterialTheme.typography.titleMedium) }

            if (readings.isEmpty()) {
                item { Text("No readings yet. Tap New reading and take a photo of the meter.") }
            }

            items(readings.sortedByDescending { it.timestamp }, key = { it.id }) { r ->
                ReadingRow(r, deltas[r.id], onClick = { onOpen(r) })
            }
        }
    }
}

@Composable
private fun ReadingRow(r: Reading, delta: Double?, onClick: () -> Unit) {
    val thumb by rememberPhoto(r.photoPath, 200)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp)) {
                thumb?.let {
                    Image(
                        it.asImageBitmap(), contentDescription = "Meter photo",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fmtReading(r.value), fontWeight = FontWeight.Bold)
                Text(fmtWhen(r.timestamp), style = MaterialTheme.typography.bodySmall)
            }
            delta?.let {
                Text(
                    (if (it >= 0) "+" else "") + fmtUsage(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Add reading

@Composable
private fun AddReadingScreen(
    state: AppState,
    photoPath: String?,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val photo by rememberPhoto(photoPath, 1600)
    val previous = state.readings.maxByOrNull { it.timestamp }
    val value = parseReading(text)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New reading") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp)) {
                photo?.let {
                    Image(
                        it.asImageBitmap(), contentDescription = "Meter photo",
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text("Read the numbers off the photo and type them in (kilolitres).")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Meter reading (kL)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = text.isNotBlank() && value == null,
                modifier = Modifier.fillMaxWidth(),
            )
            previous?.let { p ->
                Text("Previous: ${fmtReading(p.value)} on ${fmtWhen(p.timestamp)}", style = MaterialTheme.typography.bodySmall)
                if (value != null) {
                    val d = value - p.value
                    Text(
                        "Usage since previous: ${fmtUsage(d)}",
                        color = if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (d < 0) Text("This is lower than the previous reading. Double check it.", color = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetake) { Text("Retake photo") }
                Button(
                    enabled = value != null,
                    onClick = {
                        state.addReading(System.currentTimeMillis(), value!!, photoPath, note.ifBlank { null })
                        onSaved()
                    },
                ) { Text("Save reading") }
            }
        }
    }
}

// ---------------------------------------------------------------- Detail

@Composable
private fun DetailScreen(r: Reading, onBack: () -> Unit, onDelete: () -> Unit) {
    val photo by rememberPhoto(r.photoPath, 2000)
    var confirm by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fmtReading(r.value)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(fmtWhen(r.timestamp))
            r.note?.let { Text(it) }
            photo?.let {
                Image(it.asImageBitmap(), contentDescription = "Meter photo", modifier = Modifier.fillMaxWidth())
            } ?: Text("No photo")
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this reading?") },
            text = { Text("The reading and its photo will be removed.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(state: AppState, onDone: () -> Unit) {
    val s = state.settings
    var day by remember { mutableIntStateOf(if (s.submissionDay == 0) 25 else s.submissionDay) }
    var hour by remember { mutableIntStateOf(s.reminderHour) }
    var minute by remember { mutableIntStateOf(s.reminderMinute) }
    var account by remember { mutableStateOf(s.accountNumber) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Submission day each month", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { day = if (day <= 1) 31 else day - 1 }) { Text("−") }
                Text("Day $day", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                OutlinedButton(onClick = { day = if (day >= 31) 1 else day + 1 }) { Text("+") }
            }
            if (day > 28) Text("In shorter months the reminder comes on the last day of the month.", style = MaterialTheme.typography.bodySmall)

            Text("Reminder time", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
            }) { Text(String.format("%02d:%02d", hour, minute)) }

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("Account or meter number (optional)") },
                supportingText = { Text("Added to the message when you share a reading.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Button(onClick = {
                state.saveSettings(day, hour, minute, account)
                onDone()
            }) { Text("Save") }
        }
    }
}

// ---------------------------------------------------------------- Submit dialog

@Composable
private fun SubmitDialog(state: AppState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val latest = state.readings.maxByOrNull { it.timestamp }
    val submitted = state.isSubmittedThisMonth()
    val usage = Usage.forMonth(state.readings, YearMonth.now())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (submitted) "Reading submitted" else "Submit your meter reading") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (latest == null) {
                    Text("You have no readings yet. Take a photo of the meter first, then submit it.")
                } else {
                    Text("Latest reading: ${fmtReading(latest.value)}", fontWeight = FontWeight.Bold)
                    Text("Taken ${fmtWhen(latest.timestamp)}")
                    usage.usedKl?.let { Text("Used this month: ${fmtUsage(it)}") }
                    if (!submitted) {
                        Text("Share it with your municipality (email, WhatsApp or their app), then tap Mark as submitted.")
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (latest != null) {
                    OutlinedButton(onClick = { shareReading(context, latest, state.settings.accountNumber) }) {
                        Text("Share reading and photo")
                    }
                }
            }
        },
        confirmButton = {
            if (submitted) TextButton(onClick = { state.undoSubmitted(); onDismiss() }) { Text("Undo submitted") }
            else TextButton(onClick = { state.markSubmitted(); onDismiss() }, enabled = latest != null) { Text("Mark as submitted") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (submitted) "Close" else "Later") } },
    )
}

private fun shareReading(context: android.content.Context, r: Reading, account: String) {
    val body = buildString {
        append("Water meter reading: ").append(fmtReading(r.value)).append('\n')
        append("Date: ").append(fmtWhen(r.timestamp)).append('\n')
        if (account.isNotBlank()) append("Account/meter number: ").append(account).append('\n')
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_SUBJECT, "Water meter reading")
        putExtra(Intent.EXTRA_TEXT, body)
        val photo = r.photoPath?.let { File(it) }?.takeIf { it.exists() }
        if (photo != null) {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Photos.uriFor(context, photo))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(intent, "Submit reading via"))
}
