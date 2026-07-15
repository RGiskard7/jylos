# Event Bus Contract

Español: [es/EVENT_BUS_CONTRACT.md](es/EVENT_BUS_CONTRACT.md)

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
- Publishing one-to-one UI requests over the bus when a wired callback would be clearer.
- Using events for toolbar/dialog/list interactions that have a single owner in `MainController`.
- Routing theme changes, internal note opening, editor modified notifications, or shell status
  messages through the bus when the owner can wire a direct callback.
- Letting UI components or overlays reach for `EventBus.getInstance()` directly when
  their owner can pass typed callbacks or publish on their behalf.
- Treating feature widgets such as the Kanban overlay as independent event publishers
  instead of routing publication through their owning controller/support.
- Returning null subscriptions.
- Swallowing handler exceptions without logging.

## Approved extensibility fan-out

- `MainController` may publish note-selection fan-out events for plugins and other
  extensibility points that need to react to the active note.
- This does not reintroduce one-to-one note-open requests over the bus: the owner
  still opens the note directly and only then publishes the resulting active-note update.
