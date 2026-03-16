package com.codexswitcher.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Labeled;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UiIcons {

    private static final String ICON_BASE = "/assets/icons/heroicons-solid/";
    private static final double SVG_VIEWBOX_SIZE = 24.0;
    private static final Pattern PATH_PATTERN = Pattern.compile("<path\\s+([^>]*?)/?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-zA-Z:-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Map<String, String> LABEL_ICON_MAP = new HashMap<>();
    private static final Map<String, List<PathSpec>> ICON_CACHE = new HashMap<>();

    static {
        LABEL_ICON_MAP.put("应用账号", "user-circle");
        LABEL_ICON_MAP.put("删除账号", "trash");
        LABEL_ICON_MAP.put("刷新", "arrow-path");
        LABEL_ICON_MAP.put("刷新列表", "arrow-path");
        LABEL_ICON_MAP.put("刷新检测", "arrow-path");
        LABEL_ICON_MAP.put("刷新状态", "arrow-path");
        LABEL_ICON_MAP.put("刷新索引", "arrow-path");
        LABEL_ICON_MAP.put("批量导出", "arrow-down-tray");
        LABEL_ICON_MAP.put("导出 JSON", "document-arrow-down");
        LABEL_ICON_MAP.put("导出 Markdown", "document-arrow-down");
        LABEL_ICON_MAP.put("批量导入", "arrow-up-tray");
        LABEL_ICON_MAP.put("导入 Skill", "arrow-up-tray");
        LABEL_ICON_MAP.put("保存", "check-circle");
        LABEL_ICON_MAP.put("保存/更新", "check-circle");
        LABEL_ICON_MAP.put("清空", "no-symbol");
        LABEL_ICON_MAP.put("账号测试", "beaker");
        LABEL_ICON_MAP.put("立即检查", "shield-check");
        LABEL_ICON_MAP.put("一键更新", "arrow-path");
        LABEL_ICON_MAP.put("选择工作区", "folder-open");
        LABEL_ICON_MAP.put("选择目录", "folder-open");
        LABEL_ICON_MAP.put("一键启动 CODEX CLI", "rocket-launch");
        LABEL_ICON_MAP.put("一键启动 VS Code", "rocket-launch");
        LABEL_ICON_MAP.put("WebView错误修改", "wrench-screwdriver");
        LABEL_ICON_MAP.put("扫描插件", "magnifying-glass");
        LABEL_ICON_MAP.put("打开插件目录", "folder-open");
        LABEL_ICON_MAP.put("关闭自动更新", "no-symbol");
        LABEL_ICON_MAP.put("打开 opencode.json", "folder-open");
        LABEL_ICON_MAP.put("打开 status.openai.com", "arrow-top-right-on-square");
        LABEL_ICON_MAP.put("打开发布页", "arrow-top-right-on-square");
        LABEL_ICON_MAP.put("打开所在目录", "folder-open");
        LABEL_ICON_MAP.put("打开技能目录", "folder-open");
        LABEL_ICON_MAP.put("执行清理", "sparkles");
        LABEL_ICON_MAP.put("接口诊断", "bug-ant");
        LABEL_ICON_MAP.put("开始探测", "play");
        LABEL_ICON_MAP.put("备份技能", "archive-box");
        LABEL_ICON_MAP.put("删除 Skill", "trash");
        LABEL_ICON_MAP.put("重新读取", "arrow-path");

        LABEL_ICON_MAP.put("Codex CLI状态", "command-line");
        LABEL_ICON_MAP.put("VSCode Codex", "code-bracket-square");
        LABEL_ICON_MAP.put("config.toml配置", "cog-6-tooth");
        LABEL_ICON_MAP.put("opencode 配置", "cog-6-tooth");
        LABEL_ICON_MAP.put("多账号切换", "users");
        LABEL_ICON_MAP.put("Codex会话管理", "chat-bubble-left-right");
        LABEL_ICON_MAP.put("Skill 管理", "wrench-screwdriver");
        LABEL_ICON_MAP.put("中转站接口", "globe-alt");
        LABEL_ICON_MAP.put("OpenAI官网状态", "signal");
    }

    private UiIcons() {
    }

    public static void apply(Labeled labeled, String text, double size) {
        if (labeled == null || isBlank(text)) {
            return;
        }
        String iconName = resolveIconName(text.trim());
        if (isBlank(iconName)) {
            return;
        }
        Node icon = buildIcon(iconName, labeled, size);
        if (icon == null) {
            return;
        }
        labeled.setGraphic(icon);
        labeled.setGraphicTextGap(6);
        labeled.setContentDisplay(ContentDisplay.LEFT);
    }

    private static Node buildIcon(String iconName, Labeled owner, double size) {
        List<PathSpec> specs = iconSpecs(iconName);
        if (specs.isEmpty()) {
            return null;
        }
        javafx.scene.Group group = new javafx.scene.Group();
        for (PathSpec spec : specs) {
            SVGPath path = new SVGPath();
            path.setContent(spec.pathData);
            if (spec.evenOdd) {
                path.setFillRule(FillRule.EVEN_ODD);
            }
            path.fillProperty().bind(owner.textFillProperty());
            group.getChildren().add(path);
        }
        double scale = size / SVG_VIEWBOX_SIZE;
        group.setScaleX(scale);
        group.setScaleY(scale);

        StackPane wrap = new StackPane(group);
        wrap.setAlignment(Pos.CENTER);
        wrap.setPrefSize(size, size);
        wrap.setMinSize(size, size);
        wrap.setMaxSize(size, size);
        wrap.setPickOnBounds(false);
        return wrap;
    }

    private static List<PathSpec> iconSpecs(String iconName) {
        synchronized (ICON_CACHE) {
            List<PathSpec> cached = ICON_CACHE.get(iconName);
            if (cached != null) {
                return cached;
            }
            List<PathSpec> loaded = loadIconSpecs(iconName);
            ICON_CACHE.put(iconName, loaded);
            return loaded;
        }
    }

    private static List<PathSpec> loadIconSpecs(String iconName) {
        String resource = ICON_BASE + iconName + ".svg";
        try (InputStream input = UiIcons.class.getResourceAsStream(resource)) {
            if (input == null) {
                return List.of();
            }
            String svg = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher pathMatcher = PATH_PATTERN.matcher(svg);
            List<PathSpec> result = new ArrayList<>();
            while (pathMatcher.find()) {
                Map<String, String> attrs = parseAttrs(pathMatcher.group(1));
                String data = attrs.getOrDefault("d", "");
                if (isBlank(data)) {
                    continue;
                }
                String fillRule = attrs.getOrDefault("fill-rule", attrs.getOrDefault("fillRule", ""));
                result.add(new PathSpec(data, "evenodd".equalsIgnoreCase(fillRule)));
            }
            return result;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Map<String, String> parseAttrs(String raw) {
        Map<String, String> attrs = new HashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(raw);
        while (matcher.find()) {
            attrs.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(3));
        }
        return attrs;
    }

    private static String resolveIconName(String label) {
        String direct = LABEL_ICON_MAP.get(label);
        if (!isBlank(direct)) {
            return direct;
        }
        if (label.contains("刷新")) {
            return "arrow-path";
        }
        if (label.contains("打开")) {
            return "folder-open";
        }
        if (label.contains("导出")) {
            return "arrow-down-tray";
        }
        if (label.contains("导入")) {
            return "arrow-up-tray";
        }
        if (label.contains("保存")) {
            return "check-circle";
        }
        if (label.contains("删除")) {
            return "trash";
        }
        if (label.contains("启动")) {
            return "rocket-launch";
        }
        return "";
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static final class PathSpec {
        private final String pathData;
        private final boolean evenOdd;

        private PathSpec(String pathData, boolean evenOdd) {
            this.pathData = pathData;
            this.evenOdd = evenOdd;
        }
    }
}
