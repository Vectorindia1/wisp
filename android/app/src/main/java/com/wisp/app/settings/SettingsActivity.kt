package com.wisp.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * BYOK settings screen -- a direct visual/conceptual port of the desktop
 * app's src/renderer/Settings.tsx (same dark palette, same provider list +
 * per-provider API key field + Ollama URL field), adapted to Compose.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        setContent { SettingsScreen(store) }
    }
}

@Composable
private fun SettingsScreen(store: SettingsStore) {
    var settings by remember { mutableStateOf(store.load()) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6D5BD0))) {
        Surface(color = Color(0xFF18181B), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Wisp Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Bring your own API key -- Wisp never bundles or proxies one for you. " +
                        "Your key is stored encrypted on this device and sent only to the " +
                        "provider you choose, directly.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )

                Text(
                    "LLM PROVIDER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Provider.entries.forEach { provider ->
                    ProviderRow(
                        provider = provider,
                        selected = settings.provider == provider,
                        onClick = { settings = settings.copy(provider = provider) },
                    )
                }

                if (settings.provider.needsKey) {
                    Text(
                        "${settings.provider.label.uppercase()} API KEY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = settings.apiKeyFor(settings.provider),
                            onValueChange = { newKey ->
                                settings = settings.copy(
                                    apiKeys = settings.apiKeys + (settings.provider to newKey),
                                )
                            },
                            placeholder = { Text("Paste your API key") },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show")
                        }
                    }
                }

                if (settings.provider == Provider.OLLAMA) {
                    Text(
                        "OLLAMA SERVER URL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    OutlinedTextField(
                        value = settings.ollamaBaseUrl,
                        onValueChange = { settings = settings.copy(ollamaBaseUrl = it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Point this at Ollama running on your PC/server -- a phone has no " +
                            "\"local\" Ollama of its own. Requires that machine reachable on " +
                            "the same network.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        store.save(settings)
                        saved = true
                    }) { Text("Save") }
                    if (saved) {
                        Text("✓ Saved", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.75f))
                        LaunchedEffect(saved) {
                            delay(1800)
                            saved = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: Provider, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF6D5BD0).copy(alpha = 0.18f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(provider.label, color = Color.White, fontSize = 13.5.sp)
        }
    }
}
