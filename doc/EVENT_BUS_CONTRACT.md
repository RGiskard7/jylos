# Event Bus Contract

## Principles

- Events represent state changes or commands with explicit semantics.
- Mutable command events (for example save/delete) must not be republished in loops.
- Subscriptions must return a safe handle (no null subscription).

## Required practices

1. Publish typed events under `com.example.jylos.event.events`.
2. Avoid subscribers that trigger the same command path recursively.
3. Unsubscribe on controller teardown (`EventBus.Subscription` lists in controllers).
4. Log exceptions with `LoggerConfig`; do not use `printStackTrace()` in production paths.

## System actions

Toolbar and menu items publish `SystemActionEvent` with an `ActionType` enum value. `MainController` owns a single `EnumMap<ActionType, Runnable>` handler table (including view toggles such as `GRAPH_VIEW`, `KANBAN_VIEW`, `FOCUS_MODE`, editor modes, `PRIVATE_TOGGLE`/`NOTES_LOCK`, and Git actions when enabled). Handlers commonly delegate to the relevant feature support class (`OverlaySupport`, `GitController`, `PrivacySupport`, …).

Handlers must not re-publish the same `SystemActionEvent` they were invoked for.

## Discouraged patterns

- Recursive publication of save/delete command events from within their own handlers.
- Returning null subscriptions.
- Swallowing handler exceptions without logging.
