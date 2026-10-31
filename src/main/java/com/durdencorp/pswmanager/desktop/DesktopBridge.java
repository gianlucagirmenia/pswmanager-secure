package com.durdencorp.pswmanager.desktop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import javafx.animation.PauseTransition;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Narrow bridge: no arbitrary file paths or shell commands are exposed to HTML. */
public final class DesktopBridge {
    private final Stage owner;
    private final BooleanSupplier trustedPage;
    private final java.util.function.Consumer<String> openBrowser;
    private final PauseTransition clipboardExpiry = new PauseTransition(Duration.seconds(30));
    private String copiedText;

    DesktopBridge(Stage owner, BooleanSupplier trustedPage, java.util.function.Consumer<String> openBrowser) {
        this.owner = owner;
        this.trustedPage = trustedPage;
        this.openBrowser = openBrowser;
        clipboardExpiry.setOnFinished(event -> clearClipboard());
    }

    public void copyText(String text) {
        requireLocalPage();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        copiedText = text;
        clipboardExpiry.playFromStart();
    }

    public boolean saveText(String name, String content) throws java.io.IOException {
        requireLocalPage();
        String filename = Path.of(name).getFileName().toString();
        if (!filename.matches("passwords-[a-z-]+-\\d{4}-\\d{2}-\\d{2}\\.(enc|csv|json)"))
            throw new IllegalArgumentException("Nome del backup non valido.");
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salva esportazione");
        chooser.setInitialFileName(filename);
        var file = chooser.showSaveDialog(owner);
        if (file == null) return false;
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return true;
    }

    private void requireLocalPage() {
        if (!trustedPage.getAsBoolean()) throw new SecurityException("Pagina non autorizzata.");
    }

    public void openExternal(String url) {
        requireLocalPage();
        var uri = java.net.URI.create(url);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) return;
        var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Aprire questo indirizzo nel browser?\n" + url,
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        alert.initOwner(owner);
        alert.setHeaderText("Sito esterno");
        if (alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK)
            openBrowser.accept(url);
    }

    void clearClipboard() {
        clipboardExpiry.stop();
        if (copiedText != null && copiedText.equals(Clipboard.getSystemClipboard().getString()))
            Clipboard.getSystemClipboard().clear();
        copiedText = null;
    }
}
