package com.example.jylos.ui.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/**
 * {@link HtmlView} on JavaFX's own WebView (WebKit). A node itself, so FXML can place it where
 * a page is shown. The only place, with this package, that knows the engine.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public class JavaFxHtmlView extends StackPane implements HtmlView {

    private static final Logger logger = LoggerConfig.getLogger(JavaFxHtmlView.class);

    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private final Map<String, Object> bridges = new LinkedHashMap<>();
    private final List<Runnable> onLoaded = new ArrayList<>();

    public JavaFxHtmlView() {
        getStyleClass().add("html-view");
        webView.setMinSize(0, 0);
        webView.setPrefSize(0, 0);
        getChildren().add(webView);
        engine.setJavaScriptEnabled(true);
        engine.setOnError(event -> logger.warning("Page error: " + event.getMessage()));
        engine.setOnAlert(event -> logger.warning("Page alert: " + event.getData()));
        engine.getLoadWorker().exceptionProperty().addListener((obs, oldError, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Could not load page", error);
            }
        });
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                bridges.forEach(this::setMember);
                new ArrayList<>(onLoaded).forEach(Runnable::run);
            }
        });
    }

    @Override
    public void setContextMenuEnabled(boolean enabled) {
        webView.setContextMenuEnabled(enabled);
    }

    @Override
    public Node node() {
        return this;
    }

    @Override
    public void loadHtml(String html) {
        engine.loadContent(html != null ? html : "", "text/html");
    }

    @Override
    public void run(String script) {
        evaluate(script);
    }

    @Override
    public Object evaluate(String expression) {
        try {
            return engine.executeScript(expression);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Script failed in the page: " + e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void exposeBridge(String name, Object bridge) {
        bridges.put(name, bridge);
        if (isLoaded()) {
            setMember(name, bridge);
        }
    }

    private void setMember(String name, Object bridge) {
        try {
            ((JSObject) engine.executeScript("window")).setMember(name, bridge);
        } catch (RuntimeException e) {
            logger.warning("Could not expose " + name + " to the page: " + e.getMessage());
        }
    }

    @Override
    public void onLoaded(Runnable action) {
        onLoaded.add(action);
    }

    @Override
    public void whenLoaded(Runnable action) {
        if (isLoaded()) {
            action.run();
            return;
        }
        Worker<Void> worker = engine.getLoadWorker();
        worker.stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> obs, Worker.State oldState,
                    Worker.State state) {
                if (state == Worker.State.SUCCEEDED) {
                    worker.stateProperty().removeListener(this);
                    action.run();
                } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                    worker.stateProperty().removeListener(this);
                }
            }
        });
    }

    @Override
    public boolean isLoaded() {
        return engine.getLoadWorker().getState() == Worker.State.SUCCEEDED;
    }

    @Override
    public void focus() {
        webView.requestFocus();
    }

    @Override
    public boolean hasFocus() {
        return webView.isFocused();
    }
}
