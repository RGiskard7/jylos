import { syntaxTree } from "@codemirror/language";
import { StateEffect, StateField } from "@codemirror/state";
import { Decoration, EditorView, ViewPlugin, WidgetType } from "@codemirror/view";

// Live Preview is derived only from the visible syntax tree. Decorations never
// replace the Markdown document, so source mode and Live Preview share history.
const WIKI_LINK = /!?(?:\[\[[^\]\n]+\]\])/g;

class TextWidget extends WidgetType {
  constructor(text, className, from, action) {
    super();
    this.text = text;
    this.className = className;
    this.from = from;
    this.action = action;
  }

  eq(other) {
    return this.text === other.text && this.className === other.className && this.from === other.from;
  }

  toDOM(view) {
    const element = document.createElement("span");
    element.className = this.className;
    element.textContent = this.text;
    element.addEventListener("mousedown", event => {
      event.preventDefault();
      event.stopPropagation();
      if (this.action && (event.metaKey || event.ctrlKey)) {
        this.action();
      } else {
        view.dispatch({ selection: { anchor: this.from }, scrollIntoView: true });
        view.focus();
      }
    });
    return element;
  }

  ignoreEvent() {
    return false;
  }
}

class TaskWidget extends WidgetType {
  constructor(from, checked) {
    super();
    this.from = from;
    this.checked = checked;
  }

  eq(other) {
    return this.from === other.from && this.checked === other.checked;
  }

  toDOM(view) {
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.className = "cm-live-task";
    checkbox.checked = this.checked;
    checkbox.setAttribute("aria-label", this.checked ? "Completed task" : "Incomplete task");
    checkbox.addEventListener("mousedown", event => event.stopPropagation());
    checkbox.addEventListener("change", event => {
      event.stopPropagation();
      if (view.state.readOnly) {
        checkbox.checked = this.checked;
        return;
      }
      view.dispatch({
        changes: { from: this.from + 1, to: this.from + 2, insert: checkbox.checked ? "x" : " " },
        userEvent: "input"
      });
      view.focus();
    });
    return checkbox;
  }

  ignoreEvent() {
    return false;
  }
}

class ImageWidget extends WidgetType {
  constructor(source, alt, from) {
    super();
    this.source = source;
    this.alt = alt;
    this.from = from;
  }

  eq(other) {
    return this.source === other.source && this.alt === other.alt && this.from === other.from;
  }

  toDOM(view) {
    const figure = document.createElement("span");
    figure.className = "cm-live-image";
    const resolved = window.javaEditor?.resolveImageSource(this.source) || this.source;
    const image = document.createElement("img");
    image.alt = this.alt;
    image.src = resolved;
    image.addEventListener("error", () => {
      image.hidden = true;
      fallback.hidden = false;
    });
    const fallback = document.createElement("span");
    fallback.className = "cm-live-image-fallback";
    fallback.textContent = this.alt || this.source;
    fallback.hidden = true;
    figure.append(image, fallback);
    figure.addEventListener("mousedown", event => {
      event.preventDefault();
      view.dispatch({ selection: { anchor: this.from }, scrollIntoView: true });
      view.focus();
    });
    return figure;
  }

  ignoreEvent() {
    return false;
  }

}

class TableRowWidget extends WidgetType {
  constructor(source, delimiter, header, from) {
    super();
    this.source = source;
    this.delimiter = delimiter;
    this.header = header;
    this.from = from;
  }

  eq(other) {
    return this.source === other.source && this.delimiter === other.delimiter
      && this.header === other.header && this.from === other.from;
  }

  toDOM(view) {
    const wrapper = document.createElement("span");
    wrapper.className = `cm-live-table-row${this.header ? " cm-live-table-header" : ""}`;
    const cells = splitTableRow(this.source);
    const alignment = splitTableRow(this.delimiter);
    wrapper.style.gridTemplateColumns = `repeat(${Math.max(1, cells.length)}, minmax(0, 1fr))`;
    cells.forEach((cell, columnIndex) => {
      const element = document.createElement("span");
      element.className = "cm-live-table-cell";
      element.textContent = cell.trim();
      const marker = alignment[columnIndex]?.trim() || "";
      if (marker.startsWith(":") && marker.endsWith(":")) element.style.textAlign = "center";
      else if (marker.endsWith(":")) element.style.textAlign = "right";
      else element.style.textAlign = "left";
      wrapper.appendChild(element);
    });
    wrapper.addEventListener("mousedown", event => {
      event.preventDefault();
      view.dispatch({ selection: { anchor: this.from }, scrollIntoView: true });
      view.focus();
    });
    return wrapper;
  }

