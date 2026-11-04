package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.example.data.settings.AppSettings
import com.example.data.weather.CityLocation
import com.example.data.weather.DailyWeatherForecast
import com.example.data.weather.PRESET_CITIES
import com.example.data.weather.WeatherRepository
import com.example.data.weather.WeatherSnapshot
import com.example.util.GarmentPhotoProcessor
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private const val TAG = "AtelierViewModel"

/** Marker written into a new item until its details have been filled in. */
private const val PENDING_MARKER = "Pending"

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
    private val appContext: Context,
    private val settings: AppSettings,
    private val closetRepository: ClosetRepository,
    private val weatherRepository: WeatherRepository,
    private val aiStylistService: AiStylistService,
    private val currentUserId: String
) : ViewModel() {

    val closetItemsState: StateFlow<DataUiState<List<ClosetItem>>> =
        closetRepository.observeClosetItems(currentUserId)
            .map<List<ClosetItem>, DataUiState<List<ClosetItem>>> { list ->
                DataUiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error ->
                Log.w(TAG, "Error observing closetItems", error)
                emit(DataUiState.Error(error.localizedMessage ?: "Failed to load closet items"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val outfitsState: StateFlow<DataUiState<List<Outfit>>> =
        closetRepository.observeOutfits(currentUserId)
            .map<List<Outfit>, DataUiState<List<Outfit>>> { list ->
                DataUiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error ->
                Log.w(TAG, "Error observing outfits", error)
                emit(DataUiState.Error(error.localizedMessage ?: "Failed to load saved outfits"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val wearLogsState: StateFlow<DataUiState<List<WearLog>>> =
        closetRepository.observeWearLogs(currentUserId)
            .map<List<WearLog>, DataUiState<List<WearLog>>> { list ->
                DataUiState.Success(list.sortedByDescending { it.dateIso })
            }
            .catch { error ->
                Log.w(TAG, "Error observing wearLogs", error)
                emit(DataUiState.Error(error.localizedMessage ?: "Failed to load wear logs"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    private val currentItems get() = (closetItemsState.value as? DataUiState.Success)?.data.orEmpty()
    private val currentOutfits get() = (outfitsState.value as? DataUiState.Success)?.data.orEmpty()
    private val currentLogs get() = (wearLogsState.value as? DataUiState.Success)?.data.orEmpty()

    private val _weatherState = MutableStateFlow<DataUiState<WeatherSnapshot>>(DataUiState.Loading)
    val weatherState: StateFlow<DataUiState<WeatherSnapshot>> = _weatherState.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(0)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _useFahrenheit = MutableStateFlow(settings.useFahrenheit)
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

    private val _vtonUserPhotoBitmap = MutableStateFlow(settings.loadVtonPhoto())
    val vtonUserPhotoBitmap: StateFlow<Bitmap?> = _vtonUserPhotoBitmap.asStateFlow()

    private val _vtonFitNotes = MutableStateFlow(settings.vtonFitNotes)
    val vtonFitNotes: StateFlow<String> = _vtonFitNotes.asStateFlow()

    private val _vtonResultState = MutableStateFlow<AiTaskState<Bitmap>>(AiTaskState.Idle)
    val vtonResultState: StateFlow<AiTaskState<Bitmap>> = _vtonResultState.asStateFlow()

    private val _preferFastLiteModel = MutableStateFlow(settings.preferFastLiteModel)
    val preferFastLiteModel: StateFlow<Boolean> = _preferFastLiteModel.asStateFlow()

    private val _autoFallbackOnHighDemand = MutableStateFlow(settings.autoFallbackOnHighDemand)
    val autoFallbackOnHighDemand: StateFlow<Boolean> = _autoFallbackOnHighDemand.asStateFlow()

    private val _aiStudioPhotos = MutableStateFlow(settings.aiStudioPhotos)
    val aiStudioPhotos: StateFlow<Boolean> = _aiStudioPhotos.asStateFlow()

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

    /** At most two photos are processed at the same time. */
    private val photoQueue = Semaphore(2)

    init {
        loadWeatherForCity(settings.lastCity ?: PRESET_CITIES.first())
    }

    // ------------------------------------------------------------------ banners & background jobs

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    fun showBanner(message: String) {
        _statusBannerMessage.value = message
    }

    private fun jobStarted(status: String) {
        _backgroundAiJobsCount.update { it + 1 }
        _backgroundAiStatusText.value = status
    }

    private fun jobFinished() {
        _backgroundAiJobsCount.update { (it - 1).coerceAtLeast(0) }
        if (_backgroundAiJobsCount.value == 0) _backgroundAiStatusText.value = null
    }

    private fun markAnalyzing(id: String, on: Boolean) {
        _analyzingItemIds.update { if (on) it + id else it - id }
    }

    // ------------------------------------------------------------------ preferences

    fun toggleTemperatureUnit() {
        _useFahrenheit.value = !_useFahrenheit.value
        settings.useFahrenheit = _useFahrenheit.value
    }

    fun setPreferFastLiteModel(preferLite: Boolean) {
        _preferFastLiteModel.value = preferLite
        settings.preferFastLiteModel = preferLite
    }

    fun setAutoFallbackOnHighDemand(enabled: Boolean) {
        _autoFallbackOnHighDemand.value = enabled
        settings.autoFallbackOnHighDemand = enabled
    }

    fun setAiStudioPhotos(enabled: Boolean) {
        _aiStudioPhotos.value = enabled
        settings.aiStudioPhotos = enabled
    }

    fun setVtonUserPhoto(bitmap: Bitmap?) {
        _vtonUserPhotoBitmap.value = bitmap
        viewModelScope.launch(Dispatchers.IO) { settings.saveVtonPhoto(bitmap) }
        if (bitmap != null) showBanner("Try-on photo saved.")
    }

    fun setVtonFitNotes(notes: String) {
        _vtonFitNotes.value = notes
        settings.vtonFitNotes = notes
    }

    // ------------------------------------------------------------------ weather

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

    private fun forecastForDate(dateIso: String): DailyWeatherForecast? =
        (_weatherState.value as? DataUiState.Success)?.data?.dailyForecasts?.firstOrNull { it.dateIso == dateIso }

    fun loadWeatherForCity(city: CityLocation) {
        viewModelScope.launch {
            _weatherState.value = DataUiState.Loading
            weatherRepository.fetchWeatherForecast(city).fold(
                onSuccess = { snapshot ->
                    _selectedDayIndex.value = 0
                    _weatherState.value = DataUiState.Success(snapshot)
                    settings.lastCity = city
                },
                onFailure = { error ->
                    _weatherState.value = DataUiState.Error(
                        error.localizedMessage ?: "Couldn't load the weather for ${city.name}."
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
                val fallback = settings.lastCity ?: PRESET_CITIES.first()
                loadWeatherForCity(fallback)
                showBanner("Current location unavailable; showing ${fallback.name}. Use City Search to pick another place.")
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
            result.onSuccess { _citySearchResults.value = it }
        }
    }

    // ------------------------------------------------------------------ outfit suggestions

    fun generateAiDailyOutfits() {
        val closetItems = currentItems
        val snapshot = (_weatherState.value as? DataUiState.Success)?.data
        val activeDay = activeWeatherForecast()
        if (closetItems.isEmpty()) {
            _aiOutfitState.value = AiTaskState.Error("Your closet is empty. Add some items first.")
            return
        }
        if (snapshot == null || activeDay == null) {
            _aiOutfitState.value = AiTaskState.Error("Wait for the weather to load first.")
            return
        }
        viewModelScope.launch {
            _aiOutfitState.value = AiTaskState.Loading
            jobStarted("Putting outfits together…")
            val result = aiStylistService.generateWeatherOutfits(
                closetItems = closetItems,
                weather = activeDay,
                cityName = snapshot.location.name,
                occasion = _selectedOccasion.value
            )
            jobFinished()
            result.fold(
                onSuccess = { _aiOutfitState.value = AiTaskState.Success(it) },
                onFailure = { _aiOutfitState.value = AiTaskState.Error(it.localizedMessage ?: "Couldn't suggest outfits.") }
            )
        }
    }

    fun runVirtualTryOnForCanvas() {
        val selectedItems = currentItems.filter { it.id in canvasSelectedIds(_canvasState.value) }
        if (selectedItems.isEmpty()) {
            _vtonResultState.value = AiTaskState.Error("Pick at least one item first.")
            return
        }
        val activeDay = activeWeatherForecast()
        val weatherSummary = activeDay?.let { "${it.feelsLikeC.toInt()}°C, ${it.conditionLabel}" } ?: "Mild"
        val state = _canvasState.value
        viewModelScope.launch {
            _vtonResultState.value = AiTaskState.Loading
            jobStarted("Creating try-on image…")
            val result = aiStylistService.generateVirtualTryOn(
                userReferencePhoto = _vtonUserPhotoBitmap.value,
                selectedItems = selectedItems,
                occasion = state.selectedOccasion,
                weatherSummary = weatherSummary,
                fitNotes = _vtonFitNotes.value
            )
            jobFinished()
            result.fold(
                onSuccess = { _vtonResultState.value = AiTaskState.Success(it) },
                onFailure = { _vtonResultState.value = AiTaskState.Error(it.localizedMessage ?: "Try-on failed.") }
            )
        }
    }

    // ------------------------------------------------------------------ add / edit dialog helpers

    fun analyzeGarmentPhoto(bitmap: Bitmap, materialLabelBitmap: Bitmap? = null, userHintName: String = "") {
        viewModelScope.launch {
            _photoTagState.value = AiTaskState.Loading
            aiStylistService.analyzeGarmentPhoto(bitmap, materialLabelBitmap, userHintName).fold(
                onSuccess = { _photoTagState.value = AiTaskState.Success(it) },
                onFailure = { _photoTagState.value = AiTaskState.Error(it.localizedMessage ?: "Couldn't read this photo.") }
            )
        }
    }

    fun scanMaterialCompositionLabel(bitmap: Bitmap) {
        viewModelScope.launch {
            _materialScanState.value = AiTaskState.Loading
            aiStylistService.scanMaterialLabel(bitmap).fold(
                onSuccess = { _materialScanState.value = AiTaskState.Success(it) },
                onFailure = { _materialScanState.value = AiTaskState.Error(it.localizedMessage ?: "Couldn't read the label.") }
            )
        }
    }

    fun resetPhotoTagState() {
        _photoTagState.value = AiTaskState.Idle
        _materialScanState.value = AiTaskState.Idle
    }

    /**
     * Saves an item from the add/edit dialog straight away and closes the dialog. When a new
     * photo was picked, the photo is cleaned up into a cutout in the background, and when
     * [fillDetails] is true the details are filled in from the photo as well.
     */
    fun saveGarment(
        newPhoto: Bitmap?,
        materialLabelBitmap: Bitmap?,
        draftItem: ClosetItem,
        fillDetails: Boolean,
        onImmediateDismiss: () -> Unit
    ) {
        val needsDetails = fillDetails && (newPhoto != null || draftItem.imageBase64.isNotBlank() || materialLabelBitmap != null)
        val initial = draftItem.copy(
            name = draftItem.name.trim().ifBlank { if (needsDetails) "New piece" else "Untitled Piece" },
            subCategory = draftItem.subCategory.trim().ifBlank { if (needsDetails) PENDING_MARKER else "" },
            imageBase64 = newPhoto?.let { ImageUtils.encodeBitmapToBase64(it, maxDim = 512, quality = 82) }
                ?: draftItem.imageBase64
        )
        onImmediateDismiss()
        viewModelScope.launch {
            val saveResult = if (initial.id.isBlank()) {
                closetRepository.addClosetItem(initial)
            } else {
                closetRepository.updateClosetItem(initial).map { initial.id }
            }
            val savedId = saveResult.getOrElse {
                showBanner(it.localizedMessage ?: "Couldn't save this item.")
                return@launch
            }
            if (newPhoto == null && !needsDetails) {
                showBanner("Saved \"${initial.name}\".")
                return@launch
            }
            val source = newPhoto ?: ImageUtils.decodeBase64ToBitmap(initial.imageBase64)
            processItem(
                item = initial.copy(id = savedId),
                photo = source,
                labelPhoto = materialLabelBitmap,
                fillDetails = needsDetails,
                makeCutout = newPhoto != null,
                userTyped = draftItem
            )
        }
    }

    /** Saves edits that don't touch the photo. */
    fun saveClosetItem(item: ClosetItem, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = if (item.id.isBlank()) closetRepository.addClosetItem(item).map { } else closetRepository.updateClosetItem(item)
            result.fold(
                onSuccess = {
                    showBanner("Saved \"${item.name}\".")
                    onComplete()
                },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't save this item.") }
            )
        }
    }

    // ------------------------------------------------------------------ photo processing

    /**
     * Fills in details from the photo and/or replaces the photo with a clean cutout, then saves.
     * Fields the owner typed themselves ([userTyped]) are kept.
     */
    private suspend fun processItem(
        item: ClosetItem,
        photo: Bitmap?,
        labelPhoto: Bitmap?,
        fillDetails: Boolean,
        makeCutout: Boolean,
        userTyped: ClosetItem? = null
    ) {
        photoQueue.withPermit {
            markAnalyzing(item.id, true)
            jobStarted(if (fillDetails) "Adding details for ${displayName(item)}…" else "Cleaning up photo…")
            try {
                var updated = item
                if (fillDetails && photo != null) {
                    aiStylistService.analyzeGarmentPhoto(photo, labelPhoto, userTyped?.name.orEmpty()).fold(
                        onSuccess = { a ->
                            val typed = userTyped
                            updated = updated.copy(
                                name = typed?.name?.trim()?.takeIf { it.isNotBlank() } ?: a.name,
                                category = a.category,
                                subCategory = typed?.subCategory?.trim()?.takeIf { it.isNotBlank() } ?: a.subCategory,
                                materialComposition = typed?.materialComposition?.trim()?.takeIf { it.isNotBlank() } ?: a.materialComposition,
                                colorName = a.colorName,
                                colorHex = a.colorHex,
                                warmthLevel = a.warmthLevel,
                                isRainResistant = a.isRainResistant || (typed?.isRainResistant == true),
                                isWindResistant = a.isWindResistant || (typed?.isWindResistant == true),
                                occasions = a.occasions,
                                seasons = a.seasons,
                                notes = typed?.notes?.trim()?.takeIf { it.isNotBlank() } ?: a.notes
                            )
                        },
                        onFailure = { showBanner("Couldn't fill in details for ${displayName(item)}: ${it.localizedMessage}") }
                    )
                } else if (fillDetails && labelPhoto != null) {
                    aiStylistService.scanMaterialLabel(labelPhoto).onSuccess { scan ->
                        val care = scan.careInstructions.takeIf { it.isNotBlank() }?.let { "Care: $it" }
                        updated = updated.copy(
                            subCategory = updated.subCategory.takeUnless { it == PENDING_MARKER }.orEmpty(),
                            materialComposition = scan.materialComposition,
                            warmthLevel = scan.suggestedWarmthLevel,
                            isRainResistant = updated.isRainResistant || scan.isRainResistant,
                            isWindResistant = updated.isWindResistant || scan.isWindResistant,
                            seasons = scan.recommendedSeasons,
                            notes = listOfNotNull(updated.notes.ifBlank { null }, care).joinToString(" ").take(480)
                        )
                    }
                }

                if (makeCutout || (photo == null && updated.imageBase64.isBlank())) {
                    val cutout = GarmentPhotoProcessor.makeCutoutBase64(
                        photo = photo,
                        item = updated,
                        useAiStudioRender = settings.aiStudioPhotos,
                        ai = aiStylistService
                    )
                    if (cutout != null) updated = updated.copy(imageBase64 = cutout)
                }

                if (updated != item) {
                    closetRepository.updateClosetItem(updated).onFailure {
                        showBanner(it.localizedMessage ?: "Couldn't save changes to ${displayName(item)}.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing ${item.id} failed", e)
            } finally {
                markAnalyzing(item.id, false)
                jobFinished()
            }
        }
    }

    private fun displayName(item: ClosetItem): String =
        item.name.takeUnless { it.isBlank() || it == "New piece" }?.let { "\"$it\"" } ?: "the new item"

    /**
     * Adds many photos at once: each becomes an item immediately, then details and a clean
     * cutout are filled in in the background (two at a time).
     */
    fun batchUploadGarmentPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            showBanner("Adding ${uris.size} photo(s)…")
            val created = mutableListOf<Pair<ClosetItem, File>>()
            val dir = File(appContext.cacheDir, "uploads").apply { mkdirs() }
            for (uri in uris) {
                val bmp = withContext(Dispatchers.IO) { ImageUtils.loadAndCompressUriToBitmap(appContext, uri, maxDim = 1280) }
                    ?: continue
                val (colorName, colorHex) = ImageUtils.extractDominantGarmentColor(bmp)
                val draft = ClosetItem(
                    userId = currentUserId,
                    name = "New piece",
                    category = GarmentCategory.TOP.name,
                    subCategory = PENDING_MARKER,
                    colorName = colorName,
                    colorHex = colorHex,
                    occasions = listOf("Casual"),
                    seasons = listOf("Spring", "Autumn"),
                    imageBase64 = ImageUtils.encodeBitmapToBase64(bmp, maxDim = 512, quality = 82)
                )
                val id = closetRepository.addClosetItem(draft).getOrNull() ?: continue
                val file = File(dir, "$id.jpg")
                withContext(Dispatchers.IO) { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
                created.add(draft.copy(id = id) to file)
            }
            if (created.isEmpty()) {
                showBanner("Couldn't read those photos.")
                return@launch
            }
            showBanner("Added ${created.size} item(s). Details and photos are being filled in.")
            created.map { (item, file) ->
                async {
                    val original = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
                    processItem(item, original, null, fillDetails = true, makeCutout = true)
                    file.delete()
                }
            }.awaitAll()
        }
    }

    /** Fills in details for items still marked pending. */
    fun runAiAutoDetectOnClosetItems(itemsToAnalyze: List<ClosetItem>) {
        val targets = itemsToAnalyze.filter { it.imageBase64.isNotBlank() && it.id.isNotBlank() && it.id !in _analyzingItemIds.value }
        if (targets.isEmpty()) {
            showBanner("Nothing to fill in.")
            return
        }
        viewModelScope.launch {
            targets.map { item ->
                async {
                    val bmp = ImageUtils.decodeBase64ToBitmap(item.imageBase64)
                    processItem(item, bmp, null, fillDetails = true, makeCutout = !ImageUtils.isCutoutImage(item.imageBase64))
                }
            }.awaitAll()
            showBanner("Updated ${targets.size} item(s).")
        }
    }

    /** Re-reads one item's photo and refreshes its details (keeps the photo). */
    fun autoDetectSingleClosetItemInBackground(item: ClosetItem) {
        val bmp = ImageUtils.decodeBase64ToBitmap(item.imageBase64)
        if (bmp == null) {
            showBanner("Add a photo to \"${item.name}\" first.")
            return
        }
        viewModelScope.launch {
            processItem(item.copy(name = item.name), bmp, null, fillDetails = true, makeCutout = false)
        }
    }

    /** Redoes the item's photo as a clean product cutout (from its photo, or from its details if it has none). */
    fun enhanceItemPhoto(item: ClosetItem) {
        if (item.id in _analyzingItemIds.value) return
        viewModelScope.launch {
            val bmp = ImageUtils.decodeBase64ToBitmap(item.imageBase64)
            processItem(item, bmp, null, fillDetails = false, makeCutout = true)
        }
    }

    /** Cleans up every photo that isn't a cutout yet (and draws one for items without a photo). */
    fun enhanceAllPhotos() {
        val targets = currentItems.filter { !ImageUtils.isCutoutImage(it.imageBase64) && it.id !in _analyzingItemIds.value }
        if (targets.isEmpty()) {
            showBanner("All photos are already cleaned up.")
            return
        }
        showBanner("Cleaning up ${targets.size} photo(s)…")
        targets.forEach { enhanceItemPhoto(it) }
    }

    // ------------------------------------------------------------------ closet edits

    fun seedStarterWardrobe() {
        if (_isSeedingCapsule.value) return
        viewModelScope.launch {
            _isSeedingCapsule.value = true
            val result = closetRepository.seedStarterCapsuleWardrobe()
            _isSeedingCapsule.value = false
            result.fold(
                onSuccess = { showBanner("Added $it starter pieces and a sample outfit.") },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't add the starter pieces.") }
            )
        }
    }

    fun toggleClosetItemFavorite(item: ClosetItem) {
        viewModelScope.launch { closetRepository.toggleClosetItemFavorite(item.id, !item.isFavorite) }
    }

    fun deleteClosetItem(itemId: String) = deleteClosetItems(setOf(itemId))

    /** Deletes items and removes them from saved outfits (outfits left empty are deleted too). */
    fun deleteClosetItems(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            closetRepository.deleteClosetItems(itemIds, currentOutfits).fold(
                onSuccess = {
                    _canvasState.update { s ->
                        s.copy(
                            selectedTopId = s.selectedTopId?.takeUnless { it in itemIds },
                            selectedBottomId = s.selectedBottomId?.takeUnless { it in itemIds },
                            selectedOnePieceId = s.selectedOnePieceId?.takeUnless { it in itemIds },
                            selectedOuterwearId = s.selectedOuterwearId?.takeUnless { it in itemIds },
                            selectedFootwearId = s.selectedFootwearId?.takeUnless { it in itemIds },
                            selectedAccessoryId = s.selectedAccessoryId?.takeUnless { it in itemIds }
                        )
                    }
                    showBanner(if (itemIds.size == 1) "Item deleted." else "${itemIds.size} items deleted.")
                },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't delete.") }
            )
        }
    }

    /** Deletes the whole closet, all outfits and all wear history. */
    fun deleteAllData() {
        viewModelScope.launch {
            closetRepository.deleteAllUserData().fold(
                onSuccess = {
                    _canvasState.value = CanvasSelectionState()
                    _aiOutfitState.value = AiTaskState.Idle
                    showBanner("Everything was deleted ($it records).")
                },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't delete everything.") }
            )
        }
    }

    // ------------------------------------------------------------------ outfits & wear logs

    fun saveAiSuggestionAsOutfit(suggestion: AiOutfitSuggestion) {
        val activeDay = activeWeatherForecast()
        val weatherSummary = activeDay?.let { "${it.tempCurrentOrMidC.toInt()}°C · ${it.conditionLabel}" } ?: "Any weather"
        viewModelScope.launch {
            closetRepository.addOutfit(
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
            ).fold(
                onSuccess = { showBanner("Saved \"${suggestion.outfitName}\".") },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't save the outfit.") }
            )
        }
    }

    /**
     * Logs an outfit as worn. Defaults to TODAY (not the forecast day being viewed); the weather
     * stored is the forecast for that date when known.
     */
    fun logWearForSuggestionOrOutfit(
        outfitId: String,
        outfitName: String,
        itemIds: List<String>,
        occasion: String,
        dateIso: String = LocalDate.now().toString(),
        notes: String = ""
    ) {
        val existingIds = currentItems.map { it.id }.toSet()
        val ids = itemIds.filter { it in existingIds }
        if (ids.isEmpty()) {
            showBanner("These items are no longer in your closet.")
            return
        }
        val day = forecastForDate(dateIso)
        val outfitExists = outfitId.isNotBlank() && currentOutfits.any { it.id == outfitId }
        viewModelScope.launch {
            closetRepository.logOutfitWear(
                dateIso = dateIso,
                outfitId = outfitId,
                outfitName = outfitName,
                itemIds = ids,
                occasion = occasion,
                weatherTempC = day?.tempCurrentOrMidC ?: 18.0,
                weatherCondition = day?.conditionLabel ?: "Not recorded",
                notes = notes,
                outfitExists = outfitExists
            ).fold(
                onSuccess = {
                    val label = if (dateIso == LocalDate.now().toString()) "today" else "on $dateIso"
                    showBanner("Logged \"$outfitName\" $label.")
                },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't log this outfit.") }
            )
        }
    }

    fun toggleOutfitFavorite(outfit: Outfit) {
        viewModelScope.launch { closetRepository.toggleOutfitFavorite(outfit.id, !outfit.isFavorite) }
    }

    fun deleteOutfit(outfitId: String) {
        viewModelScope.launch {
            closetRepository.deleteOutfit(outfitId).onSuccess { showBanner("Outfit deleted.") }
        }
    }

    /** Deletes a wear-history entry and rolls back the wear counts it added. */
    fun deleteWearLog(logId: String) {
        val log = currentLogs.firstOrNull { it.id == logId }
        if (log == null) {
            viewModelScope.launch { closetRepository.deleteWearLog(logId) }
            return
        }
        val remaining = currentLogs.filter { it.id != logId }
        val itemsById = currentItems.associateBy { it.id }
        val itemUpdates = log.itemIds.distinct().mapNotNull { id ->
            val item = itemsById[id] ?: return@mapNotNull null
            val lastWorn = remaining.filter { id in it.itemIds }
                .mapNotNull { runCatching { LocalDate.parse(it.dateIso).toEpochDay() }.getOrNull() }
                .maxOrNull() ?: 0L
            id to ((item.wearCount - 1).coerceAtLeast(0L) to lastWorn)
        }.toMap()
        val outfit = currentOutfits.firstOrNull { it.id == log.outfitId }
        viewModelScope.launch {
            closetRepository.deleteWearLog(
                logId = logId,
                itemUpdates = itemUpdates,
                outfitId = outfit?.id.orEmpty(),
                outfitWearCount = outfit?.let { (it.wearCount - 1).coerceAtLeast(0L) }
            ).fold(
                onSuccess = { showBanner("Removed from history.") },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't remove this entry.") }
            )
        }
    }

    // ------------------------------------------------------------------ studio canvas

    fun updateCanvasState(transform: (CanvasSelectionState) -> CanvasSelectionState) {
        _canvasState.value = transform(_canvasState.value)
    }

    private fun canvasSelectedIds(state: CanvasSelectionState): List<String> = listOfNotNull(
        if (state.useOnePieceMode) state.selectedOnePieceId else state.selectedTopId,
        if (state.useOnePieceMode) null else state.selectedBottomId,
        state.selectedOuterwearId,
        state.selectedFootwearId,
        state.selectedAccessoryId
    ).distinct()

    fun shuffleCanvasForActiveWeather() {
        val items = currentItems
        if (items.isEmpty()) {
            showBanner("Add items to your closet first.")
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
            return weatherFiltered.sortedBy { kotlin.math.abs(it.warmthLevel - targetWarmth) }.take(3).random().id
        }

        val outerwearId = if (targetWarmth >= 3 || activeDay?.isRainy == true || activeDay?.isWindy == true) {
            pickBestOrRandom(GarmentCategory.OUTERWEAR)
        } else {
            null
        }
        _canvasState.value = _canvasState.value.copy(
            useOnePieceMode = false,
            selectedTopId = pickBestOrRandom(GarmentCategory.TOP),
            selectedBottomId = pickBestOrRandom(GarmentCategory.BOTTOM),
            selectedOnePieceId = null,
            selectedOuterwearId = outerwearId,
            selectedFootwearId = pickBestOrRandom(GarmentCategory.FOOTWEAR),
            selectedAccessoryId = if (targetWarmth >= 4) pickBestOrRandom(GarmentCategory.ACCESSORY) else null
        )
    }

    fun saveCurrentCanvasOutfit() {
        val state = _canvasState.value
        val selectedIds = canvasSelectedIds(state).filter { id -> currentItems.any { it.id == id } }
        if (selectedIds.isEmpty()) {
            showBanner("Pick at least one item first.")
            return
        }
        val selectedModels = currentItems.filter { it.id in selectedIds }
        val activeDay = activeWeatherForecast()
        val eval = AiStylistService.evaluateOutfitComfort(selectedModels, activeDay)
        val weatherSummary = activeDay?.let { "${it.tempCurrentOrMidC.toInt()}°C · ${it.conditionLabel}" } ?: "Any weather"
        val title = state.outfitTitle.trim().ifBlank { "${state.selectedOccasion} Look" }

        viewModelScope.launch {
            closetRepository.addOutfit(
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
            ).fold(
                onSuccess = {
                    _canvasState.value = state.copy(outfitTitle = "")
                    showBanner("Saved \"$title\".")
                },
                onFailure = { showBanner(it.localizedMessage ?: "Couldn't save the outfit.") }
            )
        }
    }
}
