package com.codexswitcher.app;

import com.codexswitcher.service.AppServices;
import com.codexswitcher.ui.MacTrayController;
import com.codexswitcher.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CodexSwitcherApplication extends Application {

    private Stage primaryStage;
    private MainView mainView;
    private MacTrayController trayController;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        AppState state = new AppState();
        AppServices services = new AppServices();
        try {
            services.store().ensureDefaultConfigExists();
        } catch (Exception e) {
            services.store().logLine("初始化默认 config.toml 失败", e.getMessage());
        }
        state.setActiveAccount(services.store().getActiveAccount());
        state.setVscodeInstallDir(services.store().loadVscodeInstallDir());
        state.setCloudSyncSettings(services.store().loadCloudSyncSettings());

        mainView = new MainView(state, services);
        Scene scene = new Scene(mainView, 1120, 780);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("Codex Switcher");
        stage.setMinWidth(1020);
        stage.setMinHeight(700);
        try (InputStream input = getClass().getResourceAsStream("/assets/icon_tray.png")) {
            if (input != null) {
                stage.getIcons().add(new Image(input));
            }
        } catch (Exception ignored) {
        }
        stage.setScene(scene);
        trayController = new MacTrayController(stage, services, () -> requestApplicationQuit(services, "Tray Quit"));
        boolean trayStarted = trayController.start();
        if (trayStarted) {
            MacDesktopIntegration.configureTrayWindow(stage);
        }
        MacDesktopIntegration.install(stage, trayController, services, () -> requestApplicationQuit(services, "Desktop QuitHandler"));
        stage.show();
    }

    @Override
    public void stop() {
        if (trayController != null) {
            trayController.shutdownSync();
        }
        if (mainView != null) {
            mainView.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void requestApplicationQuit(AppServices services, String reason) {
        services.store().logLine("应用退出", reason);
        Thread quitThread = new Thread(() -> {
            try {
                if (trayController != null) {
                    trayController.shutdownSync();
                }
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        if (mainView != null) {
                            mainView.shutdown();
                        }
                        Platform.exit();
                    } finally {
                        latch.countDown();
                    }
                });
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    services.store().logLine("JavaFX 退出超时", reason);
                }
            } catch (Exception e) {
                services.store().logLine("退出失败", e.getMessage());
            } finally {
                System.exit(0);
            }
        }, "codex-quit");
        quitThread.setDaemon(true);
        quitThread.start();
    }
}
