package com.durdencorp.pswmanager.desktop;

import com.durdencorp.pswmanager.PswmanagerApplication;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.StandardServletEnvironment;

public final class DesktopApplication extends Application {
    private DesktopStorage storage;
    private ConfigurableApplicationContext context;
    private boolean closing;
    private Thread startup;
    private Stage stage;
    private WebEngine engine;
    private String origin;
    // WebEngine retains Java callbacks weakly; keep the bridge alive here.
    private DesktopBridge bridge;

    @Override public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Password123");
        stage.setScene(new Scene(new BorderPane(new Label("Avvio di Password123…")), 1150, 780));
        stage.setMinWidth(850);
        stage.setMinHeight(600);
        stage.show();
        try {
            storage = new DesktopStorage(DesktopStorage.defaultDirectory());
            if (!Files.exists(storage.database()) && !selectInitialDatabase()) {
                Platform.exit();
                return;
            }
        } catch (Exception e) {
            error(e.getMessage());
            Platform.exit();
            return;
        }
        startup = new Thread(this::startServer, "desktop-startup");
        startup.setDaemon(false);
        startup.start();
    }

    private boolean selectInitialDatabase() throws Exception {
        ButtonType create = new ButtonType("Crea nuovo archivio");
        ButtonType existing = new ButtonType("Importa database esistente");
        Alert choice = new Alert(Alert.AlertType.CONFIRMATION,
                "Il database desktop sarà salvato in:\n" + storage.database()
                + "\n\nPuoi creare un archivio nuovo oppure importare una copia di un passwords.db esistente."
                + " La copia manterrà la stessa master password e l'originale resterà invariato.",
                create, existing, ButtonType.CANCEL);
        choice.initOwner(stage);
        choice.setTitle("Primo avvio");
        choice.setHeaderText("Scegli il tuo archivio");
        ButtonType selected = choice.showAndWait().orElse(ButtonType.CANCEL);
        if (selected == create) return true;
        if (selected != existing) return false;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleziona il database da copiare");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Database SQLite", "*.db"));
        Path working = Path.of("").toAbsolutePath();
        if (Files.isDirectory(working)) chooser.setInitialDirectory(working.toFile());
        var file = chooser.showOpenDialog(stage);
        if (file == null) return false;
        storage.importDatabase(file.toPath());
        return true;
    }

    private void startServer() {
        try {
            SpringApplication application = new SpringApplication(PswmanagerApplication.class);
            application.setHeadless(false);
            application.setAddCommandLineProperties(false);
            // Configure before EnvironmentPrepared so logging also uses the data directory.
            StandardServletEnvironment environment = new StandardServletEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("desktop", Map.of(
                            "server.address", "127.0.0.1", "server.port", "0",
                            "spring.datasource.url", "jdbc:sqlite:" + storage.database(),
                            "logging.file.name", storage.directory().resolve("logs/application.log").toString(),
                            "spring.jpa.show-sql", "false", "logging.level.org.hibernate.SQL", "WARN",
                            "logging.level.org.hibernate.type", "WARN")));
            application.setEnvironment(environment);
            System.setProperty("pswmanager.log-dir", storage.directory().resolve("logs").toString());
            ConfigurableApplicationContext started = application.run();
            synchronized (this) {
                if (closing) {
                    started.close();
                    closeStorage();
                    return;
                }
                context = started;
            }
            int port = ((ServletWebServerApplicationContext) started).getWebServer().getPort();
            Platform.runLater(() -> showBrowser("http://127.0.0.1:" + port));
        } catch (Exception e) {
            synchronized (this) {
                closeStorage();
                if (closing) return;
            }
            Platform.runLater(() -> { error("Impossibile avviare l'applicazione. " + e.getMessage()); Platform.exit(); });
        }
    }

    private void showBrowser(String origin) {
        this.origin = origin;
        WebView view = new WebView();
        engine = view.getEngine();
        engine.setUserDataDirectory(storage.directory().resolve("webview").toFile());
        bridge = new DesktopBridge(stage, () -> isLocal(engine.getLocation()), url -> getHostServices().showDocument(url));
        engine.setOnAlert(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, event.getData(), ButtonType.OK);
            alert.initOwner(stage); alert.setHeaderText(null); alert.showAndWait();
        });
        engine.setConfirmHandler(message -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
            alert.initOwner(stage); alert.setHeaderText(null);
            return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
        });
        engine.setPromptHandler(prompt -> {
            TextInputDialog dialog = new TextInputDialog(prompt.getDefaultValue());
            dialog.initOwner(stage); dialog.setHeaderText(prompt.getMessage());
            return dialog.showAndWait().orElse(null);
        });
        engine.setCreatePopupHandler(features -> null);
        engine.locationProperty().addListener((observable, oldUrl, newUrl) -> {
            if (!isLocal(newUrl) && !newUrl.isEmpty()) {
                engine.getLoadWorker().cancel();
                Platform.runLater(() -> { engine.load(origin); error("La finestra può aprire solo le pagine di Password123."); });
            }
        });
        engine.getLoadWorker().stateProperty().addListener((observable, previous, current) -> {
            if (current == Worker.State.SUCCEEDED && isLocal(engine.getLocation())) {
                ((JSObject) engine.executeScript("window")).setMember("password123Desktop", bridge);
                engine.executeScript("""
                    document.addEventListener('click', function(event) {
                        const link = event.target.closest && event.target.closest('a[href]');
                        if (link && new URL(link.href).origin !== location.origin) {
                            event.preventDefault();
                            window.password123Desktop.openExternal(link.href);
                        }
                    }, true);
                    """);
            } else if (current == Worker.State.FAILED) {
                Platform.runLater(() -> error("Impossibile caricare la pagina. Usa Ricarica per riprovare."));
            }
        });
        Button reload = new Button("Ricarica");
        reload.setOnAction(event -> engine.load(origin));
        Button info = new Button("Archivio");
        info.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Database: " + storage.database() + "\n\nPer il backup usa Esporta nell'applicazione."
                    + "\nLa master password è necessaria anche per riaprire il backup.", ButtonType.OK);
            alert.initOwner(stage); alert.setHeaderText("Archivio locale"); alert.showAndWait();
        });
        BorderPane root = new BorderPane(view);
        root.setTop(new ToolBar(reload, info));
        stage.getScene().setRoot(root);
        engine.load(origin);
    }

    private boolean isLocal(String location) {
        try {
            URI uri = URI.create(location);
            return origin != null && origin.equals(uri.getScheme() + "://" + uri.getRawAuthority());
        } catch (IllegalArgumentException e) { return false; }
    }

    private void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "Errore inatteso." : message, ButtonType.OK);
        alert.initOwner(stage); alert.setHeaderText("Password123"); alert.showAndWait();
    }

    @Override public synchronized void stop() {
        closing = true;
        if (bridge != null) bridge.clearClipboard();
        if (context != null) {
            context.close();
            closeStorage();
        } else if (startup == null || !startup.isAlive()) {
            // The startup thread may still be reading the vault: release only after it finishes.
            closeStorage();
        }
    }

    private void closeStorage() {
        if (storage != null) {
            try { storage.close(); } catch (java.io.IOException ignored) { }
            storage = null;
        }
    }
}
