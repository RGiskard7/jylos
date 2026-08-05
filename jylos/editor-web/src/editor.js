import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from "@codemirror/autocomplete";
import { defaultKeymap, history, historyKeymap, indentWithTab, redo, redoDepth, undo, undoDepth } from "@codemirror/commands";
import { bracketMatching, defaultHighlightStyle, HighlightStyle, indentOnInput, syntaxHighlighting, syntaxTree } from "@codemirror/language";
import { languages } from "@codemirror/language-data";
import { markdown } from "@codemirror/lang-markdown";
import { highlightSelectionMatches, openSearchPanel, search, searchKeymap } from "@codemirror/search";
import { Compartment, EditorSelection, EditorState } from "@codemirror/state";
import {
  Decoration,
  dropCursor,
  EditorView,
  highlightActiveLine,
  keymap,
  MatchDecorator,
  ViewPlugin
} from "@codemirror/view";
import { Tag, tags } from "@lezer/highlight";
import { GFM } from "@lezer/markdown";
import { livePreview } from "./live-preview.js";

// `==mark==` highlight syntax is a common CommonMark extension (Obsidian,
// Typora) but isn't part of GFM, so @lezer/markdown has no built-in node for
// it. This inline parser mirrors @lezer/markdown's own Strikethrough
// extension (same delimiter-pair mechanism, `==` instead of `~~`).
const markContentTag = Tag.define();
const markPunctuation = /[!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~\xA1‐-‧]/;
const markDelimiter = { resolve: "Mark", mark: "MarkMark" };
const markExtension = {
  defineNodes: [
    { name: "Mark", style: { "Mark/...": markContentTag } },
    { name: "MarkMark", style: tags.processingInstruction }
  ],
  parseInline: [{
    name: "Mark",
    parse(cx, next, pos) {
      if (next !== 61 /* '=' */ || cx.char(pos + 1) !== 61 || cx.char(pos + 2) === 61) return -1;
      const before = cx.slice(pos - 1, pos);
      const after = cx.slice(pos + 2, pos + 3);
      const spaceBefore = /\s|^$/.test(before);
      const spaceAfter = /\s|^$/.test(after);
      const punctuationBefore = markPunctuation.test(before);
      const punctuationAfter = markPunctuation.test(after);
      return cx.addDelimiter(
        markDelimiter,
        pos,
        pos + 2,
        !spaceAfter && (!punctuationAfter || spaceBefore || punctuationBefore),
        !spaceBefore && (!punctuationBefore || spaceAfter || punctuationAfter)
      );
    },
    after: "Emphasis"
  }]
};

let view;
let autocompleteTitles = [];
let suppressChangeNotification = false;
const themeCompartment = new Compartment();
const fontCompartment = new Compartment();
const phraseCompartment = new Compartment();
const highlightCompartment = new Compartment();
const presentationCompartment = new Compartment();
const editableCompartment = new Compartment();

function editorHighlight(config) {
  const dark = Boolean(config?.dark);
  const accent = config?.accent || (dark ? "#7da6ff" : "#315fbd");
  const foreground = dark ? "#e6e6e6" : "#23252a";
  const muted = dark ? "#9ca3af" : "#667085";
  const code = dark ? "#d7ba7d" : "#8a4b08";
  const string = dark ? "#a8cc8c" : "#347a37";
  const keyword = dark ? "#c792ea" : "#7c3db5";
  const number = dark ? "#f78c6c" : "#a33b20";

  return HighlightStyle.define([
    { tag: tags.heading1, color: accent, fontSize: "1.35em", fontWeight: "750" },
    { tag: tags.heading2, color: accent, fontSize: "1.22em", fontWeight: "725" },
    { tag: tags.heading3, color: accent, fontSize: "1.12em", fontWeight: "700" },
    { tag: tags.heading, color: accent, fontWeight: "700" },
    { tag: tags.strong, color: foreground, fontWeight: "750" },
    { tag: tags.emphasis, fontStyle: "italic" },
    { tag: tags.strikethrough, textDecoration: "line-through" },
    { tag: markContentTag, backgroundColor: dark ? "#5a4a1f" : "#fff3b0", color: foreground },
    { tag: [tags.link, tags.url], color: accent, textDecoration: "underline" },
    { tag: tags.monospace, color: code, fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace" },
    { tag: tags.meta, color: muted },
    { tag: tags.quote, color: muted, fontStyle: "italic" },
    { tag: [tags.keyword, tags.typeName, tags.className, tags.definitionKeyword], color: keyword },
    { tag: [tags.string, tags.regexp], color: string },
    { tag: [tags.number, tags.bool, tags.null], color: number },
    { tag: [tags.comment, tags.docComment], color: muted, fontStyle: "italic" },
    { tag: tags.invalid, color: dark ? "#ff6b6b" : "#b42318", textDecoration: "underline wavy" }
  ]);
}

const wikiLinkMatcher = new MatchDecorator({
  regexp: /!?(?:\[\[[^\]\n]+\]\])/g,
  decoration: match => Decoration.mark({ class: match[0].startsWith("!") ? "cm-embed" : "cm-wikilink" })
});

