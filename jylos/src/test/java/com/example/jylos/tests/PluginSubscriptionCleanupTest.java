package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginManager;

/**
 * Regression: disabling a plugin must release its event subscriptions.
 *
 * <p>Every other kind of contribution — menu entries, side panels, preview enhancers,
 * editor hooks, toolbar buttons, block renderers, commands — is torn down by
 * {@code PluginManager.cleanupPluginRuntime} regardless of what the plugin's own
 * {@code shutdown()} does. Subscriptions were the one exception, so a plugin whose
 * {@code shutdown()} threw before reaching its cancellation code left handlers running
 * for the rest of the session: still executing code from a disabled plugin, and holding
 * its classloader alive so it could never be collected.</p>
 */
class PluginSubscriptionCleanupTest {

    private static final String[] PLUGIN_IDS = { "leaky", "tidy" };

    private EventBus eventBus;

    // Deliberately no EventBus.clear() here. The bus is a process-wide singleton, so
    // wiping it would tear down subscriptions belonging to whatever else is running in
    // this JVM — which showed up as unrelated tests failing at random. Nothing here needs
    // a quiet bus anyway: each test counts deliveries to its own handler, which no other
    // subscriber can touch.
    @BeforeEach
    void setUp() {
        eventBus = EventBus.getInstance();
        forgetDisabledPreferences();
    }

    @AfterEach
    void tearDown() {
        forgetDisabledPreferences();
    }

    /**
     * {@code disablePlugin} persists "disabled" into the real user {@link Preferences},
     * and {@code initializePlugin} then returns {@code true} for such a plugin
     * <em>without</em> initializing it. Left behind, that makes this test pass once and
     * fail on every later run, because the plugin would never get to subscribe.
     */
    private static void forgetDisabledPreferences() {
        Preferences preferences = Preferences.userNodeForPackage(PluginManager.class);
        for (String id : PLUGIN_IDS) {
            preferences.remove("plugin.disabled." + id);
        }
    }

    @Test
    void disablingAPluginStopsItsHandlersEvenWhenShutdownThrows() {
        AtomicInteger deliveries = new AtomicInteger();
        PluginManager manager = managerWith(eventBus);
        ThrowingOnShutdownPlugin plugin = new ThrowingOnShutdownPlugin("leaky", deliveries);

        assertTrue(manager.registerPlugin(plugin));
        assertTrue(manager.initializePlugin("leaky"));

        eventBus.publishSync(new NoteEvents.NoteSavedEvent(new Note("n1", "Note", "")));
        assertEquals(1, deliveries.get(), "the handler should receive events while the plugin is enabled");

        // The plugin throws on the way out, exactly as a half-broken plugin would. The
        // manager swallows that, so cleanup is the only thing left to release the handler.
        assertTrue(manager.disablePlugin("leaky"));
        assertTrue(plugin.shutdownAttempted, "shutdown should have been attempted");

        eventBus.publishSync(new NoteEvents.NoteSavedEvent(new Note("n2", "Note", "")));
        assertEquals(1, deliveries.get(),
                "a disabled plugin's handler must not keep firing after its shutdown threw");
    }

    @Test
    void aPluginThatCancelsItsOwnSubscriptionsIsUnaffected() {
        AtomicInteger deliveries = new AtomicInteger();
        PluginManager manager = managerWith(eventBus);
        SelfCancellingPlugin plugin = new SelfCancellingPlugin("tidy", deliveries);

        assertTrue(manager.registerPlugin(plugin));
        assertTrue(manager.initializePlugin("tidy"));

        eventBus.publishSync(new NoteEvents.NoteSavedEvent(new Note("n1", "Note", "")));
        assertEquals(1, deliveries.get());

        // Cancelled once by the plugin, once by the manager: cancel() is idempotent, so
        // the well-behaved plugin sees no change in behaviour and no error.
        assertTrue(manager.disablePlugin("tidy"));

        eventBus.publishSync(new NoteEvents.NoteSavedEvent(new Note("n2", "Note", "")));
        assertEquals(1, deliveries.get());
    }

    private static PluginManager managerWith(EventBus eventBus) {
        return new PluginManager(null, null, null, eventBus, null, null, null, null, null, null, null, note -> {
        }, null);
    }

    /** Subscribes on initialize, then throws on shutdown before it can unsubscribe. */
    private static final class ThrowingOnShutdownPlugin implements Plugin {
        private final String id;
        private final AtomicInteger deliveries;
        private boolean shutdownAttempted;

        private ThrowingOnShutdownPlugin(String id, AtomicInteger deliveries) {
            this.id = id;
            this.deliveries = deliveries;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public void initialize(PluginContext context) {
            context.subscribe(NoteEvents.NoteSavedEvent.class, event -> deliveries.incrementAndGet());
        }

        @Override
        public void shutdown() {
            shutdownAttempted = true;
            throw new IllegalStateException("plugin blew up before unsubscribing");
        }
    }

    /** The well-behaved shape: keeps its own handle and cancels it in shutdown(). */
    private static final class SelfCancellingPlugin implements Plugin {
        private final String id;
        private final AtomicInteger deliveries;
        private EventBus.Subscription subscription;

        private SelfCancellingPlugin(String id, AtomicInteger deliveries) {
            this.id = id;
            this.deliveries = deliveries;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public void initialize(PluginContext context) {
            subscription = context.subscribe(NoteEvents.NoteSavedEvent.class,
                    event -> deliveries.incrementAndGet());
        }

        @Override
        public void shutdown() {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }
}
