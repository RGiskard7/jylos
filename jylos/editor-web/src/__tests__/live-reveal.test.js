import { afterEach, describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import { blockRenderField, livePreview, livePreviewPlugin, setEditorFocus } from "../live-preview.js";

function makeView(doc, anchor = 0, focused = true) {
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

/** Ranges hidden by a bare replace (no widget, no class). */
function hidden(view, from, to) {
  let found = false;
  view.plugin(livePreviewPlugin).decorations.between(from, to, (rangeFrom, rangeTo, deco) => {
    if (rangeFrom < to && rangeTo > from && deco.spec.widget === undefined && !deco.spec.class
        && deco.spec.attributes === undefined && deco.point !== false) found = true;
  });
  return found;
}

/** Widgets (by class name) replacing text inside [from, to). */
function widgets(view, from = 0, to = view.state.doc.length) {
  const out = [];
  view.plugin(livePreviewPlugin).decorations.between(from, to, (rangeFrom, rangeTo, deco) => {
    if (deco.spec.widget) out.push(deco.spec.widget.className || deco.spec.widget.constructor.name);
  });
  return out;
}

function marks(view, className) {
  const out = [];
  view.plugin(livePreviewPlugin).decorations.between(0, view.state.doc.length, (from, to, deco) => {
    if (deco.spec.class === className) out.push(view.state.sliceDoc(from, to));
  });
  return out;
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("reveal per element", () => {
  it("shows the marks of the element under the cursor and keeps the rest of the line rendered", () => {
    const doc = "Uno **dos** y *tres* fin";
    const bold = doc.indexOf("**");
    const italic = doc.indexOf("*tres");
    const view = makeView(doc, bold + 3);

    expect(hidden(view, bold, bold + 2)).toBe(false);
    expect(hidden(view, italic, italic + 1)).toBe(true);
  });

  it("shows heading marks while the cursor is on that line only", () => {
    const doc = "# Título\n\nTexto";
    const view = makeView(doc, doc.indexOf("Texto"));
    expect(hidden(view, 0, 2)).toBe(true);
    view.dispatch({ selection: { anchor: 5 } });
    expect(hidden(view, 0, 2)).toBe(false);
  });

  it("renders everything while the editor does not have the focus", () => {
    const doc = "Uno **dos** fin";
    const bold = doc.indexOf("**");
    const view = makeView(doc, bold + 3, false);
    expect(hidden(view, bold, bold + 2)).toBe(true);
    view.dispatch({ effects: setEditorFocus.of(true) });
    expect(hidden(view, bold, bold + 2)).toBe(false);
  });

  it("does not rebuild while the cursor moves through plain text of the same line", () => {
    const doc = "Texto plano **marca** y mucho más texto plano al final de la línea";
    const view = makeView(doc, 2);
    const before = view.plugin(livePreviewPlugin).decorations;
    view.dispatch({ selection: { anchor: 4 } });
    view.dispatch({ selection: { anchor: doc.length - 3 } });
    expect(view.plugin(livePreviewPlugin).decorations).toBe(before);
    view.dispatch({ selection: { anchor: doc.indexOf("marca") } });
    expect(view.plugin(livePreviewPlugin).decorations).not.toBe(before);
  });

  it("shows a wiki-link's source only while the cursor touches it", () => {
    const doc = "Ver [[Nota]] y [[Otra]]";
    const view = makeView(doc, doc.indexOf("Otra") + 1);
    expect(widgets(view, 0, 13)).toContain("cm-live-wikilink");
    expect(widgets(view, 14, doc.length)).not.toContain("cm-live-wikilink");
  });
});

describe("code is code", () => {
  it("does not render wiki-links, tags or footnotes inside code", () => {
    const doc = "`[[Nota]] #tag [^1]`\n\n```\n[[Bloque]] #tag\n```\n\nfin";
    const view = makeView(doc, doc.length, false);
    expect(widgets(view)).toEqual([]);
  });
});

describe("Obsidian inline syntax", () => {
  it("renders footnote references as numbers in order of first use", () => {
    const doc = "A[^b] B[^a] C[^b] D^[en línea]\n\n[^a]: def a\n[^b]: def b";
    const view = makeView(doc, doc.length, false);
    const texts = [];
    view.plugin(livePreviewPlugin).decorations.between(0, doc.indexOf("\n"), (from, to, deco) => {
      if (deco.spec.widget?.className === "cm-live-footnote") texts.push(deco.spec.widget.text);
    });
    expect(texts).toEqual(["1", "2", "1", "3"]);
    expect(widgets(view, doc.indexOf("[^a]:"), doc.length)).toContain("cm-live-footnote-def");
  });

  it("dims comments, also across lines, but not inside code", () => {
    const doc = "Antes %%oculto%% y\n%%\nvarias\n%%\n`%%código%%`";
    const view = makeView(doc, 0, false);
    expect(marks(view, "cm-live-comment")).toEqual(["%%oculto%%", "%%\nvarias\n%%"]);
  });

  it("renders tags as tags, but not headings, anchors or words with a hash inside", () => {
    const doc = "Con #proyecto y #area/sub, a#b, [x](#ancla)\n\n# Título";
    const view = makeView(doc, doc.length, false);
    const tags = [];
    view.plugin(livePreviewPlugin).decorations.between(0, doc.length, (from, to, deco) => {
      if (deco.spec.widget?.className === "cm-live-tag") tags.push(deco.spec.widget.text);
    });
    expect(tags).toEqual(["#proyecto", "#area/sub"]);
  });

  it("does not take a hash and only digits as a tag, as Obsidian", () => {
    const doc = "Año #1984, #2024 y #٣٤ no; #y1984 #1984a #2024-review #año sí";
    const view = makeView(doc, doc.length, false);
    const tags = [];
    view.plugin(livePreviewPlugin).decorations.between(0, doc.length, (from, to, deco) => {
      if (deco.spec.widget?.className === "cm-live-tag") tags.push(deco.spec.widget.text);
    });
    expect(tags).toEqual(["#y1984", "#1984a", "#2024-review", "#año"]);
  });

  it("dims a block id at the end of a line", () => {
    const doc = "Un párrafo ^bloque-1\n\nprecio^2";
    const view = makeView(doc, doc.length, false);
    expect(marks(view, "cm-live-block-id")).toEqual([" ^bloque-1"]);
  });

  it("shows the language of a code block when its fences are hidden", () => {
    const doc = "```python\nprint(1)\n```\n\nfin";
    const view = makeView(doc, doc.length, false);
    const labels = [];
    view.plugin(livePreviewPlugin).decorations.between(0, 10, (from, to, deco) => {
      if (deco.spec.widget?.className === "cm-live-code-lang") labels.push(deco.spec.widget.text);
    });
    expect(labels).toEqual(["python"]);
  });

  it("shows an embedded image as the image", () => {
    window.javaEditor = { resolveImageSource: source => `file:///vault/${source}` };
    const doc = "Mira ![[foto.png]] aquí";
    const view = makeView(doc, doc.length, false);
    const dom = view.dom.querySelector(".cm-live-image img");
    expect(dom?.getAttribute("src")).toBe("file:///vault/foto.png");
    delete window.javaEditor;
  });
});

describe("focus tracking", () => {
  it("follows the focus and blur events of the editor content, not document.hasFocus()", async () => {
    const parent = document.createElement("div");
    document.body.appendChild(parent);
    const doc = "Uno **dos** fin";
    const view = new EditorView({
      state: EditorState.create({
        doc,
        selection: { anchor: doc.indexOf("dos") },
        extensions: [markdown({ extensions: [GFM, markExtension] }), blockRenderField, livePreview()]
      }),
      parent
    });
    const bold = doc.indexOf("**");
    expect(hidden(view, bold, bold + 2)).toBe(true);

    view.contentDOM.dispatchEvent(new FocusEvent("focus"));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(hidden(view, bold, bold + 2)).toBe(false);

    view.contentDOM.dispatchEvent(new FocusEvent("blur"));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(hidden(view, bold, bold + 2)).toBe(true);
    view.destroy();
  });
});
