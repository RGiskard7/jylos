package com.example.jylos.data.dao.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.example.jylos.data.dao.interfaces.EncryptionKeyDAO;
import com.example.jylos.data.models.KeyMaterial;
import com.example.jylos.exceptions.DataAccessException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Keeps the private-notes key material in {@code .jylos/encryption.json} inside the vault,
 * so a vault synced or copied to another machine can still be unlocked with its password.
 */
public class EncryptionKeyDAOFileSystem implements EncryptionKeyDAO {

    private static final int FORMAT_VERSION = 1;
    private static final String KDF = "PBKDF2WithHmacSHA256";

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public EncryptionKeyDAOFileSystem(Path vaultRoot) {
        this.file = vaultRoot.resolve(".jylos").resolve("encryption.json");
    }

    @Override
    public Optional<KeyMaterial> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String salt = root.get("salt").getAsString();
            String verifier = root.get("verifier").getAsString();
            int iterations = root.has("iterations") ? root.get("iterations").getAsInt() : 0;
            return Optional.of(new KeyMaterial(salt, verifier, iterations));
        } catch (IOException | RuntimeException e) {
            // A corrupt file must not look like "no password set up": that would invite
            // setting a new one and orphan every note encrypted with the old key.
            throw new DataAccessException("Unreadable encryption settings: " + file, e);
        }
    }

    @Override
    public void save(KeyMaterial material) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("kdf", KDF);
        root.addProperty("iterations", material.iterations());
        root.addProperty("salt", material.salt());
        root.addProperty("verifier", material.verifier());
        try {
            FileSystemAtomicWriter.writeString(file, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Could not write encryption settings: " + file, e);
        }
    }
}
