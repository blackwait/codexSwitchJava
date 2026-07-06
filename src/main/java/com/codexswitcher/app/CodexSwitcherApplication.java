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

public class CodexSwitcherApplication extends Application {

    private MainView mainView;
    private MacTrayController trayController;

    @Override
    public void start(Stage stage) {
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
        trayController = new MacTrayController(stage, services);
        if (trayController.start()) {
            Platform.setImplicitExit(false);
            stage.setOnCloseRequest(event -> {
                event.consume();
                stage.hide();
            });
        }
        stage.show();
    }

    @Override
    public void stop() {
        if (trayController != null) {
            trayController.shutdown();
        }
        if (mainView != null) {
            mainView.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
