package com.example.jylos.service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.util.RichLinks;
import com.example.jylos.util.RichLinks.RichLink;

/**
 * Fetches a web page and extracts its rich-link metadata (title, description,
 * image, site name). This is the only networked part of the rich-link feature;
 * the format and parsing live in {@link RichLinks}, which stays pure.
 *
 * <p>Callers must invoke {@link #fetch(String)} off the JavaFX thread (it blocks
 * on network I/O). Failures never throw: an unreachable or invalid page yields a
 * minimal {@link RichLink} carrying just the URL and host, so the card still
 * renders something useful.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class RichLinkService {

    private static final Logger logger = LoggerConfig.getLogger(RichLinkService.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** A desktop-browser User-Agent; some sites omit OpenGraph tags for unknown agents. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; Jylos/1.0; +https://github.com/RGiskard7/jylos)";
    /** Cap the body we parse; OpenGraph tags live in <head>, so 512 KB is plenty. */
    private static final int MAX_BYTES = 512 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Fetches {@code url} and returns its metadata. On any error (bad URL, timeout,
     * non-HTML response) returns a minimal {@link RichLink} with just the URL/host,
     * never {@code null} and never throwing.
     */
    public RichLink fetch(String url) {
        String trimmed = url == null ? "" : url.trim();
        RichLink fallback = new RichLink(trimmed, "", "", "", RichLinks.hostOf(trimmed));
        if (!isHttp(trimmed)) {
            return fallback;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimmed))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() / 100 != 2) {
                    logger.fine("Rich link fetch got HTTP " + response.statusCode() + " for " + trimmed);
                    return fallback;
                }
                // Read at most MAX_BYTES and close: a huge or never-ending response must not
                // be downloaded (or buffered) whole just to find tags that live in <head>.
                byte[] head = in.readNBytes(MAX_BYTES);
                return RichLinks.parseMetadata(new String(head, charsetOf(response)), trimmed);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not fetch rich link metadata for " + trimmed + ": " + e.getMessage());
            return fallback;
        }
    }

    /** Charset declared in the response's {@code Content-Type}, defaulting to UTF-8. */
    private static Charset charsetOf(HttpResponse<?> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        for (String part : contentType.split(";")) {
            String param = part.trim();
            if (param.toLowerCase(java.util.Locale.ROOT).startsWith("charset=")) {
                try {
                    return Charset.forName(param.substring("charset=".length()).replace("\"", "").trim());
                } catch (IllegalArgumentException unknownCharset) {
                    break;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static boolean isHttp(String url) {
        String u = url.toLowerCase(java.util.Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://");
    }
}
