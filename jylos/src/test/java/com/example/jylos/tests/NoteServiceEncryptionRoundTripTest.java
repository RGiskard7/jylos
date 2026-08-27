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
 * {@code createNote}/{@code encryptForWrite}/{@code decryptForRead}/
 * {@code restoreStoredBody}/{@code scrubEncryptedForList}: el ciclo completo de cifrado
 * de una nota privada, de punta a punta con disco real.
 *
 * <p>Estos cinco métodos sumaban 26 mutaciones, 12 sin cazar. La mayoría de gaps no eran
 * "falta un test cualquiera": eran ramas donde el código hace exactamente lo contrario de
 * lo esperado (cifrar dos veces, no revertir el texto plano en memoria) y nada se
 * enteraba.</p>
 */
class NoteServiceEncryptionRoundTripTest {

    private static final String PASSWORD = "clave-para-round-trip";
    private static final String CUERPO = "contenido que debe quedar cifrado en disco";

    // ── encryptForWrite, a través de createNote ─────────────────────────────

    @Test
    void crearUnaNotaPrivadaConClaveLaDejaCifradaEnDiscoYEnClaroEnMemoria(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        try {
            NoteService notas = servicioPara(vault);
            Note nota = new Note("Privada", CUERPO);
            nota.setPrivate(true);

            Note creada = notas.createNote(nota);

            assertEquals(CUERPO, creada.getContent(),
                    "tras crear, la instancia en memoria debe quedar en texto plano otra vez");
            String enDisco = Files.readString(vault.resolve(creada.getId()), StandardCharsets.UTF_8);
            assertTrue(enDisco.contains("JENC1:"), "el fichero debe llevar el cuerpo cifrado: " + enDisco);
            assertFalse(enDisco.contains(CUERPO), "el texto plano no debe quedar en disco: " + enDisco);
        } finally {
            cifrado.lock();
        }
    }

    @Test
    void crearUnaNotaPublicaNuncaSeCifraAunqueHayaClave(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        try {
            NoteService notas = servicioPara(vault);
            Note nota = new Note("Pública", CUERPO); // isPrivate queda false por defecto

            Note creada = notas.createNote(nota);

            String enDisco = Files.readString(vault.resolve(creada.getId()), StandardCharsets.UTF_8);
            assertTrue(enDisco.contains(CUERPO), "una nota pública debe guardarse en claro: " + enDisco);
            assertFalse(enDisco.contains("JENC1:"), "no debe cifrarse nada: " + enDisco);
        } finally {
            cifrado.lock();
        }
    }

    @Test
    void reguardarUnaNotaYaCifradaNoLaCifraPorSegundaVez(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        try {
            NoteService notas = servicioPara(vault);
            Note nota = new Note("Privada", CUERPO);
            nota.setPrivate(true);
            Note creada = notas.createNote(nota);

            // El contenido en memoria ya está en claro tras crear; lo volvemos a guardar
            // sin cambiar nada. encryptForWrite debe reconocer que en disco ya hay
            // ciphertext, no envolverlo dos veces.
            notas.updateNote(creada);

            Note reabierta = notas.getNoteById(creada.getId()).orElseThrow();
            assertEquals(CUERPO, reabierta.getContent(),
                    "un cifrado doble sería indescifrable; el contenido debe seguir siendo el original");
        } finally {
            cifrado.lock();
        }
    }

    // ── decryptForRead ───────────────────────────────────────────────────────

    @Test
    void leerUnaNotaPrivadaConClaveDevuelveElTextoPlano(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        String id;
        try {
            NoteService notas = servicioPara(vault);
            Note nota = new Note("Privada", CUERPO);
            nota.setPrivate(true);
            id = notas.createNote(nota).getId();

            Note reabierta = notas.getNoteById(id).orElseThrow();
            assertEquals(CUERPO, reabierta.getContent(),
                    "con la clave puesta, decryptForRead debe devolver el texto plano real");
        } finally {
            cifrado.lock();
        }
    }

    // ── restoreStoredBody ────────────────────────────────────────────────────

