package pe.logyx.identity.application.event;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/** El operador verificó, rechazó o suspendió una organización (RN01, RF08): se le avisa por el outbox. */
public record OrganizationVerificationChanged(UUID organizationId, Organization.VerificationStatus status,
                                              String note) implements OutboxEvent {}
