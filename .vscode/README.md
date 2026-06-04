# VS Code Configuration for Jylos

## ✅ Issue Resolved

The Eclipse `.classpath` and `.project` files were interfering with Maven detection. These files have been removed.

## Steps to Reload the Project

### 1. Clean Java Language Server Workspace

1. Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on Mac)
2. Type: `Java: Clean Java Language Server Workspace`
3. Select and confirm
4. Wait for VS Code to reload automatically

### 2. Reload Maven Projects

1. Press `Ctrl+Shift+P`
2. Type: `Java: Reload Projects`
3. Wait for Maven to sync dependencies (may take 1-2 minutes)

### 3. Verify Maven Detected the Project

1. Open command palette (`Ctrl+Shift+P`)
2. Type: `Java: Show Build Job Status`
3. You should see that the `jylos` project is being processed

### 4. If It Still Doesn't Work, Reload the Window

1. Press `Ctrl+Shift+P`
2. Type: `Developer: Reload Window`
3. Wait for it to reload completely

## Verify Java Configuration

1. Press `Ctrl+Shift+P`
2. Type: `Java: Configure Java Runtime`
3. Make sure Java 17 is selected

## Run from the IDE (JavaFX)

JavaFX needs `--module-path` and `--add-modules`. Do **not** use the default Run button without a launch config.

1. Open **Run and Debug** (sidebar).
2. Choose **Run: Main (recomendado)** (macOS Apple Silicon) or **Run: Main (Mac Intel)** / **Run: Main (Windows)** as appropriate.
3. Press F5 or the green play button.

If you still see `Module javafx.web not found`:

1. Download dependencies: `mvn -f jylos/pom.xml compile`
2. **Java: Clean Java Language Server Workspace**, then reload the window.

Alternative without IDE config:

```bash
mvn -f jylos/pom.xml javafx:run
```

or `./scripts/launch-jylos.sh`

## Verify Maven is Working

1. Open integrated terminal (`Ctrl+`` `)
2. Run: `mvn -f jylos/pom.xml -version`
3. Should display Maven version

## Important Note

**DO NOT** manually recreate `.classpath` or `.project` files. VS Code should use Maven automatically to detect the project structure.

If you need to use Eclipse, let Eclipse generate these files automatically from Maven (Import → Existing Maven Projects).

## If Nothing Works

1. Close VS Code completely
2. Delete the `.metadata` folder if it exists in the workspace (shouldn't be there)
3. Open VS Code again
4. Wait for Maven to import the project automatically (may take 2-3 minutes the first time)
