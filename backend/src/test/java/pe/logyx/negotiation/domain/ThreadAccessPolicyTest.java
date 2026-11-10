package pe.logyx.negotiation.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.negotiation.domain.ThreadAccessPolicy.RequestState;
import pe.logyx.shared.domain.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class ThreadAccessPolicyTest {

    @Test
    void publicadaAbiertaAdjudicadaSoloConElGanadorCanceladaCerrada() {
        assertDoesNotThrow(() -> ThreadAccessPolicy.ensureCanPost(RequestState.PUBLISHED, false));
        assertDoesNotThrow(() -> ThreadAccessPolicy.ensureCanPost(RequestState.MATCHED, true));
        assertEquals("LOGYX-NEG-002", assertThrows(BusinessException.class,
                () -> ThreadAccessPolicy.ensureCanPost(RequestState.MATCHED, false)).code());
        assertEquals("LOGYX-NEG-002", assertThrows(BusinessException.class,
                () -> ThreadAccessPolicy.ensureCanPost(RequestState.CANCELLED, true)).code());
    }
}
