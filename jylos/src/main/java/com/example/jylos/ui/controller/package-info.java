/**
 * JavaFX UI layer for Jylos.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><strong>5 FXML controllers</strong> — one per view: {@link MainController}, {@link ToolbarController},
 *       {@link SidebarController}, {@link NotesListController}, {@link EditorController}</li>
 *   <li><strong>Shell-support units</strong> — package-private helpers grouped by responsibility and named by role:
 *       {@code *Support}, {@code *Store}, {@code *Catalog}, {@code *Operations}. They collaborate with
 *       {@link MainController}; not a plugin framework</li>
 *   <li><strong>{@code ui.components}</strong> — reusable widgets (command palette, quick switcher, plugin manager)</li>
 * </ul>
 *
 * <p>Business logic lives in {@code service.*} and {@code data.dao.*}. Controllers bind FXML, publish/consume
 * {@link com.example.jylos.event.EventBus} events where fan-out is useful, and delegate persistence to services.
 * New code should prefer explicit wiring over opportunistic global lookups.</p>
 */
package com.example.jylos.ui.controller;
