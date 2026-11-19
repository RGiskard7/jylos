/**
 * Jylos's own plugins (core plugins): built into JARs that the installers ship next to the
 * app, updated with it, and turned on or off in the plugin manager (they are not removed).
 * The list, with each one's state in a new install, is
 * {@link com.example.jylos.plugin.CorePlugins}.
 *
 * <p>Single-file plugins live here (Outline, Calendar, Daily Notes, Reading Time, Auto Backup,
 * Templates, Word Count, Table of Contents); larger ones are bundles in their own folder with a
 * {@code plugin.properties} (Dataview, MCP Server, Publish). See docs/PLUGINS.md.</p>
 *
 * <h2>Creating Custom Plugins</h2>
 * <p>To create a custom plugin, implement the 
 * {@link com.example.jylos.plugin.Plugin} interface and register
 * it with the {@link com.example.jylos.plugin.PluginManager}.</p>
 * 
 * <pre>{@code
 * public class MyPlugin implements Plugin {
 *     @Override
 *     public String getId() { return "my-plugin"; }
 *     
 *     @Override
 *     public String getName() { return "My Plugin"; }
 *     
 *     @Override
 *     public String getVersion() { return "1.0.0"; }
 *     
 *     @Override
 *     public void initialize(PluginContext context) {
 *         context.registerCommand("My Command", "Description", () -> {
 *             // Your action here
 *         });
 *     }
 *     
 *     @Override
 *     public void shutdown() {
 *         // Cleanup
 *     }
 * }
 * }</pre>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
package com.example.jylos.plugin.builtin;
