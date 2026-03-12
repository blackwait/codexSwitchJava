package com.codexswitcher.model;

public class Account {

    private String name;
    private String baseUrl;
    private String apiKey;
    private String orgId;
    private boolean team;
    private String accountType;

    public Account() {
    }

    public Account(String name, String baseUrl, String apiKey, String orgId, boolean team, String accountType) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.orgId = orgId;
        this.team = team;
        this.accountType = accountType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public boolean isTeam() {
        return team;
    }

    public void setTeam(boolean team) {
        this.team = team;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getDisplayText() {
        String prefix = team ? "[Team] " : "[" + ("official".equals(accountType) ? "官方" : "中转") + "] ";
        return prefix + name + " -> " + baseUrl;
    }
}
