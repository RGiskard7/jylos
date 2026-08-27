package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * {@link FolderDAOSQLite}: borrado suave, borrado permanente, restauración y la
 * reconstrucción de jerarquía de {@code fetchTrashFolders}.
 *
 * <p>Es la mitad de mayor riesgo de la clase: {@code permanentlyDeleteFolder} es
 * irreversible, y las tres operaciones cascadean a subcarpetas y notas por recursión —
 * exactamente el tipo de código donde un fallo se traduce en borrar de más o de menos.
 * {@code removeSubFolder}, {@code fetchTrashFolders}, {@code permanentlyDeleteFolder} y
 * {@code loadParentFoldersHelper} estaban al 0%.</p>
 */
class FolderDAOSQLiteDeleteRestoreTest {

    /**
     * Hallazgo real al escribir este test, no una suposición: {@code getFolderById} no
     * filtra por {@code is_deleted} en su SQL — a diferencia de
     * {@code fetchAllFoldersAsList}, que sí lo hace. Una carpeta borrada se sigue
     * encontrando por id; lo que cambia es que desaparece del listado general y de
     * {@code existsByTitle}. Se fija tal cual, sin "corregirlo" de camino.
     */
    @Test
    void borrarUnaCarpetaLaSacaDelListadoGeneralPeroSigueSiendoAlcanzablePorId(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "borrar.sqlite")) {
            Folder carpeta = new Folder("Efímera");
            carpeta.setId(s.folderDAO.createFolder(carpeta));

            s.folderDAO.deleteFolder(carpeta.getId());

            assertTrue(s.folderDAO.fetchAllFoldersAsList().stream().noneMatch(f -> f.getId().equals(carpeta.getId())),
                    "una carpeta borrada no debe salir en el listado general");
            assertNotNull(s.folderDAO.getFolderById(carpeta.getId()),
                    "pero getFolderById() no filtra por is_deleted: sigue siendo alcanzable por id");
        }
    }

    @Test
    void borrarUnaCarpetaBorraTambienSusNotas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "borrar-cascada-notas.sqlite")) {
            Folder carpeta = new Folder("Con notas");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.folderDAO.addNote(carpeta, nota);

            s.folderDAO.deleteFolder(carpeta.getId());

            assertTrue(s.noteDAO.fetchTrashNotes().stream().anyMatch(n -> n.getId().equals(nota.getId())),
                    "la nota de una carpeta borrada debe terminar en la papelera, no perderse ni quedar huérfana");
        }
    }

    @Test
    void borrarUnaCarpetaBorraTambienSusSubcarpetasRecursivamente(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "borrar-cascada-subcarpetas.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            s.folderDAO.deleteFolder(padre.getId());

            assertTrue(s.folderDAO.fetchAllFoldersAsList().stream().noneMatch(f -> f.getId().equals(hija.getId())),
                    "la subcarpeta debe desaparecer del listado general igual que su padre");
            assertTrue(s.folderDAO.fetchTrashFolders().getChildren().size() >= 1,
                    "y debe poder encontrarse en la papelera, no perderse sin más");
        }
    }

    @Test
    void restaurarUnaCarpetaLaDevuelveALasVivas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "restaurar.sqlite")) {
            Folder carpeta = new Folder("Recuperable");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            s.folderDAO.deleteFolder(carpeta.getId());

            s.folderDAO.restoreFolder(carpeta.getId());

            Folder restaurada = s.folderDAO.getFolderById(carpeta.getId());
            assertNotNull(restaurada, "tras restaurar debe volver a aparecer en las consultas normales");
            assertEquals("Recuperable", restaurada.getTitle());
        }
    }

    @Test
    void restaurarUnaCarpetaRestauraTambienSusNotasBorradasConElla(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "restaurar-notas.sqlite")) {
            Folder carpeta = new Folder("Con notas");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.folderDAO.addNote(carpeta, nota);
            s.folderDAO.deleteFolder(carpeta.getId());

            s.folderDAO.restoreFolder(carpeta.getId());

            assertTrue(s.noteDAO.fetchTrashNotes().stream().noneMatch(n -> n.getId().equals(nota.getId())),
                    "la nota debe salir de la papelera junto con su carpeta");
        }
    }

    @Test
    void borrarPermanentementeUnaCarpetaYaBorradaLaHaceIrrecuperable(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "borrado-permanente.sqlite")) {
            Folder carpeta = new Folder("Definitiva");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            s.folderDAO.deleteFolder(carpeta.getId());

            s.folderDAO.permanentlyDeleteFolder(carpeta.getId());

            assertTrue(s.folderDAO.fetchTrashFolders().getChildren().isEmpty(),
                    "no debe quedar ni rastro en la papelera");
            // restoreFolder sobre un id que ya no existe en absoluto: no debe lanzar, y
            // desde luego no debe resucitar nada.
            s.folderDAO.restoreFolder(carpeta.getId());
            assertNull(s.folderDAO.getFolderById(carpeta.getId()));
        }
    }

    @Test
    void borrarPermanentementeUnaCarpetaBorraTambienSusNotasParaSiempre(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "borrado-permanente-notas.sqlite")) {
            Folder carpeta = new Folder("Definitiva");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.folderDAO.addNote(carpeta, nota);
            s.folderDAO.deleteFolder(carpeta.getId());

            s.folderDAO.permanentlyDeleteFolder(carpeta.getId());

            assertFalse(s.noteDAO.fetchTrashNotes().stream().anyMatch(n -> n.getId().equals(nota.getId())),
                    "la nota debe desaparecer del todo, no quedar colgando en la papelera para siempre");
        }
    }

    // ── fetchTrashFolders: reconstrucción de jerarquía ──────────────────────

    @Test
    void fetchTrashFoldersAgrupaCarpetasHermanasBajoLaRaizVirtual(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "papelera-plana.sqlite")) {
            Folder una = new Folder("Una");
            una.setId(s.folderDAO.createFolder(una));
            Folder otra = new Folder("Otra");
            otra.setId(s.folderDAO.createFolder(otra));
            s.folderDAO.deleteFolder(una.getId());
            s.folderDAO.deleteFolder(otra.getId());

            List<Component> raiz = s.folderDAO.fetchTrashFolders().getChildren();

            assertEquals(2, raiz.size(), "las dos carpetas borradas independientes cuelgan de la raíz virtual");
        }
    }

    @Test
    void fetchTrashFoldersConservaLaJerarquiaCuandoPadreEHijaSeBorranJuntos(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "papelera-jerarquia.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            s.folderDAO.deleteFolder(padre.getId());

            Folder papelera = s.folderDAO.fetchTrashFolders();
            assertEquals(1, papelera.getChildren().size(), "solo el padre debe colgar directo de la raíz virtual");
            Folder padreEnPapelera = (Folder) papelera.getChildren().get(0);
            assertEquals(1, padreEnPapelera.getChildren().size(),
                    "la hija debe seguir anidada bajo su padre, no aparecer suelta");
            Folder hijaEnPapelera = (Folder) padreEnPapelera.getChildren().get(0);
            assertEquals(padreEnPapelera.getId(), hijaEnPapelera.getParent().getId(),
                    "el enlace de vuelta al padre también debe reconstruirse, no solo la lista de hijos");
        }
    }

    @Test
    void fetchTrashFoldersTrataComoHuerfanaUnaSubcarpetaBorradaCuyoPadreSigueVivo(@TempDir Path tmp)
            throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "papelera-huerfana.sqlite")) {
            Folder padre = new Folder("Vivo");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Borrada");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            s.folderDAO.deleteFolder(hija.getId()); // solo la hija, el padre sigue vivo

            Folder papelera = s.folderDAO.fetchTrashFolders();
            assertEquals(1, papelera.getChildren().size());
            assertEquals(hija.getId(), papelera.getChildren().get(0).getId(),
                    "sin su padre en la papelera, debe colgar suelta de la raíz virtual");
            assertEquals(papelera, ((Folder) papelera.getChildren().get(0)).getParent(),
                    "y su enlace de vuelta debe apuntar a la raíz virtual, no quedar huérfano de verdad");
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
