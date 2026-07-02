package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.jylos.event.AppEvent;
import com.example.jylos.event.EventBus;
import com.example.jylos.plugin.PluginContext;

import javafx.application.Platform;

class EventBusContractTest {

    private static boolean fxRuntimeAvailable = false;

    private static final class DummyEvent extends AppEvent {
    }

    private static final class OrderedEvent extends AppEvent {
        private final int sequence;

        private OrderedEvent(int sequence) {
            this.sequence = sequence;
        }

        int getSequence() {
            return sequence;
        }
    }

    @BeforeAll
    static void initFxRuntime() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            fxRuntimeAvailable = latch.await(2, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            fxRuntimeAvailable = true;
        } catch (Exception e) {
            fxRuntimeAvailable = false;
        }
    }

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void pluginContextSubscribeReturnsNoOpWhenEventBusIsMissing() {
        PluginContext context = new PluginContext(
                "test-plugin",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                note -> {
                });

        EventBus.Subscription subscription = context.subscribe(DummyEvent.class, event -> {
        });
        assertNotNull(subscription);
        assertFalse(subscription.isCancelled());

        subscription.cancel();
        assertFalse(subscription.isCancelled());
    }

    @Test
    void noOpSubscriptionIsSafeAndIdempotent() {
        EventBus.Subscription subscription = EventBus.Subscription.NO_OP;
        assertNotNull(subscription);
        assertFalse(subscription.isCancelled());

        subscription.cancel();
        subscription.cancel();

        assertFalse(subscription.isCancelled());
    }

    @Test
    void concurrentPublishSyncDispatchesToAllSubscribers() throws Exception {
        EventBus bus = EventBus.getInstance();
        AtomicInteger counter = new AtomicInteger(0);
        int subscribers = 20;
        int publishes = 100;

        for (int i = 0; i < subscribers; i++) {
            bus.subscribe(DummyEvent.class, event -> counter.incrementAndGet());
        }

        ExecutorService pool = Executors.newFixedThreadPool(6);
        for (int i = 0; i < publishes; i++) {
            pool.submit(() -> bus.publishSync(new DummyEvent()));
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(subscribers * publishes, counter.get());
    }

    @Test
    void asyncPublishRunsOnFxThreadAndKeepsOrder() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable, "JavaFX runtime no disponible");

        EventBus bus = EventBus.getInstance();
        CountDownLatch latch = new CountDownLatch(3);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean onFxThread = new AtomicBoolean(true);

        bus.subscribe(OrderedEvent.class, event -> {
            onFxThread.set(onFxThread.get() && Platform.isFxApplicationThread());
            order.add(event.getSequence());
            latch.countDown();
        });

        Thread publisher = new Thread(() -> {
            bus.publish(new OrderedEvent(1));
            bus.publish(new OrderedEvent(2));
            bus.publish(new OrderedEvent(3));
        });
        publisher.start();
        publisher.join();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(onFxThread.get());
        assertEquals(List.of(1, 2, 3), order);
    }
}
