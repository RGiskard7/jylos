/**
 * JavaFX UI layer for Jylos.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><strong>6 FXML controllers</strong> — one per view: {@link MainController}, {@link ToolbarController},
 *       {@link SidebarController}, {@link NotesListController}, {@link EditorController},
 *       {@link GraphController}</li>
 *   <li><strong>Shell-support units</strong> — feature helpers grouped by responsibility and named by role:
 *       {@code *Support} and {@code *Operations}. They collaborate with {@link MainController};
 *       not a plugin framework</li>
 *   <li><strong>{@code ui.components}</strong> — reusable widgets (command palette, quick switcher, plugin manager)</li>
 *   <li><strong>{@code ui.theme}</strong> — theme application, catalogs and snippet discovery</li>
 *   <li><strong>{@code ui.preferences}</strong> — persistence of UI preference state</li>
 * </ul>
 *
 * <p>Business logic lives in {@code service.*} and {@code data.dao.*}. Controllers bind FXML, publish/consume
 * {@link com.example.jylos.event.EventBus} events where fan-out is useful, and delegate persistence to services.
 * One-to-one UI flows should prefer explicit wiring and callbacks.</p>
 */
package com.example.jylos.ui.controller;
