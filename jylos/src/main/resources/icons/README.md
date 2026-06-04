# Packaging icons

Paths referenced from `src/main/resources/app.properties` (relative to the `jylos/` module):

| Platform | Property | Expected file |
|----------|----------|----------------|
| Windows | `app.icon.windows` | `src/main/resources/icons/icon.ico` |
| macOS | `app.icon.macos` | `src/main/resources/icons/icon.icns` |
| Linux | `app.icon.linux` | `src/main/resources/icons/icon.png` |

Window icon (in-app title bar): `app.icon.window` → **`icons/app-icon.png`** (loaded by `Main.java` via `AppConfig.getWindowIconPath()`).

Update **`icons/app-icon.png`** for:
- the main window title bar (`Main.java`),
- the large icon in **Help → About Jylos** (`DialogSupport.showAbout()` via `AppIconLoader`).

Files `icon.ico` / `icon.icns` / `icon.png` are only for installers (jpackage).

If a packaging icon file is missing, `package-*.sh` / `package-windows.ps1` may omit `--icon`; the app still runs.

Add multi-size assets before release builds (typical sizes: 16–256 px for ICO; up to 1024 px for ICNS).
