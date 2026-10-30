package com.example.jylos.data.dao.interfaces;

import java.util.Optional;

import com.example.jylos.data.models.KeyMaterial;

/**
 * Persistence of the private-notes {@link KeyMaterial}. It lives with the notes it
 * protects (inside the vault, or in the database) so encrypted notes can be unlocked on
 * any machine that has them.
 */
public interface EncryptionKeyDAO {

    /** The stored key material, or empty when no master password has been set up here. */
    Optional<KeyMaterial> load();

    /** Stores (replacing) the key material. */
    void save(KeyMaterial material);
}
