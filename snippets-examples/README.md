# CSS snippet examples

Ready-to-use [CSS snippets](../README.md#css-snippets) for Jylos. A snippet is a
small `.css` file layered **on top of** the active theme to retint the interface —
unlike a full theme, you don't author a whole stylesheet, you just override colours.

| File | Palette | Adapts to |
|------|---------|-----------|
| `atom-one.css` | Atom One Dark / One Light | Dark **and** Light themes |
| `nord.css` | Nord — Polar Night / Snow Storm | Dark **and** Light themes |
| `solarized.css` | Solarized Dark / Light | Dark **and** Light themes |

Each example is **adaptive**: it ships both a dark and a light variant in one file
and follows whichever base theme you have active (see below).

## How to use

1. **Preferences → CSS snippets → Open folder** (this opens `<appData>/snippets`).
2. Copy the `.css` file(s) you want into that folder.
3. Back in Preferences, click **Reload**, tick the snippet, and **Save**.

The snippet applies immediately and is restored on the next launch. Multiple
snippets can be enabled at once and are layered after the theme, so their rules
win; a deleted snippet is simply ignored. Note that two *full-palette* snippets
(like these) will fight each other — enable one at a time.

## Writing your own

Jylos tags the scene root with `theme-dark` or `theme-light` (mirroring Obsidian's
body classes). Branch on it to support both modes from a single file; each example
works by redefining the theme's colour *lookup* variables on the root:

```css
.root.theme-dark {
    -fx-base-bg: #282c34;        /* main background            */
    -fx-sidebar-bg: #21252b;     /* sidebar / panels           */
    -fx-header-bg: #21252b;      /* toolbars / headers         */
    -fx-hover-bg: #2c313a;       /* row / button hover         */
    -fx-hover-text: #ffffff;
    -fx-accent: #61afef;         /* primary accent             */
    -fx-accent-hover: #4d9fe0;
    -fx-accent-contrast: #282c34;/* text/icon on accent        */
    -fx-selected-bg: #3e4451;    /* selected row background    */
    -fx-selected-text: #ffffff;
    -fx-text-main: #abb2bf;      /* primary text               */
    -fx-text-muted: #828997;     /* secondary text             */
    -fx-text-faint: #5c6370;     /* hints / disabled           */
    -fx-fn-border-color: #181a1f;/* separators / borders       */
    -fx-success: #98c379;
    -fx-warning: #e5c07b;
    -fx-danger: #e06c75;
    -fx-folder-all: #c678dd;     /* folder accent (all notes)  */
    -fx-folder-open: #98c379;    /* folder accent (open)       */
    -fx-folder-closed: #d19a66;  /* folder accent (closed)     */
}

.root.theme-light {
    /* the same variables with light-mode values */
}
```

You can also target individual JavaFX style classes (e.g. `.sidebar`,
`.note-card`, `.markdown-toolbar`) for finer tweaks. This is **JavaFX CSS**, a
subset of web CSS: properties are prefixed `-fx-` and there are no media queries,
which is why the `theme-dark` / `theme-light` root class exists — use it instead
of trying to detect the mode.
