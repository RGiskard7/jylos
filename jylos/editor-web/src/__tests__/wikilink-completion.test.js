import { beforeEach, describe, expect, it, vi } from "vitest";

// Drives the real CodeMirror completion machinery (not the source function in
// isolation): CodeMirror filters the source's options against the text between
// the result's `from` and the caret, so a source can return the right titles and
// still show nothing.
async function mountEditorWithTitles(titles) {
  vi.resetModules();
  delete window.__jylosEditorConfig;
  window.javaEditor = { onEditorError: vi.fn(), transformInsertion: text => text };
  document.body.innerHTML = "";
  const parent = document.createElement("div");
  parent.id = "editor";
  document.body.appendChild(parent);
  await import("../editor.js");
  // Imported after editor.js, inside the same reset module registry, so these are
  // the instances the editor itself uses.
  const { EditorView } = await import("@codemirror/view");
  const autocomplete = await import("@codemirror/autocomplete");
  window.JylosEditor.initialize({ editable: true });
  window.JylosEditor.setAutocompleteTitles(titles);
  const view = EditorView.findFromDOM(document.querySelector(".cm-editor"));
  return { view, autocomplete };
}

function typeText(view, text) {
  const at = view.state.selection.main.head;
  view.dispatch({ changes: { from: at, insert: text }, selection: { anchor: at + text.length }, userEvent: "input.type" });
}

const settle = () => new Promise(resolve => setTimeout(resolve, 400));

describe("wiki-link autocomplete", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("lists note titles right after typing [[ (with the auto-inserted ]])", async () => {
    const { view, autocomplete } = await mountEditorWithTitles(["Alpha", "Beta"]);
    view.dispatch({ changes: { from: 0, insert: "[[]]" }, selection: { anchor: 2 }, userEvent: "input.type" });
    await settle();

    expect(autocomplete.completionStatus(view.state)).toBe("active");
    expect(autocomplete.currentCompletions(view.state).map(option => option.label)).toEqual(["Alpha", "Beta"]);
  });

  it("narrows the list as the title is typed", async () => {
    const { view, autocomplete } = await mountEditorWithTitles(["Alpha", "Beta", "Alphabet soup"]);
    view.dispatch({ changes: { from: 0, insert: "[[]]" }, selection: { anchor: 2 }, userEvent: "input.type" });
    typeText(view, "alp");
    await settle();

    const labels = autocomplete.currentCompletions(view.state).map(option => option.label);
    expect(labels).toContain("Alpha");
    expect(labels).toContain("Alphabet soup");
    expect(labels).not.toContain("Beta");
  });

  it("offers nothing outside a wiki-link", async () => {
    const { view, autocomplete } = await mountEditorWithTitles(["Alpha"]);
    typeText(view, "alp");
    await settle();

    expect(autocomplete.currentCompletions(view.state)).toEqual([]);
  });

  it("accepting replaces the whole link once, without doubling the closing ]]", async () => {
    const { view, autocomplete } = await mountEditorWithTitles(["Alpha"]);
    view.dispatch({ changes: { from: 0, insert: "see [[]] here" }, selection: { anchor: 6 }, userEvent: "input.type" });
    typeText(view, "al");
    await settle();

    expect(autocomplete.acceptCompletion(view)).toBe(true);
    expect(view.state.doc.toString()).toBe("see [[Alpha]] here");
    expect(view.state.selection.main.head).toBe("see [[Alpha]]".length);
  });

  it("Enter accepts the highlighted title, even in a list item where Markdown also binds Enter", async () => {
    const { view } = await mountEditorWithTitles(["Alpha"]);
    view.dispatch({ changes: { from: 0, insert: "- [[]]" }, selection: { anchor: 4 }, userEvent: "input.type" });
    typeText(view, "al");
    await settle();

    // As JavaFX's WebKit sends it: empty `key`, legacy keyCode only.
    const enter = new KeyboardEvent("keydown", { key: "", bubbles: true, cancelable: true });
    Object.defineProperty(enter, "keyCode", { value: 13 });
    view.contentDOM.dispatchEvent(enter);

    expect(view.state.doc.toString()).toBe("- [[Alpha]]");
  });

  it("accepting without an auto-inserted ]] still closes the link", async () => {
    const { view, autocomplete } = await mountEditorWithTitles(["Alpha"]);
    typeText(view, "[[al");
    await settle();

    expect(autocomplete.acceptCompletion(view)).toBe(true);
    expect(view.state.doc.toString()).toBe("[[Alpha]]");
  });
});
