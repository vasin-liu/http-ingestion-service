package com.pcitech.http.ingestion.core.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class JiaduSignVerifier {

    private JiaduSignVerifier() {
    }

    public static String compute(String platFlag, String eventId) {
        String payload = platFlag + "_" + eventId;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02X", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 not available", ex);
        }
    }

    public static boolean verify(String platFlag, String eventId, String sign) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        return compute(platFlag, eventId).equalsIgnoreCase(sign.trim());
    }
}
