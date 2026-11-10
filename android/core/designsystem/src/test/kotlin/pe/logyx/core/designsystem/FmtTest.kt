package pe.logyx.core.designsystem

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FmtTest {
    @Test fun money() {
        assertEquals("S/ 1,850.00", Fmt.money(1850.0))
        assertEquals("S/ 12,345.60", Fmt.money(12345.6))
        assertEquals("—", Fmt.money(null))
    }

    @Test fun dates() {
        assertEquals("28 nov · 08:00", Fmt.dateTime("2026-11-28T08:00:00-05:00"))
        assertEquals("28 nov · 08:00", Fmt.dateTime("2026-11-28T13:00:00Z"))
        assertEquals("28 nov", Fmt.date("2026-11-28"))
    }

    @Test fun remaining() {
        val now = Instant.parse("2026-11-10T12:00:00Z")
        assertEquals("12 min", Fmt.remaining("2026-11-10T12:12:00Z", now))
        assertEquals("30 s", Fmt.remaining("2026-11-10T12:00:30Z", now))
        assertNull(Fmt.remaining("2026-11-10T11:59:00Z", now))
    }

    @Test fun weights() {
        assertEquals("3.2 t", Fmt.tons(3200.0))
        assertEquals("1,010 km", Fmt.km(1010.4))
    }
}
