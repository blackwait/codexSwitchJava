package com.codexswitcher.ui.page;

import com.codexswitcher.model.Account;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AccountPage extends PagePane {

    private final Label currentLabel = new Label("未选择");
    private final ListView<Account> listView = new ListView<>();
    private final TextField nameField = new TextField();
    private final TextField baseField = new TextField();
    private final PasswordField keyField = new PasswordField();
    private final TextField orgField = new TextField();
    private final TextField modelField = new TextField("gpt-5.2-codex");
    private final RadioButton teamRadio = new RadioButton("Team 账号");
    private final RadioButton officialRadio = new RadioButton("ChatGPT 官方账号");
    private final RadioButton proxyRadio = new RadioButton("中转账号");
    private final Label statusLabel = new Label();

    public AccountPage(AppContext context) {
        super(context);
        root.getChildren().add(Ui.title("多账号切换"));
        root.getChildren().add(Ui.card("当前账号", currentLabel));

        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplayText());
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populate(newValue));

        ToggleGroup group = new ToggleGroup();
        teamRadio.setToggleGroup(group);
        officialRadio.setToggleGroup(group);
        proxyRadio.setToggleGroup(group);
        proxyRadio.setSelected(true);

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
        form.add(new Label("Org ID"), 0, 4);
        form.add(orgField, 1, 4);
        form.add(new Label("测试模型"), 0, 5);
        form.add(modelField, 1, 5);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(baseField, Priority.ALWAYS);
        GridPane.setHgrow(keyField, Priority.ALWAYS);
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
        var testButton = Ui.button("账号测试");
        testButton.setOnAction(event -> testAccount());

        VBox left = Ui.card("账号列表", listView, Ui.row(applyButton, deleteButton, refreshButton), Ui.row(exportButton, importButton));
        left.setPrefWidth(360);
        VBox right = Ui.card("新增/更新账号", form, Ui.row(saveButton, clearButton, testButton), statusLabel);
        HBox body = new HBox(12, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);

        root.getChildren().addAll(body, Ui.card("说明",
            new Label("保存后会自动写入 ~/.codex 下的 config.toml、auth.json 与激活账号指针。"),
            new Label("批量导入/导出使用 JSON 文件，导入时会全量替换本地账号列表。")));
    }

    @Override
    public void onShow() {
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
        return new Account(nameField.getText().trim(), baseField.getText().trim(), keyField.getText().trim(), orgField.getText().trim(), team, type);
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
            statusLabel.setText("账号已应用");
            context.refreshAll();
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
            statusLabel.setText("账号已保存并应用");
            context.refreshAll();
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
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
        orgField.clear();
        proxyRadio.setSelected(true);
    }

    private void testAccount() {
        Account account = buildFormAccount();
        if (account.getBaseUrl().isBlank() || account.getApiKey().isBlank()) {
            Ui.warn("提示", "Base URL 和 API Key 不能为空");
            return;
        }
        if (account.isTeam() && account.getOrgId().isBlank()) {
            Ui.warn("提示", "Team 账号需要填写 Org ID");
            return;
        }
        try {
            context.services().codex().testAccount(account, modelField.getText().trim());
            Ui.info("提示", "已启动 codex chat，请在新终端中验证账号是否可用。");
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
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
}
