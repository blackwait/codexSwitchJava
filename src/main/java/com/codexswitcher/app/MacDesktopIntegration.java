package com.codexswitcher.app;

import com.codexswitcher.service.AppServices;
import com.codexswitcher.ui.MacTrayController;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.awt.desktop.AppReopenedListener;
import java.awt.desktop.QuitStrategy;

final class MacDesktopIntegration {

    private MacDesktopIntegration() {
    }

    static void install(Stage stage, MacTrayController trayController, AppServices services, Runnable quitAction) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            desktop.addAppEventListener((AppReopenedListener) event -> Platform.runLater(() -> showMainWindow(stage, trayController)));
            desktop.setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((event, response) -> {
                    quitAction.run();
                    response.performQuit();
                });
            }
        } catch (Exception e) {
            services.store().logLine("注册 macOS 桌面集成失败", e.getMessage());
        }
    }

    static void configureTrayWindow(Stage stage) {
        Platform.setImplicitExit(false);
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });
    }

    static void showMainWindow(Stage stage, MacTrayController trayController) {
        if (trayController != null) {
            trayController.showMainWindow();
            return;
        }
        if (stage.isIconified()) {
            stage.setIconified(false);
        }
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }
}
