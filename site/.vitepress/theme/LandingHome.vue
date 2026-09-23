<script setup lang="ts">
import { computed, ref } from "vue";
import { useData, withBase } from "vitepress";

type FeatureCard = { title: string; image: string; alt: string };
type FeatureSection = {
  id?: string;
  icon: "pen" | "network" | "board" | "sparkle";
  label: string;
  title: string;
  description: string;
  cards: FeatureCard[];
};
type Feature = { icon: string; title: string; text: string };
type PluginCard = { title: string; text: string; bullets: string[] };
type DocsLink = { icon: "rocket" | "network" | "workflow" | "refresh"; title: string; text: string; link: string };
type SimpleItem = { title: string; text: string };
type DownloadPlatform = {
  platform: string;
  subtitle: string;
  icon: "windows" | "apple" | "linux";
  links: { label: string; href: string }[];
};

type Copy = {
  heroTitle: string;
  heroLede: string;
  heroDownload: string;
  heroGithub: string;
  heroDocs: string;
  heroNote: string;
  badges: string[];
  downloadLabel: string;
  downloadTitle: string;
  downloadSubtitle: string;
  downloadPlatforms: DownloadPlatform[];
  jbangLabel: string;
  jbangHint: string;
  copy: string;
  copied: string;
  allReleases: string;
  featuresLabel: string;
  featuresTitle: string;
  featuresSubtitle: string;
  features: Feature[];
  featureSections: FeatureSection[];
  pluginsLabel: string;
  pluginsTitle: string;
  pluginsSubtitle: string;
  plugins: PluginCard[];
  docsLabel: string;
  docsTitle: string;
  docsText: string;
  docsLinks: DocsLink[];
  principlesLabel: string;
  principlesTitle: string;
  principlesSubtitle: string;
  principlesItems: SimpleItem[];
  ctaTitle: string;
  ctaText: string;
  ctaButton: string;
};

const githubUrl = "https://github.com/RGiskard7/jylos";
const releasesBase = `${githubUrl}/releases/latest/download`;

const downloadPlatforms: DownloadPlatform[] = [
  {
    platform: "Windows",
    subtitle: "Windows 10/11 · x64",
    icon: "windows",
    links: [
      { label: ".exe installer", href: `${releasesBase}/jylos-windows-x64.exe` },
      { label: ".msi installer", href: `${releasesBase}/jylos-windows-x64.msi` },
      { label: "Portable ZIP", href: `${releasesBase}/jylos-windows-portable.zip` },
    ],
  },
  {
    platform: "Linux",
    subtitle: "x64 · deb · rpm",
    icon: "linux",
    links: [
      { label: ".deb (Debian / Ubuntu)", href: `${releasesBase}/jylos-linux-amd64.deb` },
      { label: ".rpm (Fedora / RHEL / openSUSE)", href: `${releasesBase}/jylos-linux-amd64.rpm` },
    ],
  },
  {
    platform: "macOS",
    subtitle: "no native installer yet",
    icon: "apple",
    links: [{ label: "uber-JAR (Java 21 + JavaFX)", href: `${releasesBase}/jylos-uber.jar` }],
  },
];

