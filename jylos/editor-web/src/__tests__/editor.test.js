import { beforeEach, describe, expect, it, vi } from "vitest";

// editor.js keeps its EditorView in module-level state and attaches
// window.JylosEditor as an import side effect (matching the WebView runtime,
// which only ever loads one document per page). Each test re-imports the
// module fresh via resetModules() so `view` doesn't leak across cases.
async function mountEditor() {
  vi.resetModules();
  delete window.__jylosEditorConfig;
  document.body.innerHTML = "";
  const parent = document.createElement("div");
  parent.id = "editor";
  document.body.appendChild(parent);
  await import("../editor.js");
  window.JylosEditor.initialize({ editable: true });
}

describe("window.JylosEditor document/history isolation", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("starts a fresh, undo-less history when switching documents", async () => {
    await mountEditor();
    window.JylosEditor.setDocument("first note content");
    window.JylosEditor.replaceSelection("more text");
    expect(window.JylosEditor.canUndo()).toBe(true);

    // Simulate opening a different note: undo must not be able to reach
    // back into the previous document's edit history.
    window.JylosEditor.setDocument("second note content");
    expect(window.JylosEditor.canUndo()).toBe(false);
    expect(window.JylosEditor.getSelectedText()).toBe("");
  });

  it("keeps the requested document text after setDocument", async () => {
    await mountEditor();
    window.JylosEditor.setDocument("hello world");
    window.JylosEditor.selectAll();
    expect(window.JylosEditor.getSelectedText()).toBe("hello world");
  });

  it("openReplace focuses the replace field, not just the search field", async () => {
    await mountEditor();
    window.JylosEditor.setDocument("hello world");

    expect(window.JylosEditor.openReplace()).toBe(true);

    expect(document.activeElement).not.toBeNull();
    expect(document.activeElement.name).toBe("replace");
  });
});

describe("emoji decoration", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
    window.javaEditor = { rasterizeEmoji: vi.fn(() => "data:image/png;base64,AAAA"), onEditorError: vi.fn() };
  });

  it("replaces an emoji outside the selection with an <img> from the Java bridge", async () => {
    await mountEditor();
    window.JylosEditor.setDocument("hello world");
    window.JylosEditor.replaceSelection(" 😀");

    const img = document.querySelector(".cm-emoji");
    expect(img).not.toBeNull();
    expect(img.tagName).toBe("IMG");
    expect(img.alt).toBe("😀");
    expect(img.src).toBe("data:image/png;base64,AAAA");
    expect(window.javaEditor.rasterizeEmoji).toHaveBeenCalledWith("😀");
  });

  it("keeps the raw character editable while the caret is inside the run", async () => {
    await mountEditor();
    window.JylosEditor.setDocument("hi 😀 there");
    // Place the caret inside the emoji run (offset 3-5).
    window.JylosEditor.replaceRange(3, 3, "", 4);

    expect(document.querySelector(".cm-emoji")).toBeNull();
    expect(window.JylosEditor.getSelectionFrom()).toBe(4);
  });

  it("falls back to plain text when the bridge has no image", async () => {
    window.javaEditor = { rasterizeEmoji: vi.fn(() => ""), onEditorError: vi.fn() };
    await mountEditor();
    window.JylosEditor.setDocument("plain text");
    window.JylosEditor.replaceSelection(" 😀");

    expect(document.querySelector(".cm-emoji")).toBeNull();
    expect(document.getElementById("editor").textContent).toContain("😀");
  });
});
