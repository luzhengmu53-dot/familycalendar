package com.example.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object QrGenerator {
  fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    return try {
      val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
      )
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = matrix.width
      val height = matrix.height
      val pixels = IntArray(width * height)
      val fg = 0xFF2F2A26.toInt()
      val bg = 0xFFFFFFFF.toInt()
      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (matrix.get(x, y)) fg else bg
        }
      }
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
      }
    } catch (_: Exception) {
      null
    }
  }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF4285F4)): Color {
  return try {
    val clean = hex.trim().removePrefix("#")
    if (clean.length == 6) {
      Color(("FF$clean").toLong(16))
    } else if (clean.length == 8) {
      Color(clean.toLong(16))
    } else {
      fallback
    }
  } catch (_: Exception) {
    fallback
  }
}

fun parseHexColorInt(hex: String, fallback: Int = 0xFF4285F4.toInt()): Int {
  return try {
    val clean = hex.trim().removePrefix("#")
    if (clean.length == 6) {
      ("FF$clean").toLong(16).toInt()
    } else if (clean.length == 8) {
      clean.toLong(16).toInt()
    } else {
      fallback
    }
  } catch (_: Exception) {
    fallback
  }
}

fun formatMinutesToTime(minutes: Long): String {
  val clamped = minutes.coerceIn(0L, 1439L)
  val h = clamped / 60
  val m = clamped % 60
  return "%02d:%02d".format(Locale.US, h, m)
}

fun formatEventTimeRange(isAllDay: Boolean, startMinutes: Long, endMinutes: Long): String {
  return if (isAllDay) {
    "終日"
  } else {
    "${formatMinutesToTime(startMinutes)}〜${formatMinutesToTime(endMinutes)}"
  }
}

fun formatJapaneseMonth(yearMonth: YearMonth): String {
  return "${yearMonth.year}年${yearMonth.monthValue}月"
}

fun formatJapaneseDate(date: LocalDate): String {
  val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE)
  return "${date.monthValue}月${date.dayOfMonth}日($dow)"
}

fun buildMonthGridDays(yearMonth: YearMonth): List<LocalDate?> {
  val firstDay = yearMonth.atDay(1)
  // Sunday-start calendar (日 月 火 水 木 金 土)
  val leadingBlanks = when (firstDay.dayOfWeek) {
    DayOfWeek.SUNDAY -> 0
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
  }
  val totalDays = yearMonth.lengthOfMonth()
  val cells = MutableList<LocalDate?>(leadingBlanks) { null }
  for (day in 1..totalDays) {
    cells.add(yearMonth.atDay(day))
  }
  while (cells.size % 7 != 0) {
    cells.add(null)
  }
  // Keep consistent 6 rows (42 cells) for stable calendar & widget height
  while (cells.size < 42) {
    cells.add(null)
  }
  return cells
}