  ignoreEvent() {
    return false;
  }

}

// Plugin-rendered fenced blocks. The host computes the HTML on a background thread and
// pushes it in; the editor never calls back into Java while building decorations, because
// WebView JavaScript runs on the JavaFX Application Thread and a synchronous bridge call
// would put plugin work — and its I/O — on the UI thread during scrolling.
export const setBlockRenders = StateEffect.define();

export const blockRenderField = StateField.define({
  create: () => ({}),
  update(value, transaction) {
    for (const effect of transaction.effects) {
      if (effect.is(setBlockRenders)) return effect.value;
    }
    return value;
  }
});

const FENCE_CLOSE = /^[ \t]{0,3}(`{3,}|~{3,})[ \t]*$/;
const FENCE_OPEN = /^[ \t]{0,3}(`{3,}|~{3,})[ \t]*([A-Za-z0-9_+-]+)[ \t]*$/;

// Fenced blocks are found by scanning lines rather than walking the syntax tree, so the
// editor applies exactly the rules the Java side used to extract and render them: same
// fence pairing (the closing fence must repeat the opening one), same info string, same
// trimmed body. It also keeps this independent of how much of the document Lezer has
// parsed, since these decorations are computed over the whole document, not the viewport.
function scanFencedBlocks(doc) {
  const blocks = [];
  let open = null;
  for (let number = 1; number <= doc.lines; number++) {
    const line = doc.line(number);
    if (open) {
      const close = line.text.match(FENCE_CLOSE);
      if (close && close[1] === open.marker) {
        blocks.push({
          from: open.from,
          to: line.to,
          key: `${open.language.toLowerCase()}\n${open.body.join("\n").trim()}`
        });
        open = null;
      } else {
        open.body.push(line.text);
      }
      continue;
    }
    const match = line.text.match(FENCE_OPEN);
    if (match) open = { from: line.from, marker: match[1], language: match[2], body: [] };
  }
  return blocks;
}

/** True when the cursor or a selection touches the block, which reveals its source. */
function selectionTouches(state, from, to) {
  return state.selection.ranges.some(range => range.to >= from && range.from <= to);
}

/** Blocks currently displayed as rendered HTML, keyed by document range. */
function renderedBlocks(state) {
  const renders = state.field(blockRenderField, false);
  if (!renders) return [];
  const rendered = [];
  for (const block of scanFencedBlocks(state.doc)) {
    const html = renders[block.key];
    if (html && !selectionTouches(state, block.from, block.to)) {
      rendered.push({ ...block, html });
    }
  }
  return rendered;
}

class RenderedBlockWidget extends WidgetType {
  constructor(html, from) {
    super();
    this.html = html;
    this.from = from;
  }

  eq(other) {
    return this.html === other.html && this.from === other.from;
  }

  toDOM(view) {
    const wrapper = document.createElement("div");
    wrapper.className = "cm-live-block-render";
    // The markup comes from an installed plugin, the same trust boundary as the
    // preview's own enhancer injections; escaping note-derived content is the
    // renderer's responsibility.
    wrapper.innerHTML = this.html;

    wrapper.addEventListener("mousedown", event => {
      // Anchors keep their own behaviour (handled on click); anywhere else puts the
      // cursor into the block so it reverts to source and can be edited.
      if (event.target.closest("a")) return;
      event.preventDefault();
      view.dispatch({ selection: { anchor: this.from }, scrollIntoView: true });
      view.focus();
    });

    wrapper.addEventListener("click", event => {
      const anchor = event.target.closest("a");
      if (!anchor) return;
      event.preventDefault();
      const href = anchor.getAttribute("href") || "";
      if (href.startsWith("jylos://open-note/")) {
        const target = anchor.getAttribute("data-target")
          || decodeURIComponent(href.slice("jylos://open-note/".length));
        window.javaEditor?.openNote(target);
      } else if (/^https?:/i.test(href)) {
        window.javaEditor?.openExternal(href);
      }
    });

    return wrapper;
  }

  ignoreEvent() {
    return false;
  }
}

function computeBlockDecorations(state) {
  const decorations = renderedBlocks(state).map(block => Decoration.replace({
    widget: new RenderedBlockWidget(block.html, block.from),
    block: true
  }).range(block.from, block.to));
  return Decoration.set(decorations, true);
}

