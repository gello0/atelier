package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.ui.components.ExpandableNote
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiOutfitSuggestion
import com.example.data.model.ClosetItem
import com.example.data.model.Outfit
import com.example.data.weather.WeatherSnapshot
import com.example.ui.AiTaskState
import com.example.ui.DataUiState
import com.example.ui.components.OutfitStudioFlatLayCollage
import com.example.ui.components.WeatherHeroCard

val OCCASION_OPTIONS = listOf("Casual", "Work", "Evening", "Active", "Formal", "Travel")

@Composable
fun TodayStylistScreen(
    closetItemsState: DataUiState<List<ClosetItem>>,
    outfitsState: DataUiState<List<Outfit>>,
    weatherState: DataUiState<WeatherSnapshot>,
    selectedDayIndex: Int,
    useFahrenheit: Boolean,
    selectedOccasion: String,
    aiOutfitState: AiTaskState<List<AiOutfitSuggestion>>,
    isSeedingCapsule: Boolean,
    onSelectForecastDay: (Int) -> Unit,
    onToggleTempUnit: () -> Unit,
    onOpenCitySearch: () -> Unit,
    onRefreshGps: () -> Unit,
    onSelectOccasion: (String) -> Unit,
    onGenerateAiOutfits: () -> Unit,
    onSaveAiSuggestion: (AiOutfitSuggestion) -> Unit,
    onLogWearForOutfit: (String, String, List<String>, String) -> Unit,
    onToggleOutfitFavorite: (Outfit) -> Unit,
    onDeleteOutfit: (String) -> Unit,
    onSeedStarterCapsule: () -> Unit,
    onOpenAddGarment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val closetItems = (closetItemsState as? DataUiState.Success)?.data.orEmpty()
    val savedOutfits = (outfitsState as? DataUiState.Success)?.data.orEmpty()
    val weatherSnapshot = (weatherState as? DataUiState.Success)?.data
    val weatherError = (weatherState as? DataUiState.Error)?.message

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_stylist_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Live Weather Forecast Header
        item {
            WeatherHeroCard(
                weatherSnapshot = weatherSnapshot,
                selectedDayIndex = selectedDayIndex,
                useFahrenheit = useFahrenheit,
                isLoading = weatherState is DataUiState.Loading,
                errorMessage = weatherError,
                onSelectDay = onSelectForecastDay,
                onToggleUnit = onToggleTempUnit,
                onOpenCitySearch = onOpenCitySearch,
                onRefreshGps = onRefreshGps
            )
        }

        // 2. Empty Closet Onboarding Card (if user hasn't added items yet)
        if (closetItemsState is DataUiState.Success && closetItems.isEmpty()) {
            item {
                EmptyClosetWelcomeCard(
                    isSeeding = isSeedingCapsule,
                    onSeedStarterCapsule = onSeedStarterCapsule,
                    onOpenAddGarment = onOpenAddGarment
                )
            }
        }

        // 3. AI Weather Stylist Controls Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Outfit ideas",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "From your ${closetItems.size} items, for the selected day's weather",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Occasion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(OCCASION_OPTIONS) { occasion ->
                            FilterChip(
                                selected = selectedOccasion == occasion,
                                onClick = { onSelectOccasion(occasion) },
                                label = { Text(occasion) },
                                modifier = Modifier.testTag("occasion_chip_${occasion.lowercase()}")
                            )
                        }
                    }

                    Button(
                        onClick = onGenerateAiOutfits,
                        enabled = aiOutfitState !is AiTaskState.Loading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("curate_ai_outfits_button")
                    ) {
                        if (aiOutfitState is AiTaskState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Putting outfits together…")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Suggest outfits")
                        }
                    }

                    if (aiOutfitState is AiTaskState.Error) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = aiOutfitState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. AI Curated Suggestions Section
        if (aiOutfitState is AiTaskState.Success) {
            item {
                Text(
                    text = "Ideas for $selectedOccasion",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            items(aiOutfitState.data) { suggestion ->
                AiSuggestionCard(
                    suggestion = suggestion,
                    closetItems = closetItems,
                    onSaveLook = { onSaveAiSuggestion(suggestion) },
                    onWearToday = {
                        onLogWearForOutfit(
                            "",
                            suggestion.outfitName,
                            suggestion.itemIds,
                            suggestion.occasion
                        )
                    }
                )
            }
        }

        // 5. Saved Outfits Lookbook Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved outfits (${savedOutfits.size})",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        if (savedOutfits.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checkroom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No saved outfits yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Save a suggestion above, or build your own look in the Studio tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(savedOutfits, key = { it.id }) { outfit ->
                SavedOutfitCard(
                    outfit = outfit,
                    closetItems = closetItems,
                    onWearToday = {
                        onLogWearForOutfit(
                            outfit.id,
                            outfit.name,
                            outfit.itemIds,
                            outfit.occasion
                        )
                    },
                    onToggleFavorite = { onToggleOutfitFavorite(outfit) },
                    onDelete = { onDeleteOutfit(outfit.id) }
                )
            }
        }
    }
}

@Composable
private fun EmptyClosetWelcomeCard(
    isSeeding: Boolean,
    onSeedStarterCapsule: () -> Unit,
    onOpenAddGarment: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_closet_welcome_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add your clothes",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Your closet is empty. Add photos of your clothes, or try the app with some sample items.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onSeedStarterCapsule,
                    enabled = !isSeeding,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("seed_starter_capsule_button")
                ) {
                    if (isSeeding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading...")
                    } else {
                        Text("Sample items")
                    }
                }
                OutlinedButton(
                    onClick = onOpenAddGarment,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_add_garment_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Piece")
                }
            }
        }
    }
}

@Composable
private fun AiSuggestionCard(
    suggestion: AiOutfitSuggestion,
    closetItems: List<ClosetItem>,
    onSaveLook: () -> Unit,
    onWearToday: () -> Unit
) {
    val matchedItems = suggestion.itemIds.mapNotNull { id ->
        closetItems.firstOrNull { it.id == id }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.outfitName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${suggestion.occasion} · ${matchedItems.size} pieces",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${suggestion.weatherComfortScore}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            OutfitStudioFlatLayCollage(items = matchedItems)

            ExpandableNote(
                text = listOf(suggestion.stylistRationale, suggestion.layeringTip).filter { it.isNotBlank() }.joinToString("

"),
                label = "Why this works"
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onSaveLook,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save")
                }
                Button(
                    onClick = onWearToday,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Wearing today")
                }
            }
        }
    }
}

@Composable
private fun SavedOutfitCard(
    outfit: Outfit,
    closetItems: List<ClosetItem>,
    onWearToday: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val matchedItems = outfit.itemIds.mapNotNull { id ->
        closetItems.firstOrNull { it.id == id }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${outfit.name}\"?") },
            text = { Text("The outfit is removed. Your clothes stay in your closet.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = outfit.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${outfit.occasion} · ${outfit.targetWeatherSummary.ifBlank { "All Seasons" }} · Worn ${outfit.wearCount}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (outfit.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite Outfit",
                            tint = if (outfit.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Outfit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (matchedItems.isNotEmpty()) {
                OutfitStudioFlatLayCollage(items = matchedItems)
            }

            ExpandableNote(text = outfit.stylistNotes)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Comfort ${outfit.weatherComfortScore}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                OutlinedButton(onClick = onWearToday) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Wearing today")
                }
            }
        }
    }
}
