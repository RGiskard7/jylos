package com.example.jylos.insights;

/**
 * A wiki-link (or internal Markdown link) that points to a note which does not
 * exist in the vault — i.e. an edge to a "ghost" node in the knowledge graph.
 *
 * @param sourceNoteId id of the note that contains the broken link (clickable target)
 * @param sourceTitle  display title of the source note
 * @param targetTitle  the unresolved link target as written by the user
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public record BrokenLinkInfo(String sourceNoteId, String sourceTitle, String targetTitle) {
}
