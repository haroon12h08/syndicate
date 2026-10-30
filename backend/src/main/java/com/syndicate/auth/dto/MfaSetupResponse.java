package com.syndicate.auth.dto;

public record MfaSetupResponse(String secret, String qrCodeUri) {
}
