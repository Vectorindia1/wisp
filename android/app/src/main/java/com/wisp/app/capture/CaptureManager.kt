package com.wisp.app.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One-shot screenshot via MediaProjection -- the mobile equivalent of the
 * desktop app's `desktopCapturer.getSources()` call in src/main/main.ts's
 * triggerCapture(). Unlike that Electron API, Android has no "just grab a
 * screenshot" call outside of MediaProjection, which requires the user to
 * grant a one-time consent per app-launch (handled in MainActivity) and a
 * running foreground service to hold the projection alive.
 *
 * Sets up a VirtualDisplay + ImageReader, waits for exactly one frame, then
 * tears both down immediately -- this is a discrete "take one screenshot
 * now" operation, not a continuous capture session, matching the desktop
 * app's own per-hotkey-press capture model.
 */
class CaptureManager(
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
) {
    suspend fun captureScreenshot(): Bitmap = suspendCancellableCoroutine { cont ->
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        var virtualDisplay: VirtualDisplay? = null

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image == null) return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                if (cont.isActive) cont.resume(cropped)
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            } finally {
                image.close()
                virtualDisplay?.release()
                reader.close()
            }
        }, null)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "wisp-capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null,
        )

        cont.invokeOnCancellation {
            virtualDisplay?.release()
            imageReader.close()
        }
    }
}
