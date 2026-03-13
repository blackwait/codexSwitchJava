package com.codexswitcher.service;

import com.codexswitcher.model.Account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OpencodeService extends BaseSupport {

    public Path getConfigPath() {
        return Path.of(System.getProperty("user.home"), ".config", "opencode", "opencode.json");
    }

    public Map<String, Object> loadConfig() {
        return readJsonMap(getConfigPath());
    }

    public String loadConfigText() throws IOException {
        return readText(getConfigPath());
    }

    public void saveConfig(Map<String, Object> data) throws IOException {
        writeJson(getConfigPath(), data);
        Files.writeString(getConfigPath(), System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    public Map<String, Object> buildConfig(Account account) {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> provider = new LinkedHashMap<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", firstNonBlank(account.getName(), "xyai"));
        entry.put("npm", "@ai-sdk/openai");
        entry.put("models", Map.of(
            "gpt-5.2", Map.of("name", "gpt-5.2"),
            "gpt-5.2-codex", Map.of("name", "gpt-5.2-codex")
        ));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("apiKey", account.getApiKey());
        options.put("baseURL", account.getBaseUrl());
        options.put("options", Map.of(
            "reasoningEffort", "high",
            "textVerbosity", "low",
            "reasoningSummary", "auto"
        ));
        options.put("setCacheKey", true);
        entry.put("options", options);
        provider.put(firstNonBlank(account.getName(), "xyai"), entry);
        config.put("provider", provider);
        config.put("$schema", "https://opencode.ai/config.json");
        return config;
    }

    public Map<String, Object> updateConfigWithAccount(Map<String, Object> raw, Account account) {
        Map<String, Object> data = raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
        Map<String, Object> provider = asMap(data.get("provider"));
        if (provider.isEmpty()) {
            return buildConfig(account);
        }
        String name = firstNonBlank(account.getName(), "xyai");
        String key = provider.containsKey(name) ? name : provider.keySet().iterator().next();
        Map<String, Object> entry = asMap(provider.get(key));
        entry.put("name", name);
        entry.putIfAbsent("npm", "@ai-sdk/openai");
        Map<String, Object> options = asMap(entry.get("options"));
        options.put("apiKey", account.getApiKey());
        options.put("baseURL", account.getBaseUrl());
        entry.put("options", options);
        provider.put(key, entry);
        data.put("provider", provider);
        data.putIfAbsent("$schema", "https://opencode.ai/config.json");
        return data;
    }

    public Object maskApiKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object raw = entry.getValue();
                if ("apiKey".equals(key) && raw instanceof String text && !text.isBlank()) {
                    masked.put(key, "********");
                } else {
                    masked.put(key, maskApiKeys(raw));
                }
            }
            return masked;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::maskApiKeys).collect(Collectors.toList());
        }
        return value;
    }

    public Object restoreApiKeys(Object current, Object raw) {
        if (current instanceof Map<?, ?> currentMap) {
            Map<String, Object> restored = new LinkedHashMap<>();
            Map<String, Object> rawMap = asMap(raw);
            for (Map.Entry<?, ?> entry : currentMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if ("apiKey".equals(key) && "********".equals(String.valueOf(value))) {
                    restored.put(key, rawMap.get(key));
                } else {
                    restored.put(key, restoreApiKeys(value, rawMap.get(key)));
                }
            }
            return restored;
        }
        if (current instanceof List<?> currentList) {
            List<Object> rawList = asList(raw);
            List<Object> restored = new java.util.ArrayList<>();
            for (int index = 0; index < currentList.size(); index++) {
                restored.add(restoreApiKeys(currentList.get(index), index < rawList.size() ? rawList.get(index) : null));
            }
            return restored;
        }
        return current;
    }

    public VersionInfo getLocalVersion(CodexService codexService) {
        for (String name : List.of("opencode", "opencode.cmd", "opencode-ai", "opencode-ai.cmd")) {
            String executable = codexService.findExecutable(name);
            if (isBlank(executable)) {
                continue;
            }
            try {
                ProcessBuilder builder = new ProcessBuilder(executable, "--version");
                builder.environment().putAll(codexService.buildCommandEnvironment());
                Process process = builder.start();
                process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS);
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String merged = firstNonBlank(output, error);
                return new VersionInfo(true, firstNonBlank(extractSemver(merged), merged, "未知"), executable, error);
            } catch (Exception ignored) {
            }
        }
        return new VersionInfo(false, "-", "", "未找到 opencode 命令");
    }

    public VersionInfo getLatestVersion() {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://registry.npmjs.org/opencode-ai/latest"))
                .header("User-Agent", "CodexSwitcher")
                .timeout(java.time.Duration.ofSeconds(6))
                .build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var data = JSON.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {
            });
            return new VersionInfo(true, firstNonBlank(String.valueOf(data.get("version")), "-"), "", "");
        } catch (Exception e) {
            return new VersionInfo(false, "-", "", e.getMessage());
        }
    }
}
