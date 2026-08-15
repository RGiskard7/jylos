package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.service.DatabaseBackupService;

/**
 * {@link DatabaseBackupService}: la única red de seguridad ante corrupción de la base de
 * datos o un fallo del proceso a mitad de escritura.
 *
 * <p>Estaba al 17% de mutación, sin ningún test. Es el candidato más claro de todo el
 * proyecto a "nadie se entera si se rompe hasta que hace falta restaurar un backup y no
 * hay ninguno, o están vacíos".</p>
 */
class DatabaseBackupServiceTest {

    // ── backupDatabaseFile: independiente de AppDataDirectory ──────────────

    @Test
    void backupDatabaseFileConOrigenNuloDevuelveFalse(@TempDir Path tmp) {
        assertFalse(DatabaseBackupService.backupDatabaseFile(null, tmp.resolve("destino.db").toFile()));
    }

    @Test
    void backupDatabaseFileConDestinoNuloDevuelveFalse(@TempDir Path tmp) throws Exception {
        File origen = crearBaseDeDatosSqliteValida(tmp.resolve("origen.db"));
        assertFalse(DatabaseBackupService.backupDatabaseFile(origen, null));
    }

    @Test
    void backupDatabaseFileConOrigenInexistenteDevuelveFalse(@TempDir Path tmp) {
        File origen = tmp.resolve("no-existe.db").toFile();
        assertFalse(DatabaseBackupService.backupDatabaseFile(origen, tmp.resolve("destino.db").toFile()));
    }

    @Test
    void backupDatabaseFileCreaElDirectorioDestinoSiNoExiste(@TempDir Path tmp) throws Exception {
        File origen = crearBaseDeDatosSqliteValida(tmp.resolve("origen.db"));
        File destino = tmp.resolve("subcarpeta/anidada/backup.db").toFile();

        assertTrue(DatabaseBackupService.backupDatabaseFile(origen, destino));

        assertTrue(destino.isFile(), "debe crear las carpetas intermedias que hagan falta");
    }

    @Test
    void backupDatabaseFileDeUnaBaseDeDatosValidaUsaVacuumIntoYProduceUnFicheroSqliteValido(@TempDir Path tmp)
            throws Exception {
        File origen = crearBaseDeDatosSqliteValida(tmp.resolve("origen.db"));
        File destino = tmp.resolve("backup.db").toFile();

        assertTrue(DatabaseBackupService.backupDatabaseFile(origen, destino));

        // No basta con que el fichero exista: VACUUM INTO debe haber producido una base de
        // datos SQLite legible, con la misma fila que insertamos en el origen.
        try (Connection conexion = DriverManager.getConnection("jdbc:sqlite:" + destino.getAbsolutePath());
                Statement statement = conexion.createStatement()) {
            var rs = statement.executeQuery("SELECT valor FROM prueba WHERE id = 1");
            assertTrue(rs.next(), "el backup debe contener los datos del origen");
            assertEquals("contenido de prueba", rs.getString("valor"));
        }
    }

    @Test
    void backupDatabaseFileSobreescribeUnDestinoPreexistente(@TempDir Path tmp) throws Exception {
        File origen = crearBaseDeDatosSqliteValida(tmp.resolve("origen.db"));
        File destino = tmp.resolve("backup.db").toFile();
        Files.writeString(destino.toPath(), "backup antiguo, debe desaparecer");

        assertTrue(DatabaseBackupService.backupDatabaseFile(origen, destino));

        assertTrue(Files.readAllBytes(destino.toPath()).length > "backup antiguo, debe desaparecer".length(),
                "el contenido antiguo debe haberse reemplazado por el backup real");
    }

    @Test
    void backupDatabaseFileDeUnOrigenQueNoEsSqliteCaeAlCopiadoDeFicheroYPreservaLosBytes(@TempDir Path tmp)
            throws Exception {
        // vacuumInto() falla (SQLITE_NOTADB) porque el "origen" no tiene cabecera SQLite
        // válida; backupDatabaseFile debe caer en copyDatabaseFile y copiar los bytes tal
        // cual, no dejar un backup vacío ni fallar en silencio.
        byte[] contenido = "esto no es una base de datos SQLite".getBytes();
        File origen = tmp.resolve("origen-no-sqlite.db").toFile();
        Files.write(origen.toPath(), contenido);
        File destino = tmp.resolve("backup.db").toFile();

        assertTrue(DatabaseBackupService.backupDatabaseFile(origen, destino),
                "debe caer al copiado de fichero y seguir devolviendo éxito");

        assertArrayEquals(contenido, Files.readAllBytes(destino.toPath()),
                "el contenido copiado debe ser idéntico byte a byte al origen");
    }

    // ── createStartupBackupIfNeeded / pruneOldBackups: vía AppDataDirectory ─

    @AfterEach
    void restaurarAppDataDirectory() throws Exception {
        establecerBaseDir(null);
    }

    @Test
    void createStartupBackupIfNeededSinBaseDeDatosOrigenNoHaceNada(@TempDir Path tmp) throws Exception {
        establecerBaseDir(tmp.toString());

        DatabaseBackupService.createStartupBackupIfNeeded();

        assertFalse(new File(AppDataDirectory.getBackupsDirectory()).exists(),
                "sin database.db en data/, no debe ni crear la carpeta de backups");
    }

