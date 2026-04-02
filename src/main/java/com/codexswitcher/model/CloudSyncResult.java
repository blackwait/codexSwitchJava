package com.codexswitcher.model;

public class CloudSyncResult {

    private final boolean success;
    private final String message;
    private final int imported;
    private final boolean activeApplied;
    private final String activeAccountName;

    public CloudSyncResult(boolean success, String message, int imported, boolean activeApplied, String activeAccountName) {
        this.success = success;
        this.message = message;
        this.imported = imported;
        this.activeApplied = activeApplied;
        this.activeAccountName = activeAccountName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public int getImported() {
        return imported;
    }

    public boolean isActiveApplied() {
        return activeApplied;
    }

    public String getActiveAccountName() {
        return activeAccountName;
    }
}
