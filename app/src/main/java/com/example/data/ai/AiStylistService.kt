package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.settings.AppSettings
import com.example.data.weather.DailyWeatherForecast
import com.example.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private const val TAG = "AiStylistService"

val ALLOWED_OCCASIONS = listOf("Casual", "Work", "Evening", "Active", "Formal", "Travel")
val ALLOWED_SEASONS = listOf("Spring", "Summer", "Autumn", "Winter")

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
    /** Short shop-style description, stored in ClosetItem.notes. */
    val notes: String,
    /** Degrees to rotate the photo clockwise so the item is upright: 0, 90, 180 or 270. */
    val rotationDegrees: Int = 0
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

class AiStylistService(private val settings: AppSettings) {

    /** Set once the image model says it isn't in the key's plan, so we stop asking this session. */
    @Volatile
    private var imageModelUnavailable: String? = null

    /** Models that hit a quota recently -> time until which they are skipped. */
    private val skipUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Why product-style photos / try-on can't run right now, or null if they can. */
    val imageModelProblem: String? get() = imageModelUnavailable

    private fun engine(): GeminiEngine {
        val key = settings.geminiApiKey
        if (key.isNotBlank()) return RestGeminiEngine(key)
        if (com.example.BuildConfig.LOCAL_ONLY) {
            throw IllegalStateException("Add your Gemini API key in Settings \u2192 API keys & models.")
        }
        return FirebaseGeminiEngine()
    }

    val engineLabel: String get() = try {
        engine().label
    } catch (e: Exception) {
        "no Gemini key yet"
    }

    /** Text models to try, in order. */
    private fun textModels(): List<String> {
        val primary = if (settings.preferFastLiteModel) settings.liteModel else settings.textModel
        val secondary = if (settings.preferFastLiteModel) settings.textModel else settings.liteModel
        return if (settings.autoFallbackOnHighDemand) listOf(primary, secondary).distinct() else listOf(primary)
    }

