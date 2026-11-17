# Embedded web engine: JavaFX WebView limitations and the JCEF option

Español: [es/WEBVIEW.md](es/WEBVIEW.md)

The CodeMirror editor (`MarkdownEditorView`), the reading view (`EditorController`'s
`previewWebView`, filled by `MarkdownPreview`) and the release notes shown before an update
(`UpdateInstallSupport`) are HTML pages drawn by JavaFX's `WebView`. That is the WebKit engine
that comes with JavaFX. It is not Chromium: it lacks some formats and browser behaviour that
Obsidian and other Electron apps get for free.

This page lists the known gaps, what Jylos does about each one, and what moving to another
engine (JCEF) would involve.

## Known limitations

| Limitation | Effect | What Jylos does | Where |
|---|---|---|---|
| No WebP decoding (`naturalWidth` stays 0). Many sites serve WebP even under a `.png` URL. | Image shows as its link | An image that fails is handed to Java. Java fetches it (or decodes the inlined vault file), reads it with ImageIO plus the TwelveMonkeys WebP plugin, and sends back a PNG. | `WebImageConverter`, `WebViewImages`, `live-preview.js` (`imageConverted`), `MarkdownPreview` (`__jylosImages`) |
| Colour emoji are drawn only when the emoji sits alone in its own element with an emoji font. Next to other text in the same node they come out garbled, and a second emoji in the same element is not drawn. Sequences (skin tones, ZWJ families, flags, keycaps) are not shaped. On Windows, emoji text comes out black and white. | Emoji garbled, or only part of a sequence shown | Every emoji starts as an image: its colour Twemoji image (bundled, CC-BY 4.0), or, for a symbol such as ↩ and anything Twemoji lacks, one rasterised from the bundled monochrome Noto Emoji font. On macOS only, and only where drawing one on a canvas shows colour, each emoji goes back to text in its own element with an emoji font, and symbols to plain text; sequences stay Twemoji images. | `emoji.js` (editor), `colour-emoji.js` and `MarkdownPreview.emojifyToImages` (reading view), `EditorBridge.rasterizeEmoji` |
| `loadContent` garbles characters outside the BMP written as they are (it passes the page on as modified UTF-8) | Each such character shows as several broken glyphs | Emoji go into the page as numeric character references (`&#x1F600;`); other characters outside the BMP are not handled yet | `MarkdownPreview.emojifyToImages` |
| Scrolling is animated by WebKit itself | One wheel or trackpad event moves nothing for 2 frames, then takes about 5 more to arrive (about 100 ms in all). On top of the trackpad's own momentum this feels laggy. Measured with a 100 px event. | Nothing in the editor; the reading view multiplies wheel steps by 1.8. There is no public API to turn the animation off. | `EditorPreviewSupport.installAcceleratedScroll` |
| No GPU compositing; scrolling repaints on the JavaFX Application Thread | Smoothness depends on the size of the page area that repaints | CodeMirror only draws the viewport. Widget heights are remembered so content does not jump. | `live-preview.js` (`imageHeights`, `blockHeights`) |
| `KeyboardEvent.key` is empty on macOS | Option-key symbols (`[`, `{`, `@`…) matched CodeMirror shortcuts | Shortcuts that depend on key codes are kept off the typing path | `editor.js` (`typingSafeKeymap`) |
| `document.hasFocus()` does not answer as in a browser | CodeMirror's `hasFocus` is wrong | Focus is followed through the content's own focus and blur events | `live-preview.js` (`editorFocusField`) |
| No native clipboard paste into `contentEditable` | Paste does nothing | Copy, cut and paste go through the JavaFX clipboard in a key filter | `MarkdownEditorView.installDesktopEditingIntegration` |
| Pages are loaded with `loadContent`: opaque origin, no URL | `file://` resources and `#fragment` jumps don't work | Vault images and KaTeX fonts are inlined as `data:` URIs; anchor jumps are done in JavaScript | `MarkdownPreview.resolveImageSource`, the reading view's click handler |
| JavaScript only reaches methods of **public** classes | A bridge class that is not public fails silently | Bridges are public classes (`EditorBridge`, `PreviewJavaBridge`) | `EditorPreviewSupport.PreviewJavaBridge` |
| JavaScript runs on the JavaFX Application Thread | A synchronous call into Java blocks the UI | Expensive work (plugin block renders, image conversion) runs on background threads and is pushed back to the page | `EditorBlockRenderSupport`, `WebViewImages` |
| Font weights that are not multiples of 100 are dropped (e.g. 750) | Headings lose their weight | Only multiples of 100 are used | editor theme in `live-preview.js` |
| No AVIF decoding | AVIF images do not show | None yet: there is no pure-Java AVIF reader | — |

