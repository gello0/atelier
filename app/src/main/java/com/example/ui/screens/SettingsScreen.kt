package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.ImageUtils

@Composable
fun SettingsScreen(
    userDisplayName: String,
    userId: String,
    closetCount: Int,
    outfitsCount: Int,
    wearLogsCount: Int,
    useFahrenheit: Boolean,
    preferFastLiteModel: Boolean,
    autoFallbackOnHighDemand: Boolean,
    backgroundAiJobsCount: Int,
    vtonUserPhoto: Bitmap?,
    vtonFitNotes: String,
    isSeedingCapsule: Boolean,
    onToggleTempUnit: () -> Unit,
    onSetPreferFastLiteModel: (Boolean) -> Unit,
    onSetAutoFallbackOnHighDemand: (Boolean) -> Unit,
    onSetVtonUserPhoto: (Bitmap?) -> Unit,
    onSetVtonFitNotes: (String) -> Unit,
    onOpenCitySearch: () -> Unit,
    onSeedStarterCapsule: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val vtonGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageUtils.decodeUriToScaledBitmap(context, uri, maxDim = 960)
            if (bmp != null) {
                onSetVtonUserPhoto(bmp)
            }
        }
    }

    val vtonCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            onSetVtonUserPhoto(bmp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vtonCameraLauncher.launch(null)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Atelier Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "AI model configuration, Virtual Try-On profile, weather preferences, and API status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Virtual Try-On (VTON) Profile Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Virtual Try-On (VTON) Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Save a reference photo of yourself so the Studio Virtual Try-On (powered by Gemini Nano Banana 2.1) can render your closet outfits directly on you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (vtonUserPhoto != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = vtonUserPhoto.asImageBitmap(),
                                contentDescription = "Saved VTON reference photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reference Photo Active",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ready for Studio Virtual Try-On",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { onSetVtonUserPhoto(null) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear photo", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_vton_camera_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take Photo")
                        }
                        OutlinedButton(
                            onClick = {
                                vtonGalleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_vton_gallery_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Photo")
                        }
                    }

                    OutlinedTextField(
                        value = vtonFitNotes,
                        onValueChange = onSetVtonFitNotes,
                        label = { Text("Fit & Silhouette Preference") },
                        placeholder = { Text("e.g., Relaxed tailored fit, slightly oversized outerwear") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_vton_fit_notes_input")
                    )
                }
            }
        }

        // 2. Gemini AI Engine & High-Demand Reliability Settings
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Gemini AI Engine & Background Queue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "All AI tasks (photo auto-tagging, care-label material scanning, daily outfit styling, and Virtual Try-On) run asynchronously in the background so you never have to wait.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Preferred Primary Model",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !preferFastLiteModel,
                            onClick = { onSetPreferFastLiteModel(false) },
                            label = { Text("gemini-3.8-flash (Default)") },
                            modifier = Modifier.testTag("chip_model_flash")
                        )
                        FilterChip(
                            selected = preferFastLiteModel,
                            onClick = { onSetPreferFastLiteModel(true) },
                            label = { Text("gemini-3.5-flash-lite (Ultra-Fast)") },
                            modifier = Modifier.testTag("chip_model_flash_lite")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Fallback on High Demand",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Automatically switches between gemini-3.8-flash and gemini-3.5-flash-lite if a model experiences peak demand.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoFallbackOnHighDemand,
                            onCheckedChange = onSetAutoFallbackOnHighDemand,
                            modifier = Modifier.testTag("switch_high_demand_fallback")
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Active Background AI Tasks: $backgroundAiJobsCount running",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 3. Weather & Wardrobe Preferences
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Weather & Wardrobe Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Temperature Unit",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (useFahrenheit) "Showing Fahrenheit (°F)" else "Showing Celsius (°C)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onToggleTempUnit,
                            modifier = Modifier.testTag("settings_toggle_temp_button")
                        ) {
                            Text(if (useFahrenheit) "Switch to °C" else "Switch to °F")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onOpenCitySearch,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Weather City")
                        }
                        OutlinedButton(
                            onClick = onSeedStarterCapsule,
                            enabled = !isSeedingCapsule,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isSeedingCapsule) "Loading..." else "Add Starter Capsule")
                        }
                    }
                }
            }
        }

        // 4. Active APIs & Cloud Diagnostics Card (Answers "what API are you using?")
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Connected APIs & Cloud Services",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    ApiDetailRow(
                        title = "1. Firebase AI Logic (Gemini API)",
                        detail = "• Text & Multimodal Vision: gemini-3.8-flash + gemini-3.5-flash-lite fallback (Garment photo auto-tagging, Care label fiber/material scanner, Weather outfit stylist)\n" +
                            "• Virtual Try-On (VTON) Image Synthesis: gemini-nano-banana-2.1 + gemini-3.1-flash-lite-image\n" +
                            "• Security: Zero client API keys required — authenticated via Firebase App Check."
                    )
                    HorizontalDivider()
                    ApiDetailRow(
                        title = "2. Open-Meteo Weather & Geocoding API + Android GPS",
                        detail = "• Forecast Endpoint: https://api.open-meteo.com/v1/forecast (7-day highs/lows, feels-like temp, precipitation %, wind speed)\n" +
                            "• City Search Endpoint: https://geocoding-api.open-meteo.com/v1/search\n" +
                            "• Local Telemetry: Google Play Services FusedLocationProviderClient."
                    )
                    HorizontalDivider()
                    ApiDetailRow(
                        title = "3. Firebase Cloud Firestore & Google Identity",
                        detail = "• Cloud Sync: Real-time Firestore collections (closetItems: $closetCount, outfits: $outfitsCount, wearLogs: $wearLogsCount)\n" +
                            "• Authentication: Signed in as $userDisplayName (${userId.take(10)}...) via Jetpack CredentialManager."
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out of Atelier")
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiDetailRow(
    title: String,
    detail: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
