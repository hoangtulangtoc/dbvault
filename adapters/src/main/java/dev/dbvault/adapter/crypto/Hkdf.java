package dev.dbvault.adapter.crypto;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HKDF with HMAC-SHA256 (RFC 5869): turns one master key into as many independent keys as there are
 * {@code info} labels. dbvault derives one key per target, so a single leaked target key opens that
 * target's password and nothing else.
 */
public final class Hkdf {

    private static final String HMAC = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private Hkdf() {
    }

    /**
     * @param inputKeyMaterial the master key
     * @param salt optional; an empty salt means a string of zeros, as the RFC says
     * @param info what the key is for — never empty, or two uses would share a key
     * @param length bytes of output, at most 255 × 32
     */
    public static byte[] derive(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        if (info == null || info.length == 0) {
            throw new IllegalArgumentException("HKDF info must name what the key is for");
        }
        if (length <= 0 || length > 255 * HASH_LENGTH) {
            throw new IllegalArgumentException("HKDF output length out of range: " + length);
        }
        try {
            byte[] prk = hmac(salt == null || salt.length == 0 ? new byte[HASH_LENGTH] : salt, inputKeyMaterial);
            byte[] output = new byte[length];
            byte[] previous = new byte[0];
            int written = 0;
            for (int counter = 1; written < length; counter++) {
                Mac mac = Mac.getInstance(HMAC);
                mac.init(new SecretKeySpec(prk, HMAC));
                mac.update(previous);
                mac.update(info);
                mac.update((byte) counter);
                previous = mac.doFinal();
                int take = Math.min(previous.length, length - written);
                System.arraycopy(previous, 0, output, written, take);
                written += take;
            }
            return output;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HKDF failed", e);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC);
        mac.init(new SecretKeySpec(key, HMAC));
        return mac.doFinal(data);
    }
}
