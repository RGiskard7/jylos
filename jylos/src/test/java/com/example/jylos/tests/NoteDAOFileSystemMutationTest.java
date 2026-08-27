package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.exceptions.DataAccessException;

/**
 * {@link NoteDAOFileSystem}: creación, edición, borrado/restauración/permanente,
 * reindexado y resolución de rutas — sobre un directorio real.
 *
 * <p>Baseline de mutación: 53% killed, 426 mutaciones. Prioriza lo alcanzable y de mayor
 * riesgo de pérdida de datos: {@code createNote}/{@code updateNote}/{@code deleteNote}/
 * {@code restoreNote}/{@code permanentlyDeleteNote} y el reindexado de caché tras mover un
 * fichero. No se persigue el 100%: {@code pruneStaleCacheEntriesIfNeeded()} es código
 * muerto de verdad (nadie la llama en todo el fichero, confirmado por grep) y la carga en
 * segundo plano (hilos) queda fuera por ser difícil de probar de forma determinista sin
 * flakiness — documentado, no cazado.</p>
 */
class NoteDAOFileSystemMutationTest {

    @TempDir
    Path tempDir;

    private NoteDAOFileSystem noteDAO;

    @BeforeEach
    void setUp() {
        noteDAO = new NoteDAOFileSystem(tempDir.toString());
    }

    // ── createNote ────────────────────────────────────────────────────────────

    @Test
    void createNoteEscribeElFicheroRealEnDisco() {
        Note nota = new Note("Título", "cuerpo");
        String id = noteDAO.createNote(nota);

        assertTrue(Files.isRegularFile(tempDir.resolve("Título.md")));
        assertEquals(id, nota.getId());
    }

    @Test
    void createNoteConTitulosDuplicadosNoColisiona() {
        noteDAO.createNote(new Note("Repetida", "uno"));
        String id2 = noteDAO.createNote(new Note("Repetida", "dos"));

        assertTrue(Files.isRegularFile(tempDir.resolve("Repetida (1).md")));
        assertEquals("Repetida (1).md", id2);
    }

    @Test
    void createNoteConIdQueApuntaAUnaCarpetaExistenteLaCreaAhi() throws Exception {
        Files.createDirectories(tempDir.resolve("Carpeta"));
        Note nota = new Note("Carpeta/Anidada", "Anidada", "cuerpo");

        String id = noteDAO.createNote(nota);

        assertTrue(Files.isRegularFile(tempDir.resolve("Carpeta").resolve("Anidada.md")));
        assertEquals("Carpeta/Anidada.md", id);
    }

    @Test
    void createNoteConTituloEnBlancoUsaUntitled() {
        Note nota = new Note("", "cuerpo");

        noteDAO.createNote(nota);

        assertTrue(Files.isRegularFile(tempDir.resolve("Untitled.md")));
    }

    // ── updateNote ────────────────────────────────────────────────────────────

