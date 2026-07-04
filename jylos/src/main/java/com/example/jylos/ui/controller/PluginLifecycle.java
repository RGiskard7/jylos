package com.example.jylos.ui.controller;

import java.util.List;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.plugin.Plugin;
import com.example.jylos.plugin.PluginLoader;
import com.example.jylos.plugin.PluginManager;

/**
 * Loads the core and external plugins and registers them with the {@link PluginManager}.
 */
class PluginLifecycle {

    private static final Logger logger = LoggerConfig.getLogger(PluginLifecycle.class);

    record LoadResult(int registeredCount, List<String> loadFailures) {
    }

    LoadResult registerCoreAndExternalPlugins(PluginManager pluginManager) {
        if (pluginManager == null) {
            return new LoadResult(0, List.of("PluginManager is null"));
        }

        pluginManager.registerPlugin(new com.example.jylos.plugin.mermaid.MermaidPlugin());

        PluginLoader.PluginLoadReport pluginLoadReport = PluginLoader.loadExternalPluginsWithReport();
        int registeredCount = 0;

        for (Plugin plugin : pluginLoadReport.getPlugins()) {
            if (pluginManager.registerPlugin(plugin)) {
                registeredCount++;
            } else {
                logger.warning("Failed to register external plugin: " + plugin.getName());
            }
        }

        return new LoadResult(registeredCount, pluginLoadReport.getFailures());
    }

    EventBus.Subscription subscribePluginUiEvents(EventBus eventBus, Runnable refreshListsAction) {
        if (eventBus == null) {
            return EventBus.Subscription.NO_OP;
        }

        return eventBus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, event -> {
            if (refreshListsAction != null) {
                refreshListsAction.run();
            }
            logger.info("Refreshed notes from plugin request");
        });
    }
}
