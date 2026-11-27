@file:OptIn(ExperimentalMaterial3Api::class)

package com.watermeter.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.watermeter.app.data.Meter

@Composable
internal fun MetersScreen(state: AppState, onDone: () -> Unit) {
    var editing by remember { mutableStateOf<Meter?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meters") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Meter(name = "Meter ${state.meters.size + 1}") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add meter") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Tip: when a photo shows a barcode that isn't saved yet, the app offers to save it to a meter. " +
                        "After that, each photo is matched to its meter automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(state.meters, key = { it.id }) { m ->
                val latest = state.latestFor(m.id)
                Card(Modifier.fillMaxWidth().clickable { editing = m }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.name, fontWeight = FontWeight.Bold)
                        Text(if (m.serial.isBlank()) "No serial saved" else "Serial ${m.serial}")
                        Text("${m.wholeDigits} black + ${m.decimalDigits} red wheels", style = MaterialTheme.typography.bodySmall)
                        if (m.accountNumber.isNotBlank()) Text("Account ${m.accountNumber}", style = MaterialTheme.typography.bodySmall)
                        latest?.let { Text("Latest ${fmtReading(it.value)} on ${fmtWhen(it.timestamp)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    editing?.let { m ->
        MeterEditDialog(
            meter = m,
            canDelete = m.id != 0L && state.meters.size > 1,
            onDismiss = { editing = null },
            onSave = {
                state.saveMeter(it)
                editing = null
            },
            onDelete = {
                state.deleteMeter(m)
                editing = null
            },
        )
    }
}

@Composable
private fun MeterEditDialog(
    meter: Meter,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Meter) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(meter.name) }
    var serial by remember { mutableStateOf(meter.serial) }
    var whole by remember { mutableStateOf(meter.wholeDigits.toString()) }
    var decimals by remember { mutableStateOf(meter.decimalDigits.toString()) }
    var account by remember { mutableStateOf(meter.accountNumber) }
    var confirmDelete by remember { mutableStateOf(false) }

    val wholeN = whole.toIntOrNull()?.takeIf { it in 1..9 }
    val decN = decimals.toIntOrNull()?.takeIf { it in 0..6 }
    val valid = name.isNotBlank() && wholeN != null && decN != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (meter.id == 0L) "Add meter" else "Edit meter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. House or Cottage") }, singleLine = true)
                OutlinedTextField(
                    serial, { serial = it.trim() },
                    label = { Text("Serial / barcode number") },
                    supportingText = { Text("Printed under the barcode on the meter label.") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        whole, { whole = it }, label = { Text("Black wheels") }, singleLine = true,
                        isError = wholeN == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        decimals, { decimals = it }, label = { Text("Red wheels") }, singleLine = true,
                        isError = decN == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(account, { account = it }, label = { Text("Account number (optional)") }, singleLine = true)
                if (canDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete meter", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    meter.copy(
                        name = name.trim(),
                        serial = serial,
                        wholeDigits = wholeN!!,
                        decimalDigits = decN!!,
                        accountNumber = account.trim(),
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${meter.name}?") },
            text = { Text("All readings and photos for this meter will be removed.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
