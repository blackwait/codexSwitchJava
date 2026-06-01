package com.codexswitcher.ui.page;

import com.codexswitcher.service.BaseSupport;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class ConfigPage extends PagePane {

    private final Label codexPathLabel = new Label("-");
    private final Label configPathLabel = new Label(BaseSupport.CONFIG_PATH.toString());
    private final TextArea editor = new TextArea();
    private final Label statusLabel = new Label();

    public ConfigPage(AppContext context) {
        super(context);
        editor.setPrefRowCount(20);
        var reloadButton = Ui.button("重新读取");
        reloadButton.setOnAction(event -> loadConfig());
        var openButton = Ui.button("打开所在目录");
        openButton.setOnAction(event -> {
            try {
                BaseSupport.openPath(BaseSupport.CONFIG_PATH.getParent());
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });
        var saveButton = Ui.button("保存");
        saveButton.setOnAction(event -> {
            try {
                String content = BaseSupport.isBlank(editor.getText())
                    ? BaseSupport.defaultConfigToml()
                    : editor.getText();
                BaseSupport.writeText(BaseSupport.CONFIG_PATH, content);
                editor.setText(content);
                statusLabel.setText("已保存：" + BaseSupport.CONFIG_PATH);
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });
        root.getChildren().addAll(
            Ui.title("config.toml"),
            Ui.card("文件信息", codexPathLabel, configPathLabel),
            Ui.row(reloadButton, openButton, saveButton),
            Ui.card("内容（可手动修改）", editor),
            statusLabel
        );
    }

    @Override
    public void onShow() {
        loadConfig();
    }

    private void loadConfig() {
        codexPathLabel.setText("Codex 路径：" + BaseSupport.firstNonBlank(context.state().getCodexPath(), "-"));
        configPathLabel.setText("config.toml 路径：" + BaseSupport.CONFIG_PATH);
        statusLabel.setText("加载中...");
        context.runAsync(() -> BaseSupport.readText(BaseSupport.CONFIG_PATH), text -> {
            if (BaseSupport.isBlank(text)) {
                editor.setText(BaseSupport.defaultConfigToml());
                statusLabel.setText("已加载默认模板（未保存）");
                return;
            }
            editor.setText(text);
            statusLabel.setText("已加载");
        }, error -> {
            editor.clear();
            statusLabel.setText("加载失败：" + error.getMessage());
        });
    }
}