const en: Copy = {
  heroTitle: "Your knowledge, local and yours.",
  heroLede:
    "A local-first desktop app for Markdown notes, wiki-links, backlinks, knowledge graphs, Kanban, Canvas, plugins and Git sync.",
  heroDownload: "Download Jylos",
  heroGithub: "Check on GitHub",
  heroDocs: "Read the docs",
  heroNote: "Free and MIT-licensed — no account, no cloud, no telemetry",
  badges: ["MIT License", "Java 21", "JavaFX 23", "Local-first", "Offline", "SQLite or Markdown vault", "Windows · macOS · Linux"],
  downloadLabel: "Download",
  downloadTitle: "Get Jylos",
  downloadSubtitle:
    "Packages for the current release for Windows, macOS and Linux — plus a JBang launcher if you prefer to run it without installing.",
  downloadPlatforms,
  jbangLabel: "Run instantly with JBang",
  jbangHint: "JBang downloads Java 21 and JavaFX automatically if needed. No build required.",
  copy: "Copy",
  copied: "Copied",
  allReleases: "View all releases",
  featuresLabel: "Features",
  featuresTitle: "More than a notes app",
  featuresSubtitle: "Notes, wiki-links, a knowledge graph, Kanban, Canvas, plugins and Git — all offline.",
  features: [
    { icon: "editor", title: "Markdown Editor", text: "WYSIWYG: type Markdown and it renders as you type — GFM tables, KaTeX math and emoji." },
    { icon: "wiki", title: "Wiki-links", text: "Wiki-link autocomplete, backlinks panel and click-to-open navigation between notes." },
    { icon: "graph", title: "Knowledge Graph", text: "Interactive force-directed graph. Zoom, pan, drag nodes, local neighbourhood views." },
    { icon: "board", title: "Canvas & Kanban", text: "Obsidian-compatible Canvas boards and Kanban workflows for projects and planning." },
    { icon: "git", title: "Git Sync", text: "Stage, commit, push and pull from a unified panel. Full Git integration in vault mode." },
    { icon: "lock", title: "AES-256 Encryption", text: "Encrypt note bodies with AES-256-GCM behind a master password, with delete protection." },
    { icon: "storage", title: "Storage Freedom", text: "SQLite database or plain Markdown vault. Switch anytime, your choice." },
    { icon: "plugin", title: "Plugins & Themes", text: "Plugin JARs, Mermaid diagrams, CSS themes, snippets and a plugin manager." },
    { icon: "search", title: "Advanced Search", text: "Operators, date shorthands, command palette and quick switcher." },
    { icon: "workspace", title: "Workspaces", text: "Save and restore open tabs, view state and layout per context." },
  ],
  featureSections: [
    {
      id: "editor",
      icon: "pen",
      label: "Editor",
      title: "Write in Markdown, read it rendered",
      description:
        "Built on CodeMirror 6, it renders as you type — true WYSIWYG. You see GFM tables, KaTeX math, emoji and `![[embeds]]` live, while the Markdown source stays available when you want it.",
      cards: [
        { title: "Live Preview editor", image: "interfaz-24.png", alt: "Jylos editor and preview" },
        { title: "Tabs, split views and source mode", image: "interfaz-26.png", alt: "Tabs, split views and source mode" },
        { title: "Mermaid diagrams in real time", image: "interfaz-25.png", alt: "Mermaid diagrams" },
      ],
    },
    {
      icon: "network",
      label: "Knowledge graph",
      title: "A map of every link in your notes",
      description:
        "A force-directed graph built from your wiki-links. Zoom, pan and drag, open any note with a click, and switch between the whole vault and the local neighbourhood of the note you are reading. Knowledge Insights also reports orphan notes, broken links and a graph health score.",
      cards: [
        { title: "Interactive global graph", image: "interfaz-27.png", alt: "Knowledge graph" },
        { title: "Local neighbourhoods", image: "interfaz-28.png", alt: "Knowledge graph zoom" },
      ],
    },
    {
      icon: "board",
      label: "Organize",
      title: "Kanban and Canvas, in plain notes",
      description:
        "A board is just a note whose headings are columns and its list items are cards — drag cards across columns, set WIP limits and colors. The Canvas editor opens Obsidian-compatible `.canvas` files on an infinite surface.",
      cards: [
        { title: "Kanban boards inside notes", image: "interfaz-29.png", alt: "Kanban board" },
        { title: "Obsidian-compatible Canvas", image: "interfaz-32.png", alt: "Canvas editor" },
      ],
    },
    {
      icon: "sparkle",
      label: "Extensible",
      title: "Plugins, themes and snippets",
      description:
        "Dataview, MCP Server and Publish ship built-in; drop external JARs into the plugin folder to add commands, side panels, preview enhancers and toolbar buttons. CSS themes and snippets restyle the app.",
      cards: [
        { title: "Dataview queries", image: "interfaz-30.png", alt: "Dataview plugin" },
        { title: "Plugin manager", image: "interfaz-31.png", alt: "Plugins" },
      ],
    },
  ],
  pluginsLabel: "Power features",
  pluginsTitle: "Ask your notes, connect your AI, publish your vault",
  pluginsSubtitle: "Three built-in tools that go beyond note-taking — on by default.",
  plugins: [
    {
      title: "Dataview",
      text: "Query your notes' metadata like a database. A dataview block renders live in the preview.",
      bullets: [
        "TABLE / LIST / TASK queries, with FROM, WHERE, SORT, GROUP BY, FLATTEN and LIMIT",
        "Reads frontmatter, inline key:: value fields, tags and the implicit file.* attributes",
        "Inline = expression results anywhere in a note's text",
      ],
    },
    {
      title: "MCP Server",
      text: "Exposes your vault to Claude Desktop, Claude Code and any Streamable HTTP MCP client over a local server.",
      bullets: [
        "Search notes, read full content, create notes and replace a note's body",
        "Runs entirely on your machine — no cloud round-trip, no vault upload",
        "Read-mostly by design: no irreversible delete exposed to clients",
      ],
    },
    {
      title: "Publish",
      text: "Exports the whole vault to a self-contained static site — ready for GitHub Pages or any static host.",
      bullets: [
        "Wiki-links become real relative links; an interactive graph, full and per-note",
        "Folder tree, live search and a picker to choose exactly what gets included",
        "Publish just one changed note to update an already-live site",
      ],
    },
  ],
  docsLabel: "Documentation",
  docsTitle: "Learn the app, then make it yours",
  docsText: "Guides and technical docs live with the code, so behaviour and guidance evolve together.",
  docsLinks: [
    { icon: "rocket", title: "Install and get started", text: "Download Jylos, run it for the first time and create your first notes.", link: "/start/install" },
    { icon: "network", title: "Notes, links and the graph", text: "How notes, wiki-links, backlinks, Kanban, Canvas and Git work together.", link: "/guides/creating-notes" },
    { icon: "workflow", title: "Capture, plan, publish", text: "Write and link notes, organize with Kanban, and publish your vault as a static site.", link: "/guides/wiki-links" },
    { icon: "refresh", title: "Technical reference", text: "Architecture, plugins, storage and event contracts for contributors.", link: "/reference/" },
  ],
  principlesLabel: "Principles",
  principlesTitle: "Local-first by design",
  principlesSubtitle: "Everything stays on your machine — plain files, open formats, no cloud, no tracking.",
  principlesItems: [
    { title: "Stored locally", text: "SQLite or plain Markdown files on your disk. Your notes never leave your computer." },
    { title: "No account, no telemetry", text: "No sign-up, no login, no analytics." },
    { title: "No cloud backend", text: "There is no server to call. Ever." },
    { title: "Open source (MIT)", text: "Audit the code, modify it, contribute back." },
    { title: "Markdown-native", text: "Plain text with YAML frontmatter, no proprietary lock-in." },
    { title: "Cross-platform", text: "Windows, macOS and Linux." },
  ],
  ctaTitle: "Open source and actively maintained",
  ctaText: "Jylos is released under MIT and built with Java 21 and JavaFX 23. Issues, feedback and pull requests are welcome.",
  ctaButton: "Contribute on GitHub",
};

