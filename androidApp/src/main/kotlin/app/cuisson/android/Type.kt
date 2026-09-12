package app.cuisson.android

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two families, bundled in the app.
 *
 * Bundled rather than fetched, because Google's downloadable fonts need Play services and
 * Cuisson has to look the same on a phone that has none.
 *
 * Fraunces carries the recipe titles. It is a serif with some warmth to it, and it is the
 * single thing that stops a list of recipes looking like a list of settings. Inter does
 * everything else and stays out of the way, because ingredients and method are read while
 * holding a knife.
 */
private fun frauncesAt(weight: Int, soft: Float = 20f, wonk: Float = 1f) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("SOFT", soft),
        FontVariation.Setting("WONK", wonk),
        FontVariation.Setting("opsz", 48f),
    ),
)

private fun sansAt(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", 16f),
    ),
)

val Display = FontFamily(frauncesAt(400), frauncesAt(600), frauncesAt(700))
val Sans = FontFamily(sansAt(400), sansAt(500), sansAt(600), sansAt(700))

val CuissonTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight(600),
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight(600),
        fontSize = 28.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight(600),
        fontSize = 23.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight(600),
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight(600),
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight(400),
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight(400),
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    /** Small caps for the quiet labels: group names, section headings, timers. */
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight(600),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight(500),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight(500),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)
