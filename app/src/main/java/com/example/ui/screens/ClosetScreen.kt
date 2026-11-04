package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

// Match the category order shown in the user's reference screenshot
private val STUDIO_CATEGORY_ORDER = listOf(
    GarmentCategory.TOP,
    GarmentCategory.FOOTWEAR,
    GarmentCategory.BOTTOM,
    GarmentCategory.OUTERWEAR,
    GarmentCategory.ACCESSORY,
    GarmentCategory.ONE_PIECE
)

@Composable
fun ClosetScreen(
    closetItemsState: DataUiState<List<ClosetItem>>,
    isSeedingCapsule: Boolean,
    analyzingItemIds: Set<String>,
    onAddClick: () -> Unit,
    onBatchUploadPhotos: (List<Bitmap>, Boolean) -> Unit,
    onRunAiOnItems: (List<ClosetItem>) -> Unit,
    onRunAiOnSingleItem: (ClosetItem) -> Unit,
    onEditItem: (ClosetItem) -> Unit,
    onToggleFavorite: (ClosetItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onSeedStarterCapsule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<GarmentCategory?>(null) }
    var filterRainOnly by remember { mutableStateOf(false) }
    var filterFavoritesOnly by remember { mutableStateOf(false) }
    var useStudioGridView by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(ClosetSortOrder.MOST_RECENT) }
    var showSearchBar by remember { mutableStateOf(false) }

    val allItems = (closetItemsState as? DataUiState.Success)?.data.orEmpty()
    val pendingAiItems = remember(allItems) {
        allItems.filter { it.isPendingAiAnalysis() }
    }
    val alreadyAnalyzedItems = remember(allItems) {
        allItems.filter { it.isAiAnalyzed() }
    }

    val batchPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.mapNotNull { uri ->
                ImageUtils.loadAndCompressUriToBitmap(context, uri, maxDim = 512)
            }
            if (bitmaps.isNotEmpty()) {
                onBatchUploadPhotos(bitmaps, false)
            }
        }
    }

    val filteredItems = remember(
        allItems,
        searchQuery,
        selectedCategory,
        filterRainOnly,
        filterFavoritesOnly,
        sortOrder
    ) {
        val base = allItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.colorName.contains(searchQuery, ignoreCase = true) ||
                item.subCategory.contains(searchQuery, ignoreCase = true) ||
                item.materialComposition.contains(searchQuery, ignoreCase = true) ||
                item.notes.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == null || item.category == selectedCategory!!.name
            val matchesRain = !filterRainOnly || item.isRainResistant
            val matchesFav = !filterFavoritesOnly || item.isFavorite
            matchesQuery && matchesCat && matchesRain && matchesFav
        }
        when (sortOrder) {
            ClosetSortOrder.MOST_RECENT -> base.sortedByDescending { it.createdAt?.seconds ?: 0L }
            ClosetSortOrder.WARMTH_HIGH -> base.sortedByDescending { it.warmthLevel }
            ClosetSortOrder.MOST_WORN -> base.sortedByDescending { it.wearCount }
        }
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
            // 1. Horizontal Studio Pill Category Bar (matching screenshot: Tops, Footwear, Bottoms, Outerwear, Accessories...)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(STUDIO_CATEGORY_ORDER) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) StudioFabPurple else StudioPillSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF7B80B8) else StudioTileBorder
                            ),
                            modifier = Modifier.clickable {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            }
                        ) {
                            Text(
                                text = cat.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFECEFF4),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // 2. Sort & View-Mode Bar ("↕ Most recent" on left, Search + List/Grid toggle on right)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                sortOrder = when (sortOrder) {
                                    ClosetSortOrder.MOST_RECENT -> ClosetSortOrder.WARMTH_HIGH
                                    ClosetSortOrder.WARMTH_HIGH -> ClosetSortOrder.MOST_WORN
                                    ClosetSortOrder.MOST_WORN -> ClosetSortOrder.MOST_RECENT
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Change Sort Order",
                            tint = Color(0xFFD8DEE9),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sortOrder.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFECEFF4)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Toggle Search & Filters",
                                tint = if (showSearchBar || searchQuery.isNotBlank()) {
                                    Color(0xFFA5A9E2)
                                } else {
                                    Color(0xFFD8DEE9)
                                }
                            )
                        }
                        IconButton(
                            onClick = { useStudioGridView = !useStudioGridView },
                            modifier = Modifier.testTag("toggle_closet_view_mode_button")
                        ) {
                            Icon(
                                imageVector = if (useStudioGridView) {
                                    Icons.AutoMirrored.Filled.FormatListBulleted
                                } else {
                                    Icons.Default.GridView
                                },
                                contentDescription = "Switch Grid or List View",
                                tint = Color(0xFFD8DEE9)
                            )
                        }
                    }
                }
            }

            // 3. Smart AI Batch Bar (Only runs on NEW / Pending clothes; skips already-analyzed clothes & batches up to 4 per call!)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StudioTileSurface
                    ),
                    border = BorderStroke(1.dp, StudioTileBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_ai_workflow_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    analyzingItemIds.isNotEmpty() ->
                                        "Smart AI Batch Running (${analyzingItemIds.size} left)..."
                                    pendingAiItems.isNotEmpty() ->
                                        "${pendingAiItems.size} New Piece(s) Ready for AI (${alreadyAnalyzedItems.size} Skipped)"
                                    alreadyAnalyzedItems.isNotEmpty() ->
                                        "All ${alreadyAnalyzedItems.size} Photo Piece(s) AI-Tagged ✓ (0 Runs Needed)"
                                    else ->
                                        "Batch Upload & Smart AI Auto-Detect"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFECEFF4),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = when {
                                pendingAiItems.isNotEmpty() ->
                                    "Groups up to 4 new photos into 1 Gemini API call & automatically skips your ${alreadyAnalyzedItems.size} already-tagged clothes."
                                alreadyAnalyzedItems.isNotEmpty() ->
                                    "Already-analyzed clothes are locked in so they won't use extra AI runs. Upload new photos anytime!"
                                else ->
                                    "Upload multiple garment photos at once — backgrounds are automatically turned into dark studio cut-outs."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9AA3B2)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    batchPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                border = BorderStroke(1.dp, Color(0xFF3A4252)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("batch_upload_photos_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Collections,
                                    contentDescription = null,
                                    tint = Color(0xFFECEFF4),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Batch Upload", color = Color(0xFFECEFF4))
                            }

                            Button(
                                onClick = {
                                    onRunAiOnItems(pendingAiItems)
                                },
                                enabled = pendingAiItems.isNotEmpty() && analyzingItemIds.isEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_ai_all_closet_button")
                            ) {
                                Icon(
                                    imageVector = if (pendingAiItems.isEmpty() && alreadyAnalyzedItems.isNotEmpty()) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.AutoAwesome
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when {
                                        analyzingItemIds.isNotEmpty() -> "Analyzing (${analyzingItemIds.size})..."
                                        pendingAiItems.isNotEmpty() -> "Run AI (${pendingAiItems.size} New)"
                                        alreadyAnalyzedItems.isNotEmpty() -> "All ${alreadyAnalyzedItems.size} Tagged ✓"
                                        else -> "0 Pending AI"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Expandable Search & Quick Filters
            if (showSearchBar || searchQuery.isNotBlank() || filterRainOnly || filterFavoritesOnly) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search garment, material, color...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("closet_search_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterRainOnly,
                                onClick = { filterRainOnly = !filterRainOnly },
                                leadingIcon = {
                                    Icon(Icons.Default.Umbrella, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Rainproof") }
                            )
                            FilterChip(
                                selected = filterFavoritesOnly,
                                onClick = { filterFavoritesOnly = !filterFavoritesOnly },
                                leadingIcon = {
                                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Favorites") }
                            )
                        }
                    }
                }
            }

            // 5. Closet Grid / List Content
            when (closetItemsState) {
                is DataUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is DataUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = closetItemsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is DataUiState.Success -> {
                    if (allItems.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = StudioTileSurface
                                ),
                                border = BorderStroke(1.dp, StudioTileBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checkroom,
                                        contentDescription = null,
                                        tint = Color(0xFFA5A9E2),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "Your Studio Closet is Empty",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color(0xFFECEFF4)
                                    )
                                    Text(
                                        text = "Batch-upload your garment photos for automatic studio cut-outs, or load our 10-piece Editorial Starter Capsule.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF9AA3B2)
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = onSeedStarterCapsule,
                                            enabled = !isSeedingCapsule,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (isSeedingCapsule) "Loading..." else "Load Starter Capsule")
                                        }
                                        OutlinedButton(
                                            onClick = onAddClick,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Add Piece", color = Color(0xFFECEFF4))
                                        }
                                    }
                                }
                            }
                        }
                    } else if (filteredItems.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = StudioTileSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No garments in this category",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFECEFF4)
                                    )
                                    Text(
                                        text = "Tap the active category pill above to view all ${allItems.size} closet pieces.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9AA3B2)
                                    )
                                }
                            }
                        }
                    } else if (useStudioGridView) {
                        // 3-Column Square Studio Cut-Out Grid (matching user's screenshot!)
                        val rows = filteredItems.chunked(3)
                        items(rows) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { item ->
                                    StudioClosetGridTile(
                                        item = item,
                                        isAnalyzing = item.id in analyzingItemIds,
                                        onClick = { onEditItem(item) },
                                        onRunAiSingle = { onRunAiOnSingleItem(item) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredItems, key = { it.id }) { item ->
                            ClosetItemRowCard(
                                item = item,
                                isAnalyzing = item.id in analyzingItemIds,
                                onRunAiAutoDetect = { onRunAiOnSingleItem(item) },
                                onEdit = { onEditItem(item) },
                                onToggleFavorite = { onToggleFavorite(item) },
                                onDelete = { onDeleteItem(item.id) }
                            )
                        }
                    }
                }
            }
        }

        // Floating bottom-right "Create" Extended FAB with hanger icon (matching reference screenshot!)
        ExtendedFloatingActionButton(
            onClick = onAddClick,
            containerColor = StudioFabPurple,
            contentColor = Color(0xFFECEFF4),
            shape = RoundedCornerShape(22.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Checkroom,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            },
            text = {
                Text(
                    text = "Create",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .height(58.dp)
                .testTag("closet_add_piece_button")
        )
    }
}

