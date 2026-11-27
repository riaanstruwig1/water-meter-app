@file:OptIn(ExperimentalMaterial3Api::class)

package com.watermeter.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.watermeter.app.data.IdSource
import com.watermeter.app.data.Meter
import com.watermeter.app.data.MeterMatch

@Composable
internal fun AddReadingScreen(
    state: AppState,
    photoPath: String,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    /** True while the field holds the auto-read value the user hasn't changed. */
    var autoFilled by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var meterId by rememberSaveable { mutableLongStateOf(state.selectedMeterId) }
    var scanning by remember { mutableStateOf(true) }
    var scan by remember { mutableStateOf<ScanResult?>(null) }
    /** Updated after the user saves an unknown barcode onto a meter. */
    var match by remember { mutableStateOf<MeterMatch?>(null) }
    val photo by rememberPhoto(photoPath, 1600)

    fun fillGuess(forMeter: Long) {
        val g = scan?.guesses?.get(forMeter)
        if (g != null && (text.isBlank() || autoFilled)) {
            text = g.text
            autoFilled = true
        }
    }

    LaunchedEffect(photoPath) {
        val result = runCatching { MeterScanner.scan(photoPath, state.meters) }.getOrNull()
        scan = result
        match = result?.match
        result?.match?.let { meterId = it.meter.id }
        fillGuess(meterId)
        scanning = false
    }

    val meter = state.meter(meterId)
    val previous = state.latestFor(meterId)
    val value = parseReading(text)
    val guess = scan?.guesses?.get(meterId)

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

            if (scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading the meter number and barcode…")
                }
            } else {
                MeterStatus(
                    state = state,
                    scan = scan,
                    match = match,
                    selected = meter,
                    onAssignSerial = { code ->
                        meter?.let { m ->
                            val updated = m.copy(serial = code)
                            state.saveMeter(updated)
                            match = MeterMatch(updated, IdSource.BARCODE)
                        }
                    },
                    onAddMeter = { code ->
                        val base = state.selectedMeter ?: Meter(name = "")
                        val newMeter = Meter(
                            name = "Meter ${state.meters.size + 1}",
                            serial = code,
                            wholeDigits = base.wholeDigits,
                            decimalDigits = base.decimalDigits,
                        )
                        val id = state.saveMeter(newMeter)
                        meterId = id
                        match = MeterMatch(newMeter.copy(id = id), IdSource.BARCODE)
                    },
                )
            }

            Text("Meter", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.meters.forEach { m ->
                    FilterChip(
                        selected = m.id == meterId,
                        onClick = {
                            meterId = m.id
                            fillGuess(m.id)
                        },
                        label = { Text(m.name + if (match?.meter?.id == m.id) " ✓" else "") },
                    )
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    autoFilled = false
                },
                label = { Text("Meter reading (kL)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = text.isNotBlank() && value == null,
                supportingText = {
                    when {
                        scanning -> {}
                        autoFilled && guess != null -> Text(
                            "Read automatically from the photo. Check it against the meter, " +
                                "especially the last red wheel."
                        )
                        guess == null && !scanning -> Text("Couldn't read the numbers automatically. Type them in.")
                        else -> Text("Black wheels are kL, red wheels go after the decimal point.")
                    }
                },
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
                    if (d < 0) Text("This is lower than the previous reading. Double check it, or check the right meter is selected.", color = MaterialTheme.colorScheme.error)
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
                    enabled = value != null && meter != null,
                    onClick = {
                        val source = match?.takeIf { it.meter.id == meterId }?.source ?: IdSource.MANUAL
                        state.addReading(meterId, value!!, photoPath, note.ifBlank { null }, source)
                        onSaved()
                    },
                ) { Text("Save reading") }
            }
        }
    }
}

@Composable
private fun MeterStatus(
    state: AppState,
    scan: ScanResult?,
    match: MeterMatch?,
    selected: Meter?,
    onAssignSerial: (String) -> Unit,
    onAddMeter: (String) -> Unit,
) {
    val good = CardDefaults.cardColors(containerColor = Color(0xFFDFF5E1), contentColor = Color(0xFF1B5E20))
    val warn = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    val bad = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)

    val barcode = scan?.barcodes?.firstOrNull()
    when {
        match != null && selected != null && match.meter.id != selected.id -> StatusCard(bad) {
            Text("Wrong meter?", fontWeight = FontWeight.Bold)
            Text("The photo looks like ${match.meter.name}, but ${selected.name} is selected.")
        }
        match != null && match.source == IdSource.BARCODE -> StatusCard(good) {
            Text("✓ ${match.meter.name} confirmed by barcode", fontWeight = FontWeight.Bold)
            if (match.meter.serial.isNotBlank()) Text("Serial ${match.meter.serial}")
        }
        match != null && match.source == IdSource.PRINTED_SERIAL -> StatusCard(warn) {
            Text("${match.meter.name} matched from the printed serial", fontWeight = FontWeight.Bold)
            Text("The barcode couldn't be read, so check this is the right meter.")
        }
        barcode != null -> StatusCard(warn) {
            Text("New barcode: $barcode", fontWeight = FontWeight.Bold)
            Text("It doesn't match any of your meters yet.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected != null) {
                    OutlinedButton(onClick = { onAssignSerial(barcode) }) {
                        Text(if (selected.serial.isBlank()) "Save to ${selected.name}" else "Replace ${selected.name}'s serial")
                    }
                }
                OutlinedButton(onClick = { onAddMeter(barcode) }) { Text("Add new meter") }
            }
        }
        else -> StatusCard(warn) {
            Text("Couldn't identify the meter", fontWeight = FontWeight.Bold)
            Text(
                if (state.meters.size > 1) "No barcode or serial was readable. Pick the meter below."
                else "No barcode was readable. Using ${selected?.name ?: "your meter"}."
            )
        }
    }
}

@Composable
private fun StatusCard(colors: androidx.compose.material3.CardColors, content: @Composable () -> Unit) {
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}
