package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutFromAtelier
import com.example.data.ai.AiStylistService
import com.example.data.model.ClosetItem
import com.example.data.repository.ClosetRepository
import com.example.data.repository.LocalClosetRepository
import com.example.data.settings.AppSettings
import com.example.data.weather.WeatherRepository
import com.example.ui.AtelierViewModel
import com.example.ui.DataUiState
import com.example.ui.components.CitySearchDialog
import com.example.ui.screens.AddEditItemDialog
import com.example.ui.screens.ApiKeysContent
import com.example.ui.screens.ApiKeysDialog
import com.example.ui.screens.CalendarHistoryScreen
import com.example.ui.screens.ClosetScreen
import com.example.ui.screens.OutfitCanvasScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.TodayStylistScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (FirebaseBootstrap.isReady()) {
                        AtelierRootNavigation()
                    } else {
                        ApiKeysContent(onClose = null, setupMessage = FirebaseBootstrap.setupProblem)
                    }
                }
            }
        }
    }
}

enum class AtelierTab(val label: String, val icon: ImageVector, val tag: String) {
    TODAY("Stylist", Icons.Default.AutoAwesome, "nav_tab_stylist"),
    CLOSET("Closet", Icons.Default.Checkroom, "nav_tab_closet"),
    STUDIO("Studio", Icons.Default.Palette, "nav_tab_studio"),
    CALENDAR("Calendar", Icons.Default.CalendarMonth, "nav_tab_calendar"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_tab_settings")
}

@Composable
fun AtelierRootNavigation() {
    if (BuildConfig.LOCAL_ONLY) {
        // Phone-only build: no account, the closet lives on this phone.
        AtelierAuthenticatedShell(currentUserId = "local", userDisplayName = "This phone")
        return
    }
    val auth: FirebaseAuth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        SignInScreen(onAuthSuccess = { })
    } else {
        AtelierAuthenticatedShell(
            currentUserId = user.uid,
            userDisplayName = user.displayName ?: user.email ?: "Atelier Member"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtelierAuthenticatedShell(
    currentUserId: String,
    userDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AtelierViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val settings = AppSettings.get(app)
                AtelierViewModel(
                    appContext = app.applicationContext,
                    settings = settings,
                    closetRepository = if (BuildConfig.LOCAL_ONLY) LocalClosetRepository(app) else ClosetRepository(app),
                    weatherRepository = WeatherRepository(),
                    aiStylistService = AiStylistService(settings),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val closetItemsState by viewModel.closetItemsState.collectAsStateWithLifecycle()
    val outfitsState by viewModel.outfitsState.collectAsStateWithLifecycle()
    val wearLogsState by viewModel.wearLogsState.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsStateWithLifecycle()
    val useFahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    val selectedOccasion by viewModel.selectedOccasion.collectAsStateWithLifecycle()
    val aiOutfitState by viewModel.aiOutfitState.collectAsStateWithLifecycle()
    val photoTagState by viewModel.photoTagState.collectAsStateWithLifecycle()
    val materialScanState by viewModel.materialScanState.collectAsStateWithLifecycle()
    val vtonUserPhoto by viewModel.vtonUserPhotoBitmap.collectAsStateWithLifecycle()
    val vtonFitNotes by viewModel.vtonFitNotes.collectAsStateWithLifecycle()
    val vtonResultState by viewModel.vtonResultState.collectAsStateWithLifecycle()
    val preferFastLiteModel by viewModel.preferFastLiteModel.collectAsStateWithLifecycle()
    val autoFallbackOnHighDemand by viewModel.autoFallbackOnHighDemand.collectAsStateWithLifecycle()
    val aiStudioPhotos by viewModel.aiStudioPhotos.collectAsStateWithLifecycle()
    val backgroundAiJobsCount by viewModel.backgroundAiJobsCount.collectAsStateWithLifecycle()
    val backgroundAiStatusText by viewModel.backgroundAiStatusText.collectAsStateWithLifecycle()
    val analyzingItemIds by viewModel.analyzingItemIds.collectAsStateWithLifecycle()
    val canvasState by viewModel.canvasState.collectAsStateWithLifecycle()
    val citySearchResults by viewModel.citySearchResults.collectAsStateWithLifecycle()
    val isSearchingCities by viewModel.isSearchingCities.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isSeedingCapsule by viewModel.isSeedingCapsule.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AtelierTab.TODAY) }
    var showCitySearchDialog by rememberSaveable { mutableStateOf(false) }
    var showAddEditDialog by rememberSaveable { mutableStateOf(false) }
    var itemBeingEdited by remember { mutableStateOf<ClosetItem?>(null) }
    var showApiKeys by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = currentTab != AtelierTab.TODAY) {
        currentTab = AtelierTab.TODAY
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.refreshFromDeviceGps(context, showFailureBanner = false)
        }
    }

