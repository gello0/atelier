package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.FirebaseBootstrap
import com.example.data.ai.RestGeminiEngine
import com.example.data.settings.AppSettings
import com.example.data.settings.FirebaseConfig
import kotlinx.coroutines.launch

/** Full-screen dialog for entering the Gemini API key and Firebase keys. */
@Composable
fun ApiKeysDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            ApiKeysContent(onClose = onDismiss)
        }
    }
}

@Composable
fun ApiKeysContent(onClose: (() -> Unit)?, setupMessage: String? = null) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val savedFirebase = remember { settings.firebaseConfig }
    var geminiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var textModel by remember { mutableStateOf(settings.textModel) }
    var liteModel by remember { mutableStateOf(settings.liteModel) }
    var imageModel by remember { mutableStateOf(settings.imageModel) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var checkingKey by remember { mutableStateOf(false) }

    var fb by remember { mutableStateOf(savedFirebase) }
    var fbMessage by remember { mutableStateOf<String?>(null) }
    var askRestart by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("API keys", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
        }
        Text(
            "Saved on this phone only and kept when you update the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (setupMessage != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    setupMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ---------------- Gemini
        Text("Gemini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = geminiKey,
            onValueChange = { geminiKey = it.trim(); keyStatus = null },
            label = { Text("Gemini API key") },
            placeholder = { Text("AIza…") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Show key")
                }
            },
            supportingText = { Text("Leave empty to use Firebase AI Logic instead.") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    checkingKey = true
                    keyStatus = null
                    scope.launch {
                        keyStatus = try {
                            val models = RestGeminiEngine(geminiKey).listModels()
                            availableModels = models
                            val missing = listOf(textModel, liteModel, imageModel).filter { it !in models }
                            if (missing.isEmpty()) "Key works. All selected models are available."
                            else "Key works, but not available for this key: ${missing.joinToString()}"
                        } catch (e: Exception) {
                            e.message ?: "Couldn't check the key."
                        }
                        checkingKey = false
                    }
                },
                enabled = geminiKey.isNotBlank() && !checkingKey
            ) {
                if (checkingKey) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Check key")
            }
            keyStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        ModelField("Main model", textModel, availableModels.filter { "image" !in it && "lite" !in it }) { textModel = it }
        ModelField("Fast / backup model", liteModel, availableModels.filter { "image" !in it }) { liteModel = it }
        ModelField("Image model (product photos & try-on)", imageModel, availableModels.filter { "image" in it || "banana" in it }) { imageModel = it }

        Button(
            onClick = {
                settings.geminiApiKey = geminiKey
                settings.textModel = textModel
                settings.liteModel = liteModel
                settings.imageModel = imageModel
                keyStatus = "Saved."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Gemini settings") }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // ---------------- Firebase
        Text("Firebase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Leave empty to use the project built into the app. For your own project, paste the contents of " +
                "google-services.json (Firebase console → Project settings → Your apps), or fill the fields in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = {
                val text = clipboard.getText()?.text.orEmpty()
                fbMessage = try {
                    fb = FirebaseConfig.fromGoogleServicesJson(text, context.packageName, fb.firestoreDatabaseId)
                    "Filled in from google-services.json. Check the fields, then save."
                } catch (e: Exception) {
                    "The clipboard doesn't contain a valid google-services.json."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Paste google-services.json")
        }
        KeyField("Web API key", fb.apiKey) { fb = fb.copy(apiKey = it) }
        KeyField("App ID", fb.appId, "1:1234567890:android:abc…") { fb = fb.copy(appId = it) }
        KeyField("Project ID", fb.projectId) { fb = fb.copy(projectId = it) }
        KeyField("Sender ID / project number", fb.senderId) { fb = fb.copy(senderId = it) }
        KeyField("Storage bucket", fb.storageBucket) { fb = fb.copy(storageBucket = it) }
        KeyField(
            "Firestore database ID",
            fb.firestoreDatabaseId,
            placeholder = "Currently: ${settings.effectiveFirestoreDatabaseId()}"
        ) { fb = fb.copy(firestoreDatabaseId = it) }
        KeyField(
            "Web client ID (Google Sign-In)",
            fb.webClientId,
            placeholder = "…apps.googleusercontent.com"
        ) { fb = fb.copy(webClientId = it) }
        Text(
            "Google Sign-In also needs this app's SHA-1 fingerprint added in the Firebase console.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        fbMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    fb = FirebaseConfig()
                    settings.firebaseConfig = FirebaseConfig()
                    askRestart = savedFirebase != FirebaseConfig()
                    fbMessage = "Cleared. The built-in project will be used."
                },
                modifier = Modifier.weight(1f)
            ) { Text("Use built-in") }
            Button(
                onClick = {
                    val partial = listOf(fb.apiKey, fb.appId, fb.projectId).count { it.isNotBlank() }
                    if (partial in 1..2) {
                        fbMessage = "Web API key, App ID and Project ID are all needed."
                    } else {
                        settings.firebaseConfig = fb
                        askRestart = fb != savedFirebase
                        fbMessage = "Saved."
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save Firebase") }
        }
        Box(Modifier.size(24.dp))
    }

    if (askRestart) {
        AlertDialog(
            onDismissRequest = { askRestart = false },
            title = { Text("Restart needed") },
            text = { Text("The app needs to restart to use the new Firebase keys.") },
            confirmButton = { TextButton(onClick = { FirebaseBootstrap.restartApp(context) }) { Text("Restart now") } },
            dismissButton = { TextButton(onClick = { askRestart = false }) { Text("Later") } }
        )
    }
}

@Composable
private fun KeyField(label: String, value: String, placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.trim()) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ModelField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.trim()) },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (options.isNotEmpty()) {
                    IconButton(onClick = { open = true }) { Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose model") }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { m ->
                DropdownMenuItem(text = { Text(m) }, onClick = { onChange(m); open = false })
            }
        }
    }
}
