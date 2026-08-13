# Plugin servidor MCP

English: [../MCP.md](../MCP.md)

Expone la bóveda a clientes [MCP](https://modelcontextprotocol.io) — Claude Desktop,
Claude Code o cualquier otro cliente compatible con Streamable HTTP — mediante un
servidor HTTP local, mientras Jylos está en ejecución.

El plugin vive en `plugins-source/com/example/jylos/plugin/builtin/mcp/` y
`scripts/build-plugins.sh` lo compila a `jylos/plugins/McpServerPlugin.jar`.

## Conectar un cliente

El servidor escucha por defecto en `http://127.0.0.1:8843/mcp` (**Herramientas →
Plugins → MCP Server → Connection info** muestra la URL activa y el estado actual).
Jylos no necesita saber nada del cliente; cualquier cliente conforme a Streamable HTTP
puede conectarse.

Cambia el puerto con `-Djylos.mcp.port=<puerto>` si el 8843 está ocupado, y usa **MCP
Server: Restart** desde la paleta de comandos (o reinicia Jylos).

### Claude Code

Claude Code habla Streamable HTTP de forma nativa — apúntalo directo a la URL:

```bash
claude mcp add --transport http jylos http://127.0.0.1:8843/mcp
```

### Claude Desktop

El `claude_desktop_config.json` de Claude Desktop solo lanza servidores **stdio**
directamente; no tiene entrada nativa tipo "url" para un servidor HTTP ya en marcha.
Hace falta un puente con [`mcp-remote`](https://github.com/geelen/mcp-remote), pasando
`--allow-http` porque este servidor es HTTP simple por loopback, no HTTPS:

```json
{
  "mcpServers": {
    "jylos": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://127.0.0.1:8843/mcp",
        "--allow-http"
      ]
    }
  }
}
```

macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`. Windows:
`%APPDATA%\Claude\claude_desktop_config.json`. Reinicia Claude Desktop tras editar.

### Otros clientes Streamable HTTP

Cualquier cliente con soporte nativo de Streamable HTTP (no solo stdio) puede apuntarse
directo a la URL, igual que Claude Code — revisa la documentación de ese cliente para el
nombre exacto de su campo de configuración.

## Por qué plugin, y por qué HTTP

Todo lo que el servidor necesita — `NoteService`, `FolderService`, `TagService` — ya es
alcanzable a través de `PluginContext`, igual que Dataview y Mermaid. No hizo falta
ningún cambio en el core para construirlo.

Los transportes de servidor del SDK Java de MCP son STDIO o Servlet; STDIO asume que el
*cliente* lanza el servidor como subproceso, lo cual no encaja con un plugin que ya
corre dentro de una app de escritorio viva. Este aloja el propio
`HttpServletStreamableServerTransportProvider` del SDK — probado contra la suite de
conformidad, un `HttpServlet` real — sobre un Jetty embebido mínimo (un servidor, un
contexto de servlet; no una distribución completa), en vez de escribir a mano el manejo
de sesiones HTTP/SSE. Ver `plugins-source/.../mcp/lib/` y
[PLUGINS.md](PLUGINS.md#dependencias-de-terceros) para cómo un plugin empaqueta un SDK
real en vez de reimplementar desde cero un protocolo externo y en evolución.

## Vinculación

Solo loopback (`127.0.0.1`), nunca la dirección comodín. Es un punto de integración
local para herramientas que corren en la misma máquina, no un servicio pensado para ser
alcanzable desde la red.

## Herramientas

| Herramienta | Hace |
|-------------|------|
| `list_notes` | Lista notas, más recientemente modificadas primero (id, título, etiquetas, favorito/fijada, fechas) |
| `search_notes` | Búsqueda de texto completo en títulos y contenido |
| `read_note` | Contenido completo y metadatos por id o título exacto |
| `create_note` | Crea una nota en la raíz de la bóveda |
| `update_note` | Reemplaza el contenido de una nota (título y metadatos intactos) |
| `list_tags` | Todas las etiquetas con su número de notas, más usadas primero |

El contenido de una nota privada nunca se devuelve ni es editable vía MCP, aunque esté
desbloqueada en la sesión actual de la app de escritorio.

### Deliberadamente no expuesto

**Sin borrar, sin papelera.** Un cliente MCP es una frontera de confianza distinta de
los diálogos de confirmación de la propia UI de escritorio — dar a un agente de IA
externo la capacidad de destruir contenido de la bóveda no es un riesgo que merezca la
pena antes de que este servidor tenga algún historial real de uso. Ampliar el conjunto
de herramientas después es un paso mucho más pequeño que dar marcha atrás en una
herramienta destructiva que salió demasiado pronto.

**Sin herramienta de consulta Dataview.** Los plugins no pueden llamarse entre sí — cada
uno solo recibe el `PluginContext` que le da la aplicación, sin ningún registro de las
capacidades de otros plugins. Exponer "ejecuta esta consulta Dataview" vía MCP
necesitaría un punto de extensión a nivel de aplicación que hoy no existe.

## Dependencias

`mcp` (núcleo del SDK + Jackson 3), `jetty-server` + `jetty-ee11-servlet` (HTTP
embebido), `jakarta.servlet-api`. Resueltas con Maven contra las versiones exactas que
declara el SDK — no elegidas a mano — y empaquetadas dentro del JAR del plugin (ver
[PLUGINS.md](PLUGINS.md#dependencias-de-terceros) para lo que ese empaquetado resuelve:
eliminación de ficheros de firma, fusión de `META-INF/services`, eliminación de
`module-info.class`). `slf4j-api` deliberadamente **no** se empaqueta — el core ya trae
una versión compatible, vinculada a `java.util.logging`, así que el propio logging del
SDK fluye gratis a través de la configuración de log ya existente de Jylos.

## Pruebas

```bash
./scripts/test-plugins.sh
```

La prueba de este plugin
(`plugins-test/com/example/jylos/plugin/builtin/mcp/McpServerPluginTest.java`) se carga
a través del **JAR compilado** mediante su propio `URLClassLoader`, no compilada junto al
resto de fuentes de plugins como sí ocurre con Dataview. Esa distinción atrapó un bug
real durante el desarrollo: el SDK resuelve su mapeador JSON por defecto vía
`ServiceLoader`, que por defecto consulta el classloader de *contexto* del hilo que
llama — no el classloader que realmente cargó las clases del propio SDK. En un classpath
plano (fuentes compiladas junto con la prueba) esa búsqueda funciona por accidente,
porque todo comparte ya un mismo classloader; solo falla del modo en que fallaría una
instalación real de un plugin, que es justo lo que reproduce cargar el JAR real de forma
aislada. Arreglado en `JylosMcpServer.start()` fijando el classloader de contexto del
hilo al del propio plugin antes de construir el servidor.

La prueba maneja el servidor en marcha con peticiones HTTP reales: el saludo inicial
`initialize`, la emisión del id de sesión, `tools/list`, y `tools/call` para cada
herramienta contra una bóveda temporal real (incluyendo confirmar que una nota escrita
aparece en disco, y que un `#tag` inline de una nota existente aparece en los
resultados) — y por último confirma que el puerto queda liberado tras `shutdown()`.
