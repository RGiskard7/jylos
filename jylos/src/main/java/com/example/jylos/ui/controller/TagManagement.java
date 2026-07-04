package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.TagService;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Tag dialogs (manager, add/remove tag on the current note).
 */
class TagManagement {

    private static final Logger logger = LoggerConfig.getLogger(TagManagement.class);

    private Function<String, String> i18nFn;
    private Consumer<String> updateStatus;
    private TagService tagService;

    void wire(Function<String, String> i18n, Consumer<String> updateStatus, TagService tagService) {
        this.i18nFn = i18n;
        this.updateStatus = updateStatus;
        this.tagService = tagService;
    }

    /** Resolves an i18n key via the supplied callback. */
    private String i18n(String key) {
        return i18nFn.apply(key);
    }

    void handleAddTagToNote(Note currentNote, Runnable refreshSidebarTags, Consumer<Note> reloadCurrentNoteTags) {
        if (currentNote == null) {
            updateStatus.accept(i18n("status.no_note"));
            return;
        }

        if (tagService == null) {
            updateStatus.accept(i18n("status.error"));
            return;
        }
        try {
            List<String> availableTagNames = tagService.getAvailableTagsForNote(currentNote).stream()
                    .map(Tag::getTitle)
                    .sorted()
                    .toList();

            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle(i18n("dialog.add_tag.title"));
            dialog.setHeaderText(availableTagNames.isEmpty()
                    ? i18n("dialog.add_tag.header_new")
                    : i18n("dialog.add_tag.header_select"));

            ButtonType addButtonType = new ButtonType(i18n("action.add"), ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

            VBox content = new VBox(10);
            ComboBox<String> tagComboBox = new ComboBox<>();
            tagComboBox.setEditable(true);
            tagComboBox.getItems().addAll(availableTagNames);
            tagComboBox.setPromptText(i18n("dialog.add_tag.prompt"));
            tagComboBox.setPrefWidth(300);
            content.getChildren().add(new Label(i18n("label.tag")));
            content.getChildren().add(tagComboBox);
            dialog.getDialogPane().setContent(content);

            dialog.setResultConverter(dialogButton -> dialogButton == addButtonType
                    ? tagComboBox.getEditor().getText()
                    : null);

            Optional<String> result = com.example.jylos.ui.UiDialogs.show(dialog);
            if (result.isEmpty() || result.get().trim().isEmpty()) {
                return;
            }

            String tagName = result.get().trim();
            Optional<Tag> existingTag = tagService.getTagByTitle(tagName);

            Tag tag;
            if (existingTag.isPresent()) {
                tag = existingTag.get();
            } else {
                tag = new Tag(tagName);
                Tag createdTag = tagService.createTag(tag.getTitle());
                tag.setId(createdTag.getId());
            }

            boolean alreadyHasTag = tagService.getTagsForNote(currentNote).stream()
                    .anyMatch(t -> t.getId() != null && t.getId().equals(tag.getId()));

            if (alreadyHasTag) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(i18n("dialog.tag_already_assigned.title"));
                alert.setHeaderText(MessageFormat.format(i18n("dialog.tag_already_assigned.header"), tagName));
                com.example.jylos.ui.UiDialogs.show(alert);
                return;
            }

            tagService.addTagToNote(currentNote, tag);
            reloadCurrentNoteTags.accept(currentNote);
            refreshSidebarTags.run();
            updateStatus.accept(MessageFormat.format(i18n("status.tag_added"), tagName));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to add tag to note", e);
            updateStatus.accept(i18n("status.tag_add_error"));
        }
    }