const es: Copy = {
  heroTitle: "Tu conocimiento, local y tuyo.",
  heroLede:
    "Una aplicación de escritorio local-first para notas Markdown, wiki-links, backlinks, grafos de conocimiento, Kanban, Canvas, plugins y sincronización Git.",
  heroDownload: "Descargar Jylos",
  heroGithub: "Ver en GitHub",
  heroDocs: "Leer la documentación",
  heroNote: "Gratis y con licencia MIT — sin cuenta, sin nube, sin telemetría",
  badges: ["Licencia MIT", "Java 21", "JavaFX 23", "Local-first", "Sin conexión", "SQLite o vault Markdown", "Windows · macOS · Linux"],
  downloadLabel: "Descarga",
  downloadTitle: "Consigue Jylos",
  downloadSubtitle:
    "Paquetes de la versión actual para Windows, macOS y Linux — más un lanzador con JBang si prefieres ejecutarlo sin instalar.",
  downloadPlatforms: [
    {
      platform: "Windows",
      subtitle: "Windows 10/11 · x64",
      icon: "windows",
      links: [
        { label: "Instalador .exe", href: `${releasesBase}/jylos-windows-x64.exe` },
        { label: "Instalador .msi", href: `${releasesBase}/jylos-windows-x64.msi` },
        { label: "ZIP portable", href: `${releasesBase}/jylos-windows-portable.zip` },
      ],
    },
    {
      platform: "Linux",
      subtitle: "x64 · deb · rpm",
      icon: "linux",
      links: [
        { label: ".deb (Debian / Ubuntu)", href: `${releasesBase}/jylos-linux-amd64.deb` },
        { label: ".rpm (Fedora / RHEL / openSUSE)", href: `${releasesBase}/jylos-linux-amd64.rpm` },
      ],
    },
    {
      platform: "macOS",
      subtitle: "sin instalador nativo todavía",
      icon: "apple",
      links: [{ label: "uber-JAR (Java 21 + JavaFX)", href: `${releasesBase}/jylos-uber.jar` }],
    },
  ],
  jbangLabel: "Ejecútalo al instante con JBang",
  jbangHint: "JBang descarga Java 21 y JavaFX automáticamente si hace falta. Sin compilar nada.",
  copy: "Copiar",
  copied: "Copiado",
  allReleases: "Ver todas las versiones",
  featuresLabel: "Funciones",
  featuresTitle: "Más que una app de notas",
  featuresSubtitle: "Notas, wiki-links, un grafo de conocimiento, Kanban, Canvas, plugins y Git — todo sin conexión.",
  features: [
    { icon: "editor", title: "Editor Markdown", text: "WYSIWYG: escribe Markdown y se renderiza al instante — tablas GFM, fórmulas KaTeX y emoji." },
    { icon: "wiki", title: "Wiki-links", text: "Autocompletado de wiki-links, panel de backlinks y navegación entre notas con un clic." },
    { icon: "graph", title: "Grafo de conocimiento", text: "Grafo interactivo por fuerzas. Zoom, paneo, arrastre de nodos, vistas de vecindario local." },
    { icon: "board", title: "Canvas y Kanban", text: "Canvas compatible con Obsidian y flujos Kanban para proyectos y planificación." },
    { icon: "git", title: "Sincronización Git", text: "Prepara, confirma, haz push y pull desde un panel unificado. Integración Git completa en modo vault." },
    { icon: "lock", title: "Cifrado AES-256", text: "Cifra el contenido de las notas con AES-256-GCM tras una contraseña maestra, con protección de borrado." },
    { icon: "storage", title: "Libertad de almacenamiento", text: "Base de datos SQLite o vault Markdown plano. Cambia cuando quieras." },
    { icon: "plugin", title: "Plugins y temas", text: "Plugins JAR, diagramas Mermaid, temas CSS, snippets y gestor de plugins." },
    { icon: "search", title: "Búsqueda avanzada", text: "Operadores, atajos de fecha, paleta de comandos y selector rápido." },
    { icon: "workspace", title: "Workspaces", text: "Guarda y restaura pestañas abiertas, estado de vistas y layout por contexto." },
  ],
  featureSections: [
    {
      id: "editor",
      icon: "pen",
      label: "Editor",
      title: "Escribe en Markdown, visualízalo al instante",
      description:
        "Construido sobre CodeMirror 6, renderiza mientras escribes — WYSIWYG de verdad. Ves tablas GFM, fórmulas KaTeX, emoji y `![[embebidos]]` en vivo, y el código Markdown sigue disponible cuando lo necesitas.",
      cards: [
        { title: "Editor con Live Preview", image: "interfaz-24.png", alt: "Editor y vista previa de Jylos" },
        { title: "Pestañas, vistas divididas y modo fuente", image: "interfaz-26.png", alt: "Pestañas, vistas divididas y modo fuente" },
        { title: "Diagramas Mermaid en tiempo real", image: "interfaz-25.png", alt: "Diagramas Mermaid" },
      ],
    },
    {
      icon: "network",
      label: "Grafo de conocimiento",
      title: "Un mapa de cada enlace de tus notas",
      description:
        "Un grafo por fuerzas construido a partir de tus wiki-links. Haz zoom, panea y arrastra, abre cualquier nota con un clic, y alterna entre el vault completo y el vecindario local de la nota que estás leyendo. Knowledge Insights también detecta notas huérfanas, enlaces rotos y una puntuación de salud del grafo.",
      cards: [
        { title: "Grafo global interactivo", image: "interfaz-27.png", alt: "Grafo de conocimiento" },
        { title: "Vecindarios locales", image: "interfaz-28.png", alt: "Zoom del grafo de conocimiento" },
      ],
    },
    {
      icon: "board",
      label: "Organiza",
      title: "Kanban y Canvas, en notas normales",
      description:
        "Un tablero es solo una nota cuyos encabezados son columnas y sus elementos de lista son tarjetas — arrastra tarjetas entre columnas, define límites WIP y colores. El editor Canvas abre ficheros `.canvas` compatibles con Obsidian sobre una superficie infinita.",
      cards: [
        { title: "Tableros Kanban dentro de notas", image: "interfaz-29.png", alt: "Tablero Kanban" },
        { title: "Canvas compatible con Obsidian", image: "interfaz-32.png", alt: "Editor Canvas" },
      ],
    },
    {
      icon: "sparkle",
      label: "Extensible",
      title: "Plugins, temas y snippets",
      description:
        "Dataview, MCP Server y Publish vienen integrados; suelta JARs externos en la carpeta de plugins para añadir comandos, paneles laterales, mejoras de la vista previa y botones de la barra. Los temas CSS y los snippets cambian el aspecto de la app.",
      cards: [
        { title: "Consultas Dataview", image: "interfaz-30.png", alt: "Plugin Dataview" },
        { title: "Gestor de plugins", image: "interfaz-31.png", alt: "Plugins" },
      ],
    },
  ],
  pluginsLabel: "Capacidades destacadas",
  pluginsTitle: "Consulta tus notas, conecta tu IA, publica tu vault",
  pluginsSubtitle: "Tres herramientas integradas que van más allá de tomar notas — activadas por defecto.",
  plugins: [
    {
      title: "Dataview",
      text: "Consulta los metadatos de tus notas como una base de datos. Un bloque dataview se renderiza en vivo en la vista previa.",
      bullets: [
        "Consultas TABLE / LIST / TASK, con FROM, WHERE, SORT, GROUP BY, FLATTEN y LIMIT",
        "Lee frontmatter, campos inline key:: value, etiquetas y los atributos implícitos file.*",
        "Resultados inline con = expresión en cualquier parte del texto de una nota",
      ],
    },
    {
      title: "Servidor MCP",
      text: "Expone tu vault a Claude Desktop, Claude Code y cualquier cliente MCP con Streamable HTTP mediante un servidor local.",
      bullets: [
        "Busca notas, lee su contenido completo, crea notas y reemplaza el cuerpo de una nota",
        "Se ejecuta enteramente en tu máquina — sin ida y vuelta a la nube, sin subir el vault",
        "Pensado para ser mayormente de lectura: no expone ningún borrado irreversible",
      ],
    },
    {
      title: "Publish",
      text: "Exporta todo el vault a un sitio web estático autocontenido — listo para GitHub Pages o cualquier hosting estático.",
      bullets: [
        "Los wiki-links se convierten en enlaces relativos reales; grafo interactivo, completo y por nota",
        "Árbol de carpetas, búsqueda en vivo y un selector para elegir exactamente qué se incluye",
        "Publica solo la nota que has cambiado para actualizar un sitio ya publicado",
      ],
    },
  ],
  docsLabel: "Documentación",
  docsTitle: "Aprende la app y hazla tuya",
  docsText: "Las guías y las docs técnicas viven con el código, para que comportamiento y guía evolucionen juntos.",
  docsLinks: [
    { icon: "rocket", title: "Instalar y empezar", text: "Descarga Jylos, ejecútalo por primera vez y crea tus primeras notas.", link: "/start/install" },
    { icon: "network", title: "Notas, enlaces y el grafo", text: "Cómo funcionan juntos notas, wiki-links, backlinks, Kanban, Canvas y Git.", link: "/guides/creating-notes" },
    { icon: "workflow", title: "Captura, planifica, publica", text: "Escribe y enlaza notas, organízalas con Kanban y publica tu vault como sitio estático.", link: "/guides/wiki-links" },
    { icon: "refresh", title: "Referencia técnica", text: "Arquitectura, plugins, almacenamiento y contratos de eventos para contribuidores.", link: "/reference/" },
  ],
  principlesLabel: "Principios",
  principlesTitle: "Local-first por diseño",
  principlesSubtitle: "Todo se queda en tu máquina — archivos planos, formatos abiertos, sin nube, sin seguimiento.",
  principlesItems: [
    { title: "Guardado en local", text: "SQLite o archivos Markdown planos en tu disco. Tus notas nunca salen de tu ordenador." },
    { title: "Sin cuenta ni telemetría", text: "Sin registro, sin inicio de sesión, sin analíticas." },
    { title: "Sin backend en la nube", text: "No existe ningún servidor al que llamar. Nunca." },
    { title: "Código abierto (MIT)", text: "Audita el código, modifícalo y contribuye de vuelta." },
    { title: "Nativo en Markdown", text: "Texto plano con frontmatter YAML, sin bloqueo en formatos propietarios." },
    { title: "Multiplataforma", text: "Windows, macOS y Linux." },
  ],
  ctaTitle: "Código abierto y mantenimiento activo",
  ctaText: "Jylos se publica bajo licencia MIT y está construido con Java 21 y JavaFX 23. Los issues, el feedback y los pull requests son bienvenidos.",
  ctaButton: "Contribuir en GitHub",
};

