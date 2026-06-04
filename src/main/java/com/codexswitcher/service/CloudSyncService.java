package com.codexswitcher.service;

import com.codexswitcher.model.CloudSyncResult;
import com.codexswitcher.model.CloudSyncSettings;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class CloudSyncService extends BaseSupport {

    private final StoreService storeService;

    public CloudSyncService(StoreService storeService) {
        this.storeService = storeService;
    }

    public CloudSyncResult syncIfEnabled() throws IOException, InterruptedException {
        CloudSyncSettings settings = storeService.loadCloudSyncSettings();
        if (!settings.isEnabled()) {
            return new CloudSyncResult(false, "云端同步未启用", 0, false, "");
        }
        return syncFromServer(settings.getServerUrl(), settings.getProjectName());
    }

    public CloudSyncResult syncNow(String serverUrl, String projectName) throws IOException, InterruptedException {
        if (isBlank(serverUrl)) {
            throw new IOException("服务端地址不能为空");
        }
        String resolvedProject = isBlank(projectName) ? CloudSyncSettings.DEFAULT_PROJECT_NAME : projectName.trim();
        return syncFromServer(serverUrl, resolvedProject);
    }

    private CloudSyncResult syncFromServer(String serverUrl, String projectName) throws IOException, InterruptedException {
        String normalizedServerUrl = normalizeServerUrl(serverUrl);
        String encodedProject = URLEncoder.encode(projectName, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(normalizedServerUrl + "/api/sync/" + encodedProject))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("服务端返回异常状态码：" + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        if (root.path("code").asInt(-1) != 0) {
            throw new IOException(root.path("message").asText("云端同步接口返回失败"));
        }
        JsonNode dataNode = root.path("data");
        StoreService.CloudSyncApplyResult result = storeService.applyCloudAccounts(dataNode);
        String message = "云端同步完成：拉取 " + result.imported + " 条";
        if (result.activeApplied && !isBlank(result.activeAccountName)) {
            message += "，已应用 " + result.activeAccountName;
        }
        return new CloudSyncResult(true, message, result.imported, result.activeApplied, result.activeAccountName);
    }

    private String normalizeServerUrl(String serverUrl) {
        String value = trimToEmpty(serverUrl);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
