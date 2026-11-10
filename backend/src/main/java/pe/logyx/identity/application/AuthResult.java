package pe.logyx.identity.application;

public record AuthResult(String accessToken, long expiresIn, UserAccount user) {}