const { lang } = useData();
const isEs = computed(() => lang.value?.startsWith("es") ?? false);
const t = computed(() => (isEs.value ? es : en));

const asset = (path: string) => withBase(`/landing/${path}`);
const route = (path: string) => withBase(`${isEs.value ? "/es" : ""}${path}`);

const jbangCode = "jbang jylos@RGiskard7/jylos";
const copied = ref(false);

const copyJbang = async () => {
  try {
    await navigator.clipboard.writeText(jbangCode);
    copied.value = true;
    setTimeout(() => (copied.value = false), 1800);
  } catch {
    // Clipboard unavailable.
  }
};
</script>

<template>
  <main class="jylos-landing">
    <section class="landing-container hero-section">
      <div class="hero-grid">
        <div class="hero-left">
          <h1>{{ t.heroTitle }}</h1>
          <p class="hero-lede">{{ t.heroLede }}</p>
          <div class="hero-actions">
        <a class="landing-button primary" href="#download">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 3v11m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
          </svg>
          {{ t.heroDownload }}
        </a>
        <a class="landing-button secondary" :href="githubUrl" target="_blank" rel="noopener noreferrer">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.1-1.3-.3-2.6-1.2-3.6.2-1.1.2-2.3-.1-3.4 0 0-1 0-3.3 1.2a11.5 11.5 0 0 0-6 0C7 2 6 2 6 2c-.3 1.1-.3 2.3-.1 3.4A5 5 0 0 0 4.7 9c0 3.5 3 5.5 6 5.5a4.8 4.8 0 0 0-1 3.5v4" />
            <path d="M9 18c-4.5 2-5-2-7-2" />
          </svg>
          {{ t.heroGithub }}
        </a>
        <a class="landing-button secondary" :href="route('/start/install')">{{ t.heroDocs }}</a>
      </div>
      <div class="hero-badges">
        <span v-for="badge in t.badges" :key="badge" class="badge">{{ badge }}</span>
      </div>
      <p class="hero-note">{{ t.heroNote }}</p>
        </div>
        <div class="hero-right">
          <img :src="asset('logo.png')" alt="Jylos" draggable="false" />
        </div>
      </div>
    </section>

    <section class="landing-container hero-screenshot">
      <div class="screenshot-frame">
        <img class="screenshot-image" :src="asset('interfaz-24.png')" alt="Jylos editor and preview" draggable="false" />
      </div>
    </section>

    <section id="download" class="download-band">
      <div class="landing-container download-inner">
        <div class="section-heading compact">
          <span class="section-label">{{ t.downloadLabel }}</span>
          <h2>{{ t.downloadTitle }}</h2>
          <p>{{ t.downloadSubtitle }}</p>
        </div>
        <div class="download-grid">
          <div v-for="platform in t.downloadPlatforms" :key="platform.platform" class="download-card">
            <svg v-if="platform.icon === 'windows'" class="download-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M0 3.449L9.75 2.1v9.451H0V3.449zm10.949-1.4L24 0v11.551H10.949V2.049zM0 12.949h9.75V22.5L0 21.051V12.949zm10.949 0H24V24l-13.051-1.451V12.949z" />
            </svg>
            <svg v-else-if="platform.icon === 'apple'" class="download-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z" />
            </svg>
            <template v-else>
              <img class="download-icon download-icon-linux linux-dark" src="https://cdn.simpleicons.org/linux/a78bfa" alt="Linux" />
              <img class="download-icon download-icon-linux linux-light" src="https://cdn.simpleicons.org/linux/4f46e5" alt="" aria-hidden="true" />
            </template>
            <div class="download-card-title">{{ platform.platform }}</div>
            <div class="download-card-subtitle">{{ platform.subtitle }}</div>
            <div class="download-card-btns">
              <a v-for="link in platform.links" :key="link.label" class="download-link" :href="link.href">{{ link.label }}</a>
            </div>
          </div>
        </div>
        <div class="jbang-card">
          <svg class="jbang-icon" viewBox="0 0 24 24" aria-hidden="true">
            <rect x="2" y="2" width="20" height="20" rx="4" />
            <path d="M7 8h10M7 12h10M7 16h6" />
          </svg>
          <div class="jbang-text">
            <div class="jbang-label">{{ t.jbangLabel }}</div>
            <div class="jbang-code-wrap">
              <code class="jbang-code">{{ jbangCode }}</code>
              <button class="btn-copy" type="button" @click="copyJbang">{{ copied ? t.copied : t.copy }}</button>
            </div>
            <div class="jbang-hint">{{ t.jbangHint }}</div>
          </div>
        </div>
        <div class="download-more">
          <a class="landing-button secondary" :href="`${githubUrl}/releases/latest`" target="_blank" rel="noopener noreferrer">{{ t.allReleases }}</a>
        </div>
      </div>
    </section>

    <section id="features" class="features-band">
      <div class="landing-container">
        <div class="section-heading">
          <span class="section-label">{{ t.featuresLabel }}</span>
          <h2>{{ t.featuresTitle }}</h2>
          <p>{{ t.featuresSubtitle }}</p>
        </div>
        <div class="features-grid">
          <div v-for="feature in t.features" :key="feature.title" class="feature-card">
            <svg class="feature-icon" viewBox="0 0 24 24" aria-hidden="true">
              <template v-if="feature.icon === 'editor'">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <path d="M14 2v6h6M16 13H8M16 17H8" />
              </template>
              <template v-else-if="feature.icon === 'wiki'">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
              </template>
              <template v-else-if="feature.icon === 'graph'">
                <circle cx="6" cy="6" r="3" /><circle cx="18" cy="6" r="3" /><circle cx="12" cy="18" r="3" />
                <path d="M8.6 7.6 10.8 15M15.4 7.6 13.2 15M9 6h6" />
              </template>
              <template v-else-if="feature.icon === 'board'">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <path d="M3 9h18M9 21V9" />
              </template>
              <template v-else-if="feature.icon === 'git'">
                <line x1="6" x2="6" y1="3" y2="15" /><circle cx="18" cy="6" r="3" /><circle cx="6" cy="18" r="3" />
                <path d="M18 9a9 9 0 0 1-9 9" />
              </template>
              <template v-else-if="feature.icon === 'lock'">
                <rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </template>
              <template v-else-if="feature.icon === 'storage'">
                <ellipse cx="12" cy="6" rx="8" ry="3" /><path d="M4 6v6c0 1.66 3.58 3 8 3s8-1.34 8-3V6" /><path d="M4 12v6c0 1.66 3.58 3 8 3s8-1.34 8-3v-6" />
              </template>
              <template v-else-if="feature.icon === 'plugin'">
                <path d="M12 2l10 6.5v7L12 22 2 15.5v-7L12 2z" />
              </template>
              <template v-else-if="feature.icon === 'search'">
                <circle cx="11" cy="11" r="8" /><path d="M21 21l-4.35-4.35" />
              </template>
              <template v-else>
                <rect x="3" y="3" width="18" height="18" rx="2" /><path d="M3 9h18M9 21V9" />
              </template>
            </svg>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.text }}</p>
          </div>
        </div>
      </div>
    </section>

    <section
      v-for="(section, i) in t.featureSections"
      :id="section.id"
      :key="section.title"
      class="landing-container feature-section"
      :class="{ last: i === t.featureSections.length - 1 }"
    >
      <div class="section-heading">
        <span class="section-label">
          <svg v-if="section.icon === 'pen'" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z" />
          </svg>
          <svg v-else-if="section.icon === 'network'" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="6" cy="6" r="3" />
            <circle cx="18" cy="6" r="3" />
            <circle cx="12" cy="18" r="3" />
            <path d="M8.6 7.6 10.8 15" />
            <path d="M15.4 7.6 13.2 15" />
            <path d="M9 6h6" />
          </svg>
          <svg v-else-if="section.icon === 'board'" viewBox="0 0 24 24" aria-hidden="true">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <path d="M3 9h18" />
            <path d="M9 21V9" />
          </svg>
          <svg v-else viewBox="0 0 256 256" aria-hidden="true" class="filled-icon">
            <path d="M208,144a15.78,15.78,0,0,1-10.42,14.94L146,178l-19.05,51.56a15.92,15.92,0,0,1-29.88,0L78,178,26.42,158.94a15.92,15.92,0,0,1,0-29.88L78,110l19.05-51.56a15.92,15.92,0,0,1,29.88,0L146,110l51.56,19.05A15.78,15.78,0,0,1,208,144ZM152,48h16V64a8,8,0,0,0,16,0V48h16a8,8,0,0,0,0-16H184V16a8,8,0,0,0-16,0V32H152a8,8,0,0,0,0,16Zm88,32h-8V72a8,8,0,0,0-16,0v8h-8a8,8,0,0,0,0,16h8v8a8,8,0,0,0,16,0V96h8a8,8,0,0,0,0-16Z" />
          </svg>
          {{ section.label }}
        </span>
        <h2>{{ section.title }}</h2>
        <p>{{ section.description }}</p>
      </div>
      <div class="feature-grid" :class="{ 'three-card-grid': section.cards.length === 3 }">
        <article v-for="card in section.cards" :key="card.title" class="feature-card-image">
          <div class="feature-window-bar">
            <span class="feature-window-title">{{ card.title }}</span>
          </div>
          <div class="feature-image">
            <img :src="asset(card.image)" :alt="card.alt" loading="lazy" />
          </div>
        </article>
      </div>
    </section>

    <section id="plugins" class="plugins-band">
      <div class="landing-container">
        <div class="section-heading">
          <span class="section-label">{{ t.pluginsLabel }}</span>
          <h2>{{ t.pluginsTitle }}</h2>
          <p>{{ t.pluginsSubtitle }}</p>
        </div>
        <div class="plugins-grid">
          <div v-for="plugin in t.plugins" :key="plugin.title" class="plugin-card">
            <h3>{{ plugin.title }}</h3>
            <p>{{ plugin.text }}</p>
            <ul>
              <li v-for="bullet in plugin.bullets" :key="bullet">{{ bullet }}</li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <section id="docs" class="docs-band">
      <div class="landing-container docs-inner">
        <div class="section-heading compact">
          <span class="section-label">{{ t.docsLabel }}</span>
          <h2>{{ t.docsTitle }}</h2>
          <p>{{ t.docsText }}</p>
        </div>
        <div class="docs-grid">
          <a v-for="link in t.docsLinks" :key="link.title" class="docs-card" :href="route(link.link)">
            <span class="docs-icon">
              <svg v-if="link.icon === 'rocket'" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4.5 16.5c-1 1-1.5 2.7-1.5 4.5 1.8 0 3.5-.5 4.5-1.5" />
                <path d="M9 15 15 9" />
                <path d="M15 4.5c2-.7 4-.8 6-.5.3 2 .2 4-.5 6L15 15l-6-6z" />
                <path d="M9 15H5l4-6v6z" />
                <path d="M15 9v6l-6 4v-4" />
              </svg>
              <svg v-else-if="link.icon === 'network'" viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="6" cy="6" r="3" />
                <circle cx="18" cy="6" r="3" />
                <circle cx="12" cy="18" r="3" />
                <path d="M8.6 7.6 10.8 15" />
                <path d="M15.4 7.6 13.2 15" />
                <path d="M9 6h6" />
              </svg>
              <svg v-else-if="link.icon === 'workflow'" viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="4" width="6" height="6" rx="1.5" />
                <rect x="15" y="14" width="6" height="6" rx="1.5" />
                <path d="M9 7h3a3 3 0 0 1 3 3v4" />
                <path d="m12 11 3 3 3-3" />
              </svg>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 12a9 9 0 0 1 15.4-6.4L21 8" />
                <path d="M21 3v5h-5" />
                <path d="M21 12a9 9 0 0 1-15.4 6.4L3 16" />
                <path d="M3 21v-5h5" />
              </svg>
            </span>
            <h3>{{ link.title }}</h3>
            <p>{{ link.text }}</p>
          </a>
        </div>
      </div>
    </section>

    <section class="principles-band">
      <div class="landing-container principles-inner">
        <div class="section-heading compact">
          <span class="section-label">{{ t.principlesLabel }}</span>
          <h2>{{ t.principlesTitle }}</h2>
          <p>{{ t.principlesSubtitle }}</p>
        </div>
        <div class="principles-grid">
          <div v-for="item in t.principlesItems" :key="item.title" class="principles-item">
            <h4>{{ item.title }}</h4>
            <p>{{ item.text }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="final-cta">
      <div class="landing-container final-cta-inner">
        <h2>{{ t.ctaTitle }}</h2>
        <p>{{ t.ctaText }}</p>
        <a class="landing-button primary" :href="githubUrl" target="_blank" rel="noopener noreferrer">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.1-1.3-.3-2.6-1.2-3.6.2-1.1.2-2.3-.1-3.4 0 0-1 0-3.3 1.2a11.5 11.5 0 0 0-6 0C7 2 6 2 6 2c-.3 1.1-.3 2.3-.1 3.4A5 5 0 0 0 4.7 9c0 3.5 3 5.5 6 5.5a4.8 4.8 0 0 0-1 3.5v4" />
            <path d="M9 18c-4.5 2-5-2-7-2" />
          </svg>
          {{ t.ctaButton }}
        </a>
        <p class="cta-copyright">© 2025–2026 Eduardo Díaz Sánchez (RGiskard7)</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.jylos-landing {
  --landing-bg: var(--jylos-bg);
  --landing-surface: var(--jylos-surface);
  --landing-dark: #0c0c22;
  --landing-text: var(--jylos-text);
  --landing-muted: var(--jylos-text-secondary);
  --landing-tertiary: var(--jylos-text-muted);
  --landing-border: var(--jylos-border);
  --landing-accent: var(--jylos-purple);
  --landing-accent-hover: var(--jylos-purple-hover);
  --landing-accent-soft: var(--jylos-purple-soft);
  --landing-accent-border: color-mix(in srgb, var(--landing-accent) 22%, transparent);
  --landing-gradient: linear-gradient(135deg, #4755c2, #5563d6 55%, #6b79e0);
  --landing-btn-gradient: linear-gradient(135deg, #4755c2, #5563d6);
  --landing-page-width: 1280px;
  color: var(--landing-text);
  background: var(--landing-bg);
}

.dark .jylos-landing {
  --landing-gradient: linear-gradient(135deg, #8ea0ff, #a3b3ff 55%, #b4c0ff);
}

.landing-container {
  width: min(100%, var(--landing-page-width));
  margin: 0 auto;
  padding-right: 20px;
  padding-left: 20px;
}

.hero-section {
  padding-top: 40px;
  padding-bottom: 52px;
}

.hero-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
  align-items: center;
}

.hero-right {
  display: flex;
  justify-content: center;
}

.hero-right img {
  width: 160px;
  height: 160px;
}

.hero-section h1 {
  max-width: 820px;
  margin: 0;
  color: var(--landing-text);
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.12;
  background: var(--landing-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

.hero-lede {
  max-width: 900px;
  margin: 12px 0 0;
  color: var(--landing-muted);
  font-size: 20px;
  letter-spacing: 0;
  line-height: 1.4;
}

.hero-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 24px;
}

.landing-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  padding: 12px 24px;
  border: 1px solid transparent;
  border-radius: 999px;
  color: var(--landing-text);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0;
  text-decoration: none;
  transition:
    background-color 160ms ease,
    border-color 160ms ease,
    color 160ms ease;
}

.landing-button svg {
  width: 20px;
  height: 20px;
  margin-right: 8px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.landing-button.primary {
  color: #fff;
  background: var(--landing-btn-gradient);
}

.landing-button.primary:hover {
  color: #fff;
  background: var(--landing-btn-gradient);
  filter: brightness(1.08);
}

.landing-button.secondary {
  border-color: var(--landing-border);
  background: var(--landing-surface);
}

.landing-button.secondary:hover {
  color: var(--landing-accent);
  border-color: var(--landing-accent-border);
}

.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 24px;
}

.badge {
  font-size: 13px;
  font-weight: 500;
  color: var(--landing-muted);
  border: 1px solid var(--landing-border);
  border-radius: 999px;
  padding: 4px 13px;
  background: var(--landing-surface);
}

.hero-note {
  margin: 16px 0 0;
  color: var(--landing-tertiary);
  font-size: 14px;
}

.hero-screenshot {
  padding-bottom: 56px;
}

.screenshot-frame {
  position: relative;
  width: min(100%, 1160px);
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--landing-border);
  border-radius: 12px;
  box-shadow: 0 12px 48px -4px rgba(0, 0, 0, 0.3);
  user-select: none;
}

.screenshot-image {
  display: block;
  width: 100%;
  height: auto;
  pointer-events: none;
}

.section-heading {
  max-width: 980px;
  margin-bottom: 32px;
  padding-left: 4px;
}

.section-heading.compact {
  margin-bottom: 0;
}

.section-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--landing-accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  line-height: 1.2;
}

.section-label::before {
  content: "";
  width: 20px;
  height: 2px;
  border-radius: 2px;
  background: var(--landing-accent);
  flex-shrink: 0;
}

.section-label svg {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.section-label .filled-icon {
  fill: currentColor;
  stroke: none;
}

.section-heading h2,
.final-cta h2 {
  margin: 12px 0 0;
  color: var(--landing-text);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.15;
}

.section-heading p,
.final-cta p {
  max-width: 960px;
  margin: 12px 0 0;
  color: var(--landing-muted);
  font-size: 18px;
  letter-spacing: 0;
  line-height: 1.55;
}

.download-band,
.features-band,
.plugins-band,
.docs-band,
.principles-band {
  padding: 72px 0;
  border-top: 1px solid var(--landing-border);
  border-bottom: 1px solid var(--landing-border);
  background: var(--landing-surface);
}

.download-band {
  background: var(--landing-bg);
}

.features-band {
  background: var(--landing-bg);
}

.download-inner,
.docs-inner,
.principles-inner {
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
}

.download-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.download-card {
  padding: 28px 24px;
  border: 1px solid var(--landing-border);
  border-radius: 12px;
  background: var(--landing-surface);
}

.download-icon {
  width: 40px;
  height: 40px;
  margin-bottom: 14px;
  color: var(--landing-accent);
}

.download-icon-linux {
  display: block;
  object-fit: contain;
}

.linux-dark {
  display: none;
}

.dark .linux-dark {
  display: block;
}

.dark .linux-light {
  display: none;
}

.download-card-title {
  margin: 0;
  color: var(--landing-text);
  font-size: 19px;
  font-weight: 700;
}

.download-card-subtitle {
  margin: 4px 0 0;
  color: var(--landing-tertiary);
  font-size: 13px;
}

.download-card-btns {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 18px;
}

.download-link {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--landing-border);
  border-radius: 8px;
  color: var(--landing-text);
  background: var(--landing-bg);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition:
    border-color 160ms ease,
    color 160ms ease,
    background-color 160ms ease;
}

.download-link:hover {
  border-color: var(--landing-accent);
  color: var(--landing-accent);
  background: var(--landing-accent-soft);
}

.jbang-card {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  padding: 24px;
  margin-top: 20px;
  border: 1px solid var(--landing-accent-border);
  border-radius: 12px;
  background: var(--landing-accent-soft);
}

.jbang-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  fill: none;
  stroke: var(--landing-accent);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}

