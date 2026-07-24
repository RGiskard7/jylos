package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.jylos.data.models.Folder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MoveTargetSupportTest {

    @Test
    void buildTargetsUsesFolderPathLabelsToDisambiguateDuplicateNames() {
        Folder rootArticles = new Folder("articles-root", "Articulos");
        Folder things = new Folder("things", "Cosas");
        Folder nestedArticles = new Folder("articles-nested", "Articulos");
        Map<String, Folder> parents = Map.of(nestedArticles.getId(), things);

        List<MoveTargetSupport.MoveTarget> targets = MoveTargetSupport.buildTargets(
                List.of(rootArticles, things, nestedArticles),
                folder -> true,
                folder -> Optional.ofNullable(parents.get(folder.getId())),
                key -> "folder.root".equals(key) ? "Vault root" : key);

        List<String> labels = targets.stream()
                .map(MoveTargetSupport.MoveTarget::label)
                .toList();

        assertEquals(List.of("Vault root", "Articulos", "Cosas", "Cosas/Articulos"), labels);
    }

    @Test
    void filterTargetsIgnoresAccents() {
        List<MoveTargetSupport.MoveTarget> targets = List.of(
                new MoveTargetSupport.MoveTarget(null, "Vault root"),
                new MoveTargetSupport.MoveTarget(new Folder("articles", "Articulos"), "Articulos"));

        List<String> labels = MoveTargetSupport.filterTargets(targets, "Artículos").stream()
                .map(MoveTargetSupport.MoveTarget::label)
                .toList();

        assertEquals(List.of("Articulos"), labels);
    }
}