const wikiLinkDecorations = ViewPlugin.fromClass(class {
  constructor(editorView) {
    this.decorations = wikiLinkMatcher.createDeco(editorView);
  }

  update(update) {
    this.decorations = wikiLinkMatcher.updateDeco(update, this.decorations);
  }
}, { decorations: value => value.decorations });

function wikiLinkCompletion(context) {
  const before = context.matchBefore(/\[\[[^\]\[\n]*$/);
  if (!before) return null;
  const query = before.text.slice(2).toLocaleLowerCase();
  const options = autocompleteTitles
    .filter(title => !query || title.toLocaleLowerCase().includes(query))
    .slice(0, 50)
    .map(title => ({
      label: title,
      type: "text",
      apply(editorView, _completion, from, to) {
        if (editorView.state.readOnly) return;
        const raw = `[[${title}]]`;
        const inserted = window.javaEditor?.transformInsertion(raw) || raw;
        editorView.dispatch({
          changes: { from, to, insert: inserted },
          selection: { anchor: from + inserted.length },
          userEvent: "input.complete"
        });
      }
    }));
  return { from: before.from, options, validFor: /^\[\[[^\]\[\n]*$/ };
}

function editorTheme(config) {
  const dark = Boolean(config.dark);
  const accent = config.accent || (dark ? "#7da6ff" : "#315fbd");
  const background = dark ? "#1e1e1e" : "#ffffff";
  const foreground = dark ? "#e6e6e6" : "#23252a";
  const muted = dark ? "#9ca3af" : "#667085";
  const panel = dark ? "#292929" : "#f5f6f8";
  const border = dark ? "#3b3b3b" : "#d8dce3";
  const selection = dark ? "#35548a" : "#b9d2ff";

  return EditorView.theme({
    "&": {
      height: "100%",
      color: foreground,
      backgroundColor: background,
      "--jylos-accent": accent,
      "--jylos-muted": muted,
      "--jylos-panel": panel,
      "--jylos-border": border,
      "--jylos-code-bg": dark ? "#252525" : "#f4f5f7"
    },
    ".cm-scroller": {
      overflow: "auto",
      fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
      lineHeight: "1.55"
    },
    ".cm-content": { minHeight: "100%", padding: "16px 18px", caretColor: foreground },
    ".cm-line": { padding: "0" },
    ".cm-dropCursor": { borderLeftColor: foreground },
    "::selection": { backgroundColor: selection },
    ".cm-activeLine": { backgroundColor: dark ? "#242424" : "#f7f9fc" },
    ".cm-panels": { color: foreground, backgroundColor: panel, borderColor: border },
    ".cm-panels.cm-panels-top": { borderBottom: `1px solid ${border}` },
    ".cm-search label": { color: foreground },
    ".cm-textfield": { color: foreground, backgroundColor: background, border: `1px solid ${border}` },
    ".cm-button": { color: foreground, background: panel, border: `1px solid ${border}` },
    ".cm-tooltip": { color: foreground, backgroundColor: panel, border: `1px solid ${border}` },
    ".cm-tooltip-autocomplete > ul > li[aria-selected]": { color: foreground, backgroundColor: selection },
    ".cm-wikilink, .cm-embed": { color: accent, fontWeight: "600" },
    ".cm-embed": { textDecoration: "underline" },
    ".tok-link, .tok-url": { color: accent },
    ".tok-meta": { color: muted },
    ".tok-string": { color: dark ? "#a8cc8c" : "#347a37" },
    ".tok-keyword, .tok-typeName": { color: dark ? "#c792ea" : "#7c3db5" },
    ".tok-comment": { color: muted, fontStyle: "italic" }
  }, { dark });
}

function fontTheme(size) {
  const safe = Math.max(10, Math.min(36, Number(size) || 14));
  return EditorView.theme({ ".cm-scroller": { fontSize: `${safe}px` } });
}

function notifyDocumentChanged(update) {
  if (!update.docChanged || suppressChangeNotification) return;
  window.javaEditor?.onDocumentChanged(update.state.doc.toString());
}

function selectedRange() {
  return view.state.selection.main;
}

function copySelection() {
  const range = selectedRange();
  if (range.empty) return false;
  window.javaEditor?.copyToClipboard(view.state.sliceDoc(range.from, range.to));
  return true;
}

function cutSelection() {
  if (!view || view.state.readOnly) return false;
  if (!copySelection()) return false;
  const range = selectedRange();
  view.dispatch({ changes: { from: range.from, to: range.to }, selection: { anchor: range.from }, userEvent: "delete.cut" });
  return true;
}

function pasteClipboard() {
  if (!view || view.state.readOnly) return false;
  const text = window.javaEditor?.readClipboard();
  if (typeof text !== "string") return false;
  view.dispatch(view.state.replaceSelection(text));
  return true;
}

function selectAll() {
  view.dispatch({ selection: EditorSelection.single(0, view.state.doc.length), userEvent: "select" });
  return true;
}

function sourceLinkNavigation() {
  return EditorView.domEventHandlers({
    click(event, editorView) {
      if (window.__jylosEditorConfig?.livePreview || !(event.metaKey || event.ctrlKey)) return false;
      const position = editorView.posAtCoords({ x: event.clientX, y: event.clientY });
      if (position == null) return false;

      const line = editorView.state.doc.lineAt(position);
      for (const match of line.text.matchAll(/!?\[\[([^\]\n]+)\]\]/g)) {
        const from = line.from + match.index;
        const to = from + match[0].length;
        if (position >= from && position <= to) {
          window.javaEditor?.openNote(match[1].split("|", 1)[0].split("#", 1)[0].trim());
          event.preventDefault();
          return true;
        }
      }

      let node = syntaxTree(editorView.state).resolveInner(position, 1);
      while (node && !["Link", "Autolink"].includes(node.name)) node = node.parent;
      if (!node) return false;
      const urlNode = directChild(node, "URL");
      const target = urlNode
        ? editorView.state.sliceDoc(urlNode.from, urlNode.to).trim()
        : editorView.state.sliceDoc(node.from, node.to).replace(/^<|>$/g, "");
      window.javaEditor?.openMarkdownLink(target);
      event.preventDefault();
      return true;
    }
  });
}

function directChild(node, name) {
  for (let child = node.firstChild; child; child = child.nextSibling) {
    if (child.name === name) return child;
  }
  return null;
}

function editableExtensions(editable) {
  return [EditorState.readOnly.of(!editable), EditorView.editable.of(editable)];
}

function createState(doc, config) {
  const editable = Boolean(config?.editable);
  return EditorState.create({
    doc: doc || "",
    extensions: [
    history({ newGroupDelay: 500 }),
    // Deliberately NOT using drawSelection(): it replaces native text
    // selection with a JS-measured overlay that (a) fights our own
    // `::selection` theme rule — CodeMirror injects a Prec.highest
    // `::selection { background: transparent }` override that our CSS was
    // conflicting with, producing double/mismatched selection rendering —
    // and (b) recomputes overlay rectangles via layout measurement on every
    // scroll tick, which is expensive inside a WebView. Native selection
    // (the browser's own ::selection rendering) is what "select like any
    // other editor" means in practice: double-click word, drag, triple-click
    // line, all handled by WebKit itself with no per-frame JS cost.
    dropCursor(),
    // Alt-drag rectangular selection is a code-editor feature with little
    // value in a note-taking app, and JavaFX WebView has a known modifier-key
    // desync with WebKit that made it a suspect for broken plain drag-select.
    indentOnInput(),
    bracketMatching(),
    closeBrackets(),
    search({ top: true }),
    highlightSelectionMatches(),
    highlightActiveLine(),
    markdown({ extensions: [GFM, markExtension], codeLanguages: languages }),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    highlightCompartment.of(syntaxHighlighting(editorHighlight(config))),
    presentationCompartment.of(config?.livePreview ? livePreview() : []),
    editableCompartment.of(editableExtensions(editable)),
    wikiLinkDecorations,
    autocompletion({ override: [wikiLinkCompletion], activateOnTyping: true }),
    sourceLinkNavigation(),
    keymap.of([indentWithTab, ...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, ...completionKeymap]),
    EditorView.lineWrapping,
    EditorView.updateListener.of(notifyDocumentChanged),
    EditorView.exceptionSink.of(error => window.javaEditor?.onEditorError(String(error?.stack || error))),
    themeCompartment.of(editorTheme(config || {})),
    fontCompartment.of(fontTheme(config?.fontSize)),
    phraseCompartment.of(EditorState.phrases.of(config?.labels || {}))
    ]
  });
}

function initialize(config) {
  if (view) return;
  window.__jylosEditorConfig = config || {};
  view = new EditorView({
    state: createState("", window.__jylosEditorConfig),
    parent: document.getElementById("editor")
  });
}

function setDocument(text) {
  if (!view) return;
  suppressChangeNotification = true;
  // A fresh state is the canonical CodeMirror boundary between documents and
  // guarantees that undo cannot cross from one note into another.
  view.setState(createState(text || "", window.__jylosEditorConfig || {}));
  suppressChangeNotification = false;
}

function replaceDocument(text) {
  if (!view) return;
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: text || "" },
    userEvent: "input"
  });
}

