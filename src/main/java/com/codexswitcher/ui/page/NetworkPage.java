package com.codexswitcher.ui.page;

import com.codexswitcher.model.Account;
import com.codexswitcher.model.ProbeResult;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class NetworkPage extends PagePane {

    private final Label baseLabel = new Label("-");
    private final TextField diagnosisModelField = new TextField("gpt-5.2-codex");
    private final TextArea detailArea = new TextArea();
    private final Label summaryLabel = new Label();
    private final ComboBox<Account> accountBox = new ComboBox<>();
    private final TextField probeModelField = new TextField("gpt-5.2-codex");
    private final TableView<ProbeResult> table = new TableView<>();

    public NetworkPage(AppContext context) {
        super(context);
        detailArea.setPrefRowCount(14);
        detailArea.setWrapText(true);
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

        TableColumn<ProbeResult, String> modelCol = new TableColumn<>("模型");
        modelCol.setCellValueFactory(data -> new SimpleStringProperty(probeModelField.getText()));
        TableColumn<ProbeResult, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(Boolean.TRUE.equals(data.getValue().getOk()) ? "可用" : "不可用"));
        TableColumn<ProbeResult, String> resultCol = new TableColumn<>("返回结果");
        resultCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBody()));
        resultCol.setPrefWidth(520);
        table.getColumns().addAll(modelCol, statusCol, resultCol);
        table.setPrefHeight(160);

        var diagnoseButton = Ui.button("接口诊断");
        diagnoseButton.setOnAction(event -> diagnose());
        var probeButton = Ui.button("开始探测");
        probeButton.setOnAction(event -> probe());

        root.getChildren().addAll(
            Ui.title("中转站接口"),
            Ui.card("关键诊断", Ui.row(new Label("当前 Base URL"), baseLabel), Ui.row(new Label("测试模型"), diagnosisModelField, diagnoseButton), summaryLabel, detailArea),
            Ui.card("账号池模型探测", Ui.row(new Label("账号"), accountBox, new Label("模型"), probeModelField, probeButton), table)
        );
    }

    @Override
    public void onShow() {
        context.runAsync(() -> new Object[]{context.services().store().getActiveAccount(), context.services().store().buildAccounts()}, result -> {
            Account active = (Account) result[0];
            var accounts = (java.util.List<Account>) result[1];
            context.state().setActiveAccount(active);
            accountBox.setItems(FXCollections.observableArrayList(accounts));
            if (active != null) {
                baseLabel.setText(active.getBaseUrl());
                accounts.stream()
                    .filter(account -> account.getName().equals(active.getName()) && account.isTeam() == active.isTeam())
                    .findFirst()
                    .ifPresent(account -> accountBox.getSelectionModel().select(account));
            } else {
                baseLabel.setText("-");
                accountBox.getSelectionModel().clearSelection();
            }
        }, error -> Ui.error("失败", error.getMessage()));
    }

    private void diagnose() {
        Account account = context.state().getActiveAccount();
        if (account == null) {
            Ui.warn("提示", "请先应用账号");
            return;
        }
        summaryLabel.setText("诊断中...");
        context.runAsync(() -> context.services().network().probeEndpoints(account.getBaseUrl(), account.getApiKey(), account.getOrgId(), diagnosisModelField.getText().trim(), 60),
            result -> {
                summaryLabel.setText(result.getConclusion());
                detailArea.setText(result.getSummaryDetail() + System.lineSeparator() + System.lineSeparator() + result.getDetail());
            }, error -> Ui.error("失败", error.getMessage()));
    }

    private void probe() {
        Account account = accountBox.getValue();
        if (account == null) {
            Ui.warn("提示", "请选择账号");
            return;
        }
        context.runAsync(() -> context.services().network().probeSingleModel(account.getBaseUrl(), account.getApiKey(), probeModelField.getText().trim(), 45),
            result -> table.setItems(FXCollections.observableArrayList(result)),
            error -> Ui.error("失败", error.getMessage()));
    }
}
