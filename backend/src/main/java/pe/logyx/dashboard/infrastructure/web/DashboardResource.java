package pe.logyx.dashboard.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pe.logyx.dashboard.application.DashboardQueries;
import pe.logyx.dashboard.infrastructure.web.DashboardDtos.DashboardSummaryView;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

@Path("/api/v1/dashboard")
public class DashboardResource {

    @Inject DashboardQueries queries;

    @GET
    @Path("/summary")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    public ApiResponse<DashboardSummaryView> summary() {
        return ApiResponse.of(DashboardSummaryView.of(queries.summary()));
    }
}
