import { afterEach, describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markExtension } from "../editor.js";
import { blockRenderField, livePreview, setEditorFocus } from "../live-preview.js";

// Obsidian's image sizes: ![[foto.png|300]], ![[foto.png|300x100]] and ![text|200](foto.png)
// give the image its width (and height); the number is not its alternative text.

const views = [];

function makeView(doc) {
  const parent = document.createElement("div");
  document.body.appendChild(parent);
  const view = new EditorView({
    state: EditorState.create({
      doc,
      extensions: [markdown({ extensions: [GFM, markExtension] }), blockRenderField, livePreview()]
    }),
    parent
  });
  views.push(view);
  view.dispatch({ effects: setEditorFocus.of(false) });
  return view;
}

afterEach(() => {
  views.splice(0).forEach(view => view.destroy());
  document.body.innerHTML = "";
});

function images(view) {
  return [...view.contentDOM.querySelectorAll(".cm-live-image img")]
    .map(img => `${img.style.width || "-"} ${img.style.height || "-"} [${img.alt}]`);
}

describe("image sizes", () => {
  it("takes the size after | as Obsidian does", () => {
    const view = makeView("![[foto.png|300]]\n\n![[foto.png|300x100]]\n\n![texto|200](foto.png)\n\n![[foto.png|pie de foto]]\n\n![solo texto](foto.png)");

    expect(images(view)).toEqual([
      "300px - []",
      "300px 100px []",
      "200px - [texto]",
      "- - [pie de foto]",
      "- - [solo texto]"
    ]);
  });
});
