package com.codexswitcher.ui;

import com.codexswitcher.model.UsageSnapshot;
import com.codexswitcher.service.AppServices;
import com.codexswitcher.service.BaseSupport;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MacTrayController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long REFRESH_INTERVAL_MINUTES = 1;

    private final Stage stage;
    private final AppServices services;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "codex-usage-tray");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private SystemTray tray;
    private TrayIcon trayIcon;
    private MenuItem accountItem;
    private MenuItem statusItem;
    private MenuItem remainingItem;
    private MenuItem dailyItem;
    private MenuItem weeklyItem;
    private MenuItem updatedItem;

    public MacTrayController(Stage stage, AppServices services) {
        this.stage = stage;
        this.services = services;
    }

    public boolean start() {
        if (!BaseSupport.isMac() || !SystemTray.isSupported()) {
            return false;
        }
        tray = SystemTray.getSystemTray();
        PopupMenu menu = buildMenu();
        trayIcon = new TrayIcon(renderStatusImage("Usage ...", true), "Codex Usage", menu);
        trayIcon.setImageAutoSize(false);
        trayIcon.addActionListener(event -> openMainWindow());
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            services.store().logLine("初始化菜单栏失败", e.getMessage());
            return false;
        }
        refreshAsync();
        executor.scheduleWithFixedDelay(this::refreshAsync, REFRESH_INTERVAL_MINUTES, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
        return true;
    }

    public void shutdown() {
        executor.shutdownNow();
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
    }

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();
        accountItem = disabledItem("账号: -");
        statusItem = disabledItem("状态: 等待刷新");
        remainingItem = disabledItem("余额: -");
        dailyItem = disabledItem("今日: -");
        weeklyItem = disabledItem("本周: -");
        updatedItem = disabledItem("更新: -");
        MenuItem refreshItem = new MenuItem("刷新");
        refreshItem.addActionListener(event -> refreshAsync());
        MenuItem openItem = new MenuItem("打开主窗口");
        openItem.addActionListener(event -> openMainWindow());
        MenuItem quitItem = new MenuItem("退出");
        quitItem.addActionListener(event -> quit());

        menu.add(accountItem);
        menu.add(statusItem);
        menu.add(remainingItem);
        menu.add(dailyItem);
        menu.add(weeklyItem);
        menu.add(updatedItem);
        menu.addSeparator();
        menu.add(refreshItem);
        menu.add(openItem);
        menu.addSeparator();
        menu.add(quitItem);
        return menu;
    }

    private MenuItem disabledItem(String label) {
        MenuItem item = new MenuItem(label);
        item.setEnabled(false);
        return item;
    }

    private void refreshAsync() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                UsageSnapshot snapshot = services.usage().fetchCurrentUsage();
                EventQueue.invokeLater(() -> updateSuccess(snapshot));
            } catch (Exception e) {
                String message = BaseSupport.firstNonBlank(e.getMessage(), e.getClass().getSimpleName());
                services.store().logLine("刷新 Codex usage 失败", message);
                EventQueue.invokeLater(() -> updateFailure(message));
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void updateSuccess(UsageSnapshot snapshot) {
        if (trayIcon == null) {
            return;
        }
        String trayTitle = formatTrayTitle(snapshot);
        trayIcon.setImage(renderStatusImage(trayTitle, snapshot.isValid()));
        trayIcon.setToolTip(buildTooltip(snapshot));
        accountItem.setLabel("账号: " + BaseSupport.firstNonBlank(snapshot.getAccountName(), "-"));
        statusItem.setLabel("状态: " + (snapshot.isValid() ? "可用" : "不可用"));
        remainingItem.setLabel("余额: " + formatAmount(snapshot.getRemaining(), snapshot.getUnit()));
        dailyItem.setLabel("今日: " + formatUsage(snapshot.getDailyUsageUsd(), snapshot.getDailyLimitUsd(), snapshot.getDailyRemainingUsd(), snapshot.getUnit()));
        weeklyItem.setLabel("本周: " + formatUsage(snapshot.getWeeklyUsageUsd(), snapshot.getWeeklyLimitUsd(), snapshot.getWeeklyRemainingUsd(), snapshot.getUnit()));
        updatedItem.setLabel("更新: " + (snapshot.getUpdatedAt() == null ? "-" : TIME_FORMAT.format(snapshot.getUpdatedAt())));
    }

    private void updateFailure(String message) {
        if (trayIcon == null) {
            return;
        }
        trayIcon.setImage(renderStatusImage("Usage ?", false));
        trayIcon.setToolTip("Codex Usage 获取失败: " + message);
        accountItem.setLabel("账号: -");
        statusItem.setLabel("状态: 获取失败");
        remainingItem.setLabel("错误: " + compact(message, 42));
        dailyItem.setLabel("今日: -");
        weeklyItem.setLabel("本周: -");
        updatedItem.setLabel("更新: -");
    }

    private String formatTrayTitle(UsageSnapshot snapshot) {
        String remaining = shortAmount(snapshot.getRemaining(), snapshot.getUnit());
        Double weeklyRemaining = snapshot.getWeeklyRemainingUsd();
        if (weeklyRemaining != null) {
            return remaining + " | W " + shortAmount(weeklyRemaining, snapshot.getUnit());
        }
        return remaining;
    }

    private String buildTooltip(UsageSnapshot snapshot) {
        return "Codex Usage\n"
            + "账号: " + BaseSupport.firstNonBlank(snapshot.getAccountName(), "-") + "\n"
            + "余额: " + formatAmount(snapshot.getRemaining(), snapshot.getUnit()) + "\n"
            + "今日: " + formatUsage(snapshot.getDailyUsageUsd(), snapshot.getDailyLimitUsd(), snapshot.getDailyRemainingUsd(), snapshot.getUnit()) + "\n"
            + "本周: " + formatUsage(snapshot.getWeeklyUsageUsd(), snapshot.getWeeklyLimitUsd(), snapshot.getWeeklyRemainingUsd(), snapshot.getUnit());
    }

    private String formatUsage(Double used, Double limit, Double remaining, String unit) {
        if (used == null && limit == null && remaining == null) {
            return "-";
        }
        return "已用 " + formatAmount(used, unit)
            + " / 限额 " + formatAmount(limit, unit)
            + " / 剩余 " + formatAmount(remaining, unit);
    }

    private String shortAmount(Double value, String unit) {
        if (value == null) {
            return "-";
        }
        String prefix = "USD".equalsIgnoreCase(BaseSupport.firstNonBlank(unit, "")) ? "$" : "";
        String suffix = prefix.isEmpty() ? " " + BaseSupport.firstNonBlank(unit, "") : "";
        return prefix + String.format("%.2f", value) + suffix;
    }

    private String formatAmount(Double value, String unit) {
        if (value == null) {
            return "-";
        }
        return String.format("%.2f %s", value, BaseSupport.firstNonBlank(unit, "USD"));
    }

    private String compact(String text, int maxLength) {
        String value = BaseSupport.firstNonBlank(text, "-").replaceAll("\\s+", " ").trim();
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }

    private BufferedImage renderStatusImage(String text, boolean ok) {
        String value = BaseSupport.firstNonBlank(text, "Usage");
        Font font = new Font("Helvetica Neue", Font.BOLD, 24);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probeGraphics = probe.createGraphics();
        probeGraphics.setFont(font);
        FontMetrics metrics = probeGraphics.getFontMetrics();
        int width = Math.max(96, Math.min(320, metrics.stringWidth(value) + 28));
        probeGraphics.dispose();

        int height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        graphics.setColor(new Color(255, 255, 255));
        int x = 12;
        int y = ((height - metrics.getHeight()) / 2) + metrics.getAscent();
        graphics.drawString(value, x, y);
        graphics.dispose();
        return image;
    }

    private void openMainWindow() {
        Platform.runLater(() -> {
            if (stage.isIconified()) {
                stage.setIconified(false);
            }
            stage.show();
            stage.toFront();
            stage.requestFocus();
        });
    }

    private void quit() {
        shutdown();
        Platform.runLater(() -> {
            Platform.setImplicitExit(true);
            Platform.exit();
        });
    }
}
