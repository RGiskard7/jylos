package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.NoteService.SortOption;

/**
 * {@code sortNotes}/{@code getComparator}/{@code getEffectiveModifiedDate}: las seis
 * opciones de orden que la lista de notas de la UI ofrece al usuario. Estaba sin
 * cobertura — no pierde datos si falla, pero un orden incorrecto es un bug que el usuario
 * ve constantemente, no una vez.
 *
 * <p>No hace falta un servicio real ni disco: {@code sortNotes} solo depende de los
 * campos del propio {@code Note}.</p>
 */
class NoteServiceSortTest {

    private static NoteService servicio() {
        return new NoteService(null, null);
    }

    private static Note nota(String titulo, String creada, String modificada) {
        Note n = new Note(titulo, "");
        n.setCreatedDate(creada);
        n.setModifiedDate(modificada);
        return n;
    }

    private static List<String> titulos(List<Note> notas) {
        return notas.stream().map(Note::getTitle).toList();
    }

    @Test
    void listaNulaOVaciaDaListaVacia() {
        NoteService s = servicio();
        assertTrue(s.sortNotes(null, SortOption.TITLE_ASC).isEmpty());
        assertTrue(s.sortNotes(new ArrayList<>(), SortOption.TITLE_ASC).isEmpty());
    }

    @Test
    void noMutaLaListaOriginal() {
        List<Note> original = new ArrayList<>(List.of(nota("B", "1", "1"), nota("A", "1", "1")));
        List<Note> copia = List.copyOf(original);

        servicio().sortNotes(original, SortOption.TITLE_ASC);

        assertEquals(copia, original, "sortNotes debe ordenar una copia, no la lista que le pasan");
    }

    @Test
    void tituloAscendenteIgnoraMayusculas() {
        List<Note> notas = List.of(nota("banana", "1", "1"), nota("Manzana", "1", "1"), nota("Cereza", "1", "1"));
        assertEquals(List.of("banana", "Cereza", "Manzana"),
                titulos(servicio().sortNotes(notas, SortOption.TITLE_ASC)));
    }

    @Test
    void tituloDescendenteEsElOrdenInverso() {
        List<Note> notas = List.of(nota("A", "1", "1"), nota("B", "1", "1"), nota("C", "1", "1"));
        assertEquals(List.of("C", "B", "A"), titulos(servicio().sortNotes(notas, SortOption.TITLE_DESC)));
    }

    @Test
    void creacionMasReciente() {
        List<Note> notas = List.of(
                nota("Vieja", "2026-01-01", "x"),
                nota("Nueva", "2026-06-01", "x"),
                nota("Media", "2026-03-01", "x"));
        assertEquals(List.of("Nueva", "Media", "Vieja"),
                titulos(servicio().sortNotes(notas, SortOption.CREATED_NEWEST)));
    }

    @Test
    void creacionMasAntigua() {
        List<Note> notas = List.of(nota("Nueva", "2026-06-01", "x"), nota("Vieja", "2026-01-01", "x"));
        assertEquals(List.of("Vieja", "Nueva"), titulos(servicio().sortNotes(notas, SortOption.CREATED_OLDEST)));
    }

    @Test
    void modificacionMasReciente() {
        List<Note> notas = List.of(
                nota("Vieja", "x", "2026-01-01"),
                nota("Nueva", "x", "2026-06-01"));
        assertEquals(List.of("Nueva", "Vieja"),
                titulos(servicio().sortNotes(notas, SortOption.MODIFIED_NEWEST)));
    }

    @Test
    void modificacionMasAntigua() {
        List<Note> notas = List.of(
                nota("Nueva", "x", "2026-06-01"),
                nota("Vieja", "x", "2026-01-01"));
        assertEquals(List.of("Vieja", "Nueva"),
                titulos(servicio().sortNotes(notas, SortOption.MODIFIED_OLDEST)));
    }

    @Test
    void sinFechaDeModificacionCaeALaDeCreacion() {
        Note sinModificar = nota("Sin modificar", "2026-05-01", null);
        Note modificadaAntes = nota("Modificada antes", "2026-01-01", "2026-02-01");

        // getEffectiveModifiedDate: sin modifiedDate, usa createdDate (2026-05-01),
        // que es más reciente que el 2026-02-01 de la otra nota.
        assertEquals(List.of("Sin modificar", "Modificada antes"),
                titulos(servicio().sortNotes(List.of(modificadaAntes, sinModificar), SortOption.MODIFIED_NEWEST)));
    }

    @Test
    void fromDisplayNameEncuentraLaOpcionPorSuNombreVisible() {
        assertEquals(SortOption.TITLE_ASC, SortOption.fromDisplayName("Title (A-Z)"));
    }

    @Test
    void fromDisplayNameConNombreDesconocidoCaeAModificadoMasReciente() {
        assertEquals(SortOption.MODIFIED_NEWEST, SortOption.fromDisplayName("no existe"));
    }
}
