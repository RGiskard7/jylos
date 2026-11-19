// Before the site is built or served: the changelog page from the repository's changelog.md,
// and a zip of each theme in themes/ under public/themes/, for the Themes page's downloads.
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const site = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const root = path.resolve(site, "..");

const changelog = fs.readFileSync(path.join(root, "changelog.md"), "utf8");
fs.writeFileSync(path.join(site, "changelog.md"),
  // v-pre: the changelog quotes template syntax ({{date}}…), which Vue must not read as its own.
  `---\ntitle: Changelog\neditLink: false\n---\n\n<!-- Generated from ../changelog.md by scripts/prepare.mjs: edit that file. -->\n\n::: v-pre\n\n${changelog}\n\n:::\n`);

const themes = path.join(root, "themes");
const out = path.join(site, "public", "themes");
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
for (const entry of fs.readdirSync(themes, { withFileTypes: true })) {
  if (!entry.isDirectory() || entry.name.startsWith("_") || entry.name.startsWith(".")
      || !fs.existsSync(path.join(themes, entry.name, "theme.properties"))) {
    continue;
  }
  try {
    // The folder itself goes in the zip: unzipped into the themes folder, it is ready to use.
    execFileSync("zip", ["-r", "-X", "-q", path.join(out, `${entry.name}.zip`), entry.name], { cwd: themes });
  } catch (error) {
    console.warn(`Could not zip theme ${entry.name} (is "zip" installed?): ${error.message}`);
  }
}
console.log(`Prepared the changelog and ${fs.readdirSync(out).length} theme download(s).`);
