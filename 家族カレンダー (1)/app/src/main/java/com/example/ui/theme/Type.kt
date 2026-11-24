package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val DisplayRoundedFontFamily = FontFamily(
  Font(R.font.zen_maru_gothic, FontWeight.Normal),
  Font(R.font.zen_maru_gothic, FontWeight.Bold),
)

val BodyRoundedFontFamily = FontFamily(
  Font(R.font.m_plus_rounded_1c, FontWeight.Normal),
  Font(R.font.m_plus_rounded_1c, FontWeight.Medium),
  Font(R.font.m_plus_rounded_1c, FontWeight.Bold),
)

val Typography = Typography(
  headlineLarge = TextStyle(
    fontFamily = DisplayRoundedFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = DisplayRoundedFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = DisplayRoundedFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = BodyRoundedFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = BodyRoundedFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = BodyRoundedFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 19.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = BodyRoundedFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = BodyRoundedFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
  ),
)
