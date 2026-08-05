import { describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import { livePreview, livePreviewPlugin } from "../live-preview.js";

function countDecorations(rangeSet) {
  let count = 0;
  rangeSet.between(0, 1e9, () => {
    count++;
  });
  return count;
}

function isHidden(rangeSet, from, to) {
  // Only count bare `Decoration.replace({})` marks (no widget, no class) as
  // "hidden" — a styling mark (e.g. cm-live-underline) merely touching the
  // query range's boundary must not be mistaken for a hide decoration.
  let hidden = false;
  rangeSet.between(from, to, (rangeFrom, rangeTo, deco) => {
    if (rangeFrom < to && rangeTo > from && !deco.spec.widget && !deco.spec.class) {
      hidden = true;
    }
  });
  return hidden;
}

function makeView(doc) {
  const parent = document.createElement("div");
  document.body.appendChild(parent);
  return new EditorView({
    state: EditorState.create({
      doc,
      extensions: [markdown({ extensions: [GFM, markExtension] }), livePreview()]
    }),
    parent
  });
}

describe("live-preview decoration rebuild", () => {
  it("does not rebuild decorations while the selection moves within the same block", () => {
    const doc = "Hello **world** and more plain text on this very same paragraph line.";
    const view = makeView(doc);
    const plugin = view.plugin(livePreviewPlugin);
    const initialDecorations = plugin.decorations;

    // Simulate the continuous selectionchange ticks a native drag-select
    // produces, all landing inside the same paragraph as the anchor.
    for (const pos of [5, 10, 15, 20, 30, 40]) {
      view.dispatch({ selection: { anchor: pos } });
    }

    expect(view.plugin(livePreviewPlugin).decorations).toBe(initialDecorations);
    view.destroy();
  });

  it("rebuilds decorations when the selection moves into a different block", () => {
    const doc = "First paragraph with **bold** text.\n\nSecond paragraph, a different block.";
    const view = makeView(doc);
    const plugin = view.plugin(livePreviewPlugin);
    const initialDecorations = plugin.decorations;

    const secondParagraphPos = doc.indexOf("Second") + 3;
    view.dispatch({ selection: { anchor: secondParagraphPos } });

    expect(view.plugin(livePreviewPlugin).decorations).not.toBe(initialDecorations);
    view.destroy();
  });

  it("still rebuilds decorations on document changes", () => {
    // Selection stays in the second (inactive) paragraph throughout, so a
    // bold mark inserted into the first paragraph goes from unstyled plain
    // text to a hidden/decorated mark — a real, countable content change,
    // not just a same-empty-RangeSet false negative.
    const doc = "First paragraph without marks.\n\nSecond paragraph holds the cursor.";
    const view = makeView(doc);
    const cursorPos = doc.indexOf("Second") + 3;
    view.dispatch({ selection: { anchor: cursorPos } });
    const initialCount = countDecorations(view.plugin(livePreviewPlugin).decorations);

    view.dispatch({ changes: { from: 0, insert: "**" }, selection: { anchor: cursorPos + 2 } });
    view.dispatch({ changes: { from: 2 + "First paragraph without marks.".length, insert: "**" } });

    const updatedCount = countDecorations(view.plugin(livePreviewPlugin).decorations);
    expect(updatedCount).toBeGreaterThan(initialCount);
    view.destroy();
  });
});

// Snapshot-style coverage for the two mark kinds already handled, so a future
// change to buildDecorations (e.g. adding Mark/HTML-underline support) has a
// baseline to regress against.
describe("live-preview reveal/hide of raw syntax marks", () => {
  it("hides emphasis marks in an inactive block and reveals them in the active block", () => {
    const doc = "First paragraph with **bold** text.\n\nSecond paragraph holds the cursor.";
    const markFrom = doc.indexOf("**");
    const view = makeView(doc);

    // Cursor in the second paragraph: the first paragraph is inactive.
    view.dispatch({ selection: { anchor: doc.indexOf("Second") + 3 } });
    expect(isHidden(view.plugin(livePreviewPlugin).decorations, markFrom, markFrom + 2)).toBe(true);

    // Cursor moves inside the first paragraph: its marks become visible.
    view.dispatch({ selection: { anchor: markFrom + 1 } });
    expect(isHidden(view.plugin(livePreviewPlugin).decorations, markFrom, markFrom + 2)).toBe(false);
    view.destroy();
  });

  it("hides heading marks in an inactive block", () => {
    const doc = "# Heading one\n\nSecond paragraph holds the cursor.";
    const view = makeView(doc);
    view.dispatch({ selection: { anchor: doc.indexOf("Second") + 3 } });

    expect(isHidden(view.plugin(livePreviewPlugin).decorations, 0, 2)).toBe(true);
    view.destroy();
  });

  it("hides ==mark== delimiters in an inactive block", () => {
    const doc = "First paragraph with ==highlighted== text.\n\nSecond paragraph holds the cursor.";
    const markFrom = doc.indexOf("==");
    const view = makeView(doc);
    view.dispatch({ selection: { anchor: doc.indexOf("Second") + 3 } });

    expect(isHidden(view.plugin(livePreviewPlugin).decorations, markFrom, markFrom + 2)).toBe(true);
    view.destroy();
  });

  it("hides <u></u> tags and underlines the content in an inactive block", () => {
    const doc = "First paragraph with <u>underlined</u> text.\n\nSecond paragraph holds the cursor.";
    const openFrom = doc.indexOf("<u>");
    const closeFrom = doc.indexOf("</u>");
    const view = makeView(doc);
    view.dispatch({ selection: { anchor: doc.indexOf("Second") + 3 } });

    const decorations = view.plugin(livePreviewPlugin).decorations;
    expect(isHidden(decorations, openFrom, openFrom + 3)).toBe(true);
    expect(isHidden(decorations, closeFrom, closeFrom + 4)).toBe(true);

    let underlineClass = null;
    decorations.between(openFrom, closeFrom, (from, to, deco) => {
      if (deco.spec.class === "cm-live-underline") underlineClass = deco.spec.class;
    });
    expect(underlineClass).toBe("cm-live-underline");
    view.destroy();
  });

  it("keeps <u></u> tags visible in the active block but still underlines the content", () => {
    const doc = "Paragraph with <u>underlined</u> text under the cursor.";
    const openFrom = doc.indexOf("<u>");
    const view = makeView(doc);
    view.dispatch({ selection: { anchor: openFrom + 1 } });

    const decorations = view.plugin(livePreviewPlugin).decorations;
    expect(isHidden(decorations, openFrom, openFrom + 3)).toBe(false);

    let underlineClass = null;
    decorations.between(openFrom, openFrom + 20, (from, to, deco) => {
      if (deco.spec.class === "cm-live-underline") underlineClass = deco.spec.class;
    });
    expect(underlineClass).toBe("cm-live-underline");
    view.destroy();
  });
});
