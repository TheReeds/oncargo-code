package pe.logyx.settlement.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.settlement.domain.model.PayoutKind;
import pe.logyx.shared.domain.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class PayoutAccountPolicyTest {

    @Test
    void cciDe20DigitosYCelularDe9() {
        assertEquals("00219300123456789012", PayoutAccountPolicy.normalizeNumber(PayoutKind.BANK, "002-193 00123456789012"));
        assertEquals("987654321", PayoutAccountPolicy.normalizeNumber(PayoutKind.YAPE, "+51 987 654 321"));
        for (String bad : new String[]{"123", "0021930012345678901", "abc"}) {
            assertEquals("LOGYX-STL-007", assertThrows(BusinessException.class,
                    () -> PayoutAccountPolicy.normalizeNumber(PayoutKind.BANK, bad)).code());
        }
        assertThrows(BusinessException.class, () -> PayoutAccountPolicy.normalizeNumber(PayoutKind.PLIN, "887654321"));
        assertThrows(BusinessException.class, () -> PayoutAccountPolicy.bank(PayoutKind.BANK, " "));
        assertNull(PayoutAccountPolicy.bank(PayoutKind.YAPE, "BCP"));
        assertThrows(BusinessException.class, () -> PayoutAccountPolicy.ensureRoom(PayoutAccountPolicy.MAX_ACTIVE));
    }
}