.jbang-text {
  flex: 1;
  min-width: 220px;
}

.jbang-label {
  font-weight: 700;
  font-size: 15px;
  margin-bottom: 8px;
}

.jbang-code-wrap {
  display: flex;
  align-items: center;
  overflow: hidden;
  border: 1px solid var(--landing-accent-border);
  border-radius: 8px;
  background: var(--landing-dark);
}

.jbang-code {
  flex: 1;
  padding: 12px 16px;
  font-family: var(--vp-font-family-mono);
  font-size: 14px;
  color: #dbe1ff;
  overflow-x: auto;
  white-space: nowrap;
  background: transparent;
}

.btn-copy {
  flex-shrink: 0;
  padding: 12px 18px;
  border: none;
  border-left: 1px solid var(--landing-accent-border);
  background: rgba(255, 255, 255, 0.04);
  color: #c3cbfa;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  height: 100%;
}

.btn-copy:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.jbang-hint {
  margin-top: 8px;
  color: var(--landing-tertiary);
  font-size: 13px;
}

.download-more {
  text-align: center;
  margin-top: 20px;
}

.features-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.feature-card {
  padding: 24px 22px;
  border: 1px solid var(--landing-border);
  border-radius: 12px;
  background: var(--landing-surface);
}

.feature-icon {
  width: 28px;
  height: 28px;
  margin-bottom: 14px;
  color: var(--landing-accent);
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}

