package pe.logyx.shared.tenancy;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.customized.QuarkusConnectionProvider;
import io.quarkus.hibernate.orm.runtime.tenant.TenantConnectionResolver;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

/**
 * Elige con qué datasource (usuario de PostgreSQL) abre Hibernate cada sesión (ADR-0006).
 *
 * <p>Se usa la multitenencia {@code DATABASE} de Hibernate solo como mecanismo de enrutamiento, con dos
 * "tenants" fijos que no son organizaciones:
 * <ul>
 *   <li>{@value #APP}: datasource {@code app}, usuario {@code LOGIN} miembro solo de {@code logyx_app}
 *       (sin ser dueño ni BYPASSRLS). Es el de toda sesión normal, autenticada o no.</li>
 *   <li>{@value #SYSTEM}: datasource por defecto (dueño del esquema; también lo usa Flyway). Solo dentro de
 *       {@link SystemTx#call}.</li>
 * </ul>
 * La sesión se abre por transacción, así que una transacción nueva de {@code SystemTx} obtiene su propia
 * sesión y conexión privilegiada sin tocar la de la transacción del usuario.
 */
public final class DataSourceRouting {

    public static final String APP = "app";
    public static final String SYSTEM = "system";

    private DataSourceRouting() {}

    @ApplicationScoped
    @PersistenceUnitExtension
    public static class Tenants implements TenantResolver {

        @Override
        public String getDefaultTenantId() {
            return APP;
        }

        @Override
        public String resolveTenantId() {
            return SystemTx.isActive() ? SYSTEM : APP;
        }
    }

    @ApplicationScoped
    @PersistenceUnitExtension
    public static class Connections implements TenantConnectionResolver {

        @Inject @DataSource(APP) AgroalDataSource app;
        @Inject @Default AgroalDataSource system;

        private volatile ConnectionProvider appProvider;
        private volatile ConnectionProvider systemProvider;

        @Override
        public ConnectionProvider resolve(String tenantId) {
            if (SYSTEM.equals(tenantId)) {
                if (systemProvider == null) systemProvider = new QuarkusConnectionProvider(system);
                return systemProvider;
            }
            if (APP.equals(tenantId)) {
                if (appProvider == null) appProvider = new QuarkusConnectionProvider(app);
                return appProvider;
            }
            throw new IllegalStateException("Datasource desconocido: " + tenantId);
        }
    }
}
