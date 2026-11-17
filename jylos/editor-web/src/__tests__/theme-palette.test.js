import { describe, expect, it, vi } from "vitest";

// An external theme's colours reach the editor; without them it keeps its own dark and light.
async function mountEditor() {
  vi.resetModules();
  delete window.__jylosEditorConfig;
  document.head.innerHTML = "";
  document.body.innerHTML = "";
  const parent = document.createElement("div");
  parent.id = "editor";
  document.body.appendChild(parent);
  await import("../editor.js");
  window.JylosEditor.initialize({ editable: true });
}

/** The style rules that apply to the editor now: those of the classes it carries. */
function editorRules() {
  const view = document.querySelector(".cm-editor");
  const classes = [...view.classList].filter((c) => !c.startsWith("cm-"));
  const text = [...document.querySelectorAll("style")].map((s) => s.textContent).join("\n");
  // ".ͼ1" must not match ".ͼ17": the class name has to end there.
  const selectors = classes.map((c) => new RegExp(`\\.${c}(?![^\\s.:,{\\[>~+])`));
  return text.split("}").filter((rule) => selectors.some((re) => re.test(rule.split("{")[0])))
    .join("}\n").toLowerCase();
}

const GRUVBOX = {
  background: "#282828", foreground: "#ebdbb2", muted: "#bdae93", panel: "#3c3836",
  surface: "#32302f", border: "#504945", selection: "#d79921", accent: "#fabd2f"
};

describe("editor colours", () => {
  it("takes an external theme's palette", async () => {
    await mountEditor();
    window.JylosEditor.setTheme({ dark: true, accent: "", palette: GRUVBOX });

    const css = editorRules();
    expect(css).toContain("#282828");
    expect(css).toContain("#ebdbb2");
    expect(css).toContain("#504945");
    expect(css).toContain("#fabd2f");
  });

  it("keeps its own colours without one, and goes back to them", async () => {
    await mountEditor();
    window.JylosEditor.setTheme({ dark: true, accent: "", palette: GRUVBOX });
    window.JylosEditor.setTheme({ dark: true, accent: "", palette: null });

    const css = editorRules();
    expect(css).toContain("#1e1e1e");
    expect(css).not.toContain("#282828");
    expect(css).not.toContain("#ebdbb2");
  });

  it("the accent chosen in Preferences wins over the theme's", async () => {
    await mountEditor();
    window.JylosEditor.setTheme({ dark: true, accent: "#ff00aa", palette: GRUVBOX });

    const css = editorRules();
    expect(css).toContain("#ff00aa");
    expect(css).not.toContain("#fabd2f");
  });

  it("headings and bold text take the theme's accent and text colours", async () => {
    await mountEditor();
    window.JylosEditor.setDocument("# Título\n\nTexto **fuerte**");
    window.JylosEditor.setTheme({ dark: true, accent: "", palette: GRUVBOX });

    expect(rulesOf(textNode("Título"))).toContain("#fabd2f");
    expect(rulesOf(textNode("fuerte"))).toContain("#ebdbb2");
  });
});

/** The innermost element holding {@code text} in the editor. */
function textNode(text) {
  const all = [...document.querySelectorAll(".cm-content *")];
  return all.filter((el) => el.textContent.includes(text)).pop();
}

/** The style rules of the classes an element and its parents up to the line carry. */
function rulesOf(element) {
  const classes = [];
  for (let el = element; el && !el.classList.contains("cm-line"); el = el.parentElement) {
    classes.push(...[...el.classList].filter((c) => !c.startsWith("cm-")));
  }
  const selectors = classes.map((c) => new RegExp(`\\.${c}(?![^\\s.:,{\\[>~+])`));
  const text = [...document.querySelectorAll("style")].map((s) => s.textContent).join("\n");
  return text.split("}").filter((rule) => selectors.some((re) => re.test(rule.split("{")[0])))
    .join("}\n").toLowerCase();
}