.feature-card h3 {
  margin: 0;
  color: var(--landing-text);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.feature-card p {
  margin: 8px 0 0;
  color: var(--landing-muted);
  font-size: 14px;
  line-height: 1.5;
}

.feature-section {
  padding-top: 56px;
}

.feature-section.last {
  padding-bottom: 72px;
}

.feature-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.feature-card-image {
  overflow: hidden;
  border: 1px solid var(--landing-border);
  border-radius: 12px;
  background: var(--landing-surface);
  box-shadow: 0 8px 24px -12px rgba(0, 0, 0, 0.3);
}

.feature-window-bar {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid var(--landing-border);
  background: var(--jylos-surface-muted);
}

.feature-window-title {
  color: var(--landing-muted);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.feature-image {
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: #fff;
}

.feature-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
}

.plugins-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  max-width: 980px;
  margin: 0 auto;
}

.plugin-card {
  padding: 28px 26px;
  border: 1px solid var(--landing-border);
  border-radius: 12px;
  background: var(--landing-bg);
}

.plugin-card h3 {
  margin: 0;
  color: var(--landing-text);
  font-size: 18px;
  font-weight: 700;
}

.plugin-card > p {
  margin: 10px 0 0;
  color: var(--landing-muted);
  font-size: 15px;
  line-height: 1.5;
}

