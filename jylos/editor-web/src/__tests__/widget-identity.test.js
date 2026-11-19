import { afterEach, describe, expect, it, vi } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import {
  blockRenderDecorations,
  blockRenderField,
  imageConverted,
  livePreview,
  setBlockRenders,
  setEditorFocus
} from "../live-preview.js";

// Typing must not redraw the rendered elements around the cursor: a widget whose content did
// not change keeps its DOM (no KaTeX redraw, no image reload, no jump while it loads again),
// and still acts on where it is now in the document.

/** The views a test made, destroyed after it: a live one would measure itself later, outside it. */
const views = [];

function makeView(doc, anchor = 0, focused = false) {
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
  views.push(view);
  view.dispatch({ effects: setEditorFocus.of(focused) });
  return view;
}

function mousedown(element, init = {}) {
  element.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, cancelable: true, button: 0, ...init }));
}

afterEach(() => {
  views.splice(0).forEach(view => view.destroy());
  document.body.innerHTML = "";
  delete window.javaEditor;
  delete window.katex;
});

describe("widgets survive typing elsewhere", () => {
  it("keeps the DOM of every rendered element when text is typed before it", () => {
    window.katex = { renderToString: tex => `<span class="katex">${tex}</span>` };
    const doc = "Hola #proyecto, [[Nota]], $a^2$, ![foto](x.png) y nota[^1] fin\n\n[^1]: def";
    const view = makeView(doc);
    const selectors = [".cm-live-tag", ".cm-live-wikilink", ".cm-live-math", ".cm-live-image", ".cm-live-footnote"];
    const before = selectors.map(selector => view.contentDOM.querySelector(selector));
    before.forEach((element, index) => expect(element, selectors[index]).not.toBeNull());

    view.dispatch({ changes: { from: 0, insert: "Ya " } });

    const after = selectors.map(selector => view.contentDOM.querySelector(selector));
    after.forEach((element, index) => expect(element, selectors[index]).toBe(before[index]));
  });

  it("keeps a host-rendered block when text is typed before it", () => {
    const doc = "Intro\n\n```mermaid\ngraph\n```\n\nfin";
    const view = makeView(doc);
    view.dispatch({ effects: setBlockRenders.of({ "mermaid\ngraph": "<p>dibujo</p>" }) });
    const block = view.contentDOM.querySelector(".cm-live-block-render-outer");
    expect(block).not.toBeNull();

    view.dispatch({ changes: { from: 0, insert: "Más " } });

    expect(view.contentDOM.querySelector(".cm-live-block-render-outer")).toBe(block);
  });

  it("keeps the rows of a table when text is typed before it", () => {
    const view = makeView("Intro\n\n| a | b |\n|---|---|\n| 1 | 2 |\n");
    const rows = [...view.contentDOM.querySelectorAll(".cm-live-table-row")];
    expect(rows.length).toBe(2);

    view.dispatch({ changes: { from: 0, insert: "Más " } });

    expect([...view.contentDOM.querySelectorAll(".cm-live-table-row")]).toEqual(rows);
    expect(view.contentDOM.querySelectorAll(".cm-live-table-row")[1]).toBe(rows[1]);
  });

  it("a kept widget puts the cursor where its text is now, not where it was first drawn", () => {
    const view = makeView("Hola #proyecto fin");
    const tag = view.contentDOM.querySelector(".cm-live-tag");
    view.dispatch({ changes: { from: 0, insert: "Ya " } });

    mousedown(view.contentDOM.querySelector(".cm-live-tag"));

    expect(view.contentDOM.querySelector(".cm-live-tag") ?? tag).toBeTruthy();
    expect(view.state.selection.main.head).toBe(view.state.doc.toString().indexOf("#proyecto"));
  });

  it("a link whose target changes opens the new target", () => {
    const openNote = vi.fn();
    window.javaEditor = { openNote };
    const view = makeView("Ver [[Alfa|aquí]] fin");
    const start = view.state.doc.toString().indexOf("Alfa");
    view.dispatch({ changes: { from: start, to: start + 4, insert: "Beta" } });

    mousedown(view.contentDOM.querySelector(".cm-live-wikilink"), { metaKey: true });

    expect(openNote).toHaveBeenCalledTimes(1);
    expect(openNote).toHaveBeenCalledWith("Beta");
  });

  it("a kept task checkbox toggles its own task after text is typed before it", () => {
    const view = makeView("- [ ] tarea");
    const checkbox = view.contentDOM.querySelector(".cm-live-task");
    view.dispatch({ changes: { from: 0, insert: "Intro\n\n" } });
    const kept = view.contentDOM.querySelector(".cm-live-task");
    expect(kept).toBe(checkbox);

    kept.checked = true;
    kept.dispatchEvent(new Event("change", { bubbles: true }));

    expect(view.state.doc.toString()).toBe("Intro\n\n- [x] tarea");
  });
});