    // Automatically request location permission and fetch current GPS location on app launch by default
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            viewModel.refreshFromDeviceGps(context, showFailureBanner = false)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Atelier",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = userDisplayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (backgroundAiJobsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Working",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        if (!BuildConfig.LOCAL_ONLY) {
                            IconButton(
                                onClick = {
                                    signOutFromAtelier(
                                        credentialManager = credentialManager,
                                        onSignOutComplete = { },
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign Out"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        AtelierTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AtelierTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Non-blocking background AI status bar
                    AnimatedVisibility(visible = backgroundAiStatusText != null) {
                        backgroundAiStatusText?.let { statusText ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = bannerMessage != null) {
                        bannerMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clickable { viewModel.dismissBanner() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss notification",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            AtelierTab.TODAY -> TodayStylistScreen(
                                closetItemsState = closetItemsState,
                                outfitsState = outfitsState,
                                weatherState = weatherState,
                                selectedDayIndex = selectedDayIndex,
                                useFahrenheit = useFahrenheit,
                                selectedOccasion = selectedOccasion,
                                aiOutfitState = aiOutfitState,
                                isSeedingCapsule = isSeedingCapsule,
                                onSelectForecastDay = viewModel::selectForecastDay,
                                onToggleTempUnit = viewModel::toggleTemperatureUnit,
                                onOpenCitySearch = { showCitySearchDialog = true },
                                onRetryWeather = viewModel::retryWeather,
                                onRefreshGps = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onSelectOccasion = viewModel::selectOccasion,
                                onGenerateAiOutfits = viewModel::generateAiDailyOutfits,
                                onSaveAiSuggestion = viewModel::saveAiSuggestionAsOutfit,
                                onLogWearForOutfit = { outfitId, outfitName, itemIds, occ ->
                                    viewModel.logWearForSuggestionOrOutfit(
                                        outfitId = outfitId,
                                        outfitName = outfitName,
                                        itemIds = itemIds,
                                        occasion = occ
                                    )
                                },
                                onToggleOutfitFavorite = viewModel::toggleOutfitFavorite,
                                onDeleteOutfit = viewModel::deleteOutfit,
                                onSeedStarterCapsule = viewModel::seedStarterWardrobe,
                                onOpenAddGarment = {
                                    itemBeingEdited = null
                                    showAddEditDialog = true
                                }
                            )

                            AtelierTab.CLOSET -> ClosetScreen(
                                closetItemsState = closetItemsState,
                                isSeedingCapsule = isSeedingCapsule,
                                analyzingItemIds = analyzingItemIds,
                                onAddClick = {
                                    itemBeingEdited = null
                                    showAddEditDialog = true
                                },
                                onBatchUploadPhotos = viewModel::batchUploadGarmentPhotos,
                                onFillInPending = viewModel::runAiAutoDetectOnClosetItems,
                                onCleanUpAllPhotos = viewModel::enhanceAllPhotos,
                                onFixAll = viewModel::refreshAllItems,
                                onRefreshDetails = viewModel::autoDetectSingleClosetItemInBackground,
                                onRedoPhoto = viewModel::enhanceItemPhoto,
                                onRotatePhoto = viewModel::rotateItemPhoto,
                                onEditItem = { item ->
                                    itemBeingEdited = item
                                    showAddEditDialog = true
                                },
                                onToggleFavorite = viewModel::toggleClosetItemFavorite,
                                onDeleteItems = viewModel::deleteClosetItems,
                                onSeedStarterCapsule = viewModel::seedStarterWardrobe
                            )

                            AtelierTab.STUDIO -> OutfitCanvasScreen(
                                closetItemsState = closetItemsState,
                                canvasState = canvasState,
                                activeWeather = viewModel.activeWeatherForecast(),
                                useFahrenheit = useFahrenheit,
                                vtonUserPhoto = vtonUserPhoto,
                                vtonResultState = vtonResultState,
                                onSetVtonUserPhoto = viewModel::setVtonUserPhoto,
                                onRunVirtualTryOn = viewModel::runVirtualTryOnForCanvas,
                                onUpdateCanvas = viewModel::updateCanvasState,
                                onShuffleForWeather = viewModel::shuffleCanvasForActiveWeather,
                                onSaveCanvasOutfit = viewModel::saveCurrentCanvasOutfit,
                                onWearCanvasNow = { itemIds, title, occ ->
                                    viewModel.logWearForSuggestionOrOutfit(
                                        outfitId = "",
                                        outfitName = title,
                                        itemIds = itemIds,
                                        occasion = occ
                                    )
                                }
                            )

                            AtelierTab.CALENDAR -> CalendarHistoryScreen(
                                closetItemsState = closetItemsState,
                                outfitsState = outfitsState,
                                wearLogsState = wearLogsState,
                                useFahrenheit = useFahrenheit,
                                onLogOutfitOnDate = { outfit, dateIso, notes ->
                                    viewModel.logWearForSuggestionOrOutfit(
                                        outfitId = outfit.id,
                                        outfitName = outfit.name,
                                        itemIds = outfit.itemIds,
                                        occasion = outfit.occasion,
                                        dateIso = dateIso,
                                        notes = notes
                                    )
                                },
                                onDeleteWearLog = viewModel::deleteWearLog
                            )

                            AtelierTab.SETTINGS -> {
                                val closetCount = (closetItemsState as? DataUiState.Success)?.data?.size ?: 0
                                val outfitsCount = (outfitsState as? DataUiState.Success)?.data?.size ?: 0
                                val wearLogsCount = (wearLogsState as? DataUiState.Success)?.data?.size ?: 0
                                SettingsScreen(
                                    userDisplayName = userDisplayName,
                                    closetCount = closetCount,
                                    outfitsCount = outfitsCount,
                                    wearLogsCount = wearLogsCount,
                                    useFahrenheit = useFahrenheit,
                                    preferFastLiteModel = preferFastLiteModel,
                                    autoFallbackOnHighDemand = autoFallbackOnHighDemand,
                                    aiStudioPhotos = aiStudioPhotos,
                                    engineLabel = remember(showApiKeys) { AiStylistService(AppSettings.get(context)).engineLabel },
                                    vtonUserPhoto = vtonUserPhoto,
                                    vtonFitNotes = vtonFitNotes,
                                    isSeedingCapsule = isSeedingCapsule,
                                    onToggleTempUnit = viewModel::toggleTemperatureUnit,
                                    onSetPreferFastLiteModel = viewModel::setPreferFastLiteModel,
                                    onSetAutoFallbackOnHighDemand = viewModel::setAutoFallbackOnHighDemand,
                                    onSetAiStudioPhotos = viewModel::setAiStudioPhotos,
                                    onSetVtonUserPhoto = viewModel::setVtonUserPhoto,
                                    onSetVtonFitNotes = viewModel::setVtonFitNotes,
                                    onOpenCitySearch = { showCitySearchDialog = true },
                                    onOpenApiKeys = { showApiKeys = true },
                                    onSeedStarterCapsule = viewModel::seedStarterWardrobe,
                                    onDeleteAllData = viewModel::deleteAllData,
                                    onSignOut = {
                                        signOutFromAtelier(
                                            credentialManager = credentialManager,
                                            onSignOutComplete = { },
                                            scope = scope
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCitySearchDialog) {
        CitySearchDialog(
            searchResults = citySearchResults,
            isSearching = isSearchingCities,
            onQueryChange = viewModel::searchCities,
            onSelectCity = { city ->
                viewModel.loadWeatherForCity(city)
                showCitySearchDialog = false
            },
            onDismiss = { showCitySearchDialog = false }
        )
    }

    if (showAddEditDialog) {
        AddEditItemDialog(
            initialItem = itemBeingEdited,
            photoTagState = photoTagState,
            materialScanState = materialScanState,
            onAnalyzePhoto = viewModel::analyzeGarmentPhoto,
            onScanMaterialLabel = viewModel::scanMaterialCompositionLabel,
            onResetPhotoTagState = viewModel::resetPhotoTagState,
            onSave = { newPhoto, labelPhoto, draft, fillDetails ->
                viewModel.saveGarment(
                    newPhoto = newPhoto,
                    materialLabelBitmap = labelPhoto,
                    draftItem = draft,
                    fillDetails = fillDetails,
                    onImmediateDismiss = {
                        showAddEditDialog = false
                        itemBeingEdited = null
                    }
                )
            },
            onDismiss = {
                showAddEditDialog = false
                itemBeingEdited = null
            }
        )
    }

    if (showApiKeys) {
        ApiKeysDialog(onDismiss = { showApiKeys = false })
    }
}
