package com.codexswitcher.model;

public class CloudSyncSettings {

    public static final String DEFAULT_SERVER_URL = "http://118.24.80.208:8080";
    public static final String DEFAULT_PROJECT_NAME = "codex-switch.accounts";

    private boolean enabled;
    private String serverUrl;
    private String projectName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}
