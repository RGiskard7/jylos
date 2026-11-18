import { syntaxTree } from "@codemirror/language";
import { StateEffect, StateField } from "@codemirror/state";
import { Decoration, EditorView, ViewPlugin, WidgetType } from "@codemirror/view";

// Live Preview is derived only from the visible syntax tree. Decorations never
// replace the Markdown document, so source mode and Live Preview share history.
const WIKI_LINK = /!?(?:\[\[[^\]\n]+\]\])/g;
const IMAGE_EXTENSION = /\.(png|jpe?g|gif|webp|svg|bmp|avif)$/i;
const FOOTNOTE_REF = /\[\^([^\]\s]+)\](?!:)/g;
const INLINE_FOOTNOTE = /\^\[[^\]\n]+\]/g;
const FOOTNOTE_DEF = /^[ \t]{0,3}\[\^([^\]\s]+)\]:/;
const TAG = /(?<![^\s])#([\p{L}\p{N}_/-]+)/gu;
const BLOCK_ID = /(?:^|[ \t]+)\^[A-Za-z0-9-]+[ \t]*$/;
const INLINE_MATH = /(?<![\\$])\$(?![\s$])([^$\n]+?)(?<![\s\\])\$(?![$\d])/g;

// Whether the editor has the keyboard focus. Without it nothing is "being edited", so
// every element shows rendered, as in Obsidian; with it, the element under the cursor
// shows its Markdown. Kept in the state so a focus change rebuilds the decorations.
export const setEditorFocus = StateEffect.define();

export const editorFocusField = StateField.define({
  create: () => false,
  update(value, transaction) {
    for (const effect of transaction.effects) {
      if (effect.is(setEditorFocus)) return effect.value;
    }
    return value;
  }
});

// The host loads KaTeX only when a note has math; this tells the editor it can draw it now.
export const setMathReady = StateEffect.define();

const mathReadyField = StateField.define({
  create: () => typeof window !== "undefined" && Boolean(window.katex),
  update(value, transaction) {
    for (const effect of transaction.effects) {
      if (effect.is(setMathReady)) return effect.value;
    }
    return value;
  }
});

// CodeMirror's own focus tracking (view.hasFocus, focusChangeEffect) also asks
// document.hasFocus(), which the JavaFX WebView does not answer as a browser does, so the
// editor would never count as focused there. The focus and blur events of the content are
// reliable in both; the state change is dispatched after the event, outside any update.
function followFocus(view, focused) {
  setTimeout(() => {
    if (view.state.field(editorFocusField, false) !== focused) {
      view.dispatch({ effects: setEditorFocus.of(focused) });
    }
  }, 0);
}

const focusTracker = EditorView.domEventHandlers({
  focus(event, view) {
    followFocus(view, true);
  },
  blur(event, view) {
    followFocus(view, false);
  }
});

/** Whether a selection touches [from, to] — only while the editor has the focus. */
function revealed(state, from, to) {
  if (!state.field(editorFocusField, false)) return false;
  return state.selection.ranges.some(range => range.to >= from && range.from <= to);
}

/** Whether a selection is on the line of `position` — only while the editor has the focus. */
function lineRevealed(state, position) {
  const line = state.doc.lineAt(position);
  return revealed(state, line.from, line.to);
}

const CODE_NODES = new Set(["InlineCode", "FencedCode", "CodeBlock", "CodeText", "CodeMark"]);

/** True when `position` is inside code, where nothing is rendered. */
function inCode(tree, position) {
  for (let node = tree.resolveInner(position, 1); node; node = node.parent) {
    if (CODE_NODES.has(node.name)) return true;
  }
  return false;
}

const LINK_NODES = new Set(["Link", "URL", "Autolink", "Image", "LinkReference"]);

function inLink(tree, position) {
  for (let node = tree.resolveInner(position, 1); node; node = node.parent) {
    if (LINK_NODES.has(node.name)) return true;
  }
  return false;
}

// Document-wide facts (footnote numbers, comments, blocks) are recomputed only when the
// document changes, not on every scroll or cursor move. They still run on every keystroke, so
// each scan tests a line with a regular expression only when a cheap check says it can match:
// most lines are prose and are passed over without one.

/** The first character after the line's indentation (looking at most four characters in). */
function leadingChar(text) {
  for (let index = 0; index < text.length && index < 4; index++) {
    const character = text[index];
    if (character !== " " && character !== "\t") return character;
  }
  return "";
}

const FENCE_START = /^[ \t]{0,3}(`{3,}|~{3,})/;

/** The fence a line opens or closes (` ``` ` or `~~~`, three or more), or null. */
function fenceOf(text) {
  const first = leadingChar(text);
  if (first !== "`" && first !== "~") return null;
  const match = text.match(FENCE_START);
  return match ? match[1] : null;
}

/** Runs `scan` once per document version (documents are immutable: a new version is a new key). */
function cachedPerDocument(scan) {
  const cache = new WeakMap();
  return doc => {
    let result = cache.get(doc);
    if (!result) {
      result = scan(doc);
      cache.set(doc, result);
    }
    return result;
  };
}

/** The document's lines and where each starts, read once and shared by every scan. */
const linesOf = cachedPerDocument(doc => {
  const texts = [];
  const froms = [];
  let from = 0;
  for (const text of doc.iterLines()) {
    texts.push(text);
    froms.push(from);
    from += text.length + 1;
  }
  return { texts, froms };
});

/** Footnote numbers by first use, as the reading view numbers them. */
const footnoteNumbers = cachedPerDocument(doc => {
  const byLabel = new Map();
  const inline = new Map();
  let next = 1;
  // No footnote spans a line break, so the document is read line by line.
  const pattern = /\[\^([^\]\s]+)\](?!:)|\^\[[^\]\n]+\]/g;
  const { texts, froms } = linesOf(doc);
  for (let index = 0; index < texts.length; index++) {
    if (!texts[index].includes("^")) continue;
    for (const match of texts[index].matchAll(pattern)) {
      if (match[1] !== undefined) {
        if (!byLabel.has(match[1])) byLabel.set(match[1], next++);
      } else {
        inline.set(froms[index] + match.index, next++);
      }
    }
  }
  return { byLabel, inline };
});

