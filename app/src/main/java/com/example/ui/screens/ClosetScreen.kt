package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.ui.DataUiState
import com.example.ui.components.GarmentVisualThumbnail
import com.example.ui.components.StudioFabPurple
import com.example.ui.components.StudioObsidianBackground
import com.example.ui.components.StudioPillSurface
import com.example.ui.components.StudioTileBorder
import com.example.ui.components.StudioTileSurface
import com.example.util.ImageUtils

private enum class ClosetSortOrder(val label: String) {
    MOST_RECENT("Most recent"),
    WARMTH_HIGH("Warmest first"),
    MOST_WORN("Most worn")
}

private val STUDIO_CATEGORY_ORDER = listOf(
    GarmentCategory.TOP,
    GarmentCategory.FOOTWEAR,
    GarmentCategory.BOTTOM,
    GarmentCategory.OUTERWEAR,
    GarmentCategory.ACCESSORY,
    GarmentCategory.ONE_PIECE
)

private val TextPrimary = Color(0xFFECEFF4)
private val TextSecondary = Color(0xFF9AA3B2)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClosetScreen(
    closetItemsState: DataUiState<List<ClosetItem>>,
    isSeedingCapsule: Boolean,
    analyzingItemIds: Set<String>,
    onAddClick: () -> Unit,
    onBatchUploadPhotos: (List<Uri>) -> Unit,
    onFillInPending: (List<ClosetItem>) -> Unit,
    onCleanUpAllPhotos: () -> Unit,
    onFixAll: (Set<String>?) -> Unit,
    onRefreshDetails: (ClosetItem) -> Unit,
    onRedoPhoto: (ClosetItem) -> Unit,
    onRotatePhoto: (ClosetItem) -> Unit,
    onEditItem: (ClosetItem) -> Unit,
    onToggleFavorite: (ClosetItem) -> Unit,
    onDeleteItems: (Set<String>) -> Unit,
    onSeedStarterCapsule: () -> Unit,
    wearLogs: List<com.example.data.model.WearLog> = emptyList(),
    onWearToday: (ClosetItem) -> Unit = {},
    onMarkWashed: (Set<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<GarmentCategory?>(null) }
    var filterRainOnly by remember { mutableStateOf(false) }
    var filterFavoritesOnly by remember { mutableStateOf(false) }
    var filterNeedsWash by remember { mutableStateOf(false) }
    var useGridView by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(ClosetSortOrder.MOST_RECENT) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    var detailItemId by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }

    val allItems = (closetItemsState as? DataUiState.Success)?.data.orEmpty()
    val pendingItems = remember(allItems) { allItems.filter { it.isPendingAiAnalysis() } }
    val rawPhotoCount = remember(allItems) { allItems.count { ImageUtils.needsPhotoCleanup(it.imageBase64) } }
    // Drop selections for items that no longer exist.
    // Wears since each piece was last washed, from the wear history.
    val washCounts = remember(allItems, wearLogs) { allItems.associate { it.id to com.example.data.model.wearsSinceWash(it, wearLogs) } }
    val dirtyIds = remember(allItems, washCounts) {
        allItems.filter { com.example.data.model.needsWash(it, washCounts[it.id] ?: 0) }.map { it.id }.toSet()
    }
    val liveSelection = selection.filter { id -> allItems.any { it.id == id } }.toSet()
    val selecting = liveSelection.isNotEmpty()

    val batchPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris -> if (uris.isNotEmpty()) onBatchUploadPhotos(uris) }

    val filteredItems = remember(allItems, searchQuery, selectedCategory, filterRainOnly, filterFavoritesOnly, filterNeedsWash, dirtyIds, sortOrder) {
        val base = allItems.filter { item ->
            val q = searchQuery.trim()
            val matchesQuery = q.isBlank() ||
                item.name.contains(q, true) ||
                item.colorName.contains(q, true) ||
                item.subCategory.contains(q, true) ||
                item.materialComposition.contains(q, true) ||
                item.notes.contains(q, true)
            matchesQuery &&
                (selectedCategory == null || item.category == selectedCategory!!.name) &&
                (!filterRainOnly || item.isRainResistant) &&
                (!filterFavoritesOnly || item.isFavorite) &&
                (!filterNeedsWash || item.id in dirtyIds)
        }
        when (sortOrder) {
            ClosetSortOrder.MOST_RECENT -> base.sortedByDescending { it.createdAt?.seconds ?: 0L }
            ClosetSortOrder.WARMTH_HIGH -> base.sortedByDescending { it.warmthLevel }
            ClosetSortOrder.MOST_WORN -> base.sortedByDescending { it.wearCount }
        }
    }

    fun onItemTap(item: ClosetItem) {
        if (selecting) {
            selection = if (item.id in liveSelection) liveSelection - item.id else liveSelection + item.id
        } else {
            detailItemId = item.id
        }
    }

    fun onItemLongPress(item: ClosetItem) {
        selection = liveSelection + item.id
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidianBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("closet_screen_list"),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selecting) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = StudioPillSurface, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                            IconButton(onClick = { selection = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = TextPrimary)
                            }
                            Text("${liveSelection.size} selected", color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { selection = filteredItems.map { it.id }.toSet() }) { Text("Select all") }
                            TextButton(onClick = { onFixAll(liveSelection); selection = emptySet() }) { Text("Fix") }
                            IconButton(onClick = { confirmDelete = liveSelection }, modifier = Modifier.testTag("delete_selected_button")) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete selected", tint = Color(0xFFE07A5F))
                            }
                        }
                    }
                }
            }

            // Category pills
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(STUDIO_CATEGORY_ORDER) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) StudioFabPurple else StudioPillSurface,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF7B80B8) else StudioTileBorder),
                            modifier = Modifier.clickable { selectedCategory = if (selectedCategory == cat) null else cat }
                        ) {
                            Text(
                                text = cat.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Sort / search / view / menu
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                sortOrder = ClosetSortOrder.entries[(sortOrder.ordinal + 1) % ClosetSortOrder.entries.size]
                            }
                            .padding(6.dp)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Change sort order", tint = Color(0xFFD8DEE9), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(sortOrder.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (showSearchBar || searchQuery.isNotBlank()) Color(0xFFA5A9E2) else Color(0xFFD8DEE9)
                            )
                        }
                        IconButton(onClick = { useGridView = !useGridView }, modifier = Modifier.testTag("toggle_closet_view_mode_button")) {
                            Icon(
                                if (useGridView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.GridView,
                                contentDescription = "Switch grid or list",
                                tint = Color(0xFFD8DEE9)
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color(0xFFD8DEE9))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Add several photos") },
                                    leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        batchPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                                if (allItems.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Fix all (names, details, rotation)") },
                                        onClick = { showMenu = false; onFixAll(null) },
                                        modifier = Modifier.testTag("fix_all_button")
                                    )
                                }
                                if (rawPhotoCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Clean up photos ($rawPhotoCount)") },
                                        onClick = { showMenu = false; onCleanUpAllPhotos() }
                                    )
                                }
                                if (allItems.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Select items") },
                                        onClick = { showMenu = false; selection = setOfNotNull(filteredItems.firstOrNull()?.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Items that still need details: one quiet line
            if (pendingItems.isNotEmpty() || analyzingItemIds.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = StudioTileSurface, border = BorderStroke(1.dp, StudioTileBorder)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            if (analyzingItemIds.isNotEmpty()) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFA5A9E2))
                                Spacer(Modifier.width(10.dp))
                                Text("Updating ${analyzingItemIds.size} item(s)…", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            } else {
                                Text(
                                    "${pendingItems.size} item(s) without details",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onFillInPending(pendingItems) }, modifier = Modifier.testTag("run_ai_all_closet_button")) {
                                    Text("Fill in")
                                }
                            }
                        }
                    }
                }
            }

            if (showSearchBar || searchQuery.isNotBlank() || filterRainOnly || filterFavoritesOnly) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search name, colour, material…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = TextPrimary,
                                focusedPlaceholderColor = TextSecondary,
                                unfocusedPlaceholderColor = TextSecondary,
                                focusedLeadingIconColor = TextSecondary,
                                unfocusedLeadingIconColor = TextSecondary,
                                unfocusedBorderColor = StudioTileBorder
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("closet_search_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterRainOnly,
                                onClick = { filterRainOnly = !filterRainOnly },
                                leadingIcon = { Icon(Icons.Default.Umbrella, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Water-resistant") },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = TextPrimary,
                                    iconColor = TextSecondary,
                                    selectedContainerColor = StudioFabPurple,
                                    selectedLabelColor = TextPrimary,
                                    selectedLeadingIconColor = TextPrimary
                                ),
                            )
                            FilterChip(
                                selected = filterFavoritesOnly,
                                onClick = { filterFavoritesOnly = !filterFavoritesOnly },
                                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Favourites") },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = TextPrimary,
                                    iconColor = TextSecondary,
                                    selectedContainerColor = StudioFabPurple,
                                    selectedLabelColor = TextPrimary,
                                    selectedLeadingIconColor = TextPrimary
                                ),
                            )
                            FilterChip(
                                selected = filterNeedsWash,
                                onClick = { filterNeedsWash = !filterNeedsWash },
                                label = { Text("Needs washing" + if (dirtyIds.isNotEmpty()) " (${dirtyIds.size})" else "") },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = TextPrimary,
                                    selectedContainerColor = StudioFabPurple,
                                    selectedLabelColor = TextPrimary
                                ),
                            )
                        }
                    }
                }
            }

            when (closetItemsState) {
                is DataUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }

                is DataUiState.Error -> item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(closetItemsState.message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                    }
                }

                is DataUiState.Success -> when {
                    allItems.isEmpty() -> item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = StudioTileSurface),
                            border = BorderStroke(1.dp, StudioTileBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Checkroom, contentDescription = null, tint = Color(0xFFA5A9E2), modifier = Modifier.size(44.dp))
                                Text("Your closet is empty", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text(
                                    "Add photos of your clothes. Backgrounds are removed automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { batchPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.weight(1f).testTag("batch_upload_photos_button")
                                    ) { Text("Add photos") }
                                    OutlinedButton(onClick = onSeedStarterCapsule, enabled = !isSeedingCapsule, modifier = Modifier.weight(1f)) {
                                        Text(if (isSeedingCapsule) "Adding…" else "Sample items", color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    filteredItems.isEmpty() -> item {
                        Surface(shape = RoundedCornerShape(18.dp), color = StudioTileSurface, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Nothing here", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("Tap the selected category again to show all ${allItems.size} items.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }

                    useGridView -> items(filteredItems.chunked(3)) { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { item ->
                                ClosetGridTile(
                                    item = item,
                                    isBusy = item.id in analyzingItemIds,
                                    isSelected = item.id in liveSelection,
                                    needsWash = item.id in dirtyIds,
                                    selecting = selecting,
                                    onClick = { onItemTap(item) },
                                    onLongClick = { onItemLongPress(item) },
                                    modifier = Modifier.weight(1f).aspectRatio(1f)
                                )
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                        }
                    }

                    else -> items(filteredItems, key = { it.id }) { item ->
                        ClosetItemRow(
                            item = item,
                            isBusy = item.id in analyzingItemIds,
                            isSelected = item.id in liveSelection,
                            needsWash = item.id in dirtyIds,
                            onClick = { onItemTap(item) },
                            onLongClick = { onItemLongPress(item) },
                            onToggleFavorite = { onToggleFavorite(item) }
                        )
                    }
                }
            }
        }

        if (!selecting) {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                containerColor = StudioFabPurple,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(22.dp),
                icon = { Icon(Icons.Default.Checkroom, contentDescription = null, modifier = Modifier.size(22.dp)) },
                text = { Text("Create", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .height(58.dp)
                    .testTag("closet_add_piece_button")
            )
        }
    }

    val detailItem = allItems.firstOrNull { it.id == detailItemId }
    if (detailItem != null) {
        ItemDetailSheet(
            item = detailItem,
            isBusy = detailItem.id in analyzingItemIds,
            onDismiss = { detailItemId = null },
            onEdit = { detailItemId = null; onEditItem(detailItem) },
            onToggleFavorite = { onToggleFavorite(detailItem) },
            onRedoPhoto = { onRedoPhoto(detailItem) },
            onRefreshDetails = { onRefreshDetails(detailItem) },
            onRotatePhoto = { onRotatePhoto(detailItem) },
            onDelete = { confirmDelete = setOf(detailItem.id) },
            wearsSinceWash = washCounts[detailItem.id] ?: 0,
            onWearToday = { onWearToday(detailItem) },
            onMarkWashed = { onMarkWashed(setOf(detailItem.id)) }
        )
    }

    confirmDelete?.let { ids ->
        val single = allItems.firstOrNull { it.id == ids.singleOrNull() }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (single != null) "Delete \"${single.name}\"?" else "Delete ${ids.size} items?") },
            text = { Text("They'll be removed from your closet and from any saved outfits. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteItems(ids)
                    confirmDelete = null
                    selection = emptySet()
                    if (detailItemId in ids) detailItemId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClosetGridTile(
    item: ClosetItem,
    isBusy: Boolean,
    isSelected: Boolean,
    needsWash: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        GarmentVisualThumbnail(item = item, fillTile = true, cornerRadius = 22.dp)
        if (isSelected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x553F4590))
            )
        }
        if (needsWash && !selecting) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xCC0E1116),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
            ) {
                Text("Wash", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE8A55A), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        when {
            selecting -> Surface(
                shape = CircleShape,
                color = if (isSelected) Color(0xFF7B80B8) else Color(0xAA0E1116),
                border = BorderStroke(1.5.dp, Color(0xFFD0D4F7)),
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp)
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            isBusy -> Box(Modifier.align(Alignment.TopEnd).padding(9.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFA5A9E2))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClosetItemRow(
    item: ClosetItem,
    isBusy: Boolean,
    isSelected: Boolean,
    needsWash: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF262A45) else StudioTileSurface),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF7B80B8) else StudioTileBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GarmentVisualThumbnail(item = item, size = 72.dp, cornerRadius = 16.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(item.subCategory.takeUnless { it.contains("Pending", true) } ?: item.categoryEnum().shortLabel, item.colorName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.wearCount > 0) {
                    Text(
                        "Worn ${item.wearCount}×" + if (needsWash) " · needs washing" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (needsWash) Color(0xFFE8A55A) else TextSecondary
                    )
                }
            }
            if (isBusy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFA5A9E2))
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (item.isFavorite) Color(0xFFE07A5F) else TextSecondary
                )
            }
        }
    }
}