    void removeTagFromNote(Note currentNote, Tag tag, Consumer<Note> reloadCurrentNoteTags) {
        if (currentNote == null) {
            updateStatus.accept(i18n("status.no_note_selected"));
            return;
        }
        if (tag == null || tag.getId() == null) {
            updateStatus.accept(i18n("status.invalid_tag"));
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(i18n("dialog.remove_tag.title"));
        confirm.setHeaderText(MessageFormat.format(i18n("dialog.remove_tag.header"), tag.getTitle()));
        confirm.setContentText(i18n("dialog.remove_tag.content"));

        Optional<ButtonType> result = com.example.jylos.ui.UiDialogs.show(confirm);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                if (tagService == null) {
                    updateStatus.accept(i18n("status.tag_remove_error"));
                    return;
                }
                tagService.removeTagFromNote(currentNote, tag);
                reloadCurrentNoteTags.accept(currentNote);
                updateStatus.accept(MessageFormat.format(i18n("status.tag_removed"), tag.getTitle()));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to remove tag from note", e);
                updateStatus.accept(MessageFormat.format(i18n("status.tag_remove_error"), e.getMessage()));
            }
        }
    }

    void showTagsManager(Runnable refreshSidebarTags) {
        if (tagService == null) {
            updateStatus.accept(i18n("status.error"));
            return;
        }
        try {
            List<Tag> allTags = tagService.getAllTags();

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle(i18n("dialog.tags_manager.title"));
            dialog.setHeaderText(i18n("dialog.tags_manager.header"));

            ButtonType closeButton = new ButtonType(i18n("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().add(closeButton);

            VBox content = new VBox(10);
            content.setPadding(new Insets(20));

            ListView<Tag> tagListView = new ListView<>();
            tagListView.getItems().addAll(allTags);
            tagListView.setCellFactory(lv -> new ListCell<Tag>() {
                @Override
                protected void updateItem(Tag tag, boolean empty) {
                    super.updateItem(tag, empty);
                    if (empty || tag == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        HBox hbox = new HBox(10);
                        Label nameLabel = new Label(tag.getTitle());
                        nameLabel.setPrefWidth(200);
                        Label dateLabel = new Label(
                                tag.getCreatedDate() != null ? tag.getCreatedDate() : i18n("label.not_available"));
                        dateLabel.getStyleClass().add("dialog-muted-label");

                        ButtonType deleteType = new ButtonType(i18n("action.delete"), ButtonBar.ButtonData.OK_DONE);
                        javafx.scene.control.Button deleteButton = new javafx.scene.control.Button(i18n("action.delete"));
                        deleteButton.setOnAction(e -> {
                            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                            confirm.setTitle(i18n("dialog.delete_tag.title"));
                            confirm.setHeaderText(i18n("dialog.tags_manager.delete_header"));
                            confirm.setContentText(i18n("dialog.tags_manager.delete_content"));
                            confirm.getButtonTypes().setAll(deleteType, ButtonType.CANCEL);
                            Optional<ButtonType> confirmResult = com.example.jylos.ui.UiDialogs.show(confirm);
                            if (confirmResult.isPresent() && confirmResult.get() == deleteType) {
                                try {
                                    tagService.deleteTag(tag.getId());
                                    tagListView.getItems().remove(tag);
                                    refreshSidebarTags.run();
                                    updateStatus.accept(
                                            MessageFormat.format(i18n("status.tag_deleted"), tag.getTitle()));
                                } catch (Exception ex) {
                                    logger.log(Level.SEVERE, "Failed to delete tag from tags manager", ex);
                                    updateStatus.accept(i18n("status.error_deleting_tag"));
                                }
                            }
                        });

                        hbox.getChildren().addAll(nameLabel, dateLabel, deleteButton);
                        setGraphic(hbox);
                    }
                }
            });

            content.getChildren().add(
                    new Label(MessageFormat.format(i18n("dialog.tags_manager.all_tags_count"), allTags.size())));
            content.getChildren().add(tagListView);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefSize(500, 400);

            com.example.jylos.ui.UiDialogs.show(dialog);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to open tags manager", e);
            updateStatus.accept(i18n("status.tags_manager_error"));
        }
    }
}
