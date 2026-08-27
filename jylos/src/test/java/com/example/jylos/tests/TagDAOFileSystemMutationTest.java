package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * {@link TagDAOFileSystem}: sin tabla propia, las etiquetas se derivan de las notas
 * ({@code note.getTags()}), así que crear/renombrar/borrar una etiqueta es en realidad
 * reescribir las notas que la llevan.
 *
 * <p>Baseline de mutación: 45% killed. {@code deleteTag} al 0% exacto — 16 mutaciones,
 * ninguna cazada — a pesar de ser la única operación irreversible de esta clase (quita la
 * etiqueta de todas las notas, sin papelera para etiquetas).</p>
 */
class TagDAOFileSystemMutationTest {

    @TempDir
    Path tempDir;

    private NoteDAOFileSystem noteDAO;
    private TagDAOFileSystem tagDAO;

    @BeforeEach
    void setUp() {
        noteDAO = new NoteDAOFileSystem(tempDir.toString());
        tagDAO = new TagDAOFileSystem(noteDAO);
    }

    // ── deleteTag — 0% de mutación, sin ningún test ──────────────────────────

    @Test
    void deleteTagLaQuitaDeTodasLasNotasQueLaLlevan() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("efimera"));
        noteDAO.updateNote(nota);

        tagDAO.deleteTag("efimera");

        Note releida = noteDAO.getNoteById(nota.getId());
        assertTrue(releida.getTags().stream().noneMatch(t -> "efimera".equals(t.getTitle())),
                "la etiqueta debe desaparecer de la nota persistida, no solo del objeto en memoria");
    }

    @Test
    void deleteTagSoloQuitaEsaEtiquetaNoOtrasEnLaMismaNota() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("a-borrar"));
        nota.addTag(new Tag("a-conservar"));
        noteDAO.updateNote(nota);

        tagDAO.deleteTag("a-borrar");

        Note releida = noteDAO.getNoteById(nota.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "a-conservar".equals(t.getTitle())),
                "borrar una etiqueta no debe tocar otras etiquetas de la misma nota");
    }

    @Test
    void deleteTagSoloTocaLasNotasQueLaLlevanNoOtras() {
        Note conEtiqueta = crear("Con etiqueta", "cuerpo");
        conEtiqueta.addTag(new Tag("a-borrar"));
        noteDAO.updateNote(conEtiqueta);
        Note sinEtiqueta = crear("Sin etiqueta", "cuerpo");
        sinEtiqueta.addTag(new Tag("otra"));
        noteDAO.updateNote(sinEtiqueta);

        tagDAO.deleteTag("a-borrar");

        Note releida = noteDAO.getNoteById(sinEtiqueta.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "otra".equals(t.getTitle())),
                "borrar una etiqueta no debe tocar las etiquetas de una nota que no la lleva");
    }

    @Test
    void deleteTagConIdNuloOVacioNoLanzaYNoTocaNada() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("intacta"));
        noteDAO.updateNote(nota);

        tagDAO.deleteTag(null); // no debe lanzar
        tagDAO.deleteTag("");   // no debe lanzar

        Note releida = noteDAO.getNoteById(nota.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "intacta".equals(t.getTitle())));
    }

    // ── existsByTitle — mayormente sin cubrir ────────────────────────────────

    @Test
    void existsByTitleDistingueEtiquetasUsadasDeLasQueNo() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("usada"));
        noteDAO.updateNote(nota);

        assertTrue(tagDAO.existsByTitle("usada"));
        assertFalse(tagDAO.existsByTitle("no-existe"));
    }

    @Test
    void existsByTitleEsInsensibleAMayusculas() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("Proyecto"));
        noteDAO.updateNote(nota);

        assertTrue(tagDAO.existsByTitle("proyecto"),
                "una etiqueta inline conserva la mayúscula tal cual se escribió, pero la búsqueda debe ser insensible");
    }

    @Test
    void existsByTitleConNuloOVacioDevuelveFalse() {
        assertFalse(tagDAO.existsByTitle(null));
        assertFalse(tagDAO.existsByTitle(""));
    }

    // ── updateTag (renombrar) — supervivientes ───────────────────────────────

    @Test
    void updateTagRenombraLaEtiquetaEnTodasLasNotasQueLaLlevan() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("antiguo"));
        noteDAO.updateNote(nota);

        tagDAO.updateTag(new Tag("antiguo", "nuevo"));

        Note releida = noteDAO.getNoteById(nota.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "nuevo".equals(t.getTitle())));
        assertFalse(releida.getTags().stream().anyMatch(t -> "antiguo".equals(t.getTitle())));
    }

    @Test
    void updateTagSoloRenombraEsaEtiquetaNoOtrasEnLaMismaNota() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("a-renombrar"));
        nota.addTag(new Tag("intacta"));
        noteDAO.updateNote(nota);

        tagDAO.updateTag(new Tag("a-renombrar", "renombrada"));

        Note releida = noteDAO.getNoteById(nota.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "intacta".equals(t.getTitle())),
                "renombrar una etiqueta no debe tocar otra etiqueta de la misma nota");
    }

    @Test
    void updateTagSoloTocaLasNotasQueLaLlevanNoOtras() {
        Note conEtiqueta = crear("Con etiqueta", "cuerpo");
        conEtiqueta.addTag(new Tag("a-renombrar"));
        noteDAO.updateNote(conEtiqueta);
        Note otra = crear("Otra", "cuerpo");
        otra.addTag(new Tag("sin-tocar"));
        noteDAO.updateNote(otra);

        tagDAO.updateTag(new Tag("a-renombrar", "renombrada"));

        Note releida = noteDAO.getNoteById(otra.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "sin-tocar".equals(t.getTitle())),
                "renombrar una etiqueta no debe tocar una nota que no la lleva");
    }

    @Test
    void updateTagAlMismoNombreNoHaceNada() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("estable"));
        noteDAO.updateNote(nota);

        tagDAO.updateTag(new Tag("estable", "estable"));

        Note releida = noteDAO.getNoteById(nota.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "estable".equals(t.getTitle())));
    }

    @Test
    void updateTagConTituloNuevoVacioNoHaceNada() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("original"));
        noteDAO.updateNote(nota);

        tagDAO.updateTag(new Tag("original", "  "));

        Note releida = noteDAO.getNoteById(nota.getId());
        assertTrue(releida.getTags().stream().anyMatch(t -> "original".equals(t.getTitle())),
                "un título nuevo en blanco no debe borrar ni vaciar la etiqueta existente");
    }

    // ── getTagById / fetchAllTags ─────────────────────────────────────────────

    @Test
    void getTagByIdDevuelveUnaEtiquetaConIdIgualATitulo() {
        Tag tag = tagDAO.getTagById("cualquiera");

        assertEquals("cualquiera", tag.getId());
        assertEquals("cualquiera", tag.getTitle());
    }

    @Test
    void fetchAllTagsDevuelveLasEtiquetasDeTodasLasNotas() {
        Note a = crear("A", "cuerpo");
        a.addTag(new Tag("uno"));
        noteDAO.updateNote(a);
        Note b = crear("B", "cuerpo");
        b.addTag(new Tag("dos"));
        noteDAO.updateNote(b);

        List<Tag> todas = tagDAO.fetchAllTags();

        assertEquals(2, todas.size());
    }

    /**
     * Hallazgo documentado en el propio código (comentario en fetchAllTags): una etiqueta
     * `#inline` parseada de una nota llega sin id ({@code new Tag(title)} lo deja a null).
     * Sin rellenarlo con el título, todo lo que dependa del id (updateTag, TagService)
     * falla en silencio sobre esa etiqueta. Contar cuántas etiquetas hay no basta para
     * comprobarlo — hay que mirar el id de la que vuelve.
     */
    @Test
    void fetchAllTagsRellenaElIdConElTituloParaTagsInline() {
        Note nota = crear("Nota", "cuerpo");
        nota.addTag(new Tag("sin-id-original")); // Tag(String) deja el id a null
        noteDAO.updateNote(nota);

        Tag etiqueta = tagDAO.fetchAllTags().get(0);

        assertEquals("sin-id-original", etiqueta.getId(),
                "el id debe rellenarse con el título, no quedar null");
    }

    private Note crear(String title, String content) {
        Note note = new Note(title, content);
        note.setId(noteDAO.createNote(note));
        return note;
    }
}
