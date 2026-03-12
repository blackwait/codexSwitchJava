package com.codexswitcher.model;

import java.nio.file.Path;

public class ExtensionInfo {

    private final Path path;
    private final String version;
    private String channelLabel;
    private Path indexPath;

    public ExtensionInfo(Path path, String version) {
        this.path = path;
        this.version = version;
    }

    public Path getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public String getChannelLabel() {
        return channelLabel;
    }

    public void setChannelLabel(String channelLabel) {
        this.channelLabel = channelLabel;
    }

    public Path getIndexPath() {
        return indexPath;
    }

    public void setIndexPath(Path indexPath) {
        this.indexPath = indexPath;
    }

    public String getDisplayName() {
        return path.getFileName().toString();
    }
}
