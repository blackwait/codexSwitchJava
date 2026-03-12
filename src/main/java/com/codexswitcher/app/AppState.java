package com.codexswitcher.app;

import com.codexswitcher.model.Account;

import java.nio.file.Path;

public class AppState {

    private Account activeAccount;
    private String codexPath;
    private String codexVersion;
    private Path vscodeInstallDir;

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
}
