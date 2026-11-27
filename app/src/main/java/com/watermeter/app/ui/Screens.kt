@file:OptIn(ExperimentalMaterial3Api::class)

package com.watermeter.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import com.watermeter.app.data.BillingPeriod
import com.watermeter.app.data.IdSource
import com.watermeter.app.data.Meter
import com.watermeter.app.data.Reading
import com.watermeter.app.data.Schedule
import com.watermeter.app.data.Usage
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

internal sealed interface Screen {
    data object Home : Screen
    data class Add(val photoPath: String) : Screen
    data class Detail(val readingId: Long) : Screen
    data object Settings : Screen
    data object Meters : Screen
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
    val launchPicker = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
            launchPicker()
        }
    }

    if (screen != Screen.Home) BackHandler { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            state = state,
            onTakePhoto = launchCamera,
            onPickPhoto = launchPicker,
            onOpen = { screen = Screen.Detail(it.id) },
            onSettings = { screen = Screen.Settings },
            onMeters = { screen = Screen.Meters },
            onSubmit = { showSubmit = true },
        )
        is Screen.Add -> AddReadingScreen(
            state = state,
            photoPath = s.photoPath,
            onRetake = {
                File(s.photoPath).delete()
                screen = Screen.Home
                launchCamera()
            },
            onCancel = {
                File(s.photoPath).delete()
                screen = Screen.Home
            },
            onSaved = { screen = Screen.Home },
        )
        is Screen.Detail -> {
            val r = state.readings.firstOrNull { it.id == s.readingId }
            if (r == null) {
                LaunchedEffect(s) { screen = Screen.Home }
            } else {
                DetailScreen(r, state.meter(r.meterId), onBack = { screen = Screen.Home }, onDelete = {
                    screen = Screen.Home
                    state.deleteReading(r)
                })
            }
        }
        Screen.Settings -> SettingsScreen(state, onDone = { screen = Screen.Home })
        Screen.Meters -> MetersScreen(state, onDone = { screen = Screen.Home })
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
    onMeters: () -> Unit,
    onSubmit: () -> Unit,
) {
    val settingsVersion = state.settingsVersion
    val s = state.settings
    val startDay = s.periodStartDay
    val endDay = s.periodEndDay
    val price = s.unitPrice
    val currency = s.currency
    var period by remember(settingsVersion) {
        mutableStateOf(BillingPeriod.containing(LocalDate.now(), startDay, endDay))
    }
    var renaming by remember { mutableStateOf<Meter?>(null) }

    val meter = state.selectedMeter
    val readings = remember(state.readings, state.selectedMeterId) { state.readingsFor(state.selectedMeterId) }
    val usage = remember(readings, period) {
        Usage.forRange(readings, period.startMillis(), period.endMillisExclusive())
    }
    val intervals = remember(readings, period) {
        val start = period.startMillis()
        val end = period.endMillisExclusive()
        Usage.intervals(readings).filter { it.reading.timestamp in start until end }
    }
    val sinceSubmit = remember(readings, state.submissions) {
        Usage.sinceLastSubmission(readings, state.submissions)
    }
    val day = s.submissionDay
    val submitted = state.isSubmittedThisMonth()
    val due = Schedule.isDue(LocalDate.now(), day, submitted)
    val isCurrent = LocalDate.now().let { !it.isBefore(period.start) && !it.isAfter(period.endInclusive) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water Meter") },
                actions = {
                    IconButton(onClick = onMeters) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Meters") }
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
                            Text("Tap here to set the submission day, billing period and price per kL.")
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
                            Text("Readings due for submission", fontWeight = FontWeight.Bold)
                            Text("This month's readings must be submitted. Tap to submit or mark as done.")
                        }
                    }
                }
            }

            // Billing period selector
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { period = period.previous(startDay, endDay) }) { Text("◀") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${period.start.format(dateFmt)} – ${period.endInclusive.format(dateFmt)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (isCurrent) "Current billing period" else "Billing period",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { period = period.next(startDay, endDay) }) { Text("▶") }
                }
            }

            // All meters on one chart
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Water used this period (kL)", style = MaterialTheme.typography.titleSmall)
                        UsageChart(state.meters, state::readingsFor, period)
                    }
                }
            }

            // Meter selector
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.meters.forEachIndexed { i, m ->
                        FilterChip(
                            selected = m.id == state.selectedMeterId,
                            onClick = { state.selectedMeterId = m.id },
                            label = { Text(m.name) },
                            leadingIcon = {
                                Box(Modifier.size(10.dp).background(meterColour(i), CircleShape))
                            },
                        )
                    }
                    FilterChip(selected = false, onClick = onMeters, label = { Text("+ Meter") })
                }
            }

            // Summary for the selected meter
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(meter?.name ?: "Meter", style = MaterialTheme.typography.titleMedium)
                                if (meter != null && meter.serial.isNotBlank()) {
                                    Text("Serial ${meter.serial}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (meter != null) {
                                IconButton(onClick = { renaming = meter }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename or edit meter")
                                }
                            }
                        }
                        Text(
                            usage.usedKl?.let { fmtUsage(it) } ?: "Add at least two readings to see usage",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (price > 0 && usage.usedKl != null) {
                            Text("Cost: ${fmtMoney(usage.usedKl * price, currency)}", fontWeight = FontWeight.Bold)
                        }
                        if (usage.usedKl != null) {
                            Text("Over ${fmtDuration(usage.spanMillis)} between readings")
                        }
                        usage.perDayKl?.let { perDay ->
                            Text("About ${fmtUsage(perDay)} per day")
                            if (isCurrent) {
                                val projected = perDay * period.lengthDays
                                Text(
                                    "Projected for the period: ${fmtKl(projected)} kL" +
                                        if (price > 0) " (${fmtMoney(projected * price, currency)})" else "",
                                )
                            }
                        }
                        usage.latest?.let { Text("Latest reading: ${fmtReading(it.value)}") }
                        if (isCurrent) sinceSubmit?.let { Text("Since last submission: ${fmtUsage(it)}") }
                        if (day > 0 && isCurrent) {
                            Text(
                                if (submitted) "Submitted for this month ✓"
                                else "Submit by ${Schedule.dueDate(YearMonth.now(), day).format(dateFmt)}"
                            )
                        }
                        if (price <= 0) {
                            Text(
                                "Set the price per kL in Settings to see costs.",
                                style = MaterialTheme.typography.bodySmall,
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
                    OutlinedButton(onClick = onPickPhoto) { Text("Photo from gallery") }
                    if (day > 0) OutlinedButton(onClick = onSubmit) { Text("Submit readings") }
                }
            }

            item { Text("Readings · ${meter?.name ?: ""}", style = MaterialTheme.typography.titleMedium) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    ReadingsTable(intervals, usage, price, currency, onOpen)
                }
            }
            item {
                Text(
                    "Tap a row to see its photo. ✓ = meter confirmed by barcode.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    renaming?.let { m ->
        MeterEditDialog(
            meter = m,
            canDelete = false,
            onDismiss = { renaming = null },
            onSave = {
                state.saveMeter(it)
                renaming = null
            },
            onDelete = {},
        )
    }
}

