package com.codexswitcher.ui.page;

import com.codexswitcher.service.BaseSupport;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.scene.control.TextArea;

public class OpenAiStatusPagePane extends PagePane {

    private final TextArea area = Ui.readonlyArea(22);

    public OpenAiStatusPagePane(AppContext context) {
        super(context);
        var refreshButton = Ui.button("刷新状态");
        refreshButton.setOnAction(event -> refresh());
        var openButton = Ui.button("打开 status.openai.com");
        openButton.setOnAction(event -> {
            try {
                BaseSupport.openUrl("https://status.openai.com");
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });
        root.getChildren().addAll(Ui.title("OpenAI官网状态"), Ui.card("OpenAI 官网状态", area), Ui.row(refreshButton, openButton));
    }

    @Override
    public void onShow() {
        refresh();
    }

    private void refresh() {
        area.setText("正在获取 OpenAI 状态...");
        context.runAsync(() -> context.services().openAiStatus().getStatusHtml(),
            html -> area.setText(html.replaceAll("<br>", "\n").replaceAll("<[^>]+>", "")),
            error -> area.setText("获取失败：" + error.getMessage()));
    }
}
