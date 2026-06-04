package com.codexswitcher.service;

import com.codexswitcher.model.CloudSyncResult;
import com.codexswitcher.model.CloudSyncSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        return pull(settings.getServerUrl(), settings.getProjectName(), settings.getAuthSession().getToken());
    }

    public CloudSyncResult pull(String serverUrl, String projectName, String token) throws IOException, InterruptedException {
        requireAuth(token);
        if (isBlank(serverUrl)) {
            throw new IOException("服务端地址不能为空");
        }
        String resolvedProject = isBlank(projectName) ? CloudSyncSettings.DEFAULT_PROJECT_NAME : projectName.trim();
        return pullFromServer(serverUrl, resolvedProject, token);
    }

    public CloudSyncResult push(String serverUrl, String projectName, String token) throws IOException, InterruptedException {
        requireAuth(token);
        if (isBlank(serverUrl)) {
            throw new IOException("服务端地址不能为空");
        }
        String resolvedProject = isBlank(projectName) ? CloudSyncSettings.DEFAULT_PROJECT_NAME : projectName.trim();
        return pushToServer(serverUrl, resolvedProject, token);
    }

    /** @deprecated use {@link #pull(String, String, String)} */
    public CloudSyncResult syncNow(String serverUrl, String projectName) throws IOException, InterruptedException {
        CloudSyncSettings settings = storeService.loadCloudSyncSettings();
        if (!settings.isLoggedIn()) {
            throw new IOException("请先登录云端账号");
        }
        return pull(serverUrl, projectName, settings.getAuthSession().getToken());
    }

    private CloudSyncResult pullFromServer(String serverUrl, String projectName, String token) throws IOException, InterruptedException {
        String normalizedServerUrl = normalizeServerUrl(serverUrl);
        String encodedProject = URLEncoder.encode(projectName, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(normalizedServerUrl + "/api/sync/" + encodedProject))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("登录已失效，请重新登录");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("服务端返回异常状态码：" + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        if (root.path("code").asInt(-1) != 0) {
            throw new IOException(root.path("message").asText("云端拉取失败"));
        }
        JsonNode dataNode = root.path("data");
        StoreService.CloudSyncApplyResult result = storeService.applyCloudAccounts(dataNode);
        String message = "拉取完成：导入 " + result.imported + " 条账号";
        if (result.activeApplied && !isBlank(result.activeAccountName)) {
            message += "，已应用 " + result.activeAccountName;
        }
        return new CloudSyncResult(true, message, result.imported, result.activeApplied, result.activeAccountName);
    }

    private CloudSyncResult pushToServer(String serverUrl, String projectName, String token) throws IOException, InterruptedException {
        String normalizedServerUrl = normalizeServerUrl(serverUrl);
        String encodedProject = URLEncoder.encode(projectName, StandardCharsets.UTF_8);
        ObjectNode payload = storeService.buildAccountsSyncPayload();
        ObjectNode body = JSON.createObjectNode();
        body.set("data", payload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(normalizedServerUrl + "/api/sync/" + encodedProject))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("登录已失效，请重新登录");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("服务端返回异常状态码：" + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        if (root.path("code").asInt(-1) != 0) {
            throw new IOException(root.path("message").asText("云端推送失败"));
        }
        int count = payload.path("accounts").size();
        return new CloudSyncResult(true, "推送完成：已上传 " + count + " 条账号", count, false, "");
    }

    private void requireAuth(String token) throws IOException {
        if (isBlank(token)) {
            throw new IOException("请先登录云端账号");
        }
    }

    private String normalizeServerUrl(String serverUrl) {
        String value = trimToEmpty(serverUrl);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
