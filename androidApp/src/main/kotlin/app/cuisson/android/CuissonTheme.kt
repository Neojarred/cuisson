package app.cuisson.android

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * Material 3 Expressive, with the device's own colours where it has them.
 *
 * Deliberately borrowed rather than invented. Cuisson has three screens, which is not
 * enough to design an app from, and a coherent system we did not write beats a
 * half-finished one we did. Our own look comes back once there is an app to look at.
 *
 * Dynamic colour also settles a real problem: an accent we choose competes with Mealie,
 * whose default primary is an orange too. An accent taken from the user's wallpaper
 * belongs to them and competes with nothing.
 *
 * The one thing kept from the first pass is Fraunces on the recipe titles, because that
 * is the part that stops a list of recipes reading like a list of settings.
 */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF8A4B2A),
    background = Color(0xFFFBF7F1),
    surface = Color(0xFFFBF7F1),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFE8A17C),
    background = Color(0xFF15130F),
    surface = Color(0xFF15130F),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CuissonTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> FallbackDark
        else -> FallbackLight
    }

    // Status bar icons have to be dark on a light background and light on a dark one, or
    // half of them vanish. window.statusBarColor is ignored from Android 15, so the
    // surface paints under the bar and only the icon appearance is set here.
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = CuissonTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
