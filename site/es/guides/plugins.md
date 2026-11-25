<script setup>
import { data } from "../../.vitepress/data/plugins.data.ts";
</script>

# Plugins

Hay dos tipos de plugins:

- **Plugins principales:** los de Jylos. Vienen con la app, se actualizan con ella y se encienden
  o apagan en **Herramientas → Gestionar plugins**.
- **Plugins de la comunidad:** los hacen otras personas. Se instalan en tu carpeta de plugins; ver
  [Plugins de la comunidad](/es/guides/community-plugins).

## Plugins principales

<table class="catalog-table">
  <thead><tr><th>Plugin</th><th>Qué hace</th><th>Por defecto</th></tr></thead>
  <tbody>
    <tr v-for="p in data.core" :key="p.id">
      <td><strong>{{ p.name }}</strong></td>
      <td>{{ p.description.es }}</td>
      <td>{{ p.enabledByDefault ? "Encendido" : "Apagado" }}</td>
    </tr>
  </tbody>
</table>

MCP Server abre un puerto local, y Publish y Auto Backup escriben ficheros fuera de tu bóveda, así
que empiezan apagados hasta que los quieras. Al actualizar Jylos, cada plugin sigue como lo tenías.

Ver también la [guía de Dataview](/es/guides/dataview).

## Modo restringido

Los plugins de la comunidad son código de otras personas con acceso completo a tu equipo y a tus
notas, así que Jylos no carga ninguno hasta que los permites: en **Herramientas → Gestionar
plugins**, enciende el interruptor de la sección *Plugins de la comunidad* y confirma. Una
instalación nueva empieza en modo restringido.

## Instalar y quitar plugins de la comunidad

- **Instalar plugin…** copia un `.jar` a tu carpeta de plugins y lo carga.
- O **Abrir carpeta de plugins**, copia ahí el JAR y pulsa **Recargar plugins**; no hace falta
  reiniciar.
- **Eliminar** en la tarjeta de un plugin de la comunidad lo apaga y borra su JAR.

Un plugin puede indicar en su `MANIFEST.MF` la versión mínima de Jylos con la que funciona
(`Plugin-Min-Jylos`); si la tuya es anterior, no se carga y el gestor dice qué versión necesita.

## API de plugins

Un plugin extiende `AbstractPlugin` y recibe un `PluginContext` que expone estos puntos de extensión:

- `registerCommand(...)` — entradas de la paleta de comandos.
- `registerMenuItem(...)` — entradas del menú dinámico de plugins.
- `registerSidePanel(...)` — un nodo en el panel derecho.
- `registerPreviewEnhancer(...)` — CSS/JS y post-procesado HTML por nota para la vista previa.
- `registerToolbarButton(...)` — un botón en la barra de herramientas principal.
- `registerStatusBarItem(...)` — un elemento en la barra de estado, que el plugin guarda y actualiza.
- `registerEditorHook(...)` — `onBeforeTextInsert`, `onBeforeSave`, `onAfterSave`.
- `registerEditorBlockRenderer(...)` — renderiza un bloque cercado inline en Live Preview.
- Acceso al bus de eventos, diálogos con tema, diálogos de progreso y preferencias por plugin.

Cada plugin recibe su propio classloader con un límite impuesto: puede alcanzar la superficie documentada de la API de plugins, pero las peticiones a otras clases internas `com.example.jylos.*` fallan rápido.

## Compilar desde el código fuente

Las fuentes de los plugins principales viven en `plugins-source/` y se compilan a `jylos/plugins/` con:

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

## Relacionado

- Detalles técnicos, incluido el manifiesto que necesita un plugin de la comunidad: [PLUGINS.md](https://github.com/RGiskard7/jylos/blob/main/docs/es/PLUGINS.md)
- [Plugins de la comunidad](/es/guides/community-plugins)
- [Guía de Dataview](/es/guides/dataview)
- [Servidor MCP](https://github.com/RGiskard7/jylos/blob/main/docs/MCP.md)
