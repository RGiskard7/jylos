import { describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import { blockRenderDecorations, blockRenderField, livePreview, setBlockRenders } from "../live-preview.js";

function makeView(doc) {
  const parent = document.createElement("div");
  document.body.appendChild(parent);
  return new EditorView({
    state: EditorState.create({
      doc,
      // blockRenderField lives in the base state in editor.js, so mirror that here.
      extensions: [markdown({ extensions: [GFM, markExtension] }), blockRenderField, livePreview()]
    }),
    parent
  });
}

/** Finds the block widget covering [from, to), if the block was replaced. */
function widgetAt(view, from, to) {
  let found = null;
  view.state.field(blockRenderDecorations).between(from, to, (rangeFrom, rangeTo, deco) => {
    if (deco.spec.widget) found = deco.spec.widget;
  });
  return found;
}

/** Parks the cursor outside every block; a block under the cursor shows its source. */
function cursorOutside(view) {
  view.dispatch({ selection: { anchor: view.state.doc.length } });
}

const QUERY_BLOCK = "```dataview\nLIST\nFROM #book\n```";

describe("plugin-rendered fenced blocks", () => {
  it("replaces a claimed block once its rendered HTML arrives", () => {
    const view = makeView(`Intro text.\n\n${QUERY_BLOCK}\n\nOutro.`);
    const blockFrom = view.state.doc.toString().indexOf("```dataview");
    const blockTo = blockFrom + QUERY_BLOCK.length;

    expect(widgetAt(view, blockFrom, blockTo)).toBeNull();

    view.dispatch({
      effects: setBlockRenders.of({ "dataview\nLIST\nFROM #book": "<table><tr><td>Dune</td></tr></table>" })
    });

    const widget = widgetAt(view, blockFrom, blockTo);
    expect(widget).not.toBeNull();
    expect(widget.toDOM(view).innerHTML).toContain("Dune");
    view.destroy();
  });

  it("builds the key as language + newline + trimmed body", () => {
    // Guards the contract shared with EditorBlockRenderSupport.blockKey() on the Java
    // side: a mismatch here silently stops every block from ever rendering.
    const view = makeView("```DataView\n\n  LIST  \n\n```\n\ntail");
    cursorOutside(view);
    view.dispatch({ effects: setBlockRenders.of({ "dataview\nLIST": "<p>ok</p>" }) });

    expect(widgetAt(view, 0, view.state.doc.length)).not.toBeNull();
    view.destroy();
  });

  it("reveals the source while the cursor is inside the block", () => {
    const view = makeView(`${QUERY_BLOCK}\n\nAfter.`);
    cursorOutside(view);
    view.dispatch({ effects: setBlockRenders.of({ "dataview\nLIST\nFROM #book": "<p>rendered</p>" }) });
    expect(widgetAt(view, 0, QUERY_BLOCK.length)).not.toBeNull();

    view.dispatch({ selection: { anchor: 15 } });
    expect(widgetAt(view, 0, QUERY_BLOCK.length)).toBeNull();
    view.destroy();
  });

  it("leaves blocks no plugin claimed untouched", () => {
    const code = "```java\nint x = 1;\n```";
    const view = makeView(`${code}\n\ntail`);
    cursorOutside(view);
    view.dispatch({ effects: setBlockRenders.of({ "dataview\nLIST": "<p>nope</p>" }) });

    expect(widgetAt(view, 0, code.length)).toBeNull();
    view.destroy();
  });

  it("drops the rendered block when the host clears its results", () => {
    const view = makeView(`${QUERY_BLOCK}\n\ntail`);
    cursorOutside(view);
    view.dispatch({ effects: setBlockRenders.of({ "dataview\nLIST\nFROM #book": "<p>rendered</p>" }) });
    expect(widgetAt(view, 0, QUERY_BLOCK.length)).not.toBeNull();

    view.dispatch({ effects: setBlockRenders.of({}) });
    expect(widgetAt(view, 0, QUERY_BLOCK.length)).toBeNull();
    view.destroy();
  });

  it("keeps renders addressable when Live Preview is off", () => {
    // The field is part of the base state, so toggling presentation must not lose it.
    const parent = document.createElement("div");
    document.body.appendChild(parent);
    const view = new EditorView({
      state: EditorState.create({
        doc: QUERY_BLOCK,
        extensions: [markdown({ extensions: [GFM, markExtension] }), blockRenderField]
      }),
      parent
    });

    view.dispatch({ effects: setBlockRenders.of({ "dataview\nLIST\nFROM #book": "<p>x</p>" }) });
    expect(view.state.field(blockRenderField)["dataview\nLIST\nFROM #book"]).toBe("<p>x</p>");
    view.destroy();
  });
});
