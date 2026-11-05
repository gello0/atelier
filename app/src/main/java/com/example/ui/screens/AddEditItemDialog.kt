package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ai.GarmentAiAnalysis
import com.example.data.ai.MaterialScanResult
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.ui.AiTaskState
import com.example.ui.components.GarmentVisualThumbnail
import com.example.ui.components.StudioTileSurface
import com.example.util.ImageUtils
import com.example.util.rememberCameraCapture
import kotlin.math.roundToInt

private val SWATCH_PRESETS = listOf(
    "Off-White" to "#F5F0E6",
    "Camel" to "#C19A6B",
    "Rust" to "#C85A32",
    "Brown" to "#3D2B1F",
    "Black" to "#1C1917",
    "Charcoal" to "#36393B",
    "Olive" to "#6B8E78",
    "Navy" to "#1F2A44",
    "Burgundy" to "#6E2630",
    "Grey" to "#8A8F98"
)

private val ALL_SEASONS = listOf("Spring", "Summer", "Autumn", "Winter")

/**
 * Add / edit an item. A newly picked photo is passed to [onSave] so it can be turned into a
 * clean cutout; when [fillDetails] is on, empty details are filled in from the photo after saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditItemDialog(
    initialItem: ClosetItem?,
    photoTagState: AiTaskState<GarmentAiAnalysis>,
    materialScanState: AiTaskState<MaterialScanResult>,
    onAnalyzePhoto: (Bitmap, Bitmap?, String) -> Unit,
    onScanMaterialLabel: (Bitmap) -> Unit,
    onResetPhotoTagState: () -> Unit,
    onSave: (newPhoto: Bitmap?, labelPhoto: Bitmap?, draft: ClosetItem, fillDetails: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var name by remember(initialItem) { mutableStateOf(initialItem?.name?.takeUnless { it == "New piece" }.orEmpty()) }
    var category by remember(initialItem) { mutableStateOf(initialItem?.category ?: GarmentCategory.TOP.name) }
    var subCategory by remember(initialItem) {
        mutableStateOf(initialItem?.subCategory?.takeUnless { it.contains("Pending", true) }.orEmpty())
    }
    var materialComposition by remember(initialItem) { mutableStateOf(initialItem?.materialComposition.orEmpty()) }
    var colorName by remember(initialItem) { mutableStateOf(initialItem?.colorName ?: "Black") }
    var colorHex by remember(initialItem) { mutableStateOf(initialItem?.colorHex ?: "#1C1917") }
    var warmthLevel by remember(initialItem) { mutableIntStateOf(initialItem?.warmthLevel ?: 3) }
    var isRainResistant by remember(initialItem) { mutableStateOf(initialItem?.isRainResistant ?: false) }
    var isWindResistant by remember(initialItem) { mutableStateOf(initialItem?.isWindResistant ?: false) }
    var selectedOccasions by remember(initialItem) {
        mutableStateOf(initialItem?.occasions?.ifEmpty { listOf("Casual") } ?: listOf("Casual"))
    }
    var selectedSeasons by remember(initialItem) {
        mutableStateOf(initialItem?.seasons?.ifEmpty { listOf("Spring", "Autumn") } ?: listOf("Spring", "Autumn"))
    }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes.orEmpty()) }
    var newPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var labelPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var fillDetails by remember { mutableStateOf(initialItem == null) }
    var message by remember { mutableStateOf<String?>(null) }

    val existingPhoto = remember(initialItem?.imageBase64) { ImageUtils.decodeBase64ToBitmap(initialItem?.imageBase64.orEmpty()) }
    val photoForReading = newPhoto ?: existingPhoto

    LaunchedEffect(photoTagState) {
        if (photoTagState is AiTaskState.Success) {
            val tag = photoTagState.data
            name = tag.name
            category = tag.category
            subCategory = tag.subCategory
            if (tag.materialComposition.isNotBlank()) materialComposition = tag.materialComposition
            colorName = tag.colorName
            colorHex = tag.colorHex
            warmthLevel = tag.warmthLevel
            isRainResistant = tag.isRainResistant
            isWindResistant = tag.isWindResistant
            if (tag.occasions.isNotEmpty()) selectedOccasions = tag.occasions
            if (tag.seasons.isNotEmpty()) selectedSeasons = tag.seasons
            if (tag.notes.isNotBlank()) notes = tag.notes
            if (tag.rotationDegrees != 0) newPhoto = newPhoto?.let { ImageUtils.rotateBitmapByDegrees(it, tag.rotationDegrees.toFloat()) }
            fillDetails = false
        }
    }

    LaunchedEffect(materialScanState) {
        if (materialScanState is AiTaskState.Success) {
            val scan = materialScanState.data
            materialComposition = scan.materialComposition
            warmthLevel = scan.suggestedWarmthLevel
            if (scan.isRainResistant) isRainResistant = true
            if (scan.isWindResistant) isWindResistant = true
            if (scan.recommendedSeasons.isNotEmpty()) selectedSeasons = scan.recommendedSeasons
            if (scan.careInstructions.isNotBlank() && !notes.contains(scan.careInstructions)) {
                notes = listOf(notes, "Care: ${scan.careInstructions}").filter { it.isNotBlank() }.joinToString(" ").take(480)
            }
        }
    }

    fun acceptPhoto(bmp: Bitmap) {
        newPhoto = bmp
        val (detectedName, detectedHex) = ImageUtils.extractDominantGarmentColor(bmp)
        colorName = detectedName
        colorHex = detectedHex
        fillDetails = true
        onResetPhotoTagState()
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageUtils.loadAndCompressUriToBitmap(context, uri, maxDim = 1600)?.let(::acceptPhoto)
    }
    val labelGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageUtils.loadAndCompressUriToBitmap(context, uri, maxDim = 1600)?.let { labelPhoto = it }
    }
    val takePhoto = rememberCameraCapture(
        onPermissionDenied = { message = "Camera permission is needed. You can still pick from the gallery." },
        onPhoto = ::acceptPhoto
    )
    val takeLabelPhoto = rememberCameraCapture(
        onPermissionDenied = { message = "Camera permission is needed. You can still pick from the gallery." },
        onPhoto = { labelPhoto = it }
    )

    fun buildDraft(): ClosetItem = ClosetItem(
        id = initialItem?.id.orEmpty(),
        userId = initialItem?.userId.orEmpty(),
        name = name.trim(),
        category = category,
        subCategory = subCategory.trim(),
        materialComposition = materialComposition.trim(),
        colorName = colorName.trim().ifBlank { "Neutral" },
        colorHex = colorHex.trim().ifBlank { "#8A8F98" },
        warmthLevel = warmthLevel,
        isRainResistant = isRainResistant,
        isWindResistant = isWindResistant,
        occasions = selectedOccasions,
        seasons = selectedSeasons,
        notes = notes.trim(),
        imageBase64 = initialItem?.imageBase64.orEmpty(),
        wearCount = initialItem?.wearCount ?: 0L,
        lastWornEpochDay = initialItem?.lastWornEpochDay ?: 0L,
        lastWashedEpochDay = initialItem?.lastWashedEpochDay ?: 0L,
        isFavorite = initialItem?.isFavorite ?: false,
        createdAt = initialItem?.createdAt
    )

    fun close() {
        onResetPhotoTagState()
        onDismiss()
    }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (initialItem == null) "Add item" else "Edit item",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = ::close) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                // Photo
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(132.dp)) {
                        val picked = newPhoto
                        when {
                            picked != null -> Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(StudioTileSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = remember(picked) { picked.asImageBitmap() },
                                    contentDescription = "New photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            initialItem != null -> GarmentVisualThumbnail(item = initialItem, fillTile = true, cornerRadius = 20.dp)
                            else -> Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(StudioTileSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF9AA3B2))
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Button(onClick = takePhoto, modifier = Modifier.fillMaxWidth().testTag("capture_camera_photo_button")) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().testTag("pick_garment_photo_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery")
                        }
                        if (newPhoto != null) {
                            TextButton(onClick = { newPhoto = newPhoto?.let { ImageUtils.rotateBitmapByDegrees(it, 90f) } }) {
                                Icon(Icons.Default.Rotate90DegreesCw, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rotate")
                            }
                        }
                    }
                }
                if (newPhoto != null) {
                    Text(
                        "The background is removed when you save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Care label (optional)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labelPhoto?.let {
                        Image(
                            bitmap = remember(it) { it.asImageBitmap() },
                            contentDescription = "Care label",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Text(
                        if (labelPhoto == null) "Care label (optional)" else "Care label added",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = takeLabelPhoto) { Text("Camera") }
                    TextButton(onClick = {
                        labelGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Gallery") }
                }

                // Fill in from photo
                if (photoForReading != null || labelPhoto != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = fillDetails, onCheckedChange = { fillDetails = it })
                                Text(
                                    "Fill in details from the photo after saving",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            val busy = photoTagState is AiTaskState.Loading || materialScanState is AiTaskState.Loading
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                                TextButton(
                                    onClick = {
                                        val p = photoForReading
                                        if (p != null) onAnalyzePhoto(p, labelPhoto, name) else labelPhoto?.let(onScanMaterialLabel)
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.testTag("run_or_retry_ai_detect_button")
                                ) { Text("Fill in now to review") }
                                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            val error = (photoTagState as? AiTaskState.Error)?.message ?: (materialScanState as? AiTaskState.Error)?.message
                            if (error != null) {
                                Text(
                                    error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                                )
                            }
                        }
                    }
                }

                message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Navy Knit Polo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("garment_name_input")
                )

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GarmentCategory.entries.forEach { cat ->
                        FilterChip(selected = category == cat.name, onClick = { category = cat.name }, label = { Text(cat.shortLabel) })
                    }
                }

                OutlinedTextField(
                    value = subCategory,
                    onValueChange = { subCategory = it },
                    label = { Text("Type") },
                    placeholder = { Text("e.g. Polo Shirt, Cargo Trousers, Running Shoes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = materialComposition,
                    onValueChange = { materialComposition = it },
                    label = { Text("Material") },
                    placeholder = { Text("e.g. Cotton jersey, 80% Wool 20% Nylon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("garment_material_input")
                )

                Text("Colour · $colorName", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val swatches = if (SWATCH_PRESETS.none { it.second.equals(colorHex, true) }) {
                        listOf(colorName to colorHex) + SWATCH_PRESETS
                    } else {
                        SWATCH_PRESETS
                    }
                    swatches.forEach { (presetName, presetHex) ->
                        val isSelected = colorHex.equals(presetHex, ignoreCase = true)
                        Surface(
                            shape = CircleShape,
                            color = ImageUtils.parseHexColor(presetHex),
                            border = BorderStroke(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    colorName = presetName
                                    colorHex = presetHex
                                }
                        ) {}
                    }
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Warmth", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = when (warmthLevel) {
                                1 -> "Very light"
                                2 -> "Light"
                                3 -> "Medium"
                                4 -> "Warm"
                                else -> "Very warm"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = warmthLevel.toFloat(),
                        onValueChange = { warmthLevel = it.roundToInt().coerceIn(1, 5) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Water-resistant", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = isRainResistant, onCheckedChange = { isRainResistant = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Windproof", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = isWindResistant, onCheckedChange = { isWindResistant = it })
                }

                Text("Occasions", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OCCASION_OPTIONS.forEach { occ ->
                        val selected = occ in selectedOccasions
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedOccasions = if (selected) (selectedOccasions - occ).ifEmpty { listOf("Casual") } else selectedOccasions + occ
                            },
                            label = { Text(occ) }
                        )
                    }
                }

                Text("Seasons", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ALL_SEASONS.forEach { season ->
                        val selected = season in selectedSeasons
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedSeasons = if (selected) (selectedSeasons - season).ifEmpty { listOf("Spring") } else selectedSeasons + season
                            },
                            label = { Text(season) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(480) },
                    label = { Text("Description") },
                    placeholder = { Text("Fit, details, care, what to wear it with") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = ::close, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val shouldFill = fillDetails && photoTagState !is AiTaskState.Success
                            onSave(newPhoto, labelPhoto, buildDraft(), shouldFill)
                            onResetPhotoTagState()
                        },
                        modifier = Modifier.weight(1f).testTag("save_garment_button")
                    ) { Text("Save") }
                }
            }
        }
    }
}
