package com.example.jylos.plugin.mermaid;

import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PreviewEnhancer;

/**
 * Plugin to render Mermaid diagrams in note previews.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public class MermaidPlugin implements Plugin {

    private PluginContext context;
    private MermaidPreviewEnhancer enhancer;

    @Override
    public String getId() {
        return "mermaid-renderer";
    }

    @Override
    public String getName() {
        return "Mermaid Diagrams";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Renders Mermaid diagrams in the note preview. Use ```mermaid code block ```.";
    }

    @Override
    public String getAuthor() {
        return "Edu Díaz";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        this.enhancer = new MermaidPreviewEnhancer();

        // Register the preview enhancer
        context.registerPreviewEnhancer(enhancer);

        // Register a menu item to help users
        context.registerMenuItem("Diagrams", "Insert Mermaid Template", () -> {
            context.showInfo("Mermaid Template", "Copy this to your note:",
                    "```mermaid\n" +
                            "graph TD\n" +
                            "    A[\"Inicio\"] --> B{\"¿Funciona?\"}\n" +
                            "    B -- \"Sí\" --> C[\"¡Éxito!\"]\n" +
                            "    B -- \"No\" --> D[\"Refactorizar\"]\n" +
                            "```");
        });

        context.log("Mermaid Plugin initialized");
    }

    @Override
    public void shutdown() {
        if (context != null) {
            context.unregisterPreviewEnhancer();
        }
    }

    /**
     * Enhancer to inject Mermaid JS and initialization script.
     */
    private class MermaidPreviewEnhancer implements PreviewEnhancer {

        @Override
        public String getHeadInjections() {
            // No head injections needed, we load script at end of body for performance
            return "";
        }

        @Override
        public String getBodyInjections() {
            // 1. Load Mermaid from local bundled resource (reliable)
            // 2. Transform code blocks
            // 3. Initialize

            String mermaidScript = "";
            try {
                // Read the bundled JS file
                var is = getClass().getResourceAsStream("/com/example/jylos/plugin/mermaid/mermaid.min.js");
                if (is != null) {
                    mermaidScript = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    return "<script>console.error('Mermaid JS resource not found');</script>";
                }
            } catch (Exception e) {
                return "<script>console.error('Error loading Mermaid JS: " + e.getMessage() + "');</script>";
            }

            return "" +
                    "<script>\n" +
                    mermaidScript +
                    "\n</script>\n" +
                    "<script>\n" +
                    "  function decodeHtmlEntities(str) {\n" +
                    "      var txt = document.createElement('textarea');\n" +
                    "      txt.innerHTML = str;\n" +
                    "      return txt.value;\n" +
                    "  }\n" +
                    "\n" +
                    "  function transformMermaid() {\n" +
                    "    document.querySelectorAll('pre code').forEach(el => {\n" +
                    "        // 1. Get raw content (handling HTML entities which might be present)\n" +
                    "        let content = decodeHtmlEntities(el.innerHTML).trim();\n" +
                    "        if (!content) return;\n" +
                    "        \n" +
                    "        // 2. Clean up semicolons (Mermaid < 10 sometimes chokes on them in headers)\n" +
                    "        content = content.replace(/^(graph\\s+[A-Z]+);/i, '$1');\n" +
                    "        \n" +
                    "        const isMermaid = el.classList.contains('language-mermaid') || \n" +
                    "                         /^graph\\s|^sequenceDiagram|^gantt|^classDiagram|^stateDiagram|^erDiagram|^pie|^journey|^gitGraph|^C4Context|^mindmap|^timeline/i.test(content);\n"
                    +
                    "        \n" +
                    "        if (isMermaid) {\n" +
                    "            const pre = el.closest('pre');\n" +
                    "            if (pre) {\n" +
                    "                const div = document.createElement('div');\n" +
                    "                div.className = 'mermaid';\n" +
                    "                // Use textContent to ensure no HTML injection\n" +
                    "                div.textContent = content;\n" +
                    "                pre.replaceWith(div);\n" +
                    "            }\n" +
                    "        }\n" +
                    "    });\n" +
                    "  }\n" +
                    "  \n" +
                    "  // Initial run\n" +
                    "  transformMermaid();\n" +
                    "  \n" +
                    "  // Init Mermaid\n" +
                    "  if (typeof mermaid !== 'undefined') {\n" +
                    "      mermaid.initialize({\n" +
                    "          startOnLoad: false, \n" +
                    "          theme: 'default',\n" +
                    "          securityLevel: 'loose',\n" +
                    // Lift Mermaid's default caps (50k chars / 500 edges) so a diagram
                    // is never refused for size — notes must render whatever their length.
                    "          maxTextSize: 5000000,\n" +
                    "          maxEdges: 100000,\n" +
                    "          flowchart: { useMaxWidth: true, htmlLabels: true }\n" +
                    "      });\n" +
                    "      try {\n" +
                    "        mermaid.init(undefined, '.mermaid');\n" +
                    "      } catch(e) { console.error('Mermaid render error: ' + e); }\n" +
                    "  } else {\n" +
                    "      console.error('Mermaid object not defined after script load');\n" +
                    "  }\n" +
                    "</script>\n";
        }
    }
}
