package com.codexswitcher.ui.page;

import com.codexswitcher.model.CloudAuthSession;
import com.codexswitcher.model.CloudSyncResult;
import com.codexswitcher.model.CloudSyncSettings;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URI;

public class CloudSyncPage extends PagePane {

    private final VBox loginPanel = new VBox(12);
    private final VBox loggedInPanel = new VBox(12);

    private final TextField serverHostField = new TextField();
    private final TextField serverPortField = new TextField();
    private final TextField loggedInServerField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label loginStatusLabel = new Label();

    private final Label userLabel = new Label();
    private final TextField projectNameField = new TextField();
    private final Label syncStatusLabel = new Label();
    private String serverScheme = "http";

    public CloudSyncPage(AppContext context) {
        super(context);
        ServerEndpoint defaultEndpoint = parseServerUrl(CloudSyncSettings.DEFAULT_SERVER_URL);
        serverHostField.setPromptText(defaultEndpoint.host());
        serverPortField.setPromptText(defaultEndpoint.port());
        projectNameField.setPromptText(CloudSyncSettings.DEFAULT_PROJECT_NAME);
        usernameField.setPromptText("云端账号");
        passwordField.setPromptText("密码");
        serverHostField.textProperty().addListener((obs, oldValue, newValue) -> updateLoggedInServerField());
        serverPortField.textProperty().addListener((obs, oldValue, newValue) -> updateLoggedInServerField());

        GridPane loginForm = new GridPane();
        loginForm.setHgap(10);
        loginForm.setVgap(12);
        loginForm.add(new Label("服务端地址"), 0, 0);
        loginForm.add(serverHostField, 1, 0);
        loginForm.add(new Label("端口"), 0, 1);
        loginForm.add(serverPortField, 1, 1);
        loginForm.add(new Label("登录账号"), 0, 2);
        loginForm.add(usernameField, 1, 2);
        loginForm.add(new Label("密码"), 0, 3);
        loginForm.add(passwordField, 1, 3);
        GridPane.setHgrow(serverHostField, Priority.ALWAYS);
        GridPane.setHgrow(serverPortField, Priority.ALWAYS);
        GridPane.setHgrow(usernameField, Priority.ALWAYS);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);

        var loginButton = Ui.button("登录");
        loginButton.setOnAction(event -> login());
        loginPanel.getChildren().addAll(
            Ui.card("云端登录", loginForm, Ui.row(loginButton), loginStatusLabel)
        );

        var logoutButton = Ui.button("退出登录");
        logoutButton.setOnAction(event -> logout());
        var saveButton = Ui.button("保存配置");
        saveButton.setOnAction(event -> saveSettings());
        var pullButton = Ui.button("从云端拉取");
        pullButton.setOnAction(event -> pullFromCloud());
        var pushButton = Ui.button("推送到云端");
        pushButton.setOnAction(event -> pushToCloud());

        loggedInServerField.setEditable(false);

        GridPane syncForm = new GridPane();
        syncForm.setHgap(10);
        syncForm.setVgap(12);
        syncForm.add(new Label("服务端地址"), 0, 0);
        syncForm.add(loggedInServerField, 1, 0);
        syncForm.add(new Label("项目名称"), 0, 1);
        syncForm.add(projectNameField, 1, 1);
        GridPane.setHgrow(loggedInServerField, Priority.ALWAYS);
        GridPane.setHgrow(projectNameField, Priority.ALWAYS);

        loggedInPanel.getChildren().addAll(
            Ui.card("当前账号", Ui.row(userLabel, Ui.spacer(), logoutButton)),
            Ui.card("同步操作", syncForm, Ui.row(saveButton, pullButton, pushButton), syncStatusLabel)
        );

