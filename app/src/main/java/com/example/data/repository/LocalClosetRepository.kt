package com.example.data.repository

import android.content.Context
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.model.Outfit
import com.example.data.model.WearLog
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Keeps the whole closet in one JSON file on the phone. No account and no network needed.
 * Used when the app is built in "phone only" mode.
 */
class LocalClosetRepository(context: Context) : ClosetDataSource {

    private val file = File(context.applicationContext.filesDir, "closet_local.json")
    private val lock = Mutex()

    private val items = MutableStateFlow<List<ClosetItem>>(emptyList())
    private val outfits = MutableStateFlow<List<Outfit>>(emptyList())
    private val logs = MutableStateFlow<List<WearLog>>(emptyList())

    init {
        load()
    }

    override fun observeClosetItems(userId: String): Flow<List<ClosetItem>> = items
    override fun observeOutfits(userId: String): Flow<List<Outfit>> = outfits
    override fun observeWearLogs(userId: String): Flow<List<WearLog>> = logs

    // ------------------------------------------------------------------ closet items

    override suspend fun addClosetItem(item: ClosetItem): Result<String> = mutate {
        val id = item.id.ifBlank { newId() }
        val saved = clean(item).copy(id = id, userId = UID, createdAt = Timestamp.now())
        items.value = items.value.filter { it.id != id } + saved
        id
    }

    override suspend fun updateClosetItem(item: ClosetItem): Result<Unit> = mutate {
        require(item.id.isNotBlank()) { "Item ID must not be blank for update" }
        items.value = items.value.map { old ->
            if (old.id == item.id) clean(item).copy(id = old.id, userId = UID, createdAt = old.createdAt) else old
        }
    }

