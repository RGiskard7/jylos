package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * The reading view and the editor draw images with JavaFX's WebKit, which cannot decode WebP,
 * a format Obsidian shows. An image that fails there is handed to this converter, which reads
 * it (from the web, or the {@code data:} URI a vault file was inlined as) and returns a PNG.
 */
class WebImageConverterTest {

    /** 40×30: red on the left half, blue on the right; lossy, as most WebP on the web. */
    private static final byte[] LOSSY = Base64.getDecoder().decode(
            "UklGRnQAAABXRUJQVlA4IGgAAADQBACdASooAB4APm0skkWkIqGYBABABsSgDBCoB8oFKA/ACu/9wAAveYM/tUZ2AAD+7ylH/sWctgSg"
                    + "//c4H2Gfi1/h09py7pqmxMf7NbCx6OP/t67lDyvN0ztBqTgw/Gz1doJZsAAAAA==");
    /** The same picture, lossless. */
    private static final byte[] LOSSLESS = Base64.getDecoder().decode(
            "UklGRioAAABXRUJQVlA4TB4AAAAvJ0AHAA8wHvfwvRD3/EcLhASEB/6v9UCO6H+UMAQ=");

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void aWebpInlinedFromTheVaultBecomesAPng() throws Exception {
        WebImageConverter converter = new WebImageConverter(1024 * 1024);

        for (byte[] webp : new byte[][] {LOSSY, LOSSLESS}) {
            String png = converter.convert("data:image/webp;base64," + Base64.getEncoder().encodeToString(webp))
                    .get(10, TimeUnit.SECONDS);
            assertTheFixture(png);
        }
    }

    @Test
    void aWebpServedUnderAPngNameIsFetchedAndConverted() throws Exception {
        AtomicInteger requests = serve(LOSSY, "image/webp", 200);
        WebImageConverter converter = new WebImageConverter(1024 * 1024);

        String png = converter.convert(url("/v2/format:webp/foto.png")).get(10, TimeUnit.SECONDS);

        assertTheFixture(png);
        assertEquals(1, requests.get());
    }

    @Test
    void anImageAlreadyConvertedIsNotFetchedAgain() throws Exception {
        AtomicInteger requests = serve(LOSSY, "image/webp", 200);
        WebImageConverter converter = new WebImageConverter(1024 * 1024);

        String first = converter.convert(url("/foto.png")).get(10, TimeUnit.SECONDS);
        String second = converter.convert(url("/foto.png")).get(10, TimeUnit.SECONDS);

        assertTheFixture(second);
        assertEquals(first, second);
        assertEquals(1, requests.get(), "fetched once");
    }

    @Test
    void whatCannotBeShownGivesNothing() throws Exception {
        serve(LOSSY, "image/webp", 404);
        WebImageConverter converter = new WebImageConverter(1024 * 1024);

        assertEquals("", converter.convert(url("/missing.png")).get(10, TimeUnit.SECONDS), "not found");
        assertEquals("", converter.convert("data:image/webp;base64,"
                + Base64.getEncoder().encodeToString("not an image".getBytes())).get(10, TimeUnit.SECONDS), "not an image");
        assertEquals("", converter.convert(null).get(10, TimeUnit.SECONDS));
    }

    @Test
    void anImageThatCouldNotBeFetchedIsTriedAgainNextTime() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            // Down the first time, back the second.
            int status = requests.incrementAndGet() == 1 ? 503 : 200;
            exchange.sendResponseHeaders(status, LOSSY.length);
            exchange.getResponseBody().write(LOSSY);
            exchange.close();
        });
        server.start();
        WebImageConverter converter = new WebImageConverter(1024 * 1024);

        assertEquals("", converter.convert(url("/foto.png")).get(10, TimeUnit.SECONDS));
        assertTheFixture(converter.convert(url("/foto.png")).get(10, TimeUnit.SECONDS));
        assertEquals(2, requests.get());
    }

    @Test
    void anImageLargerThanTheLimitIsNotRead() throws Exception {
        serve(LOSSY, "image/webp", 200);

        assertEquals("", new WebImageConverter(LOSSY.length - 1).convert(url("/foto.png")).get(10, TimeUnit.SECONDS));
        assertTheFixture(new WebImageConverter(LOSSY.length).convert(url("/foto.png")).get(10, TimeUnit.SECONDS));
    }

    private static void assertTheFixture(String dataUri) throws Exception {
        String prefix = "data:image/png;base64,";
        assertTrue(dataUri.startsWith(prefix), "a PNG data URI: " + dataUri.substring(0, Math.min(40, dataUri.length())));
        byte[] png = Base64.getDecoder().decode(dataUri.substring(prefix.length()));
        // A PNG file, decodable by what WebKit in JavaFX reads.
        assertEquals(0x89, png[0] & 0xFF);
        assertEquals("PNG", new String(png, 1, 3));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(40, image.getWidth());
        assertEquals(30, image.getHeight());
        int left = image.getRGB(5, 15);
        int right = image.getRGB(34, 15);
        assertTrue(red(left) > 180 && blue(left) < 80, "left is red: " + Integer.toHexString(left));
        assertTrue(blue(right) > 180 && red(right) < 80, "right is blue: " + Integer.toHexString(right));
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    private AtomicInteger serve(byte[] body, String contentType, int status) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return requests;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
