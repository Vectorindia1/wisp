package com.wisp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wisp.app.settings.SettingsActivity

/**
 * The one-time permission chain the desktop app doesn't need at all:
 * overlay ("draw over other apps"), microphone, notifications (API 33+),
 * and a fresh MediaProjection consent dialog every time Wisp is (re)started
 * -- Android does not let an app silently re-acquire screen-capture
 * permission across process restarts, unlike the desktop app's
 * desktopCapturer, which needs no per-launch user consent at all.
 *
 * Once everything is granted, this hands off to OverlayService and gets
 * out of the way -- the floating overlay (not this Activity) is the real
 * "app" from the user's perspective from that point on.
 */
class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, intent)
            moveTaskToBack(true)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* re-render happens via recomposition on next check */ }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WispSetupScreen() }
    }

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun launchScreenCaptureConsent() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    @Composable
    private fun WispSetupScreen() {
        // Re-checked on every recomposition triggered by onResume below --
        // simplest way to reflect permission grants made in the system
        // Settings screen (overlay permission has no direct callback).
        var tick by remember { mutableIntStateOf(0) }
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val overlayGranted = remember(tick) { hasOverlayPermission() }
        val micGranted = remember(tick) { hasMicPermission() }
        val notifGranted = remember(tick) { hasNotificationPermission() }
        val allGranted = overlayGranted && micGranted && notifGranted

        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6D5BD0))) {
            Surface(color = Color(0xFF18181B), modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Wisp", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "A real-time AI overlay. Grant the permissions below, then Start Wisp.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )

                    PermissionStep(
                        label = "1. Draw over other apps",
                        granted = overlayGranted,
                        actionLabel = "Grant",
                        onClick = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        },
                    )
                    PermissionStep(
                        label = "2. Microphone (for live context)",
                        granted = micGranted,
                        actionLabel = "Grant",
                        onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionStep(
                            label = "3. Notifications (required to run in background)",
                            granted = notifGranted,
                            actionLabel = "Grant",
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = { launchScreenCaptureConsent() },
                        enabled = allGranted,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (allGranted) "Start Wisp" else "Grant permissions above first")
                    }

                    OutlinedButton(
                        onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Settings (API key)")
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionStep(label: String, granted: Boolean, actionLabel: String, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (granted) {
                Text("✓", color = Color(0xFF6D5BD0), fontSize = 16.sp)
            } else {
                Button(onClick = onClick) { Text(actionLabel) }
            }
        }
    }
}
