package pe.logyx.shipment.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.ShipmentStop.Type;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryEvidencePolicyTest {

    @Test
    void laEntregaExigeFotoElRecojoNo() {
        assertEquals("LOGYX-TRK-006", assertThrows(BusinessException.class,
                () -> DeliveryEvidencePolicy.ensureEvidence(Type.DELIVERY, false)).code());
        assertDoesNotThrow(() -> DeliveryEvidencePolicy.ensureEvidence(Type.DELIVERY, true));
        assertDoesNotThrow(() -> DeliveryEvidencePolicy.ensureEvidence(Type.PICKUP, false));
    }
}
