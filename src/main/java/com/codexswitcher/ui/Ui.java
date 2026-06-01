package com.codexswitcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;

public final class Ui {

    private Ui() {
    }

    public static VBox card(String title, Node... children) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        if (title != null && !title.isBlank()) {
            Label label = new Label(title);
            label.getStyleClass().add("card-title");
            box.getChildren().add(label);
        }
        box.getChildren().addAll(children);
        return box;
    }

    public static HBox row(Node... children) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(children);
        return row;
    }

    public static Button button(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("button");
        UiIcons.apply(button, text, 13);
        return button;
    }

    public static void decorateActionIcon(Labeled labeled, String text) {
        UiIcons.apply(labeled, text, 12);
    }

    public static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("page-title");
        return label;
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static VBox page() {
        VBox root = new VBox(12);
        root.getStyleClass().add("page-root");
        return root;
    }

    public static void info(String title, String message) {
        alert(Alert.AlertType.INFORMATION, title, message);
    }

    public static void warn(String title, String message) {
        alert(Alert.AlertType.WARNING, title, message);
    }

    public static void error(String title, String message) {
        alert(Alert.AlertType.ERROR, title, message);
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    public static TextArea readonlyArea(int prefRows) {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(prefRows);
        return area;
    }

    public static TextField readonlyField() {
        TextField field = new TextField();
        field.setEditable(false);
        return field;
    }

    public static String promptText(String title, String header, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue == null ? "" : defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(null);
        return dialog.showAndWait().orElse(null);
    }

    public static String promptPassword(String title, String header) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        PasswordField field = new PasswordField();
        field.setPromptText("请输入密钥");
        dialog.getDialogPane().setContent(field);
        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK ? field.getText() : null);
        return dialog.showAndWait().orElse(null);
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
