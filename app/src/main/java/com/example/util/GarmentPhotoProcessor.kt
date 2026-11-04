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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a garment photo into a clean, centred product cutout with a transparent background,
 * like an online-shop grid:
 *  1. optionally re-renders it as a studio product photo with the Gemini image model,
 *  2. finds the garment on-device with ML Kit subject segmentation (per-pixel confidence),
 *  3. refines the mask: soft edges, no stray specks, no pinholes, no background-coloured fringe,
 *  4. trims and centres it in a square.
 */
object GarmentPhotoProcessor {
    private const val TAG = "GarmentPhoto"

    /** Segmentation runs at this size; bigger = finer edges but slower. */
    private const val WORK_SIZE = 1536

    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
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

        val work = withContext(Dispatchers.Default) {
            ImageUtils.flattenOnWhite(ImageUtils.scaleDownBitmap(source, WORK_SIZE))
        }
        val confidence = segment(work)
        val cut = withContext(Dispatchers.Default) {
            when {
                confidence != null -> refineCutout(work, confidence)
                isRender -> ImageUtils.plainBackgroundToAlpha(work)
                else -> null
            }
        }
        if (cut == null || !looksLikeCleanCutout(cut)) {
            Log.w(TAG, "No clean cutout (render=$isRender, mask=${confidence != null})")
            return null
        }
        return withContext(Dispatchers.Default) {
            ImageUtils.encodeCutoutToBase64(ImageUtils.trimToContentSquare(cut))
        }.ifBlank { null }
    }

    /** Per-pixel foreground confidence (0..1) for [input], or null if ML Kit is unavailable. */
    private suspend fun segment(input: Bitmap): FloatArray? {
        return try {
            withTimeoutOrNull(25_000L) {
                val result = segmenter.process(InputImage.fromBitmap(input, 0)).await()
                val buffer = result.foregroundConfidenceMask ?: return@withTimeoutOrNull null
                buffer.rewind()
                val out = FloatArray(input.width * input.height)
                if (buffer.remaining() < out.size) return@withTimeoutOrNull null
                buffer.get(out)
                out
            }
        } catch (e: Exception) {
            // Happens when Google Play services hasn't downloaded the model yet, or isn't available.
            Log.w(TAG, "Subject segmentation unavailable: ${e.message}")
            null
        }
    }

    /**
     * Builds a clean RGBA cutout from the photo and ML Kit's confidence mask.
     */
    internal fun refineCutout(src: Bitmap, confidence: FloatArray): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // 1. Confidence -> alpha with a smooth ramp instead of a hard threshold.
        val alpha = FloatArray(n) { smoothstep(0.25f, 0.75f, confidence[it]) }

        // 2. Drop stray specks: keep only blobs that are a meaningful size compared with the largest
        //    (a pair of shoes stays two blobs).
        val solid = BooleanArray(n) { alpha[it] > 0.5f }
        val labels = IntArray(n)
        val sizes = labelComponents(solid, w, h, labels)
        val largest = sizes.maxOrNull() ?: 0
        if (largest == 0) return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val minKeep = max(64, (largest * 0.03f).toInt())
        for (i in 0 until n) {
            val l = labels[i]
            if (l > 0 && sizes[l - 1] < minKeep) alpha[i] = 0f
        }
        // Also clear soft pixels that aren't near any kept blob.
        val nearKept = dilate(BooleanArray(n) { labels[it] > 0 && sizes[labels[it] - 1] >= minKeep }, w, h, radius(w, 3))
        for (i in 0 until n) if (!nearKept[i]) alpha[i] = 0f

        // 3. Fill pinholes inside the garment (but keep real gaps such as between trouser legs).
        val holes = BooleanArray(n) { alpha[it] <= 0.5f }
        val holeLabels = IntArray(n)
        val holeSizes = labelComponents(holes, w, h, holeLabels)
        val touchesBorder = BooleanArray(holeSizes.size)
        for (x in 0 until w) {
            markBorder(holeLabels[x], touchesBorder)
            markBorder(holeLabels[(h - 1) * w + x], touchesBorder)
        }
        for (y in 0 until h) {
            markBorder(holeLabels[y * w], touchesBorder)
            markBorder(holeLabels[y * w + w - 1], touchesBorder)
        }
        val maxHole = (n * 0.0015f).toInt()
        for (i in 0 until n) {
            val l = holeLabels[i]
            if (l > 0 && !touchesBorder[l - 1] && holeSizes[l - 1] <= maxHole) alpha[i] = 1f
        }

        // 4. Pull the edge in ~1px (removes the halo of background) and feather it slightly.
        val r = radius(w, 1)
        val eroded = minFilter(alpha, w, h, r)
        val soft = boxBlur(eroded, w, h, r)

        // 5. Colour decontamination: remove the background colour that bleeds into edge pixels.
        var bgR = 0.0
        var bgG = 0.0
        var bgB = 0.0
        var bgCount = 0
        val edgeBand = dilate(BooleanArray(n) { soft[it] > 0.02f }, w, h, radius(w, 6))
        for (i in 0 until n) {
            if (edgeBand[i] && soft[i] <= 0.02f) {
                val c = px[i]
                bgR += (c shr 16) and 0xFF
                bgG += (c shr 8) and 0xFF
                bgB += c and 0xFF
                bgCount++
            }
        }
        val hasBg = bgCount > 50
        val br = if (hasBg) (bgR / bgCount).toFloat() else 0f
        val bg = if (hasBg) (bgG / bgCount).toFloat() else 0f
        val bb = if (hasBg) (bgB / bgCount).toFloat() else 0f

        val out = IntArray(n)
        for (i in 0 until n) {
            val a = soft[i]
            if (a <= 0.004f) {
                out[i] = 0
                continue
            }
            val c = px[i]
            var rr = ((c shr 16) and 0xFF).toFloat()
            var gg = ((c shr 8) and 0xFF).toFloat()
            var bl = (c and 0xFF).toFloat()
            if (hasBg && a < 0.97f) {
                val ea = max(a, 0.2f)
                rr = (rr - (1f - ea) * br) / ea
                gg = (gg - (1f - ea) * bg) / ea
                bl = (bl - (1f - ea) * bb) / ea
            }
            val ai = (a * 255f).roundToInt().coerceIn(0, 255)
            out[i] = (ai shl 24) or
                (rr.roundToInt().coerceIn(0, 255) shl 16) or
                (gg.roundToInt().coerceIn(0, 255) shl 8) or
                bl.roundToInt().coerceIn(0, 255)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun markBorder(label: Int, touches: BooleanArray) {
        if (label > 0) touches[label - 1] = true
    }

    /** Scales a pixel radius to the working resolution (tuned at ~1500px). */
    private fun radius(width: Int, base: Int): Int = max(1, (base * width / 1500f).roundToInt())

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 4-connected component labelling. Labels are 1-based; returns each component's size. */
    private fun labelComponents(mask: BooleanArray, w: Int, h: Int, labels: IntArray): IntArray {
        val sizes = ArrayList<Int>()
        val queue = IntArray(mask.size)
        var next = 0
        for (start in mask.indices) {
            if (!mask[start] || labels[start] != 0) continue
            next++
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = next
            var count = 0
            while (head < tail) {
                val i = queue[head++]
                count++
                val x = i % w
                val y = i / w
                if (x > 0 && mask[i - 1] && labels[i - 1] == 0) { labels[i - 1] = next; queue[tail++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && labels[i + 1] == 0) { labels[i + 1] = next; queue[tail++] = i + 1 }
                if (y > 0 && mask[i - w] && labels[i - w] == 0) { labels[i - w] = next; queue[tail++] = i - w }
                if (y < h - 1 && mask[i + w] && labels[i + w] == 0) { labels[i + w] = next; queue[tail++] = i + w }
            }
            sizes.add(count)
        }
        return sizes.toIntArray()
    }

    /** Separable square dilation of a boolean mask. */
    private fun dilate(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val tmp = BooleanArray(mask.size)
        for (y in 0 until h) {
            var lastOn = -1_000_000
            val row = y * w
            for (x in 0 until w) {
                if (mask[row + x]) lastOn = x
                if (x - lastOn <= r) tmp[row + x] = true
            }
            lastOn = 1_000_000
            for (x in w - 1 downTo 0) {
                if (mask[row + x]) lastOn = x
                if (lastOn - x <= r) tmp[row + x] = true
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until w) {
            var lastOn = -1_000_000
            for (y in 0 until h) {
                if (tmp[y * w + x]) lastOn = y
                if (y - lastOn <= r) out[y * w + x] = true
            }
            lastOn = 1_000_000
            for (y in h - 1 downTo 0) {
                if (tmp[y * w + x]) lastOn = y
                if (lastOn - y <= r) out[y * w + x] = true
            }
        }
        return out
    }

    /** Separable min filter (grey erosion) with radius [r]. */
    private fun minFilter(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(a.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = 1f
                for (k in max(0, x - r)..min(w - 1, x + r)) m = min(m, a[row + k])
                tmp[row + x] = m
            }
        }
        val out = FloatArray(a.size)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var m = 1f
                for (k in max(0, y - r)..min(h - 1, y + r)) m = min(m, tmp[k * w + x])
                out[y * w + x] = m
            }
        }
        return out
    }

    /** Separable box blur with radius [r]. */
    private fun boxBlur(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(a.size)
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += a[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / (2 * r + 1)
                sum += a[row + (x + r + 1).coerceIn(0, w - 1)] - a[row + (x - r).coerceIn(0, w - 1)]
            }
        }
        val out = FloatArray(a.size)
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / (2 * r + 1)
                sum += tmp[(y + r + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - r).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
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
