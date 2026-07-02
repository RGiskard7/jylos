package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.plugin.EditorHook;
import com.example.jylos.plugin.EditorHookRegistry;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginContext;
import com.example.jylos.plugin.PluginManager;
import com.example.jylos.plugin.PluginMenuRegistry;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.plugin.PreviewEnhancerRegistry;
import com.example.jylos.plugin.SidePanelRegistry;
import com.example.jylos.plugin.ToolbarRegistry;

import javafx.scene.Node;

class PluginManagerLifecycleTest {

    @Test
    void registerInitializeDisableEnableAndUnregisterFlowWorks() {
        RecordingMenuRegistry menu = new RecordingMenuRegistry();
        RecordingSideRegistry side = new RecordingSideRegistry();
        RecordingPreviewRegistry preview = new RecordingPreviewRegistry();
        RecordingHookRegistry hooks = new RecordingHookRegistry();
        RecordingToolbarRegistry toolbar = new RecordingToolbarRegistry();
        PluginManager manager = new PluginManager(null, null, null, null, null, menu, side, preview, hooks, toolbar,
                note -> {
                });
        CountingPlugin plugin = new CountingPlugin("alpha");

        assertTrue(manager.registerPlugin(plugin));
        assertTrue(manager.initializePlugin("alpha"));
        assertTrue(manager.isPluginEnabled("alpha"));
        assertEquals(1, plugin.initializeCalls);

        assertTrue(manager.disablePlugin("alpha"));
        assertEquals(1, plugin.shutdownCalls);
        assertEquals(1, menu.removeCalls);
        assertEquals(1, side.removeAllCalls);
        assertEquals(1, hooks.removeCalls);
        assertEquals(1, toolbar.removeCalls);
        assertFalse(manager.isPluginEnabled("alpha"));

        assertTrue(manager.enablePlugin("alpha"));
        assertTrue(manager.isPluginEnabled("alpha"));

        assertTrue(manager.unregisterPlugin("alpha"));
        assertEquals(2, plugin.shutdownCalls);
        assertFalse(manager.getPlugin("alpha").isPresent());
    }

    @Test
    void shutdownAllCallsShutdownOnRegisteredPlugins() {
        PluginManager manager = new PluginManager(null, null, null, null, null,
                new RecordingMenuRegistry(), new RecordingSideRegistry(), new RecordingPreviewRegistry(),
                new RecordingHookRegistry(), new RecordingToolbarRegistry(), note -> {
                });
        CountingPlugin one = new CountingPlugin("one");
        CountingPlugin two = new CountingPlugin("two");

        manager.registerPlugin(one);
        manager.registerPlugin(two);
        manager.initializeAll();

        manager.shutdownAll();

        assertTrue(one.shutdownCalls >= 1);
        assertTrue(two.shutdownCalls >= 1);
    }

    @Test
    void initializePluginShouldContainThrowableFailuresAndShutdownAllShouldNotCallBrokenPlugins() {
        PluginManager manager = new PluginManager(null, null, null, null, null,
                new RecordingMenuRegistry(), new RecordingSideRegistry(), new RecordingPreviewRegistry(),
                new RecordingHookRegistry(), new RecordingToolbarRegistry(), note -> {
                });
        FailingPlugin plugin = new FailingPlugin("broken");

        assertFalse(manager.initializePlugin("broken"));
        manager.registerPlugin(plugin);
        assertFalse(manager.initializePlugin("broken"));
        assertEquals(PluginManager.PluginState.ERROR, manager.getPluginState("broken"));

        manager.shutdownAll();

        assertEquals(0, plugin.shutdownCalls);
    }

    private static final class CountingPlugin implements Plugin {
        private final String id;
        private int initializeCalls = 0;
        private int shutdownCalls = 0;

        private CountingPlugin(String id) {
            this.id = id;
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
            initializeCalls++;
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
        }
    }

    private static final class FailingPlugin implements Plugin {
        private final String id;
        private int shutdownCalls = 0;

        private FailingPlugin(String id) {
            this.id = id;
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
            throw new NoClassDefFoundError("missing plugin dependency");
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
        }
    }

    private static final class RecordingMenuRegistry implements PluginMenuRegistry {
        private int removeCalls = 0;

        @Override
        public void registerMenuItem(String pluginId, String category, String itemName, Runnable action) {
        }

        @Override
        public void registerMenuItem(String pluginId, String category, String itemName, String shortcut,
                Runnable action) {
        }

        @Override
        public void addMenuSeparator(String pluginId, String category) {
        }

        @Override
        public void removePluginMenuItems(String pluginId) {
            removeCalls++;
        }

        @Override
        public boolean isPluginEnabled(String pluginId) {
            return true;
        }
    }

    private static final class RecordingSideRegistry implements SidePanelRegistry {
        private int removeAllCalls = 0;

        @Override
        public void registerSidePanel(String pluginId, String panelId, String title, Node content, String icon) {
        }

        @Override
        public void removeSidePanel(String pluginId, String panelId) {
        }

        @Override
        public void removeAllSidePanels(String pluginId) {
            removeAllCalls++;
        }

        @Override
        public void setPluginPanelsVisible(boolean visible) {
        }

        @Override
        public boolean isPluginPanelsVisible() {
            return true;
        }
    }

    private static final class RecordingPreviewRegistry implements PreviewEnhancerRegistry {
        @Override
        public void registerPreviewEnhancer(String pluginId, PreviewEnhancer enhancer) {
        }

        @Override
        public void unregisterPreviewEnhancer(String pluginId) {
        }
    }

    private static final class RecordingHookRegistry implements EditorHookRegistry {
        private int removeCalls = 0;

        @Override
        public void registerEditorHook(String pluginId, EditorHook hook) {
        }

        @Override
        public void unregisterEditorHooks(String pluginId) {
            removeCalls++;
        }
    }

    private static final class RecordingToolbarRegistry implements ToolbarRegistry {
        private int removeCalls = 0;

        @Override
        public void registerToolbarButton(String pluginId, String buttonId, String tooltip,
                String iconLiteral, Runnable action) {
        }

        @Override
        public void removeToolbarButtons(String pluginId) {
            removeCalls++;
        }
    }
}
