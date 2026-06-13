package com.example.jylos.insights;

/**
 * How connected a note is within the knowledge graph, counting only
 * <em>resolved</em> note→note links (links to non-existent notes are reported
 * separately as {@link BrokenLinkInfo}).
 *
 * @param noteId   note id (clickable target)
 * @param title    display title of the note
 * @param inbound  number of resolved links pointing <em>to</em> this note (backlinks)
 * @param outbound number of resolved links pointing <em>from</em> this note
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public record NoteConnectivityInfo(String noteId, String title, int inbound, int outbound) {

    /** Total connections (inbound + outbound), used to rank "most connected" notes. */
    public int total() {
        return inbound + outbound;
    }
}
