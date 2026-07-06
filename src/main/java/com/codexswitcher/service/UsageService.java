package com.codexswitcher.service;

import com.codexswitcher.model.Account;
import com.codexswitcher.model.UsageSnapshot;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

public class UsageService extends BaseSupport {

    private final StoreService storeService;

    public UsageService(StoreService storeService) {
        this.storeService = storeService;
    }

    public UsageSnapshot fetchCurrentUsage() throws IOException, InterruptedException {
        Account account = storeService.getActiveAccount();
        if (account == null) {
            throw new IOException("未设置当前 active 账号");
        }
        if (isBlank(account.getBaseUrl()) || isBlank(account.getApiKey())) {
            throw new IOException("当前账号缺少 Base URL 或 API Key");
        }

        String baseUrl = trimToEmpty(account.getBaseUrl()).replaceAll("/+$", "");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/usage"))
            .timeout(Duration.ofSeconds(8))
            .header("Authorization", "Bearer " + account.getApiKey())
            .header("Accept", "application/json")
            .header("User-Agent", "CodexSwitcher");
        if (!isBlank(account.getOrgId())) {
            builder.header("OpenAI-Organization", account.getOrgId());
        }

        HttpResponse<String> response = HTTP.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = firstNonBlank(response.body(), "");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + brief(body));
        }

        JsonNode root = JSON.readTree(body);
        JsonNode subscription = root.path("subscription");
        return new UsageSnapshot(
            account.getName(),
            baseUrl,
            pickBoolean(root, true, "is_active", "isValid", "data.is_active", "data.isValid"),
            pickDouble(root, "remaining", "quota.remaining", "balance", "data.remaining", "data.quota.remaining", "data.balance"),
            pickText(root, "USD", "unit", "quota.unit", "data.unit", "data.quota.unit"),
            numberOrNull(subscription.path("daily_limit_usd")),
            numberOrNull(subscription.path("daily_usage_usd")),
            numberOrNull(subscription.path("weekly_limit_usd")),
            numberOrNull(subscription.path("weekly_usage_usd")),
            LocalDateTime.now()
        );
    }

    private String brief(String text) {
        String normalized = firstNonBlank(text, "").replaceAll("\\s+", " ").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }

    private String pickText(JsonNode root, String fallback, String... paths) {
        for (String path : paths) {
            JsonNode node = path(root, path);
            if (node != null && node.isValueNode() && !node.asText("").isBlank()) {
                return node.asText();
            }
        }
        return fallback;
    }

    private boolean pickBoolean(JsonNode root, boolean fallback, String... paths) {
        for (String path : paths) {
            JsonNode node = path(root, path);
            if (node != null && node.isValueNode()) {
                return node.asBoolean(fallback);
            }
        }
        return fallback;
    }

    private Double pickDouble(JsonNode root, String... paths) {
        for (String path : paths) {
            Double value = numberOrNull(path(root, path));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double numberOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private JsonNode path(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(part);
        }
        return current;
    }
}
