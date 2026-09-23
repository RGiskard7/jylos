# Maven / Java ausente

## Síntoma

La compilación falla porque `java` o `mvn` no se encuentra, o informa de una versión demasiado antigua.

## Requisitos

- Java JDK 21
- Maven 3.9+

## Comprobar

```bash
java -version
mvn -version
```

Instala el JDK y Maven necesarios y vuelve a intentar la compilación.

## Nota

Los avisos como `Failed to build parent project for org.openjfx:javafx-*` durante una compilación Maven son conocidos y no bloquean.

Consulta [Instalar](/es/start/install) y [BUILD](https://github.com/RGiskard7/jylos/blob/main/docs/BUILD.md).