    @Test
    void updateNoteRenombraElFicheroRealCuandoCambiaElTitulo() {
        Note nota = new Note("Antiguo", "cuerpo");
        nota.setId(noteDAO.createNote(nota));

        nota.setTitle("Nuevo");
        noteDAO.updateNote(nota);

        assertFalse(Files.exists(tempDir.resolve("Antiguo.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve("Nuevo.md")));
        assertEquals("Nuevo.md", nota.getId());
    }

    @Test
    void updateNoteEscribeElContenidoNuevoDeVerdadEnDisco() throws Exception {
        Note nota = new Note("Nota", "original");
        nota.setId(noteDAO.createNote(nota));

        nota.setContent("modificado");
        noteDAO.updateNote(nota);

        String enDisco = Files.readString(tempDir.resolve("Nota.md"));
        assertTrue(enDisco.contains("modificado"), "el fichero debe reflejar el contenido nuevo: " + enDisco);
        assertFalse(enDisco.contains("original"), "no debe quedar el contenido antiguo: " + enDisco);
    }

    @Test
    void updateNoteDeUnaNotaBorradaDelDiscoConContenidoIncompletoLanza() {
        Note nota = new Note("Fantasma", "Fantasma", "");
        nota.setContentComplete(false); // nunca se leyó el contenido real

        assertThrows(DataAccessException.class, () -> noteDAO.updateNote(nota),
                "no debe recrear en disco una nota que nunca se cargó de verdad — perdería el contenido real");
    }

    @Test
    void updateNoteResincronizaLasEtiquetasInlineDelCuerpoTrasEscribir() {
        Note nota = new Note("Nota", "cuerpo");
        nota.setId(noteDAO.createNote(nota));

        nota.setContent("cuerpo con #nueva etiqueta inline");
        noteDAO.updateNote(nota);

        assertTrue(nota.getTags().stream().anyMatch(t -> "nueva".equals(t.getTitle())),
                "una etiqueta #inline escrita en el cuerpo debe reflejarse en el objeto en memoria tras guardar");
    }

    // ── deleteNote / fetchTrashNotes ─────────────────────────────────────────

    @Test
    void deleteNoteMueveElFicheroRealAPapelera() {
        Note nota = new Note("Efimera", "cuerpo");
        nota.setId(noteDAO.createNote(nota));

        noteDAO.deleteNote(nota.getId());

        assertFalse(Files.exists(tempDir.resolve("Efimera.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".trash").resolve("Efimera.md")));
    }

    @Test
    void deleteNoteSoloMueveEsaNotaNoOtras() {
        Note aBorrar = new Note("A borrar", "cuerpo");
        aBorrar.setId(noteDAO.createNote(aBorrar));
        Note aConservar = new Note("A conservar", "cuerpo");
        aConservar.setId(noteDAO.createNote(aConservar));

        noteDAO.deleteNote(aBorrar.getId());

        assertTrue(Files.isRegularFile(tempDir.resolve("A conservar.md")));
    }

    @Test
    void deleteNoteDeUnIdInexistenteNoLanzaYEsIdempotente() {
        noteDAO.deleteNote("no-existe.md"); // no debe lanzar
        noteDAO.deleteNote("no-existe.md"); // segunda vez, sigue sin lanzar
    }

    @Test
    void fetchTrashNotesDevuelveLasNotasBorradasMarcadasComoDeleted() {
        Note nota = new Note("Efimera", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        noteDAO.deleteNote(nota.getId());

        List<Note> papelera = noteDAO.fetchTrashNotes();

        assertEquals(1, papelera.size());
        assertTrue(papelera.get(0).isDeleted());
    }

    // ── restoreNote ───────────────────────────────────────────────────────────

    @Test
    void restoreNoteDevuelveElFicheroRealASuSitioOriginal() {
        Note nota = new Note("Recuperable", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        noteDAO.deleteNote(nota.getId());

        noteDAO.restoreNote(".trash/Recuperable.md");

        assertTrue(Files.isRegularFile(tempDir.resolve("Recuperable.md")));
        assertFalse(Files.exists(tempDir.resolve(".trash").resolve("Recuperable.md")));
    }

    @Test
    void restoreNoteConConflictoDeNombreLeAsignaUnSufijoEnVezDeSobrescribir() throws Exception {
        Note original = new Note("Nota", "primero");
        original.setId(noteDAO.createNote(original));
        noteDAO.deleteNote(original.getId());
        // Una nota nueva ocupa el nombre original mientras la vieja está en la papelera.
        Note reemplazo = new Note("Nota", "segundo");
        noteDAO.createNote(reemplazo);

        noteDAO.restoreNote(".trash/Nota.md");

        String contenidoOriginalDeVuelta = Files.readString(tempDir.resolve("Nota.md"));
        assertTrue(contenidoOriginalDeVuelta.contains("segundo"),
                "el fichero que ya ocupaba el nombre no debe perderse ni sobrescribirse");
        // No basta con que "segundo" siga intacto: eso también pasaría si restoreNote
        // fallara en silencio y no restaurara nada. Hay que comprobar que "primero"
        // reapareció de verdad, en algún fichero con sufijo de conflicto.
        try (var stream = Files.list(tempDir)) {
            boolean encontradoConSufijo = stream
                    .filter(p -> p.getFileName().toString().startsWith("Nota_restored_"))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains("primero");
                        } catch (Exception e) {
                            return false;
                        }
                    });
            assertTrue(encontradoConSufijo, "la nota restaurada en conflicto debe reaparecer con sufijo, no perderse");
        }
    }

    // ── permanentlyDeleteNote — irreversible ─────────────────────────────────

    @Test
    void permanentlyDeleteNoteBorraElFicheroDeVerdadDelDisco() {
        Note nota = new Note("Definitiva", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        noteDAO.deleteNote(nota.getId());
        assertTrue(Files.isRegularFile(tempDir.resolve(".trash").resolve("Definitiva.md")));

        noteDAO.permanentlyDeleteNote(".trash/Definitiva.md");

        assertFalse(Files.exists(tempDir.resolve(".trash").resolve("Definitiva.md")),
                "el fichero debe desaparecer del disco de verdad, no solo de la caché");
    }

    @Test
    void permanentlyDeleteNoteSoloBorraEsaNotaNoOtrasEnPapelera() {
        Note aBorrar = new Note("A borrar del todo", "cuerpo");
        aBorrar.setId(noteDAO.createNote(aBorrar));
        Note aConservar = new Note("A conservar en papelera", "cuerpo");
        aConservar.setId(noteDAO.createNote(aConservar));
        noteDAO.deleteNote(aBorrar.getId());
        noteDAO.deleteNote(aConservar.getId());

        noteDAO.permanentlyDeleteNote(".trash/A borrar del todo.md");

        assertTrue(Files.isRegularFile(tempDir.resolve(".trash").resolve("A conservar en papelera.md")),
                "borrar una nota para siempre no debe tocar otra nota en la papelera");
    }

    // ── reindexMovedNote ──────────────────────────────────────────────────────

    @Test
    void reindexMovedNoteActualizaLaCacheAlNuevoId() throws Exception {
        // Una segunda nota, ajena, para que la caché nunca quede vacía durante la prueba:
        // fetchAllNotes() dispara un refreshCache() completo (relee disco de verdad) en
        // cuanto ve la caché vacía, y eso taparía cualquier fallo de reindexMovedNote sin
        // querer — el fichero movido sigue existiendo físicamente y refreshCache lo
        // encontraría de todos modos, sin que el reindexado hubiera hecho nada.
        noteDAO.createNote(new Note("Ajena", "cuerpo"));

        Note nota = new Note("Original", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        String idOriginal = nota.getId();
        // Fichero movido por fuera del DAO (p. ej. otro proceso, sync externo) — reindexMovedNote
        // es lo que pone la caché al día de un cambio que el DAO no hizo él mismo.
        Files.move(tempDir.resolve("Original.md"), tempDir.resolve("Movida.md"));
        nota.setId("Movida.md");

        noteDAO.reindexMovedNote(idOriginal, nota);

        // fetchAllNotes() lee la caché directa (cachedNotes.values()), sin caer al disco
        // como sí hacen getNoteById/resolveFilePath — es la única forma de comprobar que
        // la caché en sí se actualizó, no que el fichero de todos modos se encuentra por
        // otra vía.
        assertTrue(noteDAO.fetchAllNotes().stream().anyMatch(n -> "Movida.md".equals(n.getId())),
                "la caché debe tener el id nuevo tras reindexar");
        assertFalse(noteDAO.fetchAllNotes().stream().anyMatch(n -> idOriginal.equals(n.getId())),
                "la caché no debe conservar una entrada con el id viejo");
    }

    // ── resolveFilePath ───────────────────────────────────────────────────────

    @Test
    void resolveFilePathDeUnaNotaExistenteDevuelveSuRutaReal() {
        Note nota = new Note("Nota", "cuerpo");
        nota.setId(noteDAO.createNote(nota));

        assertTrue(noteDAO.resolveFilePath(nota.getId()).isPresent());
        assertEquals(tempDir.resolve("Nota.md").toAbsolutePath().normalize(),
                noteDAO.resolveFilePath(nota.getId()).get().toAbsolutePath().normalize());
    }

    @Test
    void resolveFilePathDeUnIdInexistenteDevuelveVacio() {
        assertTrue(noteDAO.resolveFilePath("no-existe.md").isEmpty());
    }

    @Test
    void resolveFilePathConIdNuloDevuelveVacio() {
        assertTrue(noteDAO.resolveFilePath(null).isEmpty());
    }

    // ── loadTags ──────────────────────────────────────────────────────────────

    @Test
    void loadTagsSobrescribeElEstadoEnMemoriaConElPersistido() {
        Note nota = new Note("Nota", "cuerpo #persistida");
        nota.setId(noteDAO.createNote(nota));
        Note enMemoria = new Note(nota.getId(), "Nota", "");
        enMemoria.addTag(new Tag("solo-en-memoria"));

        noteDAO.loadTags(enMemoria);

        assertTrue(enMemoria.getTags().stream().anyMatch(t -> "persistida".equals(t.getTitle())),
                "debe traer la etiqueta real del fichero");
        assertFalse(enMemoria.getTags().stream().anyMatch(t -> "solo-en-memoria".equals(t.getTitle())),
                "debe sobrescribir, no fusionar con lo que ya hubiera en memoria");
    }

    @Test
    void loadTagsDeUnaNotaInexistenteNoLanzaYNoTocaNada() {
        Note fantasma = new Note("no-existe.md", "Fantasma", "");
        fantasma.addTag(new Tag("intacta"));

        noteDAO.loadTags(fantasma); // no debe lanzar

        assertTrue(fantasma.getTags().stream().anyMatch(t -> "intacta".equals(t.getTitle())));
    }

    // ── pruneStaleCacheEntriesIfNeeded, vía fetchAllNotes ────────────────────

    /**
     * Este mecanismo existía pero no lo llamaba nadie en todo el fichero (confirmado por
     * grep antes de conectarlo) — la caché nunca se autolimpiaba si un fichero
     * desaparecía por fuera del DAO (otro proceso, sincronización externa). Ahora
     * {@code fetchAllNotes()} lo dispara. El primer prune de un DAO recién creado corre
     * siempre (el temporizador arranca en 0), así que no hace falta esperar los 3s reales.
     */
    @Test
    void fetchAllNotesLimpiaEntradasCuyoFicheroDesaparecioPorFueraDelDao() throws Exception {
        Note superviviente = new Note("Superviviente", "cuerpo");
        superviviente.setId(noteDAO.createNote(superviviente));
        Note borradaPorFuera = new Note("BorradaPorFuera", "cuerpo");
        borradaPorFuera.setId(noteDAO.createNote(borradaPorFuera));

        Files.delete(tempDir.resolve("BorradaPorFuera.md")); // fuera del DAO, sin pasar por deleteNote()

        List<Note> notas = noteDAO.fetchAllNotes();

        assertTrue(notas.stream().noneMatch(n -> "BorradaPorFuera.md".equals(n.getId())),
                "una entrada cuyo fichero desapareció por fuera del DAO no debe seguir en fetchAllNotes");
        assertTrue(notas.stream().anyMatch(n -> "Superviviente.md".equals(n.getId())),
                "las notas que sí siguen en disco deben conservarse");
    }

    // ── getFolderOfNote / fetchNotesByFolderId ───────────────────────────────

    @Test
    void getFolderOfNoteDeUnaNotaEnRaizDevuelveRoot() {
        Folder carpeta = noteDAO.getFolderOfNote("Nota.md");

        assertEquals("ROOT", carpeta.getId());
    }

    @Test
    void getFolderOfNoteDeUnaNotaAnidadaDevuelveSuCarpeta() {
        Folder carpeta = noteDAO.getFolderOfNote("Carpeta/Nota.md");

        assertEquals("Carpeta", carpeta.getId());
    }

    @Test
    void fetchNotesByFolderIdSoloDevuelveNotasDirectasDeEsaCarpetaNoAnidadas() throws Exception {
        Files.createDirectories(tempDir.resolve("Carpeta").resolve("Sub"));
        Note directa = new Note("Carpeta/Directa", "Directa", "cuerpo");
        noteDAO.createNote(directa);
        Note anidada = new Note("Carpeta/Sub/Anidada", "Anidada", "cuerpo");
        noteDAO.createNote(anidada);
        noteDAO.refreshCache();

        List<Note> notas = noteDAO.fetchNotesByFolderId("Carpeta");

        assertEquals(1, notas.size(), "solo la nota directamente dentro de Carpeta, no la de Carpeta/Sub");
    }
}
