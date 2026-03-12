package com.codexswitcher.ui.page;

import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

import java.nio.file.Path;

public class CodexStatusPage extends PagePane {

    private final Label localStatus = new Label("检测中...");
    private final Label localVersion = new Label("-");
    private final Label latestStatus = new Label("检测中...");
    private final Label latestVersion = new Label("-");
    private final Label compareLabel = new Label();
    private final TextField workspaceField = Ui.readonlyField();
    private final TextArea debugArea = Ui.readonlyArea(10);
    private Path workspaceDir;

    public CodexStatusPage(AppContext context) {
        super(context);
        var refreshButton = Ui.button("刷新检测");
        refreshButton.setOnAction(event -> refresh());
        var updateButton = Ui.button("一键更新");
        updateButton.setOnAction(event -> {
            try {
                context.services().codex().updateCodex();
                Ui.info("提示", "已打开更新终端。");
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });
        var chooseWorkspace = Ui.button("选择工作区");
        chooseWorkspace.setOnAction(event -> chooseWorkspace());
        var launchButton = Ui.button("一键启动 CODEX CLI");
        launchButton.setOnAction(event -> {
            try {
                context.services().codex().launchCodexCli(workspaceDir);
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });

        root.getChildren().addAll(
            Ui.title("Codex CLI状态"),
            Ui.row(refreshButton),
            Ui.card("本机 Codex CLI", localStatus, localVersion, new Label("安装命令：npm i -g @openai/codex")),
            Ui.card("官方最新版本", latestStatus, latestVersion, Ui.row(new Label("更新命令：npm i -g @openai/codex@latest"), updateButton), compareLabel),
            Ui.card("Codex CLI 一键启动", Ui.row(new Label("工作区"), workspaceField, chooseWorkspace), Ui.row(launchButton)),
            Ui.card("调试信息", debugArea)
        );
    }

    @Override
    public void onShow() {
        refresh();
    }

    private void refresh() {
        debugArea.setText("正在收集调试信息...");
        context.runAsync(() -> context.services().codex().buildDebugReport(),
            debugArea::setText,
            error -> debugArea.setText("调试信息获取失败：" + error.getMessage()));
        context.runAsync(() -> context.services().codex().getLocalVersion(), info -> {
            localStatus.setText(info.ok ? "已安装" : "未安装");
            localVersion.setText("路径：" + info.path + "\n版本：" + info.version);
            context.state().setCodexPath(info.path);
            context.state().setCodexVersion(info.version);
        }, error -> Ui.error("失败", error.getMessage()));
        context.runAsync(() -> context.services().codex().getLatestVersion(), info -> {
            latestStatus.setText(info.ok ? "可获取" : "获取失败");
            latestVersion.setText("版本：" + info.version);
            compareLabel.setText(context.services().codex().compareVersions(context.state().getCodexVersion(), info.version));
        }, error -> Ui.error("失败", error.getMessage()));
    }

    private void chooseWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        if (workspaceDir != null) {
            chooser.setInitialDirectory(workspaceDir.toFile());
        }
        var file = chooser.showDialog(getScene().getWindow());
        if (file != null) {
            workspaceDir = file.toPath();
            workspaceField.setText(workspaceDir.toString());
        }
    }
}