.plugin-card ul {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin: 16px 0 0;
  padding: 0;
}

.plugin-card li {
  position: relative;
  padding-left: 18px;
  color: var(--landing-muted);
  font-size: 14px;
  line-height: 1.4;
}

.plugin-card li::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0.5em;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--landing-accent);
  opacity: 0.7;
}

.docs-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.docs-card {
  min-height: 196px;
  padding: 22px;
  border: 1px solid var(--landing-border);
  border-radius: 8px;
  color: var(--landing-text);
  background: var(--landing-bg);
  text-decoration: none;
}

.docs-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  margin-bottom: 24px;
  border-radius: 8px;
  color: var(--landing-accent);
  background: var(--landing-accent-soft);
}

.docs-icon svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.docs-card h3 {
  margin: 0;
  color: var(--landing-text);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0;
}

.docs-card p {
  margin: 10px 0 0;
  color: var(--landing-muted);
  font-size: 15px;
  line-height: 1.5;
}

.principles-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.principles-item {
  padding: 20px;
  border: 1px solid var(--landing-border);
  border-radius: 8px;
  background: var(--landing-bg);
}

.principles-item h4 {
  margin: 0;
  color: var(--landing-text);
  font-size: 15px;
  font-weight: 700;
}

.principles-item p {
  margin: 8px 0 0;
  color: var(--landing-muted);
  font-size: 14px;
  line-height: 1.5;
}

