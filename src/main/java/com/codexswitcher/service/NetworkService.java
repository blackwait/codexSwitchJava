package com.codexswitcher.service;

import com.codexswitcher.model.DiagnosisResult;
import com.codexswitcher.model.ProbeResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class NetworkService extends BaseSupport {

    public DiagnosisResult probeEndpoints(String base, String apiKey, String orgId, String model, int timeoutSeconds) throws IOException, InterruptedException {
        String cleanedBase = trimToEmpty(base).replaceAll("/+$", "");
        String host = extractHost(cleanedBase);
        if (isBlank(host)) {
            throw new IOException("Base URL 无效，无法解析主机。");
        }

        Double ping = pingAverage(host, 1);
        Double http = httpHeadAverage(cleanedBase + "/models", apiKey, 1);
        boolean portOk = checkPort(cleanedBase);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "CodexSwitcher");
        if (!isBlank(orgId)) {
            headers.put("OpenAI-Organization", orgId);
        }

        DiagnosisResult result = new DiagnosisResult();
        result.setBaseHost(host);
        for (EndpointCandidate candidate : buildCandidates(cleanedBase)) {
            ProbeResult probe = requestEndpoint(candidate, headers, model, timeoutSeconds);
            result.getResults().add(probe);
            if (Boolean.TRUE.equals(probe.getOk())) {
                if (!result.getSupportedLabels().contains(candidate.label)) {
                    result.getSupportedLabels().add(candidate.label);
                }
                if (!result.getSupportedUrls().contains(candidate.url)) {
                    result.getSupportedUrls().add(candidate.url);
                }
                if (isBlank(result.getSuccessEndpoint()) && !"/models".equals(candidate.endpoint)) {
                    result.setSuccessEndpoint(candidate.endpoint);
                }
            }
        }

        Boolean modelInList = null;
        Boolean modelSupported = null;
        String modelSource = "";
        String responseModel = "";
        String responseModelSource = "";
        for (ProbeResult probe : result.getResults()) {
            if ("/models".equals(probe.getEndpoint()) && Boolean.TRUE.equals(probe.getOk())) {
                Set<String> models = parseModels(probe.getBody());
                if (!models.isEmpty()) {
                    modelInList = models.contains(model);
                }
            }
            if (Boolean.TRUE.equals(probe.getOk()) && List.of("/responses", "/chat/completions", "/completions").contains(probe.getEndpoint())) {
                modelSupported = true;
                modelSource = probe.getEndpoint();
                if (isBlank(responseModel)) {
                    responseModel = extractResponseModel(probe.getBody());
                    responseModelSource = probe.getEndpoint();
                }
            }
        }
        if (modelSupported == null) {
            for (ProbeResult probe : result.getResults()) {
                if (Boolean.FALSE.equals(probe.getOk())
                    && List.of("/responses", "/chat/completions", "/completions").contains(probe.getEndpoint())
                    && isModelError(probe.getBody())) {
                    modelSupported = false;
                    modelSource = probe.getEndpoint();
                    break;
                }
            }
        }
        result.setModelInList(modelInList);
        result.setModelSupported(modelSupported);
        result.setModelSource(modelSource);
        result.setResponseModel(responseModel);
        result.setResponseModelSource(responseModelSource);

        String errors = result.getResults().stream().map(ProbeResult::getBody).reduce("", (left, right) -> left + " " + right).toLowerCase(Locale.ROOT);
        String conclusion;
        if (!isBlank(result.getSuccessEndpoint())) {
            conclusion = "结论：链路正常（API 请求成功，接口 " + result.getSuccessEndpoint() + "）。";
        } else if (result.getSupportedLabels().stream().anyMatch(label -> label.endsWith("/models"))) {
            conclusion = "结论：仅 /models 可用，API 接口可能受限。";
        } else if (errors.contains("401") || errors.contains("403") || errors.contains("auth")) {
            conclusion = "结论：账号或 API Key 可能有误。";
        } else if (errors.contains("404") || errors.contains("not found")) {
            conclusion = "结论：接口路径可能不支持。";
        } else {
            conclusion = "结论：疑似中转服务异常。";
        }
        result.setConclusion(conclusion);

        String inList = modelInList == null ? "未知" : modelInList ? "是" : "否";
        List<String> summary = new ArrayList<>();
        summary.add("Base URL: " + cleanedBase);
        summary.add("Base Host: " + host);
        summary.add("Base 连通：Ping=" + formatMs(ping) + " / HTTP=" + formatMs(http) + " / Port=" + (portOk ? "OK" : "FAIL"));
        summary.add("");
        summary.add("可用接口：" + (result.getSupportedLabels().isEmpty() ? "无" : String.join(", ", result.getSupportedLabels())));
        if (!result.getSupportedUrls().isEmpty()) {
            summary.add("可用接口(URL)：");
            result.getSupportedUrls().forEach(url -> summary.add("- " + url));
        }
        summary.add("模型列表包含（" + model + "）： " + inList);
        if (!isBlank(responseModel)) {
            summary.add("实际返回 model：" + responseModel + "（来源 " + responseModelSource + "）");
        }
        result.setSummaryDetail(String.join(System.lineSeparator(), summary));

        List<String> detail = new ArrayList<>(summary);
        detail.add("模型可用性（" + model + "）："
            + (modelSupported == null ? "未知" : modelSupported ? "可用" : "不可用")
            + (isBlank(modelSource) ? "" : "（来源 " + modelSource + "）"));
        detail.add("");
        detail.add("接口探测结果：");
        for (ProbeResult probe : result.getResults()) {
            if (Boolean.TRUE.equals(probe.getOk())) {
                detail.add("- " + probe.getLabel() + ": OK");
            } else {
                String brief = firstNonBlank(probe.getBody(), "-");
                brief = brief.contains(System.lineSeparator()) ? brief.substring(0, brief.indexOf(System.lineSeparator())) : brief;
                detail.add("- " + probe.getLabel() + ": FAIL (" + brief + ")");
            }
        }
        result.setDetail(String.join(System.lineSeparator(), detail));
        return result;
    }

    public ProbeResult probeSingleModel(String base, String apiKey, String model, int timeoutSeconds) throws IOException, InterruptedException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "CodexSwitcher");
        return requestEndpoint(new EndpointCandidate("模型探测 /chat/completions", "/chat/completions", trimToEmpty(base).replaceAll("/+$", "") + "/chat/completions"),
            headers, model, timeoutSeconds);
    }

    public String extractHost(String baseUrl) {
        if (isBlank(baseUrl)) {
            return "";
        }
        try {
            URI uri = baseUrl.startsWith("http://") || baseUrl.startsWith("https://") ? URI.create(baseUrl) : URI.create("https://" + baseUrl);
            return firstNonBlank(uri.getHost(), "");
        } catch (Exception e) {
            return "";
        }
    }

    private List<EndpointCandidate> buildCandidates(String base) {
        return List.of(
            new EndpointCandidate("Responses /responses", "/responses", base + "/responses"),
            new EndpointCandidate("Chat Completions /chat/completions", "/chat/completions", base + "/chat/completions"),
            new EndpointCandidate("Completions /completions", "/completions", base + "/completions"),
            new EndpointCandidate("Models /models", "/models", base + "/models"),
            new EndpointCandidate("Embeddings /embeddings", "/embeddings", base + "/embeddings"),
            new EndpointCandidate("Moderations /moderations", "/moderations", base + "/moderations")
        );
    }

    private ProbeResult requestEndpoint(EndpointCandidate candidate, Map<String, String> headers, String model, int timeoutSeconds) throws IOException, InterruptedException {
        HttpResponse<String> response;
        if ("/models".equals(candidate.endpoint)) {
            response = sendRequest("GET", candidate.url, headers, null, timeoutSeconds);
        } else {
            Map<String, Object> payload = switch (candidate.endpoint) {
                case "/moderations" -> Map.of("model", "omni-moderation-latest", "input", "hello");
                case "/embeddings" -> Map.of("model", "text-embedding-3-small", "input", "hello");
                case "/chat/completions" -> Map.of("model", model, "messages", List.of(Map.of("role", "user", "content", "hello")));
                case "/completions" -> Map.of("model", model, "prompt", "hello");
                default -> Map.of("model", model, "input", "hello");
            };
            response = sendRequest("POST", candidate.url, headers, JSON.writeValueAsString(payload), timeoutSeconds);
        }
        String body = response.body();
        boolean ok = response.statusCode() >= 200 && response.statusCode() < 300 && validateSuccessBody(candidate.endpoint, body);
        if (!ok) {
            body = "HTTP " + response.statusCode() + ": " + body;
        }
        return new ProbeResult(candidate.label, candidate.endpoint, candidate.url, ok, body);
    }

    private HttpResponse<String> sendRequest(String method, String url, Map<String, String> headers, String body, int timeoutSeconds) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds));
        headers.forEach(builder::header);
        if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(firstNonBlank(body, "")));
        } else {
            builder.GET();
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private boolean validateSuccessBody(String endpoint, String body) {
        JsonNode json = parseJsonPayload(body);
        if (json == null) {
            return false;
        }
        return switch (endpoint) {
            case "/models", "/embeddings" -> json.path("data").isArray();
            case "/moderations" -> json.path("results").isArray() || json.path("id").isTextual();
            case "/chat/completions", "/completions" -> json.path("choices").isArray() || json.path("id").isTextual();
            default -> json.path("output").isArray() || json.path("id").isTextual() || json.path("data").isArray();
        };
    }

    private JsonNode parseJsonPayload(String body) {
        if (isBlank(body)) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (Exception ignored) {
        }
        String lastData = null;
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).trim();
            if (!payload.isBlank() && !payload.equals("[DONE]")) {
                lastData = payload;
            }
        }
        if (lastData != null) {
            try {
                return JSON.readTree(lastData);
            } catch (Exception ignored) {
            }
        }
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return JSON.readTree(body.substring(start, end + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Set<String> parseModels(String body) {
        JsonNode json = parseJsonPayload(body);
        if (json == null || !json.path("data").isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode node : json.path("data")) {
            String id = node.path("id").asText("");
            if (!id.isBlank()) {
                values.add(id);
            }
            String model = node.path("model").asText("");
            if (!model.isBlank()) {
                values.add(model);
            }
        }
        return values;
    }

    private String extractResponseModel(String body) {
        JsonNode json = parseJsonPayload(body);
        if (json == null) {
            return "";
        }
        return firstNonBlank(json.path("model").asText(""), json.path("response").path("model").asText(""));
    }

    private boolean isModelError(String body) {
        String text = firstNonBlank(body, "").toLowerCase(Locale.ROOT);
        return text.contains("model") && (text.contains("not found") || text.contains("unsupported") || text.contains("does not exist"));
    }

    private String formatMs(Double value) {
        return value == null ? "不可用" : String.format(Locale.ROOT, "%.0fms", value);
    }

    private Double pingAverage(String host, int attempts) {
        List<Long> values = new ArrayList<>();
        for (int index = 0; index < attempts; index++) {
            Long value = pingOnce(host);
            if (value != null) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private Long pingOnce(String host) {
        List<String> command = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
            ? List.of("ping", "-n", "1", "-w", "1000", host)
            : List.of("ping", "-c", "1", "-W", "1", host);
        String output = runAndRead(command, 5);
        Matcher matcher = PING_REGEX.matcher(output);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Double httpHeadAverage(String url, String apiKey, int attempts) {
        List<Long> values = new ArrayList<>();
        for (int index = 0; index < attempts; index++) {
            long start = System.nanoTime();
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(4))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
                HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                values.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            } catch (Exception ignored) {
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private boolean checkPort(String base) {
        try {
            URI uri = URI.create(base);
            int port = uri.getPort() > 0 ? uri.getPort() : ("http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), 3000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static class EndpointCandidate {
        private final String label;
        private final String endpoint;
        private final String url;

        private EndpointCandidate(String label, String endpoint, String url) {
            this.label = label;
            this.endpoint = endpoint;
            this.url = url;
        }
    }
}