    override suspend fun toggleClosetItemFavorite(itemId: String, isFavorite: Boolean): Result<Unit> = mutate {
        items.value = items.value.map { if (it.id == itemId) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun deleteClosetItem(itemId: String): Result<Unit> = deleteClosetItems(listOf(itemId), outfits.value)

    override suspend fun deleteClosetItems(itemIds: Collection<String>, outfits: List<Outfit>): Result<Unit> = mutate {
        val removing = itemIds.toSet()
        items.value = items.value.filterNot { it.id in removing }
        this.outfits.value = this.outfits.value.mapNotNull { o ->
            if (o.itemIds.none { it in removing }) {
                o
            } else {
                val remaining = o.itemIds.filterNot { it in removing }
                if (remaining.isEmpty()) null else o.copy(itemIds = remaining)
            }
        }
    }

    // ------------------------------------------------------------------ outfits

    override suspend fun addOutfit(outfit: Outfit): Result<String> = mutate {
        require(outfit.itemIds.isNotEmpty()) { "Outfit must contain at least one closet item" }
        val id = outfit.id.ifBlank { newId() }
        val saved = outfit.copy(
            id = id,
            userId = UID,
            name = outfit.name.trim().take(100).ifBlank { "Curated Look" },
            itemIds = outfit.itemIds.take(12),
            weatherComfortScore = outfit.weatherComfortScore.coerceIn(0, 100),
            createdAt = Timestamp.now()
        )
        outfits.value = outfits.value.filter { it.id != id } + saved
        id
    }

    override suspend fun toggleOutfitFavorite(outfitId: String, isFavorite: Boolean): Result<Unit> = mutate {
        outfits.value = outfits.value.map { if (it.id == outfitId) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun deleteOutfit(outfitId: String): Result<Unit> = mutate {
        outfits.value = outfits.value.filterNot { it.id == outfitId }
    }

    // ------------------------------------------------------------------ wear logs

    override suspend fun logOutfitWear(
        dateIso: String,
        outfitId: String,
        outfitName: String,
        itemIds: List<String>,
        occasion: String,
        weatherTempC: Double,
        weatherCondition: String,
        notes: String,
        outfitExists: Boolean
    ): Result<String> = mutate {
        val ids = itemIds.distinct().take(12)
        require(ids.isNotEmpty()) { "Wear log must include at least one item" }
        val epochDay = try {
            LocalDate.parse(dateIso).toEpochDay()
        } catch (e: Exception) {
            LocalDate.now().toEpochDay()
        }
        val id = newId()
        logs.value = logs.value + WearLog(
            id = id,
            userId = UID,
            dateIso = dateIso.trim().take(10),
            outfitId = outfitId.trim().take(128),
            outfitName = outfitName.trim().take(100).ifBlank { "Daily Look" },
            itemIds = ids,
            occasion = occasion.trim().take(60).ifBlank { "Casual" },
            weatherTempC = weatherTempC.coerceIn(-80.0, 70.0),
            weatherCondition = weatherCondition.trim().take(80),
            notes = notes.trim().take(400),
            createdAt = Timestamp.now()
        )
        items.value = items.value.map {
            if (it.id in ids) it.copy(wearCount = it.wearCount + 1, lastWornEpochDay = epochDay) else it
        }
        if (outfitId.isNotBlank() && outfitExists) {
            outfits.value = outfits.value.map { if (it.id == outfitId) it.copy(wearCount = it.wearCount + 1) else it }
        }
        id
    }

    override suspend fun deleteWearLog(
        logId: String,
        itemUpdates: Map<String, Pair<Long, Long>>,
        outfitId: String,
        outfitWearCount: Long?
    ): Result<Unit> = mutate {
        logs.value = logs.value.filterNot { it.id == logId }
        items.value = items.value.map { item ->
            val fix = itemUpdates[item.id]
            if (fix != null) item.copy(wearCount = fix.first.coerceAtLeast(0L), lastWornEpochDay = fix.second.coerceAtLeast(0L)) else item
        }
        if (outfitId.isNotBlank() && outfitWearCount != null) {
            outfits.value = outfits.value.map { if (it.id == outfitId) it.copy(wearCount = outfitWearCount.coerceAtLeast(0L)) else it }
        }
    }

    // ------------------------------------------------------------------ bulk

    override suspend fun deleteAllUserData(): Result<Int> = mutate {
        val total = items.value.size + outfits.value.size + logs.value.size
        items.value = emptyList()
        outfits.value = emptyList()
        logs.value = emptyList()
        total
    }

    override suspend fun seedStarterCapsuleWardrobe(): Result<Int> = runCatching {
        val ids = starterCapsuleItems(UID).map { addClosetItem(it).getOrThrow() }
        if (ids.size >= 8) {
            addOutfit(
                Outfit(
                    userId = UID,
                    name = "Breezy City Look",
                    itemIds = listOf(ids[0], ids[2], ids[4], ids[7]),
                    occasion = "Work",
                    targetWeatherSummary = "14\u00B0C \u00B7 Crisp & Breezy",
                    weatherComfortScore = 90,
                    stylistNotes = "Trench over a poplin shirt and wool trousers keeps the wind out.",
                    isFavorite = true
                )
            )
        }
        ids.size
    }

    // ------------------------------------------------------------------ storage

    private fun newId() = UUID.randomUUID().toString().replace("-", "")

    private fun clean(i: ClosetItem) = i.copy(
        name = i.name.trim().take(100).ifBlank { "Untitled Piece" },
        subCategory = i.subCategory.trim().take(60),
        materialComposition = i.materialComposition.trim().take(200),
        colorName = i.colorName.trim().take(50).ifBlank { "Neutral" },
        colorHex = i.colorHex.trim().take(12).ifBlank { "#C85A32" },
        warmthLevel = i.warmthLevel.coerceIn(1, 5),
        occasions = i.occasions.take(10),
        seasons = i.seasons.take(6),
        notes = i.notes.trim().take(500)
    )

    private suspend fun <T> mutate(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val result = block()
                save()
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun save() {
        val root = JSONObject()
            .put("version", 1)
            .put("items", JSONArray(items.value.map(::itemJson)))
            .put("outfits", JSONArray(outfits.value.map(::outfitJson)))
            .put("wearLogs", JSONArray(logs.value.map(::logJson)))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            items.value = root.optJSONArray("items").objects().map(::itemFrom)
            outfits.value = root.optJSONArray("outfits").objects().map(::outfitFrom)
            logs.value = root.optJSONArray("wearLogs").objects().map(::logFrom)
        } catch (e: Exception) {
            // Unreadable file: keep a copy for recovery and start empty rather than crash.
            file.renameTo(File(file.parentFile, "closet_local.broken.json"))
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun ts(seconds: Long) = Timestamp(seconds, 0)

    private fun itemJson(i: ClosetItem) = JSONObject()
        .put("id", i.id).put("name", i.name).put("category", i.category).put("subCategory", i.subCategory)
        .put("materialComposition", i.materialComposition).put("colorName", i.colorName).put("colorHex", i.colorHex)
        .put("warmthLevel", i.warmthLevel).put("isRainResistant", i.isRainResistant)
        .put("isWindResistant", i.isWindResistant).put("occasions", JSONArray(i.occasions))
        .put("seasons", JSONArray(i.seasons)).put("notes", i.notes).put("imageBase64", i.imageBase64)
        .put("wearCount", i.wearCount).put("lastWornEpochDay", i.lastWornEpochDay).put("lastWashedEpochDay", i.lastWashedEpochDay)
        .put("isFavorite", i.isFavorite).put("createdAt", i.createdAt?.seconds ?: 0L)

    private fun itemFrom(o: JSONObject) = ClosetItem(
        id = o.optString("id"), userId = UID, name = o.optString("name"),
        category = o.optString("category", GarmentCategory.TOP.name), subCategory = o.optString("subCategory"),
        materialComposition = o.optString("materialComposition"), colorName = o.optString("colorName", "Neutral"),
        colorHex = o.optString("colorHex", "#8A8F98"), warmthLevel = o.optInt("warmthLevel", 3),
        isRainResistant = o.optBoolean("isRainResistant"), isWindResistant = o.optBoolean("isWindResistant"),
        occasions = o.optJSONArray("occasions").strings(), seasons = o.optJSONArray("seasons").strings(),
        notes = o.optString("notes"), imageBase64 = o.optString("imageBase64"),
        wearCount = o.optLong("wearCount"), lastWornEpochDay = o.optLong("lastWornEpochDay"),
        lastWashedEpochDay = o.optLong("lastWashedEpochDay"),
        isFavorite = o.optBoolean("isFavorite"), createdAt = ts(o.optLong("createdAt"))
    )

    private fun outfitJson(o: Outfit) = JSONObject()
        .put("id", o.id).put("name", o.name).put("itemIds", JSONArray(o.itemIds)).put("occasion", o.occasion)
        .put("targetWeatherSummary", o.targetWeatherSummary).put("weatherComfortScore", o.weatherComfortScore)
        .put("stylistNotes", o.stylistNotes).put("isAiGenerated", o.isAiGenerated)
        .put("isFavorite", o.isFavorite).put("wearCount", o.wearCount).put("createdAt", o.createdAt?.seconds ?: 0L)

    private fun outfitFrom(o: JSONObject) = Outfit(
        id = o.optString("id"), userId = UID, name = o.optString("name"), itemIds = o.optJSONArray("itemIds").strings(),
        occasion = o.optString("occasion", "Casual"), targetWeatherSummary = o.optString("targetWeatherSummary"),
        weatherComfortScore = o.optInt("weatherComfortScore", 85), stylistNotes = o.optString("stylistNotes"),
        isAiGenerated = o.optBoolean("isAiGenerated"), isFavorite = o.optBoolean("isFavorite"),
        wearCount = o.optLong("wearCount"), createdAt = ts(o.optLong("createdAt"))
    )

    private fun logJson(l: WearLog) = JSONObject()
        .put("id", l.id).put("dateIso", l.dateIso).put("outfitId", l.outfitId).put("outfitName", l.outfitName)
        .put("itemIds", JSONArray(l.itemIds)).put("occasion", l.occasion).put("weatherTempC", l.weatherTempC)
        .put("weatherCondition", l.weatherCondition).put("notes", l.notes).put("createdAt", l.createdAt?.seconds ?: 0L)

    private fun logFrom(o: JSONObject) = WearLog(
        id = o.optString("id"), userId = UID, dateIso = o.optString("dateIso"), outfitId = o.optString("outfitId"),
        outfitName = o.optString("outfitName"), itemIds = o.optJSONArray("itemIds").strings(),
        occasion = o.optString("occasion", "Casual"), weatherTempC = o.optDouble("weatherTempC", 20.0),
        weatherCondition = o.optString("weatherCondition", "Clear"), notes = o.optString("notes"),
        createdAt = ts(o.optLong("createdAt"))
    )

    private companion object {
        const val UID = "local"
    }
}