    /**
     * Runs [block] on each model in turn. A busy/rate-limited model gets one retry after a
     * short pause before moving on to the next model.
     */
    private suspend fun <T> withFallback(
        models: List<String>,
        timeoutMs: Long = 60_000L,
        block: suspend (GeminiEngine, String) -> T
    ): T {
        val engine = engine()
        var last: Exception? = null
        val now = System.currentTimeMillis()
        // Skip models that recently hit their quota, unless that would leave nothing to try.
        val usable = models.filter { (skipUntil[it] ?: 0L) < now }.ifEmpty { models }
        for (model in usable) {
            for (attempt in 0..2) {
                try {
                    return withTimeout(timeoutMs) { block(engine, model) }
                } catch (e: TimeoutCancellationException) {
                    last = GeminiException("The request timed out.", retryable = true, cause = e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GeminiException) {
                    last = e
                    if (e.rateLimited) skipUntil[model] = System.currentTimeMillis() + 15 * 60_000L
                    if (!e.retryable || e.rateLimited) break
                } catch (e: Exception) {
                    last = e
                }
                Log.w(TAG, "Model $model attempt ${attempt + 1} failed: ${last?.message}")
                if (attempt < 2) delay(if (attempt == 0) 1500L else 5000L)
            }
        }
        throw last ?: GeminiException("The AI service is unavailable.", retryable = true)
    }

    // ------------------------------------------------------------------ garment tagging

    private val garmentSchema = JSONObject(
        """
        {
          "type": "OBJECT",
          "properties": {
            "name": {"type": "STRING"},
            "category": {"type": "STRING", "enum": ["TOP","BOTTOM","OUTERWEAR","ONE_PIECE","FOOTWEAR","ACCESSORY"]},
            "subCategory": {"type": "STRING"},
            "materialComposition": {"type": "STRING"},
            "colorName": {"type": "STRING"},
            "colorHex": {"type": "STRING"},
            "warmthLevel": {"type": "INTEGER"},
            "isRainResistant": {"type": "BOOLEAN"},
            "isWindResistant": {"type": "BOOLEAN"},
            "occasions": {"type": "ARRAY", "items": {"type": "STRING", "enum": ["Casual","Work","Evening","Active","Formal","Travel"]}},
            "seasons": {"type": "ARRAY", "items": {"type": "STRING", "enum": ["Spring","Summer","Autumn","Winter"]}},
            "description": {"type": "STRING"}
          },
          "required": ["name","category","subCategory","materialComposition","colorName","colorHex",
                       "warmthLevel","isRainResistant","isWindResistant","occasions","seasons","description"]
        }
        """.trimIndent()
    )

    private val garmentInstructions = """
        You catalogue clothing for a personal wardrobe app.
        Look only at the main garment or accessory in the photo. Ignore the background, hangers,
        mannequins, the floor and any person wearing it.

        Fill in:
        - name: what a shop would call it, 2 to 5 words, colour first. Examples: "Navy Knit Polo",
          "Grey Crewneck Sweatshirt", "Black Leather Biker Jacket", "Khaki Cargo Trousers".
          If a brand or team name is clearly printed, you may add it ("Black Adidas Runners").
        - category, one of:
            TOP: t-shirts, shirts, polos, sweaters, sweatshirts, hoodies, cardigans, tank tops, jerseys
            BOTTOM: jeans, trousers, chinos, cargos, shorts, skirts, joggers, leggings
            OUTERWEAR: coats, jackets, blazers, parkas, gilets, windbreakers, raincoats
            ONE_PIECE: dresses, jumpsuits, rompers, dungarees
            FOOTWEAR: sneakers, trainers, boots, loafers, heels, sandals, slippers
            ACCESSORY: watches, bags, belts, hats, caps, scarves, sunglasses, jewellery, ties
          A zip-up hoodie is still a TOP. A shirt jacket worn open over a tee is OUTERWEAR.
        - subCategory: the specific type, e.g. "Polo Shirt", "Cargo Trousers", "Running Shoes", "Smartwatch".
        - materialComposition: the fabric you can see (or read on a label), e.g. "Cotton jersey",
          "Wool knit", "Denim", "Leather", "Polyester mesh". Do not invent exact percentages unless a label shows them.
        - colorName: the main colour in plain words ("Navy", "Heather Grey", "Olive", "Off-White").
        - colorHex: that colour as #RRGGBB, sampled from the garment itself, not the background.
        - warmthLevel: 1 = very light summer wear, 3 = normal layer, 5 = heavy winter coat.
        - isRainResistant / isWindResistant: only true for genuinely water-repellent or wind-blocking items.
        - occasions: 1 to 4 that fit. seasons: 1 to 4 that fit.
        - description: one or two short sentences, at most 220 characters, written like a product
          listing: what it is, notable details (collar, fit, pockets, print), and one pairing idea.
          Plain, factual tone. No hype, no emojis, never mention AI or the photo.
    """.trimIndent()

    suspend fun analyzeGarmentPhoto(
        bitmap: Bitmap,
        materialLabelBitmap: Bitmap? = null,
        userHintName: String = ""
    ): Result<GarmentAiAnalysis> = runCatchingAi {
        val images = buildList {
            add(ImageUtils.flattenOnWhite(ImageUtils.scaleDownBitmap(bitmap, maxDim = 1024)))
            if (materialLabelBitmap != null) add(ImageUtils.scaleDownBitmap(materialLabelBitmap, maxDim = 1024))
        }
        val hint = userHintName.trim()
            .takeIf { it.isNotBlank() && !it.startsWith("Uploaded", true) && !it.startsWith("New piece", true) }
        val prompt = buildString {
            appendLine(garmentInstructions)
            if (materialLabelBitmap != null) {
                appendLine("The second image is the garment's care/composition label. Use it for materialComposition.")
            }
            if (hint != null) appendLine("The owner calls this item \"$hint\". Use it if it fits what you see.")
            append("Return only the JSON object.")
        }
        val raw = withFallback(textModels()) { engine, model ->
            engine.generateJson(model, prompt, images, garmentSchema)
        }
        parseGarment(extractJsonObject(raw), fallbackHex = ImageUtils.extractDominantGarmentColor(bitmap).second)
    }

    private fun parseGarment(json: JSONObject, fallbackHex: String): GarmentAiAnalysis {
        val hex = json.optString("colorHex").trim().let {
            if (Regex("^#[0-9a-fA-F]{6}$").matches(it)) it.uppercase() else fallbackHex
        }
        val description = json.optString("description").ifBlank { json.optString("notes") }
        return GarmentAiAnalysis(
            name = json.optString("name").trim().ifBlank { "Untitled Piece" }.take(80),
            category = GarmentCategory.fromCode(json.optString("category", "TOP")).name,
            subCategory = json.optString("subCategory").trim().ifBlank { "Apparel" }.take(50),
            materialComposition = json.optString("materialComposition").trim().take(180),
            colorName = json.optString("colorName").trim().ifBlank { "Neutral" }.take(40),
            colorHex = hex,
            warmthLevel = json.optInt("warmthLevel", 3).coerceIn(1, 5),
            isRainResistant = json.optBoolean("isRainResistant", false),
            isWindResistant = json.optBoolean("isWindResistant", false),
            occasions = stringList(json.optJSONArray("occasions"), ALLOWED_OCCASIONS).ifEmpty { listOf("Casual") },
            seasons = stringList(json.optJSONArray("seasons"), ALLOWED_SEASONS).ifEmpty { listOf("Spring", "Autumn") },
            notes = description.trim().take(300),
            rotationDegrees = 0
        )
    }

    // ------------------------------------------------------------------ orientation

    private val orientationSchema = JSONObject(
        """{"type": "OBJECT", "properties": {"upright": {"type": "STRING", "enum": ["A", "B", "C", "D"]}}, "required": ["upright"]}"""
    )

    /**
     * Degrees to rotate [photo] clockwise so the item is upright (0/90/180/270). Shows the model
     * the photo in all four orientations as labelled panels and asks which one is upright, which
     * is far more reliable than asking it to reason about rotation directly. Returns 0 on failure.
     */
    suspend fun detectUprightRotation(photo: Bitmap): Int {
        return try {
            val grid = ImageUtils.orientationGrid(photo)
            val prompt = "These 4 panels (A, B, C, D) show the SAME clothing photo rotated in different directions. " +
                "Which panel shows the item upright, the way it would appear in a shop photo? " +
                "Upright means: shirts, t-shirts, kurtas, jackets and dresses have the collar or neckline at " +
                "the top and the hem at the bottom; trousers, shorts and skirts have the waistband at the top " +
                "and the leg openings at the bottom; shoes stand on their soles. " +
                "Return JSON {\"upright\": \"A\" | \"B\" | \"C\" | \"D\"}."
            val raw = withFallback(textModels(), timeoutMs = 45_000L) { engine, model ->
                engine.generateJson(model, prompt, listOf(grid), orientationSchema, temperature = 0f)
            }
            when (extractJsonObject(raw).optString("upright").trim().uppercase()) {
                "B" -> 90
                "C" -> 180
                "D" -> 270
                else -> 0
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Orientation check failed: ${e.message}")
            0
        }
    }

    // ------------------------------------------------------------------ care label

    private val labelSchema = JSONObject(
        """
        {
          "type": "OBJECT",
          "properties": {
            "materialComposition": {"type": "STRING"},
            "careInstructions": {"type": "STRING"},
            "suggestedWarmthLevel": {"type": "INTEGER"},
            "isRainResistant": {"type": "BOOLEAN"},
            "isWindResistant": {"type": "BOOLEAN"},
            "recommendedSeasons": {"type": "ARRAY", "items": {"type": "STRING", "enum": ["Spring","Summer","Autumn","Winter"]}}
          },
          "required": ["materialComposition","careInstructions","suggestedWarmthLevel","isRainResistant","isWindResistant","recommendedSeasons"]
        }
        """.trimIndent()
    )

    suspend fun scanMaterialLabel(bitmap: Bitmap): Result<MaterialScanResult> = runCatchingAi {
        val prompt = """
            This is a clothing care or composition label (or a close-up of the fabric).
            Read the fibre composition exactly as printed, e.g. "80% Wool, 20% Polyamide". If there is no
            readable text, name the fabric you see instead. Summarise the care symbols/instructions in a short
            phrase, e.g. "Machine wash 30°C, do not tumble dry". Rate warmth 1 (very light) to 5 (very warm).
            Return only the JSON object.
        """.trimIndent()
        val raw = withFallback(textModels()) { engine, model ->
            engine.generateJson(model, prompt, listOf(ImageUtils.scaleDownBitmap(bitmap, 1280)), labelSchema)
        }
        val json = extractJsonObject(raw)
        MaterialScanResult(
            materialComposition = json.optString("materialComposition").trim().ifBlank { "Unknown fabric" }.take(180),
            careInstructions = json.optString("careInstructions").trim().take(200),
            suggestedWarmthLevel = json.optInt("suggestedWarmthLevel", 3).coerceIn(1, 5),
            isRainResistant = json.optBoolean("isRainResistant", false),
            isWindResistant = json.optBoolean("isWindResistant", false),
            recommendedSeasons = stringList(json.optJSONArray("recommendedSeasons"), ALLOWED_SEASONS)
                .ifEmpty { listOf("Spring", "Autumn") }
        )
    }

    // ------------------------------------------------------------------ studio product photo

    /**
     * Re-draws the garment as a clean e-commerce product shot on a plain light grey background.
     * With no photo, draws one from the item's details. Returns null if the image model fails.
     */
    suspend fun renderStudioPhoto(photo: Bitmap?, item: ClosetItem): Bitmap? {
        if (imageModelUnavailable != null) return null
        return try {
            val details = listOf(item.colorName, item.materialComposition, item.subCategory.ifBlank { item.name })
                .filter { it.isNotBlank() && !it.contains("Pending", true) && !it.contains("Analyzing", true) }
                .joinToString(", ")
            val prompt = if (photo != null) {
                "Turn the main clothing item in this photo into a clean e-commerce product photo. " +
                    "Show only that single item, front view, neatly shaped as if on an invisible mannequin " +
                    "(shoes as a side-angle pair, watches and accessories upright). Keep its exact colours, " +
                    "fabric texture, prints, logos, stitching and proportions; do not redesign it. " +
                    "Plain solid light grey (#D9D9D9) background, soft even studio light, no shadow on the background, " +
                    "no person, no hanger, no props, no text. Square image with the item centred and filling about 80% of the frame."
            } else {
                "A clean e-commerce product photo of a single ${item.name} ($details). Front view, as if on an " +
                    "invisible mannequin, plain solid light grey (#D9D9D9) background, soft studio light, no person, no props, no text. " +
                    "Square image with the item centred and filling about 80% of the frame."
            }
            val images = if (photo != null) {
                listOf(ImageUtils.flattenOnWhite(ImageUtils.scaleDownBitmap(photo, 1024)))
            } else {
                emptyList()
            }
            withFallback(listOf(settings.imageModel), timeoutMs = 90_000L) { engine, model ->
                engine.generateImage(model, prompt, images)
                    ?: throw GeminiException("No image returned.", retryable = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is GeminiException && e.notInPlan) imageModelUnavailable = e.message
            Log.w(TAG, "Studio photo render failed: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------ virtual try-on

    suspend fun generateVirtualTryOn(
        userReferencePhoto: Bitmap?,
        selectedItems: List<ClosetItem>,
        occasion: String,
        weatherSummary: String,
        fitNotes: String
    ): Result<Bitmap> = runCatchingAi {
        if (selectedItems.isEmpty()) throw IllegalStateException("Pick at least one item first.")
        imageModelUnavailable?.let { throw IllegalStateException(it) }
        val garmentSummary = selectedItems.joinToString("; ") { item ->
            val mat = if (item.materialComposition.isNotBlank()) " in ${item.materialComposition}" else ""
            "${item.name} (${item.colorName}$mat)"
        }
        val garmentBitmaps = selectedItems.mapNotNull { item ->
            ImageUtils.decodeBase64ToBitmap(item.imageBase64)?.let {
                ImageUtils.flattenOnWhite(ImageUtils.scaleDownBitmap(it, 768))
            }
        }.take(5)
        val person = userReferencePhoto?.let { ImageUtils.scaleDownBitmap(it, 1024) }
        val prompt = buildString {
            if (person != null) {
                append("The first image is a person. The other images are clothing items. ")
                append("Show this same person wearing all of these items together: $garmentSummary. ")
                append("Keep their face, body shape, pose and the lighting natural and unchanged; render each garment's colour, fabric and details faithfully. ")
            } else {
                append("A realistic full-body fashion photo of a model wearing exactly these items together: $garmentSummary. ")
                append("The attached images show the actual items; match them faithfully. Plain light studio background. ")
            }
            append("Occasion: $occasion. Weather: $weatherSummary.")
            if (fitNotes.isNotBlank()) append(" Fit preference: $fitNotes.")
        }
        val images = listOfNotNull(person) + garmentBitmaps
        try {
            withFallback(listOf(settings.imageModel), timeoutMs = 120_000L) { engine, model ->
                engine.generateImage(model, prompt, images)
                    ?: throw GeminiException("No image was returned.", retryable = true)
            }
        } catch (e: GeminiException) {
            if (e.notInPlan) imageModelUnavailable = e.message
            throw e
        }
    }

    // ------------------------------------------------------------------ outfit suggestions

    private val outfitsSchema = JSONObject(
        """
        {
          "type": "OBJECT",
          "properties": {
            "outfits": {
              "type": "ARRAY",
              "items": {
                "type": "OBJECT",
                "properties": {
                  "outfitName": {"type": "STRING"},
                  "itemIds": {"type": "ARRAY", "items": {"type": "STRING"}},
                  "occasion": {"type": "STRING"},
                  "weatherComfortScore": {"type": "INTEGER"},
                  "stylistRationale": {"type": "STRING"},
                  "layeringTip": {"type": "STRING"}
                },
                "required": ["outfitName","itemIds","occasion","weatherComfortScore","stylistRationale","layeringTip"]
              }
            }
          },
          "required": ["outfits"]
        }
        """.trimIndent()
    )

    suspend fun generateWeatherOutfits(
        closetItems: List<ClosetItem>,
        weather: DailyWeatherForecast,
        cityName: String,
        occasion: String
    ): Result<List<AiOutfitSuggestion>> = runCatchingAi {
        if (closetItems.isEmpty()) throw IllegalStateException("Your closet is empty. Add some items first.")
        val inventory = closetItems.joinToString("\n") { item ->
            "- id=${item.id} | ${item.name} | ${item.category}/${item.subCategory} | ${item.colorName} | " +
                "${item.materialComposition.ifBlank { "fabric unknown" }} | warmth ${item.warmthLevel}/5" +
                (if (item.isRainResistant) " | rainproof" else "") +
                (if (item.isWindResistant) " | windproof" else "") +
                " | ${item.occasions.joinToString(",")}"
        }
        val prompt = """
            Put together 2 or 3 outfits for $occasion using ONLY the items listed below.

            Weather in $cityName on ${weather.dayLabel} (${weather.dateIso}): ${weather.conditionLabel},
            ${weather.tempMinC}–${weather.tempMaxC}°C, feels like ${weather.feelsLikeC}°C,
            ${weather.precipitationProbability}% chance of rain, wind ${weather.windSpeedKmh} km/h.
            Ideal overall warmth: ${weather.recommendedWarmthLevel}/5.

            Items:
            $inventory

            Rules:
            1. itemIds must be ids copied exactly from the list.
            2. Each outfit is a TOP + BOTTOM (or one ONE_PIECE), plus FOOTWEAR if available, plus OUTERWEAR
               when it is cold, wet or windy, plus at most one ACCESSORY.
            3. Make the outfits different from each other.
            4. outfitName: 2–4 plain words. stylistRationale: one sentence on why it works for this weather.
               layeringTip: one short practical tip. Plain, friendly tone.
            Return only the JSON object.
        """.trimIndent()
        val raw = withFallback(textModels()) { engine, model ->
            engine.generateJson(model, prompt, emptyList(), outfitsSchema)
        }
        val outfits = extractJsonObject(raw).optJSONArray("outfits") ?: JSONArray()
        val validIds = closetItems.map { it.id }.toSet()
        val suggestions = (0 until outfits.length()).mapNotNull { i ->
            val o = outfits.optJSONObject(i) ?: return@mapNotNull null
            val ids = o.optJSONArray("itemIds")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it).trim() }.filter { it in validIds }.distinct()
            }.orEmpty()
            if (ids.isEmpty()) return@mapNotNull null
            AiOutfitSuggestion(
                outfitName = o.optString("outfitName").ifBlank { "Today's Look" },
                itemIds = ids,
                occasion = o.optString("occasion").ifBlank { occasion },
                // Scored locally from warmth / rain / wind so it matches the Studio's comfort meter.
                weatherComfortScore = evaluateOutfitComfort(closetItems.filter { it.id in ids }, weather).score,
                stylistRationale = o.optString("stylistRationale"),
                layeringTip = o.optString("layeringTip")
            )
        }
        if (suggestions.isEmpty()) {
            throw IllegalStateException("Couldn't build outfits from your closet. Try adding more types of items.")
        }
        suggestions
    }

    // ------------------------------------------------------------------ helpers

    private inline fun <T> runCatchingAi(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "AI call failed", e)
        Result.failure(IllegalStateException(friendlyMessage(e), e))
    }

    private fun friendlyMessage(e: Throwable): String {
        val msg = e.message?.takeIf { it.isNotBlank() && !it.contains("Something unexpected", true) }
        return msg ?: "The AI service is busy right now. Please try again."
    }

    private fun extractJsonObject(raw: String): JSONObject {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        val body = if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
        return JSONObject(body)
    }

    private fun stringList(arr: JSONArray?, allowed: List<String>): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val v = arr.optString(i).trim()
            allowed.firstOrNull { it.equals(v, ignoreCase = true) }
        }.distinct()
    }

