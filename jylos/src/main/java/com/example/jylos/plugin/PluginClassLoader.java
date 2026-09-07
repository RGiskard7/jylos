package com.example.jylos.plugin;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * The {@link URLClassLoader} every plugin JAR is loaded with — a plain {@code
 * URLClassLoader} except for one override: a request for a {@code com.example.jylos.*}
 * class outside {@link PluginApiSurface} never reaches the app's own classloader (its
 * parent), even though that parent could resolve it. It is looked for only in the
 * plugin's own JAR instead, and fails with a clear message pointing at the documented
 * API if it is not there — which, for an app-internal class a plugin never bundles
 * itself, it never is.
 *
 * <p>Everything else is completely unaffected: the JDK, JavaFX, and any third-party
 * library the app bundles (Gson, commonmark, …) still resolve through the parent exactly
 * as before — see {@link PluginApiSurface}'s own doc for why this only ever gates the
 * app's own namespace. A name already inside the allowed surface (including a plugin's
 * own {@code com.example.jylos.plugin.builtin.*} classes, since that whole package is
 * allowed) falls through to the standard {@link ClassLoader#loadClass(String, boolean)}
 * algorithm unchanged: parent first, then this loader's own JAR if the parent does not
 * have it — which is exactly how a plugin's own classes have always resolved.</p>
 *
 * <p>This is still namespace isolation with one compiler-enforced boundary added, not a
 * security sandbox — see {@code docs/PLUGINS.md}'s own, unchanged, honest statement of
 * what plugins can do once loaded. What changes here is only which classes a plugin
 * can even reach in the first place.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 4.6.0
 */
final class PluginClassLoader extends URLClassLoader {

    PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("com.example.jylos.") && !PluginApiSurface.isAllowed(name)) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> alreadyLoaded = findLoadedClass(name);
                if (alreadyLoaded != null) {
                    if (resolve) {
                        resolveClass(alreadyLoaded);
                    }
                    return alreadyLoaded;
                }
                try {
                    // Deliberately skips the parent (the app's own classloader, which
                    // could resolve this) and looks only in the plugin's own JAR — a
                    // legitimate plugin class never collides with an unlisted internal
                    // app class name, so this only ever matters for the rejection below.
                    Class<?> ownClass = findClass(name);
                    if (resolve) {
                        resolveClass(ownClass);
                    }
                    return ownClass;
                } catch (ClassNotFoundException notInOwnJar) {
                    throw new ClassNotFoundException(
                            "Plugin tried to load '" + name + "', which is not part of the documented plugin API. "
                                    + "See docs/PLUGINS.md#extension-points-plugincontext for what plugins can "
                                    + "depend on — everything else in com.example.jylos is internal and not "
                                    + "supported for plugin use, even though it may have worked before this check "
                                    + "existed.",
                            notInOwnJar);
                }
            }
        }
        return super.loadClass(name, resolve);
    }
}
