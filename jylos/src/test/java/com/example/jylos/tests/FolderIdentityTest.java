package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * Identidad de {@link Folder}: {@code equals}, {@code hashCode} e {@code isEmpty}.
 *
 * <p>Importa más de lo que parece para un modelo tan pequeño: las carpetas acaban en
 * {@code Set} y {@code Map}, y de esas comparaciones dependen la selección del árbol, la
 * validación de movimientos y el borrado. Antes de esto, la clase entera estaba al 6% de
 * mutación — de 17 mutaciones solo se detectaba una.</p>
 *
 * <p>Son tests de <em>caracterización</em>: fijan lo que el código hace hoy. Uno de ellos
 * documenta a propósito un incumplimiento del contrato de {@code equals}/{@code hashCode}
 * — ver {@link #equalsYHashCodeSonIncoherentesCuandoSoloUnaTieneId()}. No se corrige aquí:
 * congelar y cambiar a la vez es justo lo que la caracterización evita.</p>
 */
class FolderIdentityTest {

    // ── equals ───────────────────────────────────────────────────────────────

    @Test
    void dosCarpetasConElMismoIdSonIguales() {
        assertEquals(new Folder("id-1", "Trabajo"), new Folder("id-1", "Otro nombre"),
                "con ambos ids presentes, manda el id y el título da igual");
    }

    @Test
    void dosCarpetasConIdDistintoNoSonIguales() {
        assertNotEquals(new Folder("id-1", "Trabajo"), new Folder("id-2", "Trabajo"));
    }

    @Test
    void sinIdEnAlgunaDeLasDosSeComparaPorTitulo() {
        // new Folder(titulo) deja el id a null.
        Folder sinId = new Folder("Trabajo");
        assertNull(sinId.getId(), "el constructor de un solo argumento no asigna id");

        assertEquals(sinId, new Folder("Trabajo"), "sin ids, se comparan los títulos");
        assertEquals(new Folder("id-1", "Trabajo"), sinId,
                "basta con que a una le falte el id para caer en la comparación por título");
        assertNotEquals(sinId, new Folder("Personal"));
    }

    @Test
    void noEsIgualANullNiAOtroTipo() {
        Folder carpeta = new Folder("id-1", "Trabajo");
        assertNotEquals(null, carpeta);
        assertNotEquals(carpeta, new Note("id-1", "Trabajo", ""),
                "getClass() distinto corta antes de comparar nada");
    }

    @Test
    void esIgualASiMisma() {
        Folder carpeta = new Folder("id-1", "Trabajo");
        assertEquals(carpeta, carpeta);
    }

    // ── hashCode ─────────────────────────────────────────────────────────────

    @Test
    void elHashSaleDelIdCuandoLoHay() {
        assertEquals("id-1".hashCode(), new Folder("id-1", "Trabajo").hashCode());
        assertEquals(new Folder("id-1", "Trabajo").hashCode(), new Folder("id-1", "Otro").hashCode(),
                "mismo id, mismo hash, aunque cambie el título");
    }

    @Test
    void sinIdElHashSaleDelTitulo() {
        assertEquals("Trabajo".hashCode(), new Folder("Trabajo").hashCode());
    }

    /**
     * <b>Incumplimiento del contrato, fijado a propósito.</b>
     *
     * <p>Dos carpetas que {@code equals} considera iguales devuelven hash distinto cuando
     * una tiene id y la otra no. {@code equals} cae a comparar títulos, pero
     * {@code hashCode} usa el id de la que lo tiene. El contrato de Java exige que objetos
     * iguales compartan hash.</p>
     *
     * <p>Consecuencia práctica: en un {@code HashSet} pueden convivir las dos pese a ser
     * "iguales", y una búsqueda en un {@code HashMap} puede no encontrar la que sí está.</p>
     *
     * <p>Este test <em>documenta</em> el comportamiento actual, no lo aprueba. Arreglarlo
     * es una decisión aparte: cambiaría cómo se comportan colecciones de carpetas en toda
     * la aplicación.</p>
     */
    @Test
    void equalsYHashCodeSonIncoherentesCuandoSoloUnaTieneId() {
        Folder conId = new Folder("id-1", "Trabajo");
        Folder sinId = new Folder("Trabajo");

        assertEquals(conId, sinId, "equals las da por iguales");
        assertNotEquals(conId.hashCode(), sinId.hashCode(),
                "pero el hash difiere: incumple el contrato equals/hashCode");

        // Y así es como se nota: un Set acaba con las dos dentro.
        Set<Folder> conjunto = new HashSet<>();
        conjunto.add(conId);
        conjunto.add(sinId);
        assertEquals(2, conjunto.size(),
                "dos carpetas 'iguales' conviven en el mismo Set por culpa del hash distinto");
    }

    // ── isEmpty ──────────────────────────────────────────────────────────────

    @Test
    void isEmptyRespondeSegunLosHijos() {
        Folder carpeta = new Folder("id-1", "Trabajo");
        assertTrue(carpeta.isEmpty(), "una carpeta recién creada no tiene hijos");

        carpeta.add(new Note("n1", "Nota", ""));
        assertFalse(carpeta.isEmpty(), "con un hijo dentro deja de estar vacía");
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toStringLlevaIdYTitulo() {
        String texto = new Folder("id-1", "Trabajo").toString();
        assertTrue(texto.contains("id-1") && texto.contains("Trabajo"),
                "toString debe seguir siendo útil para diagnosticar: " + texto);
    }
}
