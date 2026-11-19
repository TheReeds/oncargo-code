package pe.logyx.shared.config;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Valor vigente de un parámetro que el operador cambió (V18). Sin fila = valor por omisión de la configuración. */
@Entity
@Table(name = "system_parameters")
public class SystemParameterEntity extends PanacheEntityBase {

    @Id
    @Column(name = "param_key")
    public String key;

    @Column(name = "param_value", nullable = false)
    public String value;

    @Column(nullable = false)
    public int version;

    @Column(nullable = false)
    public String reason;

    @Column(name = "updated_by")
    public UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
