<script setup>
import { withBase } from "vitepress";
import { data as themes } from "../../.vitepress/data/themes.data.ts";
</script>

# Temas

Además de sus temas claro, oscuro y de sistema, Jylos puede usar temas que instales. Estos están
listos para descargar:

<div class="catalog-grid">
  <div v-for="t in themes" :key="t.id" class="catalog-card">
    <h3>{{ t.name }}</h3>
    <p class="catalog-meta">{{ t.dark ? "Oscuro" : "Claro" }}<span v-if="t.author"> · de {{ t.author }}</span></p>
    <p>{{ t.description.es }}</p>
    <a :href="withBase(`/themes/${t.id}.zip`)" download>Descargar</a>
  </div>
</div>

## Instalar un tema

1. Descárgalo y descomprímelo: obtienes una carpeta con `theme.properties` y `theme.css`.
2. En Jylos, **Herramientas → Preferencias → Apariencia**: **Abrir carpeta de temas** y copia ahí
   esa carpeta.
3. **Recargar temas**, pon *Tema* en *Otro tema* y elígelo. No hace falta reiniciar.

Un tema cambia toda la app: el editor, la vista de lectura y el grafo también toman sus colores.

## Crear el tuyo

Parte de uno de estos o de la plantilla del repositorio; ver
[Temas y snippets](/es/guides/themes-snippets). Para que el tuyo aparezca aquí, abre un pull
request que añada su carpeta a [`themes/`](https://github.com/RGiskard7/jylos/tree/main/themes).
