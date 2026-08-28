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
