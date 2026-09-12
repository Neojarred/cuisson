package app.cuisson.importer

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
    fun `a range is set to its lower end`() {
        // "30 - 40 minutes" means check at thirty.
        assertEquals(
            30 * 60,
            durationInStep("Turn up the heat and reduce sauce for 30 - 40 minutes."),
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
