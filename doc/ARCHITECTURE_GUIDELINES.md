# Architecture Guidelines

Normative growth rules for Jylos. This document complements [ARCHITECTURE.md](ARCHITECTURE.md): that file describes the current system; this one defines how we should extend and gradually sanitize it without a rewrite.

## Scope

- Applies to all new code.
- Applies to touched existing code when a change is already in flight.
- Does **not** require broad refactors just to satisfy the rules mechanically.

## Architectural stance

Jylos is a desktop monolith with a layered core and a few feature-oriented subsystems:

```text
ui/ -> service/ -> data/dao/
          |
          +-> feature packages (graph/, git/, search/, workspace/, insights/, plugin/)
```

This is intentional. Jylos is **not** aiming for framework-heavy DI, strict hexagonal layering, or a full rewrite into feature slices. The goal is a pragmatic architecture with clear responsibilities and predictable growth.

## Dependency direction

Default direction:

1. `ui/*` may depend on `service/*`, `data.models/*`, typed events, and feature APIs it coordinates.
2. `service/*` may depend on `data.dao/*`, `data.models/*`, `util/*`, and feature-internal collaborators.
3. `data.dao/*` may depend on `data.models/*`, storage helpers, and low-level infrastructure only.
4. `data.models/*` must stay independent of UI, DAOs, and JavaFX.

Forbidden by default:

- `service/*` depending on JavaFX UI types
- `data/*` depending on `ui/*`
- controllers calling storage directly when a service already owns that workflow
- feature packages reaching into unrelated UI internals instead of going through a small API or callback

Allowed exceptions must be narrow, documented, and preferable to a larger architectural contortion.

## UI layer rules

The presentation layer is `ui/*`. It is allowed to coordinate workflows, but it must not become the place where persistence policy or cross-cutting business rules live.

### Naming and responsibilities

Use these names consistently:

- `*Controller`
  - FXML-backed controller or feature coordinator with a stable UI-facing API
  - owns view state, event handlers, and composition for that screen/panel/overlay
- `*Support`
  - helper owned by another controller
  - encapsulates one cohesive UI behavior or panel
  - does not represent a primary screen of its own
- `*Store`
  - persistence of UI preferences or serialized UI state
- `*Catalog`
  - read-only or cache-like registry of UI resources/options
- `*Operations`
  - small imperative helper for a narrow workflow; do not use as a dumping ground

If a class does not clearly fit one of those roles, stop and rename or move it before adding more logic.

### `MainController`

`MainController` is the shell coordinator. It may own:

- app bootstrapping and wiring
- note open/save/close/tab/navigation coordination
- high-level dispatch of typed actions to feature helpers

It must **not** absorb new self-contained feature bodies. If a feature can be explained as "a panel/overlay/dialog/workflow with its own state", it belongs in a dedicated `*Controller` or `*Support`.

### Injection style

Jylos currently has mixed injection styles. Going forward, use these rules:

- For new helper-style UI features owned by `MainController`, prefer `wire(...)`.
- For FXML-instantiated controllers, setter injection is acceptable because `FXMLLoader` owns construction.
- Within a single class, do not freely mix:
  - `wire(...)`
  - unrelated ad-hoc setters
  - direct `AppContext` lookups

Pick one primary composition style per class and keep dependencies visible.

### What belongs in controllers

Controllers and supports may:

- translate UI events into service calls
- validate view-local input
- coordinate dialogs, background tasks, and callbacks
- publish typed UI or shell events

Controllers and supports should not:

- implement storage policy
- contain SQL or filesystem traversal that belongs in DAOs/services
- duplicate domain rules already owned by a service
- become generic utility buckets

## Services

`service/*` is the application/business layer. The name is correct even though Jylos exposes no HTTP API. A service here means "owner of a use case, policy, or reusable application workflow".

### Allowed service categories

These categories are all valid:

- entity/application services
  - `NoteService`, `FolderService`, `TagService`
- feature services
  - `BacklinkService`, `ImportService`, `RichLinkService`
- technical services
  - `EncryptionService`, `DatabaseBackupService`, `NoteHistoryService`
- indexes/caches
  - `NoteTitleIndex`

What matters is not the suffix alone, but whether the class has a coherent responsibility.

### Service rules

Services should:

- expose use-case level operations
- encapsulate rules shared by multiple UI flows
- own coordination across DAOs when needed
- stay independent from JavaFX presentation types

Services should not:

- reach into FXML/controller state
- become static helper bags
- hide unrelated responsibilities behind one broad class

If a class mostly transforms strings, collections, or files without owning workflow policy, it probably belongs in `util/*` or a feature package, not necessarily in `service/*`.