@Composable
private fun StudioClosetGridTile(
    item: ClosetItem,
    isAnalyzing: Boolean,
    onClick: () -> Unit,
    onRunAiSingle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPending = item.isPendingAiAnalysis()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
    ) {
        GarmentVisualThumbnail(
            item = item,
            fillTile = true,
            cornerRadius = 22.dp
        )

        if (isAnalyzing) {
            Surface(
                shape = CircleShape,
                color = Color(0xCC0E1116),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFA5A9E2)
                    )
                }
            }
        } else if (isPending) {
            // Only show the AI action badge on tiles that still need AI detection
            Surface(
                shape = CircleShape,
                color = Color(0xD92B2E4A),
                border = BorderStroke(1.dp, Color(0xFF7B80B8)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clickable { onRunAiSingle() }
                    .testTag("item_ai_autodetect_${item.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Run AI Auto-Detect",
                        tint = Color(0xFFD0D4F7),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClosetItemRowCard(
    item: ClosetItem,
    isAnalyzing: Boolean,
    onRunAiAutoDetect: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val isPending = item.isPendingAiAnalysis()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = StudioTileSurface
        ),
        border = BorderStroke(1.dp, StudioTileBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GarmentVisualThumbnail(item = item, size = 84.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.categoryEnum().shortLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA5A9E2)
                    )
                    if (item.subCategory.isNotBlank()) {
                        Text(
                            text = "· ${item.subCategory}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9AA3B2)
                        )
                    }
                }

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFECEFF4),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.materialComposition.isNotBlank()) {
                    Text(
                        text = item.materialComposition,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB8C0CC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${item.colorName} · Warmth ${item.warmthLevel}/5 · Worn ${item.wearCount}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA3B2)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isRainResistant) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Umbrella,
                                    contentDescription = "Rain Resistant",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Rainproof",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    if (item.isWindResistant) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Air,
                                    contentDescription = "Wind Resistant",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Windblock",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.imageBase64.isNotBlank()) {
                        IconButton(
                            onClick = onRunAiAutoDetect,
                            enabled = !isAnalyzing,
                            modifier = Modifier.testTag("item_ai_autodetect_${item.id}")
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPending) Icons.Default.AutoAwesome else Icons.Default.CheckCircle,
                                    contentDescription = if (isPending) "Run AI Auto-Detect" else "AI Tagged (Tap to Re-scan)",
                                    tint = if (isPending) Color(0xFFA5A9E2) else Color(0xFF7AC798)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite Piece",
                            tint = if (item.isFavorite) Color(0xFFE07A5F) else Color(0xFF9AA3B2)
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Piece",
                            tint = Color(0xFF9AA3B2),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Piece",
                            tint = Color(0xFF9AA3B2),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
