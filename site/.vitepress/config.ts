import process from "node:process";
import { defineConfig } from "vitepress";

const base = process.env.VITEPRESS_BASE ?? "/jylos/";

const githubUrl = "https://github.com/RGiskard7/jylos";
const releasesUrl = `${githubUrl}/releases/latest`;

export default defineConfig({
  title: "Jylos",
  description:
    "Jylos is a local-first, open-source desktop app for Markdown notes, wiki-links, backlinks, knowledge graphs, Kanban, Canvas, plugins and Git sync.",
  base,
  cleanUrls: false,
  ignoreDeadLinks: [/^\.\//],
  sitemap: {
    hostname: "https://RGiskard7.github.io/jylos/",
  },
  head: [
    ["link", { rel: "icon", type: "image/png", href: `${base}landing/logo.png` }],
    ["meta", { property: "og:title", content: "Jylos" }],
    [
      "meta",
      {
        property: "og:description",
        content:
          "Local-first desktop notes app for Markdown, wiki-links, Canvas, Kanban and graph views. Free, open source, offline.",
      },
    ],
    ["meta", { property: "og:type", content: "website" }],
    ["meta", { property: "og:image", content: `${base}landing/banner.png` }],
    ["meta", { name: "twitter:card", content: "summary_large_image" }],
    ["meta", { name: "twitter:image", content: `${base}landing/banner.png` }],
  ],
  locales: {
    root: {
      label: "English",
      lang: "en-US",
      title: "Jylos",
      description:
        "Jylos is a local-first, open-source desktop app for Markdown notes, wiki-links, backlinks, knowledge graphs, Kanban, Canvas, plugins and Git sync.",
      themeConfig: {
        logo: { src: "/landing/logo.png", alt: "Jylos" },
        editLink: {
          pattern: "https://github.com/RGiskard7/jylos/edit/main/site/:path",
          text: "Edit this page",
        },
        nav: [
          { text: "Start", link: "/start/install" },
          { text: "Guides", link: "/guides/creating-notes" },
          { text: "Reference", link: "/reference/" },
          { text: "Changelog", link: "/changelog" },
          { text: "Download", link: releasesUrl },
        ],
        sidebar: [
          {
            text: "Start Here",
            items: [
              { text: "Install Jylos", link: "/start/install" },
              { text: "First Launch", link: "/start/first-launch" },
              { text: "Getting Started", link: "/start/getting-started" },
            ],
          },
          {
            text: "Guides",
            items: [
              { text: "Creating Notes", link: "/guides/creating-notes" },
              { text: "Wiki-links", link: "/guides/wiki-links" },
              { text: "Knowledge Graph", link: "/guides/knowledge-graph" },
              { text: "Kanban", link: "/guides/kanban" },
              { text: "Canvas", link: "/guides/canvas" },
              { text: "Private Notes", link: "/guides/encryption" },
              { text: "Git Sync", link: "/guides/git-sync" },
              { text: "Plugins", link: "/guides/plugins" },
              { text: "Themes & Snippets", link: "/guides/themes-snippets" },
              { text: "Dataview", link: "/guides/dataview" },
              { text: "Search", link: "/guides/search" },
              { text: "Workspaces", link: "/guides/workspaces" },
            ],
          },
          {
            text: "Reference",
            items: [
              { text: "Technical Docs", link: "/reference/" },
              { text: "Keyboard Shortcuts", link: "/reference/keyboard-shortcuts" },
            ],
          },
          {
            text: "Troubleshooting",
            items: [
              { text: "JavaFX Runtime Errors", link: "/troubleshooting/javafx-runtime" },
              { text: "JAR Not Found", link: "/troubleshooting/jar-not-found" },
              { text: "Maven / Java Missing", link: "/troubleshooting/maven-java-missing" },
            ],
          },
        ],
        footer: {
          message: "Free and open source. Local-first, offline, and Markdown-based.",
          copyright: "© 2025–2026 Eduardo Díaz Sánchez (RGiskard7) · MIT License",
        },
      },
    },
    es: {
      label: "Español",
      lang: "es-ES",
      title: "Jylos",
      description:
        "Jylos es una aplicación de escritorio local-first y de código abierto para notas Markdown, wiki-links, backlinks, grafos de conocimiento, Kanban, Canvas, plugins y sincronización Git.",
      themeConfig: {
        logo: { src: "/landing/logo.png", alt: "Jylos" },
        editLink: {
          pattern: "https://github.com/RGiskard7/jylos/edit/main/site/:path",
          text: "Editar esta página",
        },
        outline: { label: "En esta página" },
        docFooter: {
          prev: "Página anterior",
          next: "Página siguiente",
        },
        returnToTopLabel: "Volver arriba",
        sidebarMenuLabel: "Menú",
        darkModeSwitchLabel: "Tema",
        langMenuLabel: "Cambiar idioma",
        nav: [
          { text: "Empezar", link: "/es/start/install" },
          { text: "Guías", link: "/es/guides/creating-notes" },
          { text: "Referencia", link: "/es/reference/" },
          { text: "Changelog", link: "/changelog" },
          { text: "Descargar", link: releasesUrl },
        ],
        sidebar: [
          {
            text: "Para empezar",
            items: [
              { text: "Instalar Jylos", link: "/es/start/install" },
              { text: "Primer arranque", link: "/es/start/first-launch" },
              { text: "Primeros pasos", link: "/es/start/getting-started" },
            ],
          },
          {
            text: "Guías",
            items: [
              { text: "Crear notas", link: "/es/guides/creating-notes" },
              { text: "Wiki-links", link: "/es/guides/wiki-links" },
              { text: "Grafo de conocimiento", link: "/es/guides/knowledge-graph" },
              { text: "Kanban", link: "/es/guides/kanban" },
              { text: "Canvas", link: "/es/guides/canvas" },
              { text: "Notas privadas", link: "/es/guides/encryption" },
              { text: "Sincronización Git", link: "/es/guides/git-sync" },
              { text: "Plugins", link: "/es/guides/plugins" },
              { text: "Temas y snippets", link: "/es/guides/themes-snippets" },
              { text: "Dataview", link: "/es/guides/dataview" },
              { text: "Búsqueda", link: "/es/guides/search" },
              { text: "Workspaces", link: "/es/guides/workspaces" },
            ],
          },
          {
            text: "Referencia",
            items: [
              { text: "Docs técnicas", link: "/es/reference/" },
              { text: "Atajos de teclado", link: "/es/reference/keyboard-shortcuts" },
            ],
          },
          {
            text: "Solución de problemas",
            items: [
              { text: "Errores de runtime JavaFX", link: "/es/troubleshooting/javafx-runtime" },
              { text: "JAR no encontrado", link: "/es/troubleshooting/jar-not-found" },
              { text: "Maven / Java ausente", link: "/es/troubleshooting/maven-java-missing" },
            ],
          },
        ],
        footer: {
          message: "Gratis y de código abierto. Local-first, sin conexión y basado en Markdown.",
          copyright: "© 2025–2026 Eduardo Díaz Sánchez (RGiskard7) · Licencia MIT",
        },
      },
    },
  },
  themeConfig: {
    search: { provider: "local" },
    socialLinks: [{ icon: "github", link: "https://github.com/RGiskard7/jylos" }],
  },
});
