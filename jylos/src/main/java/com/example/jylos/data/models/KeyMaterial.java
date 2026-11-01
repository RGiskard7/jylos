package com.example.jylos.data.models;

/**
 * What is persisted for the private-notes master password: never the password or the key,
 * only what is needed to re-derive and check the key from the password.
 *
 * @param salt Base64 PBKDF2 salt
 * @param verifier a known plaintext encrypted with the derived key, to check a password
 * @param iterations PBKDF2 iteration count the key was derived with
 */
public record KeyMaterial(String salt, String verifier, int iterations) {
}
