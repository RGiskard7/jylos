package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.exceptions.NoteException;
import com.example.jylos.service.NoteService;

/**
 * {@code createNote} frente a un DAO que se comporta mal — algo que el DAO real de
 * ficheros nunca hace, así que hace falta un doble mínimo para alcanzar estas ramas.
 *
 * <p>Dos casos: el DAO no consigue crear el fichero y devuelve un id vacío (debe
 * lanzar, no devolver una nota fantasma), y el DAO devuelve un id sin habérselo
 * asignado ya al propio objeto {@code Note} (comprueba que la responsabilidad de
 * {@code NoteService.createNote} de asignarlo es real y no un paso redundante que
 * el DAO de ficheros ya hace por su cuenta).</p>
 */
class NoteServiceCreateFailureTest {

    @Test
    void unIdVacioDevueltoPorElDaoHaceQueCreateNoteLance() {
        NoteService notas = new NoteService(new DaoConIdVacio(), null);

        NoteException error = assertThrows(NoteException.class,
                () -> notas.createNote("Título", "cuerpo"));
        assertEquals("Failed to create note: Título", error.getMessage());
    }

    @Test
    void createNoteAsignaElIdDevueltoPorElDaoAlPropioObjeto() {
        // Este DAO deliberadamente NO llama a note.setId(...) — a diferencia del DAO real
        // de ficheros, que sí lo hace internamente. Si NoteService dejara de asignarlo,
        // la nota devuelta se quedaría sin id.
        NoteService notas = new NoteService(new DaoQueNoAutoAsignaId(), null);

        Note creada = notas.createNote("Título", "cuerpo");

        assertEquals("id-del-dao", creada.getId());
    }

    /** El mínimo necesario de {@link NoteDAO} para forzar un id vacío al crear. */
    private static class DaoConIdVacio implements NoteDAO {
        @Override
        public String createNote(Note note) {
            return "";
        }

        @Override
        public Note getNoteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNote(Note note) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteNote(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void permanentlyDeleteNote(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void restoreNote(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Note> fetchTrashNotes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Note> fetchAllNotes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Note> fetchNotesByFolderId(String folderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fetchNotesByFolderId(Folder folder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Folder getFolderOfNote(String noteId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addTag(String noteId, String tagId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addTag(Note note, Tag tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeTag(String noteId, String tagId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeTag(Note note, Tag tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Tag> fetchTags(String noteId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void loadTags(Note note) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Note> fetchNotesByTagId(String tagId) {
            throw new UnsupportedOperationException();
        }
    }

    /** Igual que el anterior, pero devuelve un id real sin asignárselo a la nota. */
    private static final class DaoQueNoAutoAsignaId extends DaoConIdVacio {
        @Override
        public String createNote(Note note) {
            return "id-del-dao";
        }
    }
}
