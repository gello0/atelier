package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.weather.CityLocation
import com.example.data.weather.DailyWeatherForecast
import com.example.data.weather.PRESET_CITIES
import com.example.data.weather.WeatherSnapshot
import com.example.util.ImageUtils
import kotlin.math.roundToInt

val StudioObsidianBackground = Color(0xFF0E1116)
val StudioTileSurface = Color(0xFF171B22)
val StudioTileBorder = Color(0xFF252B36)
val StudioPillSurface = Color(0xFF1C212B)
val StudioFabPurple = Color(0xFF43466E)

fun formatTemp(tempC: Double, useFahrenheit: Boolean): String {
    return if (useFahrenheit) {
        val f = (tempC * 9.0 / 5.0 + 32.0).roundToInt()
        "$f°F"
    } else {
        "${tempC.roundToInt()}°C"
    }
}

fun categoryIcon(category: GarmentCategory): ImageVector = when (category) {
    GarmentCategory.TOP -> Icons.Default.Checkroom
    GarmentCategory.BOTTOM -> Icons.Default.DryCleaning
    GarmentCategory.OUTERWEAR -> Icons.Default.Umbrella
    GarmentCategory.ONE_PIECE -> Icons.Default.Diamond
    GarmentCategory.FOOTWEAR -> Icons.Default.ShoppingBag
    GarmentCategory.ACCESSORY -> Icons.Default.Diamond
}

fun weatherConditionIcon(code: Int, isRainy: Boolean): ImageVector = when {
    code in listOf(95, 96, 99) -> Icons.Default.Thunderstorm
    isRainy -> Icons.Default.Umbrella
    code in listOf(0, 1) -> Icons.Default.WbSunny
    else -> Icons.Default.Cloud
}

/**
 * Square dark tile showing a closet item like an online-shop grid.
 * - Clean cutouts (transparent WebP/PNG) are centred with breathing room.
 * - Older raw photos fill the tile.
 * - Items without a photo show a simple drawn silhouette in the item's colour.
 */
@Composable
fun GarmentVisualThumbnail(
    item: ClosetItem,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    fillTile: Boolean = false,
    cornerRadius: Dp = 22.dp,
    showColorDot: Boolean = false
) {
    val decodedBitmap = remember(item.imageBase64) {
        ImageUtils.decodeBase64ToImageBitmap(item.imageBase64)
    }
    val isCutout = remember(item.imageBase64) { ImageUtils.isCutoutImage(item.imageBase64) }
    val swatchColor = remember(item.colorHex) {
        ImageUtils.parseHexColor(item.colorHex)
    }
    val shape = RoundedCornerShape(cornerRadius)

    val sized = if (fillTile) modifier.fillMaxSize() else modifier.size(size)
    Box(
        modifier = sized
            .clip(shape)
            .background(StudioTileSurface)
            .border(1.dp, StudioTileBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            decodedBitmap != null && isCutout -> Image(
                bitmap = decodedBitmap,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (fillTile) 10.dp else 6.dp),
                contentScale = ContentScale.Fit
            )
            decodedBitmap != null -> Image(
                bitmap = decodedBitmap,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            else -> Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (fillTile) 14.dp else 8.dp)
            ) {
                drawStudioGarmentCutout(item.categoryEnum(), swatchColor, item.subCategory, item.name)
            }
        }

        if (showColorDot) {
            Surface(
                shape = CircleShape,
                color = swatchColor,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .size(11.dp)
            ) {}
        }
    }
}

/**
 * Renders an outfit's pieces as a 3-column dark studio lookbook flat-lay grid
 * matching the reference screenshot aesthetic (#0E1116 frame with #171B22 square cut-out tiles).
 */
