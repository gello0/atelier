package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.model.Outfit
import com.example.data.model.WearLog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class ClosetRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeClosetItems(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<ClosetItem>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid.orEmpty() }
        val path = "users/$targetUid/closetItems"
        emitAll(
            db.collection("users").document(targetUid).collection("closetItems")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClosetItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeOutfits(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<Outfit>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid.orEmpty() }
        val path = "users/$targetUid/outfits"
        emitAll(
            db.collection("users").document(targetUid).collection("outfits")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Outfit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeWearLogs(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<WearLog>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid.orEmpty() }
        val path = "users/$targetUid/wearLogs"
        emitAll(
            db.collection("users").document(targetUid).collection("wearLogs")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WearLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getClosetItemById(targetUserId: String, itemId: String): Result<ClosetItem?> = runCatching {
        val docRef = db.collection("users").document(targetUserId)
            .collection("closetItems").document(itemId)
        try {
            val snap = docRef.get().await()
            snap.toObject(ClosetItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun addClosetItem(item: ClosetItem): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("closetItems")
        val docRef = if (item.id.isNotBlank()) colRef.document(item.id) else colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "name" to item.name.trim().take(100).ifBlank { "Untitled Piece" },
            "category" to item.category,
            "subCategory" to item.subCategory.trim().take(60),
            "materialComposition" to item.materialComposition.trim().take(200),
            "colorName" to item.colorName.trim().take(50).ifBlank { "Neutral" },
            "colorHex" to item.colorHex.trim().take(12).ifBlank { "#C85A32" },
            "warmthLevel" to item.warmthLevel.coerceIn(1, 5),
            "isRainResistant" to item.isRainResistant,
            "isWindResistant" to item.isWindResistant,
            "occasions" to item.occasions.take(10),
            "seasons" to item.seasons.take(6),
            "notes" to item.notes.trim().take(500),
            "imageBase64" to item.imageBase64.take(120000),
            "wearCount" to item.wearCount.coerceAtLeast(0L),
            "lastWornEpochDay" to item.lastWornEpochDay.coerceAtLeast(0L),
            "isFavorite" to item.isFavorite,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateClosetItem(item: ClosetItem): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Item ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("closetItems").document(item.id)
        val updates = mapOf(
            "name" to item.name.trim().take(100).ifBlank { "Untitled Piece" },
            "category" to item.category,
            "subCategory" to item.subCategory.trim().take(60),
            "materialComposition" to item.materialComposition.trim().take(200),
            "colorName" to item.colorName.trim().take(50).ifBlank { "Neutral" },
            "colorHex" to item.colorHex.trim().take(12).ifBlank { "#C85A32" },
            "warmthLevel" to item.warmthLevel.coerceIn(1, 5),
            "isRainResistant" to item.isRainResistant,
            "isWindResistant" to item.isWindResistant,
            "occasions" to item.occasions.take(10),
            "seasons" to item.seasons.take(6),
            "notes" to item.notes.trim().take(500),
            "imageBase64" to item.imageBase64.take(120000),
            "wearCount" to item.wearCount.coerceAtLeast(0L),
            "lastWornEpochDay" to item.lastWornEpochDay.coerceAtLeast(0L),
            "isFavorite" to item.isFavorite,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleClosetItemFavorite(itemId: String, isFavorite: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("closetItems").document(itemId)
        try {
            docRef.update(
                mapOf(
                    "isFavorite" to isFavorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteClosetItem(itemId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("closetItems").document(itemId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun addOutfit(outfit: Outfit): Result<String> = runCatching {
        val uid = requireUserId()
        require(outfit.itemIds.isNotEmpty()) { "Outfit must contain at least one closet item" }
        val colRef = db.collection("users").document(uid).collection("outfits")
        val docRef = if (outfit.id.isNotBlank()) colRef.document(outfit.id) else colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "name" to outfit.name.trim().take(100).ifBlank { "Curated Look" },
            "itemIds" to outfit.itemIds.take(12),
            "occasion" to outfit.occasion.trim().take(60).ifBlank { "Casual" },
            "targetWeatherSummary" to outfit.targetWeatherSummary.trim().take(120),
            "weatherComfortScore" to outfit.weatherComfortScore.coerceIn(0, 100),
            "stylistNotes" to outfit.stylistNotes.trim().take(600),
            "isAiGenerated" to outfit.isAiGenerated,
            "isFavorite" to outfit.isFavorite,
            "wearCount" to outfit.wearCount.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleOutfitFavorite(outfitId: String, isFavorite: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("outfits").document(outfitId)
        try {
            docRef.update(
                mapOf(
                    "isFavorite" to isFavorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteOutfit(outfitId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("outfits").document(outfitId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun logOutfitWear(
        dateIso: String,
        outfitId: String,
        outfitName: String,
        itemIds: List<String>,
        occasion: String,
        weatherTempC: Double,
        weatherCondition: String,
        notes: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(itemIds.isNotEmpty()) { "Wear log must include at least one item" }
        val colRef = db.collection("users").document(uid).collection("wearLogs")
        val docRef = colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "dateIso" to dateIso.trim().take(10),
            "outfitId" to outfitId.trim().take(128),
            "outfitName" to outfitName.trim().take(100).ifBlank { "Daily Look" },
            "itemIds" to itemIds.take(12),
            "occasion" to occasion.trim().take(60).ifBlank { "Casual" },
            "weatherTempC" to weatherTempC.coerceIn(-80.0, 70.0),
            "weatherCondition" to weatherCondition.trim().take(80),
            "notes" to notes.trim().take(400),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            val epochDay = try {
                LocalDate.parse(dateIso).toEpochDay()
            } catch (e: Exception) {
                LocalDate.now().toEpochDay()
            }
            for (itemId in itemIds.take(12)) {
                val itemRef = db.collection("users").document(uid).collection("closetItems").document(itemId)
                try {
                    itemRef.update(
                        mapOf(
                            "wearCount" to FieldValue.increment(1L),
                            "lastWornEpochDay" to epochDay,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (ignored: Exception) {
                }
            }
            if (outfitId.isNotBlank()) {
                val outfitRef = db.collection("users").document(uid).collection("outfits").document(outfitId)
                try {
                    outfitRef.update(
                        mapOf(
                            "wearCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (ignored: Exception) {
                }
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteWearLog(logId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("wearLogs").document(logId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun seedStarterCapsuleWardrobe(): Result<Int> = runCatching {
        val uid = requireUserId()
        val starterItems = listOf(
            ClosetItem(
                userId = uid,
                name = "Sand Gabardine Trench Coat",
                category = GarmentCategory.OUTERWEAR.name,
                subCategory = "Trench Coat",
                materialComposition = "100% Water-Repellent Cotton Gabardine",
                colorName = "Warm Sand",
                colorHex = "#D2B48C",
                warmthLevel = 4,
                isRainResistant = true,
                isWindResistant = true,
                occasions = listOf("Work", "Casual", "Evening"),
                seasons = listOf("Spring", "Autumn", "Winter"),
                notes = "Storm-flap double-breasted cotton gabardine, water-repellent finish.",
                isFavorite = true
            ),
            ClosetItem(
                userId = uid,
                name = "Espresso Merino Rollneck",
                category = GarmentCategory.TOP.name,
                subCategory = "Knit Sweater",
                materialComposition = "100% Extra-Fine Merino Wool",
                colorName = "Espresso",
                colorHex = "#3D2B1F",
                warmthLevel = 4,
                isRainResistant = false,
                isWindResistant = false,
                occasions = listOf("Work", "Casual", "Evening"),
                seasons = listOf("Autumn", "Winter"),
                notes = "Fine-gauge extra-fine merino wool, ideal base or mid-layer."
            ),
            ClosetItem(
                userId = uid,
                name = "Crisp Ivory Poplin Shirt",
                category = GarmentCategory.TOP.name,
                subCategory = "Button-Down Shirt",
                materialComposition = "100% Organic Cotton Poplin",
                colorName = "Ivory",
                colorHex = "#F5F0E6",
                warmthLevel = 2,
                isRainResistant = false,
                isWindResistant = false,
                occasions = listOf("Work", "Casual", "Formal"),
                seasons = listOf("Spring", "Summer", "Autumn", "Winter"),
                notes = "Organic cotton poplin with relaxed architectural cuff.",
                isFavorite = true
            ),
            ClosetItem(
                userId = uid,
                name = "Sage Linen Relaxed Tee",
                category = GarmentCategory.TOP.name,
                subCategory = "T-Shirt",
                materialComposition = "100% European Flax Linen",
                colorName = "Sage Olive",
                colorHex = "#6B8E78",
                warmthLevel = 1,
                isRainResistant = false,
                isWindResistant = false,
                occasions = listOf("Casual", "Active", "Travel"),
                seasons = listOf("Spring", "Summer"),
                notes = "Breathable high-twist linen jersey for warm humid days."
            ),
            ClosetItem(
                userId = uid,
                name = "Charcoal Pleated Wool Trousers",
                category = GarmentCategory.BOTTOM.name,
                subCategory = "Tailored Trousers",
                materialComposition = "98% Virgin Wool, 2% Elastane",
                colorName = "Charcoal",
                colorHex = "#36393B",
                warmthLevel = 3,
                isRainResistant = false,
                isWindResistant = true,
                occasions = listOf("Work", "Evening", "Formal"),
                seasons = listOf("Spring", "Autumn", "Winter"),
                notes = "Drape-weight virgin wool with double front pleats.",
                isFavorite = true
            ),
            ClosetItem(
                userId = uid,
                name = "Ecru Selvedge Straight Denim",
                category = GarmentCategory.BOTTOM.name,
                subCategory = "Jeans",
                materialComposition = "100% Cotton 13oz Selvedge Denim",
                colorName = "Ecru Cream",
                colorHex = "#EAE3D2",
                warmthLevel = 3,
                isRainResistant = false,
                isWindResistant = true,
                occasions = listOf("Casual", "Travel"),
                seasons = listOf("Spring", "Summer", "Autumn"),
                notes = "13oz Japanese selvedge denim."
            ),
            ClosetItem(
                userId = uid,
                name = "Terracotta Silk Midi Slip Dress",
                category = GarmentCategory.ONE_PIECE.name,
                subCategory = "Midi Dress",
                materialComposition = "100% Mulberry Silk Crepe",
                colorName = "Terracotta",
                colorHex = "#C85A32",
                warmthLevel = 2,
                isRainResistant = false,
                isWindResistant = false,
                occasions = listOf("Evening", "Casual", "Formal"),
                seasons = listOf("Spring", "Summer", "Autumn"),
                notes = "Bias-cut matte silk; layer under knitwear or blazer in cooler weather."
            ),
            ClosetItem(
                userId = uid,
                name = "Obsidian Weatherproof Chelsea Boots",
                category = GarmentCategory.FOOTWEAR.name,
                subCategory = "Leather Boots",
                materialComposition = "Treated Full-Grain Calfskin & Rubber Sole",
                colorName = "Obsidian Black",
                colorHex = "#1C1917",
                warmthLevel = 4,
                isRainResistant = true,
                isWindResistant = true,
                occasions = listOf("Work", "Casual", "Evening", "Travel"),
                seasons = listOf("Autumn", "Winter", "Spring"),
                notes = "Storm-welted calfskin with lugged rubber sole for wet pavement.",
                isFavorite = true
            ),
            ClosetItem(
                userId = uid,
                name = "Warm Tan Suede Penny Loafers",
                category = GarmentCategory.FOOTWEAR.name,
                subCategory = "Loafers",
                materialComposition = "100% Unlined Italian Calf Suede",
                colorName = "Cognac Tan",
                colorHex = "#9A6344",
                warmthLevel = 2,
                isRainResistant = false,
                isWindResistant = false,
                occasions = listOf("Work", "Casual", "Evening"),
                seasons = listOf("Spring", "Summer", "Autumn"),
                notes = "Unlined Italian suede, best for dry mild or warm days."
            ),
            ClosetItem(
                userId = uid,
                name = "Oatmeal Cashmere Wrap Scarf",
                category = GarmentCategory.ACCESSORY.name,
                subCategory = "Scarf",
                materialComposition = "100% Scottish Brushed Cashmere",
                colorName = "Oatmeal",
                colorHex = "#D8CFC2",
                warmthLevel = 5,
                isRainResistant = false,
                isWindResistant = true,
                occasions = listOf("Work", "Casual", "Travel", "Evening"),
                seasons = listOf("Autumn", "Winter"),
                notes = "Brushed Scottish cashmere blanket scarf."
            )
        )
        val createdIds = mutableListOf<String>()
        for (item in starterItems) {
            val id = addClosetItem(item).getOrThrow()
            createdIds.add(id)
        }
        if (createdIds.size >= 4) {
            addOutfit(
                Outfit(
                    userId = uid,
                    name = "Breezy City Atelier Look",
                    itemIds = listOf(createdIds[0], createdIds[2], createdIds[4], createdIds[7]),
                    occasion = "Work",
                    targetWeatherSummary = "14°C · Crisp & Breezy",
                    weatherComfortScore = 94,
                    stylistNotes = "Architectural trench layered over ivory poplin and pleated charcoal wool keeps wind chill out while staying breathable indoors.",
                    isAiGenerated = true,
                    isFavorite = true
                )
            )
        }
        createdIds.size
    }
}
