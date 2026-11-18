import { describe, expect, it, vi } from "vitest";

// The [[ suggestions, through the real CodeMirror completion machinery: what is listed while
// typing, in which order, and what accepting one writes.
async function mount(notes, headings = {}) {
  vi.resetModules();
  delete window.__jylosEditorConfig;
  window.javaEditor = {
    onEditorError: vi.fn(),
    transformInsertion: text => text,
    headingsOf: link => JSON.stringify(headings[link] || [])
  };
  document.body.innerHTML = "";
  const parent = document.createElement("div");
  parent.id = "editor";
  document.body.appendChild(parent);
  await import("../editor.js");
  const { EditorView } = await import("@codemirror/view");
  const autocomplete = await import("@codemirror/autocomplete");
  window.JylosEditor.initialize({ editable: true });
  window.JylosEditor.setLinkSuggestions(notes);
  return { view: EditorView.findFromDOM(document.querySelector(".cm-editor")), autocomplete };
}

const settle = () => new Promise(resolve => setTimeout(resolve, 400));

/** Writes `before`, then types `typed` inside a [[]] after it; returns what is listed. */
async function typeLink(editor, typed, before = "") {
  const { view, autocomplete } = editor;
  const start = before.length;
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: before + "[[]]" },
    selection: { anchor: start + 2 }, userEvent: "input.type" });
  for (const ch of typed) {
    const at = view.state.selection.main.head;
    view.dispatch({ changes: { from: at, insert: ch }, selection: { anchor: at + 1 }, userEvent: "input.type" });
  }
  await settle();
  return autocomplete.currentCompletions(view.state).map(option =>
    option.detail ? `${option.label} (${option.detail})` : option.label);
}

function accept(editor, index = 0) {
  const { view, autocomplete } = editor;
  for (let i = 0; i < index; i++) {
    autocomplete.moveCompletionSelection(true)(view);
  }
  expect(autocomplete.acceptCompletion(view)).toBe(true);
  return view.state.doc.toString();
}

const note = (name, folder = "", extra = {}) => ({ name, folder, link: name, aliases: [], ...extra });

describe("wiki-link suggestions", () => {
  it("finds notes whatever the accents and case typed", async () => {
    const editor = await mount([note("Reunión"), note("Sueño"), note("Plan")]);

    expect(await typeLink(editor, "reunion")).toEqual(["Reunión"]);
    expect(await typeLink(editor, "SUEN")).toEqual(["Sueño"]);
    expect(await typeLink(editor, "reunión")).toEqual(["Reunión"]);
  });

  it("lists the names that start with what is typed first, then a word that does, then the rest", async () => {
    const editor = await mount([note("Plan de proyecto"), note("Proyectos"), note("Improvisar"),
      note("Proyecto Jylos"), note("Mi proyecto")]);

    expect(await typeLink(editor, "proy")).toEqual(["Proyectos", "Proyecto Jylos", "Mi proyecto", "Plan de proyecto"]);
    expect(await typeLink(editor, "pj")).toEqual(["Proyecto Jylos"]);
    expect(await typeLink(editor, "zzz")).toEqual([]);
  });

  it("lists every note of a name with its folder, and links the one picked unambiguously", async () => {
    const editor = await mount([
      note("Reunión", "Trabajo", { link: "Trabajo/Reunión" }),
      note("Reunión", "Personal", { link: "Personal/Reunión" }),
      note("Plan", "Trabajo")]);

    expect(await typeLink(editor, "reu")).toEqual(["Reunión (Personal)", "Reunión (Trabajo)"]);
    expect(accept(editor, 1)).toBe("[[Trabajo/Reunión]]");
    expect(await typeLink(editor, "pla")).toEqual(["Plan (Trabajo)"]);
    expect(accept(editor)).toBe("[[Plan]]");
    expect(await typeLink(editor, "personal/reu")).toEqual(["Reunión (Personal)"]);
  });

  it("offers a note's aliases, written as an alias of its link", async () => {
    const editor = await mount([note("Jylos", "", { aliases: ["Mi app de notas"] }), note("Apuntes")]);

    expect(await typeLink(editor, "app")).toEqual(["Mi app de notas (→ Jylos)"]);
    expect(accept(editor)).toBe("[[Jylos|Mi app de notas]]");
  });

  it("after # lists the headings of that note, or of this one, and writes the heading", async () => {
    const editor = await mount([note("Reunión")], { "Reunión": ["Temas", "Acuerdos", "Temas pendientes"] });

    expect(await typeLink(editor, "Reunión#")).toEqual(["Temas", "Acuerdos", "Temas pendientes"]);
    expect(await typeLink(editor, "Reunión#acu")).toEqual(["Acuerdos"]);
    expect(accept(editor)).toBe("[[Reunión#Acuerdos]]");

    const here = "# Título\n\n## Contexto\n\n```\n# no es un encabezado\n```\n\n";
    expect(await typeLink(editor, "#con", here)).toEqual(["Contexto"]);
    expect(accept(editor)).toBe(here + "[[#Contexto]]");
  });

  it("suggests nothing after | (the text the link shows is free) nor for a block (^)", async () => {
    const editor = await mount([note("Reunión")], { "Reunión": ["Temas"] });

    expect(await typeLink(editor, "Reunión|re")).toEqual([]);
    expect(await typeLink(editor, "Reunión#^ab")).toEqual([]);
  });
});
