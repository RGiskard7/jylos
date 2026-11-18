package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.ui.UiDialogs;
import com.example.jylos.workspace.SessionStore;
import com.example.jylos.workspace.TabHistory;
import com.example.jylos.workspace.Workspace;
import com.example.jylos.workspace.WorkspaceService;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Workspaces as working contexts (writing, research, a project…): each one keeps its tabs,
 * their back and forward, the active one and a little layout. Opening one replaces the open
 * tabs with its own, after saving the one in use (unless that is turned off). The status bar
 * names the one in use and switches, saves and manages them; the File menu and the command
 * palette do the same. The tabs open when the app closes, and the workspace in use, come back
 * at the next start (the last session).
 *
 * <p>Persistence lives in {@link WorkspaceService} and {@link SessionStore}; the window's tabs
 * and layout are reached through {@link Host}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class WorkspaceController {

    /** Preference: save the workspace in use when another one is opened (on by default). */
    static final String SAVE_ON_SWITCH = "workspace.save_on_switch";
    /** Preference: the ids of the workspaces used last, the last one first. */
    static final String RECENT = "workspace.recent";
    /** How many workspaces the status bar menu lists; the dialog finds the rest. */
    static final int MENU_LIMIT = 8;
    private static final int RECENT_KEPT = 20;

    /**
     * What the window does for the workspaces.
     *
     * @param liveState    the open tabs, the active one and the layout, as a workspace with no
     *                     identity yet
     * @param history      the back and forward of each open tab, in their order
     * @param closeAllTabs closes every tab once the open note's unsaved edits are dealt with;
     *                     {@code false} when the user cancels
     * @param restoreTabs  reopens tabs with their history, returning how many notes are gone
     * @param applyLayout  applies a workspace's view mode, panels and dividers
     */
    record Host(Supplier<Workspace> liveState, Supplier<List<TabHistory>> history, BooleanSupplier closeAllTabs,
            TabsRestorer restoreTabs, Consumer<Workspace> applyLayout) {
    }

    /** Reopens tabs in order, with the history of each; returns how many of their notes are gone. */
    @FunctionalInterface
    interface TabsRestorer {
        int restore(List<String> ids, String activeId, List<TabHistory> histories);
    }

    private final WorkspaceService service;
    private final SessionStore session;

    private Host host;
    private Function<String, String> i18n = key -> key;
    private Consumer<String> status = message -> {
    };
    private Supplier<Scene> sceneSupplier = () -> null;
    private Preferences prefs;
    private Label indicator;

    /** The workspace in use: the last one opened or saved, the target of "Save". */
    private String currentId;
    private String currentName;
    /** The last session is reopened once, and kept only after that (so it is not lost first). */
    private boolean sessionRestored;

    public WorkspaceController() {
        this(new WorkspaceService(), new SessionStore());
    }

    WorkspaceController(WorkspaceService service, SessionStore session) {
        this.service = service;
        this.session = session;
    }

    /**
     * Connects to the window, and puts the workspace in use in its status bar, after the
     * first item (the storage switcher). Wiring again keeps the one indicator.
     */
    void wire(Host host, Function<String, String> i18n, Consumer<String> status, Supplier<Scene> sceneSupplier,
            Preferences prefs, HBox statusBar) {
        this.host = host;
        this.i18n = i18n != null ? i18n : key -> key;
        this.status = status != null ? status : message -> {
        };
        this.sceneSupplier = sceneSupplier != null ? sceneSupplier : () -> null;
        this.prefs = prefs;
        if (indicator == null && statusBar != null) {
            indicator = new Label();
            indicator.getStyleClass().addAll("status-git", "status-workspace");
            FontIcon icon = new FontIcon("fth-layout");
            icon.getStyleClass().add("feather-icon-faint");
            indicator.setGraphic(icon);
            indicator.setGraphicTextGap(5);
            indicator.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY || event.getButton() == MouseButton.SECONDARY) {
                    menu().show(indicator, Side.TOP, 0, 0);
                }
            });
            Separator separator = new Separator(Orientation.VERTICAL);
            separator.getStyleClass().add("status-separator");
            int at = Math.min(1, statusBar.getChildren().size());
            statusBar.getChildren().addAll(at, List.of(separator, indicator));
        }
        showCurrent();
    }

    /** The menu and palette actions on workspaces: save, save as, and open or manage them. */
    void register(Map<ActionType, Runnable> handlers) {
        handlers.put(ActionType.WORKSPACE_SAVE, this::saveCurrent);
        handlers.put(ActionType.WORKSPACE_SAVE_AS, this::saveCurrentAs);
        handlers.put(ActionType.WORKSPACE_OPEN, this::manageDialog);
        handlers.put(ActionType.WORKSPACE_MANAGE, this::manageDialog);
    }

    // ── Opening ──────────────────────────────────────────────────────────────

    /**
     * Opens {@code ws}: the one in use is saved first (when that is on and it is another one),
     * every tab closes and its tabs open, with their history, and its layout applies. When the
     * user cancels the open note's save prompt, nothing changes.
     */
    void switchTo(Workspace ws) {
        if (ws == null || host == null) {
            return;
        }
        if (currentId == null) {
            session.saveDefault(captureLive(), host.history().get()); // to come back to "no workspace"
        } else if (saveOnSwitch() && !currentId.equals(ws.id())) {
            saveInUse();
        }
        if (!host.closeAllTabs().getAsBoolean()) {
            return;
        }
        int missing = host.restoreTabs().restore(ws.openNoteIds(), ws.activeNoteId(), service.history(ws.id()));
        host.applyLayout().accept(ws);
        setCurrent(ws.id(), ws.name());
        markUsed(ws.id());
        String storage = host.liveState().get() != null ? host.liveState().get().storageMode() : "";
        if (ws.storageMode() != null && !ws.storageMode().isBlank() && !ws.storageMode().equalsIgnoreCase(storage)) {
            updateStatus(getString("workspace.status.storage_mismatch"));
        } else if (missing > 0) {
            updateStatus(format("workspace.status.missing", missing));
        } else {
            updateStatus(format("workspace.status.opened", ws.name()));
        }
    }

    /**
     * Back to "no workspace": the workspace in use is saved (when that is on), and the tabs it
     * had when a workspace was opened from it come back.
     */
    void switchToNone() {
        if (currentId == null || host == null) {
            return;
        }
        if (saveOnSwitch()) {
            saveInUse();
        }
        if (!host.closeAllTabs().getAsBoolean()) {
            return;
        }
        session.loadDefault().ifPresent(tabs -> {
            host.restoreTabs().restore(tabs.openNoteIds(), tabs.activeNoteId(), session.defaultHistory());
            host.applyLayout().accept(tabs);
        });
        setCurrent(null, null);
        updateStatus(getString("workspace.status.none_in_use"));
    }

    // ── Saving ───────────────────────────────────────────────────────────────

    /** Saves into the workspace in use, or asks for a name when there is none. */
    public void saveCurrent() {
        if (currentId != null && service.findById(currentId).isPresent()) {
            saveInUse();
            updateStatus(format("workspace.status.saved", currentName));
        } else {
            saveCurrentAs();
        }
    }

    /** Asks for a name and saves the tabs and layout under it; that workspace is then in use. */
    public void saveCurrentAs() {
        if (host == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(currentName != null ? currentName : "");
        dialog.setTitle(getString("workspace.save_as.title"));
        dialog.setHeaderText(getString("workspace.save_as.header"));
        dialog.setContentText(getString("workspace.save_as.content"));
        style(dialog);
        dialog.showAndWait()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .ifPresent(name -> {
                    Workspace saved = service.save(name, captureLive(), host.history().get());
                    setCurrent(saved.id(), saved.name());
                    markUsed(saved.id());
                    updateStatus(format("workspace.status.saved", saved.name()));
                });
    }

    private void saveInUse() {
        service.findById(currentId).ifPresent(ws -> service.save(ws.name(), captureLive(), host.history().get()));
    }

    // ── The last session ─────────────────────────────────────────────────────

    /** At startup: reopens the last session's tabs and its workspace in use, once. */
    void restoreSession() {
        if (sessionRestored || host == null) {
            return;
        }
        sessionRestored = true;
        session.load().ifPresent(last -> {
            host.restoreTabs().restore(last.openNoteIds(), last.activeNoteId(), session.history());
            service.findById(last.id()).ifPresent(ws -> setCurrent(ws.id(), ws.name()));
        });
    }

    /** Keeps the open tabs and the workspace in use for the next start (on closing the app). */
    void saveSession() {
        if (!sessionRestored || host == null) {
            return;
        }
        Workspace live = captureLive();
        session.save(new Workspace(Objects.requireNonNullElse(currentId, ""),
                Objects.requireNonNullElse(currentName, ""), null, null, live.openNoteIds(), live.activeNoteId(),
                live.viewMode(), live.sidebarVisible(), live.focusMode(), live.splitMain(), live.splitContent(),
                live.storageMode()), host.history().get());
    }

    // ── Status bar menu ──────────────────────────────────────────────────────

    /**
     * The status bar's menu: "no workspace" and the {@value #MENU_LIMIT} workspaces used last,
     * by name (the one in use ticked; another one opens it), save, save as, manage (or "all
     * workspaces" when there are more), and whether to save the one in use on switching.
     */
    ContextMenu menu() {
        ContextMenu menu = new ContextMenu();
        CheckMenuItem none = new CheckMenuItem(getString("workspace.indicator.none"));
        none.setSelected(currentId == null);
        none.setOnAction(event -> switchToNone());
        menu.getItems().add(none);
        List<Workspace> all = service.list();
        for (Workspace ws : usedLast(all)) {
            CheckMenuItem item = new CheckMenuItem(ws.name());
            item.setSelected(ws.id().equals(currentId));
            item.setOnAction(event -> {
                if (!ws.id().equals(currentId)) {
                    switchTo(ws);
                }
            });
            menu.getItems().add(item);
        }
        menu.getItems().add(new SeparatorMenuItem());
        if (currentId != null) {
            menu.getItems().add(item(format("workspace.menu.save", currentName), this::saveCurrent));
        }
        menu.getItems().add(item(getString("workspace.menu.save_as"), this::saveCurrentAs));
        menu.getItems().add(item(all.size() > MENU_LIMIT ? format("workspace.menu.all", all.size())
                : getString("workspace.menu.manage"), this::manageDialog));
        menu.getItems().add(new SeparatorMenuItem());
        CheckMenuItem saveOnSwitch = new CheckMenuItem(getString("workspace.menu.save_on_switch"));
        saveOnSwitch.setSelected(saveOnSwitch());
        saveOnSwitch.setOnAction(event -> {
            if (prefs != null) {
                prefs.putBoolean(SAVE_ON_SWITCH, saveOnSwitch.isSelected());
            }
        });
        menu.getItems().add(saveOnSwitch);
        return menu;
    }

    // ── Manage ───────────────────────────────────────────────────────────────

    /** The workspaces, to open (also with a double click), rename or delete one. */
    public void manageDialog() {
        ListView<Workspace> list = new ListView<>();
        TextField search = new TextField();
        search.setPromptText(getString("workspace.search"));
        Runnable refill = () -> {
            list.getItems().setAll(matching(service.list(), search.getText()));
            list.getSelectionModel().selectFirst();
        };
        list.getItems().setAll(service.list());
        list.setPlaceholder(new Label(getString("workspace.status.none")));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Workspace ws, boolean empty) {
                super.updateItem(ws, empty);
                if (empty || ws == null) {
                    setText(null);
                    return;
                }
                String text = ws.name() + "  ·  " + format("workspace.note_count", ws.openNoteIds().size());
                setText(ws.id().equals(currentId) ? text + "  ·  " + getString("workspace.in_use") : text);
            }
        });
        list.getItems().stream().filter(ws -> ws.id().equals(currentId)).findFirst()
                .ifPresentOrElse(list.getSelectionModel()::select, () -> list.getSelectionModel().selectFirst());

        Dialog<Void> dialog = new Dialog<>();
        Button openBtn = new Button(getString("workspace.open"));
        Button renameBtn = new Button(getString("workspace.rename"));
        Button deleteBtn = new Button(getString("workspace.delete"));
        Runnable openSelected = () -> {
            Workspace selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                dialog.close();
                switchTo(selected);
            }
        };
        openBtn.setOnAction(event -> openSelected.run());
        search.textProperty().addListener((obs, old, text) -> refill.run());
        search.setOnAction(event -> openSelected.run()); // Enter opens the first one found
        search.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                list.requestFocus();
                event.consume();
            }
        });
        list.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelected.run();
            }
        });
        renameBtn.setOnAction(event -> {
            Workspace selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                askRename(selected);
                refill.run();
            }
        });
        deleteBtn.setOnAction(event -> {
            Workspace selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                delete(selected);
                list.getItems().remove(selected);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            openBtn.setDisable(selected == null);
            renameBtn.setDisable(selected == null);
            deleteBtn.setDisable(selected == null);
        });
        boolean none = list.getSelectionModel().getSelectedItem() == null;
        openBtn.setDisable(none);
        renameBtn.setDisable(none);
        deleteBtn.setDisable(none);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox box = new VBox(8, search, list, new HBox(8, openBtn, renameBtn, spacer, deleteBtn));
        box.setPadding(new Insets(12));
        VBox.setVgrow(list, Priority.ALWAYS);
        style(dialog);
        dialog.setTitle(getString("workspace.manage.title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefSize(460, 460);
        dialog.setOnShown(event -> search.requestFocus());
        dialog.showAndWait();
    }

    private void askRename(Workspace ws) {
        TextInputDialog dialog = new TextInputDialog(ws.name());
        dialog.setTitle(getString("workspace.rename.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(getString("workspace.rename.content"));
        style(dialog);
        dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty()).ifPresent(name -> rename(ws, name));
    }

    /** Renames {@code ws}; {@code false} (and a status message) when another one has that name. */
    boolean rename(Workspace ws, String name) {
        var renamed = service.rename(ws.id(), name);
        if (renamed.isEmpty()) {
            updateStatus(format("workspace.status.name_taken", name.trim()));
            return false;
        }
        if (ws.id().equals(currentId)) {
            setCurrent(currentId, renamed.get().name());
        }
        updateStatus(format("workspace.status.renamed", renamed.get().name()));
        return true;
    }

    /** Deletes {@code ws}; when it was in use, none is in use afterwards (the tabs stay). */
    void delete(Workspace ws) {
        service.delete(ws.id());
        if (ws.id().equals(currentId)) {
            setCurrent(null, null);
        }
        updateStatus(format("workspace.status.deleted", ws.name()));
    }

    /** The workspaces whose name has {@code query}, ignoring case and accents; all for a blank one. */
    static List<Workspace> matching(List<Workspace> all, String query) {
        String wanted = plain(query);
        return wanted.isEmpty() ? all : all.stream().filter(ws -> plain(ws.name()).contains(wanted)).toList();
    }

    private static String plain(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text.strip(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /**
     * The {@value #MENU_LIMIT} workspaces used last, by name; while fewer have been used, the
     * others fill the list in name order.
     */
    private List<Workspace> usedLast(List<Workspace> all) {
        if (all.size() <= MENU_LIMIT) {
            return all;
        }
        List<String> recent = recentIds();
        List<Workspace> picked = new ArrayList<>();
        recent.forEach(id -> all.stream().filter(ws -> ws.id().equals(id)).findFirst().ifPresent(picked::add));
        all.stream().filter(ws -> !picked.contains(ws)).forEach(picked::add);
        List<Workspace> shown = new ArrayList<>(picked.subList(0, MENU_LIMIT));
        shown.sort(Comparator.comparing(ws -> all.indexOf(ws)));
        return shown;
    }

    private void markUsed(String id) {
        if (prefs == null || id == null) {
            return;
        }
        List<String> recent = new ArrayList<>(recentIds());
        recent.remove(id);
        recent.add(0, id);
        prefs.put(RECENT, String.join(",", recent.subList(0, Math.min(RECENT_KEPT, recent.size()))));
    }

    private List<String> recentIds() {
        String value = prefs != null ? prefs.get(RECENT, "") : "";
        return value.isBlank() ? List.of() : List.of(value.split(","));
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private void setCurrent(String id, String name) {
        currentId = id;
        currentName = name;
        showCurrent();
    }

    private void showCurrent() {
        if (indicator == null) {
            return;
        }
        indicator.setText(currentName != null ? currentName : getString("workspace.indicator.none"));
        indicator.setTooltip(new Tooltip(getString("workspace.indicator.tooltip")));
    }

    private boolean saveOnSwitch() {
        return prefs == null || prefs.getBoolean(SAVE_ON_SWITCH, true);
    }

    private Workspace captureLive() {
        Workspace live = host != null ? host.liveState().get() : null;
        return live != null ? live : WorkspaceService.liveState(List.of(), null, "", true, false, -1, -1, "");
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private void style(Dialog<?> dialog) {
        Scene scene = sceneSupplier.get();
        if (scene != null) {
            dialog.initOwner(scene.getWindow());
        }
        UiDialogs.apply(dialog);
    }

    private String getString(String key) {
        return i18n.apply(key);
    }

    private String format(String key, Object arg) {
        return MessageFormat.format(getString(key), arg);
    }

    private void updateStatus(String message) {
        status.accept(message);
    }
}
