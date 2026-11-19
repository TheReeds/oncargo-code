package pe.logyx.notification.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;
import pe.logyx.notification.domain.model.NotificationType;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPreferencePolicyTest {

    @Test
    void porOmisionTodoLlegaALaAppYAlCorreoSoloLoImportante() {
        for (NotificationCategory c : NotificationCategory.values()) {
            assertTrue(NotificationPreferencePolicy.defaults(c).inApp(), c.name());
        }
        assertTrue(NotificationPreferencePolicy.defaults(NotificationCategory.AWARDS).email());
        assertTrue(NotificationPreferencePolicy.defaults(NotificationCategory.SHIPMENTS).email());
        assertTrue(NotificationPreferencePolicy.defaults(NotificationCategory.ACCOUNT).email());
        assertFalse(NotificationPreferencePolicy.defaults(NotificationCategory.BIDS).email());
        assertFalse(NotificationPreferencePolicy.defaults(NotificationCategory.NEGOTIATION).email());
    }

    @Test
    void loGuardadoGanaYLoQueFaltaEsElValorPorDefecto() {
        var stored = Map.of(NotificationCategory.BIDS, new NotificationPreference(NotificationCategory.BIDS, false, true));
        var eff = NotificationPreferencePolicy.effective(stored);
        assertEquals(NotificationCategory.values().length, eff.size());
        assertFalse(eff.get(NotificationCategory.BIDS).inApp());
        assertTrue(eff.get(NotificationCategory.BIDS).email());
        assertEquals(NotificationPreferencePolicy.defaults(NotificationCategory.TRIPS), eff.get(NotificationCategory.TRIPS));
        assertEquals(NotificationCategory.values().length, NotificationPreferencePolicy.effective(null).size());
    }

    @Test
    void cuentaYAccesosSiempreLleganALaApp() {
        var ex = assertThrows(BusinessException.class, () -> NotificationPreferencePolicy.validate(
                new NotificationPreference(NotificationCategory.ACCOUNT, false, false)));
        assertEquals("LOGYX-NTF-001", ex.code());
        assertEquals("inApp", ex.field());
        assertDoesNotThrow(() -> NotificationPreferencePolicy.validate(new NotificationPreference(NotificationCategory.ACCOUNT, true, false)));
        assertDoesNotThrow(() -> NotificationPreferencePolicy.validate(new NotificationPreference(NotificationCategory.BIDS, false, false)));
    }

    @Test
    void cadaTipoTieneCategoriaYCadaCategoriaAplicaAAlgunTipoDeCuenta() {
        Arrays.stream(NotificationType.values()).forEach(t -> assertNotNull(NotificationCategory.of(t), t.name()));
        for (NotificationCategory c : NotificationCategory.values()) {
            assertTrue(Arrays.stream(OrgType.values()).anyMatch(c::appliesTo), c.name());
        }
        assertTrue(NotificationCategory.AWARDS.appliesTo(OrgType.CARRIER));
        assertFalse(NotificationCategory.AWARDS.appliesTo(OrgType.PYME));
        assertEquals(NotificationCategory.ACCOUNT, NotificationCategory.of(NotificationType.ACCESS_REQUESTED));
    }
}
