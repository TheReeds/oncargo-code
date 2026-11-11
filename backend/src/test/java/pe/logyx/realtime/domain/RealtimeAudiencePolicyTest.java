package pe.logyx.realtime.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.realtime.domain.model.Recipient;
import pe.logyx.realtime.domain.model.Subscriber;
import pe.logyx.shared.tenancy.OrgType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeAudiencePolicyTest {

    private final UUID pymeOrg = UUID.randomUUID();
    private final UUID otherPymeOrg = UUID.randomUUID();
    private final UUID carrierOrg = UUID.randomUUID();
    private final UUID driverUser = UUID.randomUUID();

    private final Subscriber pyme = new Subscriber(UUID.randomUUID(), pymeOrg, OrgType.PYME, false);
    private final Subscriber otherPyme = new Subscriber(UUID.randomUUID(), otherPymeOrg, OrgType.PYME, false);
    private final Subscriber carrier = new Subscriber(UUID.randomUUID(), carrierOrg, OrgType.CARRIER, false);
    private final Subscriber driver = new Subscriber(driverUser, carrierOrg, OrgType.CARRIER, true);
    private final Subscriber otherDriver = new Subscriber(UUID.randomUUID(), carrierOrg, OrgType.CARRIER, true);

    @Test
    void eventoDeOrganizacionSoloASusUsuariosNoConductores() {
        Recipient r = Recipient.org(pymeOrg);
        assertTrue(RealtimeAudiencePolicy.matches(r, pyme));
        assertFalse(RealtimeAudiencePolicy.matches(r, otherPyme));
        assertFalse(RealtimeAudiencePolicy.matches(r, carrier));
        assertFalse(RealtimeAudiencePolicy.matches(Recipient.org(carrierOrg), driver), "el conductor no recibe lo de la org");
        assertTrue(RealtimeAudiencePolicy.matches(Recipient.org(carrierOrg), carrier));
    }

    @Test
    void eventoDeUsuarioSoloAEseUsuarioDeEsaOrganizacion() {
        Recipient r = Recipient.user(carrierOrg, driverUser);
        assertTrue(RealtimeAudiencePolicy.matches(r, driver));
        assertFalse(RealtimeAudiencePolicy.matches(r, otherDriver));
        assertFalse(RealtimeAudiencePolicy.matches(r, carrier));
        // Mismo id de usuario pero otra organización (token manipulado no es posible, pero fail-closed)
        assertFalse(RealtimeAudiencePolicy.matches(Recipient.user(UUID.randomUUID(), driverUser), driver));
    }

    @Test
    void difusionPorTipoSinConductores() {
        Recipient r = Recipient.broadcast(OrgType.CARRIER);
        assertTrue(RealtimeAudiencePolicy.matches(r, carrier));
        assertFalse(RealtimeAudiencePolicy.matches(r, driver));
        assertFalse(RealtimeAudiencePolicy.matches(r, pyme));
        assertFalse(RealtimeAudiencePolicy.matches(null, pyme));
        assertFalse(RealtimeAudiencePolicy.matches(r, null));
    }

    @Test
    void destinatarioInvalidoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> new Recipient(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Recipient(null, OrgType.CARRIER, UUID.randomUUID()));
    }
}
