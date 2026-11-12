package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Live Preview shows callouts and notes embedded on their own line rendered, as the reading
 * view does. The host finds those blocks and renders them off the UI thread; the editor finds
 * the same blocks and looks the HTML up by the same key (the editor's side of this contract
 * is checked by editor-web/src/__tests__/host-blocks.test.js with the same document).
 */
class EditorHostBlocksTest {

    /** Same document as host-blocks.test.js. */
    static final String DOC = String.join("\n",
            "Intro",
            "",
            "> [!tip] Consejo",
            "> Línea uno",
            "> Línea dos",
            "",
            "> cita normal",
            "",
            "![[Otra nota#Sección|alias]]",
            "",
            "![[foto.png]]",
            "",
            "```",
            "> [!note] dentro de código",
            "![[Tampoco]]",
            "```",
            "Texto ![[En línea]] no es bloque");

    @Test
    void findsTheSameBlocksAndKeysAsTheEditor() {
        assertEquals(List.of(
                "callout\n> [!tip] Consejo\n> Línea uno\n> Línea dos",
                "embed\nOtra nota#Sección"),
                EditorBlockRenderSupport.hostBlocks(DOC).stream().map(EditorBlockRenderSupport.HostBlock::key).toList());
    }

    @Test
    void rendersCalloutsAndEmbedsWithoutAnyPlugin() throws Exception {
        EditorBlockRenderSupport support = new EditorBlockRenderSupport();
        support.setEmbedRenderer(link -> link.startsWith("Otra nota") ? "<div class=\"embed\">contenido</div>" : null);
        CompletableFuture<Map<String, String>> result = new CompletableFuture<>();
        try {
            support.requestRender(null, DOC, true, result::complete);
            Map<String, String> renders = result.get(10, TimeUnit.SECONDS);

            String callout = renders.get("callout\n> [!tip] Consejo\n> Línea uno\n> Línea dos");
            assertTrue(callout != null && callout.contains("class=\"callout callout-cyan\" data-callout=\"tip\"")
                    && callout.contains("Línea uno"), String.valueOf(callout));
            assertEquals("<div class=\"embed\">contenido</div>", renders.get("embed\nOtra nota#Sección"));
            assertEquals(2, renders.size(), "only the two host blocks: " + renders.keySet());
        } finally {
            support.shutdown();
        }
    }
}
