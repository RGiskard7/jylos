package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.jylos.service.RichLinkService;
import com.example.jylos.util.RichLinks.RichLink;
import com.sun.net.httpserver.HttpServer;

/**
 * Pasting a URL fetches its page to build a rich-link card. Only the head of the page is
 * needed, so the fetch must stop reading after a bounded number of bytes: a huge or
 * never-ending response must neither hang the fetch nor be buffered whole in memory.
 */
class RichLinkServiceDownloadLimitTest {

    private HttpServer server;
    private ExecutorService serverThreads;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/endless", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, 0); // chunked: no length, the body never ends
            byte[] padding = new byte[64 * 1024];
            Arrays.fill(padding, (byte) 'x');
            try (OutputStream body = exchange.getResponseBody()) {
                body.write("<html><head><title>Endless Page</title></head><body>"
                        .getBytes(StandardCharsets.UTF_8));
                while (true) {
                    body.write(padding);
                }
            } catch (IOException clientWentAway) {
                // Expected: the client stops reading and closes the connection.
            }
        });
        server.createContext("/small", exchange -> {
            byte[] page = "<html><head><title>Small Page</title><meta property=\"og:description\" content=\"desc\"></head></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, page.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(page);
            }
        });
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverThreads.shutdownNow();
    }

    @Test
    void stopsReadingANeverEndingPageAndStillParsesItsHead() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/endless";
        RichLink link = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> new RichLinkService().fetch(url));
        assertEquals("Endless Page", link.title());
    }

    @Test
    void aNormalPageIsParsedCompletely() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/small";
        RichLink link = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> new RichLinkService().fetch(url));
        assertEquals("Small Page", link.title());
        assertEquals("desc", link.description());
    }
}
