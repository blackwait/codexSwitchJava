package com.codexswitcher.ui;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

public abstract class PagePane extends ScrollPane {

    protected final AppContext context;
    protected final VBox root = Ui.page();

    protected PagePane(AppContext context) {
        this.context = context;
        setFitToWidth(true);
        setContent(root);
    }

    public abstract void onShow();

    public void refreshStateOnly() {
    }
}
