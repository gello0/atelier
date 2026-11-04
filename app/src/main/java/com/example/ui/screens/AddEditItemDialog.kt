package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
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
import androidx.core.content.ContextCompat
import com.example.data.ai.GarmentAiAnalysis
import com.example.data.ai.MaterialScanResult
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.ui.AiTaskState
import com.example.util.ImageUtils
import kotlin.math.roundToInt

private val SWATCH_PRESETS = listOf(
    "Ivory" to "#F5F0E6",
    "Warm Camel" to "#C19A6B",
    "Terracotta" to "#C85A32",
    "Espresso" to "#3D2B1F",
    "Obsidian" to "#1C1917",
    "Charcoal" to "#36393B",
    "Sage Olive" to "#6B8E78",
    "Slate Blue" to "#3B6E8C",
    "Burgundy" to "#6E2630",
    "Blush Sand" to "#E2C4B5"
)

private val ALL_SEASONS = listOf("Spring", "Summer", "Autumn", "Winter")

private enum class CameraTarget {
    GARMENT_PHOTO,
    MATERIAL_LABEL
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditItemDialog(
    initialItem: ClosetItem?,
    photoTagState: AiTaskState<GarmentAiAnalysis>,
    materialScanState: AiTaskState<MaterialScanResult>,
    onAnalyzePhoto: (android.graphics.Bitmap, android.graphics.Bitmap?, String) -> Unit,
    onScanMaterialLabel: (android.graphics.Bitmap) -> Unit,
    onSaveAndAutoTagInBackground: (android.graphics.Bitmap?, android.graphics.Bitmap?, ClosetItem) -> Unit,
    onResetPhotoTagState: () -> Unit,
    onSave: (ClosetItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var name by remember(initialItem) { mutableStateOf(initialItem?.name.orEmpty()) }
    var category by remember(initialItem) {
        mutableStateOf(initialItem?.category ?: GarmentCategory.TOP.name)
    }
    var subCategory by remember(initialItem) { mutableStateOf(initialItem?.subCategory.orEmpty()) }
    var materialComposition by remember(initialItem) {
        mutableStateOf(initialItem?.materialComposition.orEmpty())
    }
    var colorName by remember(initialItem) { mutableStateOf(initialItem?.colorName ?: "Warm Camel") }
    var colorHex by remember(initialItem) { mutableStateOf(initialItem?.colorHex ?: "#C19A6B") }
    var warmthLevel by remember(initialItem) { mutableIntStateOf(initialItem?.warmthLevel ?: 3) }
    var isRainResistant by remember(initialItem) { mutableStateOf(initialItem?.isRainResistant ?: false) }
    var isWindResistant by remember(initialItem) { mutableStateOf(initialItem?.isWindResistant ?: false) }
    var selectedOccasions by remember(initialItem) {
        mutableStateOf(initialItem?.occasions?.ifEmpty { listOf("Casual") } ?: listOf("Casual"))
    }
    var selectedSeasons by remember(initialItem) {
        mutableStateOf(
            initialItem?.seasons?.ifEmpty { listOf("Spring", "Autumn") } ?: listOf("Spring", "Autumn")
        )
    }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes.orEmpty()) }
    var imageBase64 by remember(initialItem) { mutableStateOf(initialItem?.imageBase64.orEmpty()) }
    var pickedGarmentBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pickedMaterialBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var activeCameraTarget by remember { mutableStateOf(CameraTarget.GARMENT_PHOTO) }
    var cameraPermissionMessage by remember { mutableStateOf<String?>(null) }

    val existingSavedBitmap = remember(imageBase64) {
        ImageUtils.decodeBase64ToBitmap(imageBase64)
    }
    val activeGarmentBitmap = pickedGarmentBitmap ?: existingSavedBitmap

