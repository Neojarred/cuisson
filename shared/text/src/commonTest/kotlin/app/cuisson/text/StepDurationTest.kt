package app.cuisson.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepDurationTest {

    @Test
    fun `finds the time a step tells you to wait`() {
        assertEquals(35 * 60, durationInStep("Simmer gently for 35 minutes, stirring now and then."))
        assertEquals(75 * 60, durationInStep("Put the lid on and leave it to simmer for 75 minutes."))
        assertEquals(2 * 3600, durationInStep("Cook on low for 2 hours."))
    }

    @Test
    fun `an hour and some minutes is one duration`() {
        // "1 hr 15 minutes" was being read as an hour, so the timer was fifteen minutes
        // short of the recipe.
        assertEquals(75 * 60, durationInStep("Put the lid on and leave it to simmer for 1 hr 15 minutes."))
        assertEquals(90 * 60, durationInStep("Leave it to prove for 1 hour 30 minutes."))
    }

    @Test
    fun `a following sentence is not part of the wait`() {
        assertEquals(
            20 * 60,
            durationInStep("Simmer for 20 minutes. Rest for 5 minutes before serving."),
        )
    }

    @Test
    fun `a range is set to its lower end`() {
        // "30 - 40 minutes" means check at thirty.
        assertEquals(
            30 * 60,
            durationInStep("Turn up the heat and reduce sauce for 30 - 40 minutes."),
        )
    }

    /**
     * All four were found by reading the timers a real import produced, not by thinking
     * about the regex. The lentil soup had seven steps, four of which name a time, and
     * only two of them offered a timer.
     */
    @Test
    fun `the time can be a long way from the verb`() {
        assertEquals(
            5 * 60,
            durationInStep(
                "Once the oil is shimmering, add the chopped onion and carrot and cook, " +
                    "stirring often, until the onion has softened and is turning " +
                    "translucent, about 5 minutes."
            ),
        )
    }

    @Test
    fun `five more minutes is five minutes`() {
        assertEquals(
            5 * 60,
            durationInStep("Add the chopped greens and cook for 5 more minutes."),
        )
        assertEquals(
            10 * 60,
            durationInStep("Bake for a further 10 minutes until golden."),
        )
    }

    @Test
    fun `a range written in words is still set to its lower end`() {
        // This one read the wrong end rather than missing: "5 to 10 minutes" gave ten.
        assertEquals(5 * 60, durationInStep("Simmer for 5 to 10 minutes."))
        assertEquals(20 * 60, durationInStep("Bake for 20 or 25 minutes."))
        assertEquals(30 * 60, durationInStep("Laisser cuire 30 \u00e0 40 minutes."))
    }

    @Test
    fun `a quantity before the time is not the time`() {
        // The unit is what settles it: "4 large potatoes" has no unit of time after it,
        // so the reading moves on to the number that does.
        assertEquals(
            20 * 60,
            durationInStep("Cook 4 large potatoes until tender, about 20 minutes."),
        )
    }

    @Test
    fun `a measurement is not a duration`() {
        assertNull(durationInStep("Cut the beef into 4 cm cubes."))
        assertNull(durationInStep("Add 2 tbsp of oil to the pan."))
        assertNull(durationInStep("Serves 6 people generously."))
    }

    @Test
    fun `an oven temperature is not a duration`() {
        assertNull(durationInStep("Preheat the oven to 180 degrees."))
    }

    @Test
    fun `a step with no waiting has no timer`() {
        assertNull(durationInStep("Stir to combine."))
        assertNull(durationInStep("Season with salt and pepper."))
    }

    @Test
    fun `french steps work too`() {
        assertEquals(35 * 60, durationInStep("Laisser cuire 35 minutes a feu doux."))
    }

    @Test
    fun `something absurd is not offered as a timer`() {
        assertNull(durationInStep("Leave to rest for 3 seconds."))
        assertNull(durationInStep("Cook for 40 hours."))
    }
}