.final-cta {
  padding: 64px 0;
  background: var(--landing-dark);
}

.final-cta-inner {
  display: flex;
  align-items: center;
  flex-direction: column;
  text-align: center;
}

.final-cta h2 {
  color: #f7f7f4;
  font-size: 30px;
}

.final-cta p {
  max-width: 720px;
  color: #a0a098;
  font-size: 16px;
}

.final-cta .landing-button {
  width: auto;
  margin-top: 20px;
}

.cta-copyright {
  margin: 28px 0 0;
  color: #a0a098;
  font-size: 13px;
}

@media (min-width: 640px) {
  .hero-actions {
    align-items: center;
    flex-direction: row;
  }

  .landing-button {
    width: auto;
  }
}

@media (min-width: 768px) {
  .landing-container {
    padding-right: 40px;
    padding-left: 40px;
  }

  .hero-section {
    padding-top: 64px;
    padding-bottom: 84px;
  }

  .hero-grid {
    grid-template-columns: 1fr auto;
  }

  .hero-right img {
    width: 200px;
    height: 200px;
  }

  .hero-section h1 {
    max-width: 960px;
    font-size: 40px;
  }

  .hero-lede {
    max-width: 820px;
    font-size: 24px;
  }

  .landing-button {
    min-height: 42px;
    padding: 10px 20px;
    font-size: 14px;
  }

  .landing-button svg {
    width: 18px;
    height: 18px;
  }

  .section-heading {
    margin-bottom: 40px;
    padding-left: 8px;
  }

  .section-heading h2 {
    font-size: 32px;
  }

  .section-heading p {
    font-size: 20px;
  }

  .download-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .download-inner,
  .docs-inner,
  .principles-inner {
    gap: 40px;
  }

  .features-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
  }

  .feature-section {
    padding-top: 80px;
  }

  .feature-section.last {
    padding-bottom: 80px;
  }

  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .feature-grid.three-card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .docs-inner {
    grid-template-columns: minmax(0, 0.86fr) minmax(0, 1.14fr);
  }

  .docs-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .principles-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .final-cta {
    padding: 96px 0;
  }

  .final-cta h2 {
    font-size: 44px;
  }

  .final-cta p {
    font-size: 20px;
  }
}
</style>
