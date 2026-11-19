package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.marketplace.application.port.out.CarrierPreferenceRepository;
import pe.logyx.marketplace.domain.model.CarrierPreference;
import pe.logyx.marketplace.domain.model.PreferenceKind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheCarrierPreferenceRepository implements CarrierPreferenceRepository {

    @Override
    public Optional<CarrierPreference> find(UUID requesterOrgId, UUID carrierOrgId) {
        return CarrierPreferenceEntity.<CarrierPreferenceEntity>find("requesterOrgId = ?1 and carrierOrgId = ?2",
                requesterOrgId, carrierOrgId).firstResultOptional().map(CarrierPreferenceEntity::toDomain);
    }

    @Override
    public List<CarrierPreference> list(UUID requesterOrgId, PreferenceKind kind) {
        var q = kind == null
                ? CarrierPreferenceEntity.<CarrierPreferenceEntity>find("requesterOrgId = ?1 order by updatedAt desc",
                        requesterOrgId)
                : CarrierPreferenceEntity.<CarrierPreferenceEntity>find(
                        "requesterOrgId = ?1 and kind = ?2 order by updatedAt desc", requesterOrgId, kind);
        return q.list().stream().map(CarrierPreferenceEntity::toDomain).toList();
    }

    @Override
    public void save(CarrierPreference preference) {
        CarrierPreferenceEntity e = CarrierPreferenceEntity.findById(
                new CarrierPreferenceEntity.Key(preference.requesterOrgId(), preference.carrierOrgId()));
        if (e == null) {
            e = new CarrierPreferenceEntity();
            e.apply(preference);
            e.persist();
        } else {
            e.apply(preference);
        }
    }

    @Override
    public void delete(UUID requesterOrgId, UUID carrierOrgId) {
        CarrierPreferenceEntity.delete("requesterOrgId = ?1 and carrierOrgId = ?2", requesterOrgId, carrierOrgId);
    }
}
