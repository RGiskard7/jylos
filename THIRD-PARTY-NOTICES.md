# Third-party notices

Jylos is MIT-licensed (see [`LICENSE`](LICENSE)). It bundles the third-party software and assets
listed below. Each keeps its own license, which applies to it and not to Jylos's own code.

The full license texts are in [`licenses/`](licenses/), except the two kept next to the assets
they cover (Twemoji and Noto Emoji). This file and the `licenses/` folder also ship inside the
application JAR, under `META-INF/third-party/`. The `NOTICE` files of the Apache-licensed
libraries are merged into the JAR's `META-INF/NOTICE`.

## Assets

| Component | Copyright | License |
|---|---|---|
| [Twemoji](https://github.com/jdecked/twemoji) 17.0.3, 72×72 PNG graphics, unmodified (`ui/preview/twemoji/twemoji-72.zip`) | © 2014–2021 Twitter, Inc. and other contributors; © 2022–present Jason Sofonia & Justine De Caires | [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/), text in `ui/preview/twemoji/LICENSE-GRAPHICS.txt` |
| [Noto Emoji](https://github.com/googlefonts/noto-emoji) 3.002, monochrome font, unmodified (`ui/preview/fonts/NotoEmoji-Regular.ttf`) | © 2013 Google LLC | [SIL OFL 1.1](https://openfontlicense.org), text in `ui/preview/fonts/OFL.txt` |

Paths are relative to `jylos/src/main/resources/com/example/jylos/`.

## Web libraries

Bundled as JavaScript in the editor and the reading view.

| Component | Copyright | License |
|---|---|---|
| [CodeMirror 6](https://codemirror.net) and [Lezer](https://lezer.codemirror.net) (`@codemirror/*`, `@lezer/*`, `crelt`, `style-mod`, `w3c-keyname`, `@marijn/find-cluster-break`), built into `ui/editor/editor.bundle.js`; versions in `jylos/editor-web/package-lock.json` | © 2016–2024 Marijn Haverbeke and others | [MIT](licenses/MIT.txt) |
| [KaTeX](https://katex.org) 0.16.11, with its fonts | © 2013–2020 Khan Academy and other contributors | [MIT](licenses/MIT.txt) |
| [highlight.js](https://highlightjs.org) 11.9.0 | © 2006 Ivan Sagalaev and other contributors | [BSD-3-Clause](licenses/BSD-3-Clause.txt) |
| [Mermaid](https://mermaid.js.org) 9.3.0 | © 2014–2022 Knut Sveidqvist | [MIT](licenses/MIT.txt) |

`mermaid.min.js` also contains Mermaid's own dependencies:
- d3 7, under [ISC](licenses/ISC.txt);
- DOMPurify 2.4.1, used under [Apache-2.0](licenses/Apache-2.0.txt) (it is dual-licensed with MPL-2.0);
- dagre-d3-es, khroma, lodash-es, moment-mini, non-layered-tidy-tree-layout, stylis, uuid and
  @braintree/sanitize-url, under [MIT](licenses/MIT.txt).

## Java libraries

| Component | Copyright | License |
|---|---|---|
| [OpenJFX](https://openjfx.io) 23.0.2 (JavaFX) | Oracle and/or its affiliates | [GPL-2.0 with the Classpath Exception](licenses/GPL-2.0-with-Classpath-exception.txt) |
| [Open HTML to PDF](https://github.com/danfickle/openhtmltopdf) 1.0.10 (`openhtmltopdf-core`, `openhtmltopdf-pdfbox`) | The Flying Saucer and Open HTML to PDF contributors | [LGPL-2.1 or later](licenses/LGPL-2.1.txt) |
| [Apache PDFBox](https://pdfbox.apache.org) 2.0.31 (`pdfbox`, `fontbox`), XmpBox 2.0.24 | The Apache Software Foundation | [Apache-2.0](licenses/Apache-2.0.txt) |
| [Apache Commons Logging](https://commons.apache.org/logging/) 1.2 | The Apache Software Foundation | [Apache-2.0](licenses/Apache-2.0.txt) |
| [pdfbox-graphics2d](https://github.com/rototor/pdfbox-graphics2d) 0.32 | Emmeran Seehuber | [Apache-2.0](licenses/Apache-2.0.txt) |
| [Gson](https://github.com/google/gson) 2.11.0, Error Prone annotations 2.27.0 | Google LLC | [Apache-2.0](licenses/Apache-2.0.txt) |
| [Ikonli](https://kordamp.org/ikonli/) 12.3.1 (core, JavaFX, Bootstrap Icons and Feather packs) | Andres Almiray | [Apache-2.0](licenses/Apache-2.0.txt) |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) 2.2 | SnakeYAML authors | [Apache-2.0](licenses/Apache-2.0.txt) |
| [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) 3.46.0.0 | Taro L. Saito and contributors | [Apache-2.0](licenses/Apache-2.0.txt); the SQLite library it embeds is in the public domain |
| [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) 3.15.3 (`imageio-webp` and its common modules) | Harald Kuhr | [BSD-3-Clause](licenses/BSD-3-Clause.txt) |
| [ControlsFX](https://controlsfx.github.io) 11.1.2 | ControlsFX | [BSD-3-Clause](licenses/BSD-3-Clause.txt) |
| [commonmark-java](https://github.com/commonmark/commonmark-java) 0.21.0, with its autolink, GFM strikethrough, GFM tables and task list extensions | Atlassian Pty Ltd and contributors | [BSD-2-Clause](licenses/BSD-2-Clause.txt) |
| [autolink-java](https://github.com/robinst/autolink-java) 0.10.0 | Robin Stocker | [MIT](licenses/MIT.txt) |
| [jsoup](https://jsoup.org) 1.17.2 | Jonathan Hedley | [MIT](licenses/MIT.txt) |
| [SLF4J](https://www.slf4j.org) 2.0.16 (`slf4j-api`, `slf4j-jdk14`) | QOS.ch Sarl | [MIT](licenses/MIT.txt) |

The icon fonts in the Ikonli packs are [Bootstrap Icons](https://icons.getbootstrap.com)
(© The Bootstrap Authors) and [Feather](https://feathericons.com) (© Cole Bemis), both
[MIT](licenses/MIT.txt).

**Open HTML to PDF (LGPL).** Jylos uses it unmodified, as a library, for PDF export. Its source
code is at the link above. The classes keep their original package names inside the Jylos JAR,
so it can be replaced with another build of the library.

The installers built with `jpackage` also include a Java runtime from the JDK. Its own notices
are in the runtime's `legal/` folder.
