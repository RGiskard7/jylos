package com.example.jylos.ui.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Command routing table with stable IDs and backward-compatible aliases.
 */
class CommandRouting {

    record DispatchResult(boolean handled, String resolvedToken) {
    }

    private final Map<String, Runnable> routes = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    boolean isEmpty() {
        return routes.isEmpty();
    }

    void registerRoute(String id, String legacyName, Runnable action) {
        routes.put(id, action);
        aliases.put(id, id);
        if (legacyName != null && !legacyName.isEmpty()) {
            aliases.put(legacyName, id);
        }
    }

    void registerAlias(String alias, String commandId) {
        if (alias == null || alias.isEmpty() || commandId == null || commandId.isEmpty()) {
            return;
        }
        aliases.put(alias, commandId);
    }

    String resolveToken(String commandToken) {
        if (commandToken == null) {
            return "";
        }
        return aliases.getOrDefault(commandToken, commandToken);
    }

    DispatchResult dispatch(String commandToken, Predicate<String> fallbackExecutor) {
        String resolved = resolveToken(commandToken);
        Runnable route = routes.get(resolved);
        if (route != null) {
            route.run();
            return new DispatchResult(true, resolved);
        }

        boolean handledByFallback = false;
        if (fallbackExecutor != null) {
            handledByFallback = fallbackExecutor.test(commandToken)
                    || (!resolved.equals(commandToken) && fallbackExecutor.test(resolved));
        }
        return new DispatchResult(handledByFallback, resolved);
    }
}
