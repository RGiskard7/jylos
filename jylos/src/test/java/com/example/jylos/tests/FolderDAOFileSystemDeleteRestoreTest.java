package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * {@link FolderDAOFileSystem}: borrado a papelera, restauración, borrado permanente, y
 * {@code fetchTrashFolders} — sobre un directorio real.
 *
 * <p>La mitad de mayor riesgo: {@code permanentlyDeleteFolder} borra ficheros del disco de
 * verdad (no hay marcha atrás) y estaba al 0% de mutación exacto, sin ningún test.</p>
 */
class FolderDAOFileSystemDeleteRestoreTest {

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
    void borrarUnaCarpetaLaMueveFisicamenteALaPapelera() {
        Folder carpeta = new Folder("Efimera");
        carpeta.setId(folderDAO.createFolder(carpeta));

        folderDAO.deleteFolder(carpeta.getId());

        assertFalse(Files.exists(tempDir.resolve("Efimera")), "no debe quedar en su sitio original");
        assertTrue(Files.isDirectory(tempDir.resolve(".trash").resolve("Efimera")),
                "el directorio debe existir físicamente dentro de .trash");
    }

    @Test
    void borrarUnaCarpetaSoloMueveEsaCarpetaNoOtras() {
        Folder aBorrar = new Folder("A borrar");
        aBorrar.setId(folderDAO.createFolder(aBorrar));
        Folder aConservar = new Folder("A conservar");
        aConservar.setId(folderDAO.createFolder(aConservar));

        folderDAO.deleteFolder(aBorrar.getId());

        assertTrue(Files.isDirectory(tempDir.resolve("A conservar")),
                "borrar una carpeta no debe tocar el directorio de otra");
    }

    @Test
    void borrarUnaCarpetaConSubcarpetasLasMueveJuntasAPapelera() {
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);

        folderDAO.deleteFolder(padre.getId());

