package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;

/**
 * {@code togglePinned}: sin ningún test hasta ahora — 0% de mutación, las seis
 * mutaciones sin cazar. Es el gemelo exacto de {@code toggleFavorite}, que sí estaba
 * caracterizado desde la Fase 5; este método se había quedado fuera sin más.
 */
class NoteServiceTogglePinnedTest {

    @Test
    void fijarUnaNotaCambiaElEstadoYLoPersiste(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        Note nota = notas.createNote("Diario", "cuerpo");
        assertFalse(nota.isPinned(), "una nota nueva no empieza fijada");

        boolean resultado = notas.togglePinned(nota);

        assertTrue(resultado, "el método debe devolver el nuevo estado");
        assertTrue(nota.isPinned());
        assertTrue(notas.getNoteById(nota.getId()).orElseThrow().isPinned(),
                "el cambio debe llegar a disco, no quedarse solo en memoria");
    }

    @Test
    void volverAAlternarLaDesfija(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        Note nota = notas.createNote("Diario", "cuerpo");
        notas.togglePinned(nota);

        boolean resultado = notas.togglePinned(nota);

        assertFalse(resultado);
        assertFalse(notas.getNoteById(nota.getId()).orElseThrow().isPinned());
    }

    @Test
    void conNotaNulaLanzaEnVezDeContinuar(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        assertThrows(IllegalArgumentException.class, () -> notas.togglePinned(null));
    }

    @Test
    void fijarNoAlteraElFavorito(@TempDir Path vault) {
        // Cubre que la llamada real es a setPinned, no a setFavorite por un
        // copia-y-pega del método hermano.
        NoteService notas = servicioPara(vault);
        Note nota = notas.createNote("Diario", "cuerpo");

        notas.togglePinned(nota);

        assertFalse(nota.isFavorite(), "togglePinned no debe tocar el estado de favorito");
        assertEquals("cuerpo", notas.getNoteById(nota.getId()).orElseThrow().getContent(),
                "ni tocar el contenido");
    }

    private static NoteService servicioPara(Path vault) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        return new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
    }
}
