@file:OptIn(ExperimentalMaterial3Api::class)

package com.watermeter.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watermeter.app.data.Reading

/** Every photo on record for one meter, newest first, with date, time and reading. */
@Composable
internal fun PhotoHistoryScreen(state: AppState, meterId: Long, onBack: () -> Unit, onOpen: (Reading) -> Unit) {
    val meter = state.meter(meterId)
    val withPhotos = state.readingsFor(meterId)
        .filter { it.photoPath != null }
        .sortedByDescending { it.timestamp }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos · ${meter?.name ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    if (withPhotos.isEmpty()) "No photos for this meter yet."
                    else "${withPhotos.size} photos on record. Copies with the date, time and reading stamped on " +
                        "are also in your phone's gallery under Pictures/Water Meter (Android 10 and newer).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(withPhotos, key = { it.id }) { r -> PhotoTile(r, onClick = { onOpen(r) }) }
        }
    }
}

@Composable
private fun PhotoTile(r: Reading, onClick: () -> Unit) {
    val thumb by rememberPhoto(r.photoPath, 500)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
            thumb?.let {
                Image(
                    it.asImageBitmap(), contentDescription = "Meter photo",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0xAA000000)).padding(6.dp),
            ) {
                Text(fmtWhen(r.timestamp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text(fmtReading(r.value), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
