package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

object AvatarImageUtils {
  private const val AVATAR_PX = 160
  private const val MAX_BASE64_LENGTH = 45_000

  suspend fun encodeUriToAvatarBase64(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        val rawBitmap = decodeUriBitmap(context, uri) ?: return@withContext null
        val square = centerCropAndScale(rawBitmap, AVATAR_PX)
        if (square !== rawBitmap) {
          rawBitmap.recycle()
        }
        val encoded = compressToBase64(square)
        square.recycle()
        encoded
      }.getOrNull()
    }

  fun decodeBase64ToBitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return runCatching {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
  }

  private fun decodeUriBitmap(context: Context, uri: Uri): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(context.contentResolver, uri)
      ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        if (w > 0 && h > 0) {
          val minDim = min(w, h)
          if (minDim > AVATAR_PX * 2) {
            val scale = (AVATAR_PX * 2).toFloat() / minDim.toFloat()
            decoder.setTargetSize(
              (w * scale).toInt().coerceAtLeast(1),
              (h * scale).toInt().coerceAtLeast(1),
            )
          }
        }
      }
    } else {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
      }
      val minDim = min(bounds.outWidth, bounds.outHeight)
      var sampleSize = 1
      while (minDim / (sampleSize * 2) >= AVATAR_PX) {
        sampleSize *= 2
      }
      val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
      }
    }
  }

  internal fun centerCropAndScale(source: Bitmap, targetSize: Int): Bitmap {
    val size = min(source.width, source.height).coerceAtLeast(1)
    val left = ((source.width - size) / 2).coerceAtLeast(0)
    val top = ((source.height - size) / 2).coerceAtLeast(0)
    val cropped = Bitmap.createBitmap(source, left, top, size, size)
    return if (cropped.width == targetSize && cropped.height == targetSize) {
      cropped
    } else {
      val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
      if (scaled !== cropped) {
        cropped.recycle()
      }
      scaled
    }
  }

  internal fun compressToBase64(bitmap: Bitmap): String? {
    val qualities = listOf(78, 64, 48, 32)
    for (q in qualities) {
      val baos = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, q, baos)
      val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
      if (base64.length <= MAX_BASE64_LENGTH) {
        return base64
      }
    }
    return null
  }
}
