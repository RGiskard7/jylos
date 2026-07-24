package com.example.jylos.ui.controller;

import com.example.jylos.data.models.Folder;
import com.example.jylos.ui.UiDialogs;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shared folder picker for move actions.
 * Keeps the move dialog searchable and uses path labels to disambiguate duplicate folder names.
 */
final class MoveTargetSupport {

    private MoveTargetSupport() {
    }

    static Optional<MoveTarget> show(
            List<Folder> folders,
            Predicate<Folder> folderFilter,
            Function<Folder, Optional<Folder>> parentResolver,
            Function<String, String> i18n) {
        List<MoveTarget> allTargets = buildTargets(folders, folderFilter, parentResolver, i18n);
        if (allTargets.isEmpty()) {
            return Optional.empty();
        }

        Dialog<MoveTarget> dialog = new Dialog<>();
        dialog.setTitle(i18n.apply("dialog.move_to.title"));
        dialog.setHeaderText(i18n.apply("dialog.move_to.header"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField searchField = new TextField();
        searchField.setPromptText(i18n.apply("dialog.move_to.search_prompt"));

        Label destinationLabel = new Label(i18n.apply("dialog.move_to.content"));
        ListView<MoveTarget> targetsListView = new ListView<>();
        ObservableList<MoveTarget> visibleTargets = FXCollections.observableArrayList(allTargets);
        targetsListView.setItems(visibleTargets);
        targetsListView.setPrefHeight(280);
        targetsListView.setMinHeight(160);
        VBox.setVgrow(targetsListView, Priority.ALWAYS);
        targetsListView.getSelectionModel().selectFirst();

        VBox content = new VBox(8, destinationLabel, searchField, targetsListView);
        content.setPrefWidth(440);
        dialog.getDialogPane().setContent(content);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(targetsListView.getSelectionModel().selectedItemProperty().isNull());

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            visibleTargets.setAll(filterTargets(allTargets, newValue));
            targetsListView.getSelectionModel().selectFirst();
        });
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !okButton.isDisabled()) {
                okButton.fire();
                event.consume();
            }
        });
        targetsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !okButton.isDisabled()) {
                okButton.fire();
            }
        });
        targetsListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !okButton.isDisabled()) {
                okButton.fire();
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == ButtonType.OK
                ? targetsListView.getSelectionModel().getSelectedItem()
                : null);
        return UiDialogs.show(dialog);
    }

    static List<MoveTarget> buildTargets(
            List<Folder> folders,
            Predicate<Folder> folderFilter,
            Function<Folder, Optional<Folder>> parentResolver,
            Function<String, String> i18n) {
        List<MoveTarget> targets = new ArrayList<>();
        targets.add(new MoveTarget(null, i18n.apply("folder.root")));
        if (folders == null) {
            return targets;
        }
        folders.stream()
                .filter(folder -> folder != null && folder.getId() != null)
                .filter(folderFilter != null ? folderFilter : folder -> true)
                .map(folder -> new MoveTarget(folder, buildFolderLabel(folder, parentResolver)))
                .sorted(Comparator.comparing(MoveTarget::label, String.CASE_INSENSITIVE_ORDER))
                .forEach(targets::add);
        return targets;
    }

    static List<MoveTarget> filterTargets(List<MoveTarget> targets, String query) {
        String normalizedQuery = normalizeSearchText(query);
        if (normalizedQuery.isBlank()) {
            return targets;
        }
        return targets.stream()
                .filter(target -> normalizeSearchText(target.label()).contains(normalizedQuery))
                .toList();
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String buildFolderLabel(Folder folder, Function<Folder, Optional<Folder>> parentResolver) {
        ArrayDeque<String> parts = new ArrayDeque<>();
        Folder cursor = folder;
        int safety = 0;
        while (cursor != null && cursor.getId() != null && safety++ < 128) {
            if (!"ROOT".equals(cursor.getId()) && !"ALL_NOTES_VIRTUAL".equals(cursor.getId())) {
                parts.addFirst(cursor.getTitle());
            }
            Optional<Folder> parent = parentResolver != null ? parentResolver.apply(cursor) : Optional.empty();
            cursor = parent.orElse(null);
        }
        if (!parts.isEmpty()) {
            return String.join("/", parts);
        }
        String id = folder.getId();
        return id != null && id.contains("/") ? id : folder.getTitle();
    }

    record MoveTarget(Folder folder, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
