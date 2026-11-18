import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/** A theme in the repository's themes/ folder, as the website lists it. */
export interface Theme {
  id: string;
  name: string;
  dark: boolean;
  base: string;
  author: string;
  description: { en: string; es: string };
}

declare const data: Theme[];
export { data };

const themesDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../themes");

/** A Java .properties file: key=value lines, # comments, \uXXXX escapes. */
export function parseProperties(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || line.startsWith("!")) continue;
    const eq = line.search(/[=:]/);
    if (eq < 0) continue;
    const value = line.slice(eq + 1).trim().replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) =>
      String.fromCharCode(parseInt(hex, 16)));
    out[line.slice(0, eq).trim()] = value;
  }
  return out;
}

export default {
  watch: ["../../../themes/*/theme.properties"],
  load(): Theme[] {
    return fs.readdirSync(themesDir, { withFileTypes: true })
      .filter((entry) => entry.isDirectory() && !entry.name.startsWith("_") && !entry.name.startsWith("."))
      .map((entry) => path.join(themesDir, entry.name, "theme.properties"))
      .filter((file) => fs.existsSync(file))
      .map((file) => {
        const p = parseProperties(fs.readFileSync(file, "utf8"));
        const id = p.id || path.basename(path.dirname(file));
        return {
          id,
          name: p.name || id,
          dark: p.darkLike === "true",
          base: p.base || "auto",
          author: p.author || "",
          description: { en: p.description || "", es: p["description.es"] || p.description || "" },
        };
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  },
};
