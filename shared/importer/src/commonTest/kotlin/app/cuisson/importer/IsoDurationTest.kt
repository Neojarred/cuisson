package app.cuisson.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoDurationTest {

    @Test
    fun `reads hours and minutes`() {
        assertEquals(75, parseIsoDurationMinutes("PT1H15M"))
        assertEquals(125, parseIsoDurationMinutes("PT2H5M"))
    }

    @Test
    fun `reads minutes that were never normalised into hours`() {
        // Observed on a real site: a three and a half hour recipe published as PT200M.
        assertEquals(200, parseIsoDurationMinutes("PT200M"))
    }

    @Test
    fun `reads hours alone and minutes alone`() {
        assertEquals(60, parseIsoDurationMinutes("PT1H"))
        assertEquals(30, parseIsoDurationMinutes("PT30M"))
    }

    @Test
    fun `reads days for things that prove or cure`() {
        assertEquals(2 * 24 * 60 + 180, parseIsoDurationMinutes("P2DT3H"))
    }

    @Test
    fun `ignores seconds rather than rounding a minute up`() {
        assertEquals(30, parseIsoDurationMinutes("PT30M45S"))
    }

    @Test
    fun `returns null rather than guessing`() {
        assertNull(parseIsoDurationMinutes(null))
        assertNull(parseIsoDurationMinutes(""))
        assertNull(parseIsoDurationMinutes("about an hour"))
        assertNull(parseIsoDurationMinutes("PT"))
        assertNull(parseIsoDurationMinutes("1:15"))
    }
}

/**
 * Every case here was found by running the extractor over real pages, not by reading the
 * schema.org specification, which none of these publishers followed.
 */
class RealWorldDurationTest {

    @Test
    fun `a duration given entirely in seconds is not thrown away`() {
        // The Kitchn publishes PT2400S. An earlier version of this parser scored it zero.
        assertEquals(40, parseDurationMinutes("PT2400S"))
    }

    @Test
    fun `plain english durations are read`() {
        // Bon Appetit and Epicurious both ignore ISO 8601 entirely.
        assertEquals(35, parseDurationMinutes("35 minutes"))
        assertEquals(20, parseDurationMinutes("20 minutes"))
        assertEquals(90, parseDurationMinutes("1 hour 30 minutes"))
        assertEquals(75, parseDurationMinutes("1 hr 15 min"))
        assertEquals(60, parseDurationMinutes("1 hour"))
    }

    @Test
    fun `french durations are read`() {
        assertEquals(45, parseDurationMinutes("45 minutes"))
        assertEquals(90, parseDurationMinutes("1 heure 30 minutes"))
        assertEquals(90, parseDurationMinutes("1 h 30"))
    }

    @Test
    fun `iso is still preferred and still works`() {
        assertEquals(75, parseDurationMinutes("PT1H15M"))
        assertEquals(200, parseDurationMinutes("PT200M"))
    }

    @Test
    fun `prose that merely contains a number is not a duration`() {
        assertNull(parseDurationMinutes("serves 4 people"))
        assertNull(parseDurationMinutes("about an hour, give or take"))
        assertNull(parseDurationMinutes(""))
        assertNull(parseDurationMinutes(null))
    }
}