        root.getChildren().addAll(
            Ui.title("云端同步"),
            loginPanel,
            loggedInPanel,
            Ui.card("说明",
                new Label("需先登录后才能拉取或推送账号配置。"),
                new Label("登录态保存在本地 ~/.codex/codex_profiles.json。"),
                new Label("拉取会覆盖本地账号；推送会上传当前全部账号到云端。"))
        );
        refreshPanels();
    }

    @Override
    public void onShow() {
        loadSettings();
        refreshPanels();
    }

    @Override
    public void refreshStateOnly() {
        refreshPanels();
        syncStatusLabel.setText(context.state().getCloudSyncStatus());
    }

    private void refreshPanels() {
        boolean loggedIn = context.state().getCloudSyncSettings().isLoggedIn();
        loginPanel.setVisible(!loggedIn);
        loginPanel.setManaged(!loggedIn);
        loggedInPanel.setVisible(loggedIn);
        loggedInPanel.setManaged(loggedIn);
        if (loggedIn) {
            CloudAuthSession session = context.state().getCloudSyncSettings().getAuthSession();
            userLabel.setText("已登录：" + session.getUsername());
        }
    }

    private void loadSettings() {
        CloudSyncSettings settings = context.state().getCloudSyncSettings();
        applyServerUrl(settings.getServerUrl());
        projectNameField.setText(settings.getProjectName());
        if (!settings.getAuthSession().getUsername().isBlank()) {
            usernameField.setText(settings.getAuthSession().getUsername());
        }
        syncStatusLabel.setText(context.state().getCloudSyncStatus());
        loginStatusLabel.setText("");
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
        loginStatusLabel.setText("登录中...");
        context.runAsync(
            () -> context.services().cloudAuth().login(serverUrl, username, password),
            session -> {
                try {
                    CloudSyncSettings settings = buildSettings();
                    settings.setAuthSession(session);
                    context.services().store().saveCloudSyncSettings(settings);
                    context.state().setCloudSyncSettings(settings);
                    context.state().setCloudSyncStatus("已登录：" + session.getUsername());
                    passwordField.clear();
                    loginStatusLabel.setText("");
                    syncStatusLabel.setText(context.state().getCloudSyncStatus());
                    refreshPanels();
                    Ui.info("完成", "登录成功");
                } catch (Exception e) {
                    loginStatusLabel.setText("保存登录态失败：" + e.getMessage());
                    Ui.error("失败", e.getMessage());
                }
            },
            error -> {
                loginStatusLabel.setText(error.getMessage());
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
            refreshPanels();
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void saveSettings() {
        try {
            String serverUrl = resolveServerUrlOrWarn();
            if (serverUrl == null) {
                return;
            }
            CloudSyncSettings settings = buildSettings(serverUrl);
            context.services().store().saveCloudSyncSettings(settings);
            context.state().setCloudSyncSettings(settings);
            context.state().setCloudSyncStatus("云端配置已保存");
            syncStatusLabel.setText(context.state().getCloudSyncStatus());
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
                refreshPanels();
            } catch (Exception ignored) {
            }
        }
        Ui.error("失败", text);
    }

    private CloudSyncSettings buildSettings() {
        String serverUrl = buildServerUrl();
        return buildSettings(serverUrl.isBlank() ? CloudSyncSettings.DEFAULT_SERVER_URL : serverUrl);
    }

    private CloudSyncSettings buildSettings(String serverUrl) {
        CloudSyncSettings current = context.state().getCloudSyncSettings();
        CloudSyncSettings settings = new CloudSyncSettings();
        settings.setEnabled(false);
        settings.setAuthSession(current.getAuthSession());
        settings.setServerUrl(serverUrl.isBlank() ? CloudSyncSettings.DEFAULT_SERVER_URL : serverUrl);
        String projectName = projectNameField.getText() == null ? "" : projectNameField.getText().trim();
        settings.setProjectName(projectName.isBlank() ? CloudSyncSettings.DEFAULT_PROJECT_NAME : projectName);
        return settings;
    }

    private void applyServerUrl(String serverUrl) {
        ServerEndpoint endpoint = parseServerUrl(serverUrl);
        serverScheme = endpoint.scheme();
        serverHostField.setText(endpoint.host());
        serverPortField.setText(endpoint.port());
        updateLoggedInServerField();
    }

    private void updateLoggedInServerField() {
        loggedInServerField.setText(buildServerUrl());
    }

    private String resolveServerUrlOrWarn() {
        String host = serverHostField.getText() == null ? "" : serverHostField.getText().trim();
        if (host.isBlank()) {
            Ui.warn("提示", "请填写服务端地址");
            return null;
        }
        String port = resolvePort(host, serverPortField.getText());
        if (port.isBlank()) {
            Ui.warn("提示", "请填写端口");
            return null;
        }
        try {
            int portValue = Integer.parseInt(port);
            if (portValue < 1 || portValue > 65535) {
                Ui.warn("提示", "端口需在 1 到 65535 之间");
                return null;
            }
        } catch (NumberFormatException e) {
            Ui.warn("提示", "端口只能填写数字");
            return null;
        }
        return buildServerUrl();
    }

    private String buildServerUrl() {
        String hostText = serverHostField.getText() == null ? "" : serverHostField.getText().trim();
        if (hostText.isBlank()) {
            return "";
        }
        ServerEndpoint endpoint = parseServerUrl(composeRawServerUrl(hostText, serverPortField.getText()));
        return endpoint.scheme() + "://" + endpoint.host() + ":" + endpoint.port();
    }

    private String composeRawServerUrl(String hostText, String portText) {
        String port = resolvePort(hostText, portText);
        String host = trimScheme(hostText);
        int colonIndex = host.lastIndexOf(':');
        if (colonIndex > 0) {
            host = host.substring(0, colonIndex);
        }
        return serverScheme + "://" + host + ":" + port;
    }

    private String resolvePort(String hostText, String portText) {
        String port = portText == null ? "" : portText.trim();
        if (!port.isBlank()) {
            return port;
        }
        ServerEndpoint endpoint = parseServerUrl(hostText);
        if (!endpoint.port().isBlank()) {
            return endpoint.port();
        }
        return parseServerUrl(CloudSyncSettings.DEFAULT_SERVER_URL).port();
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
