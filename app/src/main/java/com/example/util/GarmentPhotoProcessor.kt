package com.example.util

import android.graphics.Bitmap
import android.util.Log
import com.example.data.ai.AiStylistService
import com.example.data.model.ClosetItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns a garment photo into a clean, centred product cutout with a transparent background,
 * like an online-shop grid:
 *  1. optionally re-renders it as a studio product photo with the Gemini image model,
 *  2. removes the background on-device with ML Kit subject segmentation,
 *  3. trims and centres it in a square.
 */
object GarmentPhotoProcessor {
    private const val TAG = "GarmentPhoto"

    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        )
    }

    /** Returns the cutout as WebP base64 ready to store, or null if no clean cutout could be made. */
    suspend fun makeCutoutBase64(
        photo: Bitmap?,
        item: ClosetItem,
        useAiStudioRender: Boolean,
        ai: AiStylistService
    ): String? {
        val rendered = if (useAiStudioRender) ai.renderStudioPhoto(photo, item) else null
        val source = rendered ?: photo ?: return null
        val isRender = rendered != null

        val cut = segment(source)
            ?: if (isRender) withContext(Dispatchers.Default) { ImageUtils.plainBackgroundToAlpha(source) } else null
        if (cut == null || !looksLikeCleanCutout(cut)) {
            Log.w(TAG, "No clean cutout (render=$isRender)")
            return null
        }
        return withContext(Dispatchers.Default) {
            ImageUtils.encodeCutoutToBase64(ImageUtils.trimToContentSquare(cut))
        }.ifBlank { null }
    }

    private suspend fun segment(source: Bitmap): Bitmap? {
        return try {
            val input = ImageUtils.flattenOnWhite(ImageUtils.scaleDownBitmap(source, 1024))
            withTimeoutOrNull(20_000L) {
                segmenter.process(InputImage.fromBitmap(input, 0)).await().foregroundBitmap
            }
        } catch (e: Exception) {
            // Happens when Google Play services hasn't downloaded the model yet, or isn't available.
            Log.w(TAG, "Subject segmentation unavailable: ${e.message}")
            null
        }
    }

    /** Rejects masks that kept almost nothing or almost everything. */
    private fun looksLikeCleanCutout(bmp: Bitmap): Boolean {
        val small = ImageUtils.scaleDownBitmap(bmp, 128)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val visible = px.count { (it ushr 24) > 40 }.toFloat() / px.size
        return visible in 0.03f..0.97f
    }
}
