package com.codexswitcher.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAiStatusService extends BaseSupport {

    public String getStatusHtml() throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://status.openai.com/api/v2/summary.json"))
            .header("User-Agent", "CodexSwitcher")
            .timeout(Duration.ofSeconds(6))
            .build();
        var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = JSON.readTree(response.body());
        String desc = root.path("status").path("description").asText("-");
        String indicator = root.path("status").path("indicator").asText("-");
        Map<String, String> statusText = Map.of(
            "operational", "正常",
            "degraded_performance", "性能下降",
            "partial_outage", "部分中断",
            "major_outage", "严重故障",
            "under_maintenance", "维护中",
            "unknown", "未知"
        );
        Map<String, String> colors = Map.of(
            "under_maintenance", "#5bc0de",
            "degraded_performance", "#f0ad4e",
            "partial_outage", "#fd7e14",
            "major_outage", "#d9534f",
            "unknown", "#888888"
        );
        List<String> abnormal = new ArrayList<>();
        List<String> normal = new ArrayList<>();
        for (JsonNode component : root.path("components")) {
            String name = component.path("name").asText("");
            String raw = component.path("status").asText("unknown");
            String text = "[" + statusText.getOrDefault(raw, raw) + "] " + name;
            if ("operational".equals(raw)) {
                normal.add(escape(text));
            } else {
                abnormal.add("<span style='color:" + colors.getOrDefault(raw, "#d9534f") + ";'>" + escape(text) + "</span>");
            }
        }
        StringBuilder html = new StringBuilder();
        html.append("<b>总体状态：</b>").append(escape(desc)).append(" (").append(escape(indicator)).append(")<br><br>");
        if (!abnormal.isEmpty()) {
            html.append("<b>异常/需关注：</b><br>");
            abnormal.forEach(line -> html.append(line).append("<br>"));
            html.append("<br>");
        }
        html.append("<b>组件状态：</b><br>");
        normal.forEach(line -> html.append(line).append("<br>"));
        return html.toString();
    }

    private String escape(String text) {
        return firstNonBlank(text, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
