package com.codexswitcher.ui;

import com.codexswitcher.app.AppState;
import com.codexswitcher.service.AppServices;
import com.codexswitcher.ui.page.AccountPage;
import com.codexswitcher.ui.page.CodexStatusPage;
import com.codexswitcher.ui.page.CloudSyncPage;
import com.codexswitcher.ui.page.ConfigPage;
import com.codexswitcher.ui.page.NetworkPage;
import com.codexswitcher.ui.page.OpenAiStatusPagePane;
import com.codexswitcher.ui.page.OpencodePage;
import com.codexswitcher.ui.page.SessionsPage;
import com.codexswitcher.ui.page.SkillsPagePane;
import com.codexswitcher.ui.page.VscodePage;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainView extends BorderPane {

    private static final String CLOUD_SYNC_KEY = "cloud_sync";

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, PagePane> pages = new HashMap<>();
    private final Map<String, ToggleButton> buttons = new HashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane stack = new StackPane();
    private final VBox nav = new VBox(10);
    private final AppContext context;
    private String activePageKey;

    public MainView(AppState state, AppServices services) {
        getStyleClass().add("app-root");
        context = new AppContext(state, services, executor, this::refreshAllPages, this::showPage, count -> {
        });

        VBox frame = new VBox(12);
        frame.getStyleClass().add("frame-root");

        HBox toolbar = buildToolbar();
        HBox shell = new HBox(18);
        shell.getStyleClass().add("shell-root");

        nav.getStyleClass().add("nav-pane");

        stack.getStyleClass().add("content-stack");

        shell.getChildren().addAll(nav, stack);
        HBox.setHgrow(stack, Priority.ALWAYS);
        frame.getChildren().addAll(toolbar, shell);
        setCenter(frame);

        pages.put("codex_status", new CodexStatusPage(context));
        pages.put("vscode", new VscodePage(context));
        pages.put("config", new ConfigPage(context));
        pages.put("opencode", new OpencodePage(context));
        pages.put("account", new AccountPage(context));
        pages.put(CLOUD_SYNC_KEY, new CloudSyncPage(context));
        pages.put("sessions", new SessionsPage(context));
        pages.put("skills", new SkillsPagePane(context));
        pages.put("network", new NetworkPage(context));
        pages.put("openai", new OpenAiStatusPagePane(context));

        addNav(nav, "Codex CLI状态", "codex_status");
        addNav(nav, "VSCode Codex", "vscode");
        addNav(nav, "config.toml配置", "config");
        addNav(nav, "opencode 配置", "opencode");
        addNav(nav, "多账号切换", "account");
        addNav(nav, "云端同步", CLOUD_SYNC_KEY);
        addNav(nav, "Codex会话管理", "sessions");
        addNav(nav, "Skill 管理", "skills");
        addNav(nav, "中转站接口", "network");
        addNav(nav, "OpenAI官网状态", "openai");

        showPage("account");
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void addNav(VBox nav, String text, String key) {
        if (buttons.containsKey(key)) {
            return;
        }
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("nav-button");
        Ui.decorateActionIcon(button, text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setToggleGroup(navGroup);
        button.setOnAction(event -> showPage(key));
        nav.getChildren().add(button);
        buttons.put(key, button);
    }

    private void showPage(String key) {
        if (key != null && key.equals(activePageKey)) {
            return;
        }
        PagePane page = pages.get(key);
        if (page == null) {
            return;
        }
        buttons.values().forEach(button -> button.getStyleClass().remove("active"));
        ToggleButton button = buttons.get(key);
        if (button != null) {
            button.setSelected(true);
            button.getStyleClass().add("active");
        }
        stack.getChildren().setAll(page);
        activePageKey = key;
        Platform.runLater(page::onShow);
    }

    private void refreshAllPages() {
        pages.values().forEach(PagePane::refreshStateOnly);
    }

    private HBox buildToolbar() {
        Label title = new Label("Codex Switcher");
        title.getStyleClass().add("toolbar-title");
        Label subtitle = new Label("本地切换 + 云端同步");
        subtitle.getStyleClass().add("toolbar-subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        HBox toolbar = new HBox(12, titleBox);
        toolbar.getStyleClass().add("toolbar-box");
        return toolbar;
    }
}