### Event publication from services

Prefer one canonical publisher per workflow.

In the current codebase, many note-related events are published by workflow coordinators in the UI layer after a successful mutation. That is acceptable and should stay consistent within a given flow.

Avoid creating duplicate publication paths where:

- one caller publishes after a save
- another path relies on the service to publish the same event

Choose one owner for that event contract and keep it stable.

## Feature packages

Packages such as `graph/`, `git/`, `search/`, `workspace/`, `insights/`, and `plugin/` are not architectural anomalies. They exist because some capabilities cut across several layers and deserve a dedicated boundary.

Use a dedicated feature package when all of the following are true:

1. the capability has its own vocabulary or model
2. it spans more than one layer
3. forcing it into `service/*` or `ui/*` would blur ownership

Do **not** create a new top-level feature package for a small helper that could live cleanly in an existing layer.

## EventBus

`EventBus` is the project's in-process publish-subscribe mechanism, i.e. an Observer-style coordination tool. It is not a replacement for JavaFX threading, not a general service locator, and not the default answer to every interaction.

See also [EVENT_BUS_CONTRACT.md](EVENT_BUS_CONTRACT.md).

### Good uses

- fan-out refresh after a state change
- cross-feature coordination where direct references would be awkward
- plugin extensibility points
- shell-level actions that multiple UI components can trigger

### Bad uses

- simple parent-child calls where a direct method call is clearer
- request/response flows that need an immediate return value
- critical invariants whose order should remain explicit in code
- hiding core mutation logic behind event chains

### Event categories

Keep event intent clear:

- domain facts
  - "a note was saved", "a folder was deleted"
- UI requests
  - "open this note", "show this dialog"
- shell actions
  - `SystemActionEvent`

Do not casually blur those categories. A shell action is not the same thing as a persisted domain fact.

### Controlling indirect coupling

Indirect coupling is acceptable only when it stays legible. To keep it under control:

1. publish typed events with explicit names
2. keep subscriptions close to the owning feature
3. unsubscribe on teardown
4. avoid recursive publication loops
5. document "single owner" actions such as `SystemActionEvent`
6. prefer direct calls when only one collaborator needs the result

If following an event flow requires reading half the codebase, the event boundary is too broad.

## `AppContext`

`AppContext` is a lightweight global service locator, not an application state store.

It currently exposes bootstrapped references such as DAOs, core services, and the active `ResourceBundle`. That is acceptable for a plain JavaFX desktop app, but it must stay constrained.

### What `AppContext` is for

- bootstrapped application-wide references
- leaf UI helpers where passing the dependency explicitly would add disproportionate wiring noise
- rare singleton-like adapters that are tied to application lifecycle

### What `AppContext` is not for

- storing live UI/session state
- bypassing proper composition by default
- making domain dependencies invisible just because wiring is inconvenient

### Rules

- New domain logic should not default to `AppContext`.
- New services should receive dependencies explicitly unless there is a narrow, documented exception.
- UI code may read from `AppContext` sparingly, but new feature code should prefer explicit wiring first.
- Keep `AppContext` small; do not keep expanding it into a miscellaneous registry.

If a class can reasonably receive a dependency through `wire(...)`, a setter, or a constructor, prefer that over a new `AppContext.getX()` call.

## Current inconsistencies we should reduce

These are known cleanup targets, not reasons to rewrite the project:

- mixed injection patterns across UI classes
- too many presentation-oriented helpers living under `ui/controller` without a tighter taxonomy
- growing reliance on `AppContext` in some helpers instead of explicit composition
- event categories sharing one bus without always making intent obvious
- `MainController` remaining the largest maintenance hotspot

New code should move the project away from those patterns, not reinforce them.

## Review checklist for new code

Before merging a feature, ask:

1. Does the class clearly belong to `ui`, `service`, `data`, `util`, or a feature package?
2. Is the class name aligned with its role (`Controller`, `Support`, `Store`, `Catalog`, `Service`, etc.)?
3. Are dependencies explicit where they reasonably can be?
4. Is `AppContext` being used as a narrow convenience, not as a lazy default?
5. Would a direct call be clearer than an event?
6. If an event is used, who owns publishing and who unsubscribes?
7. Did we keep `MainController` as coordinator instead of growing feature logic inside it?

## Migration posture

Sanitize opportunistically:

- when touching a class, make it slightly more explicit and cohesive
- do not refactor unrelated areas just to satisfy aesthetics
- prefer small, stable improvements over big architecture swings

The target is not theoretical purity. The target is a codebase that stays understandable, testable, and publishable as Jylos grows.
