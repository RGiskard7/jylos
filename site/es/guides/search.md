# Búsqueda

La búsqueda de Jylos es simple por defecto: escribe palabras y coincide con **títulos y cuerpos** de notas. Los usuarios avanzados pueden añadir operadores al estilo Gmail/Obsidian y mezclarlos libremente.

## Acceso rápido

- **Ctrl/Cmd+P** — paleta de comandos.
- **Ctrl/Cmd+O** — selector rápido de notas.

## Sintaxis de búsqueda avanzada

Los filtros se combinan con **AND**. Prefija cualquier cláusula con `-` para negarla. Los valores de operador pueden ir entre comillas (`body:"máquina virtual java"`).

| Operador | Coincide con |
| --- | --- |
| `palabra`, `otra` | texto libre en título o cuerpo |
| `"frase exacta"` | la frase tal cual (el orden importa) |
| `tag:java` | notas con la etiqueta *java* |
| `folder:backend` | notas de la carpeta *backend* |
| `title:borrador` | el título contiene *borrador* |
| `body:"prueba unitaria"` | el cuerpo contiene la frase |
| `created:<fecha>` | fecha de creación |
| `modified:<fecha>` | fecha de modificación |
| `favorite:true` / `favorite:false` | marcada como favorita |
| `private:true` / `encrypted:true` | notas cifradas/privadas |
| `has:tag` | la nota tiene al menos una etiqueta |
| `has:links` | la nota enlaza a al menos una nota existente |
| `has:backlinks` | al menos una nota enlaza a ella |
| `is:orphan` | sin enlaces de entrada ni de salida |
| `-tag:archivo`, `-title:borrador` | negación (NO debe coincidir) |

### Tokens de fecha

`today`, `yesterday`, `last-week`, `last-month`, `YYYY`, `YYYY-MM`, `YYYY-MM-DD`.

```text
modified:today
created:2026
created:2026-06
modified:2026-06-13
modified:last-week
```

### Ejemplos

```text
tag:java modified:last-week
"patrones de diseño" -tag:archivo
is:orphan
has:backlinks
private:true
java tag:spring
body:"máquina virtual java"
-title:borrador favorite:true
```

## Comportamiento

- Una consulta sin operadores es texto plano en título/cuerpo.
- El parser nunca lanza: un operador desconocido se busca como texto literal y un valor inválido se descarta con un aviso mientras el resto de la consulta sigue.
- Funciona en modo SQLite y en modo vault Markdown.

## Relacionado

- Detalles técnicos: [SEARCH.md](https://github.com/RGiskard7/jylos/blob/main/docs/SEARCH.md)
