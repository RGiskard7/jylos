package com.example.jylos;

import java.util.List;
import java.util.function.UnaryOperator;

/** The project's public links, and the runtime details people are asked for in bug reports. */
public final class AppLinks {

    public static final String WEBSITE = "https://jylos.es";
    public static final String REPOSITORY = "https://github.com/RGiskard7/jylos";
    public static final String ISSUES = REPOSITORY + "/issues";
    public static final String LICENSE = REPOSITORY + "/blob/main/LICENSE";

    private AppLinks() {
    }

    /**
     * What Jylos is running on, one item per line: app version, Java, JavaFX and OS.
     *
     * @param systemProperty reads a system property; null when unset
     */
    public static List<String> runtimeDetails(String appVersion, UnaryOperator<String> systemProperty) {
        String javaVendor = systemProperty.apply("java.vendor");
        String java = "Java " + orUnknown(systemProperty.apply("java.version"))
                + (javaVendor != null && !javaVendor.isBlank() ? " (" + javaVendor + ")" : "");
        // Set by JavaFX itself once the toolkit is loaded.
        String javafx = "JavaFX " + orUnknown(systemProperty.apply("javafx.runtime.version"));
        String osName = systemProperty.apply("os.name");
        String os = osName == null || osName.isBlank()
                ? "?"
                : osName + " " + orUnknown(systemProperty.apply("os.version"))
                        + " (" + orUnknown(systemProperty.apply("os.arch")) + ")";
        return List.of("Jylos " + appVersion, java, javafx, os);
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }
}
