package com.example.jylos.plugin.builtin.publish;

/**
 * Static HTML/CSS/JS templates embedded as text blocks. No resource-file
 * bundling mechanism exists for a plugin JAR (only {@code .java} sources under a
 * bundle directory get compiled and packed, see docs/PLUGINS.md) — this keeps
 * every asset the exported site needs directly in the compiled plugin, no
 * separate resource-copy step to keep in sync.
 *
 * <p>The generated site is fully self-contained: no CDN, no external fonts, no
 * network access once published. That matters for a "publish anywhere" export —
 * a CDN script blocked by a viewer's network, or a broken CDN link years later,
 * would silently break every page.</p>
 */
final class PublishTemplates {

    private PublishTemplates() {
    }

    /**
     * One HTML page. {@code depthPrefix} is {@code "../".repeat(depth)} — the
     * relative path back to the export root from wherever this page lives (root
     * pages pass {@code ""}) — so the same template works for {@code index.html}
     * and for a note buried three folders deep under {@code notes/}.
     *
     * <p>{@code tocHtml} is the note's own "On this page" outline (empty string
     * for none — index.html and graph.html always pass empty, and the right-hand
     * pane is simply omitted rather than rendered blank). {@code pageHref} is
     * this page's own path relative to the export root (e.g. {@code
     * "notes/books/Dune.html"}, or {@code "index.html"}) — written onto
     * {@code <body>} as a data attribute purely for assets/nav.js to know which
     * sidebar entry is "this page", for highlighting. {@code pageFolderPath} is
     * that note's folder path using the SAME segment-join convention as the
     * sidebar tree's own {@code folder.path} values (e.g. {@code "books/scifi"},
     * or {@code ""} for a root-level note, index.html or graph.html) — kept as
     * its own explicit parameter, computed once in {@link VaultExporter} from
     * the same folder-path map the sidebar tree itself is built from, rather
     * than having assets/nav.js re-derive it by parsing {@code pageHref}'s
     * {@code notes/...} directory structure (which uses a different convention
     * — an output-layout detail the sidebar's data has no reason to know about).
     * Used only to keep a note's ancestor folders expanded in the sidebar.</p>
     *
     * <p>{@code siteTitle} is the header's home-link label — set once for the
     * whole site via the publish configuration dialog (default {@code "Jylos
     * Vault"} if left blank there), not this page's own {@code title}.</p>
     */
    static String page(String title, String depthPrefix, String bodyHtml, String tocHtml, String pageHref,
            String pageFolderPath, String siteTitle) {
        String aside = tocHtml == null || tocHtml.isEmpty()
                ? ""
                : "<aside class=\"toc-pane\">" + tocHtml + "</aside>";
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <link rel="stylesheet" href="%sassets/style.css">
                <script>(function () {
                  try {
                    var t = localStorage.getItem('jylos-theme');
                    if (t === 'light' || t === 'dark') document.documentElement.setAttribute('data-theme', t);
                  } catch (e) { /* localStorage unavailable — falls back to prefers-color-scheme */ }
                })();</script>
                </head>
                <body data-page-href="%s" data-page-folder="%s" data-depth-prefix="%s">
                <header class="site-header">
                <button type="button" id="jylos-menu-toggle" class="menu-toggle" aria-label="Toggle navigation">☰</button>
                <a class="site-title" href="%sindex.html">%s</a>
                <nav class="site-nav">
                <a href="%sindex.html">Notes</a><span class="nav-sep" aria-hidden="true"></span><a href="%sgraph.html">Graph</a>
                <button type="button" id="jylos-theme-toggle" class="theme-toggle" aria-label="Toggle light/dark theme">◐</button>
                </nav>
                </header>
                <div class="layout">
                <nav id="jylos-sidebar" class="sidebar" aria-label="Notes"></nav>
                <div class="sidebar-backdrop" id="jylos-sidebar-backdrop"></div>
                <main class="content">
                %s
                </main>
                %s
                </div>
                <footer class="site-footer">Published with Jylos</footer>
                <script src="%sassets/nav-data.js"></script>
                <script src="%sassets/nav.js"></script>
                </body>
                </html>
                """.formatted(escapeHtml(title), depthPrefix, escapeHtml(pageHref), escapeHtml(pageFolderPath),
                depthPrefix, depthPrefix, escapeHtml(siteTitle == null || siteTitle.isBlank() ? "Jylos Vault" : siteTitle),
                depthPrefix, depthPrefix, bodyHtml, aside, depthPrefix, depthPrefix);
    }

    static String css() {
        return """
                :root {
                  color-scheme: light dark;
                  --bg: #ffffff;
                  --bg-sidebar: #fafafa;
                  --fg: #1a1a1a;
                  --muted: #6b7280;
                  --border: #e5e7eb;
                  --accent: #5563d6;
                  --accent-bg: rgba(85, 99, 214, 0.1);
                  --code-bg: #f4f4f5;
                  --sidebar-width: 260px;
                  --toc-width: 220px;
                }
                /* Dark by system preference, unless the manual toggle (assets/nav.js,
                   persisted in localStorage as 'jylos-theme') overrides it either way —
                   :not([data-theme="light"]) lets an explicit light choice win over a
                   dark system, and the plain [data-theme="dark"] block below lets an
                   explicit dark choice apply even on a light system. */
                @media (prefers-color-scheme: dark) {
                  :root:not([data-theme="light"]) {
                    --bg: #16161a;
                    --bg-sidebar: #1b1b20;
                    --fg: #e5e5e8;
                    --muted: #9aa0ac;
                    --border: #2b2b31;
                    --accent: #8b93f0;
                    --accent-bg: rgba(139, 147, 240, 0.14);
                    --code-bg: #202024;
                  }
                }
                :root[data-theme="dark"] {
                  --bg: #16161a;
                  --bg-sidebar: #1b1b20;
                  --fg: #e5e5e8;
                  --muted: #9aa0ac;
                  --border: #2b2b31;
                  --accent: #8b93f0;
                  --accent-bg: rgba(139, 147, 240, 0.14);
                  --code-bg: #202024;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  background: var(--bg);
                  color: var(--fg);
                  font: 16px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                }

                .site-header {
                  display: flex;
                  align-items: center;
                  gap: 0.75rem;
                  padding: 0.7rem 1.25rem;
                  border-bottom: 1px solid var(--border);
                }
                .menu-toggle {
                  display: none;
                  flex: none;
                  background: none;
                  border: 1px solid var(--border);
                  border-radius: 6px;
                  color: var(--fg);
                  font-size: 1rem;
                  line-height: 1;
                  padding: 0.3rem 0.55rem;
                  cursor: pointer;
                }
                .site-title {
                  font-weight: 700;
                  color: var(--fg);
                  text-decoration: none;
                  flex: 1 1 auto;
                  min-width: 0;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: nowrap;
                }
                .site-nav { margin-left: auto; display: flex; align-items: center; flex: none; }
                .site-nav a { color: var(--muted); text-decoration: none; margin-left: 0.9rem; }
                .site-nav a:hover { color: var(--accent); }
                /* A CSS-drawn dot instead of a "·" text glyph — flex align-items:center
                   places it dead in the middle of the row regardless of font metrics; the
                   character glyph sat at whatever height its font drew it at, which on most
                   fonts is visibly off-center against the link text next to it. */
                .nav-sep {
                  width: 4px;
                  height: 4px;
                  margin-left: 0.9rem;
                  border-radius: 50%;
                  background: var(--muted);
                  flex: none;
                }
                .theme-toggle {
                  margin-left: 0.9rem;
                  background: none;
                  border: 1px solid var(--border);
                  border-radius: 6px;
                  color: var(--muted);
                  font-size: 0.95rem;
                  line-height: 1;
                  padding: 0.25rem 0.5rem;
                  cursor: pointer;
                }
                .theme-toggle:hover { color: var(--accent); border-color: var(--accent); }

                .layout {
                  display: flex;
                  align-items: flex-start;
                  width: 100%;
                }
                .sidebar {
                  width: var(--sidebar-width);
                  flex: none;
                  position: sticky;
                  top: 0;
                  max-height: 100vh;
                  overflow-y: auto;
                  background: var(--bg-sidebar);
                  padding: 1rem 0.75rem 2rem;
                  border-right: 1px solid var(--border);
                }
                .sidebar-backdrop { display: none; }
                .content {
                  flex: 1 1 auto;
                  min-width: 0;
                  max-width: 46rem;
                  margin: 0 auto;
                  padding: 1.5rem 1.5rem 4rem;
                }
                .toc-pane {
                  width: var(--toc-width);
                  flex: none;
                  position: sticky;
                  top: 0;
                  max-height: 100vh;
                  overflow-y: auto;
                  padding: 1.75rem 1.25rem 2rem 0;
                  font-size: 0.85rem;
                }

                .nav-search {
                  width: 100%;
                  padding: 0.4rem 0.6rem;
                  margin-bottom: 0.75rem;
                  border: 1px solid var(--border);
                  border-radius: 6px;
                  background: var(--bg);
                  color: var(--fg);
                  font-size: 0.85rem;
                }
                .nav-tree, .nav-tree ul { list-style: none; margin: 0; padding: 0; }
                .nav-tree ul { padding-left: 0.9rem; }
                .nav-tree li { margin: 0.05rem 0; }
                .nav-tree a {
                  display: block;
                  padding: 0.2rem 0.4rem;
                  border-radius: 5px;
                  color: var(--fg);
                  text-decoration: none;
                  font-size: 0.85rem;
                  white-space: nowrap;
                  overflow: hidden;
                  text-overflow: ellipsis;
                }
                .nav-tree a:hover { background: var(--accent-bg); }
                .nav-tree a.active { background: var(--accent-bg); color: var(--accent); font-weight: 600; }
                .nav-folder-toggle {
                  display: block;
                  width: 100%;
                  text-align: left;
                  background: none;
                  border: none;
                  color: var(--muted);
                  font: inherit;
                  font-size: 0.8rem;
                  font-weight: 600;
                  text-transform: uppercase;
                  letter-spacing: 0.03em;
                  padding: 0.3rem 0.4rem;
                  cursor: pointer;
                  border-radius: 5px;
                }
                .nav-folder-toggle:hover { background: var(--accent-bg); color: var(--fg); }
                .nav-folder-toggle::before { content: "▸"; display: inline-block; width: 1em; }
                .nav-folder-toggle[aria-expanded="true"]::before { content: "▾"; }
                .nav-search-empty { color: var(--muted); font-size: 0.85rem; padding: 0.3rem 0.4rem; }

                .mini-graph {
                  margin-bottom: 1.5rem;
                }
                .mini-graph-header {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  margin-bottom: 0.4rem;
                }
                .mini-graph-header span {
                  color: var(--muted);
                  font-size: 0.75rem;
                  text-transform: uppercase;
                  letter-spacing: 0.04em;
                }
                .mini-graph-expand {
                  background: none;
                  border: none;
                  color: var(--muted);
                  cursor: pointer;
                  font-size: 0.9rem;
                  line-height: 1;
                  padding: 0.1rem 0.2rem;
                }
                .mini-graph-expand:hover { color: var(--accent); }
                #mini-graph-canvas {
                  width: 100%;
                  height: 160px;
                  display: block;
                  border: 1px solid var(--border);
                  border-radius: 8px;
                  background: var(--bg);
                  cursor: grab;
                }

