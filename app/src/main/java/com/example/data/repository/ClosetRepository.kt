package com.example.data.repository

import android.content.Context
import com.example.data.settings.AppSettings
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

class ClosetRepository(private val db: FirebaseFirestore) : ClosetDataSource {

    constructor(context: Context) : this(firestoreFor(context))

    companion object {
        /** Firestore for the database id in Settings (or the bundled one). */
        private val emulatorWired = mutableSetOf<String>()

        fun firestoreFor(context: Context): FirebaseFirestore {
            val dbId = AppSettings.get(context).effectiveFirestoreDatabaseId()
            val db = if (dbId == "(default)") FirebaseFirestore.getInstance() else FirebaseFirestore.getInstance(dbId)
            // Local test mode: use the Firestore emulator (reached through `adb reverse tcp:8085 tcp:8085`).
            if (com.example.BuildConfig.USE_FIREBASE_EMULATORS) {
                synchronized(emulatorWired) {
                    if (emulatorWired.add(dbId)) db.useEmulator("127.0.0.1", 8085)
                }
            }
            return db
        }
    }

    // The auth of the same Firebase app as the database (the default app in the app; a named app in tests).
    private val auth get() = com.google.firebase.auth.FirebaseAuth.getInstance(db.app)

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    override fun observeClosetItems(userId: String): Flow<List<ClosetItem>> = flow {
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

    override fun observeOutfits(userId: String): Flow<List<Outfit>> = flow {
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

    override fun observeWearLogs(userId: String): Flow<List<WearLog>> = flow {
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

    override suspend fun addClosetItem(item: ClosetItem): Result<String> = runCatching {
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
            "imageBase64" to checkedImage(item.imageBase64),
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

    override suspend fun updateClosetItem(item: ClosetItem): Result<Unit> = runCatching {
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
            "imageBase64" to checkedImage(item.imageBase64),
            "wearCount" to item.wearCount.coerceAtLeast(0L),
            "lastWornEpochDay" to item.lastWornEpochDay.coerceAtLeast(0L),
            "isFavorite" to item.isFavorite,
            "updatedAt" to FieldValue.serverTimestamp(),
            *(if (item.lastWashedEpochDay > 0L) arrayOf<Pair<String, Any>>("lastWashedEpochDay" to item.lastWashedEpochDay)
              else arrayOf<Pair<String, Any>>())
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

    override suspend fun toggleClosetItemFavorite(itemId: String, isFavorite: Boolean): Result<Unit> = runCatching {
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

    override suspend fun deleteClosetItem(itemId: String): Result<Unit> = runCatching {
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

    override suspend fun addOutfit(outfit: Outfit): Result<String> = runCatching {
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

    override suspend fun toggleOutfitFavorite(outfitId: String, isFavorite: Boolean): Result<Unit> = runCatching {
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

    override suspend fun deleteOutfit(outfitId: String): Result<Unit> = runCatching {
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

    /**
     * Logs a worn outfit and bumps wear counts in ONE atomic batch, so the log and the counts
     * can't drift apart. [itemIds] must be items that still exist.
     */
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
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val ids = itemIds.distinct().take(12)
        require(ids.isNotEmpty()) { "Wear log must include at least one item" }
        val userRef = db.collection("users").document(uid)
        val docRef = userRef.collection("wearLogs").document()
        val epochDay = try {
            LocalDate.parse(dateIso).toEpochDay()
        } catch (e: Exception) {
            LocalDate.now().toEpochDay()
        }
        val batch = db.batch()
        batch.set(
            docRef,
            mapOf(
                "userId" to uid,
                "dateIso" to dateIso.trim().take(10),
                "outfitId" to outfitId.trim().take(128),
                "outfitName" to outfitName.trim().take(100).ifBlank { "Daily Look" },
                "itemIds" to ids,
                "occasion" to occasion.trim().take(60).ifBlank { "Casual" },
                "weatherTempC" to weatherTempC.coerceIn(-80.0, 70.0),
                "weatherCondition" to weatherCondition.trim().take(80),
                "notes" to notes.trim().take(400),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        for (itemId in ids) {
            batch.update(
                userRef.collection("closetItems").document(itemId),
                mapOf(
                    "wearCount" to FieldValue.increment(1L),
                    "lastWornEpochDay" to epochDay,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        if (outfitId.isNotBlank() && outfitExists) {
            batch.update(
                userRef.collection("outfits").document(outfitId),
                mapOf("wearCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        try {
            batch.commit().await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    /**
     * Deletes a wear log and rolls the counts back. [itemUpdates] maps each still-existing item to
     * its corrected (wearCount, lastWornEpochDay); [outfitWearCount] is the outfit's corrected count.
     */
    override suspend fun deleteWearLog(
        logId: String,
        itemUpdates: Map<String, Pair<Long, Long>>,
        outfitId: String,
        outfitWearCount: Long?
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val docRef = userRef.collection("wearLogs").document(logId)
        val batch = db.batch()
        batch.delete(docRef)
        for ((itemId, counts) in itemUpdates) {
            batch.update(
                userRef.collection("closetItems").document(itemId),
                mapOf(
                    "wearCount" to counts.first.coerceAtLeast(0L),
                    "lastWornEpochDay" to counts.second.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        if (outfitId.isNotBlank() && outfitWearCount != null) {
            batch.update(
                userRef.collection("outfits").document(outfitId),
                mapOf("wearCount" to outfitWearCount.coerceAtLeast(0L), "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    /**
     * Deletes closet items and fixes the outfits that used them: the items are removed from each
     * outfit, and outfits left empty are deleted. Wear history is kept.
     */
    override suspend fun deleteClosetItems(itemIds: Collection<String>, outfits: List<Outfit>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val removing = itemIds.toSet()
        val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()
        removing.forEach { id -> ops.add { b -> b.delete(userRef.collection("closetItems").document(id)) } }
        outfits.filter { o -> o.itemIds.any { it in removing } }.forEach { o ->
            val remaining = o.itemIds.filterNot { it in removing }
            val ref = userRef.collection("outfits").document(o.id)
            if (remaining.isEmpty()) {
                ops.add { b -> b.delete(ref) }
            } else {
                ops.add { b -> b.update(ref, mapOf("itemIds" to remaining, "updatedAt" to FieldValue.serverTimestamp())) }
            }
        }
        commitInChunks(ops, "users/$uid/closetItems")
    }

    /** Deletes every closet item, outfit and wear log for the signed-in user. */
    override suspend fun deleteAllUserData(): Result<Int> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        var total = 0
        val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()
        for (collection in listOf("closetItems", "outfits", "wearLogs")) {
            val snap = userRef.collection(collection).whereEqualTo("userId", uid).get().await()
            snap.documents.forEach { d -> ops.add { b -> b.delete(d.reference) }; total++ }
        }
        commitInChunks(ops, "users/$uid")
        total
    }

    private suspend fun commitInChunks(
        ops: List<(com.google.firebase.firestore.WriteBatch) -> Unit>,
        pathForErrors: String
    ) {
        try {
            ops.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { it(batch) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, pathForErrors)
            throw e
        }
    }

    private fun checkedImage(base64: String): String {
        require(base64.length <= 120_000) { "Photo is too large to save. Try a different photo." }
        return base64
    }

    override suspend fun seedStarterCapsuleWardrobe(): Result<Int> = runCatching {
        val uid = requireUserId()
        val starterItems = starterCapsuleItems(uid)
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
