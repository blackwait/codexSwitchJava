package com.codexswitcher.ui.page;

import com.codexswitcher.model.SkillInfo;
import com.codexswitcher.service.BaseSupport;
import com.codexswitcher.ui.AppContext;
import com.codexswitcher.ui.PagePane;
import com.codexswitcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public class SkillsPagePane extends PagePane {

    private final ListView<SkillInfo> listView = new ListView<>();
    private final Label nameLabel = new Label("-");
    private final Label descLabel = new Label("-");
    private final Label sourceLabel = new Label("-");
    private final Label pathLabel = new Label("-");
    private final TextArea readmeArea = Ui.readonlyArea(18);

    public SkillsPagePane(AppContext context) {
        super(context);
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(SkillInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showSkill(newValue));

        var refreshButton = Ui.button("刷新列表");
        refreshButton.setOnAction(event -> loadSkills());
        var importButton = Ui.button("导入 Skill");
        importButton.setOnAction(event -> importSkill());
        var backupButton = Ui.button("备份技能");
        backupButton.setOnAction(event -> backup());
        var openRootButton = Ui.button("打开技能目录");
        openRootButton.setOnAction(event -> {
            try {
                BaseSupport.openPath(context.services().skills().skillsRoot());
            } catch (Exception e) {
                Ui.error("失败", e.getMessage());
            }
        });
        var openButton = Ui.button("打开所在目录");
        openButton.setOnAction(event -> openSelected());
        var removeButton = Ui.button("删除 Skill");
        removeButton.setOnAction(event -> removeSelected());

        VBox left = Ui.card("Skill 列表", listView);
        left.setPrefWidth(280);
        VBox right = Ui.card("Skill 详情", new Label("名称"), nameLabel, new Label("描述"), descLabel, new Label("来源"), sourceLabel,
            new Label("路径"), pathLabel, readmeArea, Ui.row(openButton, removeButton));
        HBox body = new HBox(12, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);
        root.getChildren().addAll(Ui.title("Skill 管理"), Ui.row(refreshButton, importButton, backupButton, openRootButton), body);
    }

    @Override
    public void onShow() {
        loadSkills();
    }

    private void loadSkills() {
        context.runAsync(() -> context.services().skills().scanSkills(),
            skills -> listView.setItems(FXCollections.observableArrayList(skills)),
            error -> Ui.error("失败", error.getMessage()));
    }

    private void showSkill(SkillInfo skill) {
        if (skill == null) {
            return;
        }
        nameLabel.setText(skill.getName());
        descLabel.setText(skill.getDescription());
        sourceLabel.setText(skill.getSource());
        pathLabel.setText(skill.getPath().toString());
        readmeArea.setText(skill.getReadme());
    }

    private void importSkill() {
        DirectoryChooser chooser = new DirectoryChooser();
        var file = chooser.showDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            context.services().skills().importSkill(file.toPath());
            loadSkills();
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void backup() {
        try {
            Ui.info("完成", "已备份到：" + context.services().skills().backupSkills());
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void openSelected() {
        SkillInfo skill = listView.getSelectionModel().getSelectedItem();
        if (skill == null) {
            return;
        }
        try {
            BaseSupport.openPath(skill.getPath());
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }

    private void removeSelected() {
        SkillInfo skill = listView.getSelectionModel().getSelectedItem();
        if (skill == null) {
            return;
        }
        if (!Ui.confirm("确认", "确认删除 Skill：" + skill.getName() + " 吗？")) {
            return;
        }
        try {
            context.services().skills().removeSkill(skill);
            loadSkills();
        } catch (Exception e) {
            Ui.error("失败", e.getMessage());
        }
    }
}
