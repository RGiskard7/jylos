package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.abstractLayers.BaseModel;

/**
 * {@code equals}/{@code hashCode}/{@code toString} y accesores de {@link BaseModel}.
 *
 * <p>Es abstracta y sus tres implementaciones concretas de producción ({@code Note},
 * {@code Folder}, {@code Tag}) la sobrescriben todas: {@code equals} de {@code BaseModel}
 * nunca se ejecuta hoy a través de ninguna de ellas — es código heredable pero muerto en
 * la práctica actual. Aun así merece test propio: es la base que cualquier modelo nuevo
 * heredaría por defecto si no la sobrescribe, y estaba al 22% con la mitad de sus ramas de
 * {@code equals} sin ejercitar.</p>
 *
 * <p>Se prueba a través de una subclase mínima local, la única forma de instanciar una
 * clase abstracta.</p>
 */
class BaseModelTest {

    /** El mínimo necesario para instanciar BaseModel sin añadir comportamiento propio. */
    private static final class Modelo extends BaseModel {
        Modelo(String id, String title, String createdDate, String modifiedDate) {
            super(id, title, createdDate, modifiedDate);
        }

        Modelo(String title, String createdDate, String modifiedDate) {
            super(title, createdDate, modifiedDate);
        }
    }

    // ── accesores ────────────────────────────────────────────────────────────

    @Test
    void elConstructorConIdAsignaLosCuatroCampos() {
        Modelo m = new Modelo("id-1", "Título", "2026-01-01", "2026-01-02");
        assertEquals("id-1", m.getId());
        assertEquals("Título", m.getTitle());
        assertEquals("2026-01-01", m.getCreatedDate());
        assertEquals("2026-01-02", m.getModifiedDate());
    }

    @Test
    void elConstructorSinIdLoDejaANull() {
        Modelo m = new Modelo("Título", "2026-01-01", "2026-01-02");
        assertNull(m.getId());
        assertEquals("Título", m.getTitle());
    }

    @Test
    void losSettersSobrescribenLosCampos() {
        Modelo m = new Modelo("id-1", "Título", null, null);
        m.setId("id-2");
        m.setTitle("Otro título");
        m.setCreatedDate("2026-02-01");
        m.setModifiedDate("2026-02-02");

        assertEquals("id-2", m.getId());
        assertEquals("Otro título", m.getTitle());
        assertEquals("2026-02-01", m.getCreatedDate());
        assertEquals("2026-02-02", m.getModifiedDate());
    }

    // ── equals: las cuatro guardas de cabecera ──────────────────────────────

    @Test
    void esIgualASiMismo() {
        Modelo m = new Modelo("id-1", "Título", null, null);
        assertEquals(m, m);
    }

    @Test
    void noEsIgualANull() {
        assertNotEquals(new Modelo("id-1", "Título", null, null), null);
    }

    @Test
    void noEsIgualAOtraClase() {
        assertNotEquals(new Modelo("id-1", "Título", null, null), "Título");
    }

    // ── equals: compara los cuatro campos, todos, no solo el id ────────────

    @Test
    void dosInstanciasConLosCuatroCamposIgualesSonIguales() {
        Modelo a = new Modelo("id-1", "Título", "2026-01-01", "2026-01-02");
        Modelo b = new Modelo("id-1", "Título", "2026-01-01", "2026-01-02");
        assertEquals(a, b);
    }

    @Test
    void difiereEnElIdLasHaceDistintas() {
        Modelo base = new Modelo("id-1", "Título", "2026-01-01", "2026-01-02");
        Modelo otroId = new Modelo("id-2", "Título", "2026-01-01", "2026-01-02");
        assertNotEquals(base, otroId);
    }

    @Test
    void difiereEnElTituloLasHaceDistintas() {
        Modelo base = new Modelo("id-1", "Título", "2026-01-01", "2026-01-02");
        Modelo otroTitulo = new Modelo("id-1", "Otro", "2026-01-01", "2026-01-02");
        assertNotEquals(base, otroTitulo);
    }

    @Test
    void difiereEnLasFechasLasHaceDistintas() {
        Modelo base = new Modelo("id-1", "Título", "2026-01-01", "2026-01-02");
        Modelo otraCreacion = new Modelo("id-1", "Título", "2026-09-09", "2026-01-02");
        Modelo otraModificacion = new Modelo("id-1", "Título", "2026-01-01", "2026-09-09");
        assertNotEquals(base, otraCreacion);
        assertNotEquals(base, otraModificacion);
    }

    @Test
    void nullEnAmbosLadosDeUnCampoCuentaComoIguales() {
        // Objects.equals(null, null) es true — dos modelos sin fecha de creación deben
        // seguir siendo iguales si el resto de campos coincide.
        Modelo a = new Modelo(null, "Título", null, "2026-01-02");
        Modelo b = new Modelo(null, "Título", null, "2026-01-02");
        assertEquals(a, b);
    }

    // ── hashCode ─────────────────────────────────────────────────────────────

    @Test
    void elHashSaleSoloDelTitulo() {
        // Objects.hash(title), no title.hashCode() a secas: envuelve el valor en un array
        // de un elemento antes de hashear, así que difiere del hash directo del String.
        assertEquals(java.util.Objects.hash("Título"), new Modelo("id-1", "Título", "x", "y").hashCode());
        assertEquals(
                new Modelo("id-1", "Título", "a", "b").hashCode(),
                new Modelo("id-2", "Título", "c", "d").hashCode(),
                "id y fechas no entran en el hash, solo el título");
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toStringLlevaIdYTitulo() {
        String texto = new Modelo("id-1", "Título", null, null).toString();
        assertTrue(texto.contains("id-1") && texto.contains("Título"),
                "toString debe seguir siendo útil para diagnosticar: " + texto);
    }
}
