package com.example.jylos.plugin;

import com.example.jylos.data.models.Note;

/**
 * Renders a fenced code block into HTML shown inline in the editor's Live Preview.
 *
 * <p>Where {@link PreviewEnhancer} post-processes the read-only preview, this hook reaches
 * the <em>editor</em>: a block written as
 * <code>```language … ```</code> is displayed as the returned markup while the cursor is
 * outside it, and reverts to its source the moment the cursor moves in — the same
 * reveal-on-edit rule Live Preview already applies to tables and images.</p>
 *
 * <h2>Threading and cost</h2>
 * <p>Called on a background thread, never on the JavaFX Application Thread, and never
 * from the editor's own render loop: results are computed ahead of time and pushed into
 * the editor. An implementation may therefore read notes or do other I/O, but it is
 * re-invoked whenever the note or the vault changes, so it should stay proportionate to
 * the work it actually needs.</p>
 *
 * <h2>Returned markup</h2>
 * <p>The HTML is inserted into the editor document as-is, so an implementation is
 * responsible for escaping anything derived from note content. Returning {@code null}
 * leaves the block showing its source, which is the right answer when the block is not
 * meant for this renderer after all.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
public interface EditorBlockRenderer {

    /**
     * Renders one block's body.
     *
     * @param note   the note the block belongs to (may be {@code null} when unknown)
     * @param source the block's body, without the fence lines and trimmed
     * @return HTML to display in place of the block, or {@code null} to leave the source
     */
    String render(Note note, String source);
}