// A StateField, not the Live Preview ViewPlugin: CodeMirror rejects block decorations —
// and any replacement spanning a line break — coming from a plugin, because it cannot
// reconcile them with the height map it computes before plugins run. This is also why
// tables are replaced row by row rather than as one widget.
export const blockRenderDecorations = StateField.define({
  create: computeBlockDecorations,
  update(value, transaction) {
    const rendersChanged = transaction.effects.some(effect => effect.is(setBlockRenders));
    if (!transaction.docChanged && !transaction.selection && !rendersChanged) return value;
    return computeBlockDecorations(transaction.state);
  },
  provide: field => EditorView.decorations.from(field)
});

function splitTableRow(line) {
  const value = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  const cells = [];
  let current = "";
  let escaped = false;
  for (const character of value) {
    if (escaped) {
      current += character;
      escaped = false;
    } else if (character === "\\") {
      current += character;
      escaped = true;
    } else if (character === "|") {
      cells.push(current);
      current = "";
    } else {
      current += character;
    }
  }
  cells.push(current);
  return cells;
}

function activeBlocks(view) {
  const tree = syntaxTree(view.state);
  return view.state.selection.ranges.map(range => {
    const position = range.head;
    let node = tree.resolveInner(position, position < view.state.doc.length ? 1 : -1);
    let paragraph = null;
    while (node && node.name !== "Document") {
      if (node.name === "Paragraph") paragraph = paragraph || node;
      if (node.name === "ListItem" || node.name === "Blockquote" || node.name === "FencedCode"
          || node.name === "CodeBlock" || node.name === "Table" || node.name === "HorizontalRule"
          || /^ATXHeading[1-6]$/.test(node.name) || /^SetextHeading[12]$/.test(node.name)) {
        return { from: node.from, to: node.to };
      }
      node = node.parent;
    }
    if (paragraph) return { from: paragraph.from, to: paragraph.to };
    const line = view.state.doc.lineAt(position);
    return { from: line.from, to: line.to };
  });
}

function intersectsActive(blocks, from, to) {
  return blocks.some(block => block.from <= to && block.to >= from);
}

function coveredBy(ranges, from, to) {
  return ranges.some(range => range.from <= from && range.to >= to);
}

function lineDecoration(view, from, className) {
  return Decoration.line({ class: className }).range(view.state.doc.lineAt(from).from);
}

/**
 * One line decoration per line spanned by [from, to). A single `Decoration.line()` only
 * ever styles the one line containing its position, so a node spanning several lines —
 * a fenced code block, a blockquote — needs one of these per line or every line after
 * the first silently falls back to Live Preview's base (proportional) styling instead
 * of the block's own.
 */
function lineDecorationsForRange(view, from, to, className) {
  const decorations = [];
  const firstLine = view.state.doc.lineAt(from).number;
  const lastLine = view.state.doc.lineAt(to).number;
  for (let number = firstLine; number <= lastLine; number++) {
    decorations.push(Decoration.line({ class: className }).range(view.state.doc.line(number).from));
  }
  return decorations;
}

function wikiReplacement(match, from) {
  const embed = match.startsWith("!");
  const inner = match.slice(embed ? 3 : 2, -2);
  const [targetPart, aliasPart] = inner.split("|", 2);
  const target = targetPart.split("#", 1)[0].trim();
  const label = (aliasPart || targetPart).trim();
  const className = embed ? "cm-live-embed" : "cm-live-wikilink";
  const text = embed ? `▣ ${label}` : label;
  return Decoration.replace({
    widget: new TextWidget(text, className, from, () => window.javaEditor?.openNote(target))
  }).range(from, from + match.length);
}

function linkReplacement(label, target, from, to) {
  return Decoration.replace({
    widget: new TextWidget(label || target, "cm-live-link", from,
      () => window.javaEditor?.openMarkdownLink(target))
  }).range(from, to);
}

function directChild(node, name) {
  for (let child = node.node.firstChild; child; child = child.nextSibling) {
    if (child.name === name) return child;
  }
  return null;
}

function linkParts(state, node, image) {
  const url = directChild(node, "URL");
  if (!url) return null;
  const source = state.sliceDoc(node.from, node.to);
  const label = source.match(image ? /^!\[([^\]]*)\]/s : /^\[([^\]]*)\]/s);
  return {
    label: label ? label[1] : "",
    target: state.sliceDoc(url.from, url.to).trim()
  };
}

const OPEN_UNDERLINE_TAG = /^<u(\s[^>]*)?>$/i;
const CLOSE_UNDERLINE_TAG = /^<\/u\s*>$/i;

