package pe.logyx.shared.config;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Una fila por cambio de un parámetro (V18). Solo se inserta. */
@Entity
@Table(name = "system_parameter_history")
public class SystemParameterHistoryEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "param_key", nullable = false)
    public String key;

    @Column(nullable = false)
    public int version;

    @Column(name = "previous_value")
    public String previousValue;

    @Column(name = "new_value", nullable = false)
    public String newValue;

    @Column(nullable = false)
    public String reason;

    @Column(name = "changed_by")
    public UUID changedBy;

    @Column(name = "changed_by_org")
    public UUID changedByOrg;

    @Column(name = "changed_at", nullable = false)
    public OffsetDateTime changedAt;
}