// ---------------------------------------------------------------- Detail

@Composable
private fun DetailScreen(r: Reading, meter: Meter?, onBack: () -> Unit, onDelete: () -> Unit) {
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
            Text(meter?.name ?: "Unknown meter", fontWeight = FontWeight.Bold)
            Text(fmtWhen(r.timestamp))
            Text(
                when (r.idSource) {
                    IdSource.BARCODE -> "Meter confirmed by barcode"
                    IdSource.PRINTED_SERIAL -> "Meter matched from the printed serial number"
                    IdSource.MANUAL -> "Meter chosen by hand"
                },
                style = MaterialTheme.typography.bodySmall,
            )
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
private fun DayStepper(label: String, day: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onChange(if (day <= 1) 31 else day - 1) }) { Text("−") }
            Text("Day $day", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedButton(onClick = { onChange(if (day >= 31) 1 else day + 1) }) { Text("+") }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppState, onDone: () -> Unit) {
    val s = state.settings
    var day by remember { mutableIntStateOf(if (s.submissionDay == 0) 25 else s.submissionDay) }
    var hour by remember { mutableIntStateOf(s.reminderHour) }
    var minute by remember { mutableIntStateOf(s.reminderMinute) }
    var periodStart by remember { mutableIntStateOf(s.periodStartDay) }
    var periodEnd by remember { mutableIntStateOf(s.periodEndDay) }
    var priceText by remember { mutableStateOf(if (s.unitPrice > 0) s.unitPrice.toString() else "") }
    var currency by remember { mutableStateOf(s.currency) }
    val price = if (priceText.isBlank()) 0.0 else parseReading(priceText)
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
            Text("Billing period", style = MaterialTheme.typography.titleMedium)
            DayStepper("Starts on", periodStart) { periodStart = it }
            DayStepper("Ends on (next month)", periodEnd) { periodEnd = it }
            val example = BillingPeriod.containing(LocalDate.now(), periodStart, periodEnd)
            Text(
                "Current period: ${example.start.format(dateFmt)} to ${example.endInclusive.format(dateFmt)}",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Text("Water price", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.take(4) },
                    label = { Text("Currency") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price per kL") },
                    singleLine = true,
                    isError = price == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Used to work out the cost of each reading and each period. 1 kL = 1 000 litres.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Text("Submission reminder", style = MaterialTheme.typography.titleMedium)
            DayStepper("Submit on", day) { day = it }
            if (day > 28) Text("In shorter months the reminder comes on the last day of the month.", style = MaterialTheme.typography.bodySmall)
            Text("Reminder time", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
            }) { Text(String.format("%02d:%02d", hour, minute)) }

            Text(
                "Meter names, serial numbers and account numbers are under Meters (the list icon on the home screen), " +
                    "or tap the pencil next to a meter's name.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Button(enabled = price != null, onClick = {
                state.saveSettings(day, hour, minute, periodStart, periodEnd, price ?: 0.0, currency.trim().ifBlank { "R" })
                onDone()
            }) { Text("Save") }
        }
    }
}

