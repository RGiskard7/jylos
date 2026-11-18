import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/** One of Jylos's own plugins (plugins-source/core-plugins.json). */
export interface CorePlugin {
  id: string;
  name: string;
  enabledByDefault: boolean;
  description: { en: string; es: string };
}

/** A community plugin listed in community-plugins.json. */
export interface CommunityPlugin {
  id: string;
  name: string;
  author: string;
  description: { en: string; es: string };
  repo: string;
  minJylos: string;
}

declare const data: { core: CorePlugin[]; community: CommunityPlugin[] };
export { data };

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

function read<T>(relative: string): T[] {
  return JSON.parse(fs.readFileSync(path.join(root, relative), "utf8")).plugins ?? [];
}

export default {
  watch: ["../../../plugins-source/core-plugins.json", "../../../community-plugins.json"],
  load() {
    return {
      core: read<CorePlugin>("plugins-source/core-plugins.json"),
      community: read<CommunityPlugin>("community-plugins.json").sort((a, b) => a.name.localeCompare(b.name)),
    };
  },
};
