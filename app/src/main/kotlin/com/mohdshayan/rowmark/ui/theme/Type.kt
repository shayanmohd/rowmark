package com.mohdshayan.rowmark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohdshayan.rowmark.R

/*
 * Gabarito (display: drum digits, titles, buttons) and Nunito Sans (text), both bundled variable
 * fonts under res/font with their OFL licences in docs/. Weights come from the wght axis.
 */
@OptIn(ExperimentalTextApi::class)
private fun gabarito(weight: Int) = Font(
    R.font.gabarito_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito_sans_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(gabarito(500), gabarito(600), gabarito(700), gabarito(800), gabarito(900))
val Body = FontFamily(nunito(400), nunito(600), nunito(700))

/** The row drum: huge, moulded numerals. */
val DrumLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 120.sp, lineHeight = 120.sp, letterSpacing = (-2).sp)
/** The row drum on a short landscape screen. */
val DrumMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 84.sp, lineHeight = 84.sp, letterSpacing = (-1.5).sp)
val DrumSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1).sp)
val DrumChip = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)
