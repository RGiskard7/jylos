import { afterEach, describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import { blockRenderDecorations, blockRenderField, livePreview, setBlockRenders, setMathReady } from "../live-preview.js";

// CodeMirror lays the document out from estimates and only measures what is on screen. A block
// widget that gives no estimate counts as a single line, so the page jumps by the block's real
// height when it scrolls into view. Rendered blocks estimate their height instead.

function makeView(doc) {
  const parent = document.createElement("div");
  document.body.appendChild(parent);
  return new EditorView({
    state: EditorState.create({
      doc,
      extensions: [markdown({ extensions: [GFM, markExtension] }), blockRenderField, livePreview()]
    }),
    parent
  });
}

function widgetAt(view, from) {
  let found = null;
  view.state.field(blockRenderDecorations).between(from, from + 1, (a, b, deco) => {
    if (deco.spec.widget) found = deco.spec.widget;
  });
  return found;
}

/** A ResizeObserver whose reports the test sends, with the height it chooses. */
function fakeResizeObserver() {
  const observers = [];
  window.ResizeObserver = class {
    constructor(callback) {
      this.callback = callback;
      this.targets = [];
      observers.push(this);
    }
    observe(target) { this.targets.push(target); }
    unobserve(target) { this.targets = this.targets.filter(t => t !== target); }
    disconnect() { this.targets = []; }
  };
  return {
    report(element, height) {
      element.getBoundingClientRect = () => ({ top: 0, bottom: height, height, left: 0, right: 0, width: 0 });
      for (const observer of observers) {
        if (observer.targets.includes(element)) observer.callback([{ target: element }]);
      }
    }
  };
}

const BODY = Array.from({ length: 10 }, (_, i) => `row ${i}`).join("\n");
const BLOCK = "```dataview\n" + BODY + "\n```"; // 12 lines
const KEY = "dataview\n" + BODY;

afterEach(() => {
  document.body.innerHTML = "";
  delete window.ResizeObserver;
});

describe("rendered block height estimates", () => {
  it("estimates a block never drawn as the height of its source lines, not one line", () => {
    const view = makeView(`Intro.\n\n${BLOCK}\n\nOutro.`);
    view.dispatch({ effects: setBlockRenders.of({ [KEY]: "<table><tr><td>a</td></tr></table>" }) });
    const from = view.state.doc.toString().indexOf("```dataview");

    expect(widgetAt(view, from).estimatedHeight).toBe(12 * view.defaultLineHeight);
    // And CodeMirror uses it: the block takes that height in its layout before being measured.
    expect(view.lineBlockAt(from).height).toBe(12 * view.defaultLineHeight);
    view.destroy();
  });

  it("estimates a block drawn before at the height it was drawn", () => {
    const sizes = fakeResizeObserver();
    const html = "<div class=\"callout\">drawn once</div>";
    const view = makeView(`Intro.\n\n${BLOCK}\n\nOutro.`);
    view.dispatch({ effects: setBlockRenders.of({ [KEY]: html }) });
    const from = view.state.doc.toString().indexOf("```dataview");
    const dom = widgetAt(view, from).toDOM(view);
    sizes.report(dom, 310);

    // Scrolled away and back (or the note reopened): a new widget for the same content.
    const again = makeView(`Other text.\n\n${BLOCK}`);
    again.dispatch({ effects: setBlockRenders.of({ [KEY]: html }) });
    const againFrom = again.state.doc.toString().indexOf("```dataview");
    expect(widgetAt(again, againFrom).estimatedHeight).toBe(310);

    // Other content is not taken for it.
    again.dispatch({ effects: setBlockRenders.of({ [KEY]: "<p>different</p>" }) });
    expect(widgetAt(again, againFrom).estimatedHeight).toBe(12 * again.defaultLineHeight);
    view.destroy();
    again.destroy();
  });

  it("estimates a display formula from its lines, then from the height it was drawn", () => {
    const sizes = fakeResizeObserver();
    window.katex = { renderToString: tex => `<span>${tex}</span>` };
    const doc = "Text.\n\n$$\na^2 + b^2\n= c^2\n$$\n\nMore."; // the formula is 4 lines
    const view = makeView(doc);
    view.dispatch({ effects: setMathReady.of(true) });
    const from = doc.indexOf("$$");
    const formula = widgetAt(view, from);

    expect(formula.estimatedHeight).toBe(4 * view.defaultLineHeight);
    sizes.report(formula.toDOM(view), 57);
    expect(widgetAt(view, from).estimatedHeight).toBe(57);
    delete window.katex;
    view.destroy();
  });
});
