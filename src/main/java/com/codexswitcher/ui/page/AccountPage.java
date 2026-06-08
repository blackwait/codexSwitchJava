package com.codexswitcher.ui.page;

import com.codexswitcher.model.Account;
import com.codexswitcher.model.AccountProbeStatus;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class AccountPage extends PagePane {

    private static final String CLOUD_SYNC_PAGE_KEY = "cloud_sync";
    private static final String DEFAULT_TEST_MODEL = "gpt-5.3-codex";
    private static final int PROBE_TIMEOUT_SECONDS = 30;

    private final Label currentLabel = new Label("未选择");
    private final ListView<Account> listView = new ListView<>();
    private final TextField nameField = new TextField();
    private final TextField baseField = new TextField();
    private final TextField keyField = new TextField();
    private final TextField accountModelField = new TextField();
    private final TextField orgField = new TextField();
    private final TextField modelField = new TextField(DEFAULT_TEST_MODEL);
    private final RadioButton teamRadio = new RadioButton("Team 账号");
    private final RadioButton officialRadio = new RadioButton("ChatGPT 官方账号");
    private final RadioButton proxyRadio = new RadioButton("中转账号");
    private final Label statusLabel = new Label();
    private final Map<String, ProbeState> probeStates = new LinkedHashMap<>();

    public AccountPage(AppContext context) {
        super(context);
        root.getChildren().add(Ui.title("多账号切换"));
        root.getChildren().add(Ui.card("当前账号", currentLabel));

        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                ProbeState state = probeStates.getOrDefault(accountKey(item), ProbeState.idle());
                Circle light = new Circle(6, state.color());
                Label title = new Label(item.getDisplayText());
                title.setWrapText(true);
                Label sub = new Label(state.summary());
                sub.setStyle("-fx-font-size: 11px;");
                HBox row = new HBox(8, light, new VBox(2, title, sub));
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
                setText(null);
                setGraphic(row);
                setTooltip(state.detail().isBlank() ? null : new Tooltip(state.detail()));
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populate(newValue));

        ToggleGroup group = new ToggleGroup();
        teamRadio.setToggleGroup(group);
        officialRadio.setToggleGroup(group);
        proxyRadio.setToggleGroup(group);
        proxyRadio.setSelected(true);
        modelField.textProperty().addListener((obs, oldValue, newValue) -> persistNonBlankTestModelQuietly(newValue));
        modelField.focusedProperty().addListener((obs, oldValue, focused) -> {
            if (Boolean.FALSE.equals(focused)) {
                persistTestModelQuietly();
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("账号类型"), 0, 0);
        form.add(Ui.row(teamRadio, officialRadio, proxyRadio), 1, 0);
        form.add(new Label("名称"), 0, 1);
        form.add(nameField, 1, 1);
        form.add(new Label("Base URL"), 0, 2);
        form.add(baseField, 1, 2);
        form.add(new Label("API Key"), 0, 3);
        form.add(keyField, 1, 3);
        form.add(new Label("账号模型"), 0, 4);
        form.add(accountModelField, 1, 4);
        form.add(new Label("Org ID"), 0, 5);
        form.add(orgField, 1, 5);
        form.add(new Label("测试模型"), 0, 6);
        form.add(modelField, 1, 6);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(baseField, Priority.ALWAYS);
        GridPane.setHgrow(keyField, Priority.ALWAYS);
        GridPane.setHgrow(accountModelField, Priority.ALWAYS);
        GridPane.setHgrow(orgField, Priority.ALWAYS);
        GridPane.setHgrow(modelField, Priority.ALWAYS);

        var applyButton = Ui.button("应用账号");
        applyButton.setOnAction(event -> applySelected());
        var deleteButton = Ui.button("删除账号");
        deleteButton.setOnAction(event -> deleteSelected());
        var refreshButton = Ui.button("刷新");
        refreshButton.setOnAction(event -> loadAccounts());
        var exportButton = Ui.button("批量导出");
        exportButton.setOnAction(event -> exportAccounts());
        var importButton = Ui.button("批量导入");
        importButton.setOnAction(event -> importAccounts());
        var saveButton = Ui.button("保存/更新");
        saveButton.setOnAction(event -> saveAccount());
        var clearButton = Ui.button("清空");
        clearButton.setOnAction(event -> clearForm());
        var testButton = Ui.button("账号检测");
        testButton.setOnAction(event -> testAccount());
        var batchTestButton = Ui.button("批量检测全部");
        batchTestButton.setOnAction(event -> testAllAccounts());

        VBox left = Ui.card("账号列表", listView, Ui.row(applyButton, deleteButton, refreshButton), Ui.row(exportButton, importButton));
        left.setPrefWidth(360);
        VBox right = Ui.card("新增/更新账号", form, Ui.row(saveButton, clearButton, testButton, batchTestButton), statusLabel);
        HBox body = new HBox(12, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);

        root.getChildren().addAll(body, Ui.card("说明",
            new Label("保存后会自动写入 ~/.codex 下的 config.toml、auth.json 与激活账号指针。"),
            new Label("批量导入/导出使用 JSON 文件，导入时会全量替换本地账号列表。")));
    }

    @Override
    public void onShow() {
        modelField.setText(context.services().store().loadAccountTestModel());
        loadAccounts();
    }

    @Override
    public void refreshStateOnly() {
        currentLabel.setText(context.state().getActiveAccount() == null ? "未选择" : context.state().getActiveAccount().getDisplayText());
    }

    private void loadAccounts() {
        context.runAsync(() -> new Object[]{context.services().store().buildAccounts(), context.services().store().getActiveAccount()}, result -> {
            var accounts = (java.util.List<Account>) result[0];
            Account active = (Account) result[1];
            context.state().setActiveAccount(active);
            listView.setItems(FXCollections.observableArrayList(accounts));
            refreshStateOnly();
            if (active != null) {
                accounts.stream()
                    .filter(account -> account.getName().equals(active.getName()) && account.isTeam() == active.isTeam())
                    .findFirst()
                    .ifPresent(account -> listView.getSelectionModel().select(account));
            }
        }, error -> Ui.error("失败", error.getMessage()));
    }

    private void populate(Account account) {
        if (account == null) {
            return;
        }
        nameField.setText(account.getName());
        baseField.setText(account.getBaseUrl());
        keyField.setText(account.getApiKey());
        accountModelField.setText(account.getModelName());
        orgField.setText(account.getOrgId());
        if (account.isTeam()) {
            teamRadio.setSelected(true);
        } else if ("official".equals(account.getAccountType())) {
            officialRadio.setSelected(true);
        } else {
            proxyRadio.setSelected(true);
        }
    }

    private Account buildFormAccount() {
        boolean team = teamRadio.isSelected();
        String type = team ? "team" : officialRadio.isSelected() ? "official" : "proxy";
        return new Account(nameField.getText().trim(), baseField.getText().trim(), keyField.getText().trim(),
            accountModelField.getText().trim(), orgField.getText().trim(), team, type);
    }

    private void applySelected() {
        Account account = listView.getSelectionModel().getSelectedItem();
        if (account == null) {
            Ui.warn("提示", "请选择账号");
            return;
        }
        try {
            context.services().store().applyAccountConfig(account);
            context.state().setActiveAccount(account);
            refreshStateOnly();
            restartCodexAppAfterAccountApplied("账号已应用，正在重启 Codex...");
            context.refreshAll();
            context.navigateTo(CLOUD_SYNC_PAGE_KEY);
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void saveAccount() {
        Account account = buildFormAccount();
        if (account.getName().isBlank() || account.getBaseUrl().isBlank() || account.getApiKey().isBlank()) {
            Ui.warn("提示", "名称、Base URL、API Key 不能为空");
            return;
        }
        if (account.isTeam() && account.getOrgId().isBlank()) {
            Ui.warn("提示", "Team 账号需要填写 Org ID");
            return;
        }
        try {
            context.services().store().upsertAccount(account);
            context.services().store().applyAccountConfig(account);
            context.state().setActiveAccount(account);
            loadAccounts();
            restartCodexAppAfterAccountApplied("账号已保存并应用，正在重启 Codex...");
            context.refreshAll();
            context.navigateTo(CLOUD_SYNC_PAGE_KEY);
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void restartCodexAppAfterAccountApplied(String pendingText) {
        statusLabel.setText(pendingText);
        context.runAsync(
            () -> {
                context.services().codex().restartCodexApp();
                return null;
            },
            ignored -> statusLabel.setText("账号已应用，Codex 已重启"),
            error -> {
                statusLabel.setText("账号已应用，但 Codex 重启失败：" + error.getMessage());
                Ui.error("Codex 重启失败", error.getMessage());
            }
        );
    }

    private void deleteSelected() {
        Account account = listView.getSelectionModel().getSelectedItem();
        if (account == null) {
            Ui.warn("提示", "请选择账号");
            return;
        }
        if (!Ui.confirm("确认", "确认删除账号 " + account.getName() + " 吗？")) {
            return;
        }
        try {
            context.services().store().deleteAccount(account);
            loadAccounts();
            context.refreshAll();
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void clearForm() {
        nameField.clear();
        baseField.clear();
        keyField.clear();
        accountModelField.clear();
        orgField.clear();
        proxyRadio.setSelected(true);
    }

    private void testAccount() {
        Account account = buildFormAccount();
        persistTestModelQuietly();
        markPending(account);
        statusLabel.setText("检测中：" + account.getName());
        context.runAsync(
            () -> context.services().network().probeAccount(account, modelField.getText().trim(), PROBE_TIMEOUT_SECONDS),
            result -> applyProbeResult(result, true),
            error -> Ui.error("失败", error.getMessage())
        );
    }

    private void testAllAccounts() {
        var accounts = new java.util.ArrayList<>(listView.getItems());
        if (accounts.isEmpty()) {
            Ui.warn("提示", "账号列表为空");
            return;
        }
        persistTestModelQuietly();
        accounts.forEach(this::markPending);
        statusLabel.setText("批量检测中...");
        context.runAsync(
            () -> context.services().network().probeAccounts(accounts, modelField.getText().trim(), PROBE_TIMEOUT_SECONDS),
            results -> {
                int okCount = 0;
                for (AccountProbeStatus result : results) {
                    applyProbeResult(result, false);
                    if (result.ok()) {
                        okCount++;
                    }
                }
                int failCount = results.size() - okCount;
                String message = "批量检测完成：成功 " + okCount + "，失败 " + failCount;
                statusLabel.setText(message);
                Ui.info("完成", message);
            },
            error -> Ui.error("失败", error.getMessage())
        );
    }

    private void persistTestModelQuietly() {
        String model = modelField.getText().trim();
        if (model.isBlank()) {
            model = DEFAULT_TEST_MODEL;
            modelField.setText(model);
        }
        try {
            context.services().store().saveAccountTestModel(model);
        } catch (Exception ignored) {
        }
    }

    private void persistNonBlankTestModelQuietly(String model) {
        String value = model == null ? "" : model.trim();
        if (value.isBlank()) {
            return;
        }
        try {
            context.services().store().saveAccountTestModel(value);
        } catch (Exception ignored) {
        }
    }

    private void markPending(Account account) {
        probeStates.put(accountKey(account), ProbeState.pending());
        listView.refresh();
    }

    private void applyProbeResult(AccountProbeStatus result, boolean updateStatusText) {
        probeStates.put(accountKey(result.accountName(), result.team()), ProbeState.from(result));
        listView.refresh();
        if (updateStatusText) {
            String prefix = result.ok() ? "检测通过：" : "检测失败：";
            statusLabel.setText(prefix + result.accountName() + " - " + result.summary());
        }
    }

    private String accountKey(Account account) {
        return accountKey(account.getName(), account.isTeam());
    }

    private String accountKey(String name, boolean team) {
        return (team ? "team:" : "profile:") + (name == null ? "" : name.trim());
    }

    private void exportAccounts() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON 文件", "*.json"));
        String fileName = "codex-accounts-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".json";
        chooser.setInitialFileName(fileName);
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            int count = context.services().store().exportAccounts(file.toPath());
            Ui.info("完成", "已导出账号 " + count + " 条");
            statusLabel.setText("已导出到：" + file.getAbsolutePath());
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void importAccounts() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON 文件", "*.json"));
        var file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        if (!Ui.confirm("确认", "导入会全量替换当前账号列表，是否继续？")) {
            return;
        }
        try {
            var result = context.services().store().importAccounts(file.toPath());
            loadAccounts();
            context.refreshAll();
            String activeText = result.restoredActive ? "，已恢复激活账号" : "";
            Ui.info("完成", "总计 " + result.total + " 条，成功导入 " + result.imported + " 条，跳过 " + result.skipped + " 条" + activeText);
            statusLabel.setText("批量导入完成：成功 " + result.imported + "，跳过 " + result.skipped);
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private record ProbeState(Color color, String summary, String detail) {
        private static ProbeState idle() {
            return new ProbeState(Color.web("#9aa3b2"), "未检测", "");
        }

        private static ProbeState pending() {
            return new ProbeState(Color.web("#f0ad4e"), "检测中...", "");
        }

        private static ProbeState from(AccountProbeStatus status) {
            return new ProbeState(
                status.ok() ? Color.web("#1a9a65") : Color.web("#d44c5d"),
                status.ok() ? "可用" : "失败",
                status.detail()
            );
        }
    }
}
