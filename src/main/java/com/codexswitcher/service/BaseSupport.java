package com.codexswitcher.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseSupport {

    public static final String APP_TITLE = "Codex Switcher";
    public static final String APP_VERSION = "2.0.9";
    public static final String APP_REPO = "nkosi-fang/CodexSwitcher";
    public static final Path CODEX_DIR = Path.of(System.getProperty("user.home"), ".codex");
    public static final Path PROFILE_STORE = CODEX_DIR.resolve("codex_profiles.json");
    public static final Path CONFIG_PATH = CODEX_DIR.resolve("config.toml");
    public static final Path AUTH_PATH = CODEX_DIR.resolve("auth.json");
    public static final Path LOG_PATH = CODEX_DIR.resolve("codex_switcher.log");
    public static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    public static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public static final Pattern SEMVER = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    public static final Pattern PING_REGEX = Pattern.compile("(?:time|时间)[=<]?\\s*(\\d+)\\s*ms", Pattern.CASE_INSENSITIVE);

    public static String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    public static String extractSemver(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = SEMVER.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static void ensureParent(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    public static String readText(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    public static void writeText(Path path, String text) throws IOException {
        ensureParent(path);
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static Map<String, Object> readJsonMap(Path path) {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    public static void writeJson(Path path, Object value) throws IOException {
        ensureParent(path);
        JSON.writeValue(path.toFile(), value);
    }

    public static String removeJsonComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlock.replaceAll("(?m)//.*$", "");
    }

    public static void openPath(Path path) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(path.toFile());
            return;
        }
        startDetached(List.of("explorer.exe", path.toAbsolutePath().toString()), null, Map.of());
    }

    public static void openUrl(String url) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI.create(url));
            return;
        }
        startDetached(List.of("cmd.exe", "/c", "start", "", url), null, Map.of());
    }

    public static void startDetached(List<String> command, Path cwd, Map<String, String> env) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (cwd != null) {
            builder.directory(cwd.toFile());
        }
        builder.environment().putAll(env);
        builder.start();
    }

    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    ensureParent(destination);
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path).sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    public static String runAndRead(List<String> command, long timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "timeout";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);
            }
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    public static void logLine(String title, String detail) {
        try {
            ensureParent(LOG_PATH);
            String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
            Files.writeString(
                LOG_PATH,
                "[" + timestamp + "] " + title + System.lineSeparator() + detail + System.lineSeparator() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    public static List<String> quotedValues(String body) {
        Matcher matcher = Pattern.compile("[\"']([^\"']+)[\"']").matcher(body);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    public static List<String> mergeUnique(List<String> first, List<String> second) {
        List<String> values = new ArrayList<>();
        for (String item : first) {
            if (item != null && values.stream().noneMatch(existing -> existing.equalsIgnoreCase(item))) {
                values.add(item);
            }
        }
        for (String item : second) {
            if (item != null && values.stream().noneMatch(existing -> existing.equalsIgnoreCase(item))) {
                values.add(item);
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return new ArrayList<>();
    }

    public static List<String> pathEntries() {
        return Arrays.stream(System.getenv().getOrDefault("PATH", "").split(Pattern.quote(java.io.File.pathSeparator)))
            .filter(item -> !item.isBlank())
            .toList();
    }

    public static String compareSemver(String left, String right) {
        String leftSem = extractSemver(left);
        String rightSem = extractSemver(right);
        if (isBlank(leftSem) || isBlank(rightSem)) {
            return "";
        }
        String[] leftParts = leftSem.split("\\.");
        String[] rightParts = rightSem.split("\\.");
        for (int index = 0; index < 3; index++) {
            int leftValue = Integer.parseInt(leftParts[index]);
            int rightValue = Integer.parseInt(rightParts[index]);
            if (leftValue == rightValue) {
                continue;
            }
            return leftValue > rightValue ? "gt" : "lt";
        }
        return "eq";
    }

    public static class VersionInfo {
        public final boolean ok;
        public final String version;
        public final String path;
        public final String message;

        public VersionInfo(boolean ok, String version, String path, String message) {
            this.ok = ok;
            this.version = version;
            this.path = path;
            this.message = message;
        }
    }

    protected static class PatchResult {
        public final String content;
        public final boolean ok;

        public PatchResult(String content, boolean ok) {
            this.content = content;
            this.ok = ok;
        }
    }

    public static class CompareResult {
        public final String text;
        public final boolean hasUpdate;
        public final int gapCount;

        public CompareResult(String text, boolean hasUpdate, int gapCount) {
            this.text = text;
            this.hasUpdate = hasUpdate;
            this.gapCount = gapCount;
        }
    }
}
