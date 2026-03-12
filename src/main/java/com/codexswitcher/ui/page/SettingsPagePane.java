package com.codexswitcher.ui.page;

import com.codexswitcher.service.BaseSupport;
import com.codexswitcher.service.UpdateService;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;

public class SettingsPagePane extends PagePane {

    private final Label currentVersion = new Label("当前版本：" + BaseSupport.APP_VERSION);
    private final Label latestVersion = new Label("最新版本：-");
    private final Label statusLabel = new Label("状态：未检查");
    private final TextArea notesArea = Ui.readonlyArea(12);
    private final ImageView qrView = new ImageView();

    public SettingsPagePane(AppContext context) {
        super(context);
        qrView.setFitWidth(220);
        qrView.setFitHeight(220);
        qrView.setPreserveRatio(true);
        try (InputStream input = getClass().getResourceAsStream("/assets/developer_qr.png")) {
            if (input != null) {
                qrView.setImage(new Image(input));
            }
        } catch (Exception ignored) {
        }

        var checkButton = Ui.button("立即检查");
        checkButton.setOnAction(event -> checkUpdate());
        var openButton = Ui.button("打开发布页");
        openButton.setOnAction(event -> {
            try {
                BaseSupport.openUrl("https://github.com/" + BaseSupport.APP_REPO + "/releases/latest");
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });

        root.getChildren().addAll(
            Ui.title("检查更新"),
            Ui.card("版本信息", currentVersion, latestVersion, statusLabel),
            Ui.card("更新内容", notesArea),
            Ui.row(checkButton, openButton),
            Ui.card("开发者反馈", new Label("本工具永久免费开源，扫码联系开发者反馈 bug 或需求。"), qrView)
        );
    }

    @Override
    public void onShow() {
        checkUpdate();
    }

    private void checkUpdate() {
        context.runAsync(() -> context.services().updates().getLatestRelease(), release -> {
            latestVersion.setText("最新版本：" + release.version);
            UpdateService.CompareResult compare = context.services().updates().compare(BaseSupport.APP_VERSION, release.version);
            statusLabel.setText(compare.text);
            context.updateBadge(compare.gapCount);
            context.runAsync(() -> context.services().updates().getReleaseNotes(release.version), notes -> notesArea.setText(notes), error -> notesArea.setText("无法获取更新内容。"));
        }, error -> {
            latestVersion.setText("最新版本：获取失败");
            statusLabel.setText("状态：" + error.getMessage());
        });
    }
}
