package com.example.jylos.ui.components;

import java.text.MessageFormat;
import java.util.function.Consumer;
import java.util.function.Function;

import com.example.jylos.insights.BrokenLinkInfo;
import com.example.jylos.insights.KnowledgeHealthReport;
import com.example.jylos.insights.KnowledgeHealthReport.NoteRef;
import com.example.jylos.insights.KnowledgeHealthReport.TagUsage;
import com.example.jylos.insights.KnowledgeInsightsService;
import com.example.jylos.insights.NoteConnectivityInfo;
import com.example.jylos.ui.UiDialogs;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * "Knowledge Insights" dialog: a read-only, tabbed analytics view over the vault built
 * from a {@link KnowledgeHealthReport}. It shows headline totals and an explainable
 * graph-health score, then four actionable lists — most-connected notes, orphan notes,
 * broken links, untagged notes — plus tag usage.
 *
 * <p>Every note reference is clickable: double-clicking a row opens that note (for a
 * broken link, its source note) and closes the dialog. The report is computed off the
 * JavaFX thread; a spinner shows meanwhile. This is a pure view over the insights
 * service.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class KnowledgeInsightsPanel {

    private final KnowledgeInsightsService service;
    private final Function<String, String> i18n;
    private final Consumer<String> onOpenNote;
    private final Scene owner;

    private final Dialog<Void> dialog = new Dialog<>();
    private final StackPane content = new StackPane();

    public KnowledgeInsightsPanel(KnowledgeInsightsService service, Function<String, String> i18n,
            Consumer<String> onOpenNote, Scene owner) {
        this.service = service;
        this.i18n = i18n;
        this.onOpenNote = onOpenNote;
        this.owner = owner;
    }

    /** Builds, themes and shows the dialog, computing the report asynchronously. */
    public void show() {
        dialog.setTitle(str("insights.title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(680, 600);
        dialog.getDialogPane().setContent(content);
        if (owner != null) {
            dialog.initOwner(owner.getWindow());
        }
        UiDialogs.apply(dialog);

        ProgressIndicator spinner = new ProgressIndicator();
        Label loading = new Label(str("insights.computing"));
        VBox loadingBox = new VBox(12, spinner, loading);
        loadingBox.setAlignment(Pos.CENTER);
        content.getChildren().setAll(loadingBox);

        Task<KnowledgeHealthReport> task = new Task<>() {
            @Override
            protected KnowledgeHealthReport call() {
                return service.generateReport();
            }
        };
        task.setOnSucceeded(e -> content.getChildren().setAll(buildReportView(task.getValue())));
        task.setOnFailed(e -> content.getChildren().setAll(new Label(str("status.git_error"))));
        Thread thread = new Thread(task, "knowledge-insights");
        thread.setDaemon(true);
        thread.start();

        dialog.showAndWait();
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private TabPane buildReportView(KnowledgeHealthReport r) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("insights.tab_summary", summaryTab(r)),
                tab("insights.tab_connected", connectedTab(r)),
                tab("insights.tab_orphans", noteListTab(r.orphans(), "insights.none_orphans")),
                tab("insights.tab_broken", brokenTab(r)),
                tab("insights.tab_untagged", noteListTab(r.notesWithoutTags(), "insights.none_untagged")),
                tab("insights.tab_tags", tagsTab(r)));
        return tabs;
    }

    private VBox summaryTab(KnowledgeHealthReport r) {
        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(10);
        addMetric(grid, 0, "insights.total_notes", String.valueOf(r.totalNotes()));
        addMetric(grid, 1, "insights.total_links", String.valueOf(r.totalLinks()));
        addMetric(grid, 2, "insights.total_backlinks", String.valueOf(r.totalBacklinks()));
        addMetric(grid, 3, "insights.total_tags", String.valueOf(r.totalTags()));
        addMetric(grid, 4, "insights.avg_links", String.format("%.2f", r.avgLinksPerNote()));

        Label scoreValue = new Label(r.healthScore() + " / 100");
        scoreValue.getStyleClass().add("insights-score");
        scoreValue.getStyleClass().add(scoreClass(r.healthScore()));
        Label scoreTitle = new Label(str("insights.health"));
        scoreTitle.getStyleClass().add("insights-metric-label");
        VBox scoreBox = new VBox(2, scoreTitle, scoreValue);

        Label explanation = new Label(MessageFormat.format(str("insights.health_explanation"),
                r.orphanPenalty(), r.untaggedPenalty(), r.brokenPenalty()));
        explanation.setWrapText(true);
        explanation.getStyleClass().add("insights-explanation");

        VBox box = new VBox(20, grid, scoreBox, explanation);
        box.setPadding(new Insets(18));
        return box;
    }

    private VBox connectedTab(KnowledgeHealthReport r) {
        TableView<NoteConnectivityInfo> table = new TableView<>();
        table.getColumns().add(column("insights.col_note", NoteConnectivityInfo::title, 320));
        table.getColumns().add(column("insights.col_inbound", NoteConnectivityInfo::inbound, 70));
        table.getColumns().add(column("insights.col_outbound", NoteConnectivityInfo::outbound, 70));
        TableColumn<NoteConnectivityInfo, Integer> totalCol = new TableColumn<>(str("insights.col_total"));
        totalCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(
                c.getValue().total()).asObject());
        totalCol.setPrefWidth(70);
        table.getColumns().add(totalCol);
        table.getItems().setAll(r.mostConnected());
        table.setPlaceholder(new Label(str("insights.none_connected")));
        onRowOpen(table, NoteConnectivityInfo::noteId);
        return wrapTable(table);
    }

    private VBox brokenTab(KnowledgeHealthReport r) {
        TableView<BrokenLinkInfo> table = new TableView<>();
        table.getColumns().add(column("insights.col_source", BrokenLinkInfo::sourceTitle, 320));
        table.getColumns().add(column("insights.col_target", BrokenLinkInfo::targetTitle, 300));
        table.getItems().setAll(r.brokenLinks());
        table.setPlaceholder(new Label(str("insights.none_broken")));
        onRowOpen(table, BrokenLinkInfo::sourceNoteId);
        return wrapTable(table);
    }

    private VBox tagsTab(KnowledgeHealthReport r) {
        TableView<TagUsage> table = new TableView<>();
        table.getColumns().add(column("insights.col_tag", TagUsage::tag, 420));
        table.getColumns().add(column("insights.col_count", TagUsage::count, 100));
        table.getItems().setAll(r.tagUsage());
        table.setPlaceholder(new Label(str("insights.none_tags")));
        return wrapTable(table);
    }

    private VBox noteListTab(java.util.List<NoteRef> refs, String emptyKey) {
        ListView<NoteRef> list = new ListView<>();
        list.getItems().setAll(refs);
        list.setPlaceholder(new Label(str(emptyKey)));
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(NoteRef ref, boolean empty) {
                super.updateItem(ref, empty);
                setText(empty || ref == null ? null : ref.title());
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                NoteRef sel = list.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    open(sel.noteId());
                }
            }
        });
        VBox box = new VBox(8, hint(), list);
        VBox.setVgrow(list, Priority.ALWAYS);
        box.setPadding(new Insets(10));
        return box;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Tab tab(String key, javafx.scene.Node body) {
        Tab t = new Tab(str(key), body);
        return t;
    }

    private <T, V> TableColumn<T, V> column(String headerKey, Function<T, V> valueOf, double width) {
        TableColumn<T, V> col = new TableColumn<>(str(headerKey));
        col.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(valueOf.apply(cell.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    /** Wires double-click on a table row to open the note returned by {@code idOf}. */
    private <T> void onRowOpen(TableView<T> table, Function<T, String> idOf) {
        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<T> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    open(idOf.apply(row.getItem()));
                }
            });
            return row;
        });
    }

    private VBox wrapTable(TableView<?> table) {
        VBox box = new VBox(8, hint(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setPadding(new Insets(10));
        return box;
    }

    private Label hint() {
        Label hint = new Label(str("insights.open_hint"));
        hint.getStyleClass().add("insights-hint");
        return hint;
    }

    private void addMetric(GridPane grid, int col, String labelKey, String value) {
        Label name = new Label(str(labelKey));
        name.getStyleClass().add("insights-metric-label");
        Label val = new Label(value);
        val.getStyleClass().add("insights-metric-value");
        VBox cell = new VBox(2, name, val);
        grid.add(cell, col, 0);
    }

    private void open(String noteId) {
        if (noteId != null && onOpenNote != null) {
            onOpenNote.accept(noteId);
            dialog.setResult(null);
            dialog.close();
        }
    }

    private static String scoreClass(int score) {
        if (score >= 75) {
            return "insights-score-good";
        }
        return score >= 45 ? "insights-score-warn" : "insights-score-bad";
    }

    private String str(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }
}
