package com.example.jylos.data.dao.filesystem;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import io.methvin.watcher.visitor.FileTreeVisitor;

/**
 * Watches a folder vault for changes made by anyone (another app, a sync service such as
 * iCloud, the shell) and reports the changed paths in batches: FSEvents on macOS, the JDK's
 * watch service elsewhere ({@code io.methvin:directory-watcher}).
 *
 * <ul>
 * <li>No file is read: files are not hashed, and the folders are walked once at the start with
 * their dates and sizes only (reading an iCloud-offloaded note downloads it).</li>
 * <li>Hidden folders ({@code .git}, {@code .obsidian}, {@code .jylos}, {@code .trash}) are not
 * walked, and hidden paths and the temporary files of an atomic write are not reported.</li>
 * <li>Paths are reported under {@code root} as given, also where the system reports the real
 * path ({@code /private/var} for {@code /var} on macOS).</li>
 * <li>A batch is reported once {@value #QUIET_MS} ms pass without a new change, or at most
 * {@value #MAX_WAIT_MS} ms after its first one, so a sync of a hundred notes is one batch.</li>
 * <li>When the system drops changes (too many at once), {@code onLost} is called instead: the
 * caller reads the vault again.</li>
 * <li>{@value #RECONCILE_AFTER_MS} ms after it starts, the vault folder itself is reported once:
 * the library takes its inventory of the files and only then starts listening, on another
 * thread, so a change in between (or since the vault was listed) would otherwise never be
 * reported. A folder reported means "bring everything under it up to date".</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
public final class VaultWatcher implements AutoCloseable {

    private static final Logger logger = LoggerConfig.getLogger(VaultWatcher.class);

    static final long QUIET_MS = 400;
    static final long MAX_WAIT_MS = 2000;
    static final long RECONCILE_AFTER_MS = 1000;

    private final Path root;
    private final Path realRoot;
    private final Consumer<Set<Path>> onChanges;
    private final Runnable onLost;
    private final ScheduledExecutorService batcher = Executors.newSingleThreadScheduledExecutor(daemon("vault-watcher-batch"));
    private final ExecutorService eventLoop = Executors.newSingleThreadExecutor(daemon("vault-watcher"));
    private final Set<Path> pending = new LinkedHashSet<>();
    private boolean lost;
    private long firstPendingAt;
    private ScheduledFuture<?> flush;
    private DirectoryWatcher watcher;
    private volatile boolean closed;

    private VaultWatcher(Path root, Consumer<Set<Path>> onChanges, Runnable onLost) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.realRoot = this.root.toRealPath();
        this.onChanges = onChanges;
        this.onLost = onLost;
    }

    /**
     * Starts watching {@code root} and its folders.
     *
     * @param onChanges gets each batch of changed paths (files or folders, created, changed or
     *                  deleted), on the watcher's own thread
     * @param onLost    called, on the same thread, when the system dropped changes
     */
    public static VaultWatcher start(Path root, Consumer<Set<Path>> onChanges, Runnable onLost) throws IOException {
        VaultWatcher vaultWatcher = new VaultWatcher(root, onChanges, onLost);
        try {
            vaultWatcher.watcher = DirectoryWatcher.builder()
                    .path(vaultWatcher.realRoot)
                    .fileHashing(false)
                    .fileTreeVisitor(vaultWatcher::visitVisible)
                    .listener(vaultWatcher::onEvent)
                    .build();
            vaultWatcher.watcher.watchAsync(vaultWatcher.eventLoop);
            vaultWatcher.batcher.schedule(() -> vaultWatcher.enqueue(vaultWatcher.root),
                    RECONCILE_AFTER_MS, TimeUnit.MILLISECONDS);
        } catch (IOException | RuntimeException e) {
            vaultWatcher.close();
            throw e;
        }
        return vaultWatcher;
    }

    /** Walks the folders under {@code start}, but not into hidden ones; files by date and size only. */
    private void visitVisible(Path start, FileTreeVisitor.Callback onDirectory, FileTreeVisitor.Callback onFile)
            throws IOException {
        if (!Files.exists(start)) {
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(start) && isHidden(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                onDirectory.call(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!isHidden(file)) {
                    onFile.call(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void onEvent(DirectoryChangeEvent event) {
        if (closed) {
            return;
        }
        if (event.eventType() == DirectoryChangeEvent.EventType.OVERFLOW) {
            synchronized (this) {
                lost = true;
                schedule();
            }
            return;
        }
        Path path = asKnown(event.path());
        if (path == null || isHidden(path) || (!path.equals(root) && path.getFileName().toString().endsWith(".tmp"))) {
            return;
        }
        enqueue(path);
    }

    private void enqueue(Path path) {
        if (closed) {
            return;
        }
        synchronized (this) {
            pending.add(path);
            schedule();
        }
    }

    /** {@code path} under {@link #root}, wherever the system reported it; null when outside the vault. */
    private Path asKnown(Path path) {
        if (path == null) {
            return null;
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(realRoot)) {
            return root.resolve(realRoot.relativize(absolute).toString());
        }
        return absolute.startsWith(root) ? absolute : null;
    }

    private boolean isHidden(Path path) {
        Path known = path.startsWith(realRoot) ? root.resolve(realRoot.relativize(path).toString()) : path;
        return known.startsWith(root) && FileSystemNames.isHiddenWithinVault(root, known);
    }

    /** Reports the batch after a quiet moment, or now if its first change waited long enough. */
    private void schedule() {
        long now = System.currentTimeMillis();
        if (flush == null || flush.isDone()) {
            firstPendingAt = now;
        } else {
            flush.cancel(false);
        }
        long delay = now - firstPendingAt >= MAX_WAIT_MS ? 0 : QUIET_MS;
        if (!batcher.isShutdown()) {
            flush = batcher.schedule(this::report, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void report() {
        Set<Path> batch;
        boolean dropped;
        synchronized (this) {
            batch = new LinkedHashSet<>(pending);
            pending.clear();
            dropped = lost;
            lost = false;
        }
        if (closed) {
            return;
        }
        try {
            if (dropped) {
                onLost.run();
            } else if (!batch.isEmpty()) {
                onChanges.accept(Set.copyOf(batch));
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not apply the changes made outside the app", e);
        }
    }

    /** Stops watching; nothing is reported afterwards. */
    @Override
    public void close() {
        closed = true;
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException | RuntimeException e) {
                logger.log(Level.FINE, "Could not stop watching " + root, e);
            }
        }
        batcher.shutdownNow();
        eventLoop.shutdownNow();
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
