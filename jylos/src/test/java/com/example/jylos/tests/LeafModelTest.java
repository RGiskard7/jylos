package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * {@link LeafModel}, a través de {@link Note} — su única implementación concreta con
 * comportamiento propio, ya que {@code LeafModel} es abstracta.
 *
 * <p>Dos cosas se prueban: la construcción de {@link Note#getPath()} a partir del padre
 * (recursiva mientras haya padres, {@code "/título"} en la raíz), y el contrato real de
 * "hoja": un nodo hoja no puede tener hijos, así que {@code add}/{@code addAll}/
 * {@code setChildren}/{@code remove}/{@code getChildren} deben lanzar
 * {@link UnsupportedOperationException} en vez de fallar en silencio o corromper estado.
 * Esto último no es un detalle interno: es el contrato que distingue una hoja de un
 * contenedor en toda la jerarquía de {@code Component}.</p>
 */
class LeafModelTest {

    // ── parent ───────────────────────────────────────────────────────────────

    @Test
    void sinPadreAsignadoDevuelveNull() {
        assertNull(new Note("id-1", "Diario", "").getParent());
    }

    @Test
    void setParentSeReflejaEnGetParent() {
        Note nota = new Note("id-1", "Diario", "");
        Folder carpeta = new Folder("id-2", "Trabajo");
        nota.setParent(carpeta);
        assertSame(carpeta, nota.getParent());
    }

    // ── getPath ──────────────────────────────────────────────────────────────

    @Test
    void sinPadreLaRutaEsBarraMasTitulo() {
        assertEquals("/Diario", new Note("id-1", "Diario", "").getPath());
    }

    @Test
    void conPadreLaRutaEncadenaLaDelPadre() {
        Folder carpeta = new Folder("id-2", "Trabajo");
        Note nota = new Note("id-1", "Diario", "");
        nota.setParent(carpeta);

        assertEquals("/Trabajo/Diario", nota.getPath(),
                "debe delegar en getPath() del padre, no reconstruirla a mano");
    }

    @Test
    void conAbueloLaRutaEncadenaLosTresNiveles() {
        Folder abuelo = new Folder("id-3", "Vault");
        Folder padre = new Folder("id-2", "Trabajo");
        padre.setParent(abuelo);
        Note nota = new Note("id-1", "Diario", "");
        nota.setParent(padre);

        assertEquals("/Vault/Trabajo/Diario", nota.getPath(),
                "la recursión debe subir tantos niveles como padres haya");
    }

    // ── contrato de hoja: no puede tener hijos ──────────────────────────────

    @Test
    void addLanzaPorqueUnaHojaNoAceptaHijos() {
        assertThrows(UnsupportedOperationException.class,
                () -> new Note("id-1", "Diario", "").add(new Note("id-2", "Otra", "")));
    }

    @Test
    void addAllLanzaPorqueUnaHojaNoAceptaHijos() {
        assertThrows(UnsupportedOperationException.class,
                () -> new Note("id-1", "Diario", "").addAll(List.of()));
    }

    @Test
    void setChildrenLanzaPorqueUnaHojaNoAceptaHijos() {
        assertThrows(UnsupportedOperationException.class,
                () -> new Note("id-1", "Diario", "").setChildren(List.of()));
    }

    @Test
    void removeLanzaPorqueUnaHojaNoTieneHijosQueQuitar() {
        assertThrows(UnsupportedOperationException.class,
                () -> new Note("id-1", "Diario", "").remove(new Note("id-2", "Otra", "")));
    }

    @Test
    void getChildrenLanzaPorqueUnaHojaNoTieneHijos() {
        assertThrows(UnsupportedOperationException.class,
                () -> new Note("id-1", "Diario", "").getChildren());
    }
}
