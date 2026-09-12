package app.cuisson.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepSplitterTest {

    @Test
    fun `a short step is left exactly as written`() {
        val step = "Bring to a simmer, then turn the heat down low."
        assertEquals(listOf(step), StepSplitter.split(step))
    }

    @Test
    fun `add the flour stir is never two cards`() {
        // The case that would make splitting worse than the wall of text.
        val step = "Add the flour. Stir."
        assertEquals(listOf(step), StepSplitter.split(step))
    }

    @Test
    fun `a dense paragraph becomes cards`() {
        // Bon Appetit's house style: five actions in one step.
        val step = "Stir the seasoning and cocoa powder in a small bowl to combine. " +
            "Sprinkle the mixture all over the chicken thighs and rub it in well with " +
            "your hands. Heat two tablespoons of the oil in a large skillet over a " +
            "medium-high heat until it shimmers. Working in batches, cook the thighs " +
            "until deeply browned underneath, about five minutes."
        val cards = StepSplitter.split(step)
        assertTrue(cards.size >= 3, "expected several cards, got ${cards.size}")
        assertTrue(cards.first().startsWith("Stir the seasoning"))
        assertTrue(cards.last().endsWith("about five minutes."))
    }

    @Test
    fun `nothing is lost or invented`() {
        val step = "Warm the oil gently in a wide pan over a low heat until it moves " +
            "easily. Add the sliced garlic and let it soften without colouring. " +
            "Crush the tomatoes into the pan and season them well with salt."
        val rejoined = StepSplitter.split(step).joinToString(" ")
        assertEquals(step, rejoined)
    }

    @Test
    fun `an aside stays with the instruction it belongs to`() {
        val step = "Put the lid on the pot and leave it to simmer for one hour and " +
            "fifteen minutes without lifting it. Do not stir. The sauce will catch " +
            "on the bottom of the pan if you are impatient with the heat."
        val cards = StepSplitter.split(step)
        assertTrue(cards.none { it.trim() == "Do not stir." }, "a two-word card is an aside")
    }

    @Test
    fun `a decimal is not the end of a sentence`() {
        val step = "Weigh out 1.5 kg of beef and cut it into cubes of about 4 cm, " +
            "trimming off any large pieces of gristle as you go along the way. " +
            "Season the cubes all over with plenty of salt and set them aside."
        assertTrue(StepSplitter.split(step).none { it.startsWith("5 kg") })
    }

    @Test
    fun `an abbreviation is not the end of a sentence`() {
        val step = "Measure 2 tbsp. of the spice paste into the pan and fry it gently " +
            "for two or three minutes until the rawness has cooked off it entirely. " +
            "Add the remaining ingredients and stir them through."
        assertTrue(StepSplitter.split(step).none { it.startsWith("of the spice paste") })
    }

    @Test
    fun `splitting can be turned off`() {
        val step = "A very long step. ".repeat(20)
        assertEquals(1, StepSplitter.split(step, enabled = false).size)
    }
}