// Raw inline HTML (unlike Strikethrough/EmphasisMark) has no paired AST node
// covering "<u>...</u>" as a unit — @lezer/markdown emits two flat, unrelated
// HTMLTag leaves for the open and close tags. Pairing them by a simple stack
// scan is the only way to know which span of text between two tags an
// "underline" decoration should cover. Tags are collected during the main
// tree walk in buildDecorations (not a separate pass over the syntax tree —
// this runs on every scroll-triggered rebuild, so a second full traversal
// of the visible range is avoided).
function pairUnderlineTags(tags) {
  const pairs = [];
  const stack = [];
  for (const tag of tags) {
    if (tag.open) {
      stack.push(tag);
    } else if (stack.length) {
      pairs.push({ open: stack.pop(), close: tag });
    }
  }
  return pairs;
}

function buildDecorations(view, blocks) {
  const decorations = [];
  const replacements = [];
  const htmlTags = [];
  const renderedRanges = renderedBlocks(view.state);

  for (const visible of view.visibleRanges) {
    const firstLine = view.state.doc.lineAt(visible.from).number;
    const lastLine = view.state.doc.lineAt(visible.to).number;
    for (let number = firstLine; number <= lastLine; number++) {
      const line = view.state.doc.line(number);
      for (const match of line.text.matchAll(WIKI_LINK)) {
        const from = line.from + match.index;
        const to = from + match[0].length;
        if (!intersectsActive(blocks, from, to)) {
          decorations.push(wikiReplacement(match[0], from));
          replacements.push({ from, to });
        }
      }
    }
  }

  const tree = syntaxTree(view.state);
  for (const visible of view.visibleRanges) {
    tree.iterate({
      from: view.state.doc.lineAt(visible.from).from,
      to: view.state.doc.lineAt(visible.to).to,
      enter(node) {
        if (coveredBy(replacements, node.from, node.to)) return false;

        const active = intersectsActive(blocks, node.from, node.to);
        const source = view.state.sliceDoc(node.from, node.to);
        if (/^ATXHeading[1-6]$/.test(node.name)) {
          decorations.push(lineDecoration(view, node.from, `cm-live-heading cm-live-${node.name.toLowerCase()}`));
        } else if (node.name === "Blockquote") {
          decorations.push(...lineDecorationsForRange(view, node.from, node.to, "cm-live-blockquote"));
        } else if (node.name === "FencedCode") {
          // Skip the code-block background for a block that blockRenderDecorations is
          // replacing with a widget, so the widget's own frame is the only one drawn.
          if (!coveredBy(renderedRanges, node.from, node.to)) {
            decorations.push(...lineDecorationsForRange(view, node.from, node.to, "cm-live-codeblock"));
          }
        } else if (node.name === "HTMLTag") {
          if (OPEN_UNDERLINE_TAG.test(source)) {
            htmlTags.push({ from: node.from, to: node.to, open: true });
          } else if (CLOSE_UNDERLINE_TAG.test(source)) {
            htmlTags.push({ from: node.from, to: node.to, open: false });
          }
        }

        if (active) return;

        if (node.name === "Image") {
          const image = linkParts(view.state, node, true);
          if (image) {
            decorations.push(Decoration.replace({
              widget: new ImageWidget(image.target, image.label, node.from)
            }).range(node.from, node.to));
            replacements.push({ from: node.from, to: node.to });
            return false;
          }
        }

        if (node.name === "Table") {
          const lines = source.split("\n");
          const delimiter = lines[1] || "";
          let lineFrom = node.from;
          lines.forEach((line, index) => {
            const lineTo = lineFrom + line.length;
            if (index === 1) {
              decorations.push(lineDecoration(view, lineFrom, "cm-live-table-delimiter-line"));
              decorations.push(Decoration.replace({}).range(lineFrom, lineTo));
            } else {
              decorations.push(Decoration.replace({
                widget: new TableRowWidget(line, delimiter, index === 0, lineFrom)
              }).range(lineFrom, lineTo));
            }
            replacements.push({ from: lineFrom, to: lineTo });
            lineFrom = lineTo + 1;
          });
          return false;
        }

        if (node.name === "HorizontalRule") {
          decorations.push(Decoration.replace({
            widget: new TextWidget("", "cm-live-horizontal-rule", node.from)
          }).range(node.from, node.to));
          replacements.push({ from: node.from, to: node.to });
          return false;
        }

        if (node.name === "TaskMarker") {
          decorations.push(Decoration.replace({
            widget: new TaskWidget(node.from, /x/i.test(source))
          }).range(node.from, node.to));
          replacements.push({ from: node.from, to: node.to });
          return false;
        }

        if (node.name === "Link") {
          const link = linkParts(view.state, node, false);
          if (link) {
            decorations.push(linkReplacement(link.label, link.target, node.from, node.to));
            replacements.push({ from: node.from, to: node.to });
            return false;
          }
        }

        if (node.name === "Autolink" || node.name === "URL") {
          const target = source.replace(/^<|>$/g, "");
          if (/^https?:\/\//i.test(target)) {
            decorations.push(linkReplacement(target, target, node.from, node.to));
            replacements.push({ from: node.from, to: node.to });
            return false;
          }
        }

        if (node.name === "ListMark" && /^[-+*]$/.test(source)) {
          decorations.push(Decoration.replace({
            widget: new TextWidget("•", "cm-live-list-marker", node.from)
          }).range(node.from, node.to));
          replacements.push({ from: node.from, to: node.to });
          return false;
        }

        if (["HeaderMark", "EmphasisMark", "StrikethroughMark", "MarkMark", "QuoteMark", "CodeMark", "CodeInfo"]
          .includes(node.name)) {
          decorations.push(Decoration.replace({}).range(node.from, node.to));
          replacements.push({ from: node.from, to: node.to });
        }
      }
    });
  }

  for (const pair of pairUnderlineTags(htmlTags)) {
    if (pair.open.to < pair.close.from) {
      decorations.push(Decoration.mark({ class: "cm-live-underline" }).range(pair.open.to, pair.close.from));
    }
    if (!intersectsActive(blocks, pair.open.from, pair.close.to)) {
      decorations.push(Decoration.replace({}).range(pair.open.from, pair.open.to));
      decorations.push(Decoration.replace({}).range(pair.close.from, pair.close.to));
    }
  }

  return Decoration.set(decorations, true);
}

