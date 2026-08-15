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

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;

/**
 * {@link FolderDAOSQLite}: lecturas de árbol — {@code loadSubFolders}/
 * {@code loadParentFolders} con límite de profundidad, {@code fetchAllFoldersAsTree},
 * {@code getPathFolder}, {@code loadNotes}, {@code getParentFolder}.
 *
 * <p>Sin riesgo de pérdida de datos aquí, pero es lo que construye el árbol que ve el
 * usuario en el panel lateral. {@code loadSubFoldersHelper} y
 * {@code loadParentFoldersHelper} estaban al 0%, incluido el límite de profundidad, que
 * es la única parte no trivial de estos dos métodos.</p>
 */
class FolderDAOSQLiteTreeTest {

    @Test
    void loadSubFoldersConProfundidadCeroSoloTraeElPrimerNivel(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "profundidad.sqlite")) {
            Folder raiz = new Folder("Raíz");
            raiz.setId(s.folderDAO.createFolder(raiz));
            Folder nivel1 = new Folder("Nivel 1");
            nivel1.setId(s.folderDAO.createFolder(nivel1));
            s.folderDAO.addSubFolder(raiz, nivel1);
            Folder nivel2 = new Folder("Nivel 2");
            nivel2.setId(s.folderDAO.createFolder(nivel2));
            s.folderDAO.addSubFolder(nivel1, nivel2);

            Folder cargada = new Folder(raiz.getId(), "Raíz");
            s.folderDAO.loadSubFolders(cargada, 0);

            assertEquals(1, cargada.getChildren().size(), "profundidad 0 trae solo el primer nivel");
            Folder hijaCargada = (Folder) cargada.getChildren().get(0);
            assertTrue(hijaCargada.isEmpty(), "y no debe bajar más allá de ese nivel");
            assertEquals(cargada.getId(), hijaCargada.getParent().getId(),
                    "el enlace de vuelta al padre también debe reconstruirse");
        }
    }

    /**
     * Mismo off-by-one que con profundidad 1: el corte es {@code currentDepth > maxDepth}
     * con {@code currentDepth} arrancando en 0, así que profundidad 0 igualmente deja
     * pasar un nivel completo (el padre directo), no se queda sin subir nada.
     */
    @Test
    void loadParentFoldersConProfundidadCeroTraeElPadreDirecto(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "padres-cero.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            Folder cargada = new Folder(hija.getId(), "Hija");
            s.folderDAO.loadParentFolders(cargada, 0);

            assertEquals(padre.getId(), cargada.getParent().getId(),
                    "comportamiento real: profundidad 0 también trae al padre directo");
            assertNull(((Folder) cargada.getParent()).getParent(), "pero no debe subir más allá");
        }
    }

    @Test
    void loadSubFoldersSinLimiteTraeTodaLaRama(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "sin-limite.sqlite")) {
            Folder raiz = new Folder("Raíz");
            raiz.setId(s.folderDAO.createFolder(raiz));
            Folder nivel1 = new Folder("Nivel 1");
            nivel1.setId(s.folderDAO.createFolder(nivel1));
            s.folderDAO.addSubFolder(raiz, nivel1);
            Folder nivel2 = new Folder("Nivel 2");
            nivel2.setId(s.folderDAO.createFolder(nivel2));
            s.folderDAO.addSubFolder(nivel1, nivel2);

            Folder cargada = new Folder(raiz.getId(), "Raíz");
            s.folderDAO.loadSubFolders(cargada);

            Folder hijaCargada = (Folder) cargada.getChildren().get(0);
            assertFalse(hijaCargada.isEmpty(), "sin límite debe seguir bajando hasta el final de la rama");
        }
    }

    @Test
    void loadSubFoldersConProfundidadNegativaLanza(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "profundidad-negativa.sqlite")) {
            Folder raiz = new Folder("Raíz");
            raiz.setId(s.folderDAO.createFolder(raiz));
            assertThrows(IllegalArgumentException.class, () -> s.folderDAO.loadSubFolders(raiz, -1));
        }
    }

    /**
     * Hallazgo real, no una suposición: {@code loadParentFoldersHelper} corta con
     * {@code currentDepth > maxDepth}, no {@code >=}. Con profundidad 1 eso deja pasar
     * dos niveles completos (el corte solo bloquea el <em>tercero</em>), así que
     * {@code loadParentFolder} — el atajo documentado como "carga el padre" — en la
     * práctica también trae al abuelo. Se fija tal cual está hoy.
     */
    @Test
    void loadParentFoldersConProfundidadUnoEnRealidadSubeDosNiveles(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "padres-uno.sqlite")) {
            Folder abuelo = new Folder("Abuelo");
            abuelo.setId(s.folderDAO.createFolder(abuelo));
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            s.folderDAO.addSubFolder(abuelo, padre);
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            Folder cargada = new Folder(hija.getId(), "Hija");
            s.folderDAO.loadParentFolder(cargada); // atajo: profundidad 1

            assertEquals(padre.getId(), cargada.getParent().getId());
            assertEquals(abuelo.getId(), ((Folder) cargada.getParent()).getParent().getId(),
                    "comportamiento real: profundidad 1 también trae al abuelo, no solo al padre directo");
        }
    }

    /**
     * Con solo tres niveles, "sube al abuelo" y "sube hasta la raíz sin límite" dan el
     * mismo resultado observable — el corte de profundidad no llega a ejercitarse de
     * verdad. Con cuatro niveles sí se distingue: profundidad 1 debe pararse en el abuelo
     * y NO alcanzar al bisabuelo.
     */
    @Test
    void loadParentFoldersConProfundidadUnoNoLlegaAUnCuartoNivel(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "padres-cuatro-niveles.sqlite")) {
            Folder bisabuelo = new Folder("Bisabuelo");
            bisabuelo.setId(s.folderDAO.createFolder(bisabuelo));
            Folder abuelo = new Folder("Abuelo");
            abuelo.setId(s.folderDAO.createFolder(abuelo));
            s.folderDAO.addSubFolder(bisabuelo, abuelo);
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            s.folderDAO.addSubFolder(abuelo, padre);
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            Folder cargada = new Folder(hija.getId(), "Hija");
            s.folderDAO.loadParentFolder(cargada); // atajo: profundidad 1

            Folder abueloCargado = (Folder) cargada.getParent().getParent();
            assertEquals(abuelo.getId(), abueloCargado.getId());
            assertNull(abueloCargado.getParent(), "profundidad 1 no debe llegar hasta el bisabuelo");
        }
    }

    @Test
    void loadParentFoldersSinLimiteSubeHastaLaRaiz(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "padres-todos.sqlite")) {
            Folder abuelo = new Folder("Abuelo");
            abuelo.setId(s.folderDAO.createFolder(abuelo));
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            s.folderDAO.addSubFolder(abuelo, padre);
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            Folder cargada = new Folder(hija.getId(), "Hija");
            s.folderDAO.loadParentFolders(cargada);

            assertEquals(abuelo.getId(), ((Folder) cargada.getParent()).getParent().getId(),
                    "sin límite debe subir hasta el abuelo");
        }
    }

    @Test
    void fetchAllFoldersAsTreeConstruyeLaJerarquiaDesdeLaRaizVirtual(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "arbol-completo.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);
            Folder suelta = new Folder("Suelta");
            suelta.setId(s.folderDAO.createFolder(suelta));

            Folder arbol = s.folderDAO.fetchAllFoldersAsTree();

            assertEquals(2, arbol.getChildren().size(), "padre y suelta cuelgan directamente de la raíz");
        }
    }

    @Test
    void getPathFolderEncadenaLosTitulosDesdeLaRaiz(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "ruta.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            assertEquals("/Padre", s.folderDAO.getPathFolder(padre.getId()));
            assertEquals("/Padre/Hija", s.folderDAO.getPathFolder(hija.getId()));
        }
    }

    @Test
    void getPathFolderDeUnIdInexistenteLanza(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "ruta-inexistente.sqlite")) {
            assertThrows(RuntimeException.class, () -> s.folderDAO.getPathFolder("no-existe"));
        }
    }

    @Test
    void loadNotesRellenaLasNotasDeCadaCarpetaDeLaJerarquia(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "cargar-notas.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);
            Note enHija = new Note("Nota", "cuerpo");
            enHija.setId(s.noteDAO.createNote(enHija));
            s.folderDAO.addNote(hija, enHija);

            Folder cargada = new Folder(padre.getId(), "Padre");
            s.folderDAO.loadSubFolders(cargada);
            s.folderDAO.loadNotes(cargada);

            Folder hijaCargada = (Folder) cargada.getChildren().stream()
                    .filter(c -> c instanceof Folder).findFirst().orElseThrow();
            List<Component> notasEnHija = hijaCargada.getChildren().stream()
                    .filter(c -> c instanceof Note).toList();
            assertEquals(1, notasEnHija.size(), "loadNotes debe bajar recursivamente a las subcarpetas ya cargadas");
        }
    }

    @Test
    void getParentFolderPorObjetoDelegaEnElId(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "padre-por-objeto.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            assertEquals(padre.getId(), s.folderDAO.getParentFolder(hija).getId());
        }
    }

    /** Abre una base de datos SQLite real de usar y tirar, con el esquema de producción. */
    private static final class Sesion implements AutoCloseable {
        final Connection connection;
        final FolderDAOSQLite folderDAO;
        final NoteDAOSQLite noteDAO;

        private Sesion(Connection connection) {
            this.connection = connection;
            this.folderDAO = new FolderDAOSQLite(connection);
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
