import { afterEach, describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import { livePreview } from "../live-preview.js";

// Following a [[Note#Heading]] link must hand the heading to the app, so the note opens there.
describe("live preview wiki-link click", () => {
  afterEach(() => {
    delete window.javaEditor;
  });

  it("passes the heading or block of the link, not just the note", () => {
    const calls = [];
    window.javaEditor = { openNote: target => calls.push(target) };
    const parent = document.createElement("div");
    document.body.appendChild(parent);
    const view = new EditorView({
      state: EditorState.create({
        doc: "Primero\n\nIr a [[Otra#Detalle|ver]], [[Nota#^b1]] y [[Simple]]",
        selection: { anchor: 0 },
        extensions: [markdown({ extensions: [GFM, markExtension] }), livePreview()]
      }),
      parent
    });

    const links = [...view.dom.querySelectorAll(".cm-live-wikilink")];
    expect(links.map(link => link.textContent)).toEqual(["ver", "Nota#^b1", "Simple"]);
    for (const link of links) {
      link.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, metaKey: true }));
    }

    expect(calls).toEqual(["Otra#Detalle", "Nota#^b1", "Simple"]);
    view.destroy();
  });
});
