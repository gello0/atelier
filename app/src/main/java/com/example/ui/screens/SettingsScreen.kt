package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.settings.AppSettings
import com.example.util.ImageUtils
import com.example.util.rememberCameraCapture

@Composable
fun SettingsScreen(
    userDisplayName: String,
    closetCount: Int,
    outfitsCount: Int,
    wearLogsCount: Int,
    useFahrenheit: Boolean,
    preferFastLiteModel: Boolean,
    autoFallbackOnHighDemand: Boolean,
    aiStudioPhotos: Boolean,
    engineLabel: String,
    vtonUserPhoto: Bitmap?,
    vtonFitNotes: String,
    isSeedingCapsule: Boolean,
    onToggleTempUnit: () -> Unit,
    onSetPreferFastLiteModel: (Boolean) -> Unit,
    onSetAutoFallbackOnHighDemand: (Boolean) -> Unit,
    onSetAiStudioPhotos: (Boolean) -> Unit,
    onSetVtonUserPhoto: (Bitmap?) -> Unit,
    onSetVtonFitNotes: (String) -> Unit,
    onOpenCitySearch: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onSeedStarterCapsule: () -> Unit,
    onDeleteAllData: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageUtils.decodeUriToScaledBitmap(context, uri, maxDim = 1280)?.let(onSetVtonUserPhoto)
    }
    val takePhoto = rememberCameraCapture(maxDim = 1280, onPhoto = onSetVtonUserPhoto)

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("settings_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }

        // Keys & models
        item {
            SettingsCard("API keys & models") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Gemini & Firebase keys", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Using: $engineLabel" + if (settings.usesCustomFirebase()) " · your Firebase project" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenApiKeys, modifier = Modifier.testTag("open_api_keys_button")) {
                        Text("Edit")
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
                HorizontalDivider()
                Text("Model", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !preferFastLiteModel,
                        onClick = { onSetPreferFastLiteModel(false) },
                        label = { Text("Best quality") },
                        modifier = Modifier.testTag("chip_model_flash")
                    )
                    FilterChip(
                        selected = preferFastLiteModel,
                        onClick = { onSetPreferFastLiteModel(true) },
                        label = { Text("Fastest") },
                        modifier = Modifier.testTag("chip_model_flash_lite")
                    )
                }
                ToggleRow(
                    title = "Use backup model when busy",
                    subtitle = "${settings.textModel} ↔ ${settings.liteModel}",
                    checked = autoFallbackOnHighDemand,
                    onChange = onSetAutoFallbackOnHighDemand,
                    tag = "switch_high_demand_fallback"
                )
                ToggleRow(
                    title = "Product-style photos",
                    subtitle = "Redraw new photos as clean shop photos (uses ${settings.imageModel}). Off: just remove the background.",
                    checked = aiStudioPhotos,
                    onChange = onSetAiStudioPhotos,
                    tag = "switch_studio_photos"
                )
            }
        }

        // Try-on
        item {
            SettingsCard("Try-on photo") {
                Text(
                    "A photo of you, used by Try on in the Studio tab. Kept on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (vtonUserPhoto != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(
                            bitmap = remember(vtonUserPhoto) { vtonUserPhoto.asImageBitmap() },
                            contentDescription = "Try-on photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSetVtonUserPhoto(null) }) { Text("Remove") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = takePhoto, modifier = Modifier.weight(1f).testTag("settings_vton_camera_button")) { Text("Camera") }
                    OutlinedButton(
                        onClick = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).testTag("settings_vton_gallery_button")
                    ) { Text("Gallery") }
                }
                OutlinedTextField(
                    value = vtonFitNotes,
                    onValueChange = onSetVtonFitNotes,
                    label = { Text("Preferred fit") },
                    placeholder = { Text("e.g. Relaxed, slightly oversized jackets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_vton_fit_notes_input")
                )
            }
        }

        // Weather
        item {
            SettingsCard("Weather") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (useFahrenheit) "Fahrenheit (°F)" else "Celsius (°C)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onToggleTempUnit, modifier = Modifier.testTag("settings_toggle_temp_button")) {
                        Text(if (useFahrenheit) "Use °C" else "Use °F")
                    }
                }
                OutlinedButton(onClick = onOpenCitySearch, modifier = Modifier.fillMaxWidth()) { Text("Change city") }
            }
        }

        // Data
        item {
            SettingsCard("Your data") {
                Text(
                    "$closetCount items · $outfitsCount outfits · $wearLogsCount wear entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onSeedStarterCapsule, enabled = !isSeedingCapsule, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSeedingCapsule) "Adding…" else "Add sample items")
                }
                OutlinedButton(
                    onClick = { confirmDeleteAll = true },
                    enabled = closetCount + outfitsCount + wearLogsCount > 0,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().testTag("delete_all_data_button")
                ) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Account
        item {
            if (com.example.BuildConfig.LOCAL_ONLY) {
                SettingsCard("Storage") {
                    Text(
                        "Your closet is stored on this phone only. It isn't backed up, and uninstalling the app deletes it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                SettingsCard("Account") {
                    Text("Signed in as $userDisplayName", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_button")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete everything?") },
            text = { Text("All $closetCount items, $outfitsCount outfits and $wearLogsCount wear entries will be permanently deleted from your account. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; onDeleteAllData() }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
