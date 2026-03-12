package com.codexswitcher.ui.page;

import com.codexswitcher.model.Account;
import com.codexswitcher.service.BaseSupport;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpencodePage extends PagePane {

    private final Label localLabel = new Label("-");
    private final Label latestLabel = new Label("-");
    private final ComboBox<Account> accountBox = new ComboBox<>();
    private final TextArea editor = new TextArea();
    private Map<String, Object> rawConfig = new LinkedHashMap<>();

    public OpencodePage(AppContext context) {
        super(context);
        accountBox.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });
        accountBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });

        var applyButton = Ui.button("应用账号");
        applyButton.setOnAction(event -> applyAccount());
        var openButton = Ui.button("打开 opencode.json");
        openButton.setOnAction(event -> {
            try {
                BaseSupport.openPath(context.services().opencode().getConfigPath().getParent());
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });
        var saveButton = Ui.button("保存");
        saveButton.setOnAction(event -> saveConfig());
        editor.setPrefRowCount(18);
        root.getChildren().addAll(
            Ui.title("opencode 配置"),
            Ui.card("状态", localLabel, latestLabel),
            Ui.card("账号映射", Ui.row(accountBox, applyButton), Ui.row(openButton, saveButton)),
            Ui.card("opencode.json 内容", editor)
        );
    }

    @Override
    public void onShow() {
        context.runAsync(() -> context.services().store().buildAccounts(),
            accounts -> accountBox.setItems(FXCollections.observableArrayList(accounts)),
            error -> Ui.error("失败", error.getMessage()));
        context.runAsync(() -> context.services().opencode().getLocalVersion(context.services().codex()),
            info -> localLabel.setText("本机版本：" + info.version + " | 路径：" + info.path),
            error -> localLabel.setText("本机版本：获取失败"));
        context.runAsync(() -> context.services().opencode().getLatestVersion(),
            info -> latestLabel.setText("最新版本：" + info.version),
            error -> latestLabel.setText("最新版本：获取失败"));
        editor.setText("加载中...");
        context.runAsync(() -> {
            Map<String, Object> config = context.services().opencode().loadConfig();
            String masked = BaseSupport.JSON.writeValueAsString(context.services().opencode().maskApiKeys(config));
            return new Object[]{config, masked};
        }, result -> {
            rawConfig = BaseSupport.asMap(result[0]);
            editor.setText(String.valueOf(result[1]));
        }, error -> {
            rawConfig = new LinkedHashMap<>();
            editor.clear();
        });
    }

    private void applyAccount() {
        Account account = accountBox.getValue();
        if (account == null) {
            Ui.warn("提示", "请选择账号");
            return;
        }
        try {
            rawConfig = context.services().opencode().updateConfigWithAccount(rawConfig, account);
            editor.setText(BaseSupport.JSON.writeValueAsString(context.services().opencode().maskApiKeys(rawConfig)));
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            Map<String, Object> parsed = BaseSupport.JSON.readValue(editor.getText(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
            });
            Object restored = context.services().opencode().restoreApiKeys(parsed, rawConfig);
            context.services().opencode().saveConfig(BaseSupport.asMap(restored));
            Ui.info("完成", "opencode.json 已保存");
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }
}