@Composable
fun OutfitStudioFlatLayCollage(
    items: List<ClosetItem>,
    modifier: Modifier = Modifier,
    showItemLabels: Boolean = true,
    onItemClick: ((ClosetItem) -> Unit)? = null
) {
    if (items.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = StudioObsidianBackground,
        border = BorderStroke(1.dp, StudioTileBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        val rows = items.chunked(3)
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (onItemClick != null) {
                                        Modifier.clickable { onItemClick(item) }
                                    } else {
                                        Modifier
                                    }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            ) {
                                GarmentVisualThumbnail(
                                    item = item,
                                    fillTile = true,
                                    cornerRadius = 20.dp
                                )
                            }
                            if (showItemLabels) {
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD5DAE2),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStudioGarmentCutout(
    category: GarmentCategory,
    baseColor: Color,
    subCategory: String,
    name: String
) {
    val w = size.width
    val h = size.height

    val highlight = blendColors(baseColor, Color.White, 0.24f)
    val midTone = baseColor
    val shadow = blendColors(baseColor, Color(0xFF090B0E), 0.38f)
    val deepSeam = blendColors(baseColor, Color(0xFF050608), 0.62f)
    val collarInterior = blendColors(baseColor, Color(0xFF050608), 0.72f)

    // Subtle soft ambient studio glow behind the garment cut-out
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.5f),
            radius = w * 0.54f
        )
    )

    val garmentBrush = Brush.linearGradient(
        colors = listOf(highlight, midTone, shadow),
        start = Offset(w * 0.2f, h * 0.1f),
        end = Offset(w * 0.85f, h * 0.9f)
    )

    when (category) {
        GarmentCategory.TOP -> {
            val isHoodie = subCategory.contains("hood", ignoreCase = true) ||
                name.contains("hood", ignoreCase = true)

            val bodyPath = Path().apply {
                // Left neck -> left shoulder -> left sleeve cuff
                moveTo(w * 0.36f, h * 0.15f)
                lineTo(w * 0.19f, h * 0.22f)
                lineTo(w * 0.10f, h * 0.82f)
                lineTo(w * 0.22f, h * 0.84f)
                lineTo(w * 0.27f, h * 0.38f)
                // Left torso down to ribbed hem
                lineTo(w * 0.27f, h * 0.85f)
                lineTo(w * 0.73f, h * 0.85f)
                // Right torso up to right armpit -> right sleeve cuff
                lineTo(w * 0.73f, h * 0.38f)
                lineTo(w * 0.78f, h * 0.84f)
                lineTo(w * 0.90f, h * 0.82f)
                lineTo(w * 0.81f, h * 0.22f)
                lineTo(w * 0.64f, h * 0.15f)
                // Neckline curve
                quadraticTo(w * 0.50f, h * 0.22f, w * 0.36f, h * 0.15f)
                close()
            }
            drawPath(bodyPath, brush = garmentBrush)
            drawPath(bodyPath, color = deepSeam, style = Stroke(width = w * 0.014f, join = StrokeJoin.Round))

            // Collar / Hood detail
            val neckOpening = Path().apply {
                moveTo(w * 0.36f, h * 0.15f)
                quadraticTo(w * 0.50f, h * 0.11f, w * 0.64f, h * 0.15f)
                quadraticTo(w * 0.50f, h * 0.22f, w * 0.36f, h * 0.15f)
                close()
            }
            drawPath(neckOpening, color = collarInterior)

            if (isHoodie) {
                val hoodPath = Path().apply {
                    moveTo(w * 0.34f, h * 0.16f)
                    quadraticTo(w * 0.32f, h * 0.04f, w * 0.50f, h * 0.04f)
                    quadraticTo(w * 0.68f, h * 0.04f, w * 0.66f, h * 0.16f)
                }
                drawPath(hoodPath, color = shadow, style = Stroke(width = w * 0.025f, cap = StrokeCap.Round))
                // Drawstrings
                drawLine(deepSeam, Offset(w * 0.46f, h * 0.19f), Offset(w * 0.46f, h * 0.36f), strokeWidth = w * 0.014f)
                drawLine(deepSeam, Offset(w * 0.54f, h * 0.19f), Offset(w * 0.54f, h * 0.36f), strokeWidth = w * 0.014f)
            }

            // Ribbed hem & sleeve crease lines
            drawLine(deepSeam, Offset(w * 0.27f, h * 0.80f), Offset(w * 0.73f, h * 0.80f), strokeWidth = w * 0.012f)
            drawLine(shadow, Offset(w * 0.27f, h * 0.26f), Offset(w * 0.27f, h * 0.38f), strokeWidth = w * 0.014f)
            drawLine(shadow, Offset(w * 0.73f, h * 0.26f), Offset(w * 0.73f, h * 0.38f), strokeWidth = w * 0.014f)
        }

        GarmentCategory.BOTTOM -> {
            // Tailored trousers / denim jeans studio cut-out
            val pantsPath = Path().apply {
                moveTo(w * 0.30f, h * 0.09f)
                lineTo(w * 0.70f, h * 0.09f)
                lineTo(w * 0.73f, h * 0.91f)
                lineTo(w * 0.54f, h * 0.91f)
                lineTo(w * 0.50f, h * 0.36f)
                lineTo(w * 0.46f, h * 0.91f)
                lineTo(w * 0.27f, h * 0.91f)
                close()
            }
            drawPath(pantsPath, brush = garmentBrush)
            drawPath(pantsPath, color = deepSeam, style = Stroke(width = w * 0.014f, join = StrokeJoin.Round))

            // Waistband, fly seam, and pockets
            drawLine(deepSeam, Offset(w * 0.30f, h * 0.15f), Offset(w * 0.70f, h * 0.15f), strokeWidth = w * 0.015f)
            drawLine(deepSeam, Offset(w * 0.50f, h * 0.15f), Offset(w * 0.50f, h * 0.34f), strokeWidth = w * 0.014f)
            val leftPocket = Path().apply {
                moveTo(w * 0.31f, h * 0.17f)
                quadraticTo(w * 0.38f, h * 0.24f, w * 0.41f, h * 0.15f)
            }
            val rightPocket = Path().apply {
                moveTo(w * 0.69f, h * 0.17f)
                quadraticTo(w * 0.62f, h * 0.24f, w * 0.59f, h * 0.15f)
            }
            drawPath(leftPocket, color = deepSeam, style = Stroke(width = w * 0.012f))
            drawPath(rightPocket, color = deepSeam, style = Stroke(width = w * 0.012f))
        }

        GarmentCategory.OUTERWEAR -> {
            // Structured jacket / coat with collar lapels and center zipper
            val jacketPath = Path().apply {
                moveTo(w * 0.35f, h * 0.12f)
                lineTo(w * 0.17f, h * 0.20f)
                lineTo(w * 0.09f, h * 0.84f)
                lineTo(w * 0.22f, h * 0.86f)
                lineTo(w * 0.26f, h * 0.38f)
                lineTo(w * 0.25f, h * 0.87f)
                lineTo(w * 0.75f, h * 0.87f)
                lineTo(w * 0.74f, h * 0.38f)
                lineTo(w * 0.78f, h * 0.86f)
                lineTo(w * 0.91f, h * 0.84f)
                lineTo(w * 0.83f, h * 0.20f)
                lineTo(w * 0.65f, h * 0.12f)
                close()
            }
            drawPath(jacketPath, brush = garmentBrush)
            drawPath(jacketPath, color = deepSeam, style = Stroke(width = w * 0.015f, join = StrokeJoin.Round))

            // Dark interior neck V + center zipper seam + chest yoke
            val collarV = Path().apply {
                moveTo(w * 0.36f, h * 0.12f)
                lineTo(w * 0.50f, h * 0.28f)
                lineTo(w * 0.64f, h * 0.12f)
                close()
            }
            drawPath(collarV, color = collarInterior)
            drawLine(deepSeam, Offset(w * 0.50f, h * 0.26f), Offset(w * 0.50f, h * 0.87f), strokeWidth = w * 0.018f)
            drawLine(deepSeam, Offset(w * 0.26f, h * 0.34f), Offset(w * 0.74f, h * 0.34f), strokeWidth = w * 0.012f)
        }

        GarmentCategory.FOOTWEAR -> {
            // Pair of angled studio sneakers with sculpted white/tonal midsole & laces
            drawSneakerProfile(
                offsetX = -w * 0.06f,
                offsetY = -h * 0.05f,
                w = w,
                h = h,
                upperBrush = Brush.linearGradient(listOf(shadow, deepSeam)),
                soleColor = Color(0xFFDADEE6),
                accentColor = highlight,
                seamColor = deepSeam
            )
            drawSneakerProfile(
                offsetX = w * 0.05f,
                offsetY = h * 0.06f,
                w = w,
                h = h,
                upperBrush = garmentBrush,
                soleColor = Color(0xFFF0F3F8),
                accentColor = highlight,
                seamColor = deepSeam
            )
        }

        GarmentCategory.ONE_PIECE -> {
            // Tailored dress / jumpsuit silhouette
            val dressPath = Path().apply {
                moveTo(w * 0.37f, h * 0.12f)
                lineTo(w * 0.27f, h * 0.18f)
                lineTo(w * 0.33f, h * 0.42f)
                lineTo(w * 0.22f, h * 0.89f)
                lineTo(w * 0.78f, h * 0.89f)
                lineTo(w * 0.67f, h * 0.42f)
                lineTo(w * 0.73f, h * 0.18f)
                lineTo(w * 0.63f, h * 0.12f)
                quadraticTo(w * 0.50f, h * 0.20f, w * 0.37f, h * 0.12f)
                close()
            }
            drawPath(dressPath, brush = garmentBrush)
            drawPath(dressPath, color = deepSeam, style = Stroke(width = w * 0.014f, join = StrokeJoin.Round))
            drawLine(deepSeam, Offset(w * 0.33f, h * 0.42f), Offset(w * 0.67f, h * 0.42f), strokeWidth = w * 0.014f)
        }

        GarmentCategory.ACCESSORY -> {
            // Sculpted studio watch / accessory silhouette (like the smartwatch in the screenshot)
            val strapPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w * 0.36f,
                        top = h * 0.12f,
                        right = w * 0.64f,
                        bottom = h * 0.88f,
                        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
                    )
                )
            }
            drawPath(strapPath, brush = garmentBrush)
            drawPath(strapPath, color = deepSeam, style = Stroke(width = w * 0.015f))

            // Watch case bezel & obsidian dial face
            drawCircle(
                color = shadow,
                radius = w * 0.24f,
                center = Offset(w * 0.50f, h * 0.50f)
            )
            drawCircle(
                color = Color(0xFF0A0C10),
                radius = w * 0.19f,
                center = Offset(w * 0.50f, h * 0.50f)
            )
            drawCircle(
                color = highlight.copy(alpha = 0.6f),
                radius = w * 0.24f,
                center = Offset(w * 0.50f, h * 0.50f),
                style = Stroke(width = w * 0.014f)
            )
        }
    }
}

