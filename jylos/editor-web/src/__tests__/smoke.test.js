import { describe, expect, it } from "vitest";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";

describe("CodeMirror EditorView smoke test", () => {
  it("mounts in jsdom and reads back the document", () => {
    const parent = document.createElement("div");
    document.body.appendChild(parent);
    const view = new EditorView({
      state: EditorState.create({ doc: "hello" }),
      parent
    });
    expect(view.state.doc.toString()).toBe("hello");
    view.destroy();
  });
});
