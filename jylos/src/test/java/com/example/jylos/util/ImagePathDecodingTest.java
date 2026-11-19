package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A vault image path is read as Obsidian reads a link: {@code %20} is a space and a
 * {@code +} is a {@code +}, so {@code ![](C++.png)} finds {@code C++.png}.
 */
class ImagePathDecodingTest {

    @Test
    void aPlusInAnImagePathIsAPlus(@TempDir Path dir) throws Exception {
        byte[] bytes = {1, 2, 3};
        Files.write(dir.resolve("C++ notas.png"), bytes);
        String expected = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);

        assertEquals(expected, MarkdownPreview.resolveImageSource("C++%20notas.png", dir));
        assertEquals(expected, MarkdownPreview.resolveImageSource("C++ notas.png", dir));
    }
}