private fun DrawScope.drawSneakerProfile(
    offsetX: Float,
    offsetY: Float,
    w: Float,
    h: Float,
    upperBrush: Brush,
    soleColor: Color,
    accentColor: Color,
    seamColor: Color
) {
    val upperPath = Path().apply {
        moveTo(w * 0.16f + offsetX, h * 0.62f + offsetY)
        quadraticTo(w * 0.18f + offsetX, h * 0.48f + offsetY, w * 0.38f + offsetX, h * 0.45f + offsetY)
        lineTo(w * 0.55f + offsetX, h * 0.30f + offsetY)
        quadraticTo(w * 0.64f + offsetX, h * 0.38f + offsetY, w * 0.72f + offsetX, h * 0.34f + offsetY)
        lineTo(w * 0.82f + offsetX, h * 0.38f + offsetY)
        lineTo(w * 0.85f + offsetX, h * 0.64f + offsetY)
        lineTo(w * 0.16f + offsetX, h * 0.64f + offsetY)
        close()
    }
    drawPath(upperPath, brush = upperBrush)
    drawPath(upperPath, color = seamColor, style = Stroke(width = w * 0.013f, join = StrokeJoin.Round))

    // Athletic side stripes
    drawLine(
        color = accentColor.copy(alpha = 0.85f),
        start = Offset(w * 0.46f + offsetX, h * 0.46f + offsetY),
        end = Offset(w * 0.56f + offsetX, h * 0.61f + offsetY),
        strokeWidth = w * 0.022f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accentColor.copy(alpha = 0.85f),
        start = Offset(w * 0.54f + offsetX, h * 0.43f + offsetY),
        end = Offset(w * 0.64f + offsetX, h * 0.61f + offsetY),
        strokeWidth = w * 0.022f,
        cap = StrokeCap.Round
    )

    // Sculpted midsole
    drawRoundRect(
        color = soleColor,
        topLeft = Offset(w * 0.14f + offsetX, h * 0.62f + offsetY),
        size = Size(w * 0.73f, h * 0.08f),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
    )
}

