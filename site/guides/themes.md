<script setup>
import { withBase } from "vitepress";
import { data as themes } from "../.vitepress/data/themes.data.ts";
</script>

# Themes

Besides its light, dark and system themes, Jylos can use themes you install. These are ready to
download:

<div class="catalog-grid">
  <div v-for="t in themes" :key="t.id" class="catalog-card">
    <h3>{{ t.name }}</h3>
    <p class="catalog-meta">{{ t.dark ? "Dark" : "Light" }}<span v-if="t.author"> · by {{ t.author }}</span></p>
    <p>{{ t.description.en }}</p>
    <a :href="withBase(`/themes/${t.id}.zip`)" download>Download</a>
  </div>
</div>

## Install a theme

1. Download it and unzip it: you get a folder with `theme.properties` and `theme.css`.
2. In Jylos, **Tools → Preferences → Appearance**: **Open themes folder** and copy that folder
   there.
3. **Reload themes**, set *Theme* to *Another theme* and choose it. No restart needed.

A theme changes the whole app: the editor, the reading view and the graph take its colours too.

## Make your own

Start from a theme here or from the template in the repository; see
[Themes & CSS snippets](/guides/themes-snippets). To have yours listed here, open a pull request
adding its folder to [`themes/`](https://github.com/RGiskard7/jylos/tree/main/themes).
