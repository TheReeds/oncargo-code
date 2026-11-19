package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.identity.application.port.out.OrganizationSuccessionStore;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL nativo del cambio asistido de RUC (V23). Corre en la transacción que lo llame (SystemTx o la del operador). */
@ApplicationScoped
public class SqlOrganizationSuccessionStore implements OrganizationSuccessionStore {

    @Inject EntityManager em;

    @Override
    public List<String> blockingCommitments(UUID org) {
        List<String> out = new ArrayList<>();
        add(out, "SELECT count(*) FROM shipments WHERE (requester_org_id = ?1 OR carrier_org_id = ?1) "
                + "AND status IN ('PENDING', 'IN_TRANSIT')", org, "envíos en curso");
        add(out, "SELECT count(*) FROM shipment_requests WHERE requester_org_id = ?1 AND status = 'PUBLISHED'", org,
                "solicitudes publicadas");
        add(out, "SELECT count(*) FROM bids WHERE carrier_org_id = ?1 AND status = 'PENDING'", org,
                "ofertas pendientes");
        add(out, "SELECT count(*) FROM carrier_trips WHERE carrier_org_id = ?1 AND status IN ('OPEN', 'FILLING')", org,
                "viajes publicados");
        return out;
    }

    private void add(List<String> out, String sql, UUID org, String label) {
        long n = ((Number) em.createNativeQuery(sql).setParameter(1, org).getSingleResult()).longValue();
        if (n > 0) out.add(n + " " + label);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UUID> transferMembersAndFleet(UUID from, UUID to) {
        List<UUID> moved = em.createNativeQuery("SELECT id FROM profiles WHERE organization_id = ?1", UUID.class)
                .setParameter(1, from).getResultList();
        // Sube la versión de sesión: su JWT lleva la organización anterior (ADR-0009).
        em.createNativeQuery("UPDATE profiles SET organization_id = ?2, session_version = session_version + 1 "
                        + "WHERE organization_id = ?1")
                .setParameter(1, from).setParameter(2, to).executeUpdate();
        em.createNativeQuery("UPDATE drivers SET organization_id = ?2 WHERE organization_id = ?1")
                .setParameter(1, from).setParameter(2, to).executeUpdate();
        em.createNativeQuery("UPDATE vehicles SET organization_id = ?2 WHERE organization_id = ?1")
                .setParameter(1, from).setParameter(2, to).executeUpdate();
        return moved;
    }

    @Override
    public void setPredecessor(UUID successor, UUID predecessor) {
        em.createNativeQuery("UPDATE organizations SET predecessor_id = ?2 WHERE id = ?1")
                .setParameter(1, successor).setParameter(2, predecessor).executeUpdate();
    }

    @Override
    public void copyReputation(UUID from, UUID to) {
        em.createNativeQuery("INSERT INTO reputation_scores (org_id, total_reviews, avg_rating, avg_punctuality, "
                        + "avg_cargo_condition, avg_communication, trust_score, updated_at) SELECT ?2, total_reviews, "
                        + "avg_rating, avg_punctuality, avg_cargo_condition, avg_communication, trust_score, now() "
                        + "FROM reputation_scores WHERE org_id = ?1 ON CONFLICT (org_id) DO NOTHING")
                .setParameter(1, from).setParameter(2, to).executeUpdate();
    }

    @Override
    public void renameLegal(UUID org, String legalName) {
        em.createNativeQuery("UPDATE organizations SET legal_name = ?2 WHERE id = ?1")
                .setParameter(1, org).setParameter(2, legalName).executeUpdate();
    }

    @Override
    public void insertLegalChange(LegalChange c) {
        em.createNativeQuery("INSERT INTO organization_legal_changes (id, organization_id, successor_id, old_legal_name, "
                        + "new_legal_name, old_ruc, new_ruc, reason, document_key, document_name, document_content_type, "
                        + "document_size_bytes, document_sha256, created_by, created_at) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)")
                .setParameter(1, c.id()).setParameter(2, c.organizationId()).setParameter(3, c.successorId())
                .setParameter(4, c.oldLegalName()).setParameter(5, c.newLegalName()).setParameter(6, c.oldRuc())
                .setParameter(7, c.newRuc()).setParameter(8, c.reason()).setParameter(9, c.documentKey())
                .setParameter(10, c.documentName()).setParameter(11, c.documentContentType())
                .setParameter(12, c.documentSizeBytes()).setParameter(13, c.documentSha256())
                .setParameter(14, c.createdBy()).setParameter(15, c.createdAt()).executeUpdate();
    }

    private static final String SELECT_CHANGE = "SELECT id, organization_id, successor_id, old_legal_name, "
            + "new_legal_name, old_ruc, new_ruc, reason, document_key, document_name, document_content_type, "
            + "document_size_bytes, document_sha256, created_by, created_at FROM organization_legal_changes ";

    @Override
    @SuppressWarnings("unchecked")
    public List<LegalChange> legalChanges(UUID org) {
        List<Object[]> rows = em.createNativeQuery(SELECT_CHANGE
                        + "WHERE organization_id = ?1 OR successor_id = ?1 ORDER BY created_at DESC")
                .setParameter(1, org).getResultList();
        return rows.stream().map(SqlOrganizationSuccessionStore::change).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<LegalChange> legalChange(UUID id) {
        List<Object[]> rows = em.createNativeQuery(SELECT_CHANGE + "WHERE id = ?1").setParameter(1, id).getResultList();
        return rows.stream().findFirst().map(SqlOrganizationSuccessionStore::change);
    }

    private static LegalChange change(Object[] r) {
        return new LegalChange((UUID) r[0], (UUID) r[1], (UUID) r[2], (String) r[3], (String) r[4], (String) r[5],
                (String) r[6], (String) r[7], (String) r[8], (String) r[9], (String) r[10],
                ((Number) r[11]).longValue(), (String) r[12], (UUID) r[13], toOffset(r[14]));
    }

    static OffsetDateTime toOffset(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof java.time.Instant i) return i.atOffset(java.time.ZoneOffset.UTC);
        if (v instanceof java.sql.Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Fecha no soportada: " + v.getClass());
    }

    @Override
    public UUID predecessorOf(UUID org) {
        var rows = em.createNativeQuery("SELECT predecessor_id FROM organizations WHERE id = ?1", UUID.class)
                .setParameter(1, org).getResultList();
        return rows.isEmpty() ? null : (UUID) rows.getFirst();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UUID> successorsOf(UUID org) {
        return em.createNativeQuery("SELECT id FROM organizations WHERE predecessor_id = ?1 ORDER BY created_at",
                UUID.class).setParameter(1, org).getResultList();
    }
}