describe("block decorations follow only what the cursor reveals", () => {
  it("moving the cursor through plain text keeps the block decorations as they are", () => {
    const doc = "Texto uno dos\n\n```mermaid\ngraph\n```\n\nfin";
    const view = makeView(doc, 0, true);
    view.dispatch({ effects: setBlockRenders.of({ "mermaid\ngraph": "<p>dibujo</p>" }) });
    const first = view.state.field(blockRenderDecorations);
    expect(first.size).toBe(1);

    view.dispatch({ selection: { anchor: 5 } });
    expect(view.state.field(blockRenderDecorations)).toBe(first);

    // Into the block: it shows its source, so the decorations do change.
    view.dispatch({ selection: { anchor: doc.indexOf("graph") } });
    expect(view.state.field(blockRenderDecorations).size).toBe(0);
  });

  it("typing outside the blocks keeps their decorations, shifted, instead of making new ones", () => {
    const doc = "Texto uno\n\n```mermaid\ngraph\n```\n\nfin";
    const view = makeView(doc, 0, true);
    view.dispatch({ effects: setBlockRenders.of({ "mermaid\ngraph": "<p>dibujo</p>" }) });
    const widgetOf = () => {
      const cursor = view.state.field(blockRenderDecorations).iter();
      return { widget: cursor.value?.spec.widget, from: cursor.from };
    };
    const before = widgetOf();

    view.dispatch({ changes: { from: 0, insert: "Más " }, selection: { anchor: 4 } });

    const after = widgetOf();
    expect(after.widget).toBe(before.widget);
    expect(after.from).toBe(before.from + 4);
  });
});

describe("images keep their room while they load again", () => {
  it("an image drawn again reserves the height it had, until it has loaded", () => {
    const realRect = Element.prototype.getBoundingClientRect;
    Element.prototype.getBoundingClientRect = function () {
      const height = this.classList?.contains("cm-live-image") ? 312 : 0;
      return { top: 0, left: 0, right: 0, bottom: height, width: 0, height, x: 0, y: 0 };
    };
    try {
      const first = makeView("![captura](foto-reservada.png)");
      const image = first.contentDOM.querySelector(".cm-live-image img");
      image.dispatchEvent(new Event("load"));
      first.destroy();

      // Scrolled out and back (or the note opened again): a new DOM for the same image.
      const again = makeView("![captura](foto-reservada.png)");
      const figure = again.contentDOM.querySelector(".cm-live-image");
      expect(figure.style.minHeight).toBe("312px");

      figure.querySelector("img").dispatchEvent(new Event("load"));
      expect(figure.style.minHeight).toBe("");
    } finally {
      Element.prototype.getBoundingClientRect = realRect;
    }
  });

  it("an image never drawn reserves nothing", () => {
    const view = makeView("![otra](nunca-vista.png)");
    expect(view.contentDOM.querySelector(".cm-live-image").style.minHeight).toBe("");
  });
});

describe("an image that loads shows only the image", () => {
  it("hides the file name shown when an image cannot load", () => {
    const view = makeView("![captura](foto.png)");
    const fallback = view.contentDOM.querySelector(".cm-live-image-fallback");
    expect(fallback.hidden).toBe(true);
    expect(getComputedStyle(fallback).display).toBe("none");

    view.contentDOM.querySelector(".cm-live-image img").dispatchEvent(new Event("error"));
    expect(getComputedStyle(fallback).display).not.toBe("none");
  });
});