    companion object {
        fun evaluateOutfitComfort(
            selectedItems: List<ClosetItem>,
            weather: DailyWeatherForecast?
        ): OutfitComfortEvaluation {
            if (selectedItems.isEmpty()) {
                return OutfitComfortEvaluation(
                    score = 0,
                    headline = "Pick pieces below to build a look",
                    detailTip = "Choose a top and bottom (or a one-piece), then outerwear and shoes."
                )
            }
            if (weather == null) {
                return OutfitComfortEvaluation(
                    score = 85,
                    headline = "${selectedItems.size} pieces",
                    detailTip = "Load the weather to check how comfortable this look will be."
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
                if (selectedItems.any { it.isRainResistant }) {
                    score += 5
                    tips.add("Rain gear included for the ${weather.precipitationProbability}% chance of rain.")
                } else {
                    score -= 18
                    tips.add("${weather.precipitationProbability}% chance of rain — add a water-resistant layer or boots.")
                }
            }

            if (weather.isWindy && selectedItems.none { it.isWindResistant }) {
                score -= 10
                tips.add("Windy (${weather.windSpeedKmh.toInt()} km/h) — a wind-blocking jacket would help.")
            }

            when {
                effectiveWarmth < targetWarmth - 0.8 -> tips.add("A bit light for ${weather.feelsLikeC}°C — add a layer.")
                effectiveWarmth > targetWarmth + 0.9 -> tips.add("Quite warm for ${weather.feelsLikeC}°C.")
                else -> tips.add("Good weight for ${weather.feelsLikeC}°C.")
            }

            val finalScore = score.coerceIn(35, 99)
            val headline = when {
                finalScore >= 88 -> "Great for ${weather.dayLabel} (${weather.feelsLikeC}°C)"
                finalScore >= 72 -> "Good fit for ${weather.feelsLikeC}°C · ${weather.conditionLabel}"
                else -> "Could use adjusting (${weather.feelsLikeC}°C)"
            }

            return OutfitComfortEvaluation(score = finalScore, headline = headline, detailTip = tips.joinToString(" "))
        }
    }
}
