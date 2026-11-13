package com.example.jylos.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Turns an image the reading view and the editor cannot draw into a PNG they can.
 *
 * <p>Both draw with JavaFX's WebKit, which does not decode WebP (Obsidian, built on Chromium,
 * does), and many web pages serve WebP even under a {@code .png} name. When an image fails
 * there, the page hands its source here: a {@code http(s)} URL is fetched, a {@code data:} URI
 * (how vault images are inlined) is decoded, and the picture is read with ImageIO — which reads
 * WebP through the TwelveMonkeys plugin — and written back as a PNG {@code data:} URI.</p>
 *
 * <p>Work runs on a background thread; the result is an empty string when the image cannot be
 * read, so the page shows its fallback. Successful conversions are kept for the session: the
 * reading view is rendered again on every edit, and each render would otherwise fetch again.</p>
 */
public final class WebImageConverter {

    private static final Logger logger = Logger.getLogger(WebImageConverter.class.getName());

    /** The same limit as for vault images inlined into a page. */
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;
    /** Larger pictures are not decoded: a few such images would exhaust the heap. */
    private static final long MAX_PIXELS = 50_000_000L;
    private static final int CONVERSIONS_KEPT = 24;
    private static final String PNG_PREFIX = "data:image/png;base64,";

    private static final WebImageConverter SHARED = new WebImageConverter(MAX_IMAGE_BYTES);

    private final long maxBytes;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "jylos-image-conversion");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Map<String, CompletableFuture<String>> conversions =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<String>> eldest) {
                    return size() > CONVERSIONS_KEPT;
                }
            };

    WebImageConverter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** The converter the reading view and the editor share. */
    public static WebImageConverter shared() {
        return SHARED;
    }

    /**
     * Converts the image at {@code source} to a PNG {@code data:} URI.
     *
     * @param source the {@code src} of the image that failed: a {@code http(s)} URL or a
     *               {@code data:} URI; anything else (a file, a bare path) is not read
     * @return the PNG, or an empty string when the image cannot be read
     */
    public CompletableFuture<String> convert(String source) {
        if (source == null || !(source.startsWith("http://") || source.startsWith("https://")
                || source.startsWith("data:"))) {
            return CompletableFuture.completedFuture("");
        }
        String key = source.startsWith("data:") ? "sha256:" + sha256(source) : source;
        synchronized (conversions) {
            CompletableFuture<String> known = conversions.get(key);
            if (known != null) {
                return known;
            }
            CompletableFuture<String> conversion = CompletableFuture.supplyAsync(() -> convertNow(source), executor);
            conversions.put(key, conversion);
            // A failure is not kept: the network may be back next time.
            conversion.thenAccept(png -> {
                if (png.isEmpty()) {
                    synchronized (conversions) {
                        conversions.remove(key, conversion);
                    }
                }
            });
            return conversion;
        }
    }

    private String convertNow(String source) {
        try {
            byte[] bytes = source.startsWith("data:") ? decodeDataUri(source) : fetch(source);
            if (bytes == null) {
                return "";
            }
            BufferedImage image = read(bytes);
            if (image == null) {
                return "";
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", png)) {
                return "";
            }
            return PNG_PREFIX + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (IOException | RuntimeException e) {
            logger.fine("Could not convert image: " + e.getMessage());
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private byte[] decodeDataUri(String source) {
        int comma = source.indexOf(',');
        if (comma < 0 || !source.substring(0, comma).endsWith(";base64")) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(source.substring(comma + 1));
        return bytes.length <= maxBytes ? bytes : null;
    }

    private byte[] fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "image/png,image/jpeg,image/gif,image/*;q=0.8")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                return null;
            }
            byte[] bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
            return bytes.length <= maxBytes ? bytes : null;
        }
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
