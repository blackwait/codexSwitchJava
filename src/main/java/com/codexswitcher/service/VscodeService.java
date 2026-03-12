package com.codexswitcher.service;

import com.codexswitcher.app.AppState;
import com.codexswitcher.model.ExtensionInfo;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VscodeService extends BaseSupport {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,120}$");

    public List<Path> extensionRoots(AppState state) {
        List<Path> homes = new ArrayList<>();
        homes.add(Path.of(System.getProperty("user.home")));
        if (!isBlank(System.getenv("USERPROFILE"))) {
            homes.add(Path.of(System.getenv("USERPROFILE")));
        }
        if (!isBlank(System.getenv("HOMEDRIVE")) && !isBlank(System.getenv("HOMEPATH"))) {
            homes.add(Path.of(System.getenv("HOMEDRIVE") + System.getenv("HOMEPATH")));
        }
        List<Path> roots = new ArrayList<>();
        for (Path home : homes) {
            roots.add(home.resolve(".vscode").resolve("extensions"));
            roots.add(home.resolve(".vscode-insiders").resolve("extensions"));
            roots.add(home.resolve(".vscode-oss").resolve("extensions"));
            roots.add(home.resolve(".cursor").resolve("extensions"));
        }
        if (!isBlank(System.getenv("VSCODE_EXTENSIONS"))) {
            roots.add(Path.of(System.getenv("VSCODE_EXTENSIONS")));
        }
        if (state.getVscodeInstallDir() != null) {
            roots.add(state.getVscodeInstallDir().resolve("resources").resolve("app").resolve("extensions"));
        }
        return roots.stream().filter(Files::isDirectory).distinct().toList();
    }

    public List<ExtensionInfo> findExtensions(AppState state) {
        List<ExtensionInfo> items = new ArrayList<>();
        for (Path root : extensionRoots(state)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path path : stream) {
                    if (Files.isDirectory(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("openai.chatgpt")) {
                        ExtensionInfo info = new ExtensionInfo(path, parseExtensionVersion(path));
                        info.setIndexPath(findIndexFile(path));
                        items.add(info);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        items.sort(Comparator.comparing(ExtensionInfo::getVersion).reversed());
        return items;
    }

    public Path findIndexFile(Path extensionPath) {
        Path assets = extensionPath.resolve("webview").resolve("assets");
        if (!Files.isDirectory(assets)) {
            return null;
        }
        try {
            return Files.list(assets)
                .filter(path -> path.getFileName().toString().startsWith("index-") && path.getFileName().toString().endsWith(".js"))
                .sorted((left, right) -> Long.compare(lastModified(right), lastModified(left)))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public String parseExtensionVersion(Path path) {
        String name = path.getFileName().toString();
        if (name.contains("openai.chatgpt-")) {
            return name.substring(name.indexOf("openai.chatgpt-") + "openai.chatgpt-".length());
        }
        int index = name.lastIndexOf('-');
        return index >= 0 ? name.substring(index + 1) : "未知";
    }

    public MarketplaceMeta fetchMarketplaceMeta() {
        try {
            Map<String, Object> payload = Map.of(
                "filters", List.of(Map.of(
                    "criteria", List.of(Map.of("filterType", 7, "value", "openai.chatgpt")),
                    "pageNumber", 1,
                    "pageSize", 20,
                    "sortBy", 0,
                    "sortOrder", 0
                )),
                "assetTypes", List.of(),
                "flags", 103
            );
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json;api-version=7.2-preview.1")
                .header("User-Agent", "CodexSwitcher")
                .timeout(Duration.ofSeconds(8))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                .build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var root = JSON.readTree(response.body());
            var versions = root.path("results").path(0).path("extensions").path(0).path("versions");
            if (!versions.isArray()) {
                return null;
            }
            MarketplaceMeta meta = new MarketplaceMeta();
            versions.forEach(node -> {
                String version = node.path("version").asText("");
                if (version.isBlank()) {
                    return;
                }
                boolean pre = isPreRelease(node);
                String platform = targetPlatform(node);
                if (!pre && meta.latestStable == null) {
                    meta.latestStable = version;
                }
                if (pre && meta.latestPreview == null) {
                    meta.latestPreview = version;
                }
                meta.channelMap.put(version + "|" + platform, pre ? "preview" : "stable");
                meta.channelMap.putIfAbsent(version + "|", pre ? "preview" : "stable");
            });
            return meta;
        } catch (Exception e) {
            return null;
        }
    }

    public String channelLabel(String rawVersion, MarketplaceMeta meta) {
        if (meta == null || isBlank(rawVersion)) {
            return "";
        }
        String semver = extractSemver(rawVersion);
        String platform = "";
        Matcher matcher = SEMVER.matcher(rawVersion);
        if (matcher.find() && matcher.end() < rawVersion.length() && rawVersion.substring(matcher.end()).startsWith("-")) {
            platform = rawVersion.substring(matcher.end() + 1).trim().toLowerCase(Locale.ROOT);
        }
        String channel = meta.channelMap.getOrDefault(semver + "|" + platform, meta.channelMap.get(semver + "|"));
        return switch (channel) {
            case "stable" -> "稳定版";
            case "preview" -> "预览版";
            default -> "";
        };
    }

    public PatchOutcome applyPatch(Path indexPath, String rawModelText) throws IOException {
        if (indexPath == null || !Files.isRegularFile(indexPath)) {
            throw new IOException("请先扫描并选择 index 文件。");
        }
        List<String> models = targetModels(rawModelText);
        if (models.isEmpty()) {
            throw new IOException("请至少输入一个模型名。");
        }
        String original = readText(indexPath);
        PatchResult allowlist = applyAllowlistPatch(original, models);
        PatchResult filter = applyApiKeyFilterPatch(allowlist.content, models);
        PatchResult order = applyApiKeyOrderPatch(filter.content, models);
        PatchResult init = applyInitialDataPatch(order.content, models);
        if (!allowlist.ok || !filter.ok) {
            throw new IOException("未能定位关键补丁片段，建议先扫描插件并确认选择的是 index-*.js。");
        }
        Path backup = backupIndex(indexPath);
        writeText(indexPath, init.content);
        List<String> optionalFailed = new ArrayList<>();
        if (!order.ok) {
            optionalFailed.add("apikey-order");
        }
        if (!init.ok) {
            optionalFailed.add("initial-data");
        }
        return new PatchOutcome(backup, models, optionalFailed);
    }

    public Path backupIndex(Path indexPath) throws IOException {
        Path backupDir = indexPath.getParent().resolve("backup");
        Files.createDirectories(backupDir);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        Path backup = backupDir.resolve(indexPath.getFileName() + "." + stamp + ".bak");
        Files.copy(indexPath, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    public Path restoreLatestBackup(Path indexPath) throws IOException {
        Path backupDir = indexPath.getParent().resolve("backup");
        if (!Files.isDirectory(backupDir)) {
            throw new IOException("未发现备份目录。");
        }
        List<Path> backups = Files.list(backupDir)
            .filter(path -> path.getFileName().toString().startsWith(indexPath.getFileName() + ".") && path.getFileName().toString().endsWith(".bak"))
            .sorted((left, right) -> Long.compare(lastModified(right), lastModified(left)))
            .toList();
        if (backups.isEmpty()) {
            throw new IOException("未找到备份文件。");
        }
        Files.copy(backups.get(0), indexPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return backups.get(0);
    }

    public void disableAutoUpdate() throws IOException {
        List<Path> paths = settingsPaths();
        if (paths.isEmpty()) {
            throw new IOException("未找到 VS Code 设置文件。");
        }
        for (Path path : paths) {
            Map<String, Object> data = readJsonMapWithComments(path);
            data.put("extensions.autoUpdate", false);
            data.put("extensions.autoCheckUpdates", false);
            writeJson(path, data);
        }
    }

    public void launchVscode(Path installDir, Path workspace, CodexService codexService) throws IOException {
        String cli = findVscodeCli(codexService);
        if (!isBlank(cli) && supportsCommand(cli)) {
            startDetached(List.of(cli, "-r", workspace.toAbsolutePath().toString(), "--command", "chatgpt.openSidebar"), workspace, Map.of());
            return;
        }
        if (!isBlank(cli)) {
            ensureOpenOnStartup(workspace);
            startDetached(List.of(cli, "-r", workspace.toAbsolutePath().toString()), workspace, Map.of());
            return;
        }
        String executable = findVscodeExecutable(installDir);
        if (isBlank(executable)) {
            throw new IOException("未找到 VS Code。");
        }
        ensureOpenOnStartup(workspace);
        startDetached(List.of(executable, workspace.toAbsolutePath().toString()), workspace, Map.of());
    }

    public void launchSessionInVscode(Path installDir, Path workspace, String sessionId, CodexService codexService) throws IOException {
        launchVscode(installDir, workspace, codexService);
        tryOpenSessionUri(sessionId, workspace.toString());
    }

    public void fixWebviewAndLaunch(Path installDir, Path workspace, String sessionId, CodexService codexService) throws IOException {
        killVscodeProcesses();
        clearVscodeCache();
        launchSessionInVscode(installDir, workspace, sessionId, codexService);
    }

    public void killVscodeProcesses() {
        for (String name : List.of("Code.exe", "Code - Insiders.exe", "msedgewebview2.exe")) {
            runAndRead(List.of("taskkill", "/F", "/T", "/IM", name), 5);
        }
    }

    public void clearVscodeCache() {
        List<Path> targets = new ArrayList<>();
        if (!isBlank(System.getenv("APPDATA"))) {
            Path appData = Path.of(System.getenv("APPDATA"));
            for (String name : List.of("Code", "Code - Insiders")) {
                Path root = appData.resolve(name);
                targets.add(root.resolve("WebView"));
                targets.add(root.resolve("CachedData"));
                targets.add(root.resolve("Cache"));
                targets.add(root.resolve("GPUCache"));
                targets.add(root.resolve("Local Storage"));
                targets.add(root.resolve("Service Worker").resolve("CacheStorage"));
                targets.add(root.resolve("Service Worker").resolve("ScriptCache"));
            }
        }
        if (!isBlank(System.getenv("LOCALAPPDATA"))) {
            Path local = Path.of(System.getenv("LOCALAPPDATA"));
            targets.add(local.resolve("Temp").resolve("Code"));
            Path microsoft = local.resolve("Microsoft");
            for (String name : List.of("Code", "Code - Insiders")) {
                targets.add(microsoft.resolve(name).resolve("User").resolve("workspaceStorage"));
                targets.add(microsoft.resolve(name).resolve("User").resolve("globalStorage"));
            }
        }
        targets.forEach(BaseSupport::deleteRecursively);
    }

    public String findVscodeCli(CodexService codexService) {
        for (String name : List.of("code", "code.cmd", "code.exe", "code-insiders", "code-insiders.cmd")) {
            String executable = codexService.findExecutable(name);
            if (!isBlank(executable)) {
                return executable;
            }
        }
        return "";
    }

    public String findVscodeExecutable(Path installDir) {
        if (installDir != null) {
            for (String name : List.of("Code.exe", "Code - Insiders.exe")) {
                Path candidate = installDir.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        List<Path> candidates = new ArrayList<>();
        if (!isBlank(System.getenv("LOCALAPPDATA"))) {
            candidates.add(Path.of(System.getenv("LOCALAPPDATA"), "Programs", "Microsoft VS Code", "Code.exe"));
            candidates.add(Path.of(System.getenv("LOCALAPPDATA"), "Programs", "Microsoft VS Code Insiders", "Code - Insiders.exe"));
        }
        if (!isBlank(System.getenv("ProgramFiles"))) {
            candidates.add(Path.of(System.getenv("ProgramFiles"), "Microsoft VS Code", "Code.exe"));
            candidates.add(Path.of(System.getenv("ProgramFiles"), "Microsoft VS Code Insiders", "Code - Insiders.exe"));
        }
        if (!isBlank(System.getenv("ProgramFiles(x86)"))) {
            candidates.add(Path.of(System.getenv("ProgramFiles(x86)"), "Microsoft VS Code", "Code.exe"));
            candidates.add(Path.of(System.getenv("ProgramFiles(x86)"), "Microsoft VS Code Insiders", "Code - Insiders.exe"));
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst().map(Path::toString).orElse("");
    }

    public boolean supportsCommand(String cli) {
        return runAndRead(List.of(cli, "--help"), 3).contains("--command");
    }

    public List<String> targetModels(String raw) {
        List<String> defaults = List.of("gpt-5.3-codex", "gpt-5.2-codex", "gpt-5.2");
        List<String> parsed = splitModelInput(raw);
        List<String> merged = new ArrayList<>(parsed);
        for (String value : defaults) {
            if (merged.stream().noneMatch(item -> item.equalsIgnoreCase(value))) {
                merged.add(value);
            }
        }
        return merged;
    }

    private List<String> splitModelInput(String raw) {
        String normalized = firstNonBlank(raw, "").replace('，', ',').replace('；', ';');
        List<String> values = new ArrayList<>();
        for (String token : normalized.split("[,;|\\s]+")) {
            String value = token.trim();
            if (TOKEN_PATTERN.matcher(value).matches() && values.stream().noneMatch(item -> item.equalsIgnoreCase(value))) {
                values.add(value);
            }
        }
        return values;
    }

    private PatchResult applyAllowlistPatch(String content, List<String> models) {
        Matcher matcher = Pattern.compile("([A-Za-z_$][\\w$]*)=new Set\\(\\[(.*?)]\\)", Pattern.DOTALL).matcher(content);
        StringBuffer buffer = new StringBuffer();
        boolean touched = false;
        while (matcher.find()) {
            String name = matcher.group(1);
            String body = matcher.group(2);
            String replacement = matcher.group(0);
            if (!name.toUpperCase(Locale.ROOT).contains("AUTH_ONLY")
                && ("SUe".equals(name) || content.contains(":" + name + ").has(v.model)"))) {
                List<String> existing = quotedValues(body);
                long gptCount = existing.stream().filter(item -> item.startsWith("gpt-")).count();
                if (existing.contains("gpt-5.2-codex") || existing.contains("gpt-5.1-codex-mini") || gptCount >= 3) {
                    touched = true;
                    String quote = body.contains("\"") ? "\"" : "'";
                    List<String> merged = mergeUnique(models, existing);
                    String mergedBody = merged.stream().map(item -> quote + item + quote).collect(java.util.stream.Collectors.joining(","));
                    replacement = name + "=new Set([" + mergedBody + "])";
                }
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        if (touched) {
            return new PatchResult(buffer.toString(), true);
        }
        Matcher order = Pattern.compile("(MODEL_ORDER_BY_AUTH_METHOD\\s*=\\s*\\{.*?apikey\\s*:\\s*\\[)(.*?)(])", Pattern.DOTALL).matcher(content);
        if (order.find()) {
            String body = order.group(2);
            String quote = body.contains("\"") ? "\"" : "'";
            String mergedBody = mergeUnique(models, quotedValues(body)).stream()
                .map(item -> quote + item + quote)
                .collect(java.util.stream.Collectors.joining(","));
            return new PatchResult(content.substring(0, order.start(2)) + mergedBody + content.substring(order.end(2)), true);
        }
        return new PatchResult(content, false);
    }

    private PatchResult applyApiKeyFilterPatch(String content, List<String> models) {
        String patched = content;
        boolean gateOk = false;
        String expected = "i===\"chatgpt\"||i===\"apikey\"?!0:";
        if (patched.contains(expected)) {
            gateOk = true;
        } else {
            String source = "i===\"chatgpt\"?!0:(i===\"copilot\"?kUe:SUe).has(v.model)";
            String target = "i===\"chatgpt\"||i===\"apikey\"?!0:(i===\"copilot\"?kUe:SUe).has(v.model)";
            if (patched.contains(source)) {
                patched = patched.replace(source, target);
                gateOk = true;
            } else {
                Matcher matcher = Pattern.compile("i===\"chatgpt\"\\?!0:\\(i===\"copilot\"\\?([A-Za-z_$][\\w$]*):([A-Za-z_$][\\w$]*)\\)\\.has\\(v\\.model\\)").matcher(patched);
                if (matcher.find()) {
                    String replacement = "i===\"chatgpt\"||i===\"apikey\"?!0:(i===\"copilot\"?" + matcher.group(1) + ":" + matcher.group(2) + ").has(v.model)";
                    patched = patched.substring(0, matcher.start()) + replacement + patched.substring(matcher.end());
                    gateOk = true;
                }
            }
        }
        patched = applyChatGptAuthGuardPatch(patched);
        patched = applyChatGptAuthOnlyModelsPatch(patched, models);
        return new PatchResult(patched, gateOk);
    }

    private String applyChatGptAuthOnlyModelsPatch(String content, List<String> models) {
        Matcher matcher = Pattern.compile("CHAT_GPT_AUTH_ONLY_MODELS\\s*=\\s*new Set\\(\\[(.*?)]\\)", Pattern.DOTALL).matcher(content);
        if (!matcher.find()) {
            return content;
        }
        String body = matcher.group(1);
        String quote = body.contains("\"") ? "\"" : "'";
        List<String> filtered = quotedValues(body).stream()
            .filter(item -> models.stream().noneMatch(model -> model.equalsIgnoreCase(item)))
            .toList();
        String replacement = filtered.stream().map(item -> quote + item + quote).collect(java.util.stream.Collectors.joining(","));
        return content.substring(0, matcher.start(1)) + replacement + content.substring(matcher.end(1));
    }

    private String applyChatGptAuthGuardPatch(String content) {
        String marker = "CHAT_GPT_AUTH_ONLY_MODELS.has(normalizeModel(mt))";
        if (!content.contains(marker)) {
            return content;
        }
        Matcher already = Pattern.compile("[A-Za-z_$][\\w$]*!==\"apikey\"\\s*&&\\s*!!mt\\s*&&\\s*CHAT_GPT_AUTH_ONLY_MODELS\\.has\\(normalizeModel\\(mt\\)\\)").matcher(content);
        if (already.find()) {
            return content;
        }
        int index = content.indexOf(marker);
        String window = content.substring(Math.max(0, index - 800), index);
        Matcher matcher = Pattern.compile("([A-Za-z_$][\\w$]*)===\"(?:chatgpt|apikey)\"").matcher(window);
        String authVar = "";
        while (matcher.find()) {
            authVar = matcher.group(1);
        }
        if (authVar.isBlank()) {
            return content;
        }
        String source = "&&!!mt&&CHAT_GPT_AUTH_ONLY_MODELS.has(normalizeModel(mt))";
        if (content.contains(source)) {
            return content.replace(source, "&&" + authVar + "!=\"apikey\"&&!!mt&&CHAT_GPT_AUTH_ONLY_MODELS.has(normalizeModel(mt))");
        }
        Matcher spaced = Pattern.compile("&&\\s*!!mt\\s*&&\\s*CHAT_GPT_AUTH_ONLY_MODELS\\.has\\(normalizeModel\\(mt\\)\\)").matcher(content);
        if (spaced.find()) {
            return content.substring(0, spaced.start()) + "&& " + authVar + "!=\"apikey\" && !!mt && CHAT_GPT_AUTH_ONLY_MODELS.has(normalizeModel(mt))"
                + content.substring(spaced.end());
        }
        return content;
    }

    private PatchResult applyApiKeyOrderPatch(String content, List<String> models) {
        Matcher matcher = Pattern.compile("i===\"apikey\"&&\\(\\(\\)=>\\{const Y=\\[(.*?)]\\,X=new Map\\(Y\\.map\\(\\(A,R\\)=>\\[A,R]\\)\\);", Pattern.DOTALL).matcher(content);
        if (!matcher.find()) {
            return new PatchResult(content, false);
        }
        String body = matcher.group(1);
        String quote = body.contains("\"") ? "\"" : "'";
        String merged = mergeUnique(models, quotedValues(body)).stream()
            .map(item -> quote + item + quote)
            .collect(java.util.stream.Collectors.joining(","));
        String patched = body.equals(merged) ? content : content.substring(0, matcher.start(1)) + merged + content.substring(matcher.end(1));
        int sortIndex = patched.indexOf("m.models.sort(", matcher.start());
        if (sortIndex < 0) {
            return new PatchResult(patched, false);
        }
        String segment = patched.substring(matcher.start(), Math.min(patched.length(), sortIndex + 500));
        StringBuilder injection = new StringBuilder();
        for (String model : models) {
            String marker = "m.models.find(A=>A.model===\"" + model + "\")";
            if (!segment.contains(marker)) {
                injection.append("m.models.find(A=>A.model===\"").append(model)
                    .append("\")||m.models.unshift({model:\"").append(model)
                    .append("\",supportedReasoningEfforts:").append(reasoningEffortsLiteral())
                    .append(",defaultReasoningEffort:\"medium\"}),");
            }
        }
        if (injection.isEmpty()) {
            return new PatchResult(patched, true);
        }
        return new PatchResult(patched.substring(0, sortIndex) + injection + patched.substring(sortIndex), true);
    }

    private PatchResult applyInitialDataPatch(String content, List<String> models) {
        String marker = "initialData:i===\"apikey\"?{data:[";
        if (!content.contains(marker)) {
            return new PatchResult(content, false);
        }
        String desired = "initialData:i===\"apikey\"?{data:["
            + models.stream()
            .map(model -> "{model:\"" + model + "\",supportedReasoningEfforts:" + reasoningEffortsLiteral() + ",defaultReasoningEffort:\"medium\",isDefault:!1}")
            .collect(java.util.stream.Collectors.joining(","))
            + "]}:void 0";
        if (content.contains(desired)) {
            return new PatchResult(content, true);
        }
        Matcher matcher = Pattern.compile("initialData:i===\\\"apikey\\\"\\?\\{data:\\[(.*?)]}:void 0", Pattern.DOTALL).matcher(content);
        if (!matcher.find()) {
            return new PatchResult(content, false);
        }
        return new PatchResult(content.substring(0, matcher.start()) + desired + content.substring(matcher.end()), true);
    }

    private String reasoningEffortsLiteral() {
        return "[{reasoningEffort:\"minimal\",description:\"minimal effort\"},"
            + "{reasoningEffort:\"low\",description:\"low effort\"},"
            + "{reasoningEffort:\"medium\",description:\"medium effort\"},"
            + "{reasoningEffort:\"high\",description:\"high effort\"},"
            + "{reasoningEffort:\"xhigh\",description:\"xhigh effort\"}]";
    }

    private void ensureOpenOnStartup(Path workspace) throws IOException {
        Path settings = workspace.resolve(".vscode").resolve("settings.json");
        Map<String, Object> data = readJsonMapWithComments(settings);
        data.put("chatgpt.openOnStartup", true);
        writeJson(settings, data);
    }

    private void tryOpenSessionUri(String sessionId, String cwd) {
        if (isBlank(sessionId)) {
            return;
        }
        List<String> uris = List.of(
            "vscode://openai.chatgpt/local/" + encode(sessionId),
            "vscode://openai.chatgpt/thread-overlay/" + encode(sessionId),
            "vscode://openai.chatgpt/remote/" + encode(sessionId)
        );
        List<String> attempts = new ArrayList<>();
        for (String uri : uris) {
            try {
                openUrl(uri);
                attempts.add("OK | uri=" + uri);
                logLine("VS Code URI Debug", "session_id=" + sessionId + "\ncwd=" + cwd + "\n" + String.join("\n", attempts));
                return;
            } catch (Exception e) {
                attempts.add("FAIL | uri=" + uri + " | error=" + e.getMessage());
            }
        }
        logLine("VS Code URI Debug", "session_id=" + sessionId + "\ncwd=" + cwd + "\n" + String.join("\n", attempts));
    }

    private List<Path> settingsPaths() {
        if (isBlank(System.getenv("APPDATA"))) {
            return List.of();
        }
        return List.of(
            Path.of(System.getenv("APPDATA"), "Code", "User", "settings.json"),
            Path.of(System.getenv("APPDATA"), "Code - Insiders", "User", "settings.json"),
            Path.of(System.getenv("APPDATA"), "VSCodium", "User", "settings.json"),
            Path.of(System.getenv("APPDATA"), "Cursor", "User", "settings.json")
        ).stream().filter(Files::exists).toList();
    }

    private Map<String, Object> readJsonMapWithComments(Path path) {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(removeJsonComments(readText(path)), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private boolean isPreRelease(com.fasterxml.jackson.databind.JsonNode node) {
        String flags = node.path("flags").asText("").toLowerCase(Locale.ROOT);
        if (flags.contains("prerelease")) {
            return true;
        }
        for (var property : node.path("properties")) {
            if ("Microsoft.VisualStudio.Code.PreRelease".equals(property.path("key").asText(""))
                && "true".equalsIgnoreCase(property.path("value").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private String targetPlatform(com.fasterxml.jackson.databind.JsonNode node) {
        String platform = node.path("targetPlatform").asText("");
        if (!platform.isBlank()) {
            return platform.toLowerCase(Locale.ROOT);
        }
        for (var property : node.path("properties")) {
            if ("Microsoft.VisualStudio.Code.TargetPlatform".equals(property.path("key").asText(""))) {
                return property.path("value").asText("").toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static class MarketplaceMeta {
        public String latestStable;
        public String latestPreview;
        public final Map<String, String> channelMap = new LinkedHashMap<>();
    }

    public static class PatchOutcome {
        public final Path backupPath;
        public final List<String> models;
        public final List<String> optionalFailed;

        public PatchOutcome(Path backupPath, List<String> models, List<String> optionalFailed) {
            this.backupPath = backupPath;
            this.models = models;
            this.optionalFailed = optionalFailed;
        }
    }
}
