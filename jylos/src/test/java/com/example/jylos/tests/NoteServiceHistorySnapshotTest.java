package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteHistoryService;
import com.example.jylos.service.NoteService;

/**
 * Cuándo {@code persistNote} deja una instantánea de historial y cuándo no
 * ({@code shouldSnapshotHistory}, {@code canResolveStoredHistoryFromDao}).
 *
 * <p>Es lo que decide si una edición se puede deshacer después. Estaba sin ningún test:
 * las dos condiciones sumaban 15 mutaciones, ninguna ejercitada — incluidas las dos
 * llamadas a {@code historyService.snapshot(...)}, que podían borrarse enteras sin que
 * nada se enterara.</p>
 */
class NoteServiceHistorySnapshotTest {

    @Test
    void editarUnaNotaMarkdownDejaUnaInstantaneaDelContenidoAnterior(@TempDir Path vault, @TempDir Path historial)
            throws Exception {
        NoteService notas = servicioPara(vault, historial);
        Note nota = notas.createNote("Diario", "versión uno");

        nota.setContent("versión dos");
        notas.updateNote(nota);

        List<NoteHistoryService.Snapshot> instantaneas = notas.getHistoryService().list(nota.getId());
        assertEquals(1, instantaneas.size(), "una edición de una nota .md debe dejar una instantánea");
        assertEquals("versión uno", notas.getHistoryService().read(instantaneas.get(0)),
                "la instantánea debe guardar el contenido ANTERIOR al cambio, no el nuevo");
    }

    @Test
    void unContenidoAnteriorExplicitoSeUsaEnVezDeIrABuscarloAlDao(@TempDir Path vault, @TempDir Path historial)
            throws Exception {
        NoteService notas = servicioPara(vault, historial);
        Note nota = notas.createNote("Diario", "lo que hay en disco");

        nota.setContent("nuevo contenido");
        // Pasamos un "anterior" distinto del que de verdad hay en disco, a propósito:
        // así se ve cuál de las dos fuentes usa realmente el snapshot.
        notas.updateNote(nota, "contenido distinto al de disco");

        String guardado = notas.getHistoryService().read(notas.getHistoryService().list(nota.getId()).get(0));
        assertEquals("contenido distinto al de disco", guardado,
                "con previousStoredContent explícito, ese es el que se archiva, no el del DAO");
    }

    @Test
    void sinContenidoAnteriorExplicitoSeResuelveDesdeElDao(@TempDir Path vault, @TempDir Path historial)
            throws Exception {
        NoteService notas = servicioPara(vault, historial);
        Note nota = notas.createNote("Diario", "lo que hay en disco");

        nota.setContent("nuevo contenido");
        notas.updateNote(nota); // sin previousStoredContent -> debe ir a buscarlo al DAO

        String guardado = notas.getHistoryService().read(notas.getHistoryService().list(nota.getId()).get(0));
        assertEquals("lo que hay en disco", guardado);
    }

    @Test
    void editarUnCanvasDejaInstantaneaSoloConContenidoAnteriorExplicito(
            @TempDir Path vault, @TempDir Path historial) throws Exception {
        NoteService notas = servicioPara(vault, historial);
        // shouldSnapshotHistory acepta CANVAS, pero canResolveStoredHistoryFromDao no —
        // así que sin previousStoredContent explícito no hay de dónde sacar el "antes".
        Note lienzo = notas.createNote(new Note("tablero.canvas", "{}"));

        lienzo.setContent("{\"nodes\":[]}");
        notas.updateNote(lienzo, "{}");

        assertEquals(1, notas.getHistoryService().list(lienzo.getId()).size(),
                "un canvas SÍ acepta historial si se le da el contenido anterior explícito");
    }

    @Test
    void editarUnCanvasSinContenidoAnteriorExplicitoTampocoDejaInstantanea(
            @TempDir Path vault, @TempDir Path historial) throws Exception {
        // El otro lado de canResolveStoredHistoryFromDao: sin previousStoredContent
        // explícito, un canvas no puede resolverse desde el DAO (solo MARKDOWN puede), así
        // que tampoco debe dejar instantánea por esta vía.
        NoteService notas = servicioPara(vault, historial);
        Note lienzo = notas.createNote(new Note("tablero.canvas", "{}"));

        lienzo.setContent("{\"nodes\":[]}");
        notas.updateNote(lienzo); // sin previousStoredContent

        assertTrue(notas.getHistoryService().list(lienzo.getId()).isEmpty());
    }

    @Test
    void editarUnaImagenNoDejaInstantanea(@TempDir Path vault, @TempDir Path historial) throws Exception {
        // Un adjunto binario tiene que existir ya en disco para que el DAO acepte
        // actualizarlo; lo que importa aquí es que, exista o no historial que archivar,
        // shouldSnapshotHistory corte antes de intentar nada para un tipo que no es
        // MARKDOWN ni CANVAS.
        java.nio.file.Files.write(vault.resolve("foto.png"), new byte[] { 1, 2, 3 });
        NoteService notas = servicioPara(vault, historial);

        Note attachment = new Note("foto.png", "foto.png", "");
        notas.updateNote(attachment, "contenido-que-nunca-deberia-archivarse");

        assertTrue(notas.getHistoryService().list("foto.png").isEmpty(),
                "una imagen no debe generar instantánea de historial nunca");
    }

    @Test
    void alternarFavoritoNoDejaInstantaneaAunqueLaNotaSeaMarkdown(@TempDir Path vault, @TempDir Path historial) {
        // toggleFavorite/togglePinned llaman a persistNote con snapshotHistory=false:
        // un cambio de metadatos no debe generar ruido en el historial de contenido.
        NoteService notas = servicioPara(vault, historial);
        Note nota = notas.createNote("Diario", "contenido");

        notas.toggleFavorite(nota);

        assertTrue(notas.getHistoryService().list(nota.getId()).isEmpty(),
                "cambiar solo el favorito no debe crear una instantánea de contenido");
    }

    private static NoteService servicioPara(Path vault, Path historial) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService notas = new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
        notas.setHistoryService(new NoteHistoryService(historial));
        return notas;
    }
}
