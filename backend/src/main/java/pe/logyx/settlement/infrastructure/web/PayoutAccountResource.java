package pe.logyx.settlement.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.settlement.application.SettlementService;
import pe.logyx.settlement.infrastructure.web.SettlementDtos.CreatePayoutAccount;
import pe.logyx.settlement.infrastructure.web.SettlementDtos.PayoutAccountView;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.security.OrgAdminOnly;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Datos de cobro del transportista (2.12): banco + CCI, Yape o Plin. Los gestiona su ADMIN; el resto de su empresa los
 * ve enmascarados. La PYME los ve en el cobro de un envío con él. Misma raíz exacta que los demás recursos de
 * {@code /api/v1/organizations}.
 */
@Path("/api/v1/organizations")
@RolesAllowed(Roles.CARRIER)
public class PayoutAccountResource {

    @Inject SettlementService service;
    @Inject TenantContext tenant;

    @GET
    @Path("/me/payout-accounts")
    @Operation(summary = "Datos de cobro vigentes de mi empresa transportista (número completo solo para el ADMIN)")
    public ApiResponse<List<PayoutAccountView>> list() {
        boolean full = tenant.isOrgAdmin();
        return ApiResponse.of(service.myPayoutAccounts().stream().map(a -> PayoutAccountView.of(a, full)).toList());
    }

    @POST
    @Path("/me/payout-accounts")
    @OrgAdminOnly
    @Operation(summary = "Agrega un dato de cobro (ADMIN)",
            description = "LOGYX-STL-007 CCI de 20 dígitos o celular de 9 dígitos, STL-008 (409) más de 5 vigentes. "
                    + "El número se guarda cifrado. El primero queda como principal.")
    public ApiResponse<PayoutAccountView> add(@Valid @NotNull CreatePayoutAccount req) {
        return ApiResponse.of(PayoutAccountView.of(service.addPayoutAccount(req.kind(), req.bankName(), req.holderName(),
                req.number(), Boolean.TRUE.equals(req.primary())), true));
    }

    @POST
    @Path("/me/payout-accounts/{id}/primary")
    @OrgAdminOnly
    @Operation(summary = "Marca un dato de cobro como principal (ADMIN)")
    public ApiResponse<PayoutAccountView> primary(@PathParam("id") UUID id) {
        return ApiResponse.of(PayoutAccountView.of(service.makePrimary(id), true));
    }

    @POST
    @Path("/me/payout-accounts/{id}/archive")
    @OrgAdminOnly
    @Operation(summary = "Archiva un dato de cobro (ADMIN); deja de verse en los cobros")
    public ApiResponse<Boolean> archive(@PathParam("id") UUID id) {
        service.archivePayoutAccount(id);
        return ApiResponse.of(Boolean.TRUE);
    }
}
