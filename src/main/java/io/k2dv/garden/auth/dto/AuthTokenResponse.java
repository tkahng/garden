package io.k2dv.garden.auth.dto;

public record AuthTokenResponse(String accessToken, String refreshToken) {}
