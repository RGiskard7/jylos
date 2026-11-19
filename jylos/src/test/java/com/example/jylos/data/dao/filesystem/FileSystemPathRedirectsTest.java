package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Where a path went after its folders were renamed or moved during the session. */
class FileSystemPathRedirectsTest {

    @Test
    void aPathInsideARenamedFolderFollowsIt() {
        FileSystemPathRedirects redirects = new FileSystemPathRedirects();
        redirects.record("Trabajo", "Oficina");

        assertEquals("Oficina", redirects.current("Trabajo"));
        assertEquals("Oficina/Actas/Lunes.md", redirects.current("Trabajo/Actas/Lunes.md"));
        assertEquals("Trabajos viejos/Nota.md", redirects.current("Trabajos viejos/Nota.md"),
                "a folder whose name only starts the same stays");
        assertEquals("Ideas/Nota.md", redirects.current("Ideas/Nota.md"));
    }

    @Test
    void successiveChangesAreFollowedToTheEnd() {
        FileSystemPathRedirects redirects = new FileSystemPathRedirects();
        redirects.record("Trabajo", "Oficina");
        redirects.record("Oficina/Actas", "Oficina/Reuniones");
        redirects.record("Oficina", "Archivo/Oficina");

        assertEquals("Archivo/Oficina/Reuniones/Lunes.md", redirects.current("Trabajo/Actas/Lunes.md"));
    }
}
