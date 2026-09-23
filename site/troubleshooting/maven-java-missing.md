# Maven / Java Missing

## Symptom

The build fails because `java` or `mvn` is not found, or reports a version too old.

## Requirements

- Java JDK 21
- Maven 3.9+

## Check

```bash
java -version
mvn -version
```

Install the required JDK and Maven, then retry the build.

## Note

Warnings such as `Failed to build parent project for org.openjfx:javafx-*` during a Maven build are known and non-blocking.

See [Install](/start/install) and [BUILD](https://github.com/RGiskard7/jylos/blob/main/docs/BUILD.md).
