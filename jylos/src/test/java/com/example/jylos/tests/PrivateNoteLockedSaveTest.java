package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteService;

/**
 * Con la sesión bloqueada, guardar una nota privada no debe tocar su contenido.
 *
 * <p>Caracterización del guard de {@code NoteService.persistNote}: si la nota es privada,
 * su contenido en memoria <em>no</em> es texto cifrado y no hay clave en la sesión, el
 * guardado se salta por completo. Sin él, el candado (o cualquier texto plano que llevara
 * la nota) se escribiría encima del cifrado, y sin clave no habría forma de volver a
 * cifrarlo: el contenido real se perdería.</p>
 *
 * <p>Es una protección contra pérdida de datos que existía desde junio de 2026 sin ningún
 * test. Estas pruebas fijan su comportamiento actual; ninguna juzga si debería hacer otra
 * cosa.</p>
 */
class PrivateNoteLockedSaveTest {

    private static final String PASSWORD = "clave-de-prueba-para-el-guard";
    private static final String CUERPO = "contenido confidencial que no se debe perder";

    @Test
    void guardarUnaNotaPrivadaConLaSesionBloqueadaNoTocaElFichero(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        String id;
        String cifradoEnDisco;
        try {
            id = crearNotaPrivada(vault);
            cifradoEnDisco = Files.readString(vault.resolve(id), StandardCharsets.UTF_8);
            assertTrue(cifradoEnDisco.contains("JENC1:"), "la nota debe quedar cifrada en disco");
        } finally {
            cifrado.lock();
        }

        // Sesión bloqueada: no hay clave, así que la nota no se puede descifrar ni volver
        // a cifrar. Es exactamente el estado que el guard protege.
        assertFalse(cifrado.hasKey(), "la sesión debe estar bloqueada para este caso");

        NoteService notas = servicioPara(vault);
        Note bloqueada = notas.getNoteById(id).orElseThrow();
        assertEquals(NoteService.LOCKED_PLACEHOLDER, bloqueada.getContent(),
                "sin clave, leer una nota privada devuelve el candado");

        notas.updateNote(bloqueada);

        assertEquals(cifradoEnDisco, Files.readString(vault.resolve(id), StandardCharsets.UTF_8),
                "el fichero debe quedar byte a byte igual: el guardado se salta entero");
    }

    @Test
    void elCifradoSigueSiendoDescifrableTrasIntentarGuardarloBloqueado(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        String id;
        try {
            id = crearNotaPrivada(vault);
        } finally {
            cifrado.lock();
        }

        NoteService bloqueado = servicioPara(vault);
        bloqueado.updateNote(bloqueado.getNoteById(id).orElseThrow());

        // La comprobación que de verdad importa: no basta con que el fichero no cambie,
        // el contenido tiene que seguir recuperándose al desbloquear.
        cifrado.unlock(PASSWORD.toCharArray());
        try {
            Note recuperada = servicioPara(vault).getNoteById(id).orElseThrow();
            assertEquals(CUERPO, recuperada.getContent(),
                    "al desbloquear, el contenido original debe seguir ahí");
        } finally {
            cifrado.lock();
        }
    }

    @Test
    void unCambioDeMetadatosConLaSesionBloqueadaTampocoSePersiste(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        String id;
        try {
            id = crearNotaPrivada(vault);
        } finally {
            cifrado.lock();
        }

        NoteService notas = servicioPara(vault);
        Note bloqueada = notas.getNoteById(id).orElseThrow();
        assertFalse(bloqueada.isFavorite());

        notas.toggleFavorite(bloqueada);

        // Comportamiento actual, ni bueno ni malo: el guard salta antes de escribir, así
        // que el favorito se pierde. Proteger el contenido tiene prioridad sobre conservar
        // un cambio de metadatos. Queda fijado aquí para que nadie lo cambie sin querer.
        Note releida = servicioPara(vault).getNoteById(id).orElseThrow();
        assertFalse(releida.isFavorite(),
                "con la sesión bloqueada el guardado se salta entero, metadatos incluidos");
    }

    /**
     * El caso que solo cubre este guard: la nota lleva texto <em>plano</em> (no el candado)
     * y la sesión está bloqueada. Ocurre si el usuario tenía la nota abierta desbloqueada,
     * la sesión se bloquea y después se guarda. Sin clave no se puede volver a cifrar, así
     * que escribirla dejaría el contenido en claro en el disco — el guard lo impide.
     */
    @Test
    void noEscribeTextoPlanoSobreUnaNotaPrivadaSinClave(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        String id;
        String cifradoEnDisco;
        try {
            id = crearNotaPrivada(vault);
            cifradoEnDisco = Files.readString(vault.resolve(id), StandardCharsets.UTF_8);
        } finally {
            cifrado.lock();
        }

        NoteService notas = servicioPara(vault);
        Note enPlano = notas.getNoteById(id).orElseThrow();
        enPlano.setContent("texto en claro escrito con la sesion bloqueada");

        notas.updateNote(enPlano);

        String tras = Files.readString(vault.resolve(id), StandardCharsets.UTF_8);
        assertFalse(tras.contains("texto en claro"),
                "jamás debe quedar texto plano en el fichero de una nota privada: " + tras);
        assertEquals(cifradoEnDisco, tras, "el fichero debe quedar intacto");
    }

    /** Crea una nota y la convierte en privada, como hace la aplicación. Requiere clave. */
    private static String crearNotaPrivada(Path vault) {
        NoteService notas = servicioPara(vault);
        Note nota = notas.createNote("Privada", CUERPO);
        nota.setPrivate(true);
        notas.updateNote(nota);
        return nota.getId();
    }

    private static NoteService servicioPara(Path vault) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        return new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
    }
}
