package com.codexswitcher.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UpdateService extends BaseSupport {

    public ReleaseInfo getLatestRelease() throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.github.com/repos/" + APP_REPO + "/releases/latest"))
            .header("User-Agent", "CodexSwitcher")
            .timeout(Duration.ofSeconds(6))
            .build();
        var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> data = JSON.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
        });
        String tag = firstNonBlank(String.valueOf(data.get("tag_name")), String.valueOf(data.get("name")), "-");
        return new ReleaseInfo(firstNonBlank(extractSemver(tag), tag, "-"),
            firstNonBlank(String.valueOf(data.get("html_url")), "https://github.com/" + APP_REPO + "/releases/latest"),
            "");
    }

    public String getReleaseNotes(String latestVersion) {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.github.com/repos/" + APP_REPO + "/releases?per_page=20"))
                .header("User-Agent", "CodexSwitcher")
                .timeout(Duration.ofSeconds(6))
                .build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            List<LinkedHashMap<String, Object>> releases = JSON.readValue(response.body(),
                new com.fasterxml.jackson.core.type.TypeReference<List<LinkedHashMap<String, Object>>>() {
                });
            for (Map<String, Object> item : releases) {
                String tag = firstNonBlank(String.valueOf(item.get("tag_name")), String.valueOf(item.get("name")));
                String semver = extractSemver(tag);
                if (semver.equals(extractSemver(latestVersion)) || tag.equals(latestVersion)) {
                    return firstNonBlank(String.valueOf(item.get("body")), "未找到 Release Notes。");
                }
            }
        } catch (Exception ignored) {
        }
        return "无法获取更新内容。";
    }

    public CompareResult compare(String local, String latest) {
        String result = compareSemver(local, latest);
        if ("eq".equals(result)) {
            return new CompareResult("状态：已是最新版本。", false, 0);
        }
        if ("gt".equals(result)) {
            return new CompareResult("状态：本地版本 " + extractSemver(local) + " 高于最新 " + extractSemver(latest) + "。", false, 0);
        }
        if ("lt".equals(result)) {
            return new CompareResult("状态：发现新版本 " + extractSemver(latest) + "。", true, Math.max(countReleasesBehind(extractSemver(local), extractSemver(latest)), 1));
        }
        return new CompareResult("状态：无法比较版本。", false, 0);
    }

    public int countReleasesBehind(String local, String latest) {
        if (local.equals(latest)) {
            return 0;
        }
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.github.com/repos/" + APP_REPO + "/releases?per_page=100"))
                .header("User-Agent", "CodexSwitcher")
                .timeout(Duration.ofSeconds(6))
                .build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            List<LinkedHashMap<String, Object>> releases = JSON.readValue(response.body(),
                new com.fasterxml.jackson.core.type.TypeReference<List<LinkedHashMap<String, Object>>>() {
                });
            List<String> versions = new ArrayList<>();
            for (Map<String, Object> item : releases) {
                String version = extractSemver(firstNonBlank(String.valueOf(item.get("tag_name")), String.valueOf(item.get("name"))));
                if (!version.isBlank() && !versions.contains(version)) {
                    versions.add(version);
                }
            }
            int latestIndex = versions.indexOf(latest);
            int localIndex = versions.indexOf(local);
            if (latestIndex >= 0 && localIndex > latestIndex) {
                return localIndex - latestIndex;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static class ReleaseInfo {
        public final String version;
        public final String url;
        public final String message;

        public ReleaseInfo(String version, String url, String message) {
            this.version = version;
            this.url = url;
            this.message = message;
        }
    }
}
