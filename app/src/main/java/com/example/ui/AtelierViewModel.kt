package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiOutfitSuggestion
import com.example.data.ai.AiStylistService
import com.example.data.ai.GarmentAiAnalysis
import com.example.data.ai.MaterialScanResult
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.model.Outfit
import com.example.data.model.WearLog
import com.example.data.repository.ClosetRepository
import com.example.data.weather.CityLocation
import com.example.data.weather.DailyWeatherForecast
import com.example.data.weather.PRESET_CITIES
import com.example.data.weather.WeatherRepository
import com.example.data.weather.WeatherSnapshot
import com.example.util.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "AtelierViewModel"

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface AiTaskState<out T> {
    data object Idle : AiTaskState<Nothing>
    data object Loading : AiTaskState<Nothing>
    data class Success<T>(val data: T) : AiTaskState<T>
    data class Error(val message: String) : AiTaskState<Nothing>
}

data class CanvasSelectionState(
    val useOnePieceMode: Boolean = false,
    val selectedTopId: String? = null,
    val selectedBottomId: String? = null,
    val selectedOnePieceId: String? = null,
    val selectedOuterwearId: String? = null,
    val selectedFootwearId: String? = null,
    val selectedAccessoryId: String? = null,
    val outfitTitle: String = "",
    val selectedOccasion: String = "Casual"
)

