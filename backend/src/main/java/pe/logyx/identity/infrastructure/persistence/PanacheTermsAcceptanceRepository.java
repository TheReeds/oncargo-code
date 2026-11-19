package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.identity.application.port.out.TermsAcceptanceRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class PanacheTermsAcceptanceRepository implements TermsAcceptanceRepository {

    @Override
    public void insert(UUID profileId, UUID organizationId, String termsVersion, OffsetDateTime acceptedAt, String source) {
        TermsAcceptanceEntity e = new TermsAcceptanceEntity();
        e.id = UUID.randomUUID();
        e.profileId = profileId;
        e.organizationId = organizationId;
        e.termsVersion = termsVersion;
        e.acceptedAt = acceptedAt;
        e.source = source;
        e.persist();
    }

    @Override
    public boolean hasAccepted(UUID profileId, String termsVersion) {
        return TermsAcceptanceEntity.count("profileId = ?1 and termsVersion = ?2", profileId, termsVersion) > 0;
    }
}
