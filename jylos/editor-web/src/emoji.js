// Emoji in JavaFX's WebKit.
//
// The engine draws a colour emoji only when the emoji sits alone in its own element
// and that element asks for an emoji font. Next to other text in the same node it
// comes out garbled, and a second emoji in the same element is not drawn at all.
// Sequences (skin tones, ZWJ families, flags, keycaps) are not shaped: only their
// first character shows. Where the engine has no colour emoji at all, every emoji
// is an image rasterised by Java instead.

// Matches MarkdownPreview.java's EMOJI_RUN exactly (same code point ranges), so the
// editor and the reading view agree on what counts as an emoji.
export const EMOJI_RUN = /[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{2300}-\u{23FF}\u{2190}-\u{21FF}\u{FE00}-\u{FE0F}\u{20E3}\u{200D}\u{1F1E6}-\u{1F1FF}\u{1F3FB}-\u{1F3FF}]+/gu;

const ZWJ = 0x200D;
const isRegionalIndicator = cp => cp >= 0x1F1E6 && cp <= 0x1F1FF;
const isModifier = cp => cp >= 0x1F3FB && cp <= 0x1F3FF;
const extendsPrevious = cp => cp === 0xFE0E || cp === 0xFE0F || cp === 0x20E3 || cp === ZWJ || isModifier(cp);

/**
 * Splits an emoji run into the emoji it shows: a character plus its variation
 * selector, skin tone or keycap mark, characters joined by ZWJ, and flags (pairs of
 * regional indicators). Same rules as MarkdownPreview.emojiClusters.
 */
export function emojiClusters(run) {
  const clusters = [];
  let current = "";
  let joinNext = false;
  let openFlag = false;
  for (const ch of run) {
    const cp = ch.codePointAt(0);
    const pairsFlag = openFlag && isRegionalIndicator(cp);
    if (current && (joinNext || extendsPrevious(cp) || pairsFlag)) {
      current += ch;
    } else {
      if (current) clusters.push(current);
      current = ch;
    }
    joinNext = cp === ZWJ;
    openFlag = isRegionalIndicator(cp) && !pairsFlag;
  }
  if (current) clusters.push(current);
  return clusters;
}

const EMOJI_PRESENTATION = /^\p{Emoji_Presentation}$/u;
const EMOJI = /^\p{Emoji}$/u;

/**
 * "colour": one emoji the engine can draw in colour (alone in its own element);
 * "text": a symbol shown as text by default, such as ↩ or ✔;
 * "sequence": anything the engine cannot draw whole, kept as an image.
 */
export function emojiKind(cluster) {
  const chars = [...cluster];
  if (chars.length === 1) {
    return EMOJI_PRESENTATION.test(chars[0]) ? "colour" : "text";
  }
  if (chars.length === 2 && chars[1] === "️" && EMOJI.test(chars[0])) {
    return "colour";
  }
  return "sequence";
}

let colourSupport;

/**
 * Whether this engine draws emoji in colour, found by drawing one on a canvas.
 * window.__jylosColourEmoji overrides it: Java sets it to false off macOS, and tests set it
 * (jsdom has no canvas).
 */
export function colourEmojiSupported() {
  if (typeof window !== "undefined" && typeof window.__jylosColourEmoji === "boolean") {
    return window.__jylosColourEmoji;
  }
  if (colourSupport === undefined) {
    colourSupport = probeColourEmoji();
  }
  return colourSupport;
}

function probeColourEmoji() {
  try {
    const canvas = document.createElement("canvas");
    canvas.width = 40;
    canvas.height = 40;
    const g = canvas.getContext("2d");
    if (!g) return false;
    g.font = '32px "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif';
    g.textBaseline = "top";
    g.fillText("\u{1F600}", 2, 2);
    const d = g.getImageData(0, 0, 40, 40).data;
    let coloured = 0;
    for (let i = 0; i < d.length; i += 4) {
      if (d[i + 3] > 0 && (Math.abs(d[i] - d[i + 1]) > 40 || Math.abs(d[i + 1] - d[i + 2]) > 40)) coloured++;
    }
    return coloured > 20;
  } catch (e) {
    return false;
  }
}

/** The font stack for an emoji drawn as text; it must come first for the engine to pick it. */
export const EMOJI_FONT_FAMILY = '"Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif';