                .toc-title {
                  margin: 0 0 0.5rem;
                  color: var(--muted);
                  font-size: 0.75rem;
                  text-transform: uppercase;
                  letter-spacing: 0.04em;
                }
                .toc-pane ul { list-style: none; margin: 0; padding: 0; }
                .toc-pane li a {
                  display: block;
                  padding: 0.15rem 0;
                  color: var(--muted);
                  text-decoration: none;
                }
                .toc-pane li a:hover { color: var(--accent); }
                .toc-h1 { padding-left: 0; }
                .toc-h2 { padding-left: 0.5rem; }
                .toc-h3 { padding-left: 1rem; }
                .toc-h4 { padding-left: 1.5rem; }
                .toc-h5 { padding-left: 2rem; }
                .toc-h6 { padding-left: 2.5rem; }

                .breadcrumb { margin: 0 0 0.5rem; color: var(--muted); font-size: 0.85rem; }
                .note-title { margin: 0 0 1.25rem; }
                .backlinks { margin-top: 3rem; padding-top: 1.25rem; border-top: 1px solid var(--border); }
                .backlinks h2 {
                  font-size: 0.85rem;
                  text-transform: uppercase;
                  letter-spacing: 0.04em;
                  color: var(--muted);
                  margin: 0 0 0.6rem;
                }
                .backlinks ul { list-style: none; margin: 0; padding: 0; }
                .backlinks li { padding: 0.25rem 0; }

