package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClosetItem
import com.example.data.model.Outfit
import com.example.data.model.WearLog
import com.example.ui.DataUiState
import com.example.ui.components.GarmentVisualThumbnail
import com.example.ui.components.formatTemp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarHistoryScreen(
    closetItemsState: DataUiState<List<ClosetItem>>,
    outfitsState: DataUiState<List<Outfit>>,
    wearLogsState: DataUiState<List<WearLog>>,
    useFahrenheit: Boolean,
    onLogOutfitOnDate: (Outfit, String, String) -> Unit,
    onDeleteWearLog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val closetItems = (closetItemsState as? DataUiState.Success)?.data.orEmpty()
    val outfits = (outfitsState as? DataUiState.Success)?.data.orEmpty()
    val wearLogs = (wearLogsState as? DataUiState.Success)?.data.orEmpty()

    val calendarDates = remember {
        val today = LocalDate.now()
        (-7..7).map { offset -> today.plusDays(offset.toLong()) }
    }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    val selectedDateIso = selectedDate.toString()
    val logsForSelectedDate = remember(wearLogs, selectedDateIso) {
        wearLogs.filter { it.dateIso == selectedDateIso }
    }

    val mostWornPiece = remember(closetItems) {
        closetItems.maxByOrNull { it.wearCount }
    }
    val rainReadyCount = remember(closetItems) {
        closetItems.count { it.isRainResistant }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_history_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Log Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Wear Calendar & Insights",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Track outfits worn and plan upcoming forecast days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showScheduleDialog = true },
                    enabled = outfits.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("schedule_outfit_date_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Look")
                }
            }
        }

        // Closet Analytics Summary Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightMetricCard(
                    label = "Total Pieces",
                    value = "${closetItems.size}",
                    sublabel = "$rainReadyCount rainproof",
                    modifier = Modifier.weight(1f)
                )
                InsightMetricCard(
                    label = "Logged Wears",
                    value = "${wearLogs.size}",
                    sublabel = "${outfits.size} saved outfits",
                    modifier = Modifier.weight(1f)
                )
                InsightMetricCard(
                    label = "Top Staple",
                    value = mostWornPiece?.name?.take(12) ?: "—",
                    sublabel = if (mostWornPiece != null) "Worn ${mostWornPiece.wearCount}x" else "No wears yet",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Interactive 15-Day Date Strip (-7 days to +7 days)
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Select Date ($selectedDateIso)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(calendarDates) { date ->
                            val dateStr = date.toString()
                            val isSelected = date == selectedDate
                            val hasLog = wearLogs.any { it.dateIso == dateStr }
                            val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    hasLog -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                modifier = Modifier
                                    .width(58.dp)
                                    .clickable { selectedDate = date }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = dayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${date.dayOfMonth}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (hasLog) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "●",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Logs Section
        item {
            Text(
                text = "Outfits Logged for $selectedDateIso (${logsForSelectedDate.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (logsForSelectedDate.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No outfit logged on $selectedDateIso",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (outfits.isNotEmpty()) {
                                "Tap 'Log Look' above to assign a saved outfit to this date."
                            } else {
                                "Save an outfit in the Today or Studio tab first to log wears on the calendar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(logsForSelectedDate, key = { it.id }) { log ->
                WearLogCard(
                    log = log,
                    closetItems = closetItems,
                    useFahrenheit = useFahrenheit,
                    onDelete = { onDeleteWearLog(log.id) }
                )
            }
        }

        // All Recent Wear History Timeline
        if (wearLogs.isNotEmpty()) {
            item {
                Text(
                    text = "Complete Wear History (${wearLogs.size})",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            items(wearLogs, key = { "history_${it.id}" }) { log ->
                WearLogCard(
                    log = log,
                    closetItems = closetItems,
                    useFahrenheit = useFahrenheit,
                    onDelete = { onDeleteWearLog(log.id) }
                )
            }
        }
    }

    if (showScheduleDialog) {
        ScheduleOutfitDialog(
            dateIso = selectedDateIso,
            outfits = outfits,
            onConfirm = { outfit, notes ->
                onLogOutfitOnDate(outfit, selectedDateIso, notes)
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false }
        )
    }
}

@Composable
private fun InsightMetricCard(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WearLogCard(
    log: WearLog,
    closetItems: List<ClosetItem>,
    useFahrenheit: Boolean,
    onDelete: () -> Unit
) {
    val matchedItems = log.itemIds.mapNotNull { id ->
        closetItems.firstOrNull { it.id == id }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.outfitName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${log.dateIso} · ${log.occasion} · ${
                            formatTemp(log.weatherTempC, useFahrenheit)
                        } ${log.weatherCondition}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Wear Log",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (matchedItems.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(matchedItems) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(72.dp)
                        ) {
                            GarmentVisualThumbnail(item = item, size = 64.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (log.notes.isNotBlank()) {
                Text(
                    text = log.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScheduleOutfitDialog(
    dateIso: String,
    outfits: List<Outfit>,
    onConfirm: (Outfit, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOutfit by remember { mutableStateOf(outfits.firstOrNull()) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Outfit for $dateIso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose a saved outfit:",
                    style = MaterialTheme.typography.labelMedium
                )
                outfits.take(6).forEach { outfit ->
                    val isSelected = selectedOutfit?.id == outfit.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOutfit = outfit }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = outfit.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${outfit.occasion} · ${outfit.itemIds.size} pieces",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Comfort or Event Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = selectedOutfit
                    if (chosen != null) {
                        onConfirm(chosen, notes)
                    }
                },
                enabled = selectedOutfit != null
            ) {
                Text("Save Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
