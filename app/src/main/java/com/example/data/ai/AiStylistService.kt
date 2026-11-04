package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.weather.DailyWeatherForecast
import com.example.util.ImageUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.abs

private const val TAG = "AiStylistService"

data class GarmentAiAnalysis(
    val name: String,
    val category: String,
    val subCategory: String,
    val materialComposition: String,
    val colorName: String,
    val colorHex: String,
    val warmthLevel: Int,
    val isRainResistant: Boolean,
    val isWindResistant: Boolean,
    val occasions: List<String>,
    val seasons: List<String>,
    val notes: String
)

data class MaterialScanResult(
    val materialComposition: String,
    val careInstructions: String,
    val suggestedWarmthLevel: Int,
    val isRainResistant: Boolean,
    val isWindResistant: Boolean,
    val recommendedSeasons: List<String>
)

data class AiOutfitSuggestion(
    val outfitName: String,
    val itemIds: List<String>,
    val occasion: String,
    val weatherComfortScore: Int,
    val stylistRationale: String,
    val layeringTip: String
)

data class OutfitComfortEvaluation(
    val score: Int,
    val headline: String,
    val detailTip: String
)

class AiStylistService {

    @Volatile
    var preferFastLiteModel: Boolean = false

    @Volatile
    var autoFallbackOnHighDemand: Boolean = true

