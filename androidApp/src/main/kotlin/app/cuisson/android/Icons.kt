package app.cuisson.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The two icons the navigation needs, drawn here rather than pulled from a dependency.
 *
 * Material's icon library is either missing the ones that matter, a cookbook among them,
 * or enormous. Two paths cost nothing and are the first of the drawings this app will
 * need anyway.
 */
private fun icon(name: String, draw: ImageVector.Builder.() -> ImageVector.Builder) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).draw().build()

private fun ImageVector.Builder.stroke(
    width: Float = 1.8f,
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) = path(
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
)

/** A list of recipes: three lines of decreasing weight, like a written list. */
val RecipesIcon: ImageVector = icon("recipes") {
    stroke { moveTo(4f, 7f); lineTo(20f, 7f) }
    stroke { moveTo(4f, 12f); lineTo(20f, 12f) }
    stroke { moveTo(4f, 17f); lineTo(14f, 17f) }
}

/** A book: a spine and two leaves. */
val CookbooksIcon: ImageVector = icon("cookbooks") {
    stroke { moveTo(12f, 6.5f); lineTo(12f, 19f) }
    stroke {
        moveTo(12f, 6.5f)
        curveTo(10.5f, 5f, 7.5f, 4.5f, 4f, 5f)
        lineTo(4f, 17.5f)
        curveTo(7.5f, 17f, 10.5f, 17.5f, 12f, 19f)
    }
    stroke {
        moveTo(12f, 6.5f)
        curveTo(13.5f, 5f, 16.5f, 4.5f, 20f, 5f)
        lineTo(20f, 17.5f)
        curveTo(16.5f, 17f, 13.5f, 17.5f, 12f, 19f)
    }
}
