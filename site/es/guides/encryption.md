# Notas privadas

Marca una nota como privada para cifrar **solo su cuerpo** en reposo con AES-256-GCM, tras una única contraseña maestra.

## Hacer una nota privada

- **Herramientas → Hacer nota privada/pública** (`Ctrl/Cmd+Shift+L`)
- Clic derecho en la nota.

## Desbloquear y bloquear

- Abrir una nota bloqueada pide desbloquear solo esa nota.
- **Herramientas → Desbloquear notas privadas** las revela todas.
- **Bloquear notas privadas** las bloquea de nuevo.

Una insignia de candado marca las notas privadas en la lista y el editor: cerrado = bloqueada, abierto = legible esta sesión.

## Cómo funciona

- La contraseña maestra se deriva con PBKDF2; la contraseña en sí nunca se almacena.
- En SQLite, la privacidad es una columna dedicada; en un vault, un flag `private:` en el frontmatter.
- Los metadatos siguen siendo legibles, de modo que una nota bloqueada se muestra como bloqueada sin la clave.

## Protección

Las notas privadas están protegidas contra el borrado y la exportación. Conviértela primero en normal.
