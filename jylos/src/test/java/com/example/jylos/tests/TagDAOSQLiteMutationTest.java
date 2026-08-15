package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.dao.sqlite.TagDAOSQLite;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.exceptions.DataAccessException;

/**
 * {@link TagDAOSQLite}: CRUD de etiquetas y su relación con notas vía {@code tagsNotes}.
 *
 * <p>Estaba al 17% de mutación, sin ningún test contra una base de datos real. Contra
 * SQLite real, en un fichero temporal, con el esquema de producción tal cual lo crea
 * {@code SQLiteDB.initDatabase()}.</p>
 */
class TagDAOSQLiteMutationTest {

    @Test
    void crearUnaEtiquetaLeAsignaUnId(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "crear.sqlite")) {
            Tag etiqueta = new Tag("Trabajo");
            String id = s.tagDAO.createTag(etiqueta);

            assertTrue(id != null && !id.isBlank());
            Tag releida = s.tagDAO.getTagById(id);
            assertEquals("Trabajo", releida.getTitle());
        }
    }

    @Test
    void crearUnaEtiquetaConTituloDuplicadoLanza(@TempDir Path tmp) throws Exception {
        // El esquema declara title TEXT UNIQUE: el segundo INSERT viola la restricción,
        // salta SQLException, y createTag() la envuelve en DataAccessException — no la traga
        // en silencio como updateTag()/deleteTag().
        try (Sesion s = Sesion.abrir(tmp, "duplicada.sqlite")) {
            s.tagDAO.createTag(new Tag("Única"));

            assertThrows(DataAccessException.class, () -> s.tagDAO.createTag(new Tag("Única")));
        }
    }

    @Test
    void actualizarUnaEtiquetaCambiaElTitulo(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "actualizar.sqlite")) {
            Tag etiqueta = new Tag("Antiguo");
            etiqueta.setId(s.tagDAO.createTag(etiqueta));

            etiqueta.setTitle("Nuevo");
            s.tagDAO.updateTag(etiqueta);

            assertEquals("Nuevo", s.tagDAO.getTagById(etiqueta.getId()).getTitle());
        }
    }

    @Test
    void actualizarUnaEtiquetaSoloTocaEsaEtiquetaNoOtras(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "actualizar-aislada.sqlite")) {
            Tag aRenombrar = new Tag("Antiguo");
            aRenombrar.setId(s.tagDAO.createTag(aRenombrar));
            Tag otra = new Tag("Intacta");
            otra.setId(s.tagDAO.createTag(otra));

            aRenombrar.setTitle("Nuevo");
            s.tagDAO.updateTag(aRenombrar);

            assertEquals("Intacta", s.tagDAO.getTagById(otra.getId()).getTitle(),
                    "actualizar una etiqueta no debe tocar el título de otra");
        }
    }

    @Test
    void borrarUnaEtiquetaLaQuitaDelListado(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "borrar.sqlite")) {
            Tag etiqueta = new Tag("Efímera");
            etiqueta.setId(s.tagDAO.createTag(etiqueta));

            s.tagDAO.deleteTag(etiqueta.getId());

            assertNull(s.tagDAO.getTagById(etiqueta.getId()),
                    "a diferencia de las carpetas y notas, las etiquetas no tienen papelera: "
                            + "el borrado es un DELETE real, sin is_deleted");
        }
    }

    @Test
    void borrarUnaEtiquetaSoloBorraEsaEtiquetaNoOtras(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "borrar-aislada.sqlite")) {
            Tag aBorrar = new Tag("A borrar");
            aBorrar.setId(s.tagDAO.createTag(aBorrar));
            Tag aConservar = new Tag("A conservar");
            aConservar.setId(s.tagDAO.createTag(aConservar));

            s.tagDAO.deleteTag(aBorrar.getId());

            assertEquals("A conservar", s.tagDAO.getTagById(aConservar.getId()).getTitle(),
                    "borrar una etiqueta no debe tocar a otra");
        }
    }

    @Test
    void borrarUnaEtiquetaBorraTambienSusAsociacionesConNotas(@TempDir Path tmp) throws Exception {
        // tagsNotes declara FOREIGN KEY (tag_id) ... ON DELETE CASCADE, y SQLiteDB activa
        // PRAGMA foreign_keys = ON al configurar la conexión: borrar la etiqueta debe
        // arrastrar consigo sus filas de tagsNotes sin que TagDAOSQLite tenga que borrarlas
        // a mano.
        try (Sesion s = Sesion.abrir(tmp, "borrar-cascada.sqlite")) {
            Tag etiqueta = new Tag("Con notas");
            etiqueta.setId(s.tagDAO.createTag(etiqueta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.noteDAO.addTag(nota, etiqueta);

            s.tagDAO.deleteTag(etiqueta.getId());

            assertTrue(s.tagDAO.fetchAllNotesWithTag(etiqueta.getId()).isEmpty(),
                    "la asociación debe desaparecer junto con la etiqueta, vía ON DELETE CASCADE");
        }
    }

    @Test
    void getTagByIdDeUnaEtiquetaInexistenteDevuelveNull(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "inexistente.sqlite")) {
            assertNull(s.tagDAO.getTagById("no-existe"));
        }
    }

    @Test
    void fetchAllTagsDevuelveTodasLasCreadas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "listado.sqlite")) {
            s.tagDAO.createTag(new Tag("Una"));
            s.tagDAO.createTag(new Tag("Otra"));

            List<Tag> todas = s.tagDAO.fetchAllTags();

            assertEquals(2, todas.size(), "debe devolver las etiquetas creadas, no una lista vacía");
        }
    }

    @Test
    void existsByTitleDistingueEtiquetasCreadasDeLasQueNo(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "existe.sqlite")) {
            s.tagDAO.createTag(new Tag("Única"));

            assertTrue(s.tagDAO.existsByTitle("Única"));
            assertFalse(s.tagDAO.existsByTitle("No existe"));
        }
    }

    // ── fetchAllNotesWithTag ────────────────────────────────────────────────

    @Test
    void fetchAllNotesWithTagDevuelveLasNotasAsociadas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "notas-por-etiqueta.sqlite")) {
            Tag etiqueta = new Tag("Proyecto");
            etiqueta.setId(s.tagDAO.createTag(etiqueta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.noteDAO.addTag(nota, etiqueta);

            List<Note> notas = s.tagDAO.fetchAllNotesWithTag(etiqueta.getId());

            assertEquals(1, notas.size());
            assertEquals(nota.getId(), notas.get(0).getId());
        }
    }

    @Test
    void fetchAllNotesWithTagSoloDevuelveLasDeEsaEtiquetaNoOtras(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "notas-por-etiqueta-aislada.sqlite")) {
            Tag etiquetaA = new Tag("A");
            etiquetaA.setId(s.tagDAO.createTag(etiquetaA));
            Tag etiquetaB = new Tag("B");
            etiquetaB.setId(s.tagDAO.createTag(etiquetaB));
            Note notaA = new Note("Nota A", "cuerpo");
            notaA.setId(s.noteDAO.createNote(notaA));
            s.noteDAO.addTag(notaA, etiquetaA);
            Note notaB = new Note("Nota B", "cuerpo");
            notaB.setId(s.noteDAO.createNote(notaB));
            s.noteDAO.addTag(notaB, etiquetaB);

            List<Note> notasDeA = s.tagDAO.fetchAllNotesWithTag(etiquetaA.getId());

            assertEquals(1, notasDeA.size());
            assertEquals(notaA.getId(), notasDeA.get(0).getId(),
                    "no debe colarse la nota etiquetada con la otra etiqueta");
        }
    }

    /**
     * Hallazgo real, no una suposición: {@code SELECT_ALL_NOTES_TAG_SQL} no filtra por
     * {@code notes.is_deleted}, a diferencia de la consulta equivalente en
     * {@code NoteDAOSQLite} (la que usa {@code fetchNotesByTagId}), que sí lo hace. Una nota
     * en la papelera se sigue devolviendo aquí como "nota con esta etiqueta". Se fija tal
     * cual está hoy, documentado, no arreglado a mitad de una tarea de caracterización.
     */
    @Test
    void fetchAllNotesWithTagIncluyeNotasBorradas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "notas-por-etiqueta-borrada.sqlite")) {
            Tag etiqueta = new Tag("Con nota borrada");
            etiqueta.setId(s.tagDAO.createTag(etiqueta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.noteDAO.addTag(nota, etiqueta);

            s.noteDAO.deleteNote(nota.getId());

            List<Note> notas = s.tagDAO.fetchAllNotesWithTag(etiqueta.getId());
            assertEquals(1, notas.size(),
                    "comportamiento actual: TagDAOSQLite.fetchAllNotesWithTag no filtra notas borradas");
        }
    }

    /** Abre una base de datos SQLite real de usar y tirar, con el esquema de producción. */
    private static final class Sesion implements AutoCloseable {
        final Connection connection;
        final TagDAOSQLite tagDAO;
        final NoteDAOSQLite noteDAO;

        private Sesion(Connection connection) {
            this.connection = connection;
            this.tagDAO = new TagDAOSQLite(connection);
            this.noteDAO = new NoteDAOSQLite(connection);
        }

        static Sesion abrir(Path dir, String nombreFichero) throws Exception {
            SQLiteTestSupport.configureFreshDatabase(dir.resolve(nombreFichero));
            return new Sesion(SQLiteTestSupport.openConnection());
        }

        @Override
        public void close() throws Exception {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }
}
