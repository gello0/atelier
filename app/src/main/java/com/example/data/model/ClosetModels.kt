package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class GarmentCategory(val label: String, val shortLabel: String) {
    TOP("Tops & Knitwear", "Tops"),
    BOTTOM("Trousers & Skirts", "Bottoms"),
    OUTERWEAR("Coats & Jackets", "Outerwear"),
    ONE_PIECE("Dresses & Jumpsuits", "One-Piece"),
    FOOTWEAR("Shoes & Boots", "Footwear"),
    ACCESSORY("Bags & Accessories", "Accessories");

    companion object {
        fun fromCode(code: String): GarmentCategory {
            return entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: TOP
        }
    }
}

@IgnoreExtraProperties
data class ClosetItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = GarmentCategory.TOP.name,
    val subCategory: String = "",
    val materialComposition: String = "",
    val colorName: String = "Ivory",
    val colorHex: String = "#F5F0E6",
    val warmthLevel: Int = 3,
    @get:PropertyName("isRainResistant")
    @set:PropertyName("isRainResistant")
    var isRainResistant: Boolean = false,
    @get:PropertyName("isWindResistant")
    @set:PropertyName("isWindResistant")
    var isWindResistant: Boolean = false,
    val occasions: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    val notes: String = "",
    val imageBase64: String = "",
    val wearCount: Long = 0L,
    val lastWornEpochDay: Long = 0L,
    @get:PropertyName("isFavorite")
    @set:PropertyName("isFavorite")
    var isFavorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): GarmentCategory = GarmentCategory.fromCode(category)

    fun isPendingAiAnalysis(): Boolean {
        if (imageBase64.isBlank()) return false
        return subCategory.isBlank() ||
            subCategory.contains("Pending", ignoreCase = true) ||
            subCategory.contains("Analyzing", ignoreCase = true) ||
            materialComposition.isBlank() ||
            materialComposition.contains("Pending", ignoreCase = true) ||
            materialComposition.contains("Analyzing", ignoreCase = true) ||
            name.startsWith("Uploaded ", ignoreCase = true) ||
            name.startsWith("Scanning ", ignoreCase = true)
    }

    fun isAiAnalyzed(): Boolean = imageBase64.isNotBlank() && !isPendingAiAnalysis()
}

@IgnoreExtraProperties
data class Outfit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val itemIds: List<String> = emptyList(),
    val occasion: String = "Casual",
    val targetWeatherSummary: String = "",
    val weatherComfortScore: Int = 85,
    val stylistNotes: String = "",
    @get:PropertyName("isAiGenerated")
    @set:PropertyName("isAiGenerated")
    var isAiGenerated: Boolean = false,
    @get:PropertyName("isFavorite")
    @set:PropertyName("isFavorite")
    var isFavorite: Boolean = false,
    val wearCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WearLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateIso: String = "",
    val outfitId: String = "",
    val outfitName: String = "",
    val itemIds: List<String> = emptyList(),
    val occasion: String = "Casual",
    val weatherTempC: Double = 20.0,
    val weatherCondition: String = "Clear",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
