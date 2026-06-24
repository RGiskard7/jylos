package com.example.jylos.util;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;

/**
 * Opens external {@code http(s)} URLs in the user's default browser.
 *
 * <p>Uses the platform "open" command via {@link ProcessBuilder} (the same
 * approach the app already uses to reveal files), which passes the URL as a
 * separate argument — no shell, so no command injection. Only {@code http} and
 * {@code https} URLs are honoured; anything else is refused.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class SystemBrowser {

    private static final Logger logger = LoggerConfig.getLogger(SystemBrowser.class);

    private SystemBrowser() {
    }

    /**
     * Opens {@code url} in the system browser. No-op (with a warning) when the URL
     * is null, blank or not an {@code http(s)} link.
     *
     * @return {@code true} if the open command was launched
     */
    public static boolean open(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            logger.warning("Refusing to open non-http(s) URL: " + u);
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", u).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", u).start();
            } else {
                new ProcessBuilder("xdg-open", u).start();
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not open URL in browser: " + u, e);
            return false;
        }
    }
}
