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
import java.util.LinkedHashMap;
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
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(buildCommandEnvironment());
            Process process = builder.start();
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
        VersionInfo npmInfo = fetchLatestVersionFromNpm();
        if (npmInfo.ok && !isBlank(npmInfo.version)) {
            return npmInfo;
        }
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.github.com/repos/openai/codex/releases/latest"))
                .header("User-Agent", "CodexSwitcher")
                .timeout(java.time.Duration.ofSeconds(6))
                .build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var data = JSON.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {
            });
            String tag = firstMeaningful(String.valueOf(data.get("tag_name")), String.valueOf(data.get("name")));
            if (!isBlank(tag)) {
                return new VersionInfo(true, firstNonBlank(extractSemver(tag), tag, "-"), "", "");
            }
            String message = firstMeaningful(String.valueOf(data.get("message")), String.valueOf(data.get("documentation_url")));
            return new VersionInfo(false, "-", "", firstNonBlank(message, npmInfo.message, "未获取到版本信息"));
        } catch (Exception e) {
            return new VersionInfo(false, "-", "", firstNonBlank(npmInfo.message, e.getMessage()));
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
            startInteractiveShell(List.of(executable, "chat"), workspace, Map.of());
        }
    }

    public void testAccount(Account account, String model) throws IOException {
        launchAccountTest(prepareAccountTestLaunch(account, model));
    }

    public int testAccounts(List<Account> accounts, String model) throws IOException {
        int started = 0;
        for (Account account : accounts) {
            if (!isTestableAccount(account)) {
                continue;
            }
            testAccount(account, model);
            started++;
        }
        return started;
    }

    public void updateCodex() throws IOException {
        if (isWindows()) {
            startDetached(List.of("cmd.exe", "/c", "start", "", "cmd.exe", "/k", "npm i -g @openai/codex@latest"), null, Map.of());
        } else {
            startInteractiveShell(List.of("sh", "-lc", "npm i -g @openai/codex@latest"), null, Map.of());
        }
    }

    public void restartCodexApp() throws IOException {
        stopRunningCodexProcesses();
        launchCodexApp();
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
            for (String ext : commandSuffixes()) {
                Path candidate = base.resolve("codex" + ext);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        String locator = isWindows()
            ? firstNonBlank(findExecutable("where"), findExecutable("where.exe"))
            : firstNonBlank(findExecutable("which"), "/usr/bin/which");
        if (!isBlank(locator)) {
            try {
                List<String> command = isWindows() ? List.of(locator, "codex") : List.of(locator, "-a", "codex");
                Process process = new ProcessBuilder(command).start();
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
        for (Path base : buildSearchPaths()) {
            for (String ext : commandSuffixes(commandName)) {
                String name = commandName.toLowerCase(Locale.ROOT).endsWith(ext) ? commandName : commandName + ext;
                Path candidate = base.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        return "";
    }

    private List<Path> buildSearchPaths() {
        List<Path> paths = new ArrayList<>();
        for (String raw : pathEntries()) {
            if (isBlank(raw)) {
                continue;
            }
            Path path = Path.of(raw);
            if (Files.isDirectory(path) && !paths.contains(path)) {
                paths.add(path);
            }
        }
        if (isWindows() && !isBlank(System.getenv("APPDATA"))) {
            Path npm = Path.of(System.getenv("APPDATA"), "npm");
            if (Files.isDirectory(npm) && !paths.contains(npm)) {
                paths.add(0, npm);
            }
        }
        if (isWindows() && !isBlank(System.getenv("USERPROFILE"))) {
            Path global = Path.of(System.getenv("USERPROFILE"), ".npm-global", "bin");
            if (Files.isDirectory(global) && !paths.contains(global)) {
                paths.add(0, global);
            }
        }
        if (isMac() || isLinux()) {
            for (Path candidate : List.of(
                Path.of(System.getProperty("user.home"), ".npm-global", "bin"),
                Path.of(System.getProperty("user.home"), ".local", "bin"),
                Path.of(System.getProperty("user.home"), ".bun", "bin"),
                Path.of("/opt/homebrew/bin"),
                Path.of("/usr/local/bin"),
                Path.of("/opt/local/bin"),
                Path.of("/usr/bin"),
                Path.of("/bin")
            )) {
                if (Files.isDirectory(candidate) && !paths.contains(candidate)) {
                    paths.add(0, candidate);
                }
            }
        }
        return paths;
    }

    private String pickBestMatch(List<String> lines) {
        for (String ext : commandSuffixes()) {
            for (String item : lines) {
                String trimmed = item.trim();
                String normalized = trimmed.toLowerCase(Locale.ROOT);
                if (!ext.isEmpty() && normalized.endsWith(ext)) {
                    return trimmed;
                }
                if (ext.isEmpty()) {
                    String fileName = Path.of(trimmed).getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!fileName.contains(".")) {
                        return trimmed;
                    }
                }
            }
        }
        return lines.get(0).trim();
    }

    AccountTestLaunch prepareAccountTestLaunch(Account account, String model) throws IOException {
        String executable = findCodexExecutable();
        if (isBlank(executable)) {
            throw new IOException("未检测到 codex 命令");
        }
        return new AccountTestLaunch(buildCodexChatCommand(executable, model), buildAccountEnv(account));
    }

    void launchAccountTest(AccountTestLaunch launch) throws IOException {
        startInteractiveShell(launch.command(), null, launch.environment());
    }

    void stopRunningCodexProcesses() {
        long currentPid = ProcessHandle.current().pid();
        List<ProcessHandle> targets = ProcessHandle.allProcesses()
            .filter(process -> process.pid() != currentPid)
            .filter(process -> isCodexProcessCommand(process.info().command().orElse("")))
            .toList();
        for (ProcessHandle process : targets) {
            try {
                process.destroy();
                ProcessHandle exited = process.onExit().completeOnTimeout(null, 2, TimeUnit.SECONDS).join();
                if (exited != null) {
                    continue;
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.onExit().completeOnTimeout(null, 2, TimeUnit.SECONDS).join();
                }
            } catch (Exception ignored) {
            }
        }
    }

    void launchCodexApp() throws IOException {
        String executable = findCodexExecutable();
        if (isBlank(executable)) {
            throw new IOException("未检测到 codex 命令");
        }
        if (isWindows() && executable.toLowerCase(Locale.ROOT).endsWith(".ps1")) {
            startDetached(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", executable, "app"), null, buildCommandEnvironment());
            return;
        }
        startDetached(List.of(executable, "app"), null, buildCommandEnvironment());
    }

    boolean isCodexProcessCommand(String command) {
        if (isBlank(command)) {
            return false;
        }
        String normalized = command.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("codexswitcher")) {
            return false;
        }
        String fileName = Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
        return "codex.exe".equals(fileName) || "codex".equals(fileName);
    }

    private List<String> buildCodexChatCommand(String executable, String model) {
        String normalizedModel = firstNonBlank(model, "gpt-5.2-codex");
        if (isWindows() && executable.toLowerCase(Locale.ROOT).endsWith(".ps1")) {
            return List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", executable, "chat", "-m", normalizedModel);
        }
        return List.of(executable, "chat", "-m", normalizedModel);
    }

    private boolean isTestableAccount(Account account) {
        if (account == null) {
            return false;
        }
        if (isBlank(account.getBaseUrl()) || isBlank(account.getApiKey())) {
            return false;
        }
        return !account.isTeam() || !isBlank(account.getOrgId());
    }

    private Map<String, String> buildAccountEnv(Account account) {
        java.util.LinkedHashMap<String, String> env = new java.util.LinkedHashMap<>();
        env.put("OPENAI_API_KEY", firstNonBlank(account.getApiKey(), ""));
        env.put("OPENAI_BASE_URL", firstNonBlank(account.getBaseUrl(), ""));
        if (account.isTeam() && !isBlank(account.getOrgId())) {
            env.put("OPENAI_ORG_ID", account.getOrgId());
        }
        return env;
    }

    private List<String> commandSuffixes() {
        return isWindows() ? List.of(".exe", ".cmd", ".bat", ".ps1", "") : List.of("", ".sh");
    }

    private List<String> commandSuffixes(String commandName) {
        return commandName.contains(".") ? List.of("") : commandSuffixes();
    }

    public Map<String, String> buildCommandEnvironment() {
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        String separator = java.io.File.pathSeparator;
        List<String> mergedPath = new ArrayList<>();
        for (Path path : buildSearchPaths()) {
            String value = path.toString();
            if (!mergedPath.contains(value)) {
                mergedPath.add(value);
            }
        }
        for (String value : pathEntries()) {
            if (!mergedPath.contains(value)) {
                mergedPath.add(value);
            }
        }
        env.put("PATH", String.join(separator, mergedPath));
        return env;
    }

    private VersionInfo fetchLatestVersionFromNpm() {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://registry.npmjs.org/@openai%2Fcodex/latest"))
                .header("User-Agent", "CodexSwitcher")
                .timeout(java.time.Duration.ofSeconds(6))
                .build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var data = JSON.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {
            });
            String version = firstMeaningful(String.valueOf(data.get("version")), String.valueOf(data.get("dist-tags")));
            if (!isBlank(version)) {
                return new VersionInfo(true, version, "", "");
            }
            return new VersionInfo(false, "-", "", firstMeaningful(String.valueOf(data.get("error")), String.valueOf(data.get("message"))));
        } catch (Exception e) {
            return new VersionInfo(false, "-", "", e.getMessage());
        }
    }

    private String firstMeaningful(String... values) {
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            String normalized = value.trim();
            if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
                continue;
            }
            return normalized;
        }
        return "";
    }

    static record AccountTestLaunch(List<String> command, Map<String, String> environment) {
    }
}
