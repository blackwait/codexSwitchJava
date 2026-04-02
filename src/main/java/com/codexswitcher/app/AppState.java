package com.codexswitcher.app;

import com.codexswitcher.model.Account;
import com.codexswitcher.model.CloudSyncSettings;

import java.nio.file.Path;

public class AppState {

    private Account activeAccount;
    private String codexPath;
    private String codexVersion;
    private Path vscodeInstallDir;
    private CloudSyncSettings cloudSyncSettings = new CloudSyncSettings();
    private String cloudSyncStatus = "";

    public Account getActiveAccount() {
        return activeAccount;
    }

    public void setActiveAccount(Account activeAccount) {
        this.activeAccount = activeAccount;
    }

    public String getCodexPath() {
        return codexPath;
    }

    public void setCodexPath(String codexPath) {
        this.codexPath = codexPath;
    }

    public String getCodexVersion() {
        return codexVersion;
    }

    public void setCodexVersion(String codexVersion) {
        this.codexVersion = codexVersion;
    }

    public Path getVscodeInstallDir() {
        return vscodeInstallDir;
    }

    public void setVscodeInstallDir(Path vscodeInstallDir) {
        this.vscodeInstallDir = vscodeInstallDir;
    }

    public CloudSyncSettings getCloudSyncSettings() {
        return cloudSyncSettings;
    }

    public void setCloudSyncSettings(CloudSyncSettings cloudSyncSettings) {
        this.cloudSyncSettings = cloudSyncSettings;
    }

    public String getCloudSyncStatus() {
        return cloudSyncStatus;
    }

    public void setCloudSyncStatus(String cloudSyncStatus) {
        this.cloudSyncStatus = cloudSyncStatus;
    }
}
