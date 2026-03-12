package com.codexswitcher.service;

import com.codexswitcher.model.SessionMeta;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SessionService extends BaseSupport {

    public List<SessionMeta> loadSessions() {
        Path root = CODEX_DIR.resolve("sessions");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<SessionMeta> items = new ArrayList<>();
        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jsonl"))
                .forEach(path -> {
                    SessionMeta meta = readSessionMeta(path);
                    if (meta != null) {
                        try {
                            meta.setPath(path);
                            meta.setSize(Files.size(path));
                            meta.setMtime(lastModified(path));
                            items.add(meta);
                        } catch (Exception ignored) {
                        }
                    }
                });
        } catch (Exception ignored) {
        }
        items.sort(Comparator.comparingDouble(SessionMeta::getEpoch).reversed());
        return items;
    }

    public Map<String, String> loadHistoryIndex() {
        Path history = CODEX_DIR.resolve("history.jsonl");
        if (!Files.isRegularFile(history)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> index = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(history, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> data = JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });
                String sid = firstNonBlank(String.valueOf(data.get("session_id")), "");
                String text = firstNonBlank(String.valueOf(data.get("text")), "");
                if (sid.isBlank()) {
                    continue;
                }
                String previous = index.getOrDefault(sid, "");
                String merged = (previous + "\n" + text).trim().toLowerCase();
                index.put(sid, merged.length() > 2000 ? merged.substring(0, 2000) : merged);
            }
        } catch (Exception ignored) {
        }
        return index;
    }

    public SearchTerms parseKeywords(String raw, boolean andMode) {
        String normalized = firstNonBlank(raw, "").trim().toLowerCase();
        if (normalized.isBlank()) {
            return new SearchTerms(List.of(), "OR");
        }
        boolean forceOr = normalized.contains("|");
        List<String> terms = java.util.Arrays.stream(normalized.split("[\\s|]+")).filter(token -> !token.isBlank()).toList();
        return new SearchTerms(terms, forceOr ? "OR" : andMode ? "AND" : "OR");
    }

    public boolean matchText(String text, List<String> terms, String mode) {
        if (terms.isEmpty()) {
            return true;
        }
        if (isBlank(text)) {
            return false;
        }
        String lowered = text.toLowerCase();
        if ("AND".equalsIgnoreCase(mode)) {
            return terms.stream().allMatch(lowered::contains);
        }
        return terms.stream().anyMatch(lowered::contains);
    }

    public List<SessionMeta> selectDeepCandidates(List<SessionMeta> sessions, int days, int limit) {
        double cutoff = Instant.now().minusSeconds(days * 86400L).getEpochSecond();
        return sessions.stream()
            .filter(item -> item.getEpoch() >= cutoff || item.getMtime() >= cutoff)
            .limit(limit)
            .toList();
    }

    public boolean sessionContainsTerms(Path path, List<String> terms, String mode) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> data = JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });
                if (!"response_item".equals(String.valueOf(data.get("type")))) {
                    continue;
                }
                Map<String, Object> payload = asMap(data.get("payload"));
                if (!"message".equals(String.valueOf(payload.get("type")))) {
                    continue;
                }
                builder.append(extractMessageText(payload)).append('\n');
            }
            return matchText(builder.toString(), terms, mode);
        } catch (Exception e) {
            return false;
        }
    }

    public String buildRenderedText(SessionMeta meta, boolean onlyUa) {
        List<String> lines = new ArrayList<>();
        lines.add("时间：" + firstNonBlank(meta.getTimeDisplay(), "-"));
        lines.add("模型：" + firstNonBlank(meta.getModel(), "-"));
        lines.add("分支：" + firstNonBlank(meta.getBranch(), "-"));
        lines.add("目录：" + firstNonBlank(meta.getCwd(), "-"));
        lines.add("文件：" + meta.getPath());
        lines.add("");
        String previousRole = null;
        try (BufferedReader reader = Files.newBufferedReader(meta.getPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> data = JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });
                if (!"response_item".equals(String.valueOf(data.get("type")))) {
                    continue;
                }
                Map<String, Object> payload = asMap(data.get("payload"));
                if (!"message".equals(String.valueOf(payload.get("type")))) {
                    continue;
                }
                String role = firstNonBlank(String.valueOf(payload.get("role")), "");
                if (onlyUa && !List.of("user", "assistant").contains(role)) {
                    continue;
                }
                String message = extractMessageText(payload);
                if (message.isBlank()) {
                    continue;
                }
                if (previousRole != null && !previousRole.equals(role)) {
                    lines.add("------------------------------");
                }
                lines.add("[" + role + "]");
                lines.add(message);
                lines.add("");
                previousRole = role;
            }
        } catch (Exception e) {
            lines.add("读取失败：" + e.getMessage());
        }
        return String.join(System.lineSeparator(), lines).trim();
    }

    public Map<String, Object> exportJsonPayload(SessionMeta meta, boolean onlyUa) throws IOException {
        List<Object> items = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(meta.getPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> data = JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });
                if (!onlyUa) {
                    items.add(data);
                    continue;
                }
                if ("session_meta".equals(String.valueOf(data.get("type")))) {
                    items.add(data);
                    continue;
                }
                if (!"response_item".equals(String.valueOf(data.get("type")))) {
                    continue;
                }
                String role = String.valueOf(asMap(data.get("payload")).get("role"));
                if (List.of("user", "assistant").contains(role)) {
                    items.add(data);
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("rendered_text", buildRenderedText(meta, onlyUa));
        return payload;
    }

    public CleanupResult cleanup(List<SessionMeta> sessions, boolean byDate, LocalDate date, int sizeMb, boolean cleanHistory) {
        List<SessionMeta> targets = new ArrayList<>();
        if (byDate) {
            double cutoff = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            for (SessionMeta session : sessions) {
                double ts = session.getEpoch() > 0 ? session.getEpoch() : session.getMtime();
                if (ts > 0 && ts < cutoff) {
                    targets.add(session);
                }
            }
        } else {
            long limit = sizeMb * 1024L * 1024L;
            for (SessionMeta session : sessions) {
                if (session.getSize() >= limit) {
                    targets.add(session);
                }
            }
        }
        Set<String> deletedIds = new LinkedHashSet<>();
        for (SessionMeta target : targets) {
            try {
                Files.deleteIfExists(target.getPath());
                if (!isBlank(target.getId())) {
                    deletedIds.add(target.getId());
                }
            } catch (Exception ignored) {
            }
        }
        if (cleanHistory && !deletedIds.isEmpty()) {
            cleanupHistory(deletedIds);
        }
        return new CleanupResult(targets.size(), deletedIds.size(), targets.stream().mapToLong(SessionMeta::getSize).sum());
    }

    public void cleanupHistory(Set<String> deletedIds) {
        Path history = CODEX_DIR.resolve("history.jsonl");
        if (!Files.isRegularFile(history)) {
            return;
        }
        Path temp = history.resolveSibling("history.jsonl.tmp");
        try (BufferedReader reader = Files.newBufferedReader(history, StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> data = JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });
                if (!deletedIds.contains(String.valueOf(data.get("session_id")))) {
                    lines.add(JSON.writeValueAsString(data));
                }
            }
            writeText(temp, String.join(System.lineSeparator(), lines) + System.lineSeparator());
            Files.move(temp, history, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    public void resumeSessionCli(SessionMeta meta, CodexService codexService) throws IOException {
        String executable = codexService.findCodexExecutable();
        if (isBlank(executable)) {
            throw new IOException("未检测到 codex 命令。");
        }
        List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("resume");
        args.add(meta.getId());
        if (!isBlank(meta.getCwd()) && Files.isDirectory(Path.of(meta.getCwd()))) {
            args.add("--cd");
            args.add(meta.getCwd());
        }
        startDetached(args, !isBlank(meta.getCwd()) ? Path.of(meta.getCwd()) : null, Map.of());
    }

    private SessionMeta readSessionMeta(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int index = 0; index < 50; index++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> data = JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });
                if (!"session_meta".equals(String.valueOf(data.get("type")))) {
                    continue;
                }
                Map<String, Object> payload = asMap(data.get("payload"));
                SessionMeta meta = new SessionMeta();
                meta.setId(firstNonBlank(String.valueOf(payload.get("id")), ""));
                meta.setTimestamp(firstNonBlank(String.valueOf(payload.get("timestamp")), ""));
                TimeValue timeValue = formatTime(meta.getTimestamp());
                meta.setTimeDisplay(timeValue.display);
                meta.setEpoch(timeValue.epoch);
                meta.setCwd(firstNonBlank(String.valueOf(payload.get("cwd")), ""));
                meta.setModel(firstNonBlank(String.valueOf(payload.get("model_provider")), ""));
                meta.setBranch(firstNonBlank(String.valueOf(asMap(payload.get("git")).get("branch")), ""));
                return meta;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private TimeValue formatTime(String value) {
        if (value.isBlank()) {
            return new TimeValue("-", 0);
        }
        try {
            OffsetDateTime time = OffsetDateTime.parse(value.replace("Z", "+00:00"));
            OffsetDateTime local = time.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
            return new TimeValue(local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), local.toEpochSecond());
        } catch (Exception e) {
            return new TimeValue(value, 0);
        }
    }

    private String extractMessageText(Map<String, Object> payload) {
        List<Object> content = asList(payload.get("content"));
        List<String> parts = new ArrayList<>();
        for (Object item : content) {
            Map<String, Object> node = asMap(item);
            String type = firstNonBlank(String.valueOf(node.get("type")), "");
            if (List.of("input_text", "output_text", "text").contains(type)) {
                parts.add(firstNonBlank(String.valueOf(node.get("text")), ""));
            } else if (type.contains("image")) {
                parts.add("[image]");
            }
        }
        return String.join(System.lineSeparator(), parts).trim();
    }

    public static class SearchTerms {
        public final List<String> terms;
        public final String mode;

        public SearchTerms(List<String> terms, String mode) {
            this.terms = terms;
            this.mode = mode;
        }
    }

    public static class CleanupResult {
        public final int deletedSessions;
        public final int deletedHistoryIds;
        public final long deletedBytes;

        public CleanupResult(int deletedSessions, int deletedHistoryIds, long deletedBytes) {
            this.deletedSessions = deletedSessions;
            this.deletedHistoryIds = deletedHistoryIds;
            this.deletedBytes = deletedBytes;
        }
    }

    private static class TimeValue {
        private final String display;
        private final double epoch;

        private TimeValue(String display, double epoch) {
            this.display = display;
            this.epoch = epoch;
        }
    }
}
