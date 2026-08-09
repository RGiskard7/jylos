package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.workspace.Workspace;
import com.example.jylos.workspace.WorkspaceRepository;

/**
 * Verifies {@link WorkspaceRepository#update} closes the read-modify-write race that
 * separate {@code loadAll()}/{@code saveAll()} calls used to leave open between two
 * concurrent callers (e.g. autosave racing a manual save).
 */
class WorkspaceRepositoryConcurrencyTest {

    private Workspace workspaceNamed(String name) {
        return new Workspace(name, name, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z",
                List.of(), null, "SPLIT", true, false, 0.22, 0.25, "filesystem");
    }

    @Test
    void concurrentUpdatesDoNotLoseWrites(@TempDir Path dir) throws InterruptedException {
        WorkspaceRepository repository = new WorkspaceRepository(dir.resolve("workspaces.dat"));
        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            String name = "ws-" + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    repository.update(all -> {
                        all.add(workspaceNamed(name));
                        return all;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "workers failed to start");
        go.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "workers did not finish in time");
        pool.shutdown();

        List<Workspace> persisted = repository.loadAll();
        assertEquals(threadCount, persisted.size(),
                "every concurrent update() must be reflected — none should be overwritten by a racing save");
        assertEquals(threadCount,
                persisted.stream().map(Workspace::name).collect(Collectors.toSet()).size(),
                "each worker's workspace name must appear exactly once");
        IntStream.range(0, threadCount).forEach(i ->
                assertTrue(persisted.stream().anyMatch(w -> ("ws-" + i).equals(w.name())),
                        "missing workspace ws-" + i));
    }
}
