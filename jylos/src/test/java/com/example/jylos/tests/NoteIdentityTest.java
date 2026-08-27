package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * Identidad y accesores de {@link Note}: {@code equals}, {@code hashCode}, etiquetas y
 * los metadatos que nadie ejercitaba.
 *
 * <p>{@code Note} es el modelo por el que pasa todo lo que el usuario escribe, y estaba al
 * 52% de mutación: quince mutaciones sobrevivían y ocho ni se ejecutaban. La mayoría en
 * {@code equals}/{@code hashCode} y en accesores cuyo valor de retorno se podía cambiar sin
 * que ningún test se enterase.</p>
 *
 * <p>Tests de <em>caracterización</em>: fijan lo que hace hoy.
 * {@link #equalsYHashCodeSonIncoherentesCuandoSoloUnaTieneId()} documenta a propósito un
 * incumplimiento del contrato de Java — el mismo que tiene {@link Folder}, así que es un
 * patrón sistémico y no un descuido suelto. No se corrige aquí.</p>
 */
class NoteIdentityTest {

    // ── equals ───────────────────────────────────────────────────────────────

    @Test
    void dosNotasConElMismoIdSonIguales() {
        assertEquals(new Note("id-1", "Diario", "a"), new Note("id-1", "Otro título", "b"),
                "con ambos ids presentes manda el id: ni título ni contenido cuentan");
    }

    @Test
    void dosNotasConIdDistintoNoSonIguales() {
        assertNotEquals(new Note("id-1", "Diario", ""), new Note("id-2", "Diario", ""));
    }

    @Test
    void sinIdEnAlgunaDeLasDosSeComparaPorTitulo() {
        Note sinId = new Note("Diario", "contenido");
        assertNull(sinId.getId(), "el constructor sin id no asigna ninguno");

        assertEquals(sinId, new Note("Diario", "otro contenido"), "sin ids manda el título");
        assertEquals(new Note("id-1", "Diario", ""), sinId,
                "basta con que a una le falte el id para caer en la comparación por título");
        assertNotEquals(sinId, new Note("Agenda", "contenido"));
    }

    @Test
    void noEsIgualANullNiAOtroTipo() {
        Note nota = new Note("id-1", "Diario", "");
        assertNotEquals(null, nota);
        assertNotEquals(nota, new Folder("id-1", "Diario"),
                "getClass() distinto corta antes de comparar nada");
    }

    @Test
    void esIgualASiMisma() {
        Note nota = new Note("id-1", "Diario", "");
        assertEquals(nota, nota);
    }

    // ── hashCode ─────────────────────────────────────────────────────────────

    @Test
    void elHashSaleDelIdCuandoLoHay() {
        assertEquals("id-1".hashCode(), new Note("id-1", "Diario", "").hashCode());
        assertEquals(new Note("id-1", "A", "").hashCode(), new Note("id-1", "B", "").hashCode(),
                "mismo id, mismo hash, aunque cambie el título");
    }

    @Test
    void sinIdElHashSaleDelTitulo() {
        assertEquals("Diario".hashCode(), new Note("Diario", "contenido").hashCode());
    }

    /**
     * <b>Incumplimiento del contrato, fijado a propósito.</b>
     *
     * <p>Idéntico al de {@link Folder}: {@code equals} cae a comparar títulos cuando a una
     * de las dos le falta el id, pero {@code hashCode} usa el id de la que sí lo tiene. Java
     * exige que dos objetos iguales compartan hash.</p>
     *
     * <p>Que aparezca igual en las dos clases indica un patrón copiado, no un descuido
     * puntual. Arreglarlo afectaría a toda colección de notas de la aplicación, así que es
     * una decisión aparte de congelar el comportamiento actual.</p>
     */
    @Test
    void equalsYHashCodeSonIncoherentesCuandoSoloUnaTieneId() {
        Note conId = new Note("id-1", "Diario", "");
        Note sinId = new Note("Diario", "");

        assertEquals(conId, sinId, "equals las da por iguales");
        assertNotEquals(conId.hashCode(), sinId.hashCode(),
                "pero el hash difiere: incumple el contrato equals/hashCode");

        Set<Note> conjunto = new HashSet<>();
        conjunto.add(conId);
        conjunto.add(sinId);
        assertEquals(2, conjunto.size(),
                "dos notas 'iguales' conviven en el mismo Set por culpa del hash distinto");
    }

    // ── etiquetas ────────────────────────────────────────────────────────────

    @Test
    void addTagIgnoraNull() {
        Note nota = new Note("id-1", "Diario", "");
        nota.addTag(null);
        assertTrue(nota.getTags().isEmpty(), "añadir null no debe meter nada");

        nota.addTag(new Tag("trabajo"));
        assertEquals(1, nota.getTags().size());
    }

    @Test
    void addAllTagsIgnoraNullYListaVacia() {
        Note nota = new Note("id-1", "Diario", "");
        nota.addAllTags(null);
        assertTrue(nota.getTags().isEmpty(), "null no añade nada");

        nota.addAllTags(List.of());
        assertTrue(nota.getTags().isEmpty(), "una lista vacía tampoco");

        nota.addAllTags(List.of(new Tag("trabajo"), new Tag("ideas")));
        assertEquals(2, nota.getTags().size());
    }

    // ── metadatos ────────────────────────────────────────────────────────────

    @Test
    void losMetadatosOpcionalesVanYVuelven() {
        Note nota = new Note("id-1", "Diario", "");
        nota.setLatitude(40.4);
        nota.setLongitude(-3.7);
        nota.setSource("importado");
        nota.setSourceUrl("https://ejemplo.test/x");
        nota.setSourceApplication("Evernote");
        nota.setDeletedDate("2026-08-14T10:00:00Z");

        assertEquals(40.4, nota.getLatitude());
        assertEquals(-3.7, nota.getLongitude());
        assertEquals("importado", nota.getSource());
        assertEquals("https://ejemplo.test/x", nota.getSourceUrl());
        assertEquals("Evernote", nota.getSourceApplication());
        assertEquals("2026-08-14T10:00:00Z", nota.getDeletedDate());
    }

    @Test
    void pinnedYFavoriteEmpiezanEnFalso() {
        Note nota = new Note("id-1", "Diario", "");
        assertFalse(nota.isPinned(), "una nota nueva no está fijada");
        assertFalse(nota.isFavorite(), "ni es favorita");

        nota.setPinned(true);
        assertTrue(nota.isPinned());
    }

    @Test
    void lasClavesDeFrontmatterVisiblesSeCopianYNullLasVacia() {
        Note nota = new Note("id-1", "Diario", "");
        assertTrue(nota.getDisplayableFrontmatterPropertyKeys().isEmpty());

        Set<String> origen = new LinkedHashSet<>(List.of("autor", "estado"));
        nota.setDisplayableFrontmatterPropertyKeys(origen);
        assertEquals(Set.of("autor", "estado"), nota.getDisplayableFrontmatterPropertyKeys());

        // Se guarda una copia: tocar el conjunto original no debe alterar la nota.
        origen.add("colado");
        assertFalse(nota.getDisplayableFrontmatterPropertyKeys().contains("colado"),
                "la nota debe quedarse con una copia, no con la referencia del llamador");

        nota.setDisplayableFrontmatterPropertyKeys(null);
        assertTrue(nota.getDisplayableFrontmatterPropertyKeys().isEmpty(),
                "null vacía el conjunto en vez de dejarlo a null");
    }

    // ── constructor completo ─────────────────────────────────────────────────

    @Test
    void elConstructorCompletoAsignaElId() {
        Note nota = new Note("id-9", "Diario", "cuerpo", "2026-01-01", "2026-01-02",
                40.4, -3.7, "edu", "https://ejemplo.test", "importado", "Evernote");

        assertEquals("id-9", nota.getId(), "el id del constructor completo debe llegar al modelo");
        assertEquals("Diario", nota.getTitle());
        assertEquals("cuerpo", nota.getContent());
        assertEquals("edu", nota.getAuthor());
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toStringLlevaIdYTitulo() {
        String texto = new Note("id-1", "Diario", "cuerpo").toString();
        assertTrue(texto.contains("id-1") && texto.contains("Diario"),
                "toString debe seguir siendo útil para diagnosticar: " + texto);
    }
}
