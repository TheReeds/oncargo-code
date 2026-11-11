package pe.logyx.security;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-0006: el usuario de conexión de la aplicación ({@code logyx_api}) es miembro solo de {@code logyx_app}.
 * Simula una inyección SQL ejecutando sentencias arbitrarias con una conexión del datasource {@code app} y
 * verifica que no puede escalar a un rol privilegiado ni saltarse RLS.
 */
@QuarkusTest
class DatabasePrivilegesTest {

    @Inject @DataSource("app") AgroalDataSource app;
    @Inject EntityManager em;
    @Inject SystemTx systemTx;

    private String owner() {
        return systemTx.call(() -> (String) em.createNativeQuery("SELECT current_user").getSingleResult());
    }

    private static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); var rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    /** La sentencia debe fallar por falta de privilegios (SQLSTATE 42501), no por otro error. */
    private static void denied(Connection c, String sql) {
        SQLException e = assertThrows(SQLException.class, () -> {
            try (Statement st = c.createStatement()) { st.execute(sql); }
        }, sql);
        assertEquals("42501", e.getSQLState(), sql + " → " + e.getMessage());
    }

    @Test
    void elUsuarioDeLaAppNoEsDuenoNiSuperusuarioNiBypassRls() throws SQLException {
        try (Connection c = app.getConnection()) {
            assertEquals("logyx_api", one(c, "SELECT session_user"));
            assertEquals("f", one(c, "SELECT (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb)::text::char "
                    + "FROM pg_roles WHERE rolname = session_user"));
            // Solo es miembro (directo o indirecto) de logyx_app
            assertEquals("logyx_app", one(c, "SELECT string_agg(r.rolname, ',') FROM pg_roles r "
                    + "WHERE r.rolname <> session_user AND pg_has_role(session_user, r.oid, 'MEMBER') "
                    + "AND r.rolname NOT LIKE 'pg\\_%'"));
            assertEquals("0", one(c, "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' "
                    + "AND tableowner = session_user"));
        }
    }

    @Test
    void resetRoleYSetRoleNoEscalanPrivilegios() throws SQLException {
        String owner = owner();
        assertNotEquals("logyx_api", owner);
        try (Connection c = app.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL ROLE logyx_app");
                st.execute("RESET ROLE");
            }
            assertEquals("logyx_api", one(c, "SELECT current_user"), "RESET ROLE vuelve a logyx_api, no al dueño");
            c.rollback();

            for (String sql : new String[] {
                    "SET ROLE \"" + owner + "\"",
                    "SET SESSION AUTHORIZATION \"" + owner + "\"",
                    "ALTER ROLE logyx_api BYPASSRLS",
                    "ALTER TABLE profiles DISABLE ROW LEVEL SECURITY",
                    "DROP POLICY profiles_own_org ON profiles",
                    "CREATE ROLE intruso LOGIN SUPERUSER",
                    "SELECT pg_read_file('/etc/passwd')",
                    "SELECT * FROM flyway_schema_history",
                    "INSERT INTO notifications (org_id, type, title, body) "
                            + "SELECT id, 'NEW_BID', 'x', 'x' FROM organizations LIMIT 1",
            }) {
                denied(c, sql);
                c.rollback();
            }
        }
    }

    @Test
    void sinContextoDeOrganizacionNoSeVeNada() throws SQLException {
        Api.register("PYME", "Privilegios Pyme");
        long total = systemTx.call(() ->
                ((Number) em.createNativeQuery("SELECT count(*) FROM profiles").getSingleResult()).longValue());
        assertTrue(total > 0);
        try (Connection c = app.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) { st.execute("RESET ROLE"); }
            assertEquals("0", one(c, "SELECT count(*) FROM profiles"), "RLS aplica aunque se haga RESET ROLE");
            assertEquals("0", one(c, "SELECT count(*) FROM vehicles"));
            assertEquals("0", one(c, "SELECT count(*) FROM shipment_requests WHERE status <> 'PUBLISHED'"));
            c.rollback();
        }
    }
}