    // Apply AI Auto-Tagging results when Gemini completes garment photo analysis
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
        }
    }

    // Apply AI Material Composition / Care Label Scan results
    LaunchedEffect(materialScanState) {
        if (materialScanState is AiTaskState.Success) {
            val scan = materialScanState.data
            materialComposition = scan.materialComposition
            warmthLevel = scan.suggestedWarmthLevel
            if (scan.isRainResistant) isRainResistant = true
            if (scan.isWindResistant) isWindResistant = true
            if (scan.recommendedSeasons.isNotEmpty()) selectedSeasons = scan.recommendedSeasons
            if (scan.careInstructions.isNotBlank()) {
                notes = listOf(notes, "Care: ${scan.careInstructions}")
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · ")
                    .take(480)
            }
        }
    }

    val garmentGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageUtils.loadAndCompressUriToBitmap(context, uri, maxDim = 512)
            if (bmp != null) {
                pickedGarmentBitmap = bmp
                imageBase64 = ImageUtils.encodeBitmapToBase64(bmp, maxDim = 280, quality = 70)
                val (detectedColorName, detectedHex) = ImageUtils.extractDominantGarmentColor(bmp)
                colorName = detectedColorName
                colorHex = detectedHex
                onResetPhotoTagState()
            }
        }
    }

    val materialGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageUtils.loadAndCompressUriToBitmap(context, uri, maxDim = 512)
            if (bmp != null) {
                pickedMaterialBitmap = bmp
                onResetPhotoTagState()
            }
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val scaled = ImageUtils.scaleDownBitmap(bitmap, maxDim = 512)
            if (activeCameraTarget == CameraTarget.GARMENT_PHOTO) {
                pickedGarmentBitmap = scaled
                imageBase64 = ImageUtils.encodeBitmapToBase64(scaled, maxDim = 280, quality = 70)
                val (detectedColorName, detectedHex) = ImageUtils.extractDominantGarmentColor(scaled)
                colorName = detectedColorName
                colorHex = detectedHex
                onResetPhotoTagState()
            } else {
                pickedMaterialBitmap = scaled
                onResetPhotoTagState()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraPermissionMessage = null
            cameraCaptureLauncher.launch()
        } else {
            cameraPermissionMessage = "Camera permission is required to snap a photo. You can still pick from Gallery."
        }
    }

    fun buildCurrentDraftItem(): ClosetItem = ClosetItem(
        id = initialItem?.id.orEmpty(),
        userId = initialItem?.userId.orEmpty(),
        name = name.trim(),
        category = category,
        subCategory = subCategory.trim(),
        materialComposition = materialComposition.trim(),
        colorName = colorName.trim().ifBlank { "Neutral" },
        colorHex = colorHex.trim().ifBlank { "#C19A6B" },
        warmthLevel = warmthLevel,
        isRainResistant = isRainResistant,
        isWindResistant = isWindResistant,
        occasions = selectedOccasions,
        seasons = selectedSeasons,
        notes = notes.trim(),
        imageBase64 = imageBase64,
        wearCount = initialItem?.wearCount ?: 0L,
        lastWornEpochDay = initialItem?.lastWornEpochDay ?: 0L,
        isFavorite = initialItem?.isFavorite ?: false,
        createdAt = initialItem?.createdAt
    )

    Dialog(
        onDismissRequest = {
            onResetPhotoTagState()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialItem == null) "Add Closet Piece" else "Edit Closet Piece",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(
                        onClick = {
                            onResetPhotoTagState()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // 1. Garment Photo Upload Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val previewBitmap = remember(activeGarmentBitmap) {
                                activeGarmentBitmap?.asImageBitmap()
                            }

                            if (previewBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF171B22)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = "Selected Garment Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "1. Garment Photo",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (activeGarmentBitmap != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.clickable {
                                                val rotated = ImageUtils.rotateBitmapByDegrees(activeGarmentBitmap, 90f)
                                                pickedGarmentBitmap = rotated
                                                imageBase64 = ImageUtils.encodeBitmapToBase64(rotated, maxDim = 400, quality = 85)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Rotate90DegreesCw,
                                                    contentDescription = "Rotate Photo 90°",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Rotate 90°",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = if (activeGarmentBitmap != null) {
                                        "Photo staged ($colorName detected) · Auto-oriented via EXIF."
                                    } else {
                                        "Upload your garment photo (Camera or Gallery) — automatically oriented via EXIF."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    activeCameraTarget = CameraTarget.GARMENT_PHOTO
                                    cameraPermissionMessage = null
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        cameraCaptureLauncher.launch()
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("capture_camera_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take Photo")
                            }

                            OutlinedButton(
                                onClick = {
                                    cameraPermissionMessage = null
                                    garmentGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_garment_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery")
                            }
                        }
                    }
                }

                // 2. Material Composition & Care Label Scanner Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (pickedMaterialBitmap != null) {
                                Image(
                                    bitmap = pickedMaterialBitmap!!.asImageBitmap(),
                                    contentDescription = "Staged Care Tag Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = "Scan Material Label",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "2. Material / Care Label Photo (Optional)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (pickedMaterialBitmap != null) {
                                        "Care label photo staged! AI will combine both photos for exact fiber % and warmth."
                                    } else {
                                        "Add the inside fabric tag (e.g., 80% Wool, 20% Cashmere) so AI reads exact fiber percentages."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    activeCameraTarget = CameraTarget.MATERIAL_LABEL
                                    cameraPermissionMessage = null
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        cameraCaptureLauncher.launch()
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_material_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Snap Tag")
                            }

                            OutlinedButton(
                                onClick = {
                                    materialGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_material_gallery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tag Photo")
                            }
                        }
                    }
                }

                // 3. Dedicated Smart AI Auto-Detect & Retry Controls (Always available when any photo exists!)
                if (activeGarmentBitmap != null || pickedMaterialBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isAiRunning =
                                photoTagState is AiTaskState.Loading || materialScanState is AiTaskState.Loading
                            val hasError =
                                photoTagState is AiTaskState.Error || materialScanState is AiTaskState.Error

                            Button(
                                onClick = {
                                    if (activeGarmentBitmap != null) {
                                        onAnalyzePhoto(activeGarmentBitmap, pickedMaterialBitmap, name)
                                    } else if (pickedMaterialBitmap != null) {
                                        onScanMaterialLabel(pickedMaterialBitmap!!)
                                    }
                                },
                                enabled = !isAiRunning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("run_or_retry_ai_detect_button")
                            ) {
                                Icon(
                                    imageVector = if (hasError) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    when {
                                        isAiRunning -> "Smart AI Analyzing Garment & Material..."
                                        hasError -> "Retry Smart AI Auto-Detect"
                                        photoTagState is AiTaskState.Success -> "Re-Run Smart AI Auto-Detect"
                                        else -> "Run Smart AI Auto-Detect Now"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onSaveAndAutoTagInBackground(
                                        activeGarmentBitmap,
                                        pickedMaterialBitmap,
                                        buildCurrentDraftItem()
                                    )
                                    onResetPhotoTagState()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_and_background_tag_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save to Closet & Run AI in Background")
                            }

                            if (isAiRunning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = "Inspecting silhouette, weave, color & care label...",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            if (photoTagState is AiTaskState.Error) {
                                Text(
                                    text = "${photoTagState.message} — You can tap 'Retry Smart AI Auto-Detect' above or save the photo now and run AI later from your Closet.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (photoTagState is AiTaskState.Success) {
                                Text(
                                    text = "AI Auto-Detected: ${photoTagState.data.name} · ${photoTagState.data.materialComposition}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (materialScanState is AiTaskState.Error) {
                                Text(
                                    text = materialScanState.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (materialScanState is AiTaskState.Success) {
                                Text(
                                    text = "Scanned Care Tag: ${materialScanState.data.materialComposition}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                if (cameraPermissionMessage != null) {
                    Text(
                        text = cameraPermissionMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Garment Name (Optional Hint for AI)") },
                    placeholder = { Text("e.g., Camel Double-Breasted Trench") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garment_name_input")
                )

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GarmentCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat.name,
                            onClick = { category = cat.name },
                            label = { Text(cat.shortLabel) }
                        )
                    }
                }

                OutlinedTextField(
                    value = subCategory,
                    onValueChange = { subCategory = it },
                    label = { Text("Sub-Category / Cut") },
                    placeholder = { Text("e.g., Knit Sweater, Tailored Trousers, Chelsea Boots") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = materialComposition,
                    onValueChange = { materialComposition = it },
                    label = { Text("Material / Fiber Composition") },
                    placeholder = { Text("e.g., 100% Extra-Fine Merino Wool, 80% Cotton 20% Linen") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garment_material_input")
                )

                // Color Swatch Selector
                Text("Color Palette ($colorName)", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SWATCH_PRESETS.forEach { (presetName, presetHex) ->
                        val parsed = ImageUtils.parseHexColor(presetHex)
                        val isSelected = colorHex.equals(presetHex, ignoreCase = true)
                        Surface(
                            shape = CircleShape,
                            color = parsed,
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

                // Warmth Rating Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Thermal Warmth Rating", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = when (warmthLevel) {
                                1 -> "1 / 5 · Ultralight (Hot)"
                                2 -> "2 / 5 · Light (Warm)"
                                3 -> "3 / 5 · Mid-Weight (Mild)"
                                4 -> "4 / 5 · Warm (Crisp/Cool)"
                                else -> "5 / 5 · Heavy Winter"
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

                // Weather Protection Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rain & Water Resistant", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Prioritized when rain probability is high",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRainResistant,
                        onCheckedChange = { isRainResistant = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wind Blocking", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Recommended for breezy days (>24 km/h)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isWindResistant,
                        onCheckedChange = { isWindResistant = it }
                    )
                }

                // Occasions Multi-Select
                Text("Occasions", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OCCASION_OPTIONS.forEach { occ ->
                        val selected = occ in selectedOccasions
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedOccasions = if (selected) {
                                    (selectedOccasions - occ).ifEmpty { listOf("Casual") }
                                } else {
                                    selectedOccasions + occ
                                }
                            },
                            label = { Text(occ) }
                        )
                    }
                }

                // Seasons Multi-Select
                Text("Seasons", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ALL_SEASONS.forEach { season ->
                        val selected = season in selectedSeasons
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedSeasons = if (selected) {
                                    (selectedSeasons - season).ifEmpty { listOf("Spring") }
                                } else {
                                    selectedSeasons + season
                                }
                            },
                            label = { Text(season) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Fabric, Care & Styling Notes") },
                    placeholder = { Text("e.g., Water-repellent cotton gabardine, dry clean only") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onResetPhotoTagState()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val draft = buildCurrentDraftItem()
                            val updated = draft.copy(
                                name = draft.name.ifBlank { "Uploaded $colorName ${GarmentCategory.fromCode(category).shortLabel}" },
                                subCategory = draft.subCategory.ifBlank {
                                    if (draft.imageBase64.isNotBlank()) "Pending AI Auto-Detect" else "Garment"
                                },
                                materialComposition = draft.materialComposition.ifBlank {
                                    if (draft.imageBase64.isNotBlank()) "Pending AI Auto-Detect" else "Natural Fiber"
                                }
                            )
                            onSave(updated)
                            onResetPhotoTagState()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_garment_button")
                    ) {
                        Text("Save to Closet")
                    }
                }
            }
        }
    }
}
