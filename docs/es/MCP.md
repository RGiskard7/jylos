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

### opencode

`opencode.json`, tipo `"remote"`, directo a la URL — sin puente:

```json
{
  "mcp": {
    "jylos": {
      "type": "remote",
      "url": "http://127.0.0.1:8843/mcp",
      "enabled": true
    }
  }
}
```

### Codex CLI

`config.toml`, campo `url` bajo `[mcp_servers.jylos]` — sin puente:

```toml
[mcp_servers.jylos]
url = "http://127.0.0.1:8843/mcp"
```

### Cursor

`.cursor/mcp.json`, campo `url` — sin puente:

```json
{
  "mcpServers": {
    "jylos": {
      "url": "http://127.0.0.1:8843/mcp"
    }
  }
}
```

### VS Code

`.vscode/mcp.json`, la clave de nivel superior es `servers` (no `mcpServers`),
`"type": "http"` — sin puente:

```json
{
  "servers": {
    "jylos": {
      "type": "http",
      "url": "http://127.0.0.1:8843/mcp"
    }
  }
}
```

### Otros clientes Streamable HTTP

Cualquier cliente con soporte nativo de Streamable HTTP (no solo stdio) puede apuntarse
directo a la URL, igual que los de arriba — revisa la documentación de ese cliente para
el nombre exacto de su campo de configuración. No existe un formato estándar de
configuración de cliente entre clientes MCP; solo el protocolo de cable está
estandarizado, así que cada fabricante inventó su propio fichero y nombres de campo. Un
cliente solo-stdio necesita el puente `mcp-remote` que se muestra para Claude Desktop.

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

## Vinculación y validación de peticiones

Solo loopback (`127.0.0.1`), nunca la dirección comodín. Es un punto de integración
local para herramientas que corren en la misma máquina, no un servicio pensado para ser
alcanzable desde la red.

Escuchar solo en loopback **no** basta, y la especificación lo dice: marca la validación
de `Origin` como MUST. Una página web que el usuario simplemente esté visitando puede
alcanzar un servidor local mediante DNS rebinding — el navegador pasa a tratar
`127.0.0.1` como mismo origen que el sitio atacante, así que no hay preflight y la
respuesta es legible. Por eso el servidor además:

- **Rechaza con `403` cualquier petición que lleve cabecera `Origin`.** Un navegador
  siempre la envía en una petición cross-origin; los clientes nativos (Claude Desktop,
  Claude Code, `mcp-remote`) no envían ninguna, así que no les afecta.
- **Fija la cabecera `Host`** a las direcciones de loopback por las que realmente se le
  puede alcanzar, de modo que un `Host: attacker.example` reescrito recibe `421`.

De ambas cosas se encarga el propio `DefaultServerTransportSecurityValidator` del SDK, no
una comprobación escrita a mano. Una consecuencia: `-Djylos.mcp.port=0` ("cualquier puerto
libre") se rechaza y cae al puerto por defecto, porque la lista de `Host` permitidos debe
construirse antes de que el conector abra el puerto.

## Herramientas

Cada herramienta lleva las anotaciones del protocolo (`readOnlyHint`, `destructiveHint`,
`idempotentHint`), así que un cliente sabe qué hace —y puede pedir confirmación— antes de
llamarla.

**Notas**

| Herramienta | Hace |
|-------------|------|
| `list_notes` | Lista notas, más recientemente modificadas primero (id, título, etiquetas, favorito/fijada, fechas) |
| `search_notes` | Búsqueda de texto completo en títulos y contenido |
| `read_note` | Contenido completo y metadatos por id o título exacto |
| `create_note` | Crea una nota en la raíz de la bóveda |
| `update_note` | Reemplaza el contenido de una nota (título y metadatos intactos) |
| `rename_note` | Renombra una nota — **devuelve un id nuevo** |
| `move_note` | Mueve una nota a una carpeta, o a la raíz — **devuelve un id nuevo** |
| `delete_note` | Manda una nota a la papelera (recuperable) |
| `restore_note` | Restaura una nota de la papelera, por su id de `list_trash` |
| `list_trash` | Lista las notas en la papelera, todas restaurables |

**Carpetas**

| Herramienta | Hace |
|-------------|------|
| `list_folders` | Todas las carpetas con su id, nombre y número de notas |
| `create_folder` | Crea una carpeta, en la raíz o dentro de otra |
| `delete_folder` | Borra una carpeta; sus notas pasan a la raíz en vez de borrarse |

**Etiquetas**

| Herramienta | Hace |
|-------------|------|
| `list_tags` | Todas las etiquetas con su número de notas, más usadas primero |
| `add_tag` | Añade una etiqueta a una nota, creándola si no existía |
| `remove_tag` | Quita una etiqueta de una nota |
| `rename_tag` | Renombra una etiqueta en toda la bóveda |

El id de una nota es su ruta dentro de la bóveda, así que `rename_note` y `move_note` lo
cambian. Ambas devuelven el id nuevo — úsalo en las llamadas siguientes o dejarán de
resolver.

El contenido de una nota privada nunca se devuelve ni es editable vía MCP, aunque esté
desbloqueada en la sesión actual de la app de escritorio. Eso cubre todas las vías: leer,
buscar, editar, renombrar, mover, etiquetar y borrar la rechazan por igual.

### Deliberadamente no expuesto

**Nada irreversible.** Borrar sí está disponible, pero solo del tipo recuperable: una nota
borrada va a la papelera y vuelve con `restore_note`, y una carpeta borrada conserva sus
notas moviéndolas a la raíz. `permanently_delete_note`, `empty_trash` y
`permanently_delete_folder` no tienen herramienta. Un cliente MCP es una frontera de
confianza distinta de los diálogos de confirmación de la UI de escritorio, así que toda
llamada destructiva que un agente pueda hacer aquí es una que el usuario puede deshacer
desde la aplicación.

**Sin renombrar ni mover carpetas, sin borrar etiquetas.** Cada una reordena la bóveda
alrededor de notas por las que no se preguntó al agente. Una nota ya se puede reubicar de
una en una con `move_note`, y una etiqueta quitarse de una nota con `remove_tag`.

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
