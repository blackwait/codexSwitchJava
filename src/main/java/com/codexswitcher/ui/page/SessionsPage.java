package com.codexswitcher.ui.page;

import com.codexswitcher.model.SessionMeta;
import com.codexswitcher.service.BaseSupport;
import com.codexswitcher.service.SessionService;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SessionsPage extends PagePane {

    private final TextField searchField = new TextField();
    private final ComboBox<String> modeBox = new ComboBox<>(FXCollections.observableArrayList("OR（任一命中）", "AND（全部命中）"));
    private final Spinner<Integer> limitSpinner = new Spinner<>(1, 10000, 200);
    private final Spinner<Integer> daysSpinner = new Spinner<>(1, 3650, 90);
    private final ListView<SessionMeta> listView = new ListView<>();
    private final TextArea detailArea = new TextArea();
    private final CheckBox onlyUaCheck = new CheckBox("仅显示 user/assistant");
    private final ComboBox<String> cleanMode = new ComboBox<>(FXCollections.observableArrayList("按日期（早于）", "按大小（大于）"));
    private final DatePicker cleanDate = new DatePicker(LocalDate.now());
    private final Spinner<Integer> cleanSize = new Spinner<>(1, 10240, 100);
    private final CheckBox cleanHistory = new CheckBox("同时清理 history.jsonl");
    private List<SessionMeta> sessions = new ArrayList<>();
    private Map<String, String> historyIndex = new LinkedHashMap<>();

    public SessionsPage(AppContext context) {
        super(context);
        searchField.setPromptText("关键词过滤（history 优先，无命中时深度扫描）");
        onlyUaCheck.setSelected(true);
        cleanHistory.setSelected(true);
        modeBox.getSelectionModel().selectFirst();
        cleanMode.getSelectionModel().selectFirst();
        detailArea.setPrefRowCount(18);
        detailArea.setWrapText(true);

        var refreshButton = Ui.button("刷新索引");
        refreshButton.setOnAction(event -> loadSessions());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        modeBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        onlyUaCheck.selectedProperty().addListener((obs, oldValue, newValue) -> renderSelected());

        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(SessionMeta item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getListText());
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> renderSelected());
        listView.setContextMenu(buildContextMenu());

        var exportJson = Ui.button("导出 JSON");
        exportJson.setOnAction(event -> exportJson());
        var exportMarkdown = Ui.button("导出 Markdown");
        exportMarkdown.setOnAction(event -> exportMarkdown());
        var cleanupButton = Ui.button("执行清理");
        cleanupButton.setOnAction(event -> cleanup());

        VBox left = Ui.card("检索", Ui.row(searchField, refreshButton), Ui.row(new javafx.scene.control.Label("模式"), modeBox,
            new javafx.scene.control.Label("最多扫描"), limitSpinner, new javafx.scene.control.Label("最近"), daysSpinner), listView);
        left.setPrefWidth(420);
        VBox right = Ui.card("详情", onlyUaCheck, detailArea, Ui.row(exportJson, exportMarkdown));
        HBox body = new HBox(12, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);

        root.getChildren().addAll(Ui.title("Codex会话管理"), body, Ui.card("统一清理", Ui.row(cleanMode, cleanDate, cleanSize, cleanHistory, cleanupButton)));
    }

    @Override
    public void onShow() {
        loadSessions();
    }

    private void loadSessions() {
        context.runAsync(() -> {
            List<SessionMeta> loaded = context.services().sessions().loadSessions();
            Map<String, String> history = context.services().sessions().loadHistoryIndex();
            return new Object[]{loaded, history};
        }, result -> {
            sessions = (List<SessionMeta>) result[0];
            historyIndex = (Map<String, String>) result[1];
            applyFilter();
        }, error -> Ui.error("失败", error.getMessage()));
    }

    private void applyFilter() {
        SessionService.SearchTerms terms = context.services().sessions().parseKeywords(searchField.getText(), modeBox.getSelectionModel().getSelectedIndex() == 1);
        if (terms.terms.isEmpty()) {
            listView.setItems(FXCollections.observableArrayList(sessions));
            return;
        }
        List<SessionMeta> matched = sessions.stream()
            .filter(session -> context.services().sessions().matchText(historyIndex.get(session.getId()), terms.terms, terms.mode))
            .collect(Collectors.toList());
        if (matched.isEmpty()) {
            List<SessionMeta> deep = context.services().sessions().selectDeepCandidates(sessions, daysSpinner.getValue(), limitSpinner.getValue()).stream()
                .filter(session -> context.services().sessions().sessionContainsTerms(session.getPath(), terms.terms, terms.mode))
                .collect(Collectors.toList());
            listView.setItems(FXCollections.observableArrayList(deep));
        } else {
            listView.setItems(FXCollections.observableArrayList(matched));
        }
    }

    private void renderSelected() {
        SessionMeta meta = listView.getSelectionModel().getSelectedItem();
        if (meta == null) {
            return;
        }
        detailArea.setText(context.services().sessions().buildRenderedText(meta, onlyUaCheck.isSelected()));
    }

    private ContextMenu buildContextMenu() {
        MenuItem resumeCli = new MenuItem("继续该会话（Codex CLI）");
        resumeCli.setOnAction(event -> resumeCli());
        MenuItem resumeVscode = new MenuItem("继续该会话（VS Code）");
        resumeVscode.setOnAction(event -> resumeVscode(false));
        MenuItem repair = new MenuItem("WebView 修复");
        repair.setOnAction(event -> resumeVscode(true));
        MenuItem openFolder = new MenuItem("打开文件夹");
        openFolder.setOnAction(event -> openFolder());
        MenuItem delete = new MenuItem("删除该会话");
        delete.setOnAction(event -> deleteSession());
        return new ContextMenu(resumeCli, resumeVscode, repair, openFolder, delete);
    }

    private void openFolder() {
        SessionMeta meta = listView.getSelectionModel().getSelectedItem();
        if (meta == null) {
            return;
        }
        try {
            BaseSupport.openPath(meta.getPath().getParent());
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void deleteSession() {
        SessionMeta meta = listView.getSelectionModel().getSelectedItem();
        if (meta == null) {
            return;
        }
        if (!Ui.confirm("确认", "确认删除该会话文件吗？")) {
            return;
        }
        try {
            Files.deleteIfExists(meta.getPath());
            context.services().sessions().cleanupHistory(Set.of(meta.getId()));
            loadSessions();
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void resumeCli() {
        SessionMeta meta = listView.getSelectionModel().getSelectedItem();
        if (meta == null) {
            return;
        }
        try {
            context.services().sessions().resumeSessionCli(meta, context.services().codex());
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void resumeVscode(boolean fixWebview) {
        SessionMeta meta = listView.getSelectionModel().getSelectedItem();
        if (meta == null || meta.getCwd() == null || meta.getCwd().isBlank()) {
            Ui.warn("提示", "当前会话缺少工作目录");
            return;
        }
        try {
            Path workspace = Path.of(meta.getCwd());
            if (fixWebview) {
                context.services().vscode().fixWebviewAndLaunch(context.state().getVscodeInstallDir(), workspace, meta.getId(), context.services().codex());
            } else {
                context.services().vscode().launchSessionInVscode(context.state().getVscodeInstallDir(), workspace, meta.getId(), context.services().codex());
            }
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void exportJson() {
        SessionMeta meta = listView.getSelectionModel().getSelectedItem();
        if (meta == null) {
            Ui.warn("提示", "请先选择会话");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("session.json");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            BaseSupport.writeJson(file.toPath(), context.services().sessions().exportJsonPayload(meta, onlyUaCheck.isSelected()));
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void exportMarkdown() {
        SessionMeta meta = listView.getSelectionModel().getSelectedItem();
        if (meta == null) {
            Ui.warn("提示", "请先选择会话");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("session.md");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            BaseSupport.writeText(file.toPath(), context.services().sessions().buildRenderedText(meta, onlyUaCheck.isSelected()));
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void cleanup() {
        SessionService.CleanupResult result = context.services().sessions().cleanup(
            sessions,
            cleanMode.getSelectionModel().getSelectedIndex() == 0,
            cleanDate.getValue(),
            cleanSize.getValue(),
            cleanHistory.isSelected()
        );
        Ui.info("完成", "已删除会话：" + result.deletedSessions + "，释放大小：" + String.format("%.1f MB", result.deletedBytes / 1024.0 / 1024.0));
        loadSessions();
    }
}
