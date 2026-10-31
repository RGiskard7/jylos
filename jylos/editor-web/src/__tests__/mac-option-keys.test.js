import { afterEach, describe, expect, it, vi } from "vitest";

// JavaFX's WebKit sends keydown events with an empty `key`, so CodeMirror names
// the key from `keyCode` as on a US keyboard. On a Spanish Mac, Option + the key
// right of P types "[" but has keyCode 192 ("`"), so it arrives as "Alt-`". If a
// binding claims that name, CodeMirror calls preventDefault() on the keydown and
// WebKit then never delivers the "[": the symbol cannot be typed.
const realPlatform = Object.getOwnPropertyDescriptor(Navigator.prototype, "platform");

async function mountEditorOn(platform) {
  Object.defineProperty(navigator, "platform", { value: platform, configurable: true });
  vi.resetModules();
  delete window.__jylosEditorConfig;
  window.javaEditor = { onEditorError: vi.fn() };
  document.body.innerHTML = "";
  const parent = document.createElement("div");
  parent.id = "editor";
  document.body.appendChild(parent);
  await import("../editor.js");
  window.JylosEditor.initialize({ editable: true });
  return document.querySelector(".cm-content");
}

// What JavaFX's WebKit dispatches: no `key`, no `code`, only the legacy keyCode.
function javafxKeydown(target, keyCode, modifiers) {
  const event = new KeyboardEvent("keydown", { key: "", bubbles: true, cancelable: true, ...modifiers });
  Object.defineProperty(event, "keyCode", { value: keyCode });
  target.dispatchEvent(event);
  return event;
}

describe("Option+key on macOS types characters", () => {
  afterEach(() => {
    delete navigator.platform;
    if (realPlatform) Object.defineProperty(Navigator.prototype, "platform", realPlatform);
  });

  it.each([
    ["Option+` (Spanish \"[\")", 192, { altKey: true }],
    ["Option+I", 73, { altKey: true }],
    ["Option+Shift+A", 65, { altKey: true, shiftKey: true }],
    ["Option+Shift+M", 77, { altKey: true, shiftKey: true }]
  ])("%s is left to the keyboard layout", async (_name, keyCode, modifiers) => {
    const content = await mountEditorOn("MacIntel");
    expect(javafxKeydown(content, keyCode, modifiers).defaultPrevented).toBe(false);
  });

  it("Ctrl+Space still opens the completion list", async () => {
    const content = await mountEditorOn("MacIntel");
    expect(javafxKeydown(content, 32, { ctrlKey: true }).defaultPrevented).toBe(true);
  });

  it("Cmd+Z is still handled by the editor", async () => {
    const content = await mountEditorOn("MacIntel");
    expect(javafxKeydown(content, 90, { metaKey: true }).defaultPrevented).toBe(true);
  });
});
