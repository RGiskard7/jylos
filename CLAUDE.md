<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->

## Changelog

Keep `## [Unreleased]` undated at the top. When preparing a release, move relevant
bullets into a single `## [x.y.z] - YYYY-MM-DD` section using the release date.
Do not duplicate version sections; use `Fixes #123` in PR descriptions when a PR
should close a GitHub issue.

## Tests

- **Un test no vale hasta haberlo visto fallar.** Código nuevo: falla porque no existe la
  implementación. Código existente: rompe el código a propósito, comprueba el rojo,
  restaura. Al informar, incluye el mensaje de fallo real. Sin ese rojo, no afirmes que un
  test protege nada.
- **Nunca afirmes solo una cota superior.** `assertTrue(n < 300)` pasa con `n == 0`, es
  decir, con la funcionalidad completamente rota. Acota por los dos lados y pregúntate qué
  valores degenerados (0, vacío, null) satisfacen la aserción.
- **Comprueba efectos observables, no llamadas.** Que se invocara un método no demuestra
  que hiciera algo. Verifica el resultado: el fichero en disco, el valor devuelto, el
  estado final.
- **Los tests que leen el código fuente como texto no son cobertura.** Sirven para vigilar
  invariantes de arquitectura, no para demostrar comportamiento. No los presentes como red
  de seguridad.
- **"Suite en verde" no es una garantía.** Al reportar, di qué has verificado y cómo. Si no
  has roto el código para comprobarlo, dilo explícitamente.
- **TDD solo para código nuevo.** Sobre código existente se hace caracterización: los tests
  pasan en verde desde el primer run y el anti-fraude es el sabotaje posterior.

Procedimiento y protocolo de sabotaje detallados: `docs/TESTING.md`.

