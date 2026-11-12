package com.example.jylos.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * The small window shown while the main window is being built. Building it (the editor, the
 * vault, the first paint) takes a few seconds and blocks the UI thread, so without this the app
 * shows nothing at all meanwhile. It uses the colours of the theme the app opens in.
 */
public final class StartupSplash {

    private static final CornerRadii CORNERS = new CornerRadii(14);
    private static final Duration ON_SCREEN_DELAY = Duration.millis(300);

    private final Stage stage;

    private StartupSplash(Stage stage) {
        this.stage = stage;
    }

    /**
     * Shows the splash, centred on the screen.
     *
     * @param dark    whether the app opens in a dark theme
     * @param loading the "loading" text, in the app's language
     */
    public static StartupSplash show(boolean dark, String loading) {
        Color background = Color.web(dark ? "#1e1e1e" : "#fafafa");
        Color border = Color.web(dark ? "#3a3a3a" : "#c6c9ce");
        Color text = Color.web(dark ? "#e0e0e0" : "#1f2024");
        Color muted = Color.web(dark ? "#a0a0a0" : "#4a4d52");

        Label name = new Label("Jylos");
        name.setFont(Font.font(null, FontWeight.BOLD, 22));
        name.setTextFill(text);
        Label status = new Label(loading);
        status.setFont(Font.font(13));
        status.setTextFill(muted);

        VBox root = new VBox(10);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(28, 48, 28, 48));
        root.setBackground(new Background(new BackgroundFill(background, CORNERS, Insets.EMPTY)));
        root.setBorder(new Border(new BorderStroke(border, BorderStrokeStyle.SOLID, CORNERS, BorderWidths.DEFAULT)));
        AppIconLoader.load(72).ifPresent(icon -> root.getChildren().add(new ImageView(icon)));
        root.getChildren().addAll(name, status);

        Scene scene = new Scene(root, 300, 220);
        scene.setFill(Color.TRANSPARENT);
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setTitle("Jylos");
        AppIconLoader.load().ifPresent(icon -> stage.getIcons().add(icon));
        stage.centerOnScreen();
        // A newly launched app is not brought to the front on macOS: without this the splash
        // opens behind the window the user launched it from.
        stage.setAlwaysOnTop(true);
        stage.show();
        stage.toFront();
        return new StartupSplash(stage);
    }

    /**
     * Runs {@code work} on the UI thread once the splash is on screen. The work blocks the UI
     * thread, and on macOS a new window only reaches the screen some time after JavaFX has drawn
     * it: blocking sooner keeps the splash hidden until the work is done. Measured, 150 ms was not
     * always enough and 200 ms was; this waits a little longer than that.
     */
    public void thenRun(Runnable work) {
        PauseTransition onScreen = new PauseTransition(ON_SCREEN_DELAY);
        // Out of the pulse: the work may show a dialog (showAndWait), which JavaFX refuses during
        // animation processing.
        onScreen.setOnFinished(event -> Platform.runLater(work));
        onScreen.play();
    }

    public void close() {
        stage.close();
    }

    Stage stage() {
        return stage;
    }
}
