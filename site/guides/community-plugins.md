<script setup>
import { data } from "../.vitepress/data/plugins.data.ts";
</script>

# Community plugins

Plugins made by other people, to install in your plugins folder. They are code with full access
to your computer and your notes: install only those from someone you trust. Jylos loads none
until you allow them (see [restricted mode](/guides/plugins#restricted-mode)).

## Listed plugins

<div v-if="data.community.length" class="catalog-grid">
  <div v-for="p in data.community" :key="p.id" class="catalog-card">
    <h3>{{ p.name }}</h3>
    <p class="catalog-meta">by {{ p.author }} · needs Jylos {{ p.minJylos }}</p>
    <p>{{ p.description.en }}</p>
    <a :href="`${p.repo}/releases/latest`" target="_blank" rel="noreferrer">Download from its repository</a>
  </div>
</div>
<p v-else class="catalog-empty">No community plugins are listed yet. Yours could be the first.</p>

## Install one

1. Download its `.jar` from the plugin's repository.
2. In Jylos, **Tools → Manage plugins**: allow community plugins if you have not yet.
3. **Install plugin…** and choose the JAR, or **Open plugins folder**, copy it there and press
   **Reload plugins**.

## List yours

1. Build it against Jylos's plugin API (see [PLUGINS.md](https://github.com/RGiskard7/jylos/blob/main/docs/PLUGINS.md)).
2. Put in its `MANIFEST.MF` the `Plugin-Class`, the `Plugin-Id` (its `getId()`) and the
   `Plugin-Min-Jylos` it needs.
3. Publish the JAR in your repository's releases.
4. Open a pull request adding it to [`community-plugins.json`](https://github.com/RGiskard7/jylos/blob/main/community-plugins.json):
   `id`, `name`, `author`, `description` (`en`, `es`), `repo` and `minJylos`.
