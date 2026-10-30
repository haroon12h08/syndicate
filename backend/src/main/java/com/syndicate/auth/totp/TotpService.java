package com.syndicate.auth.totp;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * RFC 6238 Time-Based One-Time Password (TOTP) engine.
 * Generates and validates standard 6-digit TOTP codes with a 30-second time step
 * and ±1 step (±30s) clock drift tolerance.
 */
@Service
public class TotpService {

    public static final int TIME_STEP_SECONDS = 30;
    public static final int CODE_DIGITS = 6;
    public static final int MODULUS = 1_000_000;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 20-byte cryptographically secure random secret encoded in Base32 (32 characters).
     */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /**
     * Validates a 6-digit TOTP code against the Base32 secret with ±1 time step tolerance.
     */
    public boolean verifyCode(String base32Secret, int code) {
        if (base32Secret == null || base32Secret.isBlank() || code < 0 || code >= MODULUS) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (generateCodeForStep(base32Secret, step) == code) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates the code for a specific time step (useful for testing and verification).
     */
    public int generateCodeForStep(String base32Secret, long timeStep) {
        try {
            byte[] key = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int truncatedHash = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            return truncatedHash % MODULUS;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to calculate TOTP code", e);
        }
    }

    /**
     * Constructs a standard otpauth URI for mobile authenticator apps.
     */
    public String getQrCodeUri(String email, String base32Secret, String issuer) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return "otpauth://totp/" + issuer + ":" + encodedEmail + "?secret=" + base32Secret + "&issuer=" + encodedIssuer;
    }

    // --- RFC 4648 Base32 helper methods ---

    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(BASE32_ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase(Locale.ROOT).replaceAll("[=\\s]", "");
        byte[] result = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[count++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        if (count < result.length) {
            byte[] trimmed = new byte[count];
            System.arraycopy(result, 0, trimmed, 0, count);
            return trimmed;
        }
        return result;
    }
}
