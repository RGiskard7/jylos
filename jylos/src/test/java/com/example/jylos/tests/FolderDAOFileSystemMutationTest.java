package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * {@link FolderDAOFileSystem}: CRUD, renombrado y relación con notas y subcarpetas, sobre
 * un directorio real de bóveda-en-ficheros.
 *
 * <p>Backend simétrico a {@code FolderDAOSQLite} (dos almacenamientos posibles en Jylos),
 * caracterizado tras el lado SQLite. Baseline de mutación al medir: 42% killed, 90 de 268
 * mutaciones sin cobertura — {@code updateFolder}, {@code existsByTitle},
 * {@code removeSubFolder} al 0% exacto.</p>
 */
class FolderDAOFileSystemMutationTest {

    @TempDir
    Path tempDir;

    private FolderDAOFileSystem folderDAO;
    private NoteDAOFileSystem noteDAO;

    @BeforeEach
    void setUp() {
        folderDAO = new FolderDAOFileSystem(tempDir.toString());
        noteDAO = new NoteDAOFileSystem(tempDir.toString());
    }

    @Test
    void crearUnaCarpetaCreaElDirectorioRealYAsignaUnId() {
        Folder carpeta = new Folder("Trabajo");
        String id = folderDAO.createFolder(carpeta);

        assertNotNull(id);
        assertTrue(Files.isDirectory(tempDir.resolve("Trabajo")));
        assertEquals("Trabajo", folderDAO.getFolderById(id).getTitle());
    }

    @Test
    void crearDosCarpetasConElMismoTituloNoColisiona() {
        folderDAO.createFolder(new Folder("Repetida"));
        String id2 = folderDAO.createFolder(new Folder("Repetida"));

        assertTrue(Files.isDirectory(tempDir.resolve("Repetida (1)")),
                "el conflicto de nombre debe resolverse con sufijo, no sobrescribir");
        assertEquals("Repetida (1)", folderDAO.getFolderById(id2).getTitle());
    }

    // ── updateFolder (renombrar) — 0% de mutación, sin ningún test ──────────

    @Test
    void actualizarUnaCarpetaRenombraElDirectorioRealEnDisco() {
        Folder carpeta = new Folder("Antiguo");
        carpeta.setId(folderDAO.createFolder(carpeta));

        carpeta.setTitle("Nuevo");
        folderDAO.updateFolder(carpeta);

        assertFalse(Files.exists(tempDir.resolve("Antiguo")), "el directorio viejo no debe quedar");
        assertTrue(Files.isDirectory(tempDir.resolve("Nuevo")), "debe existir el directorio con el nombre nuevo");
        assertEquals("Nuevo", folderDAO.getFolderById(carpeta.getId()).getTitle());
    }

    @Test
    void actualizarUnaCarpetaSoloRenombraEsaCarpetaNoOtras() {
        Folder aRenombrar = new Folder("A renombrar");
        aRenombrar.setId(folderDAO.createFolder(aRenombrar));
        Folder aConservar = new Folder("A conservar");
        aConservar.setId(folderDAO.createFolder(aConservar));

        aRenombrar.setTitle("Renombrada");
        folderDAO.updateFolder(aRenombrar);

        assertTrue(Files.isDirectory(tempDir.resolve("A conservar")),
                "renombrar una carpeta no debe tocar el directorio de otra");
    }

    @Test
    void actualizarUnaCarpetaConIdInexistenteNoLanzaYNoCreaNada() {
        Folder fantasma = new Folder("no-existe", "Fantasma");

        folderDAO.updateFolder(fantasma); // no debe lanzar

        assertFalse(Files.exists(tempDir.resolve("Fantasma")));
    }

    @Test
    void actualizarUnaCarpetaAlMismoNombreNoHaceNada() {
        Folder carpeta = new Folder("Estable");
        carpeta.setId(folderDAO.createFolder(carpeta));

        folderDAO.updateFolder(carpeta); // título sin cambiar

        assertTrue(Files.isDirectory(tempDir.resolve("Estable")), "el directorio debe seguir existiendo tal cual");
    }

    // ── existsByTitle — 0% de mutación ───────────────────────────────────────

    @Test
    void existsByTitleDistingueCarpetasCreadasDeLasQueNo() {
        folderDAO.createFolder(new Folder("Única"));

        assertTrue(folderDAO.existsByTitle("Única"));
        assertFalse(folderDAO.existsByTitle("No existe"));
    }

    // ── addNote / removeNote ─────────────────────────────────────────────────

