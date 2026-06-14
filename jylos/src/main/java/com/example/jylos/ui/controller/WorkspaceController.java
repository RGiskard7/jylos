package com.example.jylos.ui.controller;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.ui.UiDialogs;
import com.example.jylos.workspace.Workspace;
import com.example.jylos.workspace.WorkspaceService;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Owns the Workspaces UI and actions (save / save as / open / manage / delete), keeping
 * that logic out of {@code MainController}. Persistence and CRUD live in
 * {@link WorkspaceService}; capturing and restoring the live UI state is delegated to the
 * host through the {@code liveState} supplier and {@code applier} consumer wired in via
 * {@link #wire}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class WorkspaceController {

    private final WorkspaceService service = new WorkspaceService();

    private Supplier<Workspace> liveStateSupplier;
    private Consumer<Workspace> applier;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Scene> sceneSupplier;

    /** The workspace last opened or saved, used as the target of "Save Current". */
    private String currentWorkspaceId;
    private String currentWorkspaceName;

    /**
     * @param liveStateSupplier captures the current UI as a "live state" workspace
     *                          (state fields filled; id/name/timestamps blank)
     * @param applier           restores a chosen workspace's notes + layout (host-side)
     */
    public void wire(Supplier<Workspace> liveStateSupplier, Consumer<Workspace> applier,
            Function<String, String> i18n, Consumer<String> status, Supplier<Scene> sceneSupplier) {
        this.liveStateSupplier = liveStateSupplier;
        this.applier = applier;
        this.i18n = i18n;
        this.status = status;
        this.sceneSupplier = sceneSupplier;
    }

    // ── Actions (called from MainController's menu handlers) ──────────────────────

    /** Saves into the current workspace, or falls back to "Save As" when there is none. */
    public void saveCurrent() {
        if (currentWorkspaceId != null && service.findById(currentWorkspaceId).isPresent()) {
            service.update(currentWorkspaceId, captureLive());
            updateStatus(format("workspace.status.saved", currentWorkspaceName));
        } else {
            saveCurrentAs();
        }
    }

    /** Prompts for a name and saves the current state as a (new or overwritten) workspace. */
    public void saveCurrentAs() {
        if (liveStateSupplier == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(currentWorkspaceName != null ? currentWorkspaceName : "");
        dialog.setTitle(getString("workspace.save_as.title"));
        dialog.setHeaderText(getString("workspace.save_as.header"));
        dialog.setContentText(getString("workspace.save_as.content"));
        style(dialog);
        dialog.showAndWait()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .ifPresent(name -> {
                    Workspace saved = service.save(name, captureLive());
                    currentWorkspaceId = saved.id();
                    currentWorkspaceName = saved.name();
                    updateStatus(format("workspace.status.saved", saved.name()));
                });
    }

    /** Lists workspaces and opens the one the user picks. */
    public void openWorkspaceDialog() {
        List<Workspace> all = service.list();
        if (all.isEmpty()) {
            updateStatus(getString("workspace.status.none"));
            return;
        }
        ListView<Workspace> list = workspaceList(all);
        Dialog<ButtonType> dialog = new Dialog<>();
        style(dialog);
        dialog.setTitle(getString("workspace.open.title"));
        ButtonType openType = new ButtonType(getString("workspace.open"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(openType, ButtonType.CANCEL);
        VBox box = new VBox(8, new Label(getString("workspace.open.header")), list);
        box.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefSize(420, 420);

        dialog.showAndWait().ifPresent(choice -> {
            if (choice == openType) {
                Workspace selected = list.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    open(selected);
                }
            }
        });
    }

    /** Manage dialog: open or delete saved workspaces. */
    public void manageDialog() {
        List<Workspace> all = service.list();
        ListView<Workspace> list = workspaceList(all);
        list.setPlaceholder(new Label(getString("workspace.status.none")));

        Button openBtn = new Button(getString("workspace.open"));
        Button deleteBtn = new Button(getString("workspace.delete"));
        openBtn.setDisable(true);
        deleteBtn.setDisable(true);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            openBtn.setDisable(b == null);
            deleteBtn.setDisable(b == null);
        });
        openBtn.setOnAction(e -> {
            Workspace sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                open(sel);
                openBtn.getScene().getWindow().hide();
            }
        });
        deleteBtn.setOnAction(e -> {
            Workspace sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                service.delete(sel.id());
                if (sel.id().equals(currentWorkspaceId)) {
                    currentWorkspaceId = null;
                    currentWorkspaceName = null;
                }
                list.getItems().remove(sel);
                updateStatus(format("workspace.status.deleted", sel.name()));
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, openBtn, spacer, deleteBtn);
        VBox box = new VBox(8, list, actions);
        box.setPadding(new Insets(12));
        VBox.setVgrow(list, Priority.ALWAYS);

        Dialog<Void> dialog = new Dialog<>();
        style(dialog);
        dialog.setTitle(getString("workspace.manage.title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefSize(460, 460);
        dialog.showAndWait();
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private void open(Workspace ws) {
        if (applier != null && ws != null) {
            applier.accept(ws);
            currentWorkspaceId = ws.id();
            currentWorkspaceName = ws.name();
        }
    }

    private Workspace captureLive() {
        Workspace live = liveStateSupplier != null ? liveStateSupplier.get() : null;
        return live != null ? live : WorkspaceService.liveState(List.of(), null, "", true, false, -1, -1, "");
    }

    private ListView<Workspace> workspaceList(List<Workspace> items) {
        ListView<Workspace> list = new ListView<>();
        list.getItems().setAll(items);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Workspace ws, boolean empty) {
                super.updateItem(ws, empty);
                if (empty || ws == null) {
                    setText(null);
                    return;
                }
                int count = ws.openNoteIds().size();
                setText(ws.name() + "  ·  " + format("workspace.note_count", count));
            }
        });
        if (!items.isEmpty()) {
            list.getSelectionModel().select(0);
        }
        return list;
    }

    private void style(Dialog<?> dialog) {
        Scene scene = sceneSupplier != null ? sceneSupplier.get() : null;
        if (scene != null) {
            dialog.initOwner(scene.getWindow());
        }
        UiDialogs.apply(dialog);
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private String format(String key, Object arg) {
        return java.text.MessageFormat.format(getString(key), arg);
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }
}
