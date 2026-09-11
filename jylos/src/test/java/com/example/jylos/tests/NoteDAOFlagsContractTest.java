package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Note;

/**
 * Suite de contrato: el MISMO cuerpo de test corre contra {@code NoteDAOSQLite} y
 * {@code NoteDAOFileSystem} (subclases {@link NoteDAOFlagsContractSQLiteTest} y
 * {@link NoteDAOFlagsContractFileSystemTest}), afirmando comportamiento observable
 * idéntico para favorito/pinned/borrado+papelera/privado.
 *
 * <p>Complementa a {@link NoteAttributeParityGuardTest} — ese test solo escanea texto
 * (nombres de columna SQL / clave de frontmatter existen); esta suite ejecuta el DAO de
 * verdad y comprueba el resultado observable (releer del backend real tras cada mutación).
 * No sustituye nada, no se toca {@link NoteAttributeParityGuardTest}.</p>
 *
 * <p>Deliberadamente NO hay clase base compartida en {@code data.dao} — SQL transaccional
 * frente a mover fichero + caché + metadata sidecar + sufijo de colisión son mecánicas
 * demasiado distintas para justificar una abstracción de producción compartida. Ver
 * plan guardado ("Fase 2") y el propio docstring de {@link NoteAttributeParityGuardTest}.</p>
 */
abstract class NoteDAOFlagsContractTest {

    /** Cada subclase entrega un DAO real, recién creado, con almacenamiento vacío y temporal. */
    protected abstract NoteDAO createNoteDAO();

    private Note createAndPersist(NoteDAO dao, String title) {
        Note note = new Note(title, "cuerpo de " + title);
        String id = dao.createNote(note);
        note.setId(id);
        return note;
    }

    @Test
    void marcarFavoritoPersisteTrasReleerDelBackendReal() {
        NoteDAO dao = createNoteDAO();
        Note note = createAndPersist(dao, "Favorita");

        note.setFavorite(true);
        dao.updateNote(note);

        Note releida = dao.getNoteById(note.getId());
        assertNotNull(releida, "la nota debe seguir existiendo tras el update");
        assertTrue(releida.isFavorite(), "favorite=true debe persistir en el backend real");
    }

    @Test
    void desmarcarFavoritoPersisteTrasReleerDelBackendReal() {
        NoteDAO dao = createNoteDAO();
        Note note = createAndPersist(dao, "ExFavorita");
        note.setFavorite(true);
        dao.updateNote(note);

        note.setFavorite(false);
        dao.updateNote(note);

        Note releida = dao.getNoteById(note.getId());
        assertNotNull(releida);
        assertFalse(releida.isFavorite(), "favorite=false debe persistir, no quedarse en true");
    }

    @Test
    void marcarPinnedPersisteTrasReleerDelBackendReal() {
        NoteDAO dao = createNoteDAO();
        Note note = createAndPersist(dao, "Pinneada");

        note.setPinned(true);
        dao.updateNote(note);

        Note releida = dao.getNoteById(note.getId());
        assertNotNull(releida);
        assertTrue(releida.isPinned(), "pinned=true debe persistir en el backend real");
    }

    @Test
    void moverAPapeleraApareceEnFetchTrashNotesYDesapareceDelFetchNormal() {
        NoteDAO dao = createNoteDAO();
        Note note = createAndPersist(dao, "ParaBorrar");

        dao.deleteNote(note.getId());

        List<Note> trash = dao.fetchTrashNotes();
        assertFalse(trash.isEmpty(), "fetchTrashNotes debe devolver al menos la nota borrada");
        assertTrue(trash.stream().anyMatch(Note::isDeleted),
                "toda nota en fetchTrashNotes debe reportar isDeleted()==true");

        List<Note> todas = dao.fetchAllNotes();
        assertTrue(todas.stream().noneMatch(n -> "ParaBorrar".equals(n.getTitle())),
                "una nota borrada no debe aparecer en fetchAllNotes()");
    }

    @Test
    void restaurarReaparaceEnFetchAllNotesConIsDeletedFalse() {
        NoteDAO dao = createNoteDAO();
        Note note = createAndPersist(dao, "ParaRestaurar");
        dao.deleteNote(note.getId());

        List<Note> trash = dao.fetchTrashNotes();
        Note trashed = trash.stream().filter(n -> n.getTitle().equals("ParaRestaurar")).findFirst()
                .orElseThrow(() -> new AssertionError("la nota borrada debe aparecer en fetchTrashNotes antes de restaurar"));

        dao.restoreNote(trashed.getId());

        List<Note> todas = dao.fetchAllNotes();
        Note restaurada = todas.stream().filter(n -> "ParaRestaurar".equals(n.getTitle())).findFirst()
                .orElseThrow(() -> new AssertionError("la nota restaurada debe reaparecer en fetchAllNotes"));
        assertFalse(restaurada.isDeleted(), "tras restoreNote, isDeleted() debe volver a false");

        List<Note> trashTrasRestaurar = dao.fetchTrashNotes();
        assertTrue(trashTrasRestaurar.stream().noneMatch(n -> "ParaRestaurar".equals(n.getTitle())),
                "tras restaurar, la nota no debe seguir en la papelera");
    }

    @Test
    void privadaSobreviveIntactaAFavoritoPinnedBorradoYRestaurado() {
        NoteDAO dao = createNoteDAO();
        Note note = createAndPersist(dao, "Privada");
        note.setPrivate(true);
        dao.updateNote(note);

        // Sobrevive a marcar favorito
        note.setFavorite(true);
        dao.updateNote(note);
        Note tras1 = dao.getNoteById(note.getId());
        assertTrue(tras1.isPrivate(), "private debe sobrevivir a marcar favorito");

        // Sobrevive a marcar pinned
        tras1.setPinned(true);
        dao.updateNote(tras1);
        Note tras2 = dao.getNoteById(note.getId());
        assertTrue(tras2.isPrivate(), "private debe sobrevivir a marcar pinned");

        // Sobrevive a borrado
        dao.deleteNote(tras2.getId());
        List<Note> trash = dao.fetchTrashNotes();
        Note trashed = trash.stream().filter(n -> "Privada".equals(n.getTitle())).findFirst()
                .orElseThrow(() -> new AssertionError("la nota privada borrada debe estar en la papelera"));
        assertTrue(trashed.isPrivate(), "private debe sobrevivir al borrado (papelera)");

        // Sobrevive a restauración
        dao.restoreNote(trashed.getId());
        List<Note> todas = dao.fetchAllNotes();
        Note restaurada = todas.stream().filter(n -> "Privada".equals(n.getTitle())).findFirst()
                .orElseThrow(() -> new AssertionError("la nota privada restaurada debe reaparecer"));
        assertTrue(restaurada.isPrivate(), "private debe sobrevivir a la restauración completa");
        assertEquals(true, restaurada.isFavorite(), "favorite también debía seguir intacto tras todo el ciclo");
        assertEquals(true, restaurada.isPinned(), "pinned también debía seguir intacto tras todo el ciclo");
    }
}
