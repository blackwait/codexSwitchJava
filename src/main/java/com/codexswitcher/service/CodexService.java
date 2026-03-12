package com.codexswitcher.service;

import com.codexswitcher.model.Account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CodexService extends BaseSupport {

    public VersionInfo getLocalVersion() {
        String executable = findCodexExecutable();
        if (isBlank(executable)) {
            return new VersionInfo(false, "-", "-", "未找到 codex 命令");
        }
        List<String> command = executable.toLowerCase(Locale.ROOT).endsWith(".ps1")
            ? List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", executable, "--version")
            : List.of(executable, "--version");
        try {
            Process process = new ProcessBuilder(command).start();
            process.waitFor(5, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String merged = firstNonBlank(output, error);
            return new VersionInfo(true, firstNonBlank(extractSemver(merged), merged, "未知"), executable, error);
        } catch (Exception e) {
            return new VersionInfo(true, "未知", executable, e.getMessage());
        }
    }

    public VersionInfo getLatestVersion() {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.github.com/repos/openai/codex/releases/latest"))
                .header("User-Agent", "CodexSwitcher")
                .timeout(java.time.Duration.ofSeconds(6))
                .build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var data = JSON.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {
            });
            String tag = firstNonBlank(String.valueOf(data.get("tag_name")), String.valueOf(data.get("name")), "-");
            return new VersionInfo(true, firstNonBlank(extractSemver(tag), tag, "-"), "", "");
        } catch (Exception e) {
            return new VersionInfo(false, "-", "", e.getMessage());
        }
    }

    public String compareVersions(String local, String latest) {
        String result = compareSemver(local, latest);
        if ("eq".equals(result)) {
            return "已是最新版本，无需更新。";
        }
        if ("gt".equals(result)) {
            return "本地版本 " + extractSemver(local) + " 高于最新 " + extractSemver(latest) + "。";
        }
        if ("lt".equals(result)) {
            return "检测到新版本：" + extractSemver(latest) + "，可更新。";
        }
        return "";
    }

    public void launchCodexCli(Path workspace) throws IOException {
        String executable = findCodexExecutable();
        if (isBlank(executable)) {
            throw new IOException("未检测到 codex 命令");
        }
        if (isWindows()) {
            String command = "& '" + executable.replace("'", "''") + "' chat";
            if (workspace != null) {
                command = "Set-Location -LiteralPath '" + workspace.toAbsolutePath().toString().replace("'", "''") + "'; " + command;
            }
            String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
            startDetached(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded), workspace, Map.of());
        } else {
            startDetached(List.of(executable, "chat"), workspace, Map.of());
        }
    }

    public void testAccount(Account account, String model) throws IOException {
        String executable = findCodexExecutable();
        if (isBlank(executable)) {
            throw new IOException("未检测到 codex 命令");
        }
        ProcessBuilder builder;
        if (isWindows()) {
            builder = new ProcessBuilder("cmd.exe", "/c", "start", "", executable, "chat", "-m", firstNonBlank(model, "gpt-5.2-codex"));
        } else {
            builder = new ProcessBuilder(executable, "chat", "-m", firstNonBlank(model, "gpt-5.2-codex"));
        }
        builder.environment().put("OPENAI_API_KEY", account.getApiKey());
        builder.environment().put("OPENAI_BASE_URL", account.getBaseUrl());
        if (account.isTeam() && !isBlank(account.getOrgId())) {
            builder.environment().put("OPENAI_ORG_ID", account.getOrgId());
        } else {
            builder.environment().remove("OPENAI_ORG_ID");
        }
        builder.start();
    }

    public void updateCodex() throws IOException {
        if (isWindows()) {
            startDetached(List.of("cmd.exe", "/c", "start", "", "cmd.exe", "/k", "npm i -g @openai/codex@latest"), null, Map.of());
        } else {
            startDetached(List.of("sh", "-lc", "npm i -g @openai/codex@latest"), null, Map.of());
        }
    }

    public String buildDebugReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("Time: ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())).append(System.lineSeparator());
        builder.append("CWD: ").append(Path.of("").toAbsolutePath()).append(System.lineSeparator());
        builder.append("OS: ").append(System.getProperty("os.name")).append(" / ").append(System.getProperty("os.arch")).append(System.lineSeparator());
        builder.append(System.lineSeparator()).append("PATH entries:").append(System.lineSeparator());
        for (String item : pathEntries()) {
            builder.append("  ").append(item).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("Search paths:").append(System.lineSeparator());
        for (Path path : buildSearchPaths()) {
            builder.append("  ").append(path).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("findCodexExecutable(): ").append(findCodexExecutable()).append(System.lineSeparator());
        return builder.toString();
    }

    public String findCodexExecutable() {
        String direct = findExecutable("codex");
        if (!isBlank(direct)) {
            return direct;
        }
        for (Path base : buildSearchPaths()) {
            for (String ext : List.of(".exe", ".cmd", ".bat", ".ps1", "")) {
                Path candidate = base.resolve("codex" + ext);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        String whereExe = firstNonBlank(findExecutable("where"), findExecutable("where.exe"));
        if (!isBlank(whereExe)) {
            try {
                Process process = new ProcessBuilder(whereExe, "codex").start();
                process.waitFor(2, TimeUnit.SECONDS);
                List<String> lines = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .filter(line -> !line.isBlank())
                    .toList();
                if (!lines.isEmpty()) {
                    return pickBestMatch(lines);
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public String findExecutable(String commandName) {
        for (String raw : pathEntries()) {
            Path base = Path.of(raw);
            for (String ext : List.of(".exe", ".cmd", ".bat", ".ps1", "")) {
                String name = commandName.toLowerCase(Locale.ROOT).endsWith(ext) ? commandName : commandName + ext;
                Path candidate = base.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        return "";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private List<Path> buildSearchPaths() {
        List<Path> paths = new ArrayList<>();
        for (String raw : pathEntries()) {
            paths.add(Path.of(raw));
        }
        if (!isBlank(System.getenv("APPDATA"))) {
            Path npm = Path.of(System.getenv("APPDATA"), "npm");
            if (Files.isDirectory(npm)) {
                paths.add(0, npm);
            }
        }
        if (!isBlank(System.getenv("USERPROFILE"))) {
            Path global = Path.of(System.getenv("USERPROFILE"), ".npm-global", "bin");
            if (Files.isDirectory(global)) {
                paths.add(0, global);
            }
        }
        return paths;
    }

    private String pickBestMatch(List<String> lines) {
        for (String ext : List.of(".exe", ".cmd", ".bat", ".ps1", "")) {
            for (String item : lines) {
                String normalized = item.trim().toLowerCase(Locale.ROOT);
                if (!ext.isEmpty() && normalized.endsWith(ext)) {
                    return item.trim();
                }
                if (ext.isEmpty() && !normalized.contains(".")) {
                    return item.trim();
                }
            }
        }
        return lines.get(0).trim();
    }
}
