package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;

/** Integers are YAML 1.2's, as Obsidian reads them: a leading zero does not make one octal. */
class FrontmatterOctalTest {

    @Test
    void aLeadingZeroIsDecimalAndHexAndOctalHaveTheirPrefix() {
        Map<String, Object> values = FrontmatterHandler.propertiesOf(
                FrontmatterHandler.parse("---\ncodigo: 0123\nhex: 0x1F\noct: 0o17\ngrande: 12345678901\n---\n"));

        assertEquals(Map.of("codigo", 123, "hex", 31, "oct", 15, "grande", 12345678901L), values);
    }
}
