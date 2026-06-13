package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UiPresentationFxmlGuardTest {

    private static final Path SIDEBAR_FXML = Path
            .of("src/main/resources/com/example/jylos/ui/view/SidebarView.fxml");
    private static final Path EDITOR_FXML = Path
            .of("src/main/resources/com/example/jylos/ui/view/EditorView.fxml");

    @Test
    void sidebarTabsShouldExposeIdsForRuntimePresentationSwitch() throws IOException {
        String source = Files.readString(SIDEBAR_FXML, StandardCharsets.UTF_8);

        assertTrue(source.contains("fx:id=\"foldersTab\""), "Sidebar folders tab should have fx:id.");
        assertTrue(source.contains("fx:id=\"tagsTab\""), "Sidebar tags tab should have fx:id.");
        assertTrue(source.contains("fx:id=\"recentTab\""), "Sidebar recent tab should have fx:id.");
        assertTrue(source.contains("fx:id=\"favoritesTab\""), "Sidebar favorites tab should have fx:id.");
        assertTrue(source.contains("fx:id=\"trashTab\""), "Sidebar trash tab should have fx:id.");
    }

    @Test
    void editorViewShouldStartWithCollapsedTagsAndViewModeIconsAvailable() throws IOException {
        String source = Files.readString(EDITOR_FXML, StandardCharsets.UTF_8);

        assertTrue(source.contains("fx:id=\"toggleTagsBtn\"") && source.contains("selected=\"false\""),
                "Tags toggle should start collapsed by default.");
        assertTrue(source.contains("fx:id=\"tagsContainer\"") && source.contains("visible=\"false\"")
                && source.contains("managed=\"false\""),
                "Tags container should be hidden and unmanaged on startup.");
        assertTrue(source.contains("fx:id=\"editorOnlyButton\"") && source.contains("FontIcon"),
                "Editor-only button should support icon rendering.");
        assertTrue(source.contains("fx:id=\"splitViewButton\"") && source.contains("FontIcon"),
                "Split-view button should support icon rendering.");
        assertTrue(source.contains("fx:id=\"previewOnlyButton\"") && source.contains("FontIcon"),
                "Preview-only button should support icon rendering.");
    }

    @Test
    void editorUsesCodeAreaForSyntaxHighlighting() throws IOException {
        String source = Files.readString(EDITOR_FXML, StandardCharsets.UTF_8);

        assertTrue(source.contains("import org.fxmisc.richtext.CodeArea"),
                "EditorView should import RichTextFX CodeArea.");
        assertTrue(source.contains("<CodeArea fx:id=\"noteContentArea\""),
                "Note content editor should be a CodeArea (syntax-highlighting editor).");
    }

    @Test
    void editorExposesTabBarAndSaveIndicator() throws IOException {
        String source = Files.readString(EDITOR_FXML, StandardCharsets.UTF_8);

        assertTrue(source.contains("fx:id=\"editorTabBar\""),
                "EditorView should host the open-note tab strip.");
        assertTrue(source.contains("fx:id=\"dirtySaveIndicator\""),
                "EditorView should host the inline save indicator dot.");
    }
}