    @Test
    void addNoteMueveElFicheroRealALaCarpetaDestino() {
        Folder carpeta = new Folder("Destino");
        carpeta.setId(folderDAO.createFolder(carpeta));
        Note nota = new Note("Nota", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        String idOriginal = nota.getId();

        folderDAO.addNote(carpeta, nota);

        assertFalse(Files.exists(tempDir.resolve(idOriginal)), "el fichero no debe seguir en la raíz");
        assertTrue(Files.isRegularFile(tempDir.resolve("Destino").resolve("Nota.md")),
                "el fichero debe existir físicamente dentro de la carpeta destino");
        assertEquals(carpeta.getId(), nota.getParent().getId());
    }

    @Test
    void removeNoteMueveElFicheroRealDeVueltaALaRaiz() {
        Folder carpeta = new Folder("Origen");
        carpeta.setId(folderDAO.createFolder(carpeta));
        Note nota = new Note("Nota", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        folderDAO.addNote(carpeta, nota);

        folderDAO.removeNote(carpeta, nota);

        assertTrue(Files.isRegularFile(tempDir.resolve("Nota.md")), "el fichero debe volver a la raíz");
        // Comportamiento real: a diferencia del backend SQLite, getFolderByNoteId de una
        // nota en la raíz devuelve el objeto ROOT, no null.
        assertEquals("ROOT", folderDAO.getFolderByNoteId(nota.getId()).getId());
    }

    // ── addSubFolder / removeSubFolder ──────────────────────────────────────

    @Test
    void addSubFolderMueveElDirectorioRealBajoElPadre() {
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));

        folderDAO.addSubFolder(padre, hija);

        assertFalse(Files.isDirectory(tempDir.resolve("Hija")), "ya no debe estar en la raíz");
        assertTrue(Files.isDirectory(tempDir.resolve("Padre").resolve("Hija")),
                "debe existir físicamente dentro del directorio padre");
        assertEquals(padre.getId(), hija.getParent().getId());
    }

    @Test
    void removeSubFolderLaEnviaALaPapeleraComoUnBorradoNormal() {
        // removeSubFolder delega en deleteFolder(id) — no es una operación distinta,
        // así que "quitar" una subcarpeta la manda a la papelera, no la deja suelta.
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);

        folderDAO.removeSubFolder(padre, hija);

        assertFalse(Files.exists(tempDir.resolve("Padre").resolve("Hija")));
        assertTrue(Files.isDirectory(tempDir.resolve(".trash").resolve("Padre").resolve("Hija")),
                "comportamiento real: removeSubFolder no deja la carpeta suelta, la borra a papelera");
    }

    // ── moveFolderToRoot / moveNoteToRoot ───────────────────────────────────

    @Test
    void moveFolderToRootMueveElDirectorioRealFueraDelPadre() {
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);

        folderDAO.moveFolderToRoot(hija);

        assertTrue(Files.isDirectory(tempDir.resolve("Hija")), "debe volver a la raíz físicamente");
        assertFalse(Files.exists(tempDir.resolve("Padre").resolve("Hija")));
        assertNull(hija.getParent());
    }

    @Test
    void moveNoteToRootMueveElFicheroRealFueraDeLaCarpeta() {
        Folder carpeta = new Folder("Origen");
        carpeta.setId(folderDAO.createFolder(carpeta));
        Note nota = new Note("Nota", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        folderDAO.addNote(carpeta, nota);

        folderDAO.moveNoteToRoot(nota);

        assertTrue(Files.isRegularFile(tempDir.resolve("Nota.md")));
        assertEquals("ROOT", folderDAO.getFolderByNoteId(nota.getId()).getId());
    }

    // ── fetchAllFoldersAsList / getFolderById ────────────────────────────────

    @Test
    void fetchAllFoldersAsListNoIncluyeCarpetasOcultas() {
        // Vía real para llegar al filtro de fetchAllFoldersAsList: createFolder() permite
        // puntos en el título (sanitizeFilename no los quita) e inserta directo en
        // idToPathMap, sin pasar por el filtro de directorios ocultos de refreshCache() —
        // ese filtro solo actúa durante el walk, no en la inserción directa de createFolder.
        // Creando el directorio oculto a mano y refrescando caché (como en un intento
        // anterior de este test) el filtro de refreshCache ya lo excluye antes de llegar
        // aquí, y el de fetchAllFoldersAsList nunca se ejercita de verdad.
        folderDAO.createFolder(new Folder("Visible"));
        folderDAO.createFolder(new Folder(".oculta"));

        assertTrue(folderDAO.fetchAllFoldersAsList().stream().noneMatch(f -> f.getTitle().equals(".oculta")),
                "una carpeta oculta no debe listarse como carpeta de usuario");
    }
}
