package com.wisp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.wisp.app.capture.CaptureManager
import com.wisp.app.providers.getProvider
import com.wisp.app.settings.SettingsStore
import com.wisp.app.settings.SettingsActivity
import com.wisp.app.transcription.RollingTranscriptBuffer
import com.wisp.app.transcription.TranscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * The mobile counterpart to src/main/main.ts + Overlay.tsx combined: owns
 * the floating overlay window (built from plain Android Views, not Compose
 * -- see the class-level note below for why), the MediaProjection-backed
 * screenshot capture, and driving a provider call on each capture tap.
 *
 * Runs as a foreground service (required to hold a MediaProjection and to
 * keep the overlay window alive while the launcher Activity isn't in the
 * foreground) with a persistent notification, same "the app is always
 * running in the background, tray-icon-style" posture as the desktop app's
 * Tray (see main.ts's createTray).
 *
 * The overlay window is built with plain LinearLayout/TextView/Button, not
 * a ComposeView -- hosting Compose inside a raw WindowManager-added window
 * (rather than inside an Activity/Fragment) requires manually wiring a
 * ViewTreeLifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner, a
 * known sharp edge with no device available here to verify it actually
 * works. Classic Views are the standard, battle-tested approach for this
 * exact "floating chat-head" pattern (it's how most such apps are built)
 * and carry far less lifecycle risk to ship untested. Compose is used
 * everywhere else (MainActivity, SettingsActivity) where it's the fully
 * supported case.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var settingsStore: SettingsStore
    private val transcriptBuffer = RollingTranscriptBuffer()
    private var transcriptionManager: TranscriptionManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var overlayView: View? = null
    private lateinit var statusText: TextView
    private lateinit var responseText: TextView

    private var mediaProjection: MediaProjection? = null
    private var captureManager: CaptureManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsStore = SettingsStore(this)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (mediaProjection == null && resultCode != -1 && resultData != null) {
            val projectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            captureManager = mediaProjection?.let { CaptureManager(it, resources.displayMetrics) }
        }

        if (overlayView == null) createOverlayWindow()

        if (transcriptionManager == null) {
            transcriptionManager = TranscriptionManager(this, transcriptBuffer).also { it.start() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        transcriptionManager?.stop()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        mediaProjection?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createOverlayWindow() {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(199, 20, 20, 22)) // matches Overlay.tsx's rgba(20,20,22,0.78)
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        statusText = TextView(this).apply {
            text = "wisp — idle — tap to capture"
            setTextColor(Color.argb(153, 242, 242, 242))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(statusText)

        val settingsButton = Button(this).apply {
            text = "⚙"
            textSize = 12f
            setOnClickListener {
                startActivity(
                    Intent(this@OverlayService, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        header.addView(settingsButton)
        root.addView(header)

        responseText = TextView(this).apply {
            text = "Waiting for capture..."
            setTextColor(Color.rgb(242, 242, 242))
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
        root.addView(responseText)

        val captureButton = Button(this).apply {
            text = "Capture"
            setOnClickListener { onCaptureTapped() }
        }
        root.addView(captureButton)

        val params = WindowManager.LayoutParams(
            (320 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                // The mobile equivalent of setContentProtection(true) on the
                // desktop overlay (main.ts) -- FLAG_SECURE makes THIS window
                // render as black in screenshots and screen recording/casting
                // (Meet, Zoom, etc). Genuinely untested against a real screen
                // share here -- same caveat as the desktop app's own
                // unverified setContentProtection claim (see docs/PRD.md §6.3).
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 100
        }

        makeDraggable(root, params)

        windowManager.addView(root, params)
        overlayView = root
    }

    /** Drag-to-move, since there's no window titlebar to drag by (mirrors
     * the desktop overlay's WebkitAppRegion:'drag' on its own header). */
    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchStartX).toInt()
                    params.y = startY + (event.rawY - touchStartY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun onCaptureTapped() {
        val capture = captureManager
        if (capture == null) {
            statusText.text = "wisp — no screen capture permission"
            return
        }

        serviceScope.launch {
            statusText.text = "wisp — thinking"
            responseText.text = ""
            try {
                val bitmap = capture.captureScreenshot()
                val jpegBase64 = bitmapToBase64Jpeg(bitmap)
                val settings = settingsStore.load()

                if (!settings.hasKeyConfigured()) {
                    statusText.text = "wisp — no API key set, open Settings"
                    return@launch
                }

                statusText.text = "wisp — streaming"
                val provider = getProvider(settings)
                provider.streamVisionResponse(
                    imageBase64 = jpegBase64,
                    mimeType = "image/jpeg",
                    transcriptContext = transcriptBuffer.getContext(),
                    systemPrompt = Playbooks.GENERAL_SYSTEM_PROMPT,
                    apiKey = settings.apiKeyFor(settings.provider),
                ).catch { e ->
                    responseText.append("\n\n[wisp error] ${e.message}")
                    statusText.text = "wisp — error"
                }.collect { chunk ->
                    responseText.append(chunk)
                }
                if (statusText.text.toString().contains("streaming")) statusText.text = "wisp — idle — tap to capture"
            } catch (e: Exception) {
                statusText.text = "wisp — capture failed: ${e.message}"
            }
        }
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openSettings = PendingIntent.getActivity(
            this, 0,
            Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openSettings)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "wisp_overlay"
        private const val NOTIFICATION_ID = 1
    }
}
