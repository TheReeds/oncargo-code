package pe.logyx.dashboard.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.DashboardCounts;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentCounts;
import pe.logyx.shipment.application.ShipmentQueries;

/** Compone los KPIs de marketplace y envíos de la organización actual (todo bajo RLS). */
@ApplicationScoped
@Transactional
public class DashboardQueries {

    @Inject MarketplaceQueries marketplace;
    @Inject ShipmentQueries shipments;
    @Inject TenantContext tenant;

    public DashboardSummary summary() {
        DashboardCounts m = marketplace.dashboardCounts();
        ShipmentCounts s = shipments.counts();
        if (tenant.orgType() == OrgType.CARRIER) {
            return new DashboardSummary(OrgType.CARRIER, null, null, null,
                    m.compatibleLoads(), m.pendingBids(), s.active(), s.deliveredThisMonth());
        }
        return new DashboardSummary(OrgType.PYME, m.activeRequests(), m.bidsToReview(), s.inTransit(),
                null, null, null, s.deliveredThisMonth());
    }
}
