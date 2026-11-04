package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiStylistService
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.weather.DailyWeatherForecast
import com.example.ui.AiTaskState
import com.example.ui.CanvasSelectionState
import com.example.ui.DataUiState
import com.example.ui.components.GarmentVisualThumbnail
import com.example.ui.components.OutfitStudioFlatLayCollage
import com.example.ui.components.formatTemp
import com.example.util.ImageUtils

@Composable
fun OutfitCanvasScreen(
    closetItemsState: DataUiState<List<ClosetItem>>,
    canvasState: CanvasSelectionState,
    activeWeather: DailyWeatherForecast?,
    useFahrenheit: Boolean,
    vtonUserPhoto: Bitmap?,
    vtonResultState: AiTaskState<Bitmap>,
    onSetVtonUserPhoto: (Bitmap?) -> Unit,
    onRunVirtualTryOn: () -> Unit,
    onUpdateCanvas: ((CanvasSelectionState) -> CanvasSelectionState) -> Unit,
    onShuffleForWeather: () -> Unit,
    onSaveCanvasOutfit: () -> Unit,
    onWearCanvasNow: (List<String>, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allItems = (closetItemsState as? DataUiState.Success)?.data.orEmpty()

    val tops = remember(allItems) { allItems.filter { it.category == GarmentCategory.TOP.name } }
    val bottoms = remember(allItems) { allItems.filter { it.category == GarmentCategory.BOTTOM.name } }
    val onePieces = remember(allItems) { allItems.filter { it.category == GarmentCategory.ONE_PIECE.name } }
    val outerwearList = remember(allItems) { allItems.filter { it.category == GarmentCategory.OUTERWEAR.name } }
    val footwearList = remember(allItems) { allItems.filter { it.category == GarmentCategory.FOOTWEAR.name } }
    val accessories = remember(allItems) { allItems.filter { it.category == GarmentCategory.ACCESSORY.name } }

    val selectedIds = remember(canvasState) {
        listOfNotNull(
            if (canvasState.useOnePieceMode) canvasState.selectedOnePieceId else canvasState.selectedTopId,
            if (canvasState.useOnePieceMode) null else canvasState.selectedBottomId,
            canvasState.selectedOuterwearId,
            canvasState.selectedFootwearId,
            canvasState.selectedAccessoryId
        ).distinct()
    }

    val selectedItems = remember(allItems, selectedIds) {
        allItems.filter { it.id in selectedIds }
    }

    val comfortEvaluation = remember(selectedItems, activeWeather) {
        AiStylistService.evaluateOutfitComfort(selectedItems, activeWeather)
    }

    val vtonGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageUtils.decodeUriToScaledBitmap(context, uri, maxDim = 960)
            if (bmp != null) {
                onSetVtonUserPhoto(bmp)
            }
        }
    }

    val vtonCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            onSetVtonUserPhoto(bmp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vtonCameraLauncher.launch(null)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("outfit_canvas_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Shuffle Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mix & Match Studio",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = if (activeWeather != null) {
                            "Calibrated for ${activeWeather.dayLabel}: ${
                                formatTemp(activeWeather.feelsLikeC, useFahrenheit)
                            } · ${activeWeather.conditionLabel}"
                        } else {
                            "Compose custom looks & run Virtual Try-On"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onShuffleForWeather,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("shuffle_canvas_button")
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Match")
                }
            }
        }

        // Live Weather Comfort Meter + Studio Cut-Out Collage Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("canvas_comfort_meter_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = comfortEvaluation.headline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${comfortEvaluation.score}% Comfort",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (comfortEvaluation.score / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )

                    Text(
                        text = comfortEvaluation.detailTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selectedItems.isNotEmpty()) {
                        OutfitStudioFlatLayCollage(items = selectedItems)
                    }
                }
            }
        }

        // Virtual Try-On (VTON) Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vton_studio_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Virtual Try-On (VTON)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (vtonUserPhoto != null) {
                                    "Your reference photo is active — try on the selected pieces!"
                                } else {
                                    "Add your photo (or generate on an editorial studio model)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (vtonUserPhoto != null) {
                            Image(
                                bitmap = vtonUserPhoto.asImageBitmap(),
                                contentDescription = "Your VTON reference photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vton_camera_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Selfie / Camera")
                        }
                        OutlinedButton(
                            onClick = {
                                vtonGalleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vton_gallery_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Your Photo")
                        }
                    }

                    Button(
                        onClick = onRunVirtualTryOn,
                        enabled = selectedItems.isNotEmpty() && vtonResultState !is AiTaskState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_vton_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (vtonResultState is AiTaskState.Loading) {
                                "Synthesizing Virtual Try-On in Background..."
                            } else {
                                "Generate Virtual Try-On (${selectedItems.size} pieces)"
                            }
                        )
                    }

                    when (vtonResultState) {
                        is AiTaskState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Gemini Nano Banana 2.1 is rendering your Virtual Try-On in the background (30–60s). Feel free to switch tabs!",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        is AiTaskState.Success -> {
                            Image(
                                bitmap = vtonResultState.data.asImageBitmap(),
                                contentDescription = "Virtual Try-On Result",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }

                        is AiTaskState.Error -> {
                            Text(
                                text = vtonResultState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        AiTaskState.Idle -> Unit
                    }
                }
            }
        }

        // Silhouette Mode Toggle (Separates vs One-Piece)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !canvasState.useOnePieceMode,
                    onClick = { onUpdateCanvas { it.copy(useOnePieceMode = false) } },
                    label = { Text("Top + Bottom Separates") }
                )
                FilterChip(
                    selected = canvasState.useOnePieceMode,
                    onClick = { onUpdateCanvas { it.copy(useOnePieceMode = true) } },
                    label = { Text("One-Piece / Dress") }
                )
            }
        }

        // Slot Pickers
        if (!canvasState.useOnePieceMode) {
            item {
                CanvasSlotCarousel(
                    title = "1. Top / Knitwear",
                    items = tops,
                    selectedId = canvasState.selectedTopId,
                    onSelect = { id ->
                        onUpdateCanvas {
                            it.copy(selectedTopId = if (it.selectedTopId == id) null else id)
                        }
                    }
                )
            }
            item {
                CanvasSlotCarousel(
                    title = "2. Trousers / Skirt",
                    items = bottoms,
                    selectedId = canvasState.selectedBottomId,
                    onSelect = { id ->
                        onUpdateCanvas {
                            it.copy(selectedBottomId = if (it.selectedBottomId == id) null else id)
                        }
                    }
                )
            }
        } else {
            item {
                CanvasSlotCarousel(
                    title = "1. Dress / Jumpsuit",
                    items = onePieces,
                    selectedId = canvasState.selectedOnePieceId,
                    onSelect = { id ->
                        onUpdateCanvas {
                            it.copy(selectedOnePieceId = if (it.selectedOnePieceId == id) null else id)
                        }
                    }
                )
            }
        }

        item {
            CanvasSlotCarousel(
                title = "3. Outerwear / Weather Layer (Optional)",
                items = outerwearList,
                selectedId = canvasState.selectedOuterwearId,
                onSelect = { id ->
                    onUpdateCanvas {
                        it.copy(selectedOuterwearId = if (it.selectedOuterwearId == id) null else id)
                    }
                }
            )
        }

        item {
            CanvasSlotCarousel(
                title = "4. Footwear",
                items = footwearList,
                selectedId = canvasState.selectedFootwearId,
                onSelect = { id ->
                    onUpdateCanvas {
                        it.copy(selectedFootwearId = if (it.selectedFootwearId == id) null else id)
                    }
                }
            )
        }

        item {
            CanvasSlotCarousel(
                title = "5. Finishing Accessory (Optional)",
                items = accessories,
                selectedId = canvasState.selectedAccessoryId,
                onSelect = { id ->
                    onUpdateCanvas {
                        it.copy(selectedAccessoryId = if (it.selectedAccessoryId == id) null else id)
                    }
                }
            )
        }

        // Save & Log Controls Card
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
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Finalize Studio Look",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = canvasState.outfitTitle,
                        onValueChange = { title -> onUpdateCanvas { it.copy(outfitTitle = title) } },
                        label = { Text("Look Title") },
                        placeholder = { Text("e.g., Autumn Gallery Opening") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("canvas_outfit_title_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(OCCASION_OPTIONS) { occ ->
                            FilterChip(
                                selected = canvasState.selectedOccasion == occ,
                                onClick = { onUpdateCanvas { it.copy(selectedOccasion = occ) } },
                                label = { Text(occ) }
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    val name = canvasState.outfitTitle.trim().ifBlank {
                                        "${canvasState.selectedOccasion} Studio Look"
                                    }
                                    onWearCanvasNow(selectedIds, name, canvasState.selectedOccasion)
                                }
                            },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wear Today")
                        }

                        Button(
                            onClick = onSaveCanvasOutfit,
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_canvas_outfit_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Look")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasSlotCarousel(
    title: String,
    items: List<ClosetItem>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$title (${items.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (items.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No items in this category yet. Add pieces in the Closet tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { item ->
                    val isSelected = item.id == selectedId
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier
                            .width(126.dp)
                            .clickable { onSelect(item.id) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            GarmentVisualThumbnail(item = item, size = 76.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Warmth ${item.warmthLevel}/5",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