class AtelierViewModel(
    private val closetRepository: ClosetRepository,
    private val weatherRepository: WeatherRepository,
    private val aiStylistService: AiStylistService,
    private val currentUserId: String
) : ViewModel() {

    val closetItemsState: StateFlow<DataUiState<List<ClosetItem>>> =
        closetRepository.observeClosetItems(currentUserId)
            .map<List<ClosetItem>, DataUiState<List<ClosetItem>>> { list ->
                DataUiState.Success(
                    list.sortedByDescending { it.createdAt?.seconds ?: 0L }
                )
            }
            .catch { error ->
                Log.w(TAG, "Error observing closetItems", error)
                emit(DataUiState.Error(error.localizedMessage ?: "Failed to load closet items"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    val outfitsState: StateFlow<DataUiState<List<Outfit>>> =
        closetRepository.observeOutfits(currentUserId)
            .map<List<Outfit>, DataUiState<List<Outfit>>> { list ->
                DataUiState.Success(
                    list.sortedByDescending { it.createdAt?.seconds ?: 0L }
                )
            }
            .catch { error ->
                Log.w(TAG, "Error observing outfits", error)
                emit(DataUiState.Error(error.localizedMessage ?: "Failed to load saved outfits"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    val wearLogsState: StateFlow<DataUiState<List<WearLog>>> =
        closetRepository.observeWearLogs(currentUserId)
            .map<List<WearLog>, DataUiState<List<WearLog>>> { list ->
                DataUiState.Success(
                    list.sortedByDescending { it.dateIso }
                )
            }
            .catch { error ->
                Log.w(TAG, "Error observing wearLogs", error)
                emit(DataUiState.Error(error.localizedMessage ?: "Failed to load wear logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    private val _weatherState = MutableStateFlow<DataUiState<WeatherSnapshot>>(DataUiState.Loading)
    val weatherState: StateFlow<DataUiState<WeatherSnapshot>> = _weatherState.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(0)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _useFahrenheit = MutableStateFlow(false)
    val useFahrenheit: StateFlow<Boolean> = _useFahrenheit.asStateFlow()

    private val _citySearchResults = MutableStateFlow<List<CityLocation>>(emptyList())
    val citySearchResults: StateFlow<List<CityLocation>> = _citySearchResults.asStateFlow()

    private val _isSearchingCities = MutableStateFlow(false)
    val isSearchingCities: StateFlow<Boolean> = _isSearchingCities.asStateFlow()

    private val _selectedOccasion = MutableStateFlow("Casual")
    val selectedOccasion: StateFlow<String> = _selectedOccasion.asStateFlow()

    private val _aiOutfitState = MutableStateFlow<AiTaskState<List<AiOutfitSuggestion>>>(AiTaskState.Idle)
    val aiOutfitState: StateFlow<AiTaskState<List<AiOutfitSuggestion>>> = _aiOutfitState.asStateFlow()

    private val _photoTagState = MutableStateFlow<AiTaskState<GarmentAiAnalysis>>(AiTaskState.Idle)
    val photoTagState: StateFlow<AiTaskState<GarmentAiAnalysis>> = _photoTagState.asStateFlow()

    private val _materialScanState = MutableStateFlow<AiTaskState<MaterialScanResult>>(AiTaskState.Idle)
    val materialScanState: StateFlow<AiTaskState<MaterialScanResult>> = _materialScanState.asStateFlow()

    // Virtual Try-On (VTON) State
    private val _vtonUserPhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val vtonUserPhotoBitmap: StateFlow<Bitmap?> = _vtonUserPhotoBitmap.asStateFlow()

    private val _vtonFitNotes = MutableStateFlow("Relaxed tailored silhouette")
    val vtonFitNotes: StateFlow<String> = _vtonFitNotes.asStateFlow()

    private val _vtonResultState = MutableStateFlow<AiTaskState<Bitmap>>(AiTaskState.Idle)
    val vtonResultState: StateFlow<AiTaskState<Bitmap>> = _vtonResultState.asStateFlow()

    // AI Model & High-Demand Fallback Preferences (Settings Tab)
    private val _preferFastLiteModel = MutableStateFlow(false)
    val preferFastLiteModel: StateFlow<Boolean> = _preferFastLiteModel.asStateFlow()

    private val _autoFallbackOnHighDemand = MutableStateFlow(true)
    val autoFallbackOnHighDemand: StateFlow<Boolean> = _autoFallbackOnHighDemand.asStateFlow()

    // Tracks active background AI jobs and IDs of items currently being analyzed
    private val _backgroundAiJobsCount = MutableStateFlow(0)
    val backgroundAiJobsCount: StateFlow<Int> = _backgroundAiJobsCount.asStateFlow()

    private val _backgroundAiStatusText = MutableStateFlow<String?>(null)
    val backgroundAiStatusText: StateFlow<String?> = _backgroundAiStatusText.asStateFlow()

    private val _analyzingItemIds = MutableStateFlow<Set<String>>(emptySet())
    val analyzingItemIds: StateFlow<Set<String>> = _analyzingItemIds.asStateFlow()

    private val _canvasState = MutableStateFlow(CanvasSelectionState())
    val canvasState: StateFlow<CanvasSelectionState> = _canvasState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isSeedingCapsule = MutableStateFlow(false)
    val isSeedingCapsule: StateFlow<Boolean> = _isSeedingCapsule.asStateFlow()

    init {
        loadWeatherForCity(PRESET_CITIES.first())
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    fun showBanner(message: String) {
        _statusBannerMessage.value = message
    }

    fun toggleTemperatureUnit() {
        _useFahrenheit.value = !_useFahrenheit.value
    }

    fun setPreferFastLiteModel(preferLite: Boolean) {
        _preferFastLiteModel.value = preferLite
        aiStylistService.preferFastLiteModel = preferLite
    }

    fun setAutoFallbackOnHighDemand(enabled: Boolean) {
        _autoFallbackOnHighDemand.value = enabled
        aiStylistService.autoFallbackOnHighDemand = enabled
    }

    fun setVtonUserPhoto(bitmap: Bitmap?) {
        _vtonUserPhotoBitmap.value = bitmap
        if (bitmap != null) {
            showBanner("Updated your Virtual Try-On reference photo!")
        }
    }

    fun setVtonFitNotes(notes: String) {
        _vtonFitNotes.value = notes
    }

    fun selectForecastDay(index: Int) {
        _selectedDayIndex.value = index
    }

    fun selectOccasion(occasion: String) {
        _selectedOccasion.value = occasion
    }

    fun activeWeatherForecast(): DailyWeatherForecast? {
        val snapshot = (_weatherState.value as? DataUiState.Success)?.data ?: return null
        val idx = _selectedDayIndex.value.coerceIn(0, (snapshot.dailyForecasts.size - 1).coerceAtLeast(0))
        return snapshot.dailyForecasts.getOrNull(idx)
    }

    fun loadWeatherForCity(city: CityLocation) {
        viewModelScope.launch {
            _weatherState.value = DataUiState.Loading
            val result = weatherRepository.fetchWeatherForecast(city)
            result.fold(
                onSuccess = { snapshot ->
                    _selectedDayIndex.value = 0
                    _weatherState.value = DataUiState.Success(snapshot)
                },
                onFailure = { error ->
                    _weatherState.value = DataUiState.Error(
                        error.localizedMessage ?: "Unable to fetch live weather for ${city.name}."
                    )
                }
            )
        }
    }

    fun refreshFromDeviceGps(context: Context, showFailureBanner: Boolean = true) {
        viewModelScope.launch {
            val gpsCity = weatherRepository.resolveDeviceGpsLocation(context.applicationContext)
            if (gpsCity != null) {
                loadWeatherForCity(gpsCity)
            } else if (showFailureBanner) {
                loadWeatherForCity(PRESET_CITIES.first())
                showBanner("Current location unavailable; showing New York. Use City Search to pick any location.")
            }
        }
    }

    fun searchCities(query: String) {
        viewModelScope.launch {
            if (query.trim().length < 2) {
                _citySearchResults.value = emptyList()
                return@launch
            }
            _isSearchingCities.value = true
            val result = weatherRepository.searchCities(query)
            _isSearchingCities.value = false
            result.onSuccess { list ->
                _citySearchResults.value = list
            }
        }
    }

    fun generateAiDailyOutfits() {
        val closetItems = (closetItemsState.value as? DataUiState.Success)?.data.orEmpty()
        val snapshot = (_weatherState.value as? DataUiState.Success)?.data
        val activeDay = activeWeatherForecast()
        if (closetItems.isEmpty()) {
            _aiOutfitState.value = AiTaskState.Error("Your closet is empty. Add garments or load the starter capsule first.")
            return
        }
        if (snapshot == null || activeDay == null) {
            _aiOutfitState.value = AiTaskState.Error("Wait for the weather forecast to load before generating weather-matched looks.")
            return
        }

        viewModelScope.launch {
            _aiOutfitState.value = AiTaskState.Loading
            _backgroundAiJobsCount.value += 1
            _backgroundAiStatusText.value = "Gemini is curating ${_selectedOccasion.value} outfits in the background..."
            val result = aiStylistService.generateWeatherOutfits(
                closetItems = closetItems,
                weather = activeDay,
                cityName = snapshot.location.name,
                occasion = _selectedOccasion.value
            )
            _backgroundAiJobsCount.value = (_backgroundAiJobsCount.value - 1).coerceAtLeast(0)
            _backgroundAiStatusText.value = if (_backgroundAiJobsCount.value > 0) "Running background AI tasks..." else null
            result.fold(
                onSuccess = { suggestions ->
                    _aiOutfitState.value = AiTaskState.Success(suggestions)
                    showBanner("AI Stylist finished curating ${suggestions.size} looks for ${activeDay.dayLabel}!")
                },
                onFailure = { error ->
                    _aiOutfitState.value = AiTaskState.Error(
                        error.localizedMessage ?: "AI outfit curation failed."
                    )
                }
            )
        }
    }

    fun runVirtualTryOnForCanvas() {
        val allItems = (closetItemsState.value as? DataUiState.Success)?.data.orEmpty()
        val state = _canvasState.value
        val selectedIds = listOfNotNull(
            if (state.useOnePieceMode) state.selectedOnePieceId else state.selectedTopId,
            if (state.useOnePieceMode) null else state.selectedBottomId,
            state.selectedOuterwearId,
            state.selectedFootwearId,
            state.selectedAccessoryId
        ).distinct()

        val selectedItems = allItems.filter { it.id in selectedIds }
        if (selectedItems.isEmpty()) {
            _vtonResultState.value = AiTaskState.Error("Select at least one garment on the Studio canvas first.")
            return
        }

        val activeDay = activeWeatherForecast()
        val weatherSummary = if (activeDay != null) {
            "${activeDay.feelsLikeC.toInt()}°C, ${activeDay.conditionLabel}"
        } else {
            "Mild studio lighting"
        }

        viewModelScope.launch {
            _vtonResultState.value = AiTaskState.Loading
            _backgroundAiJobsCount.value += 1
            _backgroundAiStatusText.value = "Background AI: Synthesizing Virtual Try-On (VTON) lookbook image..."
            val result = aiStylistService.generateVirtualTryOn(
                userReferencePhoto = _vtonUserPhotoBitmap.value,
                selectedItems = selectedItems,
                occasion = state.selectedOccasion,
                weatherSummary = weatherSummary,
                fitNotes = _vtonFitNotes.value
            )
            _backgroundAiJobsCount.value = (_backgroundAiJobsCount.value - 1).coerceAtLeast(0)
            _backgroundAiStatusText.value = if (_backgroundAiJobsCount.value > 0) "Running background AI tasks..." else null
            result.fold(
                onSuccess = { bitmap ->
                    _vtonResultState.value = AiTaskState.Success(bitmap)
                    showBanner("Virtual Try-On image ready in the Studio!")
                },
                onFailure = { error ->
                    _vtonResultState.value = AiTaskState.Error(
                        error.localizedMessage ?: "Virtual Try-On image synthesis failed."
                    )
                }
            )
        }
    }

    /**
     * Runs combined multimodal analysis on the garment photo (plus optional care-label photo
     * and optional user hint name). Can be triggered or retried at any time in AddEditItemDialog.
     */
    fun analyzeGarmentPhoto(
        bitmap: Bitmap,
        materialLabelBitmap: Bitmap? = null,
        userHintName: String = ""
    ) {
        viewModelScope.launch {
            _photoTagState.value = AiTaskState.Loading
            val result = aiStylistService.analyzeGarmentPhoto(
                bitmap = bitmap,
                materialLabelBitmap = materialLabelBitmap,
                userHintName = userHintName
            )
            result.fold(
                onSuccess = { analysis ->
                    _photoTagState.value = AiTaskState.Success(analysis)
                },
                onFailure = { error ->
                    _photoTagState.value = AiTaskState.Error(
                        error.localizedMessage ?: "AI photo auto-tagging failed. Tap 'Retry AI Auto-Detect'."
                    )
                }
            )
        }
    }

    fun scanMaterialCompositionLabel(bitmap: Bitmap) {
        viewModelScope.launch {
            _materialScanState.value = AiTaskState.Loading
            val result = aiStylistService.scanMaterialLabel(bitmap)
            result.fold(
                onSuccess = { scan ->
                    _materialScanState.value = AiTaskState.Success(scan)
                },
                onFailure = { error ->
                    _materialScanState.value = AiTaskState.Error(
                        error.localizedMessage ?: "Material label scan failed. Tap 'Retry Scan'."
                    )
                }
            )
        }
    }

    /**
     * Batch-uploads multiple garment photos into the user's Firestore closet first
     * (extracting each photo's dominant pixel color immediately) so the user can upload
     * their entire closet quickly and then run AI detection on all items!
     */
    fun batchUploadGarmentPhotos(bitmaps: List<Bitmap>, runAiAfterUpload: Boolean = false) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            showBanner("Uploading ${bitmaps.size} garment photo(s) to your closet...")
            val newlyCreatedItems = mutableListOf<ClosetItem>()
            for ((idx, bmp) in bitmaps.withIndex()) {
                val (colorName, colorHex) = ImageUtils.extractDominantGarmentColor(bmp)
                val base64Thumb = ImageUtils.encodeBitmapToBase64(bmp, maxDim = 280, quality = 70)
                val draft = ClosetItem(
                    userId = currentUserId,
                    name = "Uploaded $colorName Piece #${idx + 1}",
                    category = GarmentCategory.TOP.name,
                    subCategory = "Pending AI Auto-Detect",
                    materialComposition = "Pending AI Auto-Detect",
                    colorName = colorName,
                    colorHex = colorHex,
                    warmthLevel = 3,
                    isRainResistant = false,
                    isWindResistant = false,
                    occasions = listOf("Casual"),
                    seasons = listOf("Spring", "Autumn"),
                    notes = "Uploaded photo — tap 'Run AI Auto-Detect' to classify silhouette & material.",
                    imageBase64 = base64Thumb
                )
                closetRepository.addClosetItem(draft).onSuccess { newId ->
                    newlyCreatedItems.add(draft.copy(id = newId))
                }
            }
            if (runAiAfterUpload && newlyCreatedItems.isNotEmpty()) {
                showBanner("Uploaded ${newlyCreatedItems.size} pieces! Running Smart AI Auto-Detect in the background...")
                runAiAutoDetectOnClosetItems(newlyCreatedItems)
            } else {
                showBanner(
                    "Uploaded ${newlyCreatedItems.size} photo(s) to your closet! Tap 'Run AI on Closet' anytime to auto-detect all."
                )
            }
        }
    }

    /**
     * Runs or re-runs Smart AI Auto-Detect on a single existing ClosetItem (using its saved photo)
     * in the background. Useful when an item was uploaded first or if AI previously failed.
     */
    fun autoDetectSingleClosetItemInBackground(item: ClosetItem) {
        val bmp = ImageUtils.decodeBase64ToBitmap(item.imageBase64)
        if (bmp == null) {
            showBanner("Add a photo to \"${item.name}\" first to run AI Auto-Detect.")
            return
        }
        runAiAutoDetectOnClosetItems(listOf(item), forceReRun = true)
    }

    /**
     * Runs Smart AI Auto-Detect across pending closet items, skipping items that have already
     * been AI-tagged (unless forceReRun = true) and grouping up to 4 photos into a single
     * multimodal Gemini API call to minimize API runs.
     */
    fun runAiAutoDetectOnClosetItems(
        itemsToAnalyze: List<ClosetItem>,
        forceReRun: Boolean = false
    ) {
        val candidates = itemsToAnalyze.filter { it.imageBase64.isNotBlank() && it.id.isNotBlank() }
        if (candidates.isEmpty()) {
            showBanner("No closet items with uploaded photos found to analyze.")
            return
        }

        val pendingTargets = if (forceReRun) {
            candidates
        } else {
            candidates.filter { it.isPendingAiAnalysis() }
        }

        val skippedCount = candidates.size - pendingTargets.size
        if (pendingTargets.isEmpty()) {
            showBanner("All ${candidates.size} photo piece(s) are already AI-tagged — 0 new AI runs needed!")
            return
        }

        viewModelScope.launch {
            _backgroundAiJobsCount.value += 1
            _analyzingItemIds.value = _analyzingItemIds.value + pendingTargets.map { it.id }
            var successCount = 0
            try {
                // Group up to 4 pending photos per single Gemini multimodal call to reduce API runs by up to 4x!
                val chunks = pendingTargets.chunked(4)
                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    _backgroundAiStatusText.value =
                        "Smart AI Batch ${chunkIdx + 1}/${chunks.size}: Analyzing ${chunk.size} new piece(s) in 1 run..."
                    val decodedBatch = chunk.mapNotNull { item ->
                        val bmp = ImageUtils.decodeBase64ToBitmap(item.imageBase64)
                        if (bmp != null) item to bmp else null
                    }
                    if (decodedBatch.isEmpty()) continue

                    val batchResult = aiStylistService.analyzeGarmentBatch(decodedBatch)
                    batchResult.fold(
                        onSuccess = { analysisById ->
                            for ((item, _) in decodedBatch) {
                                val analysis = analysisById[item.id]
                                if (analysis != null) {
                                    val updated = item.copy(
                                        name = analysis.name,
                                        category = analysis.category,
                                        subCategory = analysis.subCategory,
                                        materialComposition = analysis.materialComposition,
                                        colorName = analysis.colorName,
                                        colorHex = analysis.colorHex,
                                        warmthLevel = analysis.warmthLevel,
                                        isRainResistant = analysis.isRainResistant,
                                        isWindResistant = analysis.isWindResistant,
                                        occasions = analysis.occasions,
                                        seasons = analysis.seasons,
                                        notes = analysis.notes
                                    )
                                    closetRepository.updateClosetItem(updated)
                                    successCount++
                                }
                                _analyzingItemIds.value = _analyzingItemIds.value - item.id
                            }
                        },
                        onFailure = {
                            // Fallback to individual item analysis if multi-image batch encountered an error
                            for ((item, bmp) in decodedBatch) {
                                aiStylistService.analyzeGarmentPhoto(
                                    bitmap = bmp,
                                    materialLabelBitmap = null,
                                    userHintName = item.name
                                ).onSuccess { analysis ->
                                    val updated = item.copy(
                                        name = analysis.name,
                                        category = analysis.category,
                                        subCategory = analysis.subCategory,
                                        materialComposition = analysis.materialComposition,
                                        colorName = analysis.colorName,
                                        colorHex = analysis.colorHex,
                                        warmthLevel = analysis.warmthLevel,
                                        isRainResistant = analysis.isRainResistant,
                                        isWindResistant = analysis.isWindResistant,
                                        occasions = analysis.occasions,
                                        seasons = analysis.seasons,
                                        notes = analysis.notes
                                    )
                                    closetRepository.updateClosetItem(updated)
                                    successCount++
                                }
                                _analyzingItemIds.value = _analyzingItemIds.value - item.id
                            }
                        }
                    )
                }
                val skippedSuffix = if (skippedCount > 0) " (skipped $skippedCount already-tagged)" else ""
                showBanner(
                    "Smart AI tagged $successCount new piece(s) in ${chunks.size} batch run(s)$skippedSuffix!"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Batch AI auto-detect error", e)
                showBanner("Batch AI Auto-Detect paused (${e.localizedMessage ?: "busy"}). You can re-run anytime.")
            } finally {
                _analyzingItemIds.value = _analyzingItemIds.value - pendingTargets.map { it.id }.toSet()
                _backgroundAiJobsCount.value = (_backgroundAiJobsCount.value - 1).coerceAtLeast(0)
                _backgroundAiStatusText.value =
                    if (_backgroundAiJobsCount.value > 0) "Running background AI tasks..." else null
            }
        }
    }

    /**
     * Immediately saves a garment photo to the user's Firestore closet and runs Gemini
     * auto-tagging + material composition detection asynchronously in the background!
     */
    fun saveGarmentAndAutoTagInBackground(
        garmentBitmap: Bitmap?,
        materialLabelBitmap: Bitmap?,
        draftItem: ClosetItem,
        onImmediateDismiss: () -> Unit
    ) {
        val base64Thumb = if (garmentBitmap != null && draftItem.imageBase64.isBlank()) {
            ImageUtils.encodeBitmapToBase64(garmentBitmap, maxDim = 280, quality = 70)
        } else {
            draftItem.imageBase64
        }

        val (fallbackColorName, fallbackColorHex) = if (garmentBitmap != null) {
            ImageUtils.extractDominantGarmentColor(garmentBitmap)
        } else {
            draftItem.colorName to draftItem.colorHex
        }

        val initialItem = draftItem.copy(
            name = draftItem.name.trim().ifBlank { "Uploaded $fallbackColorName Piece" },
            subCategory = draftItem.subCategory.trim().ifBlank { "Analyzing silhouette..." },
            materialComposition = draftItem.materialComposition.trim().ifBlank { "Analyzing fiber composition..." },
            colorName = draftItem.colorName.ifBlank { fallbackColorName },
            colorHex = draftItem.colorHex.ifBlank { fallbackColorHex },
            imageBase64 = base64Thumb
        )

        onImmediateDismiss()
        showBanner("Piece saved! Smart AI is analyzing garment & material in the background...")

        viewModelScope.launch {
            _backgroundAiJobsCount.value += 1
            _backgroundAiStatusText.value = "Background AI: Auto-tagging garment & material..."
            var savedId = initialItem.id
            try {
                savedId = if (initialItem.id.isBlank()) {
                    closetRepository.addClosetItem(initialItem).getOrThrow()
                } else {
                    closetRepository.updateClosetItem(initialItem).getOrThrow()
                    initialItem.id
                }
                _analyzingItemIds.value = _analyzingItemIds.value + savedId

                var updatedItem = initialItem.copy(id = savedId)

                if (garmentBitmap != null) {
                    aiStylistService.analyzeGarmentPhoto(
                        bitmap = garmentBitmap,
                        materialLabelBitmap = materialLabelBitmap,
                        userHintName = draftItem.name
                    ).onSuccess { analysis ->
                        updatedItem = updatedItem.copy(
                            name = if (draftItem.name.isBlank()) analysis.name else draftItem.name,
                            category = analysis.category,
                            subCategory = if (draftItem.subCategory.isBlank()) analysis.subCategory else draftItem.subCategory,
                            materialComposition = if (draftItem.materialComposition.isBlank()) analysis.materialComposition else draftItem.materialComposition,
                            colorName = analysis.colorName,
                            colorHex = analysis.colorHex,
                            warmthLevel = analysis.warmthLevel,
                            isRainResistant = analysis.isRainResistant,
                            isWindResistant = analysis.isWindResistant,
                            occasions = analysis.occasions,
                            seasons = analysis.seasons,
                            notes = if (draftItem.notes.isBlank()) analysis.notes else draftItem.notes
                        )
                    }.onFailure { err ->
                        updatedItem = updatedItem.copy(
                            subCategory = draftItem.subCategory.ifBlank { "Pending AI Auto-Detect" },
                            materialComposition = draftItem.materialComposition.ifBlank { "Pending AI Auto-Detect" }
                        )
                        Log.w(TAG, "Background garment analysis note: ${err.message}")
                    }
                } else if (materialLabelBitmap != null) {
                    aiStylistService.scanMaterialLabel(materialLabelBitmap).onSuccess { matScan ->
                        val mergedNotes = listOf(updatedItem.notes, "Care: ${matScan.careInstructions}")
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        updatedItem = updatedItem.copy(
                            materialComposition = matScan.materialComposition,
                            warmthLevel = matScan.suggestedWarmthLevel,
                            isRainResistant = updatedItem.isRainResistant || matScan.isRainResistant,
                            isWindResistant = updatedItem.isWindResistant || matScan.isWindResistant,
                            seasons = matScan.recommendedSeasons,
                            notes = mergedNotes.take(480)
                        )
                    }
                }

                closetRepository.updateClosetItem(updatedItem)
                showBanner("Smart AI finished tagging \"${updatedItem.name}\" (${updatedItem.materialComposition})!")
            } catch (e: Exception) {
                Log.e(TAG, "Background auto-tagging failed", e)
                showBanner("Piece saved to closet! Tap the AI sparkle icon on the item card anytime to re-run detection.")
            } finally {
                if (savedId.isNotBlank()) {
                    _analyzingItemIds.value = _analyzingItemIds.value - savedId
                }
                _backgroundAiJobsCount.value = (_backgroundAiJobsCount.value - 1).coerceAtLeast(0)
                _backgroundAiStatusText.value = if (_backgroundAiJobsCount.value > 0) "Running background AI tasks..." else null
            }
        }
    }

    fun resetPhotoTagState() {
        _photoTagState.value = AiTaskState.Idle
        _materialScanState.value = AiTaskState.Idle
    }

    fun seedStarterWardrobe() {
        if (_isSeedingCapsule.value) return
        viewModelScope.launch {
            _isSeedingCapsule.value = true
            val result = closetRepository.seedStarterCapsuleWardrobe()
            _isSeedingCapsule.value = false
            result.fold(
                onSuccess = { count ->
                    showBanner("Added $count starter capsule pieces & a curated look to your cloud closet!")
                },
                onFailure = { err ->
                    showBanner(err.localizedMessage ?: "Failed to load starter capsule.")
                }
            )
        }
    }

    fun saveClosetItem(item: ClosetItem, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = if (item.id.isBlank()) {
                closetRepository.addClosetItem(item).map { }
            } else {
                closetRepository.updateClosetItem(item)
            }
            result.fold(
                onSuccess = {
                    showBanner("Saved \"${item.name}\" to your closet.")
                    onComplete()
                },
                onFailure = { err ->
                    showBanner(err.localizedMessage ?: "Failed to save closet item.")
                }
            )
        }
    }

    fun toggleClosetItemFavorite(item: ClosetItem) {
        viewModelScope.launch {
            closetRepository.toggleClosetItemFavorite(item.id, !item.isFavorite)
        }
    }

    fun deleteClosetItem(itemId: String) {
        viewModelScope.launch {
            closetRepository.deleteClosetItem(itemId).onSuccess {
                showBanner("Removed piece from your closet.")
            }
        }
    }

    fun saveAiSuggestionAsOutfit(suggestion: AiOutfitSuggestion) {
        val activeDay = activeWeatherForecast()
        val weatherSummary = if (activeDay != null) {
            "${activeDay.tempCurrentOrMidC.toInt()}°C · ${activeDay.conditionLabel}"
        } else {
            "All-Weather"
        }
        viewModelScope.launch {
            val result = closetRepository.addOutfit(
                Outfit(
                    userId = currentUserId,
                    name = suggestion.outfitName,
                    itemIds = suggestion.itemIds,
                    occasion = suggestion.occasion,
                    targetWeatherSummary = weatherSummary,
                    weatherComfortScore = suggestion.weatherComfortScore,
                    stylistNotes = "${suggestion.stylistRationale} ${suggestion.layeringTip}".trim(),
                    isAiGenerated = true,
                    isFavorite = true
                )
            )
            result.fold(
                onSuccess = {
                    showBanner("Saved \"${suggestion.outfitName}\" to your Outfits collection!")
                },
                onFailure = { err ->
                    showBanner(err.localizedMessage ?: "Failed to save outfit.")
                }
            )
        }
    }

    fun logWearForSuggestionOrOutfit(
        outfitId: String,
        outfitName: String,
        itemIds: List<String>,
        occasion: String,
        dateIso: String = activeWeatherForecast()?.dateIso ?: LocalDate.now().toString(),
        notes: String = ""
    ) {
        val activeDay = activeWeatherForecast()
        val tempC = activeDay?.tempCurrentOrMidC ?: 18.0
        val cond = activeDay?.conditionLabel ?: "Clear"
        viewModelScope.launch {
            val result = closetRepository.logOutfitWear(
                dateIso = dateIso,
                outfitId = outfitId,
                outfitName = outfitName,
                itemIds = itemIds,
                occasion = occasion,
                weatherTempC = tempC,
                weatherCondition = cond,
                notes = notes
            )
            result.fold(
                onSuccess = {
                    showBanner("Logged \"$outfitName\" on $dateIso!")
                },
                onFailure = { err ->
                    showBanner(err.localizedMessage ?: "Failed to log outfit wear.")
                }
            )
        }
    }

    fun toggleOutfitFavorite(outfit: Outfit) {
        viewModelScope.launch {
            closetRepository.toggleOutfitFavorite(outfit.id, !outfit.isFavorite)
        }
    }

    fun deleteOutfit(outfitId: String) {
        viewModelScope.launch {
            closetRepository.deleteOutfit(outfitId).onSuccess {
                showBanner("Deleted outfit.")
            }
        }
    }

    fun deleteWearLog(logId: String) {
        viewModelScope.launch {
            closetRepository.deleteWearLog(logId).onSuccess {
                showBanner("Removed wear history entry.")
            }
        }
    }

    fun updateCanvasState(transform: (CanvasSelectionState) -> CanvasSelectionState) {
        _canvasState.value = transform(_canvasState.value)
    }

    fun shuffleCanvasForActiveWeather() {
        val items = (closetItemsState.value as? DataUiState.Success)?.data.orEmpty()
        if (items.isEmpty()) {
            showBanner("Add items to your closet first to shuffle looks.")
            return
        }
        val activeDay = activeWeatherForecast()
        val targetWarmth = activeDay?.recommendedWarmthLevel ?: 3

        fun pickBestOrRandom(cat: GarmentCategory): String? {
            val candidates = items.filter { it.category == cat.name }
            if (candidates.isEmpty()) return null
            val weatherFiltered = if (activeDay?.isRainy == true) {
                candidates.filter { it.isRainResistant }.ifEmpty { candidates }
            } else {
                candidates
            }
            val sorted = weatherFiltered.sortedBy { kotlin.math.abs(it.warmthLevel - targetWarmth) }
            return sorted.take(3).random().id
        }

        val topId = pickBestOrRandom(GarmentCategory.TOP)
        val bottomId = pickBestOrRandom(GarmentCategory.BOTTOM)
        val outerwearId = if (targetWarmth >= 3 || activeDay?.isRainy == true || activeDay?.isWindy == true) {
            pickBestOrRandom(GarmentCategory.OUTERWEAR)
        } else {
            null
        }
        val footwearId = pickBestOrRandom(GarmentCategory.FOOTWEAR)
        val accessoryId = if (targetWarmth >= 4) pickBestOrRandom(GarmentCategory.ACCESSORY) else null

        _canvasState.value = _canvasState.value.copy(
            useOnePieceMode = false,
            selectedTopId = topId,
            selectedBottomId = bottomId,
            selectedOnePieceId = null,
            selectedOuterwearId = outerwearId,
            selectedFootwearId = footwearId,
            selectedAccessoryId = accessoryId
        )
    }

    fun saveCurrentCanvasOutfit() {
        val state = _canvasState.value
        val items = (closetItemsState.value as? DataUiState.Success)?.data.orEmpty()
        val selectedIds = listOfNotNull(
            if (state.useOnePieceMode) state.selectedOnePieceId else state.selectedTopId,
            if (state.useOnePieceMode) null else state.selectedBottomId,
            state.selectedOuterwearId,
            state.selectedFootwearId,
            state.selectedAccessoryId
        ).distinct()

        if (selectedIds.isEmpty()) {
            showBanner("Select at least one garment on the canvas before saving.")
            return
        }

        val selectedModels = items.filter { it.id in selectedIds }
        val activeDay = activeWeatherForecast()
        val eval = AiStylistService.evaluateOutfitComfort(selectedModels, activeDay)
        val weatherSummary = if (activeDay != null) {
            "${activeDay.tempCurrentOrMidC.toInt()}°C · ${activeDay.conditionLabel}"
        } else {
            "Custom Studio Look"
        }
        val title = state.outfitTitle.trim().ifBlank {
            "${state.selectedOccasion} Studio Look"
        }

        viewModelScope.launch {
            val result = closetRepository.addOutfit(
                Outfit(
                    userId = currentUserId,
                    name = title,
                    itemIds = selectedIds,
                    occasion = state.selectedOccasion,
                    targetWeatherSummary = weatherSummary,
                    weatherComfortScore = eval.score,
                    stylistNotes = eval.detailTip,
                    isAiGenerated = false,
                    isFavorite = false
                )
            )
            result.fold(
                onSuccess = {
                    _canvasState.value = state.copy(outfitTitle = "")
                    showBanner("Saved \"$title\" to your Outfits!")
                },
                onFailure = { err ->
                    showBanner(err.localizedMessage ?: "Failed to save studio outfit.")
                }
            )
        }
    }
}
