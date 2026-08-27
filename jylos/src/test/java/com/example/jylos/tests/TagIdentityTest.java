package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Tag;

/**
 * Identidad de {@link Tag}: {@code equals} propio y {@code hashCode} heredado de
 * {@code BaseModel}.
 *
 * <p>A diferencia de {@link Note} y {@link Folder}, {@code Tag} compara <em>siempre</em>
 * por título, tenga o no id — y {@code hashCode} (heredado, sin override) también hashea
 * solo por título. Los dos coinciden, así que aquí <b>no</b> hay el incumplimiento de
 * contrato que sí tienen las otras dos clases. Este test lo deja fijado como referencia de
 * cómo se comporta cuando está bien hecho.</p>
 */
class TagIdentityTest {

    @Test
    void dosEtiquetasConElMismoTituloSonIguales() {
        assertEquals(new Tag("id-1", "trabajo"), new Tag("id-2", "trabajo"),
                "a diferencia de Note/Folder, Tag ignora el id y compara solo el título");
    }

    @Test
    void dosEtiquetasConTituloDistintoNoSonIguales() {
        assertNotEquals(new Tag("trabajo"), new Tag("ideas"));
    }

    @Test
    void noEsIgualANullNiAOtroTipo() {
        Tag etiqueta = new Tag("trabajo");
        assertNotEquals(null, etiqueta);
        assertNotEquals(etiqueta, new Folder("trabajo"),
                "getClass() distinto corta antes de comparar nada");
    }

    @Test
    void esIgualASiMisma() {
        Tag etiqueta = new Tag("trabajo");
        assertEquals(etiqueta, etiqueta);
    }

    @Test
    void equalsYHashCodeSonCoherentesPorqueLosDosUsanSoloElTitulo() {
        Tag conId = new Tag("id-1", "trabajo");
        Tag sinId = new Tag("trabajo");

        assertEquals(conId, sinId);
        assertEquals(conId.hashCode(), sinId.hashCode(),
                "aquí sí se cumple el contrato: mismo título, mismo hash, con o sin id");
    }

    @Test
    void toStringLlevaIdYTitulo() {
        String texto = new Tag("id-1", "trabajo").toString();
        assertTrue(texto.contains("id-1") && texto.contains("trabajo"),
                "toString debe seguir siendo útil para diagnosticar: " + texto);
    }
}
