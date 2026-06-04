package com.codexswitcher.service;

import com.codexswitcher.model.CloudAuthSession;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class CloudAuthService extends BaseSupport {

    public CloudAuthSession login(String serverUrl, String username, String password) throws IOException, InterruptedException {
        if (isBlank(serverUrl)) {
            throw new IOException("服务端地址不能为空");
        }
        if (isBlank(username)) {
            throw new IOException("用户名不能为空");
        }
        if (isBlank(password)) {
            throw new IOException("密码不能为空");
        }

        String body = JSON.createObjectNode()
            .put("username", username.trim())
            .put("password", password)
            .toString();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(normalizeServerUrl(serverUrl) + "/api/auth/login"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("用户名或密码错误");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("登录失败，服务端返回：" + response.statusCode());
        }

        JsonNode root = JSON.readTree(response.body());
        String token = trimToEmpty(root.path("token").asText(""));
        if (isBlank(token)) {
            throw new IOException("登录响应缺少 token");
        }

        CloudAuthSession session = new CloudAuthSession();
        if (root.hasNonNull("userId")) {
            session.setUserId(root.path("userId").asLong());
        }
        String resolvedUsername = trimToEmpty(root.path("username").asText(""));
        session.setUsername(isBlank(resolvedUsername) ? username.trim() : resolvedUsername);
        session.setToken(token);
        return session;
    }

    private String normalizeServerUrl(String serverUrl) {
        String value = trimToEmpty(serverUrl);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
