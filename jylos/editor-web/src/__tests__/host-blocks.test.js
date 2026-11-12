import { describe, expect, it } from "vitest";
import { Text } from "@codemirror/state";
import { scanHostBlocks } from "../live-preview.js";

// Same document as EditorHostBlocksTest.java: both sides must find the same blocks and keys.
const DOC = [
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
  "Texto ![[En línea]] no es bloque"
].join("\n");

describe("host-rendered blocks", () => {
  it("finds the callouts and the notes embedded on their own line, outside code", () => {
    expect(scanHostBlocks(Text.of(DOC.split("\n"))).map(block => block.key)).toEqual([
      "callout\n> [!tip] Consejo\n> Línea uno\n> Línea dos",
      "embed\nOtra nota#Sección"
    ]);
  });
});

import { EditorState as State } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { blockRenderField, livePreview, setBlockRenders } from "../live-preview.js";

describe("host-rendered block layout", () => {
  it("does not turn the line breaks of the rendered HTML into blank lines", () => {
    const parent = document.createElement("div");
    document.body.appendChild(parent);
    const doc = "> [!tip] Consejo\n> Texto\n\nfin";
    const view = new EditorView({
      state: State.create({ doc, selection: { anchor: doc.length }, extensions: [blockRenderField, livePreview()] }),
      parent
    });
    view.dispatch({ effects: setBlockRenders.of({ "callout\n> [!tip] Consejo\n> Texto":
      "<div class=\"callout callout-cyan\">\n<div class=\"callout-title\">Consejo</div>\n<p>Texto</p>\n</div>" }) });
    const block = view.dom.querySelector(".cm-live-block-render");
    expect(block).not.toBeNull();
    expect(getComputedStyle(block).whiteSpace).toBe("normal");
    view.destroy();
  });
});
