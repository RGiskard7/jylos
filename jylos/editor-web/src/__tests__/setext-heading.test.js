import { afterEach, describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import { blockRenderField, livePreview, livePreviewPlugin, setEditorFocus } from "../live-preview.js";

function makeView(doc, anchor, focused = true) {
  const parent = document.createElement("div");
  document.body.appendChild(parent);
  const view = new EditorView({
    state: EditorState.create({
      doc,
      selection: { anchor },
      extensions: [markdown({ extensions: [GFM, markExtension] }), blockRenderField, livePreview()]
    }),
    parent
  });
  view.dispatch({ effects: setEditorFocus.of(focused) });
  return view;
}

/** The text of [from, to) that a decoration hides or replaces. */
function covered(view, from, to) {
  const out = [];
  view.plugin(livePreviewPlugin).decorations.between(from, to, (rangeFrom, rangeTo, deco) => {
    if (rangeTo > rangeFrom && rangeFrom < to && rangeTo > from) out.push(view.state.sliceDoc(rangeFrom, rangeTo));
  });
  return out;
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("a heading underlined with --- or ===", () => {
  for (const mark of ["---", "==="]) {
    const doc = `Antes\n\nTítulo\n${mark}\n\nDespués`;
    const markFrom = doc.indexOf(mark);
    const markTo = markFrom + mark.length;

    it(`shows its ${mark} when the cursor is on the ${mark} line`, () => {
      expect(covered(makeView(doc, markFrom + 1), markFrom, markTo)).toEqual([]);
    });

    it(`shows its ${mark} when the cursor is on the title line`, () => {
      expect(covered(makeView(doc, doc.indexOf("Título") + 2), markFrom, markTo)).toEqual([]);
    });

    it(`hides its ${mark} when the cursor is elsewhere`, () => {
      expect(covered(makeView(doc, 0), markFrom, markTo)).toEqual([mark]);
    });
  }
});