    @Test
    void createStartupBackupIfNeededConBaseDeDatosOrigenCreaUnBackup(@TempDir Path tmp) throws Exception {
        establecerBaseDir(tmp.toString());
        crearBaseDeDatosSqliteValida(tmp.resolve("data/database.db"));

        DatabaseBackupService.createStartupBackupIfNeeded();

        File[] backups = new File(AppDataDirectory.getBackupsDirectory())
                .listFiles((dir, name) -> name.startsWith("database-auto-backup-"));
        assertTrue(backups != null && backups.length == 1, "debe crear exactamente un backup nuevo");
    }

    @Test
    void createStartupBackupIfNeededConSeisBackupsPreviosBorraSoloElMasAntiguo(@TempDir Path tmp) throws Exception {
        establecerBaseDir(tmp.toString());
        crearBaseDeDatosSqliteValida(tmp.resolve("data/database.db"));
        File backupsDir = new File(AppDataDirectory.getBackupsDirectory());
        backupsDir.mkdirs();
        List<File> antiguos = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            File f = new File(backupsDir, "database-auto-backup-viejo-" + i + ".db");
            Files.writeString(f.toPath(), "backup " + i);
            // Separamos lastModified explícitamente: crear los ficheros en el mismo
            // milisegundo dejaría el orden de poda indeterminado.
            f.setLastModified(Instant.now().toEpochMilli() - (10 - i) * 1000L);
            antiguos.add(f);
        }
        File masAntiguo = antiguos.get(0);

        DatabaseBackupService.createStartupBackupIfNeeded(); // el 6º backup dispara la poda

        assertFalse(masAntiguo.exists(), "el backup más antiguo de los 6 debe haberse borrado");
        File[] restantes = backupsDir.listFiles((dir, name) -> name.startsWith("database-auto-backup-"));
        assertEquals(5, restantes.length, "debe conservar como máximo 5 backups");
    }

    @Test
    void pruneOldBackupsIgnoraFicherosQueNoSonBackupsAunqueHayaQuePodar(@TempDir Path tmp) throws Exception {
        // El filtro de listFiles() exige el prefijo Y el sufijo de backup; un fichero
        // suelto en la misma carpeta (p. ej. un .DS_Store, o un backup manual con otro
        // nombre) no debe contarse ni, desde luego, borrarse durante la poda.
        establecerBaseDir(tmp.toString());
        crearBaseDeDatosSqliteValida(tmp.resolve("data/database.db"));
        File backupsDir = new File(AppDataDirectory.getBackupsDirectory());
        backupsDir.mkdirs();
        for (int i = 0; i < 5; i++) {
            File f = new File(backupsDir, "database-auto-backup-viejo-" + i + ".db");
            Files.writeString(f.toPath(), "backup " + i);
            f.setLastModified(Instant.now().toEpochMilli() - (10 - i) * 1000L);
        }
        // Más antiguo que TODOS los backups reales: si el filtro de nombre fallara y lo
        // tratara como backup, sería el primer candidato a la poda por ser el más viejo.
        File ajeno = new File(backupsDir, "notas-sueltas.txt");
        Files.writeString(ajeno.toPath(), "no es un backup, no debe tocarse");
        ajeno.setLastModified(Instant.now().toEpochMilli() - 20_000L);

        DatabaseBackupService.createStartupBackupIfNeeded(); // 6º backup real, dispara poda

        assertTrue(ajeno.exists(), "un fichero que no cumple el patrón de nombre no debe borrarse en la poda");
    }

    @Test
    void createStartupBackupIfNeededConCincoBackupsPreviosNoBorraNinguno(@TempDir Path tmp) throws Exception {
        establecerBaseDir(tmp.toString());
        crearBaseDeDatosSqliteValida(tmp.resolve("data/database.db"));
        File backupsDir = new File(AppDataDirectory.getBackupsDirectory());
        backupsDir.mkdirs();
        for (int i = 0; i < 4; i++) {
            Files.writeString(new File(backupsDir, "database-auto-backup-viejo-" + i + ".db").toPath(), "x");
        }

        DatabaseBackupService.createStartupBackupIfNeeded(); // 5º backup, no debe podar

        File[] restantes = backupsDir.listFiles((dir, name) -> name.startsWith("database-auto-backup-"));
        assertEquals(5, restantes.length, "con 5 backups en total (4 viejos + 1 nuevo) no debe podar ninguno");
    }

    private static File crearBaseDeDatosSqliteValida(Path destino) throws Exception {
        Files.createDirectories(destino.getParent());
        try (Connection conexion = DriverManager.getConnection("jdbc:sqlite:" + destino.toAbsolutePath());
                Statement statement = conexion.createStatement()) {
            statement.execute("CREATE TABLE prueba (id INTEGER PRIMARY KEY, valor TEXT)");
            statement.execute("INSERT INTO prueba (id, valor) VALUES (1, 'contenido de prueba')");
        }
        return destino.toFile();
    }

    private static void establecerBaseDir(String valor) throws Exception {
        Field campo = AppDataDirectory.class.getDeclaredField("baseDir");
        campo.setAccessible(true);
        campo.set(null, valor);
    }
}