// ---------------------------------------------------------------- Submit dialog

@Composable
private fun SubmitDialog(state: AppState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val submitted = state.isSubmittedThisMonth()
    val st = state.settings
    val period = BillingPeriod.containing(LocalDate.now(), st.periodStartDay, st.periodEndDay)
    val latest = state.meters.mapNotNull { m -> state.latestFor(m.id)?.let { m to it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (submitted) "Readings submitted" else "Submit your meter readings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (latest.isEmpty()) {
                    Text("You have no readings yet. Take a photo of each meter first, then submit.")
                }
                latest.forEach { (m, r) ->
                    val used = Usage.forRange(
                        state.readingsFor(m.id), period.startMillis(), period.endMillisExclusive(),
                    ).usedKl
                    Column {
                        Text("${m.name}: ${fmtReading(r.value)}", fontWeight = FontWeight.Bold)
                        Text(
                            "Taken ${fmtWhen(r.timestamp)}" + (used?.let { u ->
                                " · used ${fmtKl(u)} kL this period" +
                                    if (st.unitPrice > 0) " (${fmtMoney(u * st.unitPrice, st.currency)})" else ""
                            } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val missing = state.meters.filter { m -> latest.none { it.first.id == m.id } }
                if (missing.isNotEmpty()) {
                    Text(
                        "No reading yet for: " + missing.joinToString { it.name },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!submitted && latest.isNotEmpty()) {
                    Text("Share them with your municipality (email, WhatsApp or their app), then tap Mark as submitted.")
                }
                Spacer(Modifier.height(4.dp))
                if (latest.isNotEmpty()) {
                    OutlinedButton(onClick = { shareReadings(context, latest) }) {
                        Text("Share readings and photos")
                    }
                }
            }
        },
        confirmButton = {
            if (submitted) TextButton(onClick = { state.undoSubmitted(); onDismiss() }) { Text("Undo submitted") }
            else TextButton(onClick = { state.markSubmitted(); onDismiss() }, enabled = latest.isNotEmpty()) { Text("Mark as submitted") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (submitted) "Close" else "Later") } },
    )
}

private fun shareReadings(context: Context, latest: List<Pair<Meter, Reading>>) {
    val body = buildString {
        append("Water meter readings\n\n")
        latest.forEach { (m, r) ->
            append(m.name).append('\n')
            if (m.serial.isNotBlank()) append("Meter serial: ").append(m.serial).append('\n')
            if (m.accountNumber.isNotBlank()) append("Account number: ").append(m.accountNumber).append('\n')
            append("Reading: ").append(fmtReading(r.value)).append('\n')
            append("Date: ").append(fmtWhen(r.timestamp)).append("\n\n")
        }
    }.trim()
    val photos: ArrayList<Uri> = ArrayList(
        latest.mapNotNull { (_, r) -> r.photoPath?.let { File(it) }?.takeIf { it.exists() } }
            .map { Photos.uriFor(context, it) }
    )
    val intent = when {
        photos.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, photos)
        }
        photos.size == 1 -> Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, photos.first())
        }
        else -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
    }.apply {
        putExtra(Intent.EXTRA_SUBJECT, "Water meter readings")
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Submit readings via"))
}
