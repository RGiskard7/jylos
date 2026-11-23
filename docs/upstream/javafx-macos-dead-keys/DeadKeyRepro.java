import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodTextRun;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Minimal reproducer: on macOS, a dead key pressed several times in a row types fewer
 * characters than key presses, in a WebView and in a TextField alike. Native Cocoa text views
 * type one character per press.
 *
 * Run with a keyboard layout where ` is a dead key (macOS "Spanish - ISO": the key right of P).
 * Click in the text area and press that key five times, then Return. Do the same in the text
 * field. The window shows both values; they and every InputMethodEvent JavaFX delivers are
 * printed.
 *
 * Build and run (JavaFX SDK or jars on the module path):
 *   javac --module-path $FX --add-modules javafx.controls,javafx.web DeadKeyRepro.java
 *   java  --module-path $FX --add-modules javafx.controls,javafx.web DeadKeyRepro
 */
public class DeadKeyRepro extends Application {

    @Override
    public void start(Stage stage) {
        WebView web = new WebView();
        web.getEngine().loadContent("<textarea id='t' style='width:95%;height:120px;font-size:20px'></textarea>"
                + "<script>document.getElementById('t').focus();</script>");
        TextField field = new TextField();
        Label values = new Label();
        web.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, e -> log("WebView  ", e));
        field.addEventFilter(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, e -> log("TextField", e));
        Thread poll = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                Platform.runLater(() -> {
                    Object area = web.getEngine().executeScript("document.getElementById('t').value");
                    String now = "textarea: \"" + String.valueOf(area).replace("\n", "\\n") + "\" ("
                            + String.valueOf(area).length() + " chars)   TextField: \"" + field.getText() + "\" ("
                            + field.getText().length() + " chars)";
                    if (!now.equals(values.getText())) {
                        values.setText(now);
                        System.out.println(now);
                    }
                });
            }
        });
        poll.setDaemon(true);
        poll.start();
        stage.setScene(new Scene(new VBox(8, web, field, values), 600, 400));
        stage.setTitle("Dead key repeat - JavaFX " + System.getProperty("javafx.runtime.version"));
        stage.show();
    }

    private static void log(String where, InputMethodEvent e) {
        String composed = e.getComposed().stream().map(InputMethodTextRun::getText).collect(Collectors.joining());
        System.out.println(where + " composed=\"" + composed + "\" committed=\""
                + e.getCommitted().replace("\r", "\\r") + "\"");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
