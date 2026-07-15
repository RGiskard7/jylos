# UI, workflows, and extension points

Most day-to-day product logic lives in the UI layer and its supporting services. The codebase follows a "thin shell, focused helpers" pattern: `MainController` orchestrates the screen, while smaller controllers/support classes own focused workflows. The same pattern is echoed in contributor guidance and in recent refactors that split shell responsibilities into targeted support classes.

## Primary UI shell

`jylos/src/main/java/com/example/jylos/ui/controller/MainController.java` is the root FXML controller. It coordinates:

- sidebar navigation
- notes list and editor state
- graph view and right-side panels
- toolbar commands
- plugins and extension registries
- theme and snippet selection
- status updates and system actions

The `MainController` docstring explicitly says persistence logic should live in DAOs/services where possible, and the top-level contributor guidance reinforces that new feature bodies should not be added directly into `MainController`.

## Workflow controllers and support classes

The repository now has many small UI classes, each focused on one domain area. Examples include:

- `EditorController` and editor support classes for note editing
- `SidebarController` for folder/tag navigation
- `NotesListController` for list and filter behavior
- `ToolbarController` for top-level actions
- `GraphController` for the graph view
- `WorkspaceController` for save/open/manage workspace workflows
- `GitController` for vault Git actions
- `ImportSupport`, `FocusModeSupport`, `PrivacySupport`, `StatusBarSupport`, and similar helpers for specific behaviors

This structure is important for maintainability: changes should usually land in the smallest class that owns the feature rather than widening the shell controller.

## Major user workflows

### Editing and preview

The app uses JavaFX, RichTextFX, Markdown rendering utilities, and a preview panel to support live editing. Notes can be opened in tabs, edited, previewed, and linked to other notes through wiki-style links.

### Graph, backlinks, and link navigation

Graph and backlink features are built on note-link relationships and the graph/insights services. These features are core to the app's Obsidian-like workflow.

### Workspaces

Workspaces let users capture and restore UI state snapshots. They are managed in `WorkspaceController` and persisted via `WorkspaceService`.

### Search

`jylos/src/main/java/com/example/jylos/search/SearchQueryParser.java` accepts a forgiving mini-language with free text, quoted phrases, negation, and `operator:value` clauses. Search is designed to degrade gracefully: invalid operators or values generate warnings rather than hard failures.

### Plugins

`PluginManager` handles registration, initialization, enable/disable state, dependencies, and UI extension points. Plugins can contribute menu items, side panels, preview enhancers, editor hooks, and toolbar buttons.

### Update checks

`UpdateChecker` queries the GitHub Releases API for a newer version and returns a structured result that distinguishes "available", "current", and "failed" states.

## Source maps

- Root shell: `jylos/src/main/java/com/example/jylos/ui/controller/MainController.java`
- Workspaces: `jylos/src/main/java/com/example/jylos/ui/controller/WorkspaceController.java`
- Search parsing: `jylos/src/main/java/com/example/jylos/search/SearchQueryParser.java`
- Plugins: `jylos/src/main/java/com/example/jylos/plugin/PluginManager.java`
- Update checks: `jylos/src/main/java/com/example/jylos/service/UpdateChecker.java`
- App bootstrap: `jylos/src/main/java/com/example/jylos/Main.java`
