package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.example.jylos.service.NoteService;

/** {@code countWords}/{@code countCharacters}: los contadores del estado de la nota. */
class NoteServiceTextUtilTest {

    private static final NoteService SERVICIO = new NoteService(null, null);

    @Test
    void nuloOVacioCuentaCero() {
        assertEquals(0, SERVICIO.countWords(null));
        assertEquals(0, SERVICIO.countWords(""));
        assertEquals(0, SERVICIO.countWords("   "));
    }

    @Test
    void cuentaPalabrasSeparadasPorEspacios() {
        assertEquals(4, SERVICIO.countWords("esto tiene cuatro palabras"));
    }

    @Test
    void variosEspaciosSeguidosNoCuentanPalabrasVacias() {
        assertEquals(2, SERVICIO.countWords("una    dos"));
    }

    @Test
    void unaSolaPalabraCuentaUno() {
        assertEquals(1, SERVICIO.countWords("sola"));
    }

    @Test
    void nuloCuentaCeroCaracteres() {
        assertEquals(0, SERVICIO.countCharacters(null));
    }

    @Test
    void cuentaCaracteresIncluidosEspacios() {
        assertEquals(5, SERVICIO.countCharacters("a b c"));
    }
}