    @Test
    void restaurarElCuerpoDeUnaNotaQueNoExisteEnDiscoNoLanzaYNoEscribeNada(@TempDir Path vault) throws Exception {
        // restoreStoredBody no encuentra nada que restaurar (la nota no existe en el DAO);
        // el comportamiento actual no es lanzar, es registrar el aviso y no escribir nada
        // — el mismo "saltarse el guardado en silencio" que el resto de guardas de
        // persistNote. Fijado tal cual, no es un fallo nuevo que se introduzca aquí.
        EncryptionService cifrado = EncryptionService.getInstance();
        cifrado.configure(PASSWORD.toCharArray());
        try {
            NoteService notas = servicioPara(vault);
            Note fantasma = new Note("id-inexistente.md", "Fantasma", NoteService.LOCKED_PLACEHOLDER);
            fantasma.setPrivate(true);

            notas.updateNote(fantasma); // no debe lanzar

            assertFalse(Files.exists(vault.resolve("id-inexistente.md")),
                    "no debe haberse creado ni escrito ningún fichero");
        } finally {
            cifrado.lock();
        }
    }

    // ── scrubEncryptedForList ────────────────────────────────────────────────

    @Test
    void unaListaSinNotasPrivadasNoSufreNingunCambio(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Pública 1", "cuerpo 1");
        notas.createNote("Pública 2", "cuerpo 2");

        for (Note n : notas.getAllNotes()) {
            assertFalse(NoteService.LOCKED_PLACEHOLDER.equals(n.getContent()),
                    "ninguna nota pública debe llevar el candado");
        }
    }

    // ── createNoteInFolder / carpeta ─────────────────────────────────────────

    @Test
    void crearEnLaRaizNoTocaFolderDAOAunqueSePaseUnaCarpetaSinId(@TempDir Path vault) {
        // Cubre la rama "folder != null pero folder.getId() == null": no debe intentar
        // añadir la nota a una carpeta que no existe realmente.
        com.example.jylos.data.dao.filesystem.FolderDAOFileSystem folderDao =
                new com.example.jylos.data.dao.filesystem.FolderDAOFileSystem(vault.toString());
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService notas = new NoteService(noteDao, folderDao);
        com.example.jylos.data.models.Folder sinId = new com.example.jylos.data.models.Folder("Sin id aún");

        Note creada = notas.createNoteInFolder("Nota", "cuerpo", sinId);

        assertEquals("Nota", creada.getTitle(), "la nota se crea igualmente, en la raíz");
        assertTrue(
                Files.exists(vault.resolve(creada.getId())) && !Files.isDirectory(vault.resolve("Sin id aún")),
                "no debe haberse creado ninguna carpeta ni movido la nota a ningún sitio");
    }

    /**
     * Hallazgo real, no una invención del test: a diferencia de {@code persistNote}
     * (que guarda explícitamente contra esto en la línea 200), {@code createNote} no
     * comprueba si hay clave antes de llamar a {@code encryptForWrite}. Si no la hay, el
     * método simplemente no cifra y {@code createNote} escribe el texto plano tal cual.
     *
     * <p>No es alcanzable hoy desde la UI — nada crea una nota ya marcada privada; el
     * flujo real siempre crea en público y activa "privada" después, camino que sí pasa
     * por el guard de {@code persistNote}. Pero cualquier código nuevo (un plugin, una
     * herramienta MCP) que construya una nota privada desde cero y llame a
     * {@code createNote} directamente se saltaría la protección sin darse cuenta. Se deja
     * fijado como está, documentado, en vez de "arreglado" a mitad de una tarea de
     * caracterización.</p>
     */
    @Test
    void crearUnaNotaYaMarcadaPrivadaSinClaveLaEscribeEnClaro(@TempDir Path vault) throws Exception {
        EncryptionService cifrado = EncryptionService.getInstance();
        assertFalse(cifrado.hasKey(), "esta prueba depende de que no haya clave en la sesión");

        NoteService notas = servicioPara(vault);
        Note nota = new Note("Privada sin clave", CUERPO);
        nota.setPrivate(true);

        Note creada = notas.createNote(nota);

        String enDisco = Files.readString(vault.resolve(creada.getId()), StandardCharsets.UTF_8);
        assertTrue(enDisco.contains(CUERPO),
                "comportamiento actual: sin clave, createNote guarda en claro en vez de negarse: " + enDisco);
    }

    private static NoteService servicioPara(Path vault) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        return new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
    }
}