/** `%%…%%` ranges outside code (fenced blocks and inline code spans), in document order. */
const commentRanges = cachedPerDocument(doc => {
  const ranges = [];
  const markers = [];
  let fence = null;
  const { texts, froms } = linesOf(doc);
  for (let index = 0; index < texts.length; index++) {
    const text = texts[index];
    const lineFrom = froms[index];
    const marker = fenceOf(text);
    if (fence) {
      if (marker && marker[0] === fence[0] && marker.length >= fence.length) fence = null;
      continue;
    }
    if (marker) {
      fence = marker;
      continue;
    }
    if (!text.includes("%%")) continue;
    const masked = text.replace(/(`+)[^`]*?\1/g, span => " ".repeat(span.length));
    for (let index = masked.indexOf("%%"); index >= 0; index = masked.indexOf("%%", index + 2)) {
      markers.push(lineFrom + index);
    }
  }
  for (let index = 0; index + 1 < markers.length; index += 2) {
    ranges.push({ from: markers[index], to: markers[index + 1] + 2 });
  }
  return ranges;
});

// Widgets are compared by what they show, never by where they are: typing before a widget
// shifts its position, and comparing positions would redraw every widget below the cursor on
// each keystroke (KaTeX drawn again, images reloaded, the text jumping while they load). A
// kept widget's DOM therefore asks CodeMirror where it is now when it is clicked.
function widgetPosition(view, element, fallback) {
  try {
    return view.posAtDOM(element);
  } catch (error) {
    return fallback;
  }
}

function placeCursorAt(view, element, fallback) {
  view.dispatch({ selection: { anchor: widgetPosition(view, element, fallback) }, scrollIntoView: true });
  view.focus();
}

class TextWidget extends WidgetType {
  /** `key` is what the action acts on (a link's target), so a changed target is a new widget. */
  constructor(text, className, from, action, key = "") {
    super();
    this.text = text;
    this.className = className;
    this.from = from;
    this.action = action;
    this.key = key;
  }

  eq(other) {
    return this.text === other.text && this.className === other.className && this.key === other.key;
  }

