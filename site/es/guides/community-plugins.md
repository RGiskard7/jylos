<script setup>
import { data } from "../../.vitepress/data/plugins.data.ts";
</script>

# Plugins de la comunidad

Plugins hechos por otras personas, para instalar en tu carpeta de plugins. Son código con acceso
completo a tu equipo y a tus notas: instala solo los de alguien de confianza. Jylos no carga
ninguno hasta que los permites (ver [modo restringido](/es/guides/plugins#modo-restringido)).

## Plugins publicados

<div v-if="data.community.length" class="catalog-grid">
  <div v-for="p in data.community" :key="p.id" class="catalog-card">
    <h3>{{ p.name }}</h3>
    <p class="catalog-meta">de {{ p.author }} · necesita Jylos {{ p.minJylos }}</p>
    <p>{{ p.description.es }}</p>
    <a :href="`${p.repo}/releases/latest`" target="_blank" rel="noreferrer">Descargar desde su repositorio</a>
  </div>
</div>
<p v-else class="catalog-empty">Todavía no hay plugins de la comunidad publicados. El tuyo puede ser el primero.</p>

## Instalar uno

1. Descarga su `.jar` del repositorio del plugin.
2. En Jylos, **Herramientas → Gestionar plugins**: permite los plugins de la comunidad si aún no lo
   has hecho.
3. **Instalar plugin…** y elige el JAR, o **Abrir carpeta de plugins**, cópialo ahí y pulsa
   **Recargar plugins**.

## Publicar el tuyo

1. Compílalo contra la API de plugins de Jylos (ver [PLUGINS.md](https://github.com/RGiskard7/jylos/blob/main/docs/es/PLUGINS.md)).
2. Pon en su `MANIFEST.MF` el `Plugin-Class`, el `Plugin-Id` (su `getId()`) y el
   `Plugin-Min-Jylos` que necesita.
3. Publica el JAR en las releases de tu repositorio.
4. Abre un pull request que lo añada a [`community-plugins.json`](https://github.com/RGiskard7/jylos/blob/main/community-plugins.json):
   `id`, `name`, `author`, `description` (`en`, `es`), `repo` y `minJylos`.