                h1, h2, h3, h4, h5, h6 { line-height: 1.3; }
                a { color: var(--accent); }
                a.wikilink-new { color: var(--muted); text-decoration: line-through; cursor: default; }
                img { max-width: 100%; }
                pre {
                  background: var(--code-bg);
                  padding: 0.75rem 1rem;
                  overflow-x: auto;
                  border-radius: 6px;
                }
                code { background: var(--code-bg); padding: 0.1rem 0.3rem; border-radius: 4px; }
                pre code { background: none; padding: 0; }
                blockquote {
                  margin: 1rem 0;
                  padding: 0.1rem 1rem;
                  border-left: 3px solid var(--border);
                  color: var(--muted);
                }
                /* display:block (not the default table layout) so a wide table gets its
                   own horizontal scrollbar on a narrow screen instead of overflowing the
                   whole page — no wrapper element needed, commonmark emits a bare <table>. */
                table { display: block; overflow-x: auto; border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid var(--border); padding: 0.4rem 0.6rem; text-align: left; }
                input[type="checkbox"] { margin-right: 0.4rem; }
                .note-list { list-style: none; padding: 0; }
                .note-list li { padding: 0.3rem 0; border-bottom: 1px solid var(--border); }
                .note-list .folder-label {
                  display: block;
                  margin: 1.5rem 0 0.5rem;
                  color: var(--muted);
                  font-size: 0.8rem;
                  text-transform: uppercase;
                  letter-spacing: 0.04em;
                }
                #graph-canvas { width: 100%; height: 78vh; border: 1px solid var(--border); border-radius: 8px; cursor: grab; }
                .graph-hint { color: var(--muted); font-size: 0.85rem; }
                .site-footer { padding: 1rem 1.5rem 3rem; color: var(--muted); font-size: 0.85rem; }

                @media (max-width: 900px) {
                  .menu-toggle { display: inline-block; }
                  .sidebar {
                    position: fixed;
                    left: 0;
                    top: 0;
                    bottom: 0;
                    z-index: 20;
                    width: 80vw;
                    max-width: 320px;
                    transform: translateX(-100%);
                    transition: transform 0.2s ease;
                  }
                  body.sidebar-open .sidebar { transform: translateX(0); }
                  .sidebar-backdrop {
                    display: none;
                    position: fixed;
                    inset: 0;
                    background: rgba(0, 0, 0, 0.4);
                    z-index: 10;
                  }
                  body.sidebar-open .sidebar-backdrop { display: block; }
                  /* The sidebar goes off-canvas above (position: fixed, doesn't take up
                     flow space) — .layout only has .content and .toc-pane left to lay
                     out, and stacking those instead of hiding the graph/outline keeps
                     them reachable on a phone rather than silently dropping them. */
                  .layout { flex-direction: column; }
                  .toc-pane {
                    width: 100%;
                    position: static;
                    max-height: none;
                    padding: 0 1.25rem 2rem;
                  }
                  #mini-graph-canvas { height: 200px; }
                }

