package com.example.data.repository

import com.example.data.model.ClosetItem
import com.example.data.model.Outfit
import com.example.data.model.WearLog
import kotlinx.coroutines.flow.Flow

/** Where the closet is stored: Firestore (signed-in) or on the phone only. */
interface ClosetDataSource {
    fun observeClosetItems(userId: String): Flow<List<ClosetItem>>
    fun observeOutfits(userId: String): Flow<List<Outfit>>
    fun observeWearLogs(userId: String): Flow<List<WearLog>>

    suspend fun addClosetItem(item: ClosetItem): Result<String>
    suspend fun updateClosetItem(item: ClosetItem): Result<Unit>
    suspend fun toggleClosetItemFavorite(itemId: String, isFavorite: Boolean): Result<Unit>
    suspend fun deleteClosetItem(itemId: String): Result<Unit>
    suspend fun deleteClosetItems(itemIds: Collection<String>, outfits: List<Outfit>): Result<Unit>

    suspend fun addOutfit(outfit: Outfit): Result<String>
    suspend fun toggleOutfitFavorite(outfitId: String, isFavorite: Boolean): Result<Unit>
    suspend fun deleteOutfit(outfitId: String): Result<Unit>

    suspend fun logOutfitWear(
        dateIso: String,
        outfitId: String,
        outfitName: String,
        itemIds: List<String>,
        occasion: String,
        weatherTempC: Double,
        weatherCondition: String,
        notes: String = "",
        outfitExists: Boolean = outfitId.isNotBlank()
    ): Result<String>

    suspend fun deleteWearLog(
        logId: String,
        itemUpdates: Map<String, Pair<Long, Long>> = emptyMap(),
        outfitId: String = "",
        outfitWearCount: Long? = null
    ): Result<Unit>

    suspend fun deleteAllUserData(): Result<Int>
    suspend fun seedStarterCapsuleWardrobe(): Result<Int>
}
