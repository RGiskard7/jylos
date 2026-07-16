# Búsqueda

English: [../SEARCH.md](../SEARCH.md)

La búsqueda simple busca texto en títulos y cuerpos. Los usuarios avanzados pueden añadir operadores estilo Gmail/Obsidian.

## Sintaxis avanzada

Los filtros se combinan con AND. Prefijo `-` niega una cláusula. Valores pueden ir entre comillas.

| Operador | Coincide con |
|----------|--------------|
| `word` | texto libre en título/cuerpo |
| `"exact phrase"` | frase exacta |
| `tag:java` | notas con tag |
| `folder:backend` | notas en carpeta |
| `title:draft` | título contiene texto |
| `body:"unit test"` | cuerpo contiene frase |
| `created:<date>` | fecha de creación |
| `modified:<date>` | fecha de modificación |
| `favorite:true` | favorito |
| `private:true` / `encrypted:true` | nota privada |
| `has:tag` | tiene tags |
| `has:links` | enlaza a notas existentes |
| `has:backlinks` | tiene backlinks |
| `is:orphan` | sin enlaces entrantes/salientes |
| `-tag:archive` | negación |

## Fechas

`today`, `yesterday`, `last-week`, `last-month`, `YYYY`, `YYYY-MM`, `YYYY-MM-DD`.

## Robustez

- Sin operadores: búsqueda simple igual que antes.
- Parser tolerante: operadores desconocidos se tratan como texto literal o warning no fatal.
- Funciona en SQLite y vault Markdown.
- Trabajo fuera del UI thread.
- Metadata cara se calcula solo si la query la usa.

## Arquitectura

| Capa | Tipo | Rol |
|------|------|-----|
| Parser | `SearchQueryParser` | String a `SearchQuery` |
| Modelo | `SearchQuery`, `SearchFilter` | Cláusulas y warnings |
| Fechas | `SearchDates` | Matching flexible |
| Servicio | `AdvancedSearchService` | Aplica query usando servicios existentes |
| Resultado | `SearchResult` | Nota, snippet, carpeta, tags |
| UI | `NotesListController` | Delegación y render en lista |
