/**
 * JavaFX UI layer for Jylos.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><strong>5 FXML controllers</strong> — one per view: {@link MainController}, {@link ToolbarController},
 *       {@link SidebarController}, {@link NotesListController}, {@link EditorController}</li>
 *   <li><strong>Shell-support units</strong> — package-private helpers grouped by responsibility:
 *       {@code CommandSupport}, {@code DocumentSupport}, {@code DialogSupport}, {@code UiEventSupport},
 *       {@code LayoutSupport}, {@code ThemeSupport}, {@code PluginSupport}, {@code ContentOperations}
 *       and {@code UiPreferencesStore}. They collaborate with {@link MainController}; not a plugin framework</li>
 *   <li><strong>{@code ui.components}</strong> — reusable widgets (command palette, quick switcher, plugin manager)</li>
 * </ul>
 *
 * <p>Business logic lives in {@code service.*} and {@code data.dao.*}. Controllers bind FXML, publish/consume
 * {@link com.example.jylos.event.EventBus} events, and delegate persistence to services.</p>
 */
package com.example.jylos.ui.controller;
