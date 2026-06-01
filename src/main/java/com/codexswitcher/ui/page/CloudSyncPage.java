package com.codexswitcher.ui.page;

import com.codexswitcher.model.CloudSyncResult;
import com.codexswitcher.model.CloudSyncSettings;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class CloudSyncPage extends PagePane {

    private final Label unlockLabel = new Label();
    private final TextField serverUrlField = new TextField();
    private final CheckBox autoSyncCheckBox = new CheckBox("客户端启动时自动拉取并覆盖本地配置");
    private final Label statusLabel = new Label();

    public CloudSyncPage(AppContext context) {
        super(context);
        serverUrlField.setPromptText("例如：http://127.0.0.1:18080");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.add(new Label("服务端地址"), 0, 0);
        form.add(serverUrlField, 1, 0);
        form.add(new Label("同步模式"), 0, 1);
        form.add(autoSyncCheckBox, 1, 1);
        GridPane.setHgrow(serverUrlField, Priority.ALWAYS);

        var saveButton = Ui.button("保存");
        saveButton.setOnAction(event -> saveSettings());
        var syncButton = Ui.button("立即同步");
        syncButton.setOnAction(event -> syncNow());

        root.getChildren().addAll(
            Ui.title("云端同步"),
            Ui.card("解锁状态", unlockLabel),
            Ui.card("同步配置", form, Ui.row(saveButton, syncButton), statusLabel),
            Ui.card("说明",
                new Label("这里填写服务端完整地址，你可以自己决定使用哪个 IP 和端口。"),
                new Label("同步接口固定为：/api/client/sync"),
                new Label("同步成功后会覆盖本地账号列表，并立刻应用云端当前指定的中转站。"))
        );
    }

    @Override
    public void onShow() {
        loadSettings();
    }

    @Override
    public void refreshStateOnly() {
        unlockLabel.setText(context.state().getCloudSyncSettings().isUnlocked() ? "已解锁" : "未解锁");
        statusLabel.setText(context.state().getCloudSyncStatus());
    }

    private void loadSettings() {
        CloudSyncSettings settings = context.state().getCloudSyncSettings();
        unlockLabel.setText(settings.isUnlocked() ? "已解锁" : "未解锁");
        serverUrlField.setText(settings.getServerUrl());
        autoSyncCheckBox.setSelected(settings.isEnabled());
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
            context.services().store().saveCloudSyncSettings(settings);
            context.state().setCloudSyncSettings(settings);
            statusLabel.setText("同步中...");
            context.runAsync(() -> context.services().cloudSync().syncNow(settings.getServerUrl()), this::onSyncSuccess, error -> {
                String message = "同步失败：" + error.getMessage();
                context.state().setCloudSyncStatus(message);
                statusLabel.setText(message);
                Ui.error("失败", message);
            });
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
        CloudSyncSettings current = context.state().getCloudSyncSettings();
        CloudSyncSettings settings = new CloudSyncSettings();
        settings.setUnlocked(current.isUnlocked());
        settings.setEnabled(autoSyncCheckBox.isSelected());
        settings.setServerUrl(serverUrlField.getText() == null ? "" : serverUrlField.getText().trim());
        return settings;
    }
}
