package pe.logyx.identity.application;

/** Edición del perfil de la organización (F20). {@code null} = sin cambio; vacío borra city/phone/address. */
public record UpdateOrganizationCommand(String legalName, String ruc, String city, String phone, String address) {}