        assertTrue(Files.isDirectory(tempDir.resolve(".trash").resolve("Padre").resolve("Hija")),
                "la estructura padre/hija debe conservarse dentro de la papelera");
    }

    @Test
    void restaurarUnaCarpetaLaDevuelveFisicamenteASuSitioOriginal() {
        Folder carpeta = new Folder("Recuperable");
        carpeta.setId(folderDAO.createFolder(carpeta));
        folderDAO.deleteFolder(carpeta.getId());

        folderDAO.restoreFolder(".trash/Recuperable");

        assertTrue(Files.isDirectory(tempDir.resolve("Recuperable")), "debe volver a existir en su sitio original");
        assertFalse(Files.exists(tempDir.resolve(".trash").resolve("Recuperable")));
    }

    @Test
    void restaurarLaPapeleraMismaNoHaceNada() {
        // Guarda explícita en el código: normalizedId.equals(".trash") corta antes de mover.
        // Para llegar a esa guarda primero tiene que existir .trash de verdad (restoreFolder
        // comprueba Files.exists(srcPath) antes de la guarda).
        Folder carpeta = new Folder("Cualquiera");
        carpeta.setId(folderDAO.createFolder(carpeta));
        folderDAO.deleteFolder(carpeta.getId());
        assertTrue(Files.isDirectory(tempDir.resolve(".trash")));

        folderDAO.restoreFolder(".trash"); // no debe lanzar ni mover la papelera misma

        assertTrue(Files.isDirectory(tempDir.resolve(".trash")), "la papelera debe seguir existiendo tal cual");
        assertTrue(Files.isDirectory(tempDir.resolve(".trash").resolve("Cualquiera")),
                "su contenido no debe haberse movido a ningún sitio");
    }

    @Test
    void restaurarUnIdQueNoEstaEnPapeleraLanza() {
        assertThrows(com.example.jylos.exceptions.DataAccessException.class,
                () -> folderDAO.restoreFolder(".trash/no-existe"));
    }

    // ── permanentlyDeleteFolder — irreversible, 0% de mutación sin este test ─

    @Test
    void borrarPermanentementeBorraElDirectorioDeVerdadDelDisco() {
        Folder carpeta = new Folder("Definitiva");
        carpeta.setId(folderDAO.createFolder(carpeta));
        folderDAO.deleteFolder(carpeta.getId());
        assertTrue(Files.isDirectory(tempDir.resolve(".trash").resolve("Definitiva")));

        folderDAO.permanentlyDeleteFolder(".trash/Definitiva");

        assertFalse(Files.exists(tempDir.resolve(".trash").resolve("Definitiva")),
                "el directorio debe desaparecer del disco de verdad, no solo de la caché");
    }

    @Test
    void borrarPermanentementeConSubcarpetasBorraTodoElArbolDelDisco() {
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);
        folderDAO.deleteFolder(padre.getId());

        folderDAO.permanentlyDeleteFolder(".trash/Padre");

        assertFalse(Files.exists(tempDir.resolve(".trash").resolve("Padre")),
                "el padre y toda su estructura anidada deben desaparecer, no quedar huérfanos");
    }

    @Test
    void borrarPermanentementeSoloBorraEsaCarpetaNoOtrasEnPapelera() {
        Folder aBorrar = new Folder("A borrar del todo");
        aBorrar.setId(folderDAO.createFolder(aBorrar));
        Folder aConservar = new Folder("A conservar en papelera");
        aConservar.setId(folderDAO.createFolder(aConservar));
        folderDAO.deleteFolder(aBorrar.getId());
        folderDAO.deleteFolder(aConservar.getId());

        folderDAO.permanentlyDeleteFolder(".trash/A borrar del todo");

        assertTrue(Files.isDirectory(tempDir.resolve(".trash").resolve("A conservar en papelera")),
                "borrar una carpeta para siempre no debe tocar otra carpeta también en la papelera");
    }

    // ── fetchTrashFolders: reconstrucción de jerarquía ──────────────────────

    @Test
    void fetchTrashFoldersReflejaLaEstructuraFisicaRealDeLaPapelera() {
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);
        folderDAO.deleteFolder(padre.getId());

        Folder papelera = folderDAO.fetchTrashFolders();

        assertEquals(1, papelera.getChildren().size(), "solo el padre cuelga directo de la papelera");
        Folder padreEnPapelera = (Folder) papelera.getChildren().get(0);
        assertEquals(1, padreEnPapelera.getChildren().size(), "la hija debe seguir anidada bajo el padre");
        assertEquals(padreEnPapelera.getId(),
                ((Folder) padreEnPapelera.getChildren().get(0)).getParent().getId());
    }

    // ── loadSubFolders / loadParentFolders — 0% de mutación, sin ningún test ─

    @Test
    void loadSubFoldersConProfundidadCeroNoTraeNada() {
        Folder raiz = new Folder("Raíz");
        raiz.setId(folderDAO.createFolder(raiz));
        Folder nivel1 = new Folder("Nivel 1");
        nivel1.setId(folderDAO.createFolder(nivel1));
        folderDAO.addSubFolder(raiz, nivel1);

        Folder cargada = new Folder(raiz.getId(), "Raíz");
        folderDAO.loadSubFolders(cargada, 0);

        assertTrue(cargada.getChildren().isEmpty(), "profundidad 0 no debe traer ni el primer nivel");
    }

    @Test
    void loadSubFoldersSinLimiteTraeTodaLaRama() {
        Folder raiz = new Folder("Raíz");
        raiz.setId(folderDAO.createFolder(raiz));
        Folder nivel1 = new Folder("Nivel 1");
        nivel1.setId(folderDAO.createFolder(nivel1));
        folderDAO.addSubFolder(raiz, nivel1);
        Folder nivel2 = new Folder("Nivel 2");
        nivel2.setId(folderDAO.createFolder(nivel2));
        folderDAO.addSubFolder(nivel1, nivel2);

        Folder cargada = new Folder(raiz.getId(), "Raíz");
        folderDAO.loadSubFolders(cargada);

        Folder hijaCargada = (Folder) cargada.getChildren().get(0);
        assertFalse(hijaCargada.isEmpty(), "sin límite debe bajar hasta el final de la rama");
    }

    @Test
    void loadParentFoldersConProfundidadUnoSoloSubeUnNivel() {
        Folder abuelo = new Folder("Abuelo");
        abuelo.setId(folderDAO.createFolder(abuelo));
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        folderDAO.addSubFolder(abuelo, padre);
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);

        Folder cargada = new Folder(hija.getId(), "Hija");
        folderDAO.loadParentFolder(cargada); // atajo: profundidad 1

        assertEquals(padre.getId(), cargada.getParent().getId());
        assertNull(((Folder) cargada.getParent()).getParent(),
                "a diferencia del backend SQLite, aquí profundidad 1 SÍ para en el padre directo");
    }

    /**
     * {@code loadParentFolder(folder)} (el atajo del test anterior) NO llama a
     * {@code loadParentFolders(folder, maxDepth)} — llama directo a {@code getParentFolder}
     * una sola vez. Para ejercitar de verdad el bucle con corte de profundidad finita hace
     * falta llamar a la versión con el parámetro explícito y una jerarquía de 4 niveles,
     * donde profundidad 1 y "sin límite" dan resultados observables distintos.
     */
    @Test
    void loadParentFoldersConProfundidadFinitaExplicitaNoLlegaAUnCuartoNivel() {
        Folder bisabuelo = new Folder("Bisabuelo");
        bisabuelo.setId(folderDAO.createFolder(bisabuelo));
        Folder abuelo = new Folder("Abuelo");
        abuelo.setId(folderDAO.createFolder(abuelo));
        folderDAO.addSubFolder(bisabuelo, abuelo);
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        folderDAO.addSubFolder(abuelo, padre);
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);

        Folder cargada = new Folder(hija.getId(), "Hija");
        folderDAO.loadParentFolders(cargada, 1);

        assertEquals(padre.getId(), cargada.getParent().getId());
        assertNull(((Folder) cargada.getParent()).getParent(),
                "profundidad 1 explícita no debe subir más allá del padre directo");
    }

    @Test
    void loadParentFoldersSinLimiteSubeHastaLaRaiz() {
        Folder abuelo = new Folder("Abuelo");
        abuelo.setId(folderDAO.createFolder(abuelo));
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        folderDAO.addSubFolder(abuelo, padre);
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);

        Folder cargada = new Folder(hija.getId(), "Hija");
        folderDAO.loadParentFolders(cargada);

        assertEquals(abuelo.getId(), ((Folder) cargada.getParent()).getParent().getId());
    }

    // ── loadNotes / getPathFolder / fetchAllFoldersAsTree / getParentFolder ──

    @Test
    void loadNotesRellenaSoloLosMarkdownDeEsaCarpeta() {
        Folder carpeta = new Folder("Con notas");
        carpeta.setId(folderDAO.createFolder(carpeta));
        Note nota = new Note("Nota", "cuerpo");
        nota.setId(noteDAO.createNote(nota));
        folderDAO.addNote(carpeta, nota);

        Folder cargada = new Folder(carpeta.getId(), "Con notas");
        folderDAO.loadNotes(cargada);

        assertEquals(1, cargada.getChildren().size());
    }

    @Test
    void getPathFolderDevuelveLaRutaAbsolutaRealEnDisco() {
        Folder carpeta = new Folder("Trabajo");
        carpeta.setId(folderDAO.createFolder(carpeta));

        String ruta = folderDAO.getPathFolder(carpeta.getId());

        assertEquals(tempDir.resolve("Trabajo").toAbsolutePath().toString(), ruta);
    }

    @Test
    void getPathFolderDeUnIdInexistenteDevuelveNull() {
        assertNull(folderDAO.getPathFolder("no-existe"));
    }

    @Test
    void fetchAllFoldersAsTreeConstruyeLaJerarquiaDesdeLaRaizVirtual() {
        Folder padre = new Folder("Padre");
        padre.setId(folderDAO.createFolder(padre));
        Folder hija = new Folder("Hija");
        hija.setId(folderDAO.createFolder(hija));
        folderDAO.addSubFolder(padre, hija);
        Folder suelta = new Folder("Suelta");
        suelta.setId(folderDAO.createFolder(suelta));

        Folder arbol = folderDAO.fetchAllFoldersAsTree();

        assertEquals(2, arbol.getChildren().size(), "padre y suelta cuelgan directamente de la raíz");
    }

    @Test
    void getParentFolderDeUnaCarpetaEnRaizDevuelveRoot() {
        Folder carpeta = new Folder("Suelta");
        carpeta.setId(folderDAO.createFolder(carpeta));

        assertEquals("ROOT", folderDAO.getParentFolder(carpeta.getId()).getId());
    }
}
