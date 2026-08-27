package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Búsqueda y recuperación: {@code searchNotes}, {@code searchNotesInFolder},
 * {@code matchesSearch}, {@code getNotesByTag}, {@code getNotesByFolder},
 * {@code findNoteByTitle}, {@code getFavoriteNotes}, {@code getRecentNotes}.
 *
 * <p>Sin datos que perder aquí — son todo lecturas —, pero es la superficie por la que
 * pasa cada búsqueda y cada filtro de la aplicación. Estaba al 0% en las siete: ninguna
 * ejercitada ni una vez.</p>
 */
class NoteServiceSearchAndRetrievalTest {

    // ── searchNotes / matchesSearch ─────────────────────────────────────────

    @Test
    void encuentraPorTituloOPorContenidoSinImportarMayusculas(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Recetas de cocina", "nada relevante");
        notas.createNote("Diario", "hoy cociné una TARTA de manzana");
        notas.createNote("Compras", "leche, pan, huevos");

        List<String> encontradas = titulos(notas.searchNotes("tarta"));

        assertEquals(List.of("Diario"), encontradas, "debe encontrar por contenido, sin importar mayúsculas");
    }

    @Test
    void buscarPorTituloTambienFunciona(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Recetas de cocina", "");
        notas.createNote("Compras", "");

        assertEquals(List.of("Recetas de cocina"), titulos(notas.searchNotes("RECETAS")));
    }

    @Test
    void unaConsultaVaciaODeSoloEspaciosDevuelveTodo(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("A", "");
        notas.createNote("B", "");

        assertEquals(2, notas.searchNotes("").size());
        assertEquals(2, notas.searchNotes("   ").size());
        assertEquals(2, notas.searchNotes(null).size());
    }

    @Test
    void unaNotaSinCoincidenciaQuedaFuera(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Diario", "cualquier cosa");

        assertTrue(notas.searchNotes("palabra-que-no-aparece-en-ningun-sitio").isEmpty());
    }

    // ── searchNotesInFolder ──────────────────────────────────────────────────

    @Test
    void buscarDentroDeUnaCarpetaSoloMiraSusPropiasNotas(@TempDir Path vault) {
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService notas = new NoteService(noteDao, folderDao);
        FolderService carpetas = new FolderService(folderDao, noteDao);

        Folder carpeta = carpetas.createFolder("Trabajo");
        Note dentro = notas.createNoteInFolder("Informe", "presupuesto anual", carpeta);
        notas.createNote("Fuera", "presupuesto también, pero en la raíz");

        List<String> resultado = titulos(notas.searchNotesInFolder("presupuesto", carpeta));

        assertEquals(List.of(dentro.getTitle()), resultado,
                "no debe colarse una nota que coincide pero vive fuera de la carpeta");
    }

    @Test
    void buscarSinCarpetaBuscaEnTodaLaBoveda(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Diario", "presupuesto");

        assertEquals(1, notas.searchNotesInFolder("presupuesto", null).size());
    }

    @Test
    void enUnaCarpetaSinConsultaDevuelveTodasSusNotas(@TempDir Path vault) {
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService notas = new NoteService(noteDao, folderDao);
        FolderService carpetas = new FolderService(folderDao, noteDao);
        Folder carpeta = carpetas.createFolder("Trabajo");
        notas.createNoteInFolder("Una", "x", carpeta);
        notas.createNoteInFolder("Otra", "y", carpeta);

        assertEquals(2, notas.searchNotesInFolder("", carpeta).size());
    }

    // ── getNotesByTag / getNotesByFolder ─────────────────────────────────────

    @Test
    void notasConUnaEtiquetaConcreta(@TempDir Path vault) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService notas = new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
        TagService etiquetas = new TagService(new TagDAOFileSystem(noteDao), noteDao);

        Note conEtiqueta = notas.createNote("Con etiqueta", "");
        notas.createNote("Sin etiqueta", "");
        Tag trabajo = etiquetas.getOrCreateTag("trabajo");
        etiquetas.addTagToNote(conEtiqueta, trabajo);

        List<Note> resultado = notas.getNotesByTag(trabajo);

        assertEquals(1, resultado.size());
        assertEquals("Con etiqueta", resultado.get(0).getTitle());
    }

    @Test
    void unaEtiquetaSinIdDevuelveListaVaciaSinPreguntarAlDao(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Cualquiera", "");

        assertTrue(notas.getNotesByTag(new Tag("sin-id")).isEmpty());
        assertTrue(notas.getNotesByTag(null).isEmpty());
    }

    @Test
    void unaCarpetaNulaODesconocidaDevuelveTodasLasNotas(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Única", "");

        assertEquals(1, notas.getNotesByFolder(null).size());
        assertEquals(1, notas.getNotesByFolder(new Folder("carpeta-sin-id-real")).size());
    }

    // ── findNoteByTitle ──────────────────────────────────────────────────────

    @Test
    void encuentraPorTituloExactoSinImportarMayusculas(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Mi Diario", "");

        assertTrue(notas.findNoteByTitle("mi diario").isPresent());
        assertTrue(notas.findNoteByTitle("MI DIARIO").isPresent());
    }

    @Test
    void unTituloEnBlancoNoBuscaNada(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Diario", "");

        assertFalse(notas.findNoteByTitle("").isPresent());
        assertFalse(notas.findNoteByTitle("   ").isPresent());
        assertFalse(notas.findNoteByTitle(null).isPresent());
    }

    @Test
    void unTituloQueNoExisteNoSeEncuentra(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        notas.createNote("Diario", "");

        assertFalse(notas.findNoteByTitle("no existe").isPresent());
    }

    // ── getFavoriteNotes / getRecentNotes ────────────────────────────────────

    @Test
    void soloDevuelveLasMarcadasComoFavoritas(@TempDir Path vault) {
        NoteService notas = servicioPara(vault);
        Note favorita = notas.createNote("Favorita", "");
        notas.createNote("Normal", "");
        notas.toggleFavorite(favorita);

        List<Note> resultado = notas.getFavoriteNotes();

        assertEquals(1, resultado.size());
        assertEquals("Favorita", resultado.get(0).getTitle());
    }

    @Test
    void recientesRespetaElLimite(@TempDir Path vault) throws InterruptedException {
        NoteService notas = servicioPara(vault);
        notas.createNote("Una", "");
        Thread.sleep(5);
        notas.createNote("Dos", "");
        Thread.sleep(5);
        notas.createNote("Tres", "");

        List<Note> recientes = notas.getRecentNotes(2);

        assertEquals(2, recientes.size(), "debe recortar a como mucho el límite pedido");
    }

    private static List<String> titulos(List<Note> notas) {
        return notas.stream().map(Note::getTitle).sorted().toList();
    }

    private static NoteService servicioPara(Path vault) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        return new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
    }
}
