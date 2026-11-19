// What [[ suggests while a link is typed: the vault's notes (every one, with its folder, even
// when two share a name), their aliases, and after "#" the headings of the note named (or of
// this one, for [[#). Accents and case do not matter, and the list is ranked: names that start
// with what is typed, then a word that does, then names that contain it, then the ones that
// have its letters in order (initials such as "pj").

/** A note the links can point to: its name, its folder ("" at the root), the shortest link
 *  that names it unambiguously, and its aliases. */
let notes = [];

export function setLinkNotes(list) {
  notes = Array.isArray(list)
    ? list.filter(entry => entry && typeof entry.name === "string").map(entry => ({
      name: entry.name,
      folder: typeof entry.folder === "string" ? entry.folder : "",
      link: typeof entry.link === "string" && entry.link ? entry.link : entry.name,
      aliases: Array.isArray(entry.aliases) ? entry.aliases.filter(alias => typeof alias === "string" && alias) : []
    }))
    : [];
}

/** Titles alone (each one its own link), as the editor was given them before. */
export function setLinkTitles(titles) {
  setLinkNotes((Array.isArray(titles) ? titles : []).filter(title => typeof title === "string")
    .map(title => ({ name: title, folder: "", link: title, aliases: [] })));
}

/** Lower case without accents, keeping where each folded character came from. */
function fold(text) {
  let folded = "";
  const from = [];
  for (let i = 0; i < text.length; i++) {
    const plain = text[i].normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
    for (let j = 0; j < plain.length; j++) {
      folded += plain[j];
      from.push(i);
    }
  }
  return { folded, from };
}

const WORD_START = /[\s\-_/.(]/;

/**
 * How well `text` matches `query` (already folded): its tier (0 the same, 1 starts with it,
 * 2 a word starts with it, 3 contains it, 4 has its letters in order) and the ranges of `text`
 * that match, or null when it does not.
 */
function rank(text, query) {
  const { folded, from } = fold(text);
  const span = (start, length) => [from[start], from[start + length - 1] + 1];
  if (!query) return { tier: 1, ranges: [] };
  if (folded === query) return { tier: 0, ranges: span(0, query.length) };
  if (folded.startsWith(query)) return { tier: 1, ranges: span(0, query.length) };
  for (let at = folded.indexOf(query, 1); at > 0; at = folded.indexOf(query, at + 1)) {
    if (WORD_START.test(folded[at - 1])) return { tier: 2, ranges: span(at, query.length) };
  }
  const inside = folded.indexOf(query);
  if (inside >= 0) return { tier: 3, ranges: span(inside, query.length) };
  const ranges = [];
  let at = 0;
  for (const ch of query) {
    const found = folded.indexOf(ch, at);
    if (found < 0) return null;
    ranges.push(from[found], from[found] + 1);
    at = found + 1;
  }
  return { tier: 4, ranges };
}

/** Ranks `candidates` ({ label, folder?, match? }) against `typed`, best first. */
function ranked(candidates, typed, matchOn = candidate => candidate.label) {
  const query = fold(typed).folded;
  const out = [];
  for (const candidate of candidates) {
    const result = rank(matchOn(candidate), query);
    if (result) out.push({ ...candidate, tier: result.tier, ranges: matchOn(candidate) === candidate.label ? result.ranges : [] });
  }
  if (!query) return out; // as given: by name
  return out.sort((a, b) => a.tier - b.tier
    || a.label.length - b.label.length
    || a.label.localeCompare(b.label)
    || (a.folder || "").localeCompare(b.folder || ""));
}

const HEADING = /^ {0,3}(#{1,6})[ \t]+(.*?)[ \t#]*$/;
const FENCE = /^ {0,3}(```|~~~)/;

/** The headings of `text`, in order; a "#" line inside a code block is not one. */
export function headingsIn(text) {
  const headings = [];
  let fence = null;
  for (const line of text.split("\n")) {
    const opening = FENCE.exec(line);
    if (opening) {
      if (!fence) fence = opening[1];
      else if (opening[1] === fence) fence = null;
      continue;
    }
    const heading = !fence && HEADING.exec(line);
    if (heading && heading[2].trim()) headings.push(heading[2].trim());
  }
  return headings;
}

function headingsOf(name, state) {
  if (!name) return headingsIn(state.doc.toString());
  try {
    const answer = window.javaEditor?.headingsOf?.(name);
    const list = typeof answer === "string" ? JSON.parse(answer) : [];
    return Array.isArray(list) ? list.filter(title => typeof title === "string") : [];
  } catch (_error) {
    return [];
  }
}

/** Writes `[[inner]]` over the link being typed, from its `[[` to its `]]` when there is one. */
function writeLink(view, linkFrom, to, inner) {
  if (view.state.readOnly) return;
  const linkTo = view.state.sliceDoc(to, to + 2) === "]]" ? to + 2 : to;
  const raw = `[[${inner}]]`;
  const inserted = window.javaEditor?.transformInsertion(raw) || raw;
  view.dispatch({
    changes: { from: linkFrom, to: linkTo, insert: inserted },
    selection: { anchor: linkFrom + inserted.length },
    userEvent: "input.complete"
  });
}

const matchOf = completion => completion.ranges || [];

/** The completion source for `[[`: notes and aliases, or headings after "#". */
export function wikiLinkCompletion(context) {
  const before = context.matchBefore(/\[\[[^\]\[\n]*$/);
  if (!before) return null;
  const linkFrom = before.from;
  const typed = before.text.slice(2);
  if (typed.includes("|")) return null; // the text the link shows is free
  const hash = typed.indexOf("#");
  if (hash >= 0) {
    const name = typed.slice(0, hash);
    const heading = typed.slice(hash + 1);
    if (heading.startsWith("^")) return null; // a block id is not suggested
    const options = ranked(headingsOf(name, context.state).map(title => ({ label: title })), heading)
      .map(option => ({
        label: option.label,
        type: "text",
        ranges: option.ranges,
        apply: (view, _completion, _from, to) => writeLink(view, linkFrom, to, `${name}#${option.label}`)
      }));
    return { from: linkFrom + 2 + hash + 1, options, filter: false, getMatch: matchOf };
  }
  const candidates = notes.map(entry => ({ label: entry.name, folder: entry.folder, entry }));
  if (typed) {
    for (const entry of notes) {
      for (const alias of entry.aliases) candidates.push({ label: alias, folder: entry.folder, entry, alias });
    }
  }
  // With a "/" what is typed is a path: it is matched against the folder and the name.
  const byPath = typed.includes("/");
  const matchOn = candidate => byPath ? (candidate.folder ? `${candidate.folder}/${candidate.label}` : candidate.label)
    : candidate.label;
  const options = ranked(candidates, typed, matchOn)
    .filter(option => !(byPath && option.alias))
    .map(option => ({
      label: option.label,
      detail: option.alias ? `→ ${option.entry.name}` : option.folder || undefined,
      type: "text",
      ranges: option.ranges,
      apply: (view, _completion, _from, to) =>
        writeLink(view, linkFrom, to, option.alias ? `${option.entry.link}|${option.alias}` : option.entry.link)
    }));
  return { from: linkFrom + 2, options, filter: false, getMatch: matchOf };
}
