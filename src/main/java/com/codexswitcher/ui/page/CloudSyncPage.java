package com.codexswitcher.ui.page;

import com.codexswitcher.model.CloudSyncResult;
import com.codexswitcher.model.CloudSyncSettings;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class CloudSyncPage extends PagePane {

    private final TextField serverUrlField = new TextField();
    private final TextField projectNameField = new TextField();
    private final Label statusLabel = new Label();

    public CloudSyncPage(AppContext context) {
        super(context);
        serverUrlField.setPromptText(CloudSyncSettings.DEFAULT_SERVER_URL);
        projectNameField.setPromptText(CloudSyncSettings.DEFAULT_PROJECT_NAME);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.add(new Label("服务端地址"), 0, 0);
        form.add(serverUrlField, 1, 0);
        form.add(new Label("项目名称"), 0, 1);
        form.add(projectNameField, 1, 1);
        GridPane.setHgrow(serverUrlField, Priority.ALWAYS);
        GridPane.setHgrow(projectNameField, Priority.ALWAYS);

        var saveButton = Ui.button("保存");
        saveButton.setOnAction(event -> saveSettings());
        var syncButton = Ui.button("立即同步");
        syncButton.setOnAction(event -> syncNow());

        root.getChildren().addAll(
            Ui.title("云端同步"),
            Ui.card("同步配置", form, Ui.row(saveButton, syncButton), statusLabel),
            Ui.card("说明",
                new Label("默认服务端：" + CloudSyncSettings.DEFAULT_SERVER_URL),
                new Label("默认项目：" + CloudSyncSettings.DEFAULT_PROJECT_NAME),
                new Label("同步接口：/api/sync/{项目名称}"),
                new Label("仅在你点击「立即同步」时才会从云端拉取并覆盖本地账号。"))
        );
    }

    @Override
    public void onShow() {
        loadSettings();
    }

    @Override
    public void refreshStateOnly() {
        statusLabel.setText(context.state().getCloudSyncStatus());
    }

    private void loadSettings() {
        CloudSyncSettings settings = context.state().getCloudSyncSettings();
        serverUrlField.setText(settings.getServerUrl());
        projectNameField.setText(settings.getProjectName());
        statusLabel.setText(context.state().getCloudSyncStatus());
    }

    private void saveSettings() {
        try {
            CloudSyncSettings settings = buildSettings();
            context.services().store().saveCloudSyncSettings(settings);
            context.state().setCloudSyncSettings(settings);
            context.state().setCloudSyncStatus("云端配置已保存");
            statusLabel.setText(context.state().getCloudSyncStatus());
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void syncNow() {
        try {
            CloudSyncSettings settings = buildSettings();
            if (settings.getServerUrl().isBlank()) {
                Ui.warn("提示", "请先填写服务端地址");
                return;
            }
            if (settings.getProjectName().isBlank()) {
                Ui.warn("提示", "请先填写项目名称");
                return;
            }
            context.services().store().saveCloudSyncSettings(settings);
            context.state().setCloudSyncSettings(settings);
            statusLabel.setText("同步中...");
            context.runAsync(
                () -> context.services().cloudSync().syncNow(settings.getServerUrl(), settings.getProjectName()),
                this::onSyncSuccess,
                error -> {
                    String message = "同步失败：" + error.getMessage();
                    context.state().setCloudSyncStatus(message);
                    statusLabel.setText(message);
                    Ui.error("失败", message);
                }
            );
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void onSyncSuccess(CloudSyncResult result) {
        context.state().setActiveAccount(context.services().store().getActiveAccount());
        context.state().setCloudSyncStatus(result.getMessage());
        statusLabel.setText(result.getMessage());
        context.refreshAll();
        Ui.info("完成", result.getMessage());
    }

    private CloudSyncSettings buildSettings() {
        CloudSyncSettings settings = new CloudSyncSettings();
        settings.setEnabled(false);
        String serverUrl = serverUrlField.getText() == null ? "" : serverUrlField.getText().trim();
        settings.setServerUrl(serverUrl.isBlank() ? CloudSyncSettings.DEFAULT_SERVER_URL : serverUrl);
        String projectName = projectNameField.getText() == null ? "" : projectNameField.getText().trim();
        settings.setProjectName(projectName.isBlank() ? CloudSyncSettings.DEFAULT_PROJECT_NAME : projectName);
        return settings;
    }
}
