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
| No colour emoji fonts; characters outside the BMP draw as tofu | Emoji missing or shown as boxes | Emoji runs are rasterised to monochrome images with the bundled Noto Emoji font | `MarkdownPreview.rasterizeEmojiRun`, `MarkdownEditorView.EditorBridge.rasterizeEmoji`, `editor.js` |
| Scrolling is animated by WebKit itself | One wheel or trackpad event moves nothing for 2 frames, then takes about 5 more to arrive (about 100 ms in all). On top of the trackpad's own momentum this feels laggy. Measured with a 100 px event. | Nothing in the editor; the reading view multiplies wheel steps by 1.8. There is no public API to turn the animation off. | `EditorController.installAcceleratedPreviewScroll` |
| No GPU compositing; scrolling repaints on the JavaFX Application Thread | Smoothness depends on the size of the page area that repaints | CodeMirror only draws the viewport. Widget heights are remembered so content does not jump. | `live-preview.js` (`imageHeights`, `blockHeights`) |
| `KeyboardEvent.key` is empty on macOS | Option-key symbols (`[`, `{`, `@`…) matched CodeMirror shortcuts | Shortcuts that depend on key codes are kept off the typing path | `editor.js` (`typingSafeKeymap`) |
| `document.hasFocus()` does not answer as in a browser | CodeMirror's `hasFocus` is wrong | Focus is followed through the content's own focus and blur events | `live-preview.js` (`editorFocusField`) |
| No native clipboard paste into `contentEditable` | Paste does nothing | Copy, cut and paste go through the JavaFX clipboard in a key filter | `MarkdownEditorView.installDesktopEditingIntegration` |
| Pages are loaded with `loadContent`: opaque origin, no URL | `file://` resources and `#fragment` jumps don't work | Vault images and KaTeX fonts are inlined as `data:` URIs; anchor jumps are done in JavaScript | `MarkdownPreview.resolveImageSource`, the reading view's click handler |
| JavaScript only reaches methods of **public** classes | A bridge class that is not public fails silently | Bridges are public classes (`EditorBridge`, `PreviewJavaBridge`) | `EditorController.PreviewJavaBridge` |
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

## Moving to JCEF

**What it is.** JCEF (Java Chromium Embedded Framework) puts Chromium, through CEF, inside a
Java application. JetBrains IDEs use it for their web-based panels; CEF itself is behind
desktop apps such as Spotify and Steam.

**What it would fix.** It is the same engine family as Obsidian's (Chromium), so it would
bring:
- WebP, AVIF and colour emoji;
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

**Before deciding, a prototype has to answer two questions:**
1. How smooth is scrolling when JCEF renders off screen inside JavaFX, compared with
   WebView?
2. How large is the packaged app on each platform?

Until then the plan is to keep the three page hosts behind one small interface, so that
changing the engine touches a single place.