describe("an image the WebView cannot draw is converted by the host", () => {
  // JavaFX's WebKit cannot decode WebP, which many pages serve even under a .png name. The
  // host is asked once for a PNG copy; the file name shows only if that fails too.
  function failingImage(convertImage) {
    window.javaEditor = { resolveImageSource: source => source, convertImage };
    const view = makeView("![](https://cdn.example/foto.png)");
    view.contentDOM.querySelector(".cm-live-image img").dispatchEvent(new Event("error"));
    return view;
  }

  function drawAgain(view) {
    const doc = view.state.doc.toString();
    view.dispatch({ changes: { from: 0, to: doc.length, insert: "" } });
    view.dispatch({ changes: { from: 0, insert: doc } });
  }

  it("shows the PNG the host makes of it, not the file name", () => {
    const convertImage = vi.fn();
    const view = failingImage(convertImage);
    expect(convertImage).toHaveBeenCalledTimes(1);
    const [id, source] = convertImage.mock.calls[0];
    expect(source).toBe("https://cdn.example/foto.png");
    const fallback = view.contentDOM.querySelector(".cm-live-image-fallback");
    expect(fallback.hidden, "no file name while it is converted").toBe(true);

    imageConverted(id, "data:image/png;base64,QUJD");

    const image = view.contentDOM.querySelector(".cm-live-image img");
    expect(image.getAttribute("src")).toBe("data:image/png;base64,QUJD");
    expect(image.hidden).toBe(false);
    expect(fallback.hidden).toBe(true);
  });

  it("drawn again, it uses the PNG and the host is not asked again", () => {
    const convertImage = vi.fn();
    const view = failingImage(convertImage);
    imageConverted(convertImage.mock.calls[0][0], "data:image/png;base64,QUJD");

    drawAgain(view);

    expect(view.contentDOM.querySelector(".cm-live-image img").getAttribute("src")).toBe("data:image/png;base64,QUJD");
    expect(convertImage).toHaveBeenCalledTimes(1);
  });

  it("shows the file name when the host cannot convert it either, and does not ask again", () => {
    const convertImage = vi.fn();
    const view = failingImage(convertImage);
    imageConverted(convertImage.mock.calls[0][0], "");

    const fallback = view.contentDOM.querySelector(".cm-live-image-fallback");
    expect(getComputedStyle(fallback).display).not.toBe("none");

    drawAgain(view);
    view.contentDOM.querySelector(".cm-live-image img").dispatchEvent(new Event("error"));
    expect(getComputedStyle(view.contentDOM.querySelector(".cm-live-image-fallback")).display).not.toBe("none");
    expect(convertImage).toHaveBeenCalledTimes(1);
  });
});

describe("work the host is not asked to repeat", () => {
  it("block renders equal to the ones shown change nothing", () => {
    const view = makeView("Intro\n\n```mermaid\ngraph\n```\n\nfin");
    view.dispatch({ effects: setBlockRenders.of({ "mermaid\ngraph": "<p>dibujo</p>" }) });
    const field = view.state.field(blockRenderField);

    // The host sends them again after every pause in typing, as a new object.
    view.dispatch({ effects: setBlockRenders.of({ "mermaid\ngraph": "<p>dibujo</p>" }) });
    expect(view.state.field(blockRenderField)).toBe(field);

    view.dispatch({ effects: setBlockRenders.of({ "mermaid\ngraph": "<p>otro</p>" }) });
    expect(view.state.field(blockRenderField)["mermaid\ngraph"]).toBe("<p>otro</p>");
  });

  it("asks the host for an image once per note, however often it is drawn", () => {
    const resolveImageSource = vi.fn(source => `data:image/png;base64,${source}`);
    window.javaEditor = { resolveImageSource };
    const doc = "![a](foto.png)\n\n![b](foto.png)\n\n![c](otra.png)";
    const view = makeView(doc);
    expect(view.contentDOM.querySelectorAll(".cm-live-image img").length).toBe(3);
    expect(resolveImageSource.mock.calls.map(call => call[0]).sort()).toEqual(["foto.png", "otra.png"]);

    // Drawn again (scrolled out and back): the same image is not read again.
    view.dispatch({ changes: { from: 0, to: doc.length, insert: "" } });
    view.dispatch({ changes: { from: 0, insert: doc } });
    expect(view.contentDOM.querySelectorAll(".cm-live-image img").length).toBe(3);
    expect(resolveImageSource).toHaveBeenCalledTimes(2);

    // Another note: the same name may be another file, so it is asked again.
    view.setState(EditorState.create({
      doc,
      extensions: [markdown({ extensions: [GFM, markExtension] }), blockRenderField, livePreview()]
    }));
    expect(resolveImageSource).toHaveBeenCalledTimes(4);
  });
});
