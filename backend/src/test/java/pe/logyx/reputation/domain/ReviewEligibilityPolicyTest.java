package pe.logyx.reputation.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class ReviewEligibilityPolicyTest {

    private static String code(boolean delivered, boolean photo, boolean reviewed) {
        return assertThrows(BusinessException.class,
                () -> ReviewEligibilityPolicy.ensureCanReview(delivered, photo, reviewed)).code();
    }

    @Test
    void rn06ExigeEntregaYFotoUnaVez() {
        assertEquals("LOGYX-REV-001", code(false, true, false));
        assertEquals("LOGYX-REV-002", code(true, false, false));
        assertEquals("LOGYX-REV-003", code(true, true, true));
        assertDoesNotThrow(() -> ReviewEligibilityPolicy.ensureCanReview(true, true, false));
        assertTrue(ReviewEligibilityPolicy.blocker(true, true, false).isEmpty());
    }
}
