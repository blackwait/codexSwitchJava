package com.codexswitcher.ui.page;

import com.codexswitcher.model.CloudSyncResult;
import com.codexswitcher.model.CloudSyncSettings;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.net.URI;

public class CloudSyncPage extends PagePane {

    private final TextField serverUrlField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label syncStatusLabel = new Label();
    private final Button loginButton = Ui.button("登录");
    private final Button logoutButton = Ui.button("退出登录");
    private final Button pullButton = Ui.button("从云端拉取");
    private final Button pushButton = Ui.button("推送到云端");
    private String serverScheme = "http";

    public CloudSyncPage(AppContext context) {
        super(context);
        serverUrlField.setPromptText(CloudSyncSettings.DEFAULT_SERVER_URL);
        usernameField.setPromptText("云端账号");
        passwordField.setPromptText("密码");

        loginButton.setOnAction(event -> login());
        logoutButton.setOnAction(event -> logout());
        pullButton.setOnAction(event -> pullFromCloud());
        pushButton.setOnAction(event -> pushToCloud());

        GridPane syncForm = new GridPane();
        syncForm.setHgap(10);
        syncForm.setVgap(12);
        syncForm.add(new Label("服务端地址"), 0, 0);
        syncForm.add(serverUrlField, 1, 0);
        syncForm.add(new Label("账号"), 0, 1);
        syncForm.add(usernameField, 1, 1);
        syncForm.add(new Label("密码"), 0, 2);
        syncForm.add(passwordField, 1, 2);
        GridPane.setHgrow(serverUrlField, Priority.ALWAYS);
        GridPane.setHgrow(usernameField, Priority.ALWAYS);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);

