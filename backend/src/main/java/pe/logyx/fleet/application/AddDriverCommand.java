package pe.logyx.fleet.application;

public record AddDriverCommand(String fullName, String email, String password, String licenseNumber) {}
