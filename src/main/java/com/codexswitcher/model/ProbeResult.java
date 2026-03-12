package com.codexswitcher.model;

public class ProbeResult {

    private final String label;
    private final String endpoint;
    private final String url;
    private final Boolean ok;
    private final String body;

    public ProbeResult(String label, String endpoint, String url, Boolean ok, String body) {
        this.label = label;
        this.endpoint = endpoint;
        this.url = url;
        this.ok = ok;
        this.body = body;
    }

    public String getLabel() {
        return label;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getUrl() {
        return url;
    }

    public Boolean getOk() {
        return ok;
    }

    public String getBody() {
        return body;
    }
}
