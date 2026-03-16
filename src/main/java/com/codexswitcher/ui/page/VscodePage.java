package com.codexswitcher.ui.page;

import com.codexswitcher.model.ExtensionInfo;
import com.codexswitcher.service.BaseSupport;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

import java.nio.file.Path;

public class VscodePage extends PagePane {

    private final TextField installField = Ui.readonlyField();
    private final TextField workspaceField = Ui.readonlyField();
    private final ComboBox<ExtensionInfo> extensionBox = new ComboBox<>();
    private final Label extensionPathLabel = new Label("-");
    private final Label extensionVersionLabel = new Label("-");
    private final Label latestVersionLabel = new Label("-");
    private final Label statusLabel = new Label();
    private Path installDir;
    private Path workspaceDir;

    public VscodePage(AppContext context) {
        super(context);
        installDir = context.state().getVscodeInstallDir();
        extensionBox.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(ExtensionInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplayName());
            }
        });
        extensionBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ExtensionInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplayName());
            }
        });
        extensionBox.valueProperty().addListener((obs, oldValue, newValue) -> selectExtension(newValue));

        var chooseInstall = Ui.button("选择目录");
        chooseInstall.setOnAction(event -> chooseInstallDir());
        var chooseWorkspace = Ui.button("选择工作区");
        chooseWorkspace.setOnAction(event -> chooseWorkspace());
        var launchButton = Ui.button("一键启动 VS Code");
        launchButton.setOnAction(event -> launch(false));
        var fixButton = Ui.button("WebView错误修改");
        fixButton.setOnAction(event -> launch(true));
        var scanButton = Ui.button("扫描插件");
        scanButton.setOnAction(event -> scanExtensions());
        var openButton = Ui.button("打开插件目录");
        openButton.setOnAction(event -> openExtensionFolder());
        var disableUpdate = Ui.button("关闭自动更新");
        disableUpdate.setOnAction(event -> disableAutoUpdate());

        root.getChildren().addAll(
            Ui.title("VSCode Codex"),
            Ui.card("VS Code Codex 启动", Ui.row(new Label("VS Code 安装目录"), installField, chooseInstall),
                Ui.row(new Label("工作区"), workspaceField, chooseWorkspace),
                Ui.row(launchButton, fixButton)),
            Ui.row(scanButton, openButton, disableUpdate),
            Ui.card("插件信息", Ui.row(new Label("插件目录"), extensionBox), extensionPathLabel, extensionVersionLabel, latestVersionLabel),
            Ui.card("状态", statusLabel)
        );
    }

    @Override
    public void onShow() {
        detectInstallDirIfNeeded();
        installField.setText(installDir == null ? "" : installDir.toString());
        workspaceField.setText(workspaceDir == null ? "" : workspaceDir.toString());
        scanExtensions();
    }

    private void chooseInstallDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        if (installDir != null) {
            chooser.setInitialDirectory(installDir.toFile());
        }
        var file = chooser.showDialog(getScene().getWindow());
        if (file != null) {
            installDir = context.services().vscode().normalizeInstallDir(file.toPath());
            persistInstallDir();
            installField.setText(installDir == null ? "" : installDir.toString());
            scanExtensions();
        }
    }

    private void chooseWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        var file = chooser.showDialog(getScene().getWindow());
        if (file != null) {
            workspaceDir = file.toPath();
            workspaceField.setText(workspaceDir.toString());
        }
    }

    private void launch(boolean fixWebview) {
        if (workspaceDir == null) {
            Ui.warn("提示", "请先选择工作区");
            return;
        }
        try {
            if (fixWebview) {
                context.services().vscode().fixWebviewAndLaunch(installDir, workspaceDir, "", context.services().codex());
            } else {
                context.services().vscode().launchVscode(installDir, workspaceDir, context.services().codex());
            }
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void scanExtensions() {
        latestVersionLabel.setText("最新版本：获取中...");
        context.runAsync(() -> context.services().vscode().findExtensions(context.state()), items -> {
            extensionBox.setItems(FXCollections.observableArrayList(items));
            if (!items.isEmpty()) {
                extensionBox.getSelectionModel().selectFirst();
            } else {
                extensionPathLabel.setText("路径：未发现 openai.chatgpt 扩展");
                extensionVersionLabel.setText("插件版本：-");
            }
        }, error -> Ui.error("失败", error.getMessage()));
        context.runAsync(() -> context.services().vscode().fetchMarketplaceMeta(), meta -> {
            latestVersionLabel.setText("最新版本：" + (meta == null ? "获取失败" : ("稳定版：" + BaseSupport.firstNonBlank(meta.latestStable, "-") + " | 预览版：" + BaseSupport.firstNonBlank(meta.latestPreview, "-"))));
        }, error -> latestVersionLabel.setText("最新版本：获取失败"));
    }

    private void selectExtension(ExtensionInfo info) {
        if (info == null) {
            return;
        }
        extensionPathLabel.setText("路径：" + info.getPath());
        extensionVersionLabel.setText("插件版本：" + info.getVersion());
    }

    private void openExtensionFolder() {
        ExtensionInfo info = extensionBox.getValue();
        if (info == null) {
            return;
        }
        try {
            BaseSupport.openPath(info.getPath());
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void disableAutoUpdate() {
        try {
            context.services().vscode().disableAutoUpdate();
            statusLabel.setText("已关闭本机 VS Code 扩展自动更新");
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void detectInstallDirIfNeeded() {
        if (installDir != null && installDir.toFile().exists()) {
            return;
        }
        Path detected = context.services().vscode().detectVscodeInstallDir(context.services().codex());
        if (detected == null) {
            return;
        }
        installDir = detected;
        persistInstallDir();
    }

    private void persistInstallDir() {
        context.state().setVscodeInstallDir(installDir);
        try {
            context.services().store().saveVscodeInstallDir(installDir);
        } catch (Exception ignored) {
        }
    }
}
