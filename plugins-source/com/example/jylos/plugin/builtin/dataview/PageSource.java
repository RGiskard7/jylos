package com.example.jylos.plugin.builtin.dataview;

import java.util.List;

/**
 * The read side of the metadata index: everything query execution needs to see.
 *
 * <p>Kept separate from {@link DataviewIndex} so the query engine depends on the set of
 * pages, not on how they are cached, invalidated or read from storage.</p>
 */
interface PageSource {

    /** Every page a query may select from. */
    List<Page> pages();

    /** Resolves a page by note title, case-insensitively, or {@code null} if absent. */
    Page pageByTitle(String title);
}
