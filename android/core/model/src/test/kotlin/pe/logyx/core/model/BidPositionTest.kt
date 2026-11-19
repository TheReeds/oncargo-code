package pe.logyx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Posición aproximada (V22, 2.8): solo se muestra cuando aporta algo y nunca trae montos (RN04). */
class BidPositionTest {
    @Test fun `se muestra la posicion cuando no eres la mejor`() {
        assertEquals(BidPosition.TOP_TWO, MyBidStanding(2, 5, false, BidStandingBand.WITHIN_5_PCT, position = BidPosition.TOP_TWO).visiblePosition)
        assertEquals(BidPosition.LOWER_HALF, MyBidStanding(3, 5, false, position = BidPosition.LOWER_HALF).visiblePosition)
        assertEquals(BidPosition.UPPER_HALF, MyBidStanding(5, 5, false, position = BidPosition.UPPER_HALF).visiblePosition)
    }

    @Test fun `no se repite cuando eres la mejor`() {
        assertNull(MyBidStanding(1, 5, true, BidStandingBand.BEST, position = BidPosition.TOP_TWO).visiblePosition)
    }

    @Test fun `sin posicion o desconocida no se muestra`() {
        assertNull(MyBidStanding(2, 5, false).visiblePosition)
        assertNull(MyBidStanding(2, 5, false, position = BidPosition.UNKNOWN).visiblePosition)
    }
}
