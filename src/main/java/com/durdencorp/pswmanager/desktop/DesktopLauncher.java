package com.durdencorp.pswmanager.desktop;

/** Separate launcher avoids the JVM's special JavaFX Application launch path. */
public final class DesktopLauncher {
    private DesktopLauncher() { }
    public static void main(String[] args) {
        javafx.application.Application.launch(DesktopApplication.class, args);
    }
}
