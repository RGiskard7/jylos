package com.example.jylos.plugin.builtin.publish;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PluginI18n;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The "Publish Vault as Static Site..." configuration dialog — site title,
 * whether to generate the graph, and which folders/notes to include, modeled
 * on Obsidian Publish's own "Publish changes..." picker. Only offered before a
 * full {@link VaultExporter#export}; {@link VaultExporter#exportSingleNote}
 * intentionally has no dialog of its own — see that method's doc for why it
 * reads these same choices back from the site it is updating instead.
 *
 * <p>The tree/list only ever show notes {@link VaultExporter} would consider
 * exportable in the first place (not private, not an attachment) — nothing
 * to gain from letting someone "select" a note that is excluded either way,
 * only confusion about why checking it did nothing.</p>
 *
 * <p>Two independent browsing modes, picked with the "Folder tree" / "All
 * notes" toggle buttons: a {@link TreeView} where whole folders can be
 * checked at once (folders start collapsed — a vault the size that motivated
 * this dialog can have thousands of notes, showing all of them at once by
 * default would be an unreadable wall; "Expand all"/"Collapse all" work the
 * tree regardless of which mode is currently shown), and a flat {@link
 * ListView} of every note with no folder grouping at all — the dialog's
 * original, simpler behavior, kept as an explicit option rather than
 * replaced. A non-blank search always wins over either: it switches to that
 * same flat list, live-filtered to matching notes, and reverts to whichever
 * mode was selected once the search is cleared. All three views share the
 * exact same {@link CheckBoxTreeItem#selectedProperty()} instances — a note
 * checked in one is still checked when another view shows it, nothing is
 * copied or rebuilt.</p>
 *
 * @author Edu Díaz (RGiskard7)
 */
final class PublishConfigDialog {

    private PublishConfigDialog() {
    }

    // Shares its bundle (and the "messages*.properties" resource files, packaged by
    // scripts/build-plugins.sh) with PublishPlugin — see that class's own bundle field
    // for why Locale.getDefault() alone, no PluginContext, is enough.
    private static java.util.ResourceBundle bundle;

    private static String tr(String key, String fallback) {
        if (bundle == null) {
            bundle = PluginI18n.bundle(PublishConfigDialog.class);
        }
        return PluginI18n.tr(bundle, key, fallback);
    }

    /**
     * Shows the dialog and blocks (JavaFX-modally) until the user publishes or
     * cancels. Must be called on the FX thread. {@code defaultSiteTitle} pre-
     * fills the title field — the last one used, if this vault has been
     * published before ({@code PublishPlugin} reads that back from the target
     * directory's manifest before showing this dialog).
     *
     * @param context used for {@link com.example.jylos.plugin.PluginContext#applyTheme},
     *                {@link com.example.jylos.plugin.PluginContext#getNoteService()} and
     *                {@link com.example.jylos.plugin.PluginContext#getFolderService()} — this
     *                dialog builds its own {@link Dialog}, so it needs the theming call every
     *                other plugin dialog gets for free through {@code showInfo}/{@code showError}
     */
    static Optional<VaultExporter.PublishOptions> show(com.example.jylos.plugin.PluginContext context,
            String defaultSiteTitle, boolean defaultGenerateGraph) {
        Dialog<VaultExporter.PublishOptions> dialog = new Dialog<>();
        dialog.setTitle(tr("dialog.title", "Publish Vault as Static Site"));
        dialog.setHeaderText(tr("dialog.header", "Choose what to publish"));
        context.applyTheme(dialog.getDialogPane());

        Built built = buildContent(context.getNoteService(), context.getFolderService(), defaultSiteTitle,
                defaultGenerateGraph);

        dialog.getDialogPane().setContent(built.content());
        dialog.getDialogPane().setPrefSize(660, 620);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,
                new ButtonType(tr("button.publish", "Publish"), ButtonBar.ButtonData.OK_DONE));
        dialog.setResizable(true);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == null || buttonType.getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                return null;
            }
            Set<String> selectedIds = new LinkedHashSet<>();
            int totalNotes = collectSelectedNoteIds(built.root(), selectedIds);
            // Everything selected (the common case, and the default) is expressed as
            // "no filter" (null) — cheaper and avoids VaultExporter treating a full
            // vault publish as if it were a deliberate partial selection.
            Set<String> includedNoteIds = selectedIds.size() == totalNotes ? null : selectedIds;
            String siteTitle = built.titleField().getText();
            return new VaultExporter.PublishOptions(siteTitle == null || siteTitle.isBlank() ? null : siteTitle.trim(),
                    built.generateGraphCheck().isSelected(), includedNoteIds);
        });

        return dialog.showAndWait();
    }

    /** Everything {@link #show} needs, minus the modal {@link Dialog} shell itself — split out
     *  so a test can build and inspect the real widgets (tree structure, toggle wiring, cascade
     *  selection) without blocking on {@code showAndWait()}, which only a live user click ends. */
    record Built(VBox content, CheckBoxTreeItem<Object> root, TreeView<Object> tree,
            ListView<CheckBoxTreeItem<Object>> flatView, ToggleButton treeModeButton, ToggleButton flatModeButton,
            TextField searchField, TextField titleField, CheckBox generateGraphCheck) {
    }

    static Built buildContent(NoteService noteService, FolderService folderService, String defaultSiteTitle,
            boolean defaultGenerateGraph) {
        TextField titleField = new TextField();
        titleField.setPromptText(tr("field.siteTitle.prompt", "Jylos Vault"));
        if (defaultSiteTitle != null && !defaultSiteTitle.isBlank() && !"Jylos Vault".equals(defaultSiteTitle)) {
            titleField.setText(defaultSiteTitle);
        }

        CheckBox generateGraphCheck = new CheckBox(
                tr("check.generateGraph", "Generate the graph view (full page + each note's mini-graph)"));
        generateGraphCheck.setSelected(defaultGenerateGraph);

        CheckBoxTreeItem<Object> root = new CheckBoxTreeItem<>("Vault");
        root.setExpanded(true);
        root.setSelected(true);
        List<CheckBoxTreeItem<Object>> allNoteItems = new ArrayList<>();
        // Build the folder subtree FIRST, recording every note id it places somewhere —
        // getAllNotes() below is every note in the ENTIRE vault, not just the ones with no
        // folder, so without this set every folder-contained note would get added a SECOND
        // time, flat, directly under root: the folder tree would technically still be there,
        // just buried under a duplicate of almost the whole vault sitting in front of it —
        // which is indistinguishable from the flat "All notes" view at a glance, exactly the
        // "looks exactly like All notes" bug this fixes.
        Set<String> placedInFolder = new java.util.HashSet<>();
        List<CheckBoxTreeItem<Object>> folderItems =
                buildFolderItems(folderService.getRootFolders(), noteService, folderService, allNoteItems, placedInFolder);

        // Folders first, then the loose notes — and each group by name, case-insensitively,
        // the same ordering Jylos's own sidebar tree uses (SidebarController sorts folders
        // with compareToIgnoreCase on the title). getAllNotes()/getRootFolders() come back
        // in whatever order the backend walked them, which is not alphabetical.
        root.getChildren().addAll(folderItems);
        List<Note> looseNotes = new ArrayList<>();
        for (Note note : noteService.getAllNotes()) {
            if (isSelectable(note) && !placedInFolder.contains(note.getId())) {
                looseNotes.add(note);
            }
        }
        looseNotes.sort(BY_TITLE);
        for (Note note : looseNotes) {
            addNoteLeaf(root, note, allNoteItems);
        }

        TreeView<Object> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setCellFactory(CheckBoxTreeCell.forTreeView(
                item -> ((CheckBoxTreeItem<Object>) item).selectedProperty(),
                new StringConverter<TreeItem<Object>>() {
                    @Override
                    public String toString(TreeItem<Object> item) {
                        // MUST tolerate null: TreeView's VirtualFlow creates cells for the
                        // empty rows below the last item and calls updateItem(null) on them.
                        // Dereferencing that null throws inside layoutChildren, on the FX
                        // thread, every pulse — which aborts the flow mid-population and
                        // leaves the tree rendering blank no matter how correct the
                        // underlying TreeItem structure is.
                        return item == null ? "" : labelFor(item.getValue());
                    }

                    @Override
                    public TreeItem<Object> fromString(String string) {
                        throw new UnsupportedOperationException();
                    }
                }));

        // Flat list sharing the SAME CheckBoxTreeItem instances the tree uses (via their
        // selectedProperty binding below) — not a rebuild, so a note checked here is
        // still checked once the tree reappears. Reparenting the real tree items into a
        // second container instead would silently detach them from their folder (a
        // TreeItem has exactly one parent), corrupting the tree the moment this view
        // goes away — this list only ever *references* them. Does double duty as both
        // view modes that need "every note, no folder grouping": the live search-filtered
        // list, and the "All notes" mode button below (unfiltered — just every item).
        ListView<CheckBoxTreeItem<Object>> flatView = new ListView<>();
        flatView.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private CheckBoxTreeItem<Object> bound;

            @Override
            protected void updateItem(CheckBoxTreeItem<Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (bound != null) {
                    checkBox.selectedProperty().unbindBidirectional(bound.selectedProperty());
                    bound = null;
                }
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setText(labelFor(item.getValue()));
                checkBox.selectedProperty().bindBidirectional(item.selectedProperty());
                bound = item;
                setGraphic(checkBox);
            }
        });

        // The TreeView goes in directly, NOT wrapped in a ScrollPane: a TreeView already
        // scrolls itself (its VirtualFlow owns the scrollbars), and the wrapper also broke
        // the show/hide below — the toggle flips the TreeView's own visible/managed, but it
        // was the wrapper that sat in this StackPane, so the wrapper (and the tree inside
        // it) stayed laid out and on screen in both modes.
        StackPane treeArea = new StackPane(tree, flatView);

        TextField searchField = new TextField();
        searchField.setPromptText(tr("field.search.prompt", "Search notes…"));

        // "Folder tree" / "All notes" — two independent, mutually-exclusive ways to browse
        // the same underlying selection, matching what was asked for: a tree view where
        // whole folders can be checked at once, and the plain flat "every note" list the
        // dialog always showed before. A non-blank search always wins over either (see
        // refreshView below) — searching needs a flat, filterable list regardless of which
        // mode was active, and reverts to that mode once the search is cleared.
        ToggleGroup viewModeGroup = new ToggleGroup();
        ToggleButton treeModeButton = new ToggleButton(tr("toggle.tree", "Folder tree"));
        treeModeButton.setToggleGroup(viewModeGroup);
        ToggleButton flatModeButton = new ToggleButton(tr("toggle.flat", "All notes"));
        flatModeButton.setToggleGroup(viewModeGroup);
        flatModeButton.setSelected(true); // matches the dialog's previous, flat-only behavior
        HBox viewModeButtons = new HBox(4, treeModeButton, flatModeButton);

        Runnable refreshView = () -> {
            String needle = searchField.getText();
            boolean searching = needle != null && !needle.isBlank();
            if (searching) {
                String lowerNeedle = needle.trim().toLowerCase();
                List<CheckBoxTreeItem<Object>> matches = new ArrayList<>();
                for (CheckBoxTreeItem<Object> item : allNoteItems) {
                    if (labelFor(item.getValue()).toLowerCase().contains(lowerNeedle)) {
                        matches.add(item);
                    }
                }
                flatView.setItems(javafx.collections.FXCollections.observableArrayList(matches));
            } else if (flatModeButton.isSelected()) {
                flatView.setItems(javafx.collections.FXCollections.observableArrayList(allNoteItems));
            }
            boolean showTree = !searching && treeModeButton.isSelected();
            tree.setVisible(showTree);
            tree.setManaged(showTree);
            flatView.setVisible(!showTree);
            flatView.setManaged(!showTree);
        };
        searchField.textProperty().addListener((obs, oldText, newText) -> refreshView.run());
        viewModeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> refreshView.run());
        refreshView.run();

        Button expandAllButton = new Button(tr("button.expandAll", "Expand all"));
        expandAllButton.setOnAction(e -> setAllExpanded(root, true));
        Button collapseAllButton = new Button(tr("button.collapseAll", "Collapse all"));
        collapseAllButton.setOnAction(e -> setAllExpanded(root, false));
        Button selectAllButton = new Button(tr("button.selectAll", "Select all"));
        selectAllButton.setOnAction(e -> setAllSelected(root, true));
        Button deselectAllButton = new Button(tr("button.deselectAll", "Deselect all"));
        deselectAllButton.setOnAction(e -> setAllSelected(root, false));
        HBox treeButtons = new HBox(8, expandAllButton, collapseAllButton, selectAllButton, deselectAllButton);

        // Six buttons crammed into one row do not fit the dialog's width — JavaFX shrinks
        // each Button to its minimum and every label ends up ellipsised ("Expandir ...",
        // "Seleccionar ..."), which is unreadable. Two rows, and nothing may shrink below
        // its text: the mode toggles on their own line (they choose what the box below
        // shows), the four tree actions under them.
        for (javafx.scene.control.ButtonBase b : List.of(treeModeButton, flatModeButton,
                expandAllButton, collapseAllButton, selectAllButton, deselectAllButton)) {
            b.setMinWidth(Region.USE_PREF_SIZE); // never ellipsise the label
        }
        viewModeButtons.setAlignment(Pos.CENTER_LEFT);
        treeButtons.setAlignment(Pos.CENTER_LEFT);

        Label includeHeading = new Label(tr("label.includeHeading", "Folders and notes to include:"));
        VBox content = new VBox(10,
                labeledField(tr("field.siteTitle.label", "Site title"), titleField),
                generateGraphCheck,
                includeHeading,
                searchField,
                viewModeButtons,
                treeButtons,
                treeArea);
        // Even padding on all four sides — 4px left/right/bottom left the fields and the
        // tree box flush against the dialog edge, which is what "no margins" looked like.
        content.setPadding(new Insets(16));
        content.setPrefWidth(620);
        VBox.setVgrow(treeArea, Priority.ALWAYS);
        VBox.setMargin(includeHeading, new Insets(6, 0, 0, 0));

        return new Built(content, root, tree, flatView, treeModeButton, flatModeButton, searchField, titleField,
                generateGraphCheck);
    }

    private static boolean isSelectable(Note note) {
        return note != null && !note.isPrivate()
                && !com.example.jylos.util.AttachmentType.isAttachment(note.getTitle());
    }

    private static String labelFor(Object value) {
        if (value instanceof Note note) {
            return note.getTitle();
        }
        if (value instanceof Folder folder) {
            // A folder-shaped icon prefix, not just a checkbox+text row identical in
            // shape to a note's — collapsed by default, a folder row has to read as
            // "a container, click to open" at a glance, not blend into the note list
            // above/below it.
            return "📁 " + (folder.getTitle() != null ? folder.getTitle() : folder.getId());
        }
        return String.valueOf(value);
    }

    private static HBox labeledField(String label, TextField field) {
        Label l = new Label(label);
        l.setMinWidth(80);
        HBox.setHgrow(field, Priority.ALWAYS);
        return new HBox(8, l, field);
    }

    /** Mirrors {@link VaultExporter}'s own folder walk ({@code walkFolders}), but builds JavaFX tree items
     *  instead of a path map. {@code allNoteItems} accumulates every note leaf created, flattened — the
     *  search view's data source. {@code placedInFolder} accumulates the id of every note placed inside
     *  SOME folder here — the caller needs that to know which of {@link NoteService#getAllNotes()} are
     *  genuinely root-level (nobody claimed them), instead of naively treating every note in the vault as
     *  root-level and then placing folder-contained ones a second time, nested. */
    private static List<CheckBoxTreeItem<Object>> buildFolderItems(List<Folder> folders, NoteService noteService,
            FolderService folderService, List<CheckBoxTreeItem<Object>> allNoteItems, Set<String> placedInFolder) {
        List<Folder> sortedFolders = new ArrayList<>(folders);
        sortedFolders.sort(BY_TITLE);

        List<CheckBoxTreeItem<Object>> items = new ArrayList<>();
        for (Folder folder : sortedFolders) {
            CheckBoxTreeItem<Object> folderItem = new CheckBoxTreeItem<>(folder);
            folderItem.setSelected(true);
            // Collapsed by default — a vault with thousands of notes showing every one of
            // them at once on open would be unreadable; "Expand all" opens the lot.
            folderItem.setExpanded(false);

            // Subfolders first, then this folder's own notes, each group sorted by name —
            // same shape and ordering as Jylos's own sidebar tree.
            folderItem.getChildren().addAll(
                    buildFolderItems(folderService.getSubfolders(folder), noteService, folderService, allNoteItems,
                            placedInFolder));

            List<Note> notesHere = new ArrayList<>();
            for (Note note : noteService.getNotesByFolder(folder)) {
                placedInFolder.add(note.getId()); // even if not isSelectable — an excluded note still
                                                   // "belongs" here, it just never gets a tree item at all
                if (isSelectable(note)) {
                    notesHere.add(note);
                }
            }
            notesHere.sort(BY_TITLE);
            for (Note note : notesHere) {
                addNoteLeaf(folderItem, note, allNoteItems);
            }
            items.add(folderItem);
        }
        return items;
    }

    /** Case-insensitive by display name, matching SidebarController's own folder comparator so the
     *  publish tree reads in the same order as the sidebar tree the user already knows. */
    private static final java.util.Comparator<Object> BY_TITLE =
            java.util.Comparator.comparing(PublishConfigDialog::sortKey, String.CASE_INSENSITIVE_ORDER);

    private static String sortKey(Object value) {
        if (value instanceof Note note) {
            return note.getTitle() != null ? note.getTitle() : "";
        }
        if (value instanceof Folder folder) {
            return folder.getTitle() != null ? folder.getTitle() : folder.getId();
        }
        return String.valueOf(value);
    }

    private static void addNoteLeaf(CheckBoxTreeItem<Object> parent, Note note, List<CheckBoxTreeItem<Object>> allNoteItems) {
        CheckBoxTreeItem<Object> item = new CheckBoxTreeItem<>(note);
        item.setSelected(true);
        parent.getChildren().add(item);
        allNoteItems.add(item);
    }

    private static void setAllSelected(CheckBoxTreeItem<Object> item, boolean selected) {
        item.setSelected(selected);
        for (TreeItem<Object> child : item.getChildren()) {
            if (child instanceof CheckBoxTreeItem<Object> checkChild) {
                setAllSelected(checkChild, selected);
            }
        }
    }

    private static void setAllExpanded(CheckBoxTreeItem<Object> item, boolean expanded) {
        if (!item.getChildren().isEmpty()) {
            item.setExpanded(expanded);
        }
        for (TreeItem<Object> child : item.getChildren()) {
            if (child instanceof CheckBoxTreeItem<Object> checkChild) {
                setAllExpanded(checkChild, expanded);
            }
        }
    }

    /** Returns the total note count in the tree (selected or not), while collecting the selected ones' ids. */
    private static int collectSelectedNoteIds(CheckBoxTreeItem<Object> item, Set<String> selectedIds) {
        int total = 0;
        for (TreeItem<Object> child : item.getChildren()) {
            if (!(child instanceof CheckBoxTreeItem<Object> checkChild)) {
                continue;
            }
            if (checkChild.getValue() instanceof Note note) {
                total++;
                if (checkChild.isSelected()) {
                    selectedIds.add(note.getId());
                }
            } else {
                total += collectSelectedNoteIds(checkChild, selectedIds);
            }
        }
        return total;
    }
}