private fun blendColors(c1: Color, c2: Color, ratio: Float): Color {
    val r = (c1.red * (1f - ratio) + c2.red * ratio).coerceIn(0f, 1f)
    val g = (c1.green * (1f - ratio) + c2.green * ratio).coerceIn(0f, 1f)
    val b = (c1.blue * (1f - ratio) + c2.blue * ratio).coerceIn(0f, 1f)
    return Color(r, g, b, 1f)
}

@Composable
fun WeatherHeroCard(
    weatherSnapshot: WeatherSnapshot?,
    selectedDayIndex: Int,
    useFahrenheit: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onSelectDay: (Int) -> Unit,
    onToggleUnit: () -> Unit,
    onOpenCitySearch: () -> Unit,
    onRefreshGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeForecast: DailyWeatherForecast? = weatherSnapshot?.dailyForecasts?.getOrNull(selectedDayIndex)
        ?: weatherSnapshot?.dailyForecasts?.firstOrNull()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weather_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenCitySearch() }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                            .testTag("city_selector_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationCity,
                            contentDescription = "Select City",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = weatherSnapshot?.location?.displayTitle ?: "Select City",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap to change city forecast",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = onToggleUnit,
                            label = {
                                Text(
                                    text = if (useFahrenheit) "°F" else "°C",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier.testTag("toggle_temp_unit_chip")
                        )
                        IconButton(
                            onClick = onRefreshGps,
                            modifier = Modifier.testTag("gps_weather_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Use Local GPS Weather",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onOpenCitySearch,
                            modifier = Modifier.testTag("search_city_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search City",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Fetching live meteorological telemetry...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    errorMessage != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(onClick = onOpenCitySearch) {
                                Text("Choose City")
                            }
                        }
                    }

                    activeForecast != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = weatherConditionIcon(
                                            activeForecast.weatherCode,
                                            activeForecast.isRainy
                                        ),
                                        contentDescription = activeForecast.conditionLabel,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = formatTemp(activeForecast.tempCurrentOrMidC, useFahrenheit),
                                        style = MaterialTheme.typography.displayMedium
                                    )
                                }
                                Text(
                                    text = "${activeForecast.dayLabel} · ${activeForecast.conditionLabel} · Feels like ${
                                        formatTemp(activeForecast.feelsLikeC, useFahrenheit)
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Warmth Tier ${activeForecast.recommendedWarmthLevel}/5",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.WaterDrop,
                                            contentDescription = "Rain Chance",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = "${activeForecast.precipitationProbability}%",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Air,
                                            contentDescription = "Wind Speed",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${activeForecast.windSpeedKmh.roundToInt()} km/h",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeForecast.stylingAdvisory,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            itemsIndexed(weatherSnapshot?.dailyForecasts.orEmpty()) { index, day ->
                                val isSelected = index == selectedDayIndex
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                    },
                                    border = if (isSelected) null else BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .clickable { onSelectDay(index) }
                                        .testTag("forecast_day_$index")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = day.dayLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Icon(
                                            imageVector = weatherConditionIcon(day.weatherCode, day.isRainy),
                                            contentDescription = day.conditionLabel,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${formatTemp(day.tempMaxC, useFahrenheit)} / ${
                                                formatTemp(day.tempMinC, useFahrenheit)
                                            }",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}

@Composable
fun CitySearchDialog(
    searchResults: List<CityLocation>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectCity: (CityLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Weather Location",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    label = { Text("Search city name...") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("city_search_input")
                )

                if (isSearching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Searching cities...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                val listToDisplay = if (query.trim().length >= 2 && searchResults.isNotEmpty()) {
                    searchResults
                } else {
                    PRESET_CITIES
                }

                Text(
                    text = if (query.trim().length >= 2 && searchResults.isNotEmpty()) {
                        "Matching Cities"
                    } else {
                        "Fashion Capitals & Quick Presets"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listToDisplay.take(6).forEach { city ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCity(city) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = city.displayTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


/** A one-line "Notes" toggle; the text only appears when tapped. */
@Composable
fun ExpandableNote(text: String, label: String = "Notes", modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Text(
                text = if (expanded) "Hide ${label.lowercase()}" else label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