function blockKey(blocks) {
  return blocks.map(block => `${block.from}-${block.to}`).join(",");
}

// Native `selectionchange` fires continuously while the user drags a text
// selection, and each tick reaches this plugin as a selection-only update.
// Rebuilding decorations on every one of those ticks replaces/restores
// syntax-mark widgets under the pointer mid-drag, which resets the browser's
// native selection anchor. Reveal/hide of raw syntax only needs to run when
// the covering block actually changes, so selection-only updates are keyed
// against the last computed active-block range and skipped when unchanged.
//
// `activeBlocks(view)` is computed once per update and threaded through to
// both buildDecorations() and the key comparison below — scroll triggers a
// viewportChanged update on every viewport shift, so computing it twice per
// tick (once inside the old buildDecorations, once for the key) was real,
// avoidable per-scroll-frame cost inside the WebView.
const livePreviewPlugin = ViewPlugin.fromClass(class {
  constructor(view) {
    const blocks = activeBlocks(view);
    this.decorations = buildDecorations(view, blocks);
    this.activeKey = blockKey(blocks);
  }

  update(update) {
    // Newly delivered block renders must reach the view even when nothing else changed.
    const rendersChanged = update.startState.field(blockRenderField, false)
      !== update.state.field(blockRenderField, false);
    if (update.docChanged || update.viewportChanged || rendersChanged) {
      const blocks = activeBlocks(update.view);
      this.decorations = buildDecorations(update.view, blocks);
      this.activeKey = blockKey(blocks);
      return;
    }
    if (update.selectionSet) {
      const blocks = activeBlocks(update.view);
      const key = blockKey(blocks);
      if (key !== this.activeKey) {
        this.decorations = buildDecorations(update.view, blocks);
        this.activeKey = key;
      }
    }
  }
}, { decorations: value => value.decorations });

// Matches modern-theme.css's .root font-family exactly. Live Preview's rendered
// prose (paragraphs, headings, revealed widgets) should read like the rest of the
// app — a document, not a code editor — while raw/source text and actual code stay
// monospace via the more specific rules below. Source mode (Live Preview off) never
// loads this theme at all, so it keeps the plain monospace editing font untouched.
const UI_FONT_FAMILY = '"Segoe UI", "SF Pro Text", "Helvetica Neue", -apple-system, sans-serif';
const MONOSPACE_FONT_FAMILY = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";