    private val photoTagSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string(description = "Specific, accurate fashion name of the garment, e.g. 'Charcoal Ribbed Merino Crewneck' or 'Light Wash Straight-Leg Denim Jeans'"),
            "category" to Schema.enumeration(
                values = listOf("TOP", "BOTTOM", "OUTERWEAR", "ONE_PIECE", "FOOTWEAR", "ACCESSORY"),
                description = "Primary wardrobe category"
            ),
            "subCategory" to Schema.string(description = "Specific garment sub-type, e.g. 'Crewneck Sweater', 'Oxford Button-Down', 'Wide-Leg Trousers', 'Puffer Jacket', 'Low-Top Sneakers'"),
            "materialComposition" to Schema.string(description = "Detected fabric weave or read fiber composition, e.g. '100% Cotton Denim', '80% Wool, 20% Nylon', 'Fine Merino Knit', 'Smooth Calfskin Leather'"),
            "colorName" to Schema.string(description = "Accurate fashion color name, e.g. 'Midnight Navy', 'Heather Grey', 'Sage Olive', 'Warm Camel'"),
            "colorHex" to Schema.string(description = "7-character hex color code starting with #, e.g. '#2C3E50'"),
            "warmthLevel" to Schema.integer(description = "Thermal warmth rating from 1 (ultralight summer tank/linen) to 5 (heavy winter coat/chunky wool)"),
            "isRainResistant" to Schema.boolean(description = "True if the material or garment is water-resistant or rain-ready"),
            "isWindResistant" to Schema.boolean(description = "True if tightly woven, leather, shell, or wind-blocking"),
            "occasions" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 occasions from: Casual, Work, Evening, Active, Formal, Travel"
            ),
            "seasons" to Schema.array(
                items = Schema.string(),
                description = "1 to 4 seasons from: Spring, Summer, Autumn, Winter"
            ),
            "notes" to Schema.string(description = "Concise fit, fabric texture, care, and styling note under 160 characters")
        )
    )

    private val materialLabelSchema = Schema.obj(
        properties = mapOf(
            "materialComposition" to Schema.string(description = "Exact fiber percentages read from the care label or fabric weave analysis, e.g. '80% Virgin Wool, 20% Cashmere'"),
            "careInstructions" to Schema.string(description = "Care instructions read from the tag or recommended for this fiber, e.g. 'Dry clean only, cool iron'"),
            "suggestedWarmthLevel" to Schema.integer(description = "Calibrated warmth rating 1 (ultralight linen/silk) to 5 (heavy wool/cashmere/down)"),
            "isRainResistant" to Schema.boolean(description = "True if the fiber/weave is water-repellent (e.g. nylon shell, treated gabardine, gore-tex)"),
            "isWindResistant" to Schema.boolean(description = "True if tightly woven or wind-blocking"),
            "recommendedSeasons" to Schema.array(
                items = Schema.string(),
                description = "1 to 4 suitable seasons from: Spring, Summer, Autumn, Winter"
            )
        )
    )

    private val outfitSuggestionsSchema = Schema.obj(
        properties = mapOf(
            "outfits" to Schema.array(
                description = "2 to 3 curated outfit suggestions built strictly from the provided closetItemIds",
                items = Schema.obj(
                    properties = mapOf(
                        "outfitName" to Schema.string(description = "Editorial title for the look"),
                        "itemIds" to Schema.array(
                            items = Schema.string(),
                            description = "Exact IDs of the chosen closet items from the user's inventory"
                        ),
                        "occasion" to Schema.string(description = "Target occasion"),
                        "weatherComfortScore" to Schema.integer(description = "Weather suitability score from 65 to 100"),
                        "stylistRationale" to Schema.string(description = "Why these specific pieces and their materials harmonize in silhouette and weather performance"),
                        "layeringTip" to Schema.string(description = "Practical styling or weather layering tip")
                    )
                )
            )
        )
    )

    // Structured Schema Models
    private val photoTagModelPrimary by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = photoTagSchema
            }
        )
    }

    private val photoTagModelLite by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = photoTagSchema
            }
        )
    }

    // Flexible JSON fallback models (no strict responseSchema, avoiding SDK SerializationException / UnknownException)
    private val flexibleJsonModelPrimary by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }

    private val flexibleJsonModelLite by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }

    private val materialModelPrimary by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = materialLabelSchema
            }
        )
    }

    private val materialModelLite by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = materialLabelSchema
            }
        )
    }

    private val outfitStylistModelPrimary by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = outfitSuggestionsSchema
            }
        )
    }

    private val outfitStylistModelLite by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = outfitSuggestionsSchema
            }
        )
    }

    // Virtual Try-On (VTON) Image Generation Models ("Nano Banana" models per Firebase AI Logic)
    private val vtonImageModelPrimary by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val vtonImageModelLite by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite-image",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private fun readableExceptionMessage(e: Throwable): String {
        val rootCauseMsg = e.cause?.localizedMessage?.takeIf { it.isNotBlank() }
        val directMsg = e.localizedMessage?.takeIf { it.isNotBlank() }
        return when {
            directMsg != null && !directMsg.contains("Something unexpected happened", ignoreCase = true) -> directMsg
            rootCauseMsg != null -> rootCauseMsg
            else -> "AI model temporarily busy or unable to parse image. Tap 'Retry AI Auto-Detect' to re-run."
        }
    }

    private fun extractCleanJsonObject(rawText: String): JSONObject {
        val trimmed = rawText.trim()
        val startIdx = trimmed.indexOf('{')
        val endIdx = trimmed.lastIndexOf('}')
        val jsonSubstring = if (startIdx >= 0 && endIdx > startIdx) {
            trimmed.substring(startIdx, endIdx + 1)
        } else {
            trimmed
        }
        return JSONObject(jsonSubstring)
    }

    /**
     * Executes a Gemini request across a multi-stage fallback pipeline:
     * 1. Primary schema model (`gemini-3.8-flash` or `gemini-3.5-flash-lite` based on user setting)
     * 2. Alternate schema model
     * 3. Flexible JSON model (`responseMimeType = "application/json"` without strict schema) to prevent
     *    SDK `UnknownException("Something unexpected happened.")` on complex multimodal payloads.
     */
    private suspend fun executeResilientJsonCall(
        primarySchemaModel: GenerativeModel,
        liteSchemaModel: GenerativeModel,
        timeoutMs: Long = 45_000L,
        block: suspend (GenerativeModel) -> GenerateContentResponse
    ): GenerateContentResponse {
        val firstSchema = if (preferFastLiteModel) liteSchemaModel else primarySchemaModel
        val secondSchema = if (preferFastLiteModel) primarySchemaModel else liteSchemaModel
        val firstFlex = if (preferFastLiteModel) flexibleJsonModelLite else flexibleJsonModelPrimary

        val modelSequence = if (autoFallbackOnHighDemand) {
            listOf(
                firstSchema to 0L,
                secondSchema to 350L,
                firstFlex to 700L,
                flexibleJsonModelLite to 1200L
            )
        } else {
            listOf(
                firstSchema to 0L,
                firstFlex to 350L
            )
        }

        var lastException: Exception? = null
        for ((index, pair) in modelSequence.withIndex()) {
            val (candidateModel, delayMs) = pair
            if (delayMs > 0L) {
                delay(delayMs)
            }
            try {
                return withTimeout(timeoutMs) {
                    block(candidateModel)
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastException = e
                Log.w(TAG, "Attempt ${index + 1} timed out, switching to fallback stage...", e)
            } catch (e: Exception) {
                lastException = e
                Log.w(
                    TAG,
                    "Attempt ${index + 1} failed (${readableExceptionMessage(e)}), switching to fallback stage...",
                    e
                )
            }
        }
        val finalMsg = lastException?.let { readableExceptionMessage(it) }
            ?: "AI service is temporarily busy. Tap 'Retry AI Auto-Detect'."
        throw IllegalStateException(finalMsg, lastException)
    }

    suspend fun analyzeGarmentPhoto(
        bitmap: Bitmap,
        materialLabelBitmap: Bitmap? = null,
        userHintName: String = ""
    ): Result<GarmentAiAnalysis> {
        val safeGarmentBmp = ImageUtils.scaleDownBitmap(bitmap, maxDim = 512)
        val safeLabelBmp = materialLabelBitmap?.let { ImageUtils.scaleDownBitmap(it, maxDim = 512) }
        val (pixelColorName, pixelColorHex) = ImageUtils.extractDominantGarmentColor(safeGarmentBmp)

        return try {
            val hintClause = if (userHintName.isNotBlank() && !userHintName.startsWith("Uploaded", ignoreCase = true) && !userHintName.startsWith("Scanning", ignoreCase = true)) {
                "User hint for this piece: \"$userHintName\". "
            } else {
                ""
            }

            val response = executeResilientJsonCall(
                primarySchemaModel = photoTagModelPrimary,
                liteSchemaModel = photoTagModelLite
            ) { activeModel ->
                activeModel.generateContent(
                    content {
                        image(safeGarmentBmp)
                        if (safeLabelBmp != null) {
                            image(safeLabelBmp)
                        }
                        text(
                            """
                            You are an expert fashion archivist and textile specialist analyzing a garment for a digital wardrobe.
                            ${if (safeLabelBmp != null) "Image 1 is the garment photo; Image 2 is its inside care/material composition label." else "Inspect the garment photo carefully."}
                            $hintClause
                            Visual pixel color sample from the center of the garment: $pixelColorName ($pixelColorHex).
                            
                            Carefully examine:
                            1. Silhouette & Construction: neckline/collar, sleeve length, buttons/zippers/lapels, pocket style, rise/inseam, or sole shape.
                            2. Category: MUST be one of TOP, BOTTOM, OUTERWEAR, ONE_PIECE, FOOTWEAR, ACCESSORY.
                               - Shirts, tees, blouses, sweaters, hoodies, cardigans, polos, tank tops -> TOP
                               - Jeans, trousers, chinos, shorts, skirts, leggings, sweatpants -> BOTTOM
                               - Coats, trenches, blazers, leather/denim/puffer jackets, parkas, windbreakers -> OUTERWEAR
                               - Dresses, jumpsuits, rompers, overalls -> ONE_PIECE
                               - Sneakers, boots, loafers, heels, sandals, flats -> FOOTWEAR
                               - Scarves, hats, belts, bags, sunglasses, watches, jewelry -> ACCESSORY
                            3. Fabric & Material Composition: Inspect the surface weave/knit texture (e.g., twill, denim, ribbed wool knit, cotton jersey, poplin, linen, fleece, quilted nylon, calfskin leather) AND read any text on the care label if provided.
                            4. Thermal & Weather Performance: Rate warmthLevel from 1 (ultralight summer) to 5 (heavy winter outerwear/chunky wool), plus rain and wind resistance.
                            
                            Return a JSON object with keys:
                            "name", "category", "subCategory", "materialComposition", "colorName", "colorHex", "warmthLevel", "isRainResistant", "isWindResistant", "occasions" (array of strings from Casual, Work, Evening, Active, Formal, Travel), "seasons" (array of strings from Spring, Summer, Autumn, Winter), and "notes".
                            """.trimIndent()
                        )
                    }
                )
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned an empty response.")
            val json = extractCleanJsonObject(rawJson)
            val occasionsArr = json.optJSONArray("occasions")
            val seasonsArr = json.optJSONArray("seasons")
            val occasions = buildList {
                if (occasionsArr != null) {
                    for (i in 0 until minOf(occasionsArr.length(), 6)) {
                        add(occasionsArr.getString(i))
                    }
                }
            }.ifEmpty { listOf("Casual") }

            val seasons = buildList {
                if (seasonsArr != null) {
                    for (i in 0 until minOf(seasonsArr.length(), 4)) {
                        add(seasonsArr.getString(i))
                    }
                }
            }.ifEmpty { listOf("Spring", "Autumn") }

            val rawHex = json.optString("colorHex", pixelColorHex).trim()
            val validHex = if (rawHex.startsWith("#") && (rawHex.length == 7 || rawHex.length == 4)) {
                rawHex
            } else {
                pixelColorHex
            }

            Result.success(
                GarmentAiAnalysis(
                    name = json.optString("name", "$pixelColorName Garment").take(90),
                    category = GarmentCategory.fromCode(json.optString("category", "TOP")).name,
                    subCategory = json.optString("subCategory", "Apparel").take(50),
                    materialComposition = json.optString("materialComposition", "Cotton / Natural Fiber Blend").take(180),
                    colorName = json.optString("colorName", pixelColorName).take(40),
                    colorHex = validHex,
                    warmthLevel = json.optInt("warmthLevel", 3).coerceIn(1, 5),
                    isRainResistant = json.optBoolean("isRainResistant", false),
                    isWindResistant = json.optBoolean("isWindResistant", false),
                    occasions = occasions,
                    seasons = seasons,
                    notes = json.optString("notes", "").take(300)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo auto-tagging failed after retries", e)
            Result.failure(IllegalStateException(readableExceptionMessage(e), e))
        }
    }

    /**
     * Analyzes up to 4 garment photos in a SINGLE multimodal Gemini API call to drastically
     * reduce the number of API runs when batch-tagging newly uploaded closet items.
     */
    suspend fun analyzeGarmentBatch(
        batch: List<Pair<ClosetItem, Bitmap>>
    ): Result<Map<String, GarmentAiAnalysis>> {
        if (batch.isEmpty()) return Result.success(emptyMap())
        if (batch.size == 1) {
            val (item, bmp) = batch.first()
            return analyzeGarmentPhoto(bmp, null, item.name).map { mapOf(item.id to it) }
        }

        return try {
            val prepared = batch.map { (item, bmp) ->
                val scaled = ImageUtils.scaleDownBitmap(bmp, maxDim = 448)
                val (cName, cHex) = ImageUtils.extractDominantGarmentColor(scaled)
                Triple(item, scaled, cName to cHex)
            }

            val response = executeResilientJsonCall(
                primarySchemaModel = flexibleJsonModelPrimary,
                liteSchemaModel = flexibleJsonModelLite,
                timeoutMs = 60_000L
            ) { activeModel ->
                activeModel.generateContent(
                    content {
                        val metadataLines = StringBuilder()
                        prepared.forEachIndexed { idx, (item, scaledBmp, colorPair) ->
                            image(scaledBmp)
                            metadataLines.appendLine(
                                "Image ${idx + 1}: itemId=\"${item.id}\", centerPixelColor=\"${colorPair.first}\" (${colorPair.second})"
                            )
                        }
                        text(
                            """
                            You are an expert fashion archivist analyzing ${prepared.size} garment photos in a single batch.
                            The images above correspond in exact order to:
                            $metadataLines
                            
                            For EACH image, classify its silhouette, category (MUST be one of TOP, BOTTOM, OUTERWEAR, ONE_PIECE, FOOTWEAR, ACCESSORY), subCategory, visible fabric/materialComposition, colorName, colorHex, warmthLevel (1-5), isRainResistant, isWindResistant, occasions, seasons, and notes.
                            
                            Return a JSON object with a single key "items" containing an array of objects, where each object has:
                            "itemId", "name", "category", "subCategory", "materialComposition", "colorName", "colorHex", "warmthLevel", "isRainResistant", "isWindResistant", "occasions", "seasons", "notes".
                            """.trimIndent()
                        )
                    }
                )
            }

            val rawJson = response.text ?: throw IllegalStateException("Empty batch AI response.")
            val root = extractCleanJsonObject(rawJson)
            val arr = root.optJSONArray("items") ?: throw IllegalStateException("Missing 'items' array in batch response.")

            val resultMap = mutableMapOf<String, GarmentAiAnalysis>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val matchedEntry = prepared.getOrNull(i)
                val itemId = obj.optString("itemId").takeIf { it.isNotBlank() }
                    ?: matchedEntry?.first?.id
                    ?: continue
                val fallbackColor = matchedEntry?.third ?: ("Warm Camel" to "#C19A6B")

                val occArr = obj.optJSONArray("occasions")
                val occasions = buildList {
                    if (occArr != null) {
                        for (j in 0 until minOf(occArr.length(), 6)) add(occArr.getString(j))
                    }
                }.ifEmpty { listOf("Casual") }

                val seaArr = obj.optJSONArray("seasons")
                val seasons = buildList {
                    if (seaArr != null) {
                        for (j in 0 until minOf(seaArr.length(), 4)) add(seaArr.getString(j))
                    }
                }.ifEmpty { listOf("Spring", "Autumn") }

                val rawHex = obj.optString("colorHex", fallbackColor.second).trim()
                val validHex = if (rawHex.startsWith("#") && (rawHex.length == 7 || rawHex.length == 4)) {
                    rawHex
                } else {
                    fallbackColor.second
                }

                resultMap[itemId] = GarmentAiAnalysis(
                    name = obj.optString("name", "${fallbackColor.first} Garment").take(90),
                    category = GarmentCategory.fromCode(obj.optString("category", "TOP")).name,
                    subCategory = obj.optString("subCategory", "Apparel").take(50),
                    materialComposition = obj.optString("materialComposition", "Cotton / Natural Fiber Blend").take(180),
                    colorName = obj.optString("colorName", fallbackColor.first).take(40),
                    colorHex = validHex,
                    warmthLevel = obj.optInt("warmthLevel", 3).coerceIn(1, 5),
                    isRainResistant = obj.optBoolean("isRainResistant", false),
                    isWindResistant = obj.optBoolean("isWindResistant", false),
                    occasions = occasions,
                    seasons = seasons,
                    notes = obj.optString("notes", "").take(300)
                )
            }
            Result.success(resultMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Multi-image single-call batch failed, falling back to individual calls", e)
            Result.failure(e)
        }
    }

    suspend fun scanMaterialLabel(bitmap: Bitmap): Result<MaterialScanResult> {
        val safeBmp = ImageUtils.scaleDownBitmap(bitmap, maxDim = 512)
        return try {
            val response = executeResilientJsonCall(
                primarySchemaModel = materialModelPrimary,
                liteSchemaModel = materialModelLite
            ) { activeModel ->
                activeModel.generateContent(
                    content {
                        image(safeBmp)
                        text(
                            """
                            Scan this clothing care label, fabric tag, or close-up fabric texture.
                            Extract the exact material/fiber composition percentages (e.g. '80% Wool, 20% Polyamide' or inferred fabric weave if no text is visible),
                            care instructions, calibrated thermal warmth rating (1 to 5), rain resistance, wind resistance, and recommended seasons.
                            Return a JSON object with keys: "materialComposition", "careInstructions", "suggestedWarmthLevel", "isRainResistant", "isWindResistant", "recommendedSeasons".
                            """.trimIndent()
                        )
                    }
                )
            }
            val rawJson = response.text ?: throw IllegalStateException("Empty material scan response.")
            val json = extractCleanJsonObject(rawJson)
            val seasonsArr = json.optJSONArray("recommendedSeasons")
            val seasons = buildList {
                if (seasonsArr != null) {
                    for (i in 0 until minOf(seasonsArr.length(), 4)) {
                        add(seasonsArr.getString(i))
                    }
                }
            }.ifEmpty { listOf("Spring", "Autumn") }

            Result.success(
                MaterialScanResult(
                    materialComposition = json.optString("materialComposition", "100% Cotton").take(180),
                    careInstructions = json.optString("careInstructions", "Machine wash cold or dry clean").take(220),
                    suggestedWarmthLevel = json.optInt("suggestedWarmthLevel", 3).coerceIn(1, 5),
                    isRainResistant = json.optBoolean("isRainResistant", false),
                    isWindResistant = json.optBoolean("isWindResistant", false),
                    recommendedSeasons = seasons
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Material label scan failed after retries", e)
            Result.failure(IllegalStateException(readableExceptionMessage(e), e))
        }
    }

    /**
     * Generates a Virtual Try-On (VTON) image using `gemini-nano-banana-2.1` (with fallback
     * to `gemini-3.1-flash-lite-image`). Accepts an optional user reference photo plus the
     * selected closet items' photos and fabric descriptions.
     */
    suspend fun generateVirtualTryOn(
        userReferencePhoto: Bitmap?,
        selectedItems: List<ClosetItem>,
        occasion: String,
        weatherSummary: String,
        fitNotes: String
    ): Result<Bitmap> {
        if (selectedItems.isEmpty()) {
            return Result.failure(IllegalStateException("Select at least one garment in the Studio to run Virtual Try-On."))
        }
        return try {
            val garmentSummary = selectedItems.joinToString("; ") { item ->
                val mat = if (item.materialComposition.isNotBlank()) " in ${item.materialComposition}" else ""
                "${item.category}: ${item.name} (${item.colorName}$mat)"
            }
            val garmentBitmaps = selectedItems.mapNotNull { item ->
                if (item.imageBase64.isNotBlank()) {
                    ImageUtils.decodeBase64ToBitmap(item.imageBase64)?.let {
                        ImageUtils.scaleDownBitmap(it, maxDim = 512)
                    }
                } else {
                    null
                }
            }.take(4)

            val safeUserPhoto = userReferencePhoto?.let { ImageUtils.scaleDownBitmap(it, maxDim = 512) }

            val modelsToTry = if (autoFallbackOnHighDemand) {
                listOf(vtonImageModelPrimary to 0L, vtonImageModelLite to 600L)
            } else {
                listOf(vtonImageModelPrimary to 0L)
            }

            var lastErr: Exception? = null
            var generatedBitmap: Bitmap? = null
            for ((model, waitMs) in modelsToTry) {
                if (waitMs > 0L) delay(waitMs)
                try {
                    val response = withTimeout(60_000L) {
                        model.generateContent(
                            content {
                                if (safeUserPhoto != null) {
                                    image(safeUserPhoto)
                                }
                                garmentBitmaps.forEach { bmp ->
                                    image(bmp)
                                }
                                val basePrompt = if (safeUserPhoto != null) {
                                    "Virtual Try-On (VTON): Dress the person in the first reference photo in this complete outfit: $garmentSummary. " +
                                        "Preserve the person's pose, identity, lighting, and proportions naturally while accurately rendering the fabric drape, colors, and textures of the selected garments."
                                } else {
                                    "Virtual Try-On (VTON) Editorial Lookbook: Generate a realistic full-body fashion lookbook photo of a model wearing this exact outfit: $garmentSummary."
                                }
                                val extraContext = " Occasion: $occasion. Weather context: $weatherSummary." +
                                    if (fitNotes.isNotBlank()) " Fit preference: $fitNotes." else ""
                                text(basePrompt + extraContext)
                            }
                        )
                    }
                    generatedBitmap = response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                    if (generatedBitmap != null) break
                } catch (e: CancellationException) {
                    if (e !is TimeoutCancellationException) throw e
                    lastErr = e
                } catch (e: Exception) {
                    lastErr = e
                }
            }

            if (generatedBitmap != null) {
                Result.success(generatedBitmap)
            } else {
                val msg = lastErr?.let { readableExceptionMessage(it) }
                    ?: "No Virtual Try-On image was returned by the model."
                Result.failure(IllegalStateException(msg, lastErr))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Virtual Try-On generation failed", e)
            Result.failure(IllegalStateException(readableExceptionMessage(e), e))
        }
    }

    suspend fun generateWeatherOutfits(
        closetItems: List<ClosetItem>,
        weather: DailyWeatherForecast,
        cityName: String,
        occasion: String
    ): Result<List<AiOutfitSuggestion>> {
        if (closetItems.isEmpty()) {
            return Result.failure(IllegalStateException("Your closet is empty. Add garments or load the starter capsule first."))
        }
        return try {
            val inventoryText = closetItems.joinToString("\n") { item ->
                val mat = if (item.materialComposition.isNotBlank()) " | material=${item.materialComposition}" else ""
                "- id=\"${item.id}\" | name=\"${item.name}\" | category=${item.category} (${item.subCategory})$mat | " +
                    "color=${item.colorName} | warmth=${item.warmthLevel}/5 | rainResistant=${item.isRainResistant} | " +
                    "windResistant=${item.isWindResistant} | occasions=${item.occasions.joinToString(",")}"
            }

            val prompt = """
                You are an expert editorial fashion stylist. Create 2 to 3 complete, weather-appropriate outfits strictly using ONLY items from the user's digital closet below.
                
                ACTIVE WEATHER FORECAST:
                - City: $cityName (${weather.dayLabel}, ${weather.dateIso})
                - Condition: ${weather.conditionLabel}
                - Temperature: ${weather.tempCurrentOrMidC}°C (High ${weather.tempMaxC}°C / Low ${weather.tempMinC}°C, Feels like ${weather.feelsLikeC}°C)
                - Rain Probability: ${weather.precipitationProbability}% (Rainy=${weather.isRainy})
                - Wind Speed: ${weather.windSpeedKmh} km/h (Windy=${weather.isWindy})
                - Target Warmth Level: ${weather.recommendedWarmthLevel}/5
                
                TARGET OCCASION: $occasion
                
                USER'S DIGITAL CLOSET INVENTORY (Use exact `id` values in `itemIds`):
                $inventoryText
                
                RULES:
                1. Every ID in `itemIds` MUST be an exact `id` from the inventory above.
                2. Combine coherent silhouettes (e.g. TOP + BOTTOM + optional OUTERWEAR + FOOTWEAR + optional ACCESSORY, or ONE_PIECE + optional OUTERWEAR + FOOTWEAR).
                3. Match the material composition, thermal warmth, and rain/wind protection to ${weather.feelsLikeC}°C and ${weather.conditionLabel}.
                Return a JSON object with an "outfits" array where each item has "outfitName", "itemIds", "occasion", "weatherComfortScore", "stylistRationale", and "layeringTip".
            """.trimIndent()

            val response = executeResilientJsonCall(
                primarySchemaModel = outfitStylistModelPrimary,
                liteSchemaModel = outfitStylistModelLite
            ) { activeModel ->
                activeModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI Stylist returned an empty response.")
            val root = extractCleanJsonObject(rawJson)
            val outfitsArray = root.getJSONArray("outfits")
            val validIds = closetItems.map { it.id }.toSet()

            val suggestions = buildList {
                for (i in 0 until outfitsArray.length()) {
                    val obj = outfitsArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("itemIds")
                    val matchedIds = buildList {
                        for (j in 0 until idsArr.length()) {
                            val candidate = idsArr.getString(j)
                            if (candidate in validIds) add(candidate)
                        }
                    }.distinct()

                    if (matchedIds.isNotEmpty()) {
                        add(
                            AiOutfitSuggestion(
                                outfitName = obj.optString("outfitName", "Curated Weather Look"),
                                itemIds = matchedIds,
                                occasion = obj.optString("occasion", occasion),
                                weatherComfortScore = obj.optInt("weatherComfortScore", 88).coerceIn(50, 100),
                                stylistRationale = obj.optString("stylistRationale", ""),
                                layeringTip = obj.optString("layeringTip", "")
                            )
                        )
                    }
                }
            }

            if (suggestions.isEmpty()) {
                Result.failure(IllegalStateException("Could not match closet items from AI response. Try adding more categories to your closet."))
            } else {
                Result.success(suggestions)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI outfit curation failed after fallback retries", e)
            Result.failure(IllegalStateException(readableExceptionMessage(e), e))
        }
    }

    companion object {
        fun evaluateOutfitComfort(
            selectedItems: List<ClosetItem>,
            weather: DailyWeatherForecast?
        ): OutfitComfortEvaluation {
            if (selectedItems.isEmpty()) {
                return OutfitComfortEvaluation(
                    score = 0,
                    headline = "Select pieces below to build your look",
                    detailTip = "Pick a top & bottom (or one-piece), outerwear, and footwear to calculate live weather compatibility."
                )
            }
            if (weather == null) {
                return OutfitComfortEvaluation(
                    score = 85,
                    headline = "${selectedItems.size} pieces layered cleanly",
                    detailTip = "Connect local or city weather to evaluate thermal and rain protection."
                )
            }

            val avgWarmth = selectedItems.map { it.warmthLevel }.average()
            val hasOuterwear = selectedItems.any { it.category == GarmentCategory.OUTERWEAR.name }
            val effectiveWarmth = if (hasOuterwear) avgWarmth + 0.5 else avgWarmth
            val targetWarmth = weather.recommendedWarmthLevel.toDouble()
            val warmthDelta = abs(effectiveWarmth - targetWarmth)

            var score = (100.0 - (warmthDelta * 16.0)).toInt()
            val tips = mutableListOf<String>()

            if (weather.isRainy) {
                val hasRainGear = selectedItems.any { it.isRainResistant }
                if (hasRainGear) {
                    score += 5
                    tips.add("Rain-resistant protection included for ${weather.precipitationProbability}% rain risk.")
                } else {
                    score -= 18
                    tips.add("Rain is forecasted (${weather.precipitationProbability}%) — consider adding a water-resistant outer layer or boots.")
                }
            }

            if (weather.isWindy) {
                val hasWindGear = selectedItems.any { it.isWindResistant }
                if (!hasWindGear) {
                    score -= 10
                    tips.add("Breezy (${weather.windSpeedKmh.toInt()} km/h) — a wind-blocking coat or trench will boost comfort.")
                }
            }

            when {
                effectiveWarmth < targetWarmth - 0.8 ->
                    tips.add("Slightly light for ${weather.feelsLikeC}°C — layer knitwear or outerwear.")
                effectiveWarmth > targetWarmth + 0.9 ->
                    tips.add("Warm insulation for ${weather.feelsLikeC}°C — ensure base layers are breathable.")
                else ->
                    tips.add("Thermal weight is well-calibrated for ${weather.feelsLikeC}°C (${weather.conditionLabel}).")
            }

            val finalScore = score.coerceIn(35, 99)
            val headline = when {
                finalScore >= 88 -> "Ideal Match for ${weather.dayLabel} (${weather.feelsLikeC}°C)"
                finalScore >= 72 -> "Good Weather Fit (${weather.feelsLikeC}°C · ${weather.conditionLabel})"
                else -> "Weather Adjustment Recommended (${weather.feelsLikeC}°C)"
            }

            return OutfitComfortEvaluation(
                score = finalScore,
                headline = headline,
                detailTip = tips.joinToString(" ")
            )
        }
    }
}
