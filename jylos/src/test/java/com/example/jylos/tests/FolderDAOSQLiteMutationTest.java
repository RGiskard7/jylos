package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * {@link FolderDAOSQLite}, la mitad "escribe algo" del backend SQLite: crear, renombrar,
 * mover carpetas y su relación con notas y subcarpetas.
 *
 * <p>Estaba al 6% de mutación — 213 mutaciones, solo 45 cazadas. Muchos métodos con 0%
 * exacto: {@code removeSubFolder} y {@code removeNote} (33 mutaciones entre las dos) no
 * tenían ni un solo test. Contra una base de datos SQLite real, en un fichero temporal,
 * con el esquema de producción tal cual lo crea {@code SQLiteDB.initDatabase()} — no un
 * esquema ad-hoc aproximado.</p>
 */
class FolderDAOSQLiteMutationTest {

    @Test
    void crearUnaCarpetaLeAsignaUnIdYQuedaEnRaiz(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "crear.sqlite")) {
            Folder carpeta = new Folder("Trabajo");
            String id = s.folderDAO.createFolder(carpeta);

            assertTrue(id != null && !id.isBlank());
            Folder releida = s.folderDAO.getFolderById(id);
            assertEquals("Trabajo", releida.getTitle());
            assertNull(s.folderDAO.getParentFolder(id), "sin padre indicado, debe quedar en la raíz");
            assertTrue(s.folderDAO.fetchAllFoldersAsList().stream().anyMatch(f -> f.getId().equals(id)),
                    "debe aparecer en el listado general, no una lista vacía");
        }
    }

    @Test
    void crearUnaSubcarpetaGuardaElPadre(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "subcarpeta.sqlite")) {
            Folder padre = new Folder("Proyectos");
            padre.setId(s.folderDAO.createFolder(padre));

            Folder hija = new Folder("Web");
            hija.setParent(padre);
            hija.setId(s.folderDAO.createFolder(hija));

            Folder padreReleido = s.folderDAO.getParentFolder(hija.getId());
            assertEquals(padre.getId(), padreReleido.getId());
        }
    }

    @Test
    void actualizarUnaCarpetaCambiaElTitulo(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "actualizar.sqlite")) {
            Folder carpeta = new Folder("Antiguo");
            carpeta.setId(s.folderDAO.createFolder(carpeta));

            carpeta.setTitle("Nuevo");
            s.folderDAO.updateFolder(carpeta);

            assertEquals("Nuevo", s.folderDAO.getFolderById(carpeta.getId()).getTitle());
        }
    }

    @Test
    void actualizarUnaCarpetaSoloTocaEsaCarpetaNoOtras(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "actualizar-aislada.sqlite")) {
            Folder aRenombrar = new Folder("Antiguo");
            aRenombrar.setId(s.folderDAO.createFolder(aRenombrar));
            Folder otra = new Folder("Intacta");
            otra.setId(s.folderDAO.createFolder(otra));

            aRenombrar.setTitle("Nuevo");
            s.folderDAO.updateFolder(aRenombrar);

            assertEquals("Intacta", s.folderDAO.getFolderById(otra.getId()).getTitle(),
                    "actualizar una carpeta no debe tocar el título de otra");
        }
    }

    @Test
    void existsByTitleDistingueCarpetasCreadasDeLasQueNo(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "existe.sqlite")) {
            Folder carpeta = new Folder("Única");
            s.folderDAO.createFolder(carpeta);

            assertTrue(s.folderDAO.existsByTitle("Única"));
            assertFalse(s.folderDAO.existsByTitle("No existe"));
        }
    }

    @Test
    void existsByTitleIgnoraCarpetasBorradas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "existe-borrada.sqlite")) {
            Folder carpeta = new Folder("Efímera");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            s.folderDAO.deleteFolder(carpeta.getId());

            assertFalse(s.folderDAO.existsByTitle("Efímera"),
                    "el nombre debe quedar libre en cuanto la carpeta va a la papelera");
        }
    }

    // ── addNote / removeNote ─────────────────────────────────────────────────

    @Test
    void addNoteMueveLaNotaALaCarpetaYActualizaAmbosLados(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "add-note.sqlite")) {
            Folder carpeta = new Folder("Destino");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));

            s.folderDAO.addNote(carpeta, nota);

            assertEquals(carpeta.getId(), s.folderDAO.getFolderByNoteId(nota.getId()).getId());
            assertTrue(carpeta.getChildren().contains(nota), "el objeto en memoria también debe reflejarlo");
            assertEquals(carpeta.getId(), nota.getParent().getId(),
                    "la nota en memoria también debe apuntar de vuelta a la carpeta");
        }
    }

    @Test
    void addNoteSoloTocaLaCarpetaIndicadaNoOtrasConNotasPropias(@TempDir Path tmp) throws Exception {
        // Blinda que el UPDATE de addNote() liga la nota SOLO a la carpeta destino: con dos
        // carpetas presentes, una nota preexistente en la otra no debe moverse.
        try (Sesion s = Sesion.abrir(tmp, "add-note-aislada.sqlite")) {
            Folder destino = new Folder("Destino");
            destino.setId(s.folderDAO.createFolder(destino));
            Folder otra = new Folder("Otra");
            otra.setId(s.folderDAO.createFolder(otra));
            Note notaEnOtra = new Note("Ya estaba", "cuerpo");
            notaEnOtra.setId(s.noteDAO.createNote(notaEnOtra));
            s.folderDAO.addNote(otra, notaEnOtra);
            Note notaNueva = new Note("Nota", "cuerpo");
            notaNueva.setId(s.noteDAO.createNote(notaNueva));

            s.folderDAO.addNote(destino, notaNueva);

            assertEquals(otra.getId(), s.folderDAO.getFolderByNoteId(notaEnOtra.getId()).getId(),
                    "la nota de la otra carpeta no debe verse afectada");
        }
    }

    @Test
    void removeNoteLaDejaSinCarpeta(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "remove-note.sqlite")) {
            Folder carpeta = new Folder("Origen");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.folderDAO.addNote(carpeta, nota);

            s.folderDAO.removeNote(carpeta, nota);

            assertNull(s.folderDAO.getFolderByNoteId(nota.getId()),
                    "tras quitarla, la nota no debe pertenecer a ninguna carpeta");
            assertFalse(carpeta.getChildren().contains(nota), "el objeto en memoria también debe reflejarlo");
            assertNull(nota.getParent(), "la nota en memoria también debe soltar la referencia al padre");
        }
    }

    @Test
    void removeNoteSoloTocaLaCarpetaIndicadaNoOtrasConNotasPropias(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "remove-note-aislada.sqlite")) {
            Folder origen = new Folder("Origen");
            origen.setId(s.folderDAO.createFolder(origen));
            Folder otra = new Folder("Otra");
            otra.setId(s.folderDAO.createFolder(otra));
            Note aQuitar = new Note("A quitar", "cuerpo");
            aQuitar.setId(s.noteDAO.createNote(aQuitar));
            s.folderDAO.addNote(origen, aQuitar);
            Note aConservar = new Note("A conservar", "cuerpo");
            aConservar.setId(s.noteDAO.createNote(aConservar));
            s.folderDAO.addNote(otra, aConservar);

            s.folderDAO.removeNote(origen, aQuitar);

            assertEquals(otra.getId(), s.folderDAO.getFolderByNoteId(aConservar.getId()).getId(),
                    "quitar una nota de una carpeta no debe tocar las notas de otra carpeta");
        }
    }

    @Test
    void removeNoteConCarpetaONotaNulaLanza(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "remove-note-null.sqlite")) {
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            assertThrows(IllegalArgumentException.class, () -> s.folderDAO.removeNote(null, nota));
        }
    }

    // ── addSubFolder / removeSubFolder ──────────────────────────────────────

    @Test
    void addSubFolderEncadenaPadreEHijo(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "add-sub.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));

            s.folderDAO.addSubFolder(padre, hija);

            assertEquals(padre.getId(), s.folderDAO.getParentFolder(hija.getId()).getId());
            assertEquals(padre.getId(), hija.getParent().getId(), "también en memoria");
            assertTrue(padre.getChildren().contains(hija), "y el padre en memoria debe listarla como hija");
        }
    }

    @Test
    void addSubFolderSoloTocaLaParejaIndicadaNoOtrasSubcarpetas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "add-sub-aislada.sqlite")) {
            Folder otroPadre = new Folder("Otro padre");
            otroPadre.setId(s.folderDAO.createFolder(otroPadre));
            Folder otraHija = new Folder("Otra hija");
            otraHija.setId(s.folderDAO.createFolder(otraHija));
            s.folderDAO.addSubFolder(otroPadre, otraHija);
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));

            s.folderDAO.addSubFolder(padre, hija);

            assertEquals(otroPadre.getId(), s.folderDAO.getParentFolder(otraHija.getId()).getId(),
                    "la pareja padre/hija ya existente no debe verse afectada");
        }
    }

    @Test
    void removeSubFolderLaDejaSinPadre(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "remove-sub.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            s.folderDAO.removeSubFolder(padre, hija);

            assertNull(s.folderDAO.getParentFolder(hija.getId()),
                    "tras quitarla, la subcarpeta debe volver a quedar sin padre");
            assertNull(hija.getParent(), "también en memoria");
            assertFalse(padre.getChildren().contains(hija), "y el padre en memoria ya no debe listarla");
        }
    }

    @Test
    void removeSubFolderSoloTocaLaParejaIndicadaNoOtrasSubcarpetas(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "remove-sub-aislada.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder aQuitar = new Folder("A quitar");
            aQuitar.setId(s.folderDAO.createFolder(aQuitar));
            s.folderDAO.addSubFolder(padre, aQuitar);
            Folder aConservar = new Folder("A conservar");
            aConservar.setId(s.folderDAO.createFolder(aConservar));
            s.folderDAO.addSubFolder(padre, aConservar);

            s.folderDAO.removeSubFolder(padre, aQuitar);

            assertEquals(padre.getId(), s.folderDAO.getParentFolder(aConservar.getId()).getId(),
                    "quitar una subcarpeta no debe tocar a su hermana");
        }
    }

    @Test
    void addSubFolderConElMismoIdParaAmbasLanza(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "add-sub-mismo-id.sqlite")) {
            Folder carpeta = new Folder("Carpeta");
            carpeta.setId(s.folderDAO.createFolder(carpeta));

            Folder mismaReferencia = new Folder(carpeta.getId(), "Carpeta");
            assertThrows(IllegalArgumentException.class,
                    () -> s.folderDAO.addSubFolder(carpeta, mismaReferencia));
        }
    }

    // ── moveFolderToRoot / moveNoteToRoot ───────────────────────────────────

    @Test
    void moveFolderToRootQuitaElPadre(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "mover-raiz.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder hija = new Folder("Hija");
            hija.setId(s.folderDAO.createFolder(hija));
            s.folderDAO.addSubFolder(padre, hija);

            s.folderDAO.moveFolderToRoot(hija);

            assertNull(s.folderDAO.getParentFolder(hija.getId()));
            assertNull(hija.getParent(), "también en memoria");
        }
    }

    @Test
    void moveFolderToRootSoloTocaLaCarpetaIndicada(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "mover-raiz-aislada.sqlite")) {
            Folder padre = new Folder("Padre");
            padre.setId(s.folderDAO.createFolder(padre));
            Folder aMover = new Folder("A mover");
            aMover.setId(s.folderDAO.createFolder(aMover));
            s.folderDAO.addSubFolder(padre, aMover);
            Folder aConservar = new Folder("A conservar");
            aConservar.setId(s.folderDAO.createFolder(aConservar));
            s.folderDAO.addSubFolder(padre, aConservar);

            s.folderDAO.moveFolderToRoot(aMover);

            assertEquals(padre.getId(), s.folderDAO.getParentFolder(aConservar.getId()).getId(),
                    "mover una hija a la raíz no debe tocar a su hermana");
        }
    }

    @Test
    void moveNoteToRootQuitaLaCarpeta(@TempDir Path tmp) throws Exception {
        try (Sesion s = Sesion.abrir(tmp, "nota-a-raiz.sqlite")) {
            Folder carpeta = new Folder("Origen");
            carpeta.setId(s.folderDAO.createFolder(carpeta));
            Note nota = new Note("Nota", "cuerpo");
            nota.setId(s.noteDAO.createNote(nota));
            s.folderDAO.addNote(carpeta, nota);

            s.folderDAO.moveNoteToRoot(nota);

            assertNull(s.folderDAO.getFolderByNoteId(nota.getId()));
            assertNull(nota.getParent(), "también en memoria");
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
