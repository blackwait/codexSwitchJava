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

public class CloudSyncPage extends PagePane {

    private final VBox loginPanel = new VBox(12);
    private final VBox loggedInPanel = new VBox(12);

    private final TextField serverUrlField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label loginStatusLabel = new Label();

    private final Label userLabel = new Label();
    private final TextField projectNameField = new TextField();
    private final Label syncStatusLabel = new Label();

    public CloudSyncPage(AppContext context) {
        super(context);
        serverUrlField.setPromptText(CloudSyncSettings.DEFAULT_SERVER_URL);
        projectNameField.setPromptText(CloudSyncSettings.DEFAULT_PROJECT_NAME);
        usernameField.setPromptText("云端账号");
        passwordField.setPromptText("密码");

        GridPane loginForm = new GridPane();
        loginForm.setHgap(10);
        loginForm.setVgap(12);
        loginForm.add(new Label("服务端地址"), 0, 0);
        loginForm.add(serverUrlField, 1, 0);
        loginForm.add(new Label("用户名"), 0, 1);
        loginForm.add(usernameField, 1, 1);
        loginForm.add(new Label("密码"), 0, 2);
        loginForm.add(passwordField, 1, 2);
        GridPane.setHgrow(serverUrlField, Priority.ALWAYS);
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

        TextField loggedInServerField = new TextField();
        loggedInServerField.textProperty().bind(serverUrlField.textProperty());
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
        serverUrlField.setText(settings.getServerUrl());
        projectNameField.setText(settings.getProjectName());
        if (!settings.getAuthSession().getUsername().isBlank()) {
            usernameField.setText(settings.getAuthSession().getUsername());
        }
        syncStatusLabel.setText(context.state().getCloudSyncStatus());
        loginStatusLabel.setText("");
        passwordField.clear();
    }

    private void login() {
        String serverUrl = serverUrlField.getText() == null ? "" : serverUrlField.getText().trim();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (serverUrl.isBlank()) {
            Ui.warn("提示", "请填写服务端地址");
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            Ui.warn("提示", "请填写用户名和密码");
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
            CloudSyncSettings settings = buildSettings();
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
            CloudSyncSettings settings = buildSettings();
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
        CloudSyncSettings current = context.state().getCloudSyncSettings();
        CloudSyncSettings settings = new CloudSyncSettings();
        settings.setEnabled(false);
        settings.setAuthSession(current.getAuthSession());
        String serverUrl = serverUrlField.getText() == null ? "" : serverUrlField.getText().trim();
        settings.setServerUrl(serverUrl.isBlank() ? CloudSyncSettings.DEFAULT_SERVER_URL : serverUrl);
        String projectName = projectNameField.getText() == null ? "" : projectNameField.getText().trim();
        settings.setProjectName(projectName.isBlank() ? CloudSyncSettings.DEFAULT_PROJECT_NAME : projectName);
        return settings;
    }
}