  toDOM(view) {
    const element = document.createElement("span");
    element.className = this.className;
    element.textContent = this.text;
    element.addEventListener("mousedown", event => {
      event.preventDefault();
      event.stopPropagation();
      // Cmd/Ctrl+click follows a link; with Shift too, or with the middle button, in a new tab.
      const middle = event.button === 1;
      if (this.action && (middle || event.metaKey || event.ctrlKey)) {
        this.action(middle || event.shiftKey);
      } else {
        placeCursorAt(view, element, this.from);
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
    return this.checked === other.checked;
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
      const from = widgetPosition(view, checkbox, this.from);
      view.dispatch({
        changes: { from: from + 1, to: from + 2, insert: checkbox.checked ? "x" : " " },
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

// A widget can change height after CodeMirror measured it: an image or an embedded note
// loads, KaTeX fonts arrive. CodeMirror reads line heights again only when it redraws something
// or the whole content changes height, and in a note shorter than the editor the content keeps
// the editor's height, so requestMeasure() alone is not enough: every click below the widget
// would land on the wrong line. One observer per editor watches the widgets that can grow and,
// when one really changes size, tells the Live Preview plugin, which changes an attribute of
// the first line: CodeMirror redraws it and measures every visible line again.
const layoutChanged = StateEffect.define();
const sizeObservers = new WeakMap();

function sizeObserver(view) {
  let observer = sizeObservers.get(view);
  if (observer || typeof ResizeObserver === "undefined") return observer;
  let pending = false;
  observer = new ResizeObserver(entries => {
    let changed = false;
    for (const entry of entries) {
      const height = Math.round(entry.target.getBoundingClientRect().height);
      const before = entry.target.__jylosHeight;
      entry.target.__jylosHeight = height;
      if (entry.target.__jylosBlockKey !== undefined && height > 0) {
        rememberBlockHeight(entry.target.__jylosBlockKey, height);
      }
      // The first report is the size CodeMirror measured when it drew the widget.
      if (before !== undefined && before !== height) changed = true;
    }
    if (!changed || pending) return;
    pending = true;
    requestAnimationFrame(() => {
      pending = false;
      if (view.dom.isConnected) view.dispatch({ effects: layoutChanged.of(null) });
    });
  });
  sizeObservers.set(view, observer);
  return observer;
}

function watchSize(view, element) {
  const observer = sizeObserver(view);
  if (!observer) return;
  observer.observe(element);
  element.__jylosSizeObserver = observer;
}

function stopWatchingSize(element) {
  element.__jylosSizeObserver?.unobserve(element);
}

// CodeMirror lays the document out from estimates and only measures what is on screen. A block
// widget without an estimate counts as one line, so the page jumps by the block's real height
// when it scrolls into view. A rendered block (callout, embed, plugin block, display formula)
// is estimated at the height it was last drawn at, keyed by its content; one never drawn, at
// the height of its source lines.
const blockHeights = new Map();
const BLOCK_HEIGHTS_KEPT = 500;
// CodeMirror's own line height before it has measured the text; updated once it has.
let lineHeight = 14;

function rememberBlockHeight(key, height) {
  blockHeights.delete(key);
  blockHeights.set(key, height);
  if (blockHeights.size > BLOCK_HEIGHTS_KEPT) blockHeights.delete(blockHeights.keys().next().value);
}

function estimateBlockHeight(key, lines) {
  return blockHeights.get(key) ?? Math.round(lines * lineHeight);
}

/** Watches a block widget's size, recording it as the estimate for the same content. */
function watchBlockSize(view, element, key) {
  if (view.defaultLineHeight > 0) lineHeight = view.defaultLineHeight;
  element.__jylosBlockKey = key;
  watchSize(view, element);
}

class MathWidget extends WidgetType {
  constructor(tex, display, from, lines = 1) {
    super();
    this.tex = tex;
    this.display = display;
    this.from = from;
    this.lines = lines;
  }

  eq(other) {
    return this.tex === other.tex && this.display === other.display;
  }

  get estimatedHeight() {
    return this.display ? estimateBlockHeight(`math:${this.tex}`, this.lines) : -1;
  }

  toDOM(view) {
    const element = document.createElement(this.display ? "div" : "span");
    element.className = this.display ? "cm-live-math cm-live-math-block" : "cm-live-math";
    try {
      element.innerHTML = window.katex.renderToString(this.tex, { displayMode: this.display, throwOnError: false });
    } catch (error) {
      element.textContent = this.tex;
    }
    element.addEventListener("mousedown", event => {
      event.preventDefault();
      placeCursorAt(view, element, this.from);
    });
    if (this.display) watchBlockSize(view, element, `math:${this.tex}`);
    else watchSize(view, element);
    return element;
  }

  destroy(dom) {
    stopWatchingSize(dom);
  }

  ignoreEvent() {
    return false;
  }
}

// The height each image was last drawn at. An image scrolled out of view loses its DOM; drawn
// again, it would take one line until the file decodes and then grow by hundreds of pixels
// above what the reader is looking at, pushing the text away. Its old height is reserved
// meanwhile, and CodeMirror is given it as the estimate before the image is drawn.
const imageHeights = new Map();

// What the host answered for each image of the open note. Reading an image means reading the
// file and encoding it on the UI thread, so it is done once per note rather than every time the
// image scrolls into view. Kept in the state: a new note (a new state) starts empty, since the
// same name may be another file there.
const imageSources = StateField.define({
  create: () => new Map(),
  update: value => value
});

// Per image: the address it is drawn from and whether the host was already asked to convert it.
function imageEntry(view, source) {
  const cache = view.state.field(imageSources, false);
  let entry = cache?.get(source);
  if (!entry) {
    entry = { src: window.javaEditor?.resolveImageSource(source) || source, converted: false };
    cache?.set(source, entry);
  }
  return entry;
}

// JavaFX's WebKit cannot decode WebP (Obsidian, on Chromium, can), and many pages serve it
// even under a .png name. An image that fails is handed to the host, which answers later,
// from a background thread, with a PNG copy or "" through imageConverted().
const conversions = new Map();
let nextConversion = 0;

function convertImage(source, done) {
  if (typeof window.javaEditor?.convertImage !== "function") return false;
  const id = String(++nextConversion);
  conversions.set(id, done);
  window.javaEditor.convertImage(id, source);
  return true;
}

export function imageConverted(id, png) {
  const done = conversions.get(String(id));
  conversions.delete(String(id));
  done?.(png || "");
}

class ImageWidget extends WidgetType {
  constructor(source, alt, from) {
    super();
    this.source = source;
    this.alt = alt;
    this.from = from;
  }

  eq(other) {
    return this.source === other.source && this.alt === other.alt;
  }

  get estimatedHeight() {
    return imageHeights.get(this.source) ?? -1;
  }

  toDOM(view) {
    const figure = document.createElement("span");
    figure.className = "cm-live-image";
    const entry = imageEntry(view, this.source);
    const image = document.createElement("img");
    image.alt = this.alt;
    const known = imageHeights.get(this.source);
    if (known) figure.style.minHeight = `${known}px`;
    image.addEventListener("load", () => {
      figure.style.minHeight = "";
      const height = figure.getBoundingClientRect().height;
      if (height > 0) imageHeights.set(this.source, height);
    });
    image.src = entry.src;
    const showFallback = () => {
      figure.style.minHeight = "";
      image.hidden = true;
      fallback.hidden = false;
    };
    image.addEventListener("error", () => {
      // Asked once per image and note: the answer, a PNG or nothing, is kept in the entry.
      if (entry.converted) {
        showFallback();
        return;
      }
      entry.converted = true;
      image.hidden = true;
      const asked = convertImage(entry.src, png => {
        if (!png) {
          showFallback();
          return;
        }
        entry.src = png;
        image.hidden = false;
        image.src = png;
      });
      if (!asked) showFallback();
    });
    const fallback = document.createElement("span");
    fallback.className = "cm-live-image-fallback";
    fallback.textContent = this.alt || this.source;
    fallback.hidden = true;
    figure.append(image, fallback);
    figure.addEventListener("mousedown", event => {
      event.preventDefault();
      placeCursorAt(view, figure, this.from);
    });
    watchSize(view, figure);
    return figure;
  }

  destroy(dom) {
    stopWatchingSize(dom);
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
      && this.header === other.header;
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
      placeCursorAt(view, wrapper, this.from);
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

function sameRenders(a, b) {
  const keys = Object.keys(a);
  return keys.length === Object.keys(b).length && keys.every(key => a[key] === b[key]);
}

export const blockRenderField = StateField.define({
  create: () => ({}),
  update(value, transaction) {
    for (const effect of transaction.effects) {
      // The host sends its renders again after every pause in typing; the same ones keep the
      // field as it is, so nothing downstream rebuilds.
      if (effect.is(setBlockRenders)) return sameRenders(value, effect.value || {}) ? value : effect.value;
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
const scanFencedBlocks = cachedPerDocument(doc => {
  const blocks = [];
  let open = null;
  const { texts, froms } = linesOf(doc);
  for (let index = 0; index < texts.length; index++) {
    const text = texts[index];
    const line = { from: froms[index], to: froms[index] + text.length, text };
    const fenceLine = fenceOf(text) !== null;
    if (open) {
      const close = fenceLine ? text.match(FENCE_CLOSE) : null;
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
    const match = fenceLine ? text.match(FENCE_OPEN) : null;
    if (match) open = { from: line.from, marker: match[1], language: match[2], body: [] };
  }
  return blocks;
});

const CALLOUT_START = /^[ \t]{0,3}>[ \t]?\[![^\]\n]+\]/;
const QUOTE_LINE = /^[ \t]{0,3}>/;
const EMBED_LINE = /^!\[\[([^\]\n]+)\]\]$/;

/**
 * Blocks the host renders for Live Preview, besides plugin fenced blocks: callouts
 * (`> [!type]` and the quote lines that follow) and a note embedded on a line of its own
 * (`![[Note]]`). Keys are shared with EditorBlockRenderSupport on the Java side:
 * `callout\n` + the block's lines, `embed\n` + the link without its display text.
 */
export const scanHostBlocks = cachedPerDocument(doc => {
  const blocks = [];
  let fence = null;
  const { texts, froms } = linesOf(doc);
  for (let index = 0; index < texts.length; index++) {
    const text = texts[index];
    const marker = fenceOf(text);
    if (fence) {
      if (marker && marker[0] === fence[0] && marker.length >= fence.length) fence = null;
      continue;
    }
    if (marker) {
      fence = marker;
      continue;
    }
    if (leadingChar(text) === ">" && CALLOUT_START.test(text)) {
      let last = index;
      while (last + 1 < texts.length && QUOTE_LINE.test(texts[last + 1])) last++;
      blocks.push({
        from: froms[index],
        to: froms[last] + texts[last].length,
        key: `callout\n${texts.slice(index, last + 1).join("\n")}`
      });
      index = last;
      continue;
    }
    const embed = text.includes("![[") ? text.trim().match(EMBED_LINE) : null;
    if (embed) {
      const target = embed[1].split("|", 1)[0].trim();
      if (!IMAGE_EXTENSION.test(target.split("#", 1)[0])) {
        blocks.push({ from: froms[index], to: froms[index] + text.length, key: `embed\n${target}` });
      }
    }
  }
  return blocks;
});

/** `$$ … $$` math blocks (fences on their own lines, or on one line), outside code. */
const scanMathBlocks = cachedPerDocument(doc => {
  const blocks = [];
  let fence = null;
  const { texts, froms } = linesOf(doc);
  for (let index = 0; index < texts.length; index++) {
    const marker = fenceOf(texts[index]);
    if (fence) {
      if (marker && marker[0] === fence[0] && marker.length >= fence.length) fence = null;
      continue;
    }
    if (marker) {
      fence = marker;
      continue;
    }
    if (!texts[index].includes("$$")) continue;
    const text = texts[index].trim();
    if (!text.startsWith("$$")) continue;
    const single = text.match(/^\$\$(.+)\$\$$/);
    if (single) {
      blocks.push({ from: froms[index], to: froms[index] + texts[index].length, tex: single[1].trim() });
      continue;
    }
    if (text !== "$$") continue;
    for (let close = index + 1; close < texts.length; close++) {
      if (texts[close].includes("$$") && texts[close].trim() === "$$") {
        blocks.push({
          from: froms[index],
          to: froms[close] + texts[close].length,
          tex: texts.slice(index + 1, close).join("\n").trim()
        });
        index = close;
        break;
      }
    }
  }
  return blocks;
});

/** Blocks currently displayed rendered, keyed by document range. */
function renderedBlocks(state) {
  const renders = state.field(blockRenderField, false) || {};
  const rendered = [];
  for (const block of [...scanFencedBlocks(state.doc), ...scanHostBlocks(state.doc)]) {
    const html = renders[block.key];
    if (html && !revealed(state, block.from, block.to)) {
      rendered.push({ ...block, html });
    }
  }
  if (state.field(mathReadyField, false)) {
    for (const block of scanMathBlocks(state.doc)) {
      if (block.tex && !revealed(state, block.from, block.to)) rendered.push({ ...block, math: true });
    }
  }
  return rendered;
}

class RenderedBlockWidget extends WidgetType {
  constructor(html, from, lines = 1) {
    super();
    this.html = html;
    this.from = from;
    this.lines = lines;
  }

  eq(other) {
    return this.html === other.html;
  }

  get estimatedHeight() {
    return estimateBlockHeight(`html:${this.html}`, this.lines);
  }

  toDOM(view) {
    // The outer box holds the spacing (padding, not margin: see the theme); the inner one
    // is the visible frame.
    const outer = document.createElement("div");
    outer.className = "cm-live-block-render-outer";
    const wrapper = document.createElement("div");
    wrapper.className = "cm-live-block-render";
    outer.appendChild(wrapper);
    // The editor's text keeps its line breaks (pre-wrap); rendered HTML must not, or the
    // line breaks between its tags show as blank lines.
    wrapper.style.whiteSpace = "normal";
    // The markup comes from an installed plugin, the same trust boundary as the
    // preview's own enhancer injections; escaping note-derived content is the
    // renderer's responsibility.
    wrapper.innerHTML = this.html;
    const callout = wrapper.querySelector(":scope > .callout");
    if (callout) {
      const colour = [...callout.classList].find(name => name.startsWith("callout-") && name !== "callout-title");
      wrapper.classList.add("cm-live-callout");
      if (colour) wrapper.classList.add(`cm-live-${colour}`);
    }

    wrapper.addEventListener("mousedown", event => {
      // Anchors keep their own behaviour (handled on click; the middle button, which
      // fires no click, here); anywhere else puts the cursor into the block so it reverts
      // to source and can be edited.
      const anchor = event.target.closest("a");
      if (anchor) {
        if (event.button === 1 && followAnchor(anchor, true)) event.preventDefault();
        return;
      }
      event.preventDefault();
      placeCursorAt(view, outer, this.from);
    });

    wrapper.addEventListener("click", event => {
      const anchor = event.target.closest("a");
      if (!anchor || event.button !== 0) return;
      event.preventDefault();
      followAnchor(anchor, event.metaKey || event.ctrlKey);
    });

    watchBlockSize(view, outer, `html:${this.html}`);
    return outer;
  }

  destroy(dom) {
    stopWatchingSize(dom);
  }

  ignoreEvent() {
    return false;
  }
}

function computeBlockDecorations(state, blocks = renderedBlocks(state)) {
  const decorations = blocks.map(block => {
    const lines = state.doc.lineAt(block.to).number - state.doc.lineAt(block.from).number + 1;
    return Decoration.replace({
      widget: block.math
        ? new MathWidget(block.tex, true, block.from, lines)
        : new RenderedBlockWidget(block.html, block.from, lines),
      block: true
    }).range(block.from, block.to);
  });
  return Decoration.set(decorations, true);
}

/** Whether `set` already shows exactly `blocks`: same places, same content. */
function showsBlocks(set, blocks) {
  if (set.size !== blocks.length) return false;
  const sorted = [...blocks].sort((a, b) => a.from - b.from || a.to - b.to);
  let index = 0;
  for (const cursor = set.iter(); cursor.value; cursor.next()) {
    const block = sorted[index++];
    const widget = cursor.value.spec.widget;
    if (cursor.from !== block.from || cursor.to !== block.to) return false;
    const same = block.math ? widget instanceof MathWidget && widget.tex === block.tex
      : widget instanceof RenderedBlockWidget && widget.html === block.html;
    if (!same) return false;
  }
  return true;
}

// A StateField, not the Live Preview ViewPlugin: CodeMirror rejects block decorations —
// and any replacement spanning a line break — coming from a plugin, because it cannot
// reconcile them with the height map it computes before plugins run. This is also why
// tables are replaced row by row rather than as one widget.
export const blockRenderDecorations = StateField.define({
  create: computeBlockDecorations,
  update(value, transaction) {
    const relevant = transaction.docChanged || transaction.selection || transaction.effects.some(effect =>
      effect.is(setBlockRenders) || effect.is(setMathReady) || effect.is(setEditorFocus));
    if (!relevant) return value;
    const blocks = renderedBlocks(transaction.state);
    // Typing outside the blocks, or moving the cursor without revealing one, keeps the set
    // (shifted by the edit): CodeMirror then sees unchanged decorations and compares nothing,
    // where a new set would make it walk every block of the document on each keystroke.
    const kept = transaction.docChanged ? value.map(transaction.changes) : value;
    return showsBlocks(kept, blocks) ? kept : computeBlockDecorations(transaction.state, blocks);
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

function coveredBy(ranges, from, to) {
  return ranges.some(range => range.from <= from && range.to >= to);
}

function overlaps(ranges, from, to) {
  return ranges.some(range => range.from < to && range.to > from);
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

/** Opens the note a link names, in a new tab when `newTab`. */
export function openNoteLink(target, newTab) {
  if (newTab && window.javaEditor?.openNoteInNewTab) window.javaEditor.openNoteInNewTab(target);
  else window.javaEditor?.openNote(target);
}

/** Follows a Markdown link: a note's in a new tab when `newTab`, an external one in the browser. */
export function openMarkdownLink(target, newTab) {
  if (newTab && window.javaEditor?.openMarkdownLinkInNewTab) window.javaEditor.openMarkdownLinkInNewTab(target);
  else window.javaEditor?.openMarkdownLink(target);
}

/** Follows a link of a rendered block; false when it is not one the app follows. */
function followAnchor(anchor, newTab) {
  const href = anchor.getAttribute("href") || "";
  if (href.startsWith("jylos://open-note/")) {
    const target = anchor.getAttribute("data-target")
      || decodeURIComponent(href.slice("jylos://open-note/".length));
    const subpath = anchor.getAttribute("data-subpath");
    openNoteLink(subpath ? `${target}#${subpath}` : target, newTab);
    return true;
  }
  if (/^https?:/i.test(href)) {
    window.javaEditor?.openExternal(href);
    return true;
  }
  return false;
}

function wikiReplacement(match, from) {
  const embed = match.startsWith("!");
  const inner = match.slice(embed ? 3 : 2, -2);
  const [targetPart, aliasPart] = inner.split("|", 2);
  // The heading or block stays in the link, so the note opens there.
  const target = targetPart.trim();
  if (embed && IMAGE_EXTENSION.test(target.split("#", 1)[0])) {
    return Decoration.replace({
      widget: new ImageWidget(target, (aliasPart || "").trim(), from)
    }).range(from, from + match.length);
  }
  const label = (aliasPart || targetPart).trim();
  const className = embed ? "cm-live-embed" : "cm-live-wikilink";
  const text = embed ? `▣ ${label}` : label;
  return Decoration.replace({
    widget: new TextWidget(text, className, from, newTab => openNoteLink(target, newTab), target)
  }).range(from, from + match.length);
}

function linkReplacement(label, target, from, to) {
  return Decoration.replace({
    widget: new TextWidget(label || target, "cm-live-link", from,
      newTab => openMarkdownLink(target, newTab), target)
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
// "underline" decoration should cover.
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

/**
 * The Obsidian syntax found by pattern on one line (Lezer does not know it): wiki-links
 * and embeds, footnotes, tags, block ids and inline math. Nothing inside code; a later
 * kind never overlaps an earlier one.
 */
function lineSpans(state, tree, line) {
  const spans = [];
  const free = (from, to) => !overlaps(spans, from, to) && !inCode(tree, from);
  for (const match of line.text.matchAll(WIKI_LINK)) {
    const from = line.from + match.index;
    const to = from + match[0].length;
    if (free(from, to)) spans.push({ kind: "wiki", from, to, text: match[0] });
  }
  const definition = line.text.match(FOOTNOTE_DEF);
  if (definition && free(line.from, line.from + definition[0].length)) {
    spans.push({ kind: "footnote-def", from: line.from, to: line.from + definition[0].length, label: definition[1] });
  }
  for (const match of line.text.matchAll(FOOTNOTE_REF)) {
    const from = line.from + match.index;
    const to = from + match[0].length;
    if (free(from, to)) spans.push({ kind: "footnote", from, to, label: match[1] });
  }
  for (const match of line.text.matchAll(INLINE_FOOTNOTE)) {
    const from = line.from + match.index;
    const to = from + match[0].length;
    if (free(from, to)) spans.push({ kind: "inline-footnote", from, to });
  }
  for (const match of line.text.matchAll(TAG)) {
    const from = line.from + match.index;
    const to = from + match[0].length;
    if (free(from, to) && !inLink(tree, from)) spans.push({ kind: "tag", from, to, text: match[0] });
  }
  const blockId = line.text.match(BLOCK_ID);
  if (blockId) {
    const from = line.from + blockId.index;
    if (free(from, line.to)) spans.push({ kind: "block-id", from, to: line.to });
  }
  if (state.field(mathReadyField, false)) {
    for (const match of line.text.matchAll(INLINE_MATH)) {
      const from = line.from + match.index;
      const to = from + match[0].length;
      if (free(from, to) && !line.text.trim().startsWith("$$")) spans.push({ kind: "math", from, to, tex: match[1] });
    }
  }
  return spans;
}

function spanDecoration(state, span) {
  const numbers = span.kind.includes("footnote") ? footnoteNumbers(state.doc) : null;
  switch (span.kind) {
    case "wiki":
      return wikiReplacement(span.text, span.from);
    case "footnote": {
      const number = numbers.byLabel.get(span.label);
      return Decoration.replace({
        widget: new TextWidget(String(number ?? span.label), "cm-live-footnote", span.from)
      }).range(span.from, span.to);
    }
    case "inline-footnote":
      return Decoration.replace({
        widget: new TextWidget(String(numbers.inline.get(span.from) ?? "*"), "cm-live-footnote", span.from)
      }).range(span.from, span.to);
    case "footnote-def":
      return Decoration.replace({
        widget: new TextWidget(String(numbers.byLabel.get(span.label) ?? span.label), "cm-live-footnote-def", span.from)
      }).range(span.from, span.to);
    case "tag":
      return Decoration.replace({
        widget: new TextWidget(span.text, "cm-live-tag", span.from,
          () => window.javaEditor?.openTag?.(span.text.slice(1)))
      }).range(span.from, span.to);
    case "block-id":
      return Decoration.mark({ class: "cm-live-block-id" }).range(span.from, span.to);
    case "math":
      return Decoration.replace({ widget: new MathWidget(span.tex, false, span.from) }).range(span.from, span.to);
    default:
      return null;
  }
}

/** The node whose range decides whether a syntax mark is shown. */
function revealOwner(node) {
  const parent = node.node.parent;
  return parent ? { from: parent.from, to: parent.to } : { from: node.from, to: node.to };
}

const INLINE_MARKS = ["EmphasisMark", "StrikethroughMark", "MarkMark", "CodeMark", "CodeInfo"];
const LINE_MARKS = ["HeaderMark", "QuoteMark"];

function buildDecorations(view, layoutGeneration = 0) {
  const state = view.state;
  // Changing this attribute makes CodeMirror measure the visible lines again (see sizeObserver).
  const decorations = [Decoration.line({ attributes: { "data-layout": String(layoutGeneration) } }).range(0)];
  const replacements = [];
  const htmlTags = [];
  const renderedRanges = renderedBlocks(state);
  const tree = syntaxTree(state);

  for (const comment of commentRanges(state.doc)) {
    if (!overlaps(renderedRanges, comment.from, comment.to)) {
      decorations.push(Decoration.mark({ class: "cm-live-comment" }).range(comment.from, comment.to));
    }
  }

  for (const visible of view.visibleRanges) {
    const firstLine = state.doc.lineAt(visible.from).number;
    const lastLine = state.doc.lineAt(visible.to).number;
    for (let number = firstLine; number <= lastLine; number++) {
      const line = state.doc.line(number);
      for (const span of lineSpans(state, tree, line)) {
        if (overlaps(renderedRanges, span.from, span.to)) continue;
        // A block id stays dimmed text even under the cursor; the rest shows its source there.
        if (span.kind !== "block-id" && revealed(state, span.from, span.to)) {
          replacements.push({ from: span.from, to: span.to });
          continue;
        }
        const decoration = spanDecoration(state, span);
        if (decoration) {
          decorations.push(decoration);
          replacements.push({ from: span.from, to: span.to });
        }
      }
    }
  }

  for (const visible of view.visibleRanges) {
    tree.iterate({
      from: state.doc.lineAt(visible.from).from,
      to: state.doc.lineAt(visible.to).to,
      enter(node) {
        if (coveredBy(replacements, node.from, node.to)) return false;
        if (coveredBy(renderedRanges, node.from, node.to)) return false;

        const source = state.sliceDoc(node.from, node.to);
        if (/^ATXHeading[1-6]$/.test(node.name)) {
          decorations.push(lineDecoration(view, node.from, `cm-live-heading cm-live-${node.name.toLowerCase()}`));
        } else if (node.name === "Blockquote") {
          decorations.push(...lineDecorationsForRange(view, node.from, node.to, "cm-live-blockquote"));
        } else if (node.name === "FencedCode") {
          decorations.push(...lineDecorationsForRange(view, node.from, node.to, "cm-live-codeblock"));
        } else if (node.name === "HTMLTag") {
          if (OPEN_UNDERLINE_TAG.test(source)) {
            htmlTags.push({ from: node.from, to: node.to, open: true });
          } else if (CLOSE_UNDERLINE_TAG.test(source)) {
            htmlTags.push({ from: node.from, to: node.to, open: false });
          }
        }

        if (node.name === "Image" && !revealed(state, node.from, node.to)) {
          const image = linkParts(state, node, true);
          if (image) {
            decorations.push(Decoration.replace({
              widget: new ImageWidget(image.target, image.label, node.from)
            }).range(node.from, node.to));
            replacements.push({ from: node.from, to: node.to });
            return false;
          }
        }

        if (node.name === "Table") {
          if (revealed(state, node.from, node.to)) return false;
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
          if (lineRevealed(state, node.from)) return;
          decorations.push(Decoration.replace({
            widget: new TextWidget("", "cm-live-horizontal-rule", node.from)
          }).range(node.from, node.to));
          replacements.push({ from: node.from, to: node.to });
          return false;
        }

        if (node.name === "TaskMarker") {
          if (revealed(state, node.from, node.to)) return;
          decorations.push(Decoration.replace({
            widget: new TaskWidget(node.from, /x/i.test(source))
          }).range(node.from, node.to));
          replacements.push({ from: node.from, to: node.to });
          return false;
        }

        if (node.name === "Link") {
          if (revealed(state, node.from, node.to)) return;
          const link = linkParts(state, node, false);
          if (link) {
            decorations.push(linkReplacement(link.label, link.target, node.from, node.to));
            replacements.push({ from: node.from, to: node.to });
            return false;
          }
        }

        if (node.name === "Autolink" || node.name === "URL") {
          if (revealed(state, node.from, node.to)) return;
          const target = source.replace(/^<|>$/g, "");
          if (/^https?:\/\//i.test(target)) {
            decorations.push(linkReplacement(target, target, node.from, node.to));
            replacements.push({ from: node.from, to: node.to });
            return false;
          }
        }

        if (node.name === "ListMark" && /^[-+*]$/.test(source)) {
          if (lineRevealed(state, node.from)) return;
          decorations.push(Decoration.replace({
            widget: new TextWidget("•", "cm-live-list-marker", node.from)
          }).range(node.from, node.to));
          replacements.push({ from: node.from, to: node.to });
          return false;
        }

        if (LINE_MARKS.includes(node.name)) {
          if (!lineRevealed(state, node.from)) {
            decorations.push(Decoration.replace({}).range(node.from, node.to));
            replacements.push({ from: node.from, to: node.to });
          }
          return;
        }

        if (INLINE_MARKS.includes(node.name)) {
          const owner = revealOwner(node);
          if (revealed(state, owner.from, owner.to)) return;
          if (node.name === "CodeInfo") {
            // The fences are hidden: say which language the block is in.
            decorations.push(Decoration.replace({
              widget: new TextWidget(source.trim(), "cm-live-code-lang", node.from)
            }).range(node.from, node.to));
          } else {
            decorations.push(Decoration.replace({}).range(node.from, node.to));
          }
          replacements.push({ from: node.from, to: node.to });
        }
      }
    });
  }

  for (const pair of pairUnderlineTags(htmlTags)) {
    if (pair.open.to < pair.close.from) {
      decorations.push(Decoration.mark({ class: "cm-live-underline" }).range(pair.open.to, pair.close.from));
    }
    if (!revealed(state, pair.open.from, pair.close.to)) {
      decorations.push(Decoration.replace({}).range(pair.open.from, pair.open.to));
      decorations.push(Decoration.replace({}).range(pair.close.from, pair.close.to));
    }
  }

  return Decoration.set(decorations, true);
}

const REVEAL_NODES = new Set(["Emphasis", "StrongEmphasis", "Strikethrough", "Mark", "InlineCode", "Link",
  "Image", "Autolink", "URL", "TaskMarker", "Table", "FencedCode", "HTMLTag"]);

/**
 * What the selection reveals, as a string: the lines it is on and every element or
 * pattern span it touches. Two positions with the same key show the same decorations,
 * so moving the cursor through plain text does not rebuild them.
 */
function revealKey(state, tree) {
  if (!state.field(editorFocusField, false)) return "unfocused";
  const parts = [];
  for (const range of state.selection.ranges) {
    const first = state.doc.lineAt(range.from);
    const last = state.doc.lineAt(range.to);
    parts.push(`L${first.number}-${last.number}`);
    for (let number = first.number; number <= last.number; number++) {
      const line = state.doc.line(number);
      for (const span of lineSpans(state, tree, line)) {
        if (range.to >= span.from && range.from <= span.to) parts.push(`s${span.from}`);
      }
      tree.iterate({
        from: line.from,
        to: line.to,
        enter(node) {
          if (REVEAL_NODES.has(node.name) && range.to >= node.from && range.from <= node.to) {
            parts.push(`${node.name}${node.from}`);
          }
        }
      });
    }
  }
  return parts.join(",");
}

// Native `selectionchange` fires continuously while the user drags a text selection,
// and each tick reaches this plugin as a selection-only update. Rebuilding decorations
// mid-drag swaps widgets under the pointer and resets the browser's selection anchor, so
// selection-only updates rebuild only when what the selection reveals changes, and not
// at all while a mouse button is held: the release rebuilds once.
const livePreviewPlugin = ViewPlugin.fromClass(class {
  constructor(view) {
    this.view = view;
    this.layoutGeneration = 0;
    this.decorations = buildDecorations(view);
    this.revealKey = revealKey(view.state, syntaxTree(view.state));
    this.dragging = false;
    this.onMouseUp = () => {
      if (!this.dragging) return;
      this.dragging = false;
      this.rebuildIfRevealChanged();
    };
    view.dom.ownerDocument.addEventListener("mouseup", this.onMouseUp);
  }

  rebuildIfRevealChanged() {
    const key = revealKey(this.view.state, syntaxTree(this.view.state));
    if (key !== this.revealKey) {
      this.revealKey = key;
      this.decorations = buildDecorations(this.view, this.layoutGeneration);
      // Ask CodeMirror to redraw with the new decorations.
      this.view.dispatch({ effects: [] });
    }
  }

  update(update) {
    const fieldChanged = field => update.startState.field(field, false) !== update.state.field(field, false);
    const resized = update.transactions.some(tr => tr.effects.some(effect => effect.is(layoutChanged)));
    if (resized) this.layoutGeneration++;
    if (resized || update.docChanged || update.viewportChanged || fieldChanged(blockRenderField)
        || fieldChanged(editorFocusField) || fieldChanged(mathReadyField)) {
      this.decorations = buildDecorations(update.view, this.layoutGeneration);
      this.revealKey = revealKey(update.state, syntaxTree(update.state));
      return;
    }
    if (update.selectionSet && !this.dragging) {
      const key = revealKey(update.state, syntaxTree(update.state));
      if (key !== this.revealKey) {
        this.decorations = buildDecorations(update.view, this.layoutGeneration);
        this.revealKey = key;
      }
    }
  }

  destroy() {
    this.view.dom.ownerDocument.removeEventListener("mouseup", this.onMouseUp);
    sizeObservers.get(this.view)?.disconnect();
    sizeObservers.delete(this.view);
  }
}, {
  decorations: value => value.decorations,
  eventHandlers: {
    mousedown(event) {
      if (event.button === 0) this.dragging = true;
    }
  }
});

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
  ".cm-live-atxheading1": { fontSize: "1.6em", fontWeight: "700" },
  ".cm-live-atxheading2": { fontSize: "1.4em", fontWeight: "700" },
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
  // border-box: the height reserved for an image that is loading again is its whole box.
  ".cm-live-image": { display: "block", boxSizing: "border-box", maxWidth: "min(100%, 720px)", margin: "0", padding: ".65em 0" },
  ".cm-live-image img": { display: "block", maxWidth: "100%", maxHeight: "520px", borderRadius: "8px", objectFit: "contain" },
  ".cm-live-image-fallback": { display: "inline-block", padding: "8px 10px", color: "var(--jylos-muted)", background: "var(--jylos-panel)", border: "1px solid var(--jylos-border)", borderRadius: "6px" },
  // The display above would otherwise override the hidden attribute, and the file name would
  // show under every image that did load.
  ".cm-live-image-fallback[hidden]": { display: "none" },
  ".cm-live-table-row": { display: "inline-grid", width: "100%", color: "inherit", verticalAlign: "top" },
  ".cm-live-table-cell": { minWidth: "0", padding: "6px 9px", border: "1px solid var(--jylos-border)", overflowWrap: "anywhere" },
  ".cm-live-table-header .cm-live-table-cell": { background: "var(--jylos-panel)", fontWeight: "700" },
  ".cm-live-table-delimiter-line": { height: "0", lineHeight: "0", overflow: "hidden" },
  // Baseline styling for whatever markup a plugin block renderer returns. These target
  // the extension point, not any particular plugin: a renderer that emits plain semantic
  // HTML already reads correctly, and one with its own classes styles them itself.
  ".cm-live-block-render-outer": { display: "block", padding: ".5em 0", margin: "0" },
  ".cm-live-block-render": {
    display: "block",
    margin: "0",
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
  ".cm-live-block-render p": { margin: ".35em 0" },
  ".cm-live-block-render a": { color: "var(--jylos-accent)", cursor: "pointer" },
  ".cm-live-comment": { color: "var(--jylos-muted)", opacity: ".6" },
  ".cm-live-block-id": { color: "var(--jylos-muted)", opacity: ".5", fontSize: ".85em" },
  ".cm-live-footnote": { verticalAlign: "super", fontSize: ".75em", color: "var(--jylos-accent)", padding: "0 1px" },
  ".cm-live-footnote-def": { fontSize: ".8em", color: "var(--jylos-muted)", paddingRight: "4px" },
  ".cm-live-footnote-def::after": { content: "'.'" },
  ".cm-live-tag": { color: "var(--jylos-accent)", background: "color-mix(in srgb, var(--jylos-accent) 12%, transparent)", borderRadius: "10px", padding: "0 7px", cursor: "pointer" },
  ".cm-live-code-lang": { float: "right", fontFamily: UI_FONT_FAMILY, fontSize: ".75em", color: "var(--jylos-muted)", padding: "0 6px" },
  ".cm-live-math": { cursor: "text" },
  // No vertical margins on anything CodeMirror measures as a block: it measures with
  // getBoundingClientRect, which leaves margins out, and every click below would land on
  // the wrong line. Padding gives the same room.
  ".cm-live-math-block": { display: "block", textAlign: "center", padding: ".5em 0" },
  // Callouts and embeds rendered by the host (same markup as the reading view).
  ".cm-live-block-render .callout, .cm-live-block-render .embed": { margin: "0" },
  ".cm-live-block-render .callout-title": { display: "flex", alignItems: "center", gap: "6px", fontWeight: "600", color: "rgb(var(--callout-rgb))" },
  ".cm-live-block-render .callout-icon": { display: "inline-flex" },
  ".cm-live-block-render summary.callout-title": { cursor: "pointer", listStyle: "none" },
  ".cm-live-block-render .callout-content > :first-child": { marginTop: ".3em" },
  ".cm-live-block-render .callout-content > :last-child": { marginBottom: "0" },
  ".cm-live-block-render .embed-title": { display: "block", fontSize: ".8em", fontWeight: "600", color: "var(--jylos-accent)", textDecoration: "none", textTransform: "uppercase", marginBottom: "4px" },
  ".cm-live-block-render .embed-note": { color: "var(--jylos-muted)", fontStyle: "italic", fontSize: ".85em" },
  ".cm-live-block-render.cm-live-callout": { padding: "10px 14px", border: "none", borderLeft: "3px solid rgb(var(--callout-rgb))", background: "rgba(var(--callout-rgb), .1)" },
  ".cm-live-block-render.cm-live-callout .callout": { padding: "0", border: "none", background: "none" },
  ".cm-live-callout-blue": { "--callout-rgb": "8, 109, 221" },
  ".cm-live-callout-cyan": { "--callout-rgb": "0, 191, 188" },
  ".cm-live-callout-green": { "--callout-rgb": "8, 185, 78" },
  ".cm-live-callout-orange": { "--callout-rgb": "236, 117, 0" },
  ".cm-live-callout-red": { "--callout-rgb": "233, 49, 71" },
  ".cm-live-callout-purple": { "--callout-rgb": "120, 82, 238" },
  ".cm-live-callout-gray": { "--callout-rgb": "158, 158, 158" },
  ".cm-live-block-render > :first-child": { marginTop: "0" },
  ".cm-live-block-render > :last-child": { marginBottom: "0" }
});

export function livePreview() {
  // blockRenderDecorations is presentation, so it belongs here rather than in the base
  // state: source mode must still show a plugin-claimed block as its raw text. The data
  // it reads (blockRenderField) stays in the base state so toggling modes keeps it.
  return [
    editorFocusField,
    mathReadyField,
    imageSources,
    focusTracker,
    livePreviewPlugin,
    blockRenderDecorations,
    livePreviewTheme
  ];
}

// Exported for tests only, so they can look up the plugin instance via
// `view.plugin(livePreviewPlugin)` and assert on decoration rebuild behavior.
export { livePreviewPlugin };