const livePreviewTheme = EditorView.baseTheme({
  // Direct rule on .cm-content, not "&": editorTheme() only sets fontFamily on
  // .cm-scroller (an ancestor), so a rule on the root would just inherit that
  // monospace value straight through — a direct rule on .cm-content itself is
  // needed to actually win, regardless of which theme layer has priority.
  ".cm-content": { fontFamily: UI_FONT_FAMILY },
  ".cm-live-heading": { lineHeight: "1.35", paddingTop: ".18em" },
  ".cm-live-atxheading1": { fontSize: "1.6em", fontWeight: "750" },
  ".cm-live-atxheading2": { fontSize: "1.4em", fontWeight: "725" },
  ".cm-live-atxheading3": { fontSize: "1.22em", fontWeight: "700" },
  ".cm-live-atxheading4, .cm-live-atxheading5, .cm-live-atxheading6": { fontWeight: "700" },
  ".cm-live-blockquote": { borderLeft: "3px solid var(--jylos-accent)", paddingLeft: "12px", color: "var(--jylos-muted)" },
  ".cm-live-codeblock": { backgroundColor: "var(--jylos-code-bg)", fontFamily: MONOSPACE_FONT_FAMILY },
  ".cm-live-wikilink, .cm-live-link": { color: "var(--jylos-accent)", textDecoration: "underline", cursor: "pointer" },
  ".cm-live-embed": { display: "inline-block", color: "var(--jylos-accent)", background: "var(--jylos-panel)", border: "1px solid var(--jylos-border)", borderRadius: "6px", padding: "1px 6px", cursor: "pointer" },
  ".cm-live-list-marker": { display: "inline-block", width: "1em", color: "var(--jylos-accent)", fontWeight: "700" },
  ".cm-live-underline": { textDecoration: "underline" },
  ".cm-live-task": { margin: "0 .45em 0 0", accentColor: "var(--jylos-accent)", verticalAlign: "middle" },
  ".cm-live-horizontal-rule": { display: "block", width: "100%", height: "1px", margin: ".8em 0", background: "var(--jylos-border)" },
  ".cm-live-image": { display: "block", maxWidth: "min(100%, 720px)", margin: ".65em 0", padding: "0" },
  ".cm-live-image img": { display: "block", maxWidth: "100%", maxHeight: "520px", borderRadius: "8px", objectFit: "contain" },
  ".cm-live-image-fallback": { display: "inline-block", padding: "8px 10px", color: "var(--jylos-muted)", background: "var(--jylos-panel)", border: "1px solid var(--jylos-border)", borderRadius: "6px" },
  ".cm-live-table-row": { display: "inline-grid", width: "100%", color: "inherit", verticalAlign: "top" },
  ".cm-live-table-cell": { minWidth: "0", padding: "6px 9px", border: "1px solid var(--jylos-border)", overflowWrap: "anywhere" },
  ".cm-live-table-header .cm-live-table-cell": { background: "var(--jylos-panel)", fontWeight: "700" },
  ".cm-live-table-delimiter-line": { height: "0", lineHeight: "0", overflow: "hidden" },
  // Baseline styling for whatever markup a plugin block renderer returns. These target
  // the extension point, not any particular plugin: a renderer that emits plain semantic
  // HTML already reads correctly, and one with its own classes styles them itself.
  ".cm-live-block-render": {
    display: "block",
    margin: ".5em 0",
    padding: "10px 12px",
    border: "1px solid var(--jylos-border)",
    borderRadius: "8px",
    background: "var(--jylos-panel)",
    fontFamily: UI_FONT_FAMILY,
    cursor: "default"
  },
  ".cm-live-block-render table": { borderCollapse: "collapse", width: "100%" },
  ".cm-live-block-render th, .cm-live-block-render td": {
    border: "1px solid var(--jylos-border)",
    padding: "6px 9px",
    textAlign: "left",
    verticalAlign: "top"
  },
  ".cm-live-block-render ul": { margin: ".3em 0", paddingLeft: "1.3em" },
  ".cm-live-block-render a": { color: "var(--jylos-accent)", cursor: "pointer" },
  ".cm-live-block-render > :first-child": { marginTop: "0" },
  ".cm-live-block-render > :last-child": { marginBottom: "0" }
});

export function livePreview() {
  // blockRenderDecorations is presentation, so it belongs here rather than in the base
  // state: source mode must still show a plugin-claimed block as its raw text. The data
  // it reads (blockRenderField) stays in the base state so toggling modes keeps it.
  return [livePreviewPlugin, blockRenderDecorations, livePreviewTheme];
}

// Exported for tests only, so they can look up the plugin instance via
// `view.plugin(livePreviewPlugin)` and assert on decoration rebuild behavior.
export { livePreviewPlugin };