                @media (max-width: 480px) {
                  .site-header { padding: 0.6rem 0.85rem; }
                  .content { padding: 1rem 1rem 3rem; }
                  .toc-pane { padding: 0 1rem 2rem; }
                }
                """;
    }

    /**
     * Renders the persistent sidebar (folder tree + title search) from
     * {@code window.__JYLOS_NAV__} (written once to assets/nav-data.js by {@link
     * NavTree}, loaded by every page via a plain {@code <script src>} — not
     * {@code fetch()}, which a browser rejects as cross-origin when the site is
     * opened directly as a file rather than served over http(s)). Also wires the
     * mobile hamburger toggle. Collapsed/expanded folder state is remembered per
     * viewer in localStorage — wrapped in try/catch since some browsers refuse
     * that in private-browsing mode, which must degrade to "always expanded by
     * default", never throw and leave the sidebar broken.
     */
    static String navJs() {
        return """
                (function () {
                  const nav = window.__JYLOS_NAV__;
                  const root = document.getElementById('jylos-sidebar');
                  if (!nav || !root) return;

                  const pageHref = document.body.dataset.pageHref || '';
                  const pageFolder = document.body.dataset.pageFolder || '';
                  const depthPrefix = document.body.dataset.depthPrefix || '';

                  function storageKey(path) { return 'jylos-nav-collapsed:' + path; }
                  function isCollapsed(path, defaultCollapsed) {
                    try {
                      const stored = localStorage.getItem(storageKey(path));
                      if (stored !== null) return stored === '1';
                    } catch (e) { /* localStorage unavailable — fall back to the default */ }
                    return defaultCollapsed;
                  }
                  function setCollapsed(path, collapsed) {
                    try { localStorage.setItem(storageKey(path), collapsed ? '1' : '0'); }
                    catch (e) { /* ignore — nothing to persist to */ }
                  }

                  // Ancestors of the current page start expanded even if the visitor
                  // collapsed that folder before — otherwise opening a deep note could hide
                  // the very trail that leads back to it. pageFolder already uses the same
                  // segment-join convention as folder.path below (see page()'s own doc) —
                  // deriving this from pageHref's notes/... URL structure instead would be
                  // parsing a different, output-layout-specific convention by mistake.
                  const expandedAncestors = new Set();
                  {
                    const parts = pageFolder ? pageFolder.split('/') : [];
                    let acc = '';
                    for (const part of parts) {
                      acc = acc ? acc + '/' + part : part;
                      expandedAncestors.add(acc);
                    }
                  }

                  function renderNoteLink(note) {
                    const li = document.createElement('li');
                    const a = document.createElement('a');
                    a.href = depthPrefix + note.href;
                    a.textContent = note.title;
                    a.title = note.title;
                    if (note.href === pageHref) {
                      a.className = 'active';
                    }
                    li.appendChild(a);
                    return li;
                  }

                  function renderFolder(folder) {
                    const li = document.createElement('li');
                    li.className = 'nav-folder';
                    const toggle = document.createElement('button');
                    toggle.type = 'button';
                    toggle.className = 'nav-folder-toggle';
                    toggle.textContent = folder.name;
                    const childList = document.createElement('ul');
                    const collapsed = !expandedAncestors.has(folder.path) && isCollapsed(folder.path, true);
                    childList.hidden = collapsed;
                    toggle.setAttribute('aria-expanded', String(!collapsed));
                    toggle.addEventListener('click', () => {
                      const nowCollapsed = !childList.hidden;
                      childList.hidden = nowCollapsed;
                      toggle.setAttribute('aria-expanded', String(!nowCollapsed));
                      setCollapsed(folder.path, nowCollapsed);
                    });
                    li.appendChild(toggle);
                    for (const sub of folder.folders) childList.appendChild(renderFolder(sub));
                    for (const note of folder.notes) childList.appendChild(renderNoteLink(note));
                    li.appendChild(childList);
                    return li;
                  }

                  const tree = document.createElement('ul');
                  tree.className = 'nav-tree';
                  for (const note of nav.notes) tree.appendChild(renderNoteLink(note));
                  for (const folder of nav.folders) tree.appendChild(renderFolder(folder));

                  // Flattened list for search, built once — independent of which folders
                  // happen to be collapsed in the tree above.
                  const allNotes = [];
                  (function flatten(node) {
                    for (const note of node.notes) allNotes.push(note);
                    for (const sub of node.folders) flatten(sub);
                  })(nav);

                  const searchInput = document.createElement('input');
                  searchInput.type = 'search';
                  searchInput.className = 'nav-search';
                  searchInput.placeholder = 'Search notes…';
                  searchInput.setAttribute('aria-label', 'Search notes');

                  const results = document.createElement('ul');
                  results.className = 'nav-tree nav-search-results';
                  results.hidden = true;

                  searchInput.addEventListener('input', () => {
                    const q = searchInput.value.trim().toLowerCase();
                    if (!q) {
                      results.hidden = true;
                      tree.hidden = false;
                      return;
                    }
                    results.innerHTML = '';
                    let count = 0;
                    for (const note of allNotes) {
                      if (note.title.toLowerCase().indexOf(q) !== -1) {
                        results.appendChild(renderNoteLink(note));
                        count++;
                        if (count >= 50) break;
                      }
                    }
                    if (count === 0) {
                      const empty = document.createElement('li');
                      empty.className = 'nav-search-empty';
                      empty.textContent = 'No matches';
                      results.appendChild(empty);
                    }
                    results.hidden = false;
                    tree.hidden = true;
                  });

                  root.appendChild(searchInput);
                  root.appendChild(results);
                  root.appendChild(tree);

                  const menuToggle = document.getElementById('jylos-menu-toggle');
                  const backdrop = document.getElementById('jylos-sidebar-backdrop');
                  function closeSidebar() { document.body.classList.remove('sidebar-open'); }
                  if (menuToggle) {
                    menuToggle.addEventListener('click', () => {
                      document.body.classList.toggle('sidebar-open');
                    });
                  }
                  if (backdrop) {
                    backdrop.addEventListener('click', closeSidebar);
                  }

                  // Manual light/dark toggle. The inline <script> in <head> already applied
                  // any saved preference before first paint (see page()'s own doc on that) —
                  // this only needs to handle the click and persist the new choice.
                  const themeToggle = document.getElementById('jylos-theme-toggle');
                  if (themeToggle) {
                    themeToggle.addEventListener('click', () => {
                      const current = document.documentElement.getAttribute('data-theme');
                      const systemDark = window.matchMedia
                          && window.matchMedia('(prefers-color-scheme: dark)').matches;
                      const currentlyDark = current ? current === 'dark' : systemDark;
                      const next = currentlyDark ? 'light' : 'dark';
                      document.documentElement.setAttribute('data-theme', next);
                      try { localStorage.setItem('jylos-theme', next); } catch (e) { /* ignore */ }
                    });
                  }
                })();
                """;
    }

    /**
     * The force-directed physics/rendering engine shared by the full graph page
     * and every note's small "local graph" preview — one file (cached by the
     * browser across every page that uses it) instead of duplicating the engine
     * in both {@link #graphJs} and {@link #miniGraphJs}.
     *
     * <p>Deliberately mirrors {@code GraphCanvas.java} — the native JavaFX graph
     * view bundled with the app itself — rather than the simpler ad-hoc physics
     * this file used before: same Barnes–Hut many-body repulsion (O(n log n),
     * needed once a vault has thousands of notes), the same collision pass so
     * circles never overlap once settled, the same per-node zoom-based label
     * reveal (a hub's name appears at a lower zoom than a small note's, the way
     * a city rotules before a village does on a map), the same hover-highlight
     * that dims everything not connected to the pointed-at node, and the exact
     * same color palette — so the published site's graph reads as the same
     * graph, not a different-looking approximation of it.</p>
     */
    static String graphEngineJs() {
        return """
                window.JylosGraph = (function () {
                  const ALPHA_INIT = 1.0;
                  const ALPHA_MIN = 0.0015;
                  const ALPHA_DECAY = 0.0135;
                  const VELOCITY_DECAY = 0.60;
                  const CHARGE = -260.0;
                  const THETA2 = 0.81;
                  const LINK_DISTANCE = 36.0;
                  const CENTER_GRAVITY = 0.035;
                  const MAX_VELOCITY = 160.0;
                  const COLLISION_PADDING = 2.0;
                  const COLLISION_STRENGTH = 0.7;
                  const MIN_SCALE = 0.02;
                  const MAX_SCALE = 8.0;
                  const CLICK_SLOP = 4.0;
                  const MIN_NODE_PX = 2.0;
                  const HOVER_DIM_ALPHA = 0.28;
                  const HOVER_EASE_RATE = 0.25;
                  const BASE_NODE_RADIUS = 2.4;
                  const LABEL_THRESHOLD = 0.4;

                  const PALETTES = {
                    light: {
                      bg: '#f4f4f6', node: '#5562c9', tag: '#7c3aed', ghost: '#9aa0bf',
                      nodeRing: 'rgba(35,38,58,0.55)', link: 'rgba(58,63,112,0.30)',
                      linkActive: 'rgba(91,33,182,0.95)', linkDim: 'rgba(58,63,112,0.07)',
                      text: '#2a2e44', accent: '#e0723a'
                    },
                    dark: {
                      bg: '#1a1a1a', node: '#aab6da', tag: '#c39bff', ghost: '#6b7390',
                      nodeRing: 'rgba(13,15,22,0.55)', link: 'rgba(255,255,255,0.18)',
                      linkActive: 'rgba(234,240,255,0.95)', linkDim: 'rgba(255,255,255,0.05)',
                      text: '#d4dcf0', accent: '#ffb066'
                    }
                  };

                  function resolveTheme() {
                    const explicit = document.documentElement.getAttribute('data-theme');
                    if (explicit === 'light' || explicit === 'dark') return explicit;
                    return (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
                        ? 'dark' : 'light';
                  }

                  function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
                  function jiggle() { return (Math.random() - 0.5) * 1e-3; }

                  function parseColor(c) {
                    if (c[0] === '#') {
                      const n = parseInt(c.slice(1), 16);
                      return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 1];
                    }
                    const m = c.match(/rgba?\\(([^)]+)\\)/);
                    const parts = m[1].split(',').map(s => parseFloat(s));
                    return [parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : 1];
                  }
                  function lerpColor(a, b, t) {
                    const pa = parseColor(a), pb = parseColor(b);
                    const r = Math.round(pa[0] + (pb[0] - pa[0]) * t);
                    const g = Math.round(pa[1] + (pb[1] - pa[1]) * t);
                    const bl = Math.round(pa[2] + (pb[2] - pa[2]) * t);
                    const al = pa[3] + (pb[3] - pa[3]) * t;
                    return 'rgba(' + r + ',' + g + ',' + bl + ',' + al + ')';
                  }

                  // ── Barnes–Hut quadtree — same structure/pruning as GraphCanvas.java's QuadNode ──
                  function QuadNode(x0, y0, size) {
                    this.x0 = x0; this.y0 = y0; this.size = size;
                    this.children = null; this.index = -1; this.count = 0;
                    this.cx = 0; this.cy = 0; this.maxRadius = 0; this.leaf = true;
                  }
                  QuadNode.prototype.childFor = function (px, py) {
                    const half = this.size / 2;
                    let q = 0, nx = this.x0, ny = this.y0;
                    if (px >= this.x0 + half) { q |= 1; nx = this.x0 + half; }
                    if (py >= this.y0 + half) { q |= 2; ny = this.y0 + half; }
                    if (!this.children[q]) this.children[q] = new QuadNode(nx, ny, half);
                    return this.children[q];
                  };
                  QuadNode.prototype.insert = function (i, xs, ys) {
                    if (this.count === 0 && this.children === null) {
                      this.index = i; this.count = 1; this.cx = xs[i]; this.cy = ys[i];
                      return;
                    }
                    if (this.children === null) {
                      this.children = [null, null, null, null];
                      this.leaf = false;
                      const existing = this.index;
                      this.index = -1;
                      if (existing >= 0) this.childFor(xs[existing], ys[existing]).insert(existing, xs, ys);
                    }
                    this.count++;
                    this.childFor(xs[i], ys[i]).insert(i, xs, ys);
                  };
                  QuadNode.prototype.computeMass = function (radius) {
                    if (this.leaf) {
                      this.maxRadius = this.index >= 0 ? radius[this.index] : 0;
                      return;
                    }
                    let sx = 0, sy = 0, c = 0, maxR = 0;
                    for (const child of this.children) {
                      if (!child) continue;
                      child.computeMass(radius);
                      sx += child.cx * child.count; sy += child.cy * child.count;
                      c += child.count; maxR = Math.max(maxR, child.maxRadius);
                    }
                    if (c > 0) { this.cx = sx / c; this.cy = sy / c; this.count = c; }
                    this.maxRadius = maxR;
                  };
                  function buildQuadtree(n, x, y, radius) {
                    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
                    for (let i = 0; i < n; i++) {
                      minX = Math.min(minX, x[i]); minY = Math.min(minY, y[i]);
                      maxX = Math.max(maxX, x[i]); maxY = Math.max(maxY, y[i]);
                    }
                    const size = Math.max(maxX - minX, maxY - minY);
                    if (!(size > 0)) return null;
                    const root = new QuadNode(minX, minY, size);
                    for (let i = 0; i < n; i++) root.insert(i, x, y);
                    root.computeMass(radius);
                    return root;
                  }

                  /** One graph instance bound to one canvas. Physics/state is private per instance. */
                  function create(canvas, data, opts) {
                    opts = opts || {};
                    const interactive = opts.interactive !== false;
                    const onNavigate = opts.onNavigate || function (href) { window.location.href = href; };

                    const ctx = canvas.getContext('2d');
                    let dpr = window.devicePixelRatio || 1;

                    const nodeDefs = data.nodes.map(nd => {
                      const base = nd.type === 'ghost' ? 2.0 : BASE_NODE_RADIUS;
                      return {
                        id: nd.id, label: nd.label || '', kind: nd.type, href: nd.href || null,
                        radius: base + Math.sqrt(Math.max(0, nd.degree || 0)) * 1.5
                      };
                    });
                    const byId = new Map(nodeDefs.map((nd, i) => [nd.id, i]));
                    const n = nodeDefs.length;
                    const x = new Float64Array(n), y = new Float64Array(n);
                    const vx = new Float64Array(n), vy = new Float64Array(n);
                    const radius = new Float64Array(n);
                    const label = nodeDefs.map(nd => nd.label);
                    const kind = nodeDefs.map(nd => nd.kind);
                    const href = nodeDefs.map(nd => nd.href);
                    for (let i = 0; i < n; i++) radius[i] = nodeDefs[i].radius;

                    const edgePairs = [];
                    const linkCount = new Array(n).fill(0);
                    const neighbors = Array.from({ length: n }, () => []);
                    for (const e of data.edges) {
                      const a = byId.get(e.source), b = byId.get(e.target);
                      if (a === undefined || b === undefined || a === b) continue;
                      edgePairs.push([a, b]);
                      linkCount[a]++; linkCount[b]++;
                      neighbors[a].push(b); neighbors[b].push(a);
                    }

                    // Seeded PRNG (xorshift32) so the initial scatter is reproducible per
                    // graph size — random is essential (an ordered seed IS the uniform-
                    // repulsion equilibrium, so the sim could never break out of it into
                    // organic clusters), reproducible just avoids the layout jumping around
                    // pointlessly if this ever re-runs with identical input.
                    (function seedLayout() {
                      let s = (n * 2654435761) >>> 0 || 1;
                      function rnd() {
                        s ^= s << 13; s ^= s >>> 17; s ^= s << 5; s >>>= 0;
                        return s / 4294967296;
                      }
                      const spread = 60 + Math.sqrt(Math.max(1, n)) * 26;
                      for (let i = 0; i < n; i++) {
                        const a = rnd() * Math.PI * 2, r = spread * Math.sqrt(rnd());
                        x[i] = Math.cos(a) * r; y[i] = Math.sin(a) * r;
                      }
                    })();

                    let alpha = ALPHA_INIT;
                    let scale = 1, offsetX = 0, offsetY = 0;
                    let dragIndex = -1, hoverIndex = -1, displayHoverIndex = -1;
                    let hoverStrength = 0, hoverTarget = 0, hoverRunning = false;
                    let running = false;
                    let needsInitialFit = true;
                    // Keep re-fitting the view every frame while the sim is still settling
                    // (see frame() below) — for an interactive graph the user can turn this
                    // off themselves the moment they wheel-zoom or drag-pan (autoFraming a
                    // view someone just deliberately changed would fight them); a non-
                    // interactive mini-graph has no such input, so this just never flips.
                    let autoFit = true;

                    function width() { return Math.max(0, canvas.clientWidth); }
                    function height() { return Math.max(0, canvas.clientHeight); }
                    function sx(wx) { return wx * scale + offsetX; }
                    function sy(wy) { return wy * scale + offsetY; }
                    function screenRadius(i) { return Math.max(MIN_NODE_PX, radius[i] * scale); }
                    function palette() { return PALETTES[resolveTheme()]; }

                    /** Attempts the one-time initial fit-to-view, but only once the canvas
                     *  actually has a real on-screen size — a small widget inside a flex
                     *  layout (the note-page mini-graph, in particular) can still read 0 for
                     *  clientWidth/clientHeight at the exact instant its own <script> runs,
                     *  before the surrounding layout has settled. Skipping the fit then and
                     *  never retrying would leave the canvas permanently stuck at a 1x1
                     *  drawing buffer — blank, and definitely not centered on anything. Safe
                     *  to call every frame: a no-op once needsInitialFit is already false. */
                    function tryInitialFit() {
                      if (!needsInitialFit) return;
                      const w = width(), h = height();
                      if (w <= 0 || h <= 0 || n === 0) return;
                      needsInitialFit = false;
                      canvas.width = Math.max(1, Math.round(w * dpr));
                      canvas.height = Math.max(1, Math.round(h * dpr));
                      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                      resetView();
                    }

                    function resize() {
                      dpr = window.devicePixelRatio || 1;
                      if (needsInitialFit) {
                        tryInitialFit();
                        if (!needsInitialFit) return;
                      }
                      const w = width(), h = height();
                      canvas.width = Math.max(1, Math.round(w * dpr));
                      canvas.height = Math.max(1, Math.round(h * dpr));
                      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                      draw();
                    }

                    function applyCenterGravity() {
                      const k = CENTER_GRAVITY * alpha;
                      for (let i = 0; i < n; i++) { vx[i] -= x[i] * k; vy[i] -= y[i] * k; }
                    }

                    function applyLinks() {
                      for (const [s, t] of edgePairs) {
                        let dx = (x[t] + vx[t]) - (x[s] + vx[s]);
                        let dy = (y[t] + vy[t]) - (y[s] + vy[s]);
                        let d = Math.sqrt(dx * dx + dy * dy);
                        if (d < 1e-6) { dx = jiggle(); dy = jiggle(); d = Math.sqrt(dx * dx + dy * dy); }
                        const strength = 1 / Math.min(linkCount[s], linkCount[t]);
                        const l = (d - LINK_DISTANCE) / d * alpha * strength;
                        const fx = dx * l, fy = dy * l;
                        const bias = linkCount[s] / (linkCount[s] + linkCount[t]);
                        vx[t] -= fx * (1 - bias); vy[t] -= fy * (1 - bias);
                        vx[s] += fx * bias; vy[s] += fy * bias;
                      }
                    }

                    function accumulateRepulsion(node, i) {
                      if (!node || (node.leaf && node.index === i)) return;
                      let dx = node.cx - x[i], dy = node.cy - y[i];
                      let d2 = dx * dx + dy * dy;
                      if (d2 < 1e-6) { dx = jiggle(); dy = jiggle(); d2 = dx * dx + dy * dy; }
                      if (node.leaf || (node.size * node.size) < THETA2 * d2) {
                        const w = CHARGE * alpha * node.count / d2;
                        vx[i] += dx * w; vy[i] += dy * w;
                        return;
                      }
                      for (const child of node.children) if (child) accumulateRepulsion(child, i);
                    }
                    function applyManyBody(root) {
                      if (n < 2 || !root) return;
                      for (let i = 0; i < n; i++) accumulateRepulsion(root, i);
                    }

                    function resolveCollisionsFor(node, i) {
                      if (!node) return;
                      if (node.leaf) {
                        const j = node.index;
                        if (j <= i) return;
                        let dx = (x[j] + vx[j]) - (x[i] + vx[i]);
                        let dy = (y[j] + vy[j]) - (y[i] + vy[i]);
                        const minDist = radius[i] + radius[j] + COLLISION_PADDING;
                        let d2 = dx * dx + dy * dy;
                        if (d2 >= minDist * minDist) return;
                        let d = Math.sqrt(d2);
                        if (d < 1e-6) { dx = jiggle(); dy = jiggle(); d = Math.sqrt(dx * dx + dy * dy); }
                        const push = (minDist - d) / d * COLLISION_STRENGTH;
                        const fx = dx * push, fy = dy * push;
                        vx[j] += fx; vy[j] += fy; vx[i] -= fx; vy[i] -= fy;
                        return;
                      }
                      const closestX = clamp(x[i], node.x0, node.x0 + node.size);
                      const closestY = clamp(y[i], node.y0, node.y0 + node.size);
                      const dx = closestX - x[i], dy = closestY - y[i];
                      const maxReach = radius[i] + node.maxRadius + COLLISION_PADDING;
                      if (dx * dx + dy * dy > maxReach * maxReach) return;
                      for (const child of node.children) if (child) resolveCollisionsFor(child, i);
                    }
                    function applyCollisions(root) {
                      if (n < 2 || !root) return;
                      for (let i = 0; i < n; i++) resolveCollisionsFor(root, i);
                    }

                    function step() {
                      if (n === 0) { alpha = 0; return; }
                      alpha += (0 - alpha) * ALPHA_DECAY;
                      const root = buildQuadtree(n, x, y, radius);
                      applyManyBody(root);
                      applyLinks();
                      applyCollisions(root);
                      applyCenterGravity();
                      for (let i = 0; i < n; i++) {
                        if (i === dragIndex) { vx[i] = 0; vy[i] = 0; continue; }
                        vx[i] = clamp(vx[i] * VELOCITY_DECAY, -MAX_VELOCITY, MAX_VELOCITY);
                        vy[i] = clamp(vy[i] * VELOCITY_DECAY, -MAX_VELOCITY, MAX_VELOCITY);
                        x[i] += vx[i]; y[i] += vy[i];
                      }
                    }

                    const labelOrder = Array.from({ length: n }, (_, i) => i).sort((a, b) => radius[b] - radius[a]);
                    const placedLabelBoxes = [];

                    function labelFadeIn(idx) {
                      const revealScale = LABEL_THRESHOLD * (BASE_NODE_RADIUS / radius[idx]);
                      return clamp((scale - revealScale) / (revealScale * 0.75), 0, 1);
                    }
                    function placeLabel(idx, avgCharWidth, labelHeight, opacity, dim) {
                      if (!label[idx]) return;
                      const w = width(), h = height();
                      const r = radius[idx] * scale;
                      const cx = sx(x[idx]), cy = sy(y[idx]) + r + 12;
                      const halfW = label[idx].length * avgCharWidth / 2 + 2;
                      const minX = cx - halfW, maxX = cx + halfW;
                      const minY = cy - labelHeight / 2, maxY = cy + labelHeight / 2;
                      if (maxX < 0 || minX > w || maxY < 0 || minY > h) return;
                      for (const box of placedLabelBoxes) {
                        if (minX < box[2] && maxX > box[0] && minY < box[3] && maxY > box[1]) return;
                      }
                      placedLabelBoxes.push([minX, minY, maxX, maxY]);
                      ctx.globalAlpha = opacity * (dim ? HOVER_DIM_ALPHA : 1);
                      ctx.fillText(label[idx], cx, cy);
                    }
                    function drawLabels(active, hoveringDisplay, pal) {
                      const fontSize = Math.max(9, Math.min(15, 11 * Math.max(0.8, scale)));
                      ctx.textAlign = 'center';
                      ctx.font = fontSize + 'px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
                      ctx.fillStyle = pal.text;
                      const avgCharWidth = fontSize * 0.56;
                      const labelHeight = fontSize + 2;
                      placedLabelBoxes.length = 0;
                      if (hoveringDisplay) {
                        for (const idx of labelOrder) {
                          if (active[idx]) {
                            const opacity = Math.max(hoverStrength, labelFadeIn(idx));
                            placeLabel(idx, avgCharWidth, labelHeight, opacity, false);
                          }
                        }
                      }
                      for (const idx of labelOrder) {
                        if (active && active[idx]) continue;
                        const fadeIn = labelFadeIn(idx);
                        if (fadeIn <= 0) continue;
                        placeLabel(idx, avgCharWidth, labelHeight, fadeIn, hoveringDisplay);
                      }
                      ctx.globalAlpha = 1;
                    }

                    function draw() {
                      const w = width(), h = height();
                      const pal = palette();
                      ctx.fillStyle = pal.bg;
                      ctx.fillRect(0, 0, w, h);
                      if (n === 0) return;

                      const hoveringDisplay = displayHoverIndex >= 0;
                      let active = null;
                      if (hoveringDisplay) {
                        active = new Uint8Array(n);
                        active[displayHoverIndex] = 1;
                        for (const nb of neighbors[displayHoverIndex]) active[nb] = 1;
                      }

                      ctx.lineWidth = clamp(scale, 0.7, 1.6);
                      for (const [s, t] of edgePairs) {
                        ctx.strokeStyle = !hoveringDisplay ? pal.link
                            : lerpColor(pal.link, (active[s] && active[t]) ? pal.linkActive : pal.linkDim, hoverStrength);
                        ctx.beginPath();
                        ctx.moveTo(sx(x[s]), sy(y[s]));
                        ctx.lineTo(sx(x[t]), sy(y[t]));
                        ctx.stroke();
                      }

                      for (let i = 0; i < n; i++) {
                        const r = screenRadius(i);
                        const px = sx(x[i]), py = sy(y[i]);
                        const dim = hoveringDisplay && !active[i];
                        ctx.globalAlpha = dim ? 1 - (1 - HOVER_DIM_ALPHA) * hoverStrength : 1;
                        if (kind[i] === 'ghost') {
                          ctx.strokeStyle = i === displayHoverIndex ? pal.accent : pal.ghost;
                          ctx.lineWidth = 1.2;
                          ctx.beginPath(); ctx.arc(px, py, r, 0, Math.PI * 2); ctx.stroke();
                        } else {
                          let fill;
                          if (i === displayHoverIndex) fill = lerpColor(pal.node, pal.accent, hoverStrength);
                          else if (kind[i] === 'tag') fill = pal.tag;
                          else fill = pal.node;
                          ctx.fillStyle = fill;
                          ctx.beginPath(); ctx.arc(px, py, r, 0, Math.PI * 2); ctx.fill();
                          if (i === displayHoverIndex) {
                            ctx.strokeStyle = pal.nodeRing;
                            ctx.lineWidth = 1.5;
                            ctx.globalAlpha = hoverStrength;
                            ctx.beginPath(); ctx.arc(px, py, r, 0, Math.PI * 2); ctx.stroke();
                          }
                        }
                      }
                      ctx.globalAlpha = 1;
                      drawLabels(active, hoveringDisplay, pal);
                    }

                    function drawIfIdle() { if (!running) draw(); }

                    function frame() {
                      if (needsInitialFit) tryInitialFit(); // retry each frame until the layout has a real size
                      step();
                      if (autoFit) {
                        // A view fixed once at the start (before physics has spread/settled
                        // the random seed via repulsion, over several more seconds) drifts
                        // nodes right out of the visible canvas as they keep moving — re-fit
                        // every frame instead, until either the sim settles or (interactive
                        // only) the user takes over framing themselves via zoom/pan.
                        resetView();
                      } else {
                        draw();
                      }
                      if (alpha < ALPHA_MIN && dragIndex < 0) { running = false; return; }
                      requestAnimationFrame(frame);
                    }
                    function startTimer() {
                      if (running) return;
                      running = true;
                      requestAnimationFrame(frame);
                    }
                    function reheat() { alpha = ALPHA_INIT; startTimer(); }

                    function hoverFrame() {
                      hoverStrength += (hoverTarget - hoverStrength) * HOVER_EASE_RATE;
                      if (Math.abs(hoverTarget - hoverStrength) < 0.01) {
                        hoverStrength = hoverTarget;
                        if (hoverTarget <= 0) displayHoverIndex = -1;
                        hoverRunning = false;
                        draw();
                        return;
                      }
                      draw();
                      requestAnimationFrame(hoverFrame);
                    }
                    function setHoverTarget(target) {
                      hoverTarget = target;
                      if (!hoverRunning) { hoverRunning = true; requestAnimationFrame(hoverFrame); }
                    }

                    function resetView() {
                      if (n === 0) { scale = 1; offsetX = width() / 2; offsetY = height() / 2; draw(); return; }
                      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
                      for (let i = 0; i < n; i++) {
                        minX = Math.min(minX, x[i] - radius[i]); minY = Math.min(minY, y[i] - radius[i]);
                        maxX = Math.max(maxX, x[i] + radius[i]); maxY = Math.max(maxY, y[i] + radius[i]);
                      }
                      const w = Math.max(1, maxX - minX), h = Math.max(1, maxY - minY);
                      const vw = Math.max(1, width()), vh = Math.max(1, height());
                      scale = clamp(Math.min(vw / w, vh / h) * 0.86, MIN_SCALE, MAX_SCALE);
                      const cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
                      offsetX = vw / 2 - cx * scale; offsetY = vh / 2 - cy * scale;
                      draw();
                    }

                    /** Zooms in on and highlights one node — used by the expand link from a
                     *  note's mini-graph, so the full graph opens centered on that note
                     *  instead of the whole vault's overview. */
                    function focusOn(id) {
                      const idx = byId.get(id);
                      if (idx === undefined) return;
                      needsInitialFit = false;
                      scale = clamp(Math.max(scale, 2.2), MIN_SCALE, MAX_SCALE);
                      offsetX = width() / 2 - x[idx] * scale;
                      offsetY = height() / 2 - y[idx] * scale;
                      displayHoverIndex = idx;
                      setHoverTarget(1);
                      draw();
                    }

                    /** Nearest node under a canvas-local (not page) point, or -1 — mirrors
                     *  GraphCanvas.java's own pick(): screen-space, nearest of any overlapping
                     *  hit rather than the first one found. */
                    function pick(px, py) {
                      let best = -1, bestD2 = Infinity;
                      for (let i = 0; i < n; i++) {
                        const r = screenRadius(i) + 4;
                        const dx = px - sx(x[i]), dy = py - sy(y[i]);
                        const d2 = dx * dx + dy * dy;
                        if (d2 <= r * r && d2 < bestD2) { bestD2 = d2; best = i; }
                      }
                      return best;
                    }
                    function localPoint(evt) {
                      const rect = canvas.getBoundingClientRect();
                      return { x: evt.clientX - rect.left, y: evt.clientY - rect.top };
                    }

                    if (interactive) {
                      let pressX = 0, pressY = 0, moved = false, panning = false, panStartX = 0, panStartY = 0;
                      canvas.addEventListener('wheel', evt => {
                        evt.preventDefault();
                        autoFit = false;
                        const p = localPoint(evt);
                        const factor = Math.exp(-evt.deltaY * 0.0015);
                        const newScale = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
                        const wx = (p.x - offsetX) / scale, wy = (p.y - offsetY) / scale;
                        scale = newScale;
                        offsetX = p.x - wx * scale; offsetY = p.y - wy * scale;
                        drawIfIdle();
                      }, { passive: false });

                      canvas.addEventListener('mousedown', evt => {
                        autoFit = false;
                        const p = localPoint(evt);
                        pressX = p.x; pressY = p.y; moved = false;
                        const hit = pick(p.x, p.y);
                        if (hit >= 0) {
                          dragIndex = hit;
                          startTimer();
                        } else {
                          panning = true; panStartX = offsetX; panStartY = offsetY;
                          canvas.style.cursor = 'grabbing';
                        }
                      });
                      window.addEventListener('mousemove', evt => {
                        const p = localPoint(evt);
                        if (Math.abs(p.x - pressX) > CLICK_SLOP || Math.abs(p.y - pressY) > CLICK_SLOP) moved = true;
                        if (dragIndex >= 0) {
                          x[dragIndex] = (p.x - offsetX) / scale;
                          y[dragIndex] = (p.y - offsetY) / scale;
                          vx[dragIndex] = 0; vy[dragIndex] = 0;
                          drawIfIdle();
                        } else if (panning) {
                          offsetX = panStartX + (p.x - pressX); offsetY = panStartY + (p.y - pressY);
                          drawIfIdle();
                        } else {
                          const hit = pick(p.x, p.y);
                          if (hit !== hoverIndex) {
                            hoverIndex = hit;
                            if (hit >= 0) displayHoverIndex = hit;
                            setHoverTarget(hit >= 0 ? 1 : 0);
                          }
                        }
                      });
                      window.addEventListener('mouseup', () => {
                        dragIndex = -1;
                        panning = false;
                        canvas.style.cursor = 'grab';
                      });
                      canvas.addEventListener('mouseleave', () => {
                        if (hoverIndex !== -1) { hoverIndex = -1; setHoverTarget(0); }
                      });
                      canvas.addEventListener('click', evt => {
                        if (moved) return; // a drag/pan just ended here, not a click-to-open
                        const p = localPoint(evt);
                        const hit = pick(p.x, p.y);
                        if (hit >= 0 && href[hit]) onNavigate(href[hit]);
                      });
                    } else {
                      // Non-interactive (the small per-note preview): click still opens a
                      // node's page, but no drag/pan/zoom/hover — it's a glance, not a tool.
                      canvas.addEventListener('click', evt => {
                        const p = localPoint(evt);
                        const hit = pick(p.x, p.y);
                        if (hit >= 0 && href[hit]) onNavigate(href[hit]);
                      });
                    }

                    window.addEventListener('resize', resize);
                    resize();
                    reheat();

                    return { resetView: resetView, focusOn: focusOn, pause: function () { running = false; } };
                  }

                  return { create: create };
                })();
                """;
    }

    /**
     * Thin wrapper around {@link #graphEngineJs} for the full graph page: loads
     * the whole vault's graph and renders it interactively into {@code
     * #graph-canvas}. A {@code #focus=<id>} URL fragment (used by a note's mini-
     * graph "expand" link) re-centers and highlights that node once loaded.
     */
    static String graphJs() {
        return """
                (function () {
                  const canvas = document.getElementById('graph-canvas');
                  if (!canvas) return;

                  // Opening this file directly (double-click, file:// URL) has the
                  // browser reject fetch('graph.json') as cross-origin — local files have
                  // no origin to satisfy CORS. graph.html embeds the same data inline as
                  // window.__JYLOS_GRAPH__ specifically to sidestep that; fetch is kept as
                  // a fallback for a page served over http(s) without that inline script.
                  const loadGraphData = () => window.__JYLOS_GRAPH__
                    ? Promise.resolve(window.__JYLOS_GRAPH__)
                    : fetch('graph.json').then(r => r.json());

                  const hint = document.querySelector('.graph-hint');

                  loadGraphData().then(data => {
                    const graph = window.JylosGraph.create(canvas, data, { interactive: true });
                    const match = /focus=([^&]+)/.exec(location.hash);
                    if (match) {
                      graph.focusOn(decodeURIComponent(match[1]));
                    }
                  }).catch(err => {
                    console.error('Could not load graph data:', err);
                    if (hint) {
                      hint.textContent = 'Could not load the graph data (see the browser console for details).';
                    }
                  });
                })();
                """;
    }

    /**
     * Thin wrapper around {@link #graphEngineJs} for a note's small "local graph"
     * preview — reads the tiny per-page {@code window.__JYLOS_MINI_GRAPH__} (just
     * this note and its direct neighbours, embedded inline since it's too small
     * to be worth a shared file) and renders it into {@code #mini-graph-canvas}.
     * Interactive (wheel-zoom, drag-pan, drag-node) same as the full graph page —
     * small does not mean read-only, and a note with more than a couple of
     * neighbours needs zoom/pan to actually make sense of it in 160px. The
     * expand button still opens the full graph centered here, for the bigger
     * picture beyond this note's immediate neighbours.
     */
    static String miniGraphJs() {
        return """
                (function () {
                  const canvas = document.getElementById('mini-graph-canvas');
                  const data = window.__JYLOS_MINI_GRAPH__;
                  if (!canvas || !data) return;
                  window.JylosGraph.create(canvas, data, { interactive: true });

                  const expand = document.getElementById('mini-graph-expand');
                  if (expand) {
                    expand.addEventListener('click', () => {
                      window.location.href = expand.dataset.href;
                    });
                  }
                })();
                """;
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