function replaceSelection(text, cursorOffset = -1) {
  if (!view || view.state.readOnly) return;
  const range = selectedRange();
  const insert = text || "";
  const anchor = cursorOffset >= 0 ? range.from + cursorOffset : range.from + insert.length;
  view.dispatch({ changes: { from: range.from, to: range.to, insert }, selection: { anchor }, userEvent: "input" });
  view.focus();
}

function replaceRange(from, to, text, anchor = -1) {
  if (!view || view.state.readOnly) return;
  const insert = text || "";
  const safeFrom = Math.max(0, Math.min(Number(from) || 0, view.state.doc.length));
  const safeTo = Math.max(safeFrom, Math.min(Number(to) || safeFrom, view.state.doc.length));
  const target = anchor >= 0 ? anchor : safeFrom + insert.length;
  view.dispatch({ changes: { from: safeFrom, to: safeTo, insert }, selection: { anchor: target }, userEvent: "input" });
  view.focus();
}

window.JylosEditor = {
  initialize,
  setDocument,
  replaceDocument,
  getSelectedText: () => view ? view.state.sliceDoc(selectedRange().from, selectedRange().to) : "",
  getSelectionFrom: () => view ? selectedRange().from : 0,
  getSelectionTo: () => view ? selectedRange().to : 0,
  getCaretPosition: () => view ? selectedRange().head : 0,
  replaceSelection,
  replaceRange,
  focus: () => view?.focus(),
  undo: () => Boolean(view && !view.state.readOnly && undo(view)),
  redo: () => Boolean(view && !view.state.readOnly && redo(view)),
  cut: cutSelection,
  copy: copySelection,
  paste: pasteClipboard,
  selectAll,
  canUndo: () => Boolean(view && undoDepth(view.state) > 0),
  canRedo: () => Boolean(view && redoDepth(view.state) > 0),
  isEditable: () => Boolean(view && !view.state.readOnly),
  hasSelection: () => Boolean(view && !selectedRange().empty),
  hasEditorFocus: () => Boolean(view?.hasFocus),
  openSearch: () => Boolean(view && openSearchPanel(view)),
  openReplace: () => {
    if (!view) return false;
    openSearchPanel(view);
    const replaceInput = view.dom.querySelector('.cm-search input[name="replace"]');
    if (replaceInput) {
      replaceInput.focus();
      replaceInput.select();
    }
    return true;
  },
  setTheme(config) {
    window.__jylosEditorConfig = { ...window.__jylosEditorConfig, ...config };
    view?.dispatch({ effects: [
      themeCompartment.reconfigure(editorTheme(window.__jylosEditorConfig)),
      highlightCompartment.reconfigure(syntaxHighlighting(editorHighlight(window.__jylosEditorConfig)))
    ] });
  },
  setFontSize(size) {
    window.__jylosEditorConfig.fontSize = size;
    view?.dispatch({ effects: fontCompartment.reconfigure(fontTheme(size)) });
  },
  setAutocompleteTitles(titles) {
    autocompleteTitles = Array.isArray(titles) ? titles.filter(title => typeof title === "string") : [];
  },
  setLabels(labels) {
    window.__jylosEditorConfig.labels = labels || {};
    view?.dispatch({ effects: phraseCompartment.reconfigure(EditorState.phrases.of(labels || {})) });
  },
  setLivePreviewEnabled(enabled) {
    window.__jylosEditorConfig.livePreview = Boolean(enabled);
    view?.dispatch({ effects: presentationCompartment.reconfigure(enabled ? livePreview() : []) });
  },
  setEditable(editable) {
    const enabled = Boolean(editable);
    window.__jylosEditorConfig.editable = enabled;
    view?.dispatch({ effects: editableCompartment.reconfigure(editableExtensions(enabled)) });
  },
  isLivePreviewEnabled: () => Boolean(window.__jylosEditorConfig?.livePreview)
};

// Exported for tests only, so the `==mark==` parser extension can be
// exercised directly against @codemirror/lang-markdown without going through
// the WebView bridge.
export { markExtension };
