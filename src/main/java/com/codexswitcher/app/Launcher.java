package com.codexswitcher.app;

import javax.swing.SwingUtilities;
import java.awt.Toolkit;

public final class Launcher {

    static {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            System.setProperty("apple.awt.application.name", "CodexSwitcher");
            Thread prewarm = new Thread(() -> {
                try {
                    SwingUtilities.invokeAndWait(Toolkit::getDefaultToolkit);
                } catch (Exception ignored) {
                }
            }, "awt-prewarm");
            prewarm.setDaemon(true);
            prewarm.start();
        }
    }

    private Launcher() {
    }

    public static void main(String[] args) {
        CodexSwitcherApplication.main(args);
    }
}
