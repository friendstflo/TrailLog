package org.mountaineers.traillog.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses field photos before local keep / Firebase Storage upload.
 * Targets ~max edge length and JPEG quality suitable for trail documentation.
 */
object PhotoCompressor {

    private const val TAG = "PhotoCompressor"
    private const val DEFAULT_MAX_EDGE = 1600
    private const val DEFAULT_QUALITY = 78

    /**
     * Writes a compressed JPEG to [dest]. Returns [dest] on success, or [source] if compression fails.
     */
    fun compress(
        source: File,
        dest: File,
        maxEdgePx: Int = DEFAULT_MAX_EDGE,
        quality: Int = DEFAULT_QUALITY
    ): File {
        if (!source.exists() || source.length() == 0L) {
            Log.w(TAG, "Source missing or empty: ${source.absolutePath}")
            return source
        }

        // Skip if already small enough (e.g. re-upload of compressed file)
        if (source.length() < 350_000L && source.absolutePath != dest.absolutePath) {
            return try {
                source.copyTo(dest, overwrite = true)
                dest
            } catch (_: Exception) {
                source
            }
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            val (srcW, srcH) = bounds.outWidth to bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return source

            val sample = calculateInSampleSize(srcW, srcH, maxEdgePx)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return source

            bitmap = applyExifOrientation(source, bitmap)

            val longest = max(bitmap.width, bitmap.height)
            if (longest > maxEdgePx) {
                val scale = maxEdgePx.toFloat() / longest
                val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            }

            if (dest.exists()) dest.delete()
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 95), out)
            }
            bitmap.recycle()

            val before = source.length()
            val after = dest.length()
            Log.d(TAG, "Compressed ${source.name}: ${before / 1024}KB → ${after / 1024}KB")
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed; using original", e)
            source
        }
    }

    /** Compress in place via temp file (replaces [source] content). */
    fun compressInPlace(source: File, maxEdgePx: Int = DEFAULT_MAX_EDGE, quality: Int = DEFAULT_QUALITY): File {
        val temp = File(source.parentFile, "${source.nameWithoutExtension}_tmp.jpg")
        val result = compress(source, temp, maxEdgePx, quality)
        if (result == temp && temp.exists()) {
            if (source.exists()) source.delete()
            temp.renameTo(source)
        } else if (temp.exists() && temp != source) {
            temp.delete()
        }
        return source
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        if (longest > maxEdge) {
            var half = longest / 2
            while (half / inSampleSize >= maxEdge) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }
}