        root.getChildren().addAll(
            Ui.title("云端同步"),
            Ui.card("云端账号", syncForm, Ui.row(loginButton, logoutButton, pullButton, pushButton), syncStatusLabel),
            Ui.card("说明",
                new Label("服务端地址、账号、密码正确后即可登录，登录成功后可拉取或推送账号配置。"),
                new Label("登录态保存在本地 ~/.codex/codex_profiles.json。"),
                new Label("拉取会覆盖本地账号；推送会上传当前全部账号到云端。"))
        );
        refreshActions();
    }

    @Override
    public void onShow() {
        loadSettings();
        refreshActions();
    }

    @Override
    public void refreshStateOnly() {
        refreshActions();
        syncStatusLabel.setText(context.state().getCloudSyncStatus());
    }

    private void refreshActions() {
        boolean loggedIn = context.state().getCloudSyncSettings().isLoggedIn();
        loginButton.setDisable(loggedIn);
        logoutButton.setDisable(!loggedIn);
        pullButton.setDisable(!loggedIn);
        pushButton.setDisable(!loggedIn);
    }

    private void loadSettings() {
        CloudSyncSettings settings = context.state().getCloudSyncSettings();
        applyServerUrl(settings.getServerUrl());
        if (!settings.getAuthSession().getUsername().isBlank()) {
            usernameField.setText(settings.getAuthSession().getUsername());
        }
        syncStatusLabel.setText(context.state().getCloudSyncStatus());
        passwordField.clear();
    }

    private void login() {
        String serverUrl = resolveServerUrlOrWarn();
        if (serverUrl == null) {
            return;
        }
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (username.isBlank() || password.isBlank()) {
            Ui.warn("提示", "请填写登录账号和密码");
            return;
        }
        syncStatusLabel.setText("登录中...");
        context.runAsync(
            () -> context.services().cloudAuth().login(serverUrl, username, password),
            session -> {
                try {
                    CloudSyncSettings settings = buildSettings(serverUrl);
                    settings.setAuthSession(session);
                    context.services().store().saveCloudSyncSettings(settings);
                    context.state().setCloudSyncSettings(settings);
                    context.state().setCloudSyncStatus("已登录：" + session.getUsername());
                    passwordField.clear();
                    syncStatusLabel.setText(context.state().getCloudSyncStatus());
                    refreshActions();
                    Ui.info("完成", "登录成功");
                } catch (Exception e) {
                    syncStatusLabel.setText("保存登录态失败：" + e.getMessage());
                    Ui.error("失败", e.getMessage());
                }
            },
            error -> {
                syncStatusLabel.setText(error.getMessage());
                Ui.error("登录失败", error.getMessage());
            }
        );
    }

    private void logout() {
        try {
            context.services().store().clearCloudAuthSession();
            CloudSyncSettings settings = context.state().getCloudSyncSettings();
            settings.getAuthSession().clear();
            context.state().setCloudSyncSettings(settings);
            context.state().setCloudSyncStatus("已退出登录");
            passwordField.clear();
            syncStatusLabel.setText(context.state().getCloudSyncStatus());
            refreshActions();
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void pullFromCloud() {
        runSyncAction(true);
    }

    private void pushToCloud() {
        runSyncAction(false);
    }

    private void runSyncAction(boolean pull) {
        try {
            String serverUrl = resolveServerUrlOrWarn();
            if (serverUrl == null) {
                return;
            }
            CloudSyncSettings settings = buildSettings(serverUrl);
            if (!settings.isLoggedIn()) {
                Ui.warn("提示", "请先登录");
                return;
            }
            context.services().store().saveCloudSyncSettings(settings);
            context.state().setCloudSyncSettings(settings);
            syncStatusLabel.setText(pull ? "拉取中..." : "推送中...");
            String token = settings.getAuthSession().getToken();
            context.runAsync(
                () -> pull
                    ? context.services().cloudSync().pull(settings.getServerUrl(), settings.getProjectName(), token)
                    : context.services().cloudSync().push(settings.getServerUrl(), settings.getProjectName(), token),
                result -> onSyncSuccess(result, pull),
                error -> onSyncError(error.getMessage())
            );
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void onSyncSuccess(CloudSyncResult result, boolean pull) {
        if (pull) {
            context.state().setActiveAccount(context.services().store().getActiveAccount());
            context.refreshAll();
        }
        context.state().setCloudSyncStatus(result.getMessage());
        syncStatusLabel.setText(result.getMessage());
        Ui.info("完成", result.getMessage());
    }

    private void onSyncError(String message) {
        String text = message.startsWith("登录") ? message : (message.contains("401") ? "登录已失效，请重新登录" : message);
        context.state().setCloudSyncStatus(text);
        syncStatusLabel.setText(text);
        if (text.contains("登录")) {
            try {
                context.services().store().clearCloudAuthSession();
                context.state().getCloudSyncSettings().getAuthSession().clear();
                refreshActions();
            } catch (Exception ignored) {
            }
        }
        Ui.error("失败", text);
    }

    private CloudSyncSettings buildSettings(String serverUrl) {
        CloudSyncSettings current = context.state().getCloudSyncSettings();
        CloudSyncSettings settings = new CloudSyncSettings();
        settings.setEnabled(false);
        settings.setAuthSession(current.getAuthSession());
        settings.setServerUrl(serverUrl.isBlank() ? CloudSyncSettings.DEFAULT_SERVER_URL : serverUrl);
        settings.setProjectName(CloudSyncSettings.DEFAULT_PROJECT_NAME);
        return settings;
    }

    private void applyServerUrl(String serverUrl) {
        ServerEndpoint endpoint = parseServerUrl(serverUrl);
        serverScheme = endpoint.scheme();
        serverUrlField.setText(endpoint.scheme() + "://" + endpoint.host() + ":" + endpoint.port());
    }

    private String resolveServerUrlOrWarn() {
        String value = serverUrlField.getText() == null ? "" : serverUrlField.getText().trim();
        if (value.isBlank()) {
            Ui.warn("提示", "请填写服务端地址");
            return null;
        }
        ServerEndpoint endpoint = parseServerUrl(value);
        try {
            int portValue = Integer.parseInt(endpoint.port());
            if (portValue < 1 || portValue > 65535) {
                Ui.warn("提示", "端口需在 1 到 65535 之间");
                return null;
            }
        } catch (NumberFormatException e) {
            Ui.warn("提示", "端口只能填写数字");
            return null;
        }
        serverScheme = endpoint.scheme();
        serverUrlField.setText(endpoint.scheme() + "://" + endpoint.host() + ":" + endpoint.port());
        return endpoint.scheme() + "://" + endpoint.host() + ":" + endpoint.port();
    }

    private ServerEndpoint parseServerUrl(String serverUrl) {
        String value = serverUrl == null ? "" : serverUrl.trim();
        if (value.isBlank()) {
            value = CloudSyncSettings.DEFAULT_SERVER_URL;
        }
        String candidate = value.contains("://") ? value : "http://" + value;
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme() == null || uri.getScheme().isBlank() ? "http" : uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || host.isBlank()) {
                return fallbackEndpoint(value);
            }
            String resolvedPort = port > 0 ? String.valueOf(port) : "";
            return new ServerEndpoint(scheme, host, resolvedPort.isBlank() ? fallbackEndpoint(value).port() : resolvedPort);
        } catch (IllegalArgumentException e) {
            return fallbackEndpoint(value);
        }
    }

    private ServerEndpoint fallbackEndpoint(String value) {
        String defaultPort = "8080";
        String host = trimScheme(value);
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }
        int colonIndex = host.lastIndexOf(':');
        if (colonIndex > 0 && colonIndex < host.length() - 1) {
            defaultPort = host.substring(colonIndex + 1);
            host = host.substring(0, colonIndex);
        }
        if (host.isBlank()) {
            host = "118.24.80.208";
        }
        return new ServerEndpoint(serverScheme == null || serverScheme.isBlank() ? "http" : serverScheme, host, defaultPort);
    }

    private String trimScheme(String value) {
        String text = value == null ? "" : value.trim();
        int schemeIndex = text.indexOf("://");
        if (schemeIndex >= 0) {
            serverScheme = text.substring(0, schemeIndex);
            return text.substring(schemeIndex + 3);
        }
        return text;
    }

    private record ServerEndpoint(String scheme, String host, String port) {
    }
}
