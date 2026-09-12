package app.cuisson.android

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * Paper rather than white, ink rather than black, and one accent.
 *
 * A recipe is read in a kitchen under warm light with greasy hands, so the page is a warm
 * off-white and the text is a soft black. Pure white on pure black is what a settings
 * screen looks like. The accent is a burnt orange, taken from food without being a
 * picture of a tomato, and it is used sparingly: step numbers, group names, one button.
 */
private val Ink = Color(0xFF1C1917)
private val InkSoft = Color(0xFF6B625B)
private val Paper = Color(0xFFFBF7F1)
private val PaperRaised = Color(0xFFFFFDFA)
private val Rule = Color(0xFFE8E0D5)
private val Ember = Color(0xFFB4451F)
private val EmberSoft = Color(0xFFF3E3DB)

private val NightInk = Color(0xFFF2EDE6)
private val NightInkSoft = Color(0xFFA79E95)
private val NightPaper = Color(0xFF15130F)
private val NightRaised = Color(0xFF1E1B17)
private val NightRule = Color(0xFF332E28)
private val NightEmber = Color(0xFFE8845C)

private val LightScheme = lightColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = EmberSoft,
    onPrimaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = InkSoft,
    outlineVariant = Rule,
    error = Color(0xFF9A3412),
)

private val DarkScheme = darkColorScheme(
    primary = NightEmber,
    onPrimary = Color(0xFF2A1008),
    primaryContainer = Color(0xFF3A1D12),
    onPrimaryContainer = NightInk,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightPaper,
    onSurface = NightInk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = NightInkSoft,
    outlineVariant = NightRule,
    error = Color(0xFFEF9A7B),
)

@Composable
fun CuissonTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalContext.current as? Activity

    // The status bar icons have to be dark on paper and light at night, or half of them
    // disappear into the background. This was on the list from phase 0.
    SideEffect {
        view?.window?.let { window ->
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = CuissonTypography,
        content = content,
    )
}
