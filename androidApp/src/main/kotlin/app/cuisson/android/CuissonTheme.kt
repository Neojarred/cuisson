package app.cuisson.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Deliberately plain. The look is decided in phase 2, with real recipes on a real screen,
 * and anything invented here would only have to be argued with later.
 */
@Composable
fun CuissonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(), content = content)
}
