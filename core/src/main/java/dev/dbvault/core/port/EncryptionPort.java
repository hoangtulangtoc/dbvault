package dev.dbvault.core.port;

import java.util.UUID;

/**
 * Symmetric encryption for secrets held at rest.
 *
 * <p>Only the application layer may call this. Adapters never hold the key,
 * so there is exactly one place in the system where a secret is unwrapped and
 * exactly one place to review.
 */
public interface EncryptionPort {

    /**
     * @return Base64 of {@code IV || ciphertext || tag}; encrypting the same
     *         input twice yields different output, because the IV is random
     * @throws dev.dbvault.core.exception.EncryptionException
     *         if encryption fails
     */
    String encrypt(String plaintext);

    /**
     * @throws dev.dbvault.core.exception.EncryptionException
     *         if the input is malformed, was encrypted under a different key,
     *         or has been tampered with
     */
    String decrypt(String ciphertext);

    /**
     * Encrypts a secret that belongs to one owner (a target) under a key derived for that owner
     * alone, so one owner's key never opens another's secret.
     */
    String encryptFor(UUID owner, String plaintext);

    /**
     * Decrypts what {@link #encryptFor} wrote for this owner. Values written by {@link #encrypt}
     * before per-owner keys existed are still read.
     */
    String decryptFor(UUID owner, String ciphertext);
}
