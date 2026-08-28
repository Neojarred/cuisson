package app.cuisson.android

import app.cuisson.domain.Extraction
import app.cuisson.domain.ExtractionTier
import app.cuisson.domain.IngredientLine
import app.cuisson.domain.MeasureUnit
import app.cuisson.domain.QuantityRange
import app.cuisson.domain.Recipe
import app.cuisson.domain.RecipeId
import app.cuisson.domain.Servings
import app.cuisson.domain.Source
import app.cuisson.domain.SourceKind
import app.cuisson.domain.Step
import app.cuisson.domain.Timings
import app.cuisson.domain.UnitSystem
import kotlinx.datetime.Clock

/**
 * The one recipe phase 0 puts on screen, written for this purpose rather than copied from
 * a site.
 *
 * Its ingredient lines carry the extraction hazards listed in section 3 of the
 * architecture, so the screen shows from the first commit what the parser will have to
 * survive: a dual unit line, a quantity range, a unicode fraction, and a step that points
 * at a note nobody captured.
 */
internal fun seedRecipe(): Recipe {
    val now = Clock.System.now()
    val id = RecipeId("seed-tomato-sauce")
    return Recipe(
        id = id,
        title = "Tomato sauce worth keeping",
        source = Source(kind = SourceKind.MANUAL, name = "Written by hand"),
        servings = Servings(4.0),
        timings = Timings(prepMinutes = 5, cookMinutes = 40, totalMinutes = 45),
        ingredients = listOf(
            IngredientLine(
                id = "seed-i1",
                position = 0,
                rawText = "400 ml / 14 oz tinned plum tomatoes",
                quantity = QuantityRange(400.0),
                unit = MeasureUnit(
                    canonical = "ml",
                    system = UnitSystem.METRIC,
                    alternate = MeasureUnit("oz", UnitSystem.IMPERIAL),
                ),
                itemText = "tinned plum tomatoes",
                parseConfidence = 0.9f,
            ),
            IngredientLine(
                id = "seed-i2",
                position = 1,
                rawText = "2 to 3 garlic cloves, thinly sliced",
                quantity = QuantityRange(2.0, 3.0),
                unit = MeasureUnit("clove", UnitSystem.COUNT),
                itemText = "garlic",
                preparation = "thinly sliced",
                parseConfidence = 0.85f,
            ),
            IngredientLine(
                id = "seed-i3",
                position = 2,
                rawText = "3 tbsp olive oil",
                quantity = QuantityRange(3.0),
                unit = MeasureUnit("tbsp", UnitSystem.METRIC),
                itemText = "olive oil",
                parseConfidence = 0.95f,
            ),
            IngredientLine(
                id = "seed-i4",
                position = 3,
                rawText = "a pinch of sugar, if the tomatoes are sharp (Note 1)",
                itemText = "sugar",
                optional = true,
                parseConfidence = 0.2f,
            ),
            IngredientLine(
                id = "seed-i5",
                position = 4,
                rawText = "½ tsp salt",
                quantity = QuantityRange(0.5),
                unit = MeasureUnit("tsp", UnitSystem.METRIC),
                itemText = "salt",
                parseConfidence = 0.95f,
            ),
        ),
        steps = listOf(
            Step(
                id = "seed-s1",
                position = 0,
                text = "Warm the oil in a wide pan over a low heat and add the garlic. " +
                    "Let it soften for 2 minutes without colouring.",
                durationSeconds = 120,
            ),
            Step(
                id = "seed-s2",
                position = 1,
                text = "Add the tomatoes, crushing them against the side of the pan. " +
                    "Simmer gently for 35 minutes, stirring now and then.",
                durationSeconds = 2100,
            ),
            Step(
                id = "seed-s3",
                position = 2,
                text = "Season with salt, and sugar if it needs it (see Note 1).",
                references = listOf("Note 1"),
            ),
        ),
        notes = null,
        language = "en",
        extraction = Extraction(
            tier = ExtractionTier.HAND_WRITTEN,
            confidence = 1.0f,
            needsReview = false,
        ),
        createdAt = now,
        updatedAt = now,
    )
}