## The Java ↔ page contract

- **Java to the page:** strings of JavaScript run with `WebEngine.executeScript`. Arguments
  are encoded with Gson (`window.JylosEditor.setDocument(...)`).
- **Page to Java:** a bridge object set as a `window` member (`javaEditor` in the editor,
  `javaApp` in the reading view).
- **Synchronous calls.** Some page-to-Java calls return a value that the page uses right
  away: `transformInsertion`, `readClipboard`, `resolveImageSource` and `rasterizeEmoji`.
  Any engine whose page-to-Java calls are asynchronous needs these turned into
  request/response calls first.
- **Re-injection.** In the reading view the bridge is set again after every page load, and
  work that arrives before the page is ready waits for it (`MarkdownEditorView.whenReady`;
  `bridgeReady()` in the reading view).

## The JCEF option (under evaluation)

**Status.** Under evaluation; for now Jylos stays on JavaFX's WebView. A prototype measured
what it would bring and what it would cost:
- **Scrolling:** a 100 px wheel step lands in one frame, instead of about 100 ms with WebView.
- **Cost:** about 290 MB more memory and about 240 MB more on disk per platform.
- **Typing:** on macOS, JCEF drops every character that is not ASCII when it renders off
  screen, so Jylos would need a keyboard layer of its own.
- **Support:** JCEF has no official JavaFX integration; that piece would be Jylos's to keep up.

**What it is.** JCEF (Java Chromium Embedded Framework) puts Chromium, through CEF, inside a
Java application. JetBrains IDEs use it for their web-based panels; CEF itself is behind
desktop apps such as Spotify and Steam.

**What it would fix.** It is the same engine family as Obsidian's (Chromium), so it would
bring:
- WebP, AVIF and complete emoji (skin tones, flags, families);
- browser-native scrolling;
- current CSS and JavaScript.

**What it would cost:**
- **Built for Swing/AWT.** JCEF's integration is made for Swing/AWT, not JavaFX. Inside
  JavaFX it has to render off screen, with each frame copied into a JavaFX image. That
  gives up part of the GPU path, so the gain in scroll smoothness has to be measured with a
  prototype rather than assumed.
- **Native binaries.** CEF ships large native binaries for each platform. On macOS its
  helper processes also have to be signed and notarized together with the app.
- **Asynchronous bridge.** Page-to-Java calls are asynchronous, so the synchronous calls
  listed above have to be redesigned.
- **Alternative.** JxBrowser (commercial) is a Chromium engine for Java with JavaFX support.

**Still open before deciding:**
- how scrolling feels on a real trackpad;
- the packaged size, measured rather than estimated;
- Windows and Linux.

## Where the engine lives

The three pages are shown through one small interface, `HtmlView` (`ui/web`): load a page, run
or evaluate script, expose a Java object to the page (set again on every load), and act once it
has loaded. `JavaFxHtmlView` is its implementation on JavaFX's WebView, and the only class, with
`ExternalLinksBridge`, that knows the engine; it is also the node FXML places where a page is
shown. `WebEngineConfinementGuardTest` keeps `javafx.scene.web` and `netscape.javascript` out of
the rest of the code, so a move to another engine is a new implementation of `HtmlView`.
