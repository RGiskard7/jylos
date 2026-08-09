import { describe, expect, it } from "vitest";
import { markdown } from "@codemirror/lang-markdown";
import { syntaxTree } from "@codemirror/language";
import { GFM } from "@lezer/markdown";
import { EditorState } from "@codemirror/state";
import { markExtension } from "../editor.js";

function nodeNamesFor(doc) {
  const state = EditorState.create({
    doc,
    extensions: [markdown({ extensions: [GFM, markExtension] })]
  });
  const names = [];
  syntaxTree(state).iterate({ enter: node => names.push(node.name) });
  return names;
}

describe("==mark== inline syntax", () => {
  it("parses ==text== as a Mark node with MarkMark delimiters", () => {
    const names = nodeNamesFor("before ==highlighted== after");
    expect(names).toContain("Mark");
    expect(names.filter(name => name === "MarkMark")).toHaveLength(2);
  });

  it("does not treat a lone == inside other text as a mark", () => {
    const names = nodeNamesFor("a = b == c");
    expect(names).not.toContain("Mark");
  });

  it("does not treat === as a mark delimiter", () => {
    const names = nodeNamesFor("a === b");
    expect(names).not.toContain("Mark");
  });
});
