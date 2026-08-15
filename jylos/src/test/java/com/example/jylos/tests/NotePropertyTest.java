package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.NoteProperty;
import com.example.jylos.data.models.NoteProperty.PropertyType;

/**
 * {@link NoteProperty}: inferencia de tipo desde YAML crudo, fábrica {@code of} e icono.
 *
 * <p>Estaba al 0% de mutación — las 21 mutaciones de la clase eran {@code NO_COVERAGE}, ni
 * una sola ejercitada. No es un rincón trivial: es la lógica que decide cómo se renderiza
 * cada campo de frontmatter en el panel de propiedades del editor (checkbox, selector de
 * fecha, número o texto plano). Inferir mal el tipo cambia lo que ve el usuario.</p>
 *
 * <p>Tests de <em>caracterización</em>: fijan las reglas de inferencia tal como están hoy,
 * incluidas las ambigüedades — ver {@link #unNumeroConFormatoDeFechaSeInfiereComoNumero()}.
 * Es un {@code record}, así que {@code equals}/{@code hashCode}/accesores los genera Java
 * y no hace falta testearlos aquí; lo que sí es código propio es {@code inferType},
 * {@code of} e {@code icon}.</p>
 */
class NotePropertyTest {

    // ── inferType: casos base ────────────────────────────────────────────────

    @Test
    void nuloOEnBlancoEsTexto() {
        assertEquals(PropertyType.TEXT, NoteProperty.inferType(null));
        assertEquals(PropertyType.TEXT, NoteProperty.inferType(""));
        assertEquals(PropertyType.TEXT, NoteProperty.inferType("   "));
    }

    @Test
    void trueYFalseSonBooleanoSinImportarMayusculas() {
        assertEquals(PropertyType.BOOLEAN, NoteProperty.inferType("true"));
        assertEquals(PropertyType.BOOLEAN, NoteProperty.inferType("false"));
        assertEquals(PropertyType.BOOLEAN, NoteProperty.inferType("TRUE"));
        assertEquals(PropertyType.BOOLEAN, NoteProperty.inferType("False"));
    }

    @Test
    void entreCorchetesEsLista() {
        assertEquals(PropertyType.LIST, NoteProperty.inferType("[uno, dos, tres]"));
        assertEquals(PropertyType.LIST, NoteProperty.inferType("[]"));
    }

    @Test
    void numeroEnteroONegativoODecimalEsNumero() {
        assertEquals(PropertyType.NUMBER, NoteProperty.inferType("42"));
        assertEquals(PropertyType.NUMBER, NoteProperty.inferType("-7"));
        assertEquals(PropertyType.NUMBER, NoteProperty.inferType("3.14"));
    }

    @Test
    void fechaSinHoraEsDate() {
        assertEquals(PropertyType.DATE, NoteProperty.inferType("2026-08-14"));
    }

    @Test
    void fechaConHoraEsDatetime() {
        assertEquals(PropertyType.DATETIME, NoteProperty.inferType("2026-08-14T10:30:00Z"));
        assertEquals(PropertyType.DATETIME, NoteProperty.inferType("2026-08-14T10:30"));
    }

    @Test
    void cualquierOtraCosaEsTexto() {
        assertEquals(PropertyType.TEXT, NoteProperty.inferType("un texto cualquiera"));
        assertEquals(PropertyType.TEXT, NoteProperty.inferType("no-es-fecha-2026"));
    }

    // ── inferType: orden de las reglas, documentado a propósito ────────────────

    /**
     * El patrón de número (\d+(\.\d+)?) coincide con "2026" completo, así que un valor de
     * cuatro dígitos que <em>parece</em> el principio de una fecha se clasifica como
     * NUMBER: la comprobación de número va antes que la de fecha y el patrón de fecha exige
     * el separador completo "AAAA-MM-DD". Documentado, no corregido: cambiar el orden
     * cambiaría cómo se muestran campos reales de vaults existentes.
     */
    @Test
    void unNumeroConFormatoDeFechaSeInfiereComoNumero() {
        assertEquals(PropertyType.NUMBER, NoteProperty.inferType("2026"),
                "cuatro dígitos solos no cumplen el patrón de fecha completo, así que caen en número");
    }

    @Test
    void espaciosAlrededorNoImpidenLaDeteccion() {
        assertEquals(PropertyType.NUMBER, NoteProperty.inferType("  42  "));
        assertEquals(PropertyType.BOOLEAN, NoteProperty.inferType(" true "));
    }

    // ── of() ─────────────────────────────────────────────────────────────────

    @Test
    void ofInfiereElTipoYConservaElValorCrudo() {
        NoteProperty prop = NoteProperty.of("prioridad", "3");
        assertEquals("prioridad", prop.key());
        assertEquals(PropertyType.NUMBER, prop.type());
        assertEquals("3", prop.rawValue());
    }

    @Test
    void ofConValorNuloGuardaCadenaVacia() {
        NoteProperty prop = NoteProperty.of("alias", null);
        assertEquals("", prop.rawValue(), "el valor crudo nunca debe quedar en null");
        assertEquals(PropertyType.TEXT, prop.type());
    }

    // ── icon() ───────────────────────────────────────────────────────────────

    @Test
    void cadaTipoTieneSuPropioIcono() {
        assertEquals("☑", NoteProperty.of("k", "true").icon());
        assertEquals("#", NoteProperty.of("k", "42").icon());
        assertEquals("📅", NoteProperty.of("k", "2026-08-14").icon());
        assertEquals("🕐", NoteProperty.of("k", "2026-08-14T10:00:00Z").icon());
        assertEquals("≡", NoteProperty.of("k", "[a, b]").icon());
        assertEquals("T", NoteProperty.of("k", "texto").icon());
    }
}
