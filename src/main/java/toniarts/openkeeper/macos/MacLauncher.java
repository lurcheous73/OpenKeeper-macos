/*
 * Copyright (C) 2026 OpenKeeper contributors
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.macos;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.game.data.Settings;
import toniarts.openkeeper.tools.convert.AssetsConverter;
import toniarts.openkeeper.utils.PathUtils;
import toniarts.openkeeper.utils.SettingUtils;

/**
 * macOS bootstrap that keeps AWT/Swing out of the JVM before LWJGL/GLFW starts.
 *
 * <p>OpenKeeper's historical setup UI is Swing based. On macOS GLFW must run on
 * the JVM's first thread, while initialising AWT on that same JVM can prevent
 * the LWJGL3 window from starting. This launcher performs the small amount of
 * first-run setup with native macOS dialogs and a headless asset conversion,
 * then hands control to the normal OpenKeeper main class.</p>
 */
public final class MacLauncher {

    private static final Logger LOGGER = System.getLogger(MacLauncher.class.getName());
    private static final String RELAUNCH_MARKER = "OPENKEEPER_MAC_RELAUNCHED";
    private static final String DK2_ARGUMENT = "dk2";

    private MacLauncher() {
    }

    public static void main(String[] args) throws Exception {
        if (!isMac()) {
            Main.main(args);
            return;
        }

        // Finder/Dock launches do not provide a useful writable working directory.
        // OpenKeeper stores its conversion metadata and converted assets relative to
        // the working directory, so relaunch the jpackage executable once from the
        // per-user Application Support folder before any OpenKeeper classes which
        // calculate those paths are initialised.
        if (relaunchInApplicationSupportIfNeeded(args)) {
            return;
        }

        if (!prepareDungeonKeeperData(args)) {
            return;
        }

        applyFirstRunDisplayDefaults();
        Main.main(args);
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /**
     * OpenKeeper inherits jME's 640x480 window default when no graphics settings
     * have ever been saved. On macOS that makes the first launch tiny. Seed a
     * native fullscreen mode once, then leave all later user choices alone.
     */
    private static void applyFirstRunDisplayDefaults() {
        Path settingsFile = Path.of(System.getProperty("user.home"), ".OpenKeeper", "openkeeper.properties");
        try {
            if (Files.exists(settingsFile)) {
                String persisted = Files.readString(settingsFile, StandardCharsets.UTF_8);
                if (persisted.contains("Width(int)=")
                        || persisted.contains("Height(int)=")
                        || persisted.contains("Fullscreen(bool)=")) {
                    return;
                }
            }

            if (!GLFW.glfwInit()) {
                LOGGER.log(Level.WARNING, "Could not initialise GLFW to choose the macOS fullscreen default.");
                return;
            }

            try {
                long monitor = GLFW.glfwGetPrimaryMonitor();
                GLFWVidMode mode = monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
                if (mode == null) {
                    LOGGER.log(Level.WARNING, "Could not determine the primary macOS display mode.");
                    return;
                }

                AppSettings appSettings = Settings.getInstance().getAppSettings();
                appSettings.setResolution(mode.width(), mode.height());
                appSettings.setFrequency(mode.refreshRate());
                appSettings.setFullscreen(true);
                appSettings.setVSync(true);

                Files.createDirectories(settingsFile.getParent());
                Settings.getInstance().save();
                LOGGER.log(Level.INFO, "Seeded first-run macOS fullscreen mode: {0}x{1} @ {2} Hz",
                        mode.width(), mode.height(), mode.refreshRate());
            } finally {
                GLFW.glfwTerminate();
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Could not seed macOS fullscreen settings; using OpenKeeper defaults.", ex);
        }
    }

    /**
     * @return true when a child process was launched and this process should exit
     */
    private static boolean relaunchInApplicationSupportIfNeeded(String[] args) throws IOException {
        if ("1".equals(System.getenv(RELAUNCH_MARKER))) {
            return false;
        }

        // Set by the jpackage launcher. It is absent for IDE/Gradle runs, where
        // the project working directory is already suitable for development.
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null || appPath.isBlank()) {
            return false;
        }

        Path appSupport = Path.of(System.getProperty("user.home"),
                "Library", "Application Support", "OpenKeeper");
        Files.createDirectories(appSupport);

        List<String> command = new ArrayList<>(args.length + 1);
        command.add(appPath);
        command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(appSupport.toFile());
        builder.environment().put(RELAUNCH_MARKER, "1");
        builder.inheritIO();
        builder.start();

        LOGGER.log(Level.INFO, "Relaunched OpenKeeper from writable application data folder: {0}", appSupport);
        return true;
    }

    private static boolean prepareDungeonKeeperData(String[] args) {
        String argumentFolder = getArgumentValue(args, DK2_ARGUMENT);
        if (argumentFolder != null) {
            if (!PathUtils.checkDkFolder(argumentFolder)) {
                showNativeError("The Dungeon Keeper II folder passed with -dk2 is not valid:\n" + argumentFolder);
                return false;
            }
            PathUtils.setDKIIFolder(PathUtils.fixFilePath(argumentFolder));
            SettingUtils.getInstance().saveSettings();
        }

        String dk2Folder = PathUtils.getDKIIFolder();
        if (!PathUtils.checkDkFolder(dk2Folder)) {
            dk2Folder = chooseDungeonKeeperFolder();
            if (dk2Folder == null) {
                LOGGER.log(Level.INFO, "Dungeon Keeper II folder selection was cancelled.");
                return false;
            }
            if (!PathUtils.checkDkFolder(dk2Folder)) {
                showNativeError("That folder does not contain a valid Dungeon Keeper II 1.7 installation.\n\n"
                        + "OpenKeeper expects Data/editor/maps/FrontEnd3DLevel.kwd below the selected folder.");
                return false;
            }

            PathUtils.setDKIIFolder(PathUtils.fixFilePath(dk2Folder));
            SettingUtils.getInstance().saveSettings();
        }

        if (!AssetsConverter.isConversionNeeded(Main.getSettings())) {
            return true;
        }

        return convertAssetsHeadlessly(PathUtils.getDKIIFolder());
    }

    private static boolean convertAssetsHeadlessly(String dk2Folder) {
        LOGGER.log(Level.INFO, "Initial macOS asset conversion is required. This may take a while.");

        AssetManager assetManager = JmeSystem.newAssetManager(
                Thread.currentThread().getContextClassLoader()
                        .getResource("com/jme3/asset/Desktop.cfg"));
        assetManager.registerLocator(AssetsConverter.getAssetsFolder(), FileLocator.class);

        AtomicReference<Exception> conversionError = new AtomicReference<>();
        AssetsConverter converter = new AssetsConverter(dk2Folder, assetManager) {
            @Override
            public void onUpdateStatus(Integer currentProgress, Integer totalProgress, ConvertProcess process) {
                LOGGER.log(Level.DEBUG, "Converting {0}: {1}/{2}", process, currentProgress, totalProgress);
            }

            @Override
            public void onComplete(ConvertProcess process) {
                LOGGER.log(Level.INFO, "Converted {0}", process);
            }

            @Override
            public void onError(Exception ex, ConvertProcess process) {
                conversionError.compareAndSet(null, ex);
                LOGGER.log(Level.ERROR, "Failed while converting " + process, ex);
            }
        };

        boolean success = converter.convertAssets() && conversionError.get() == null;
        if (success) {
            SettingUtils.getInstance().saveSettings();
            LOGGER.log(Level.INFO, "Dungeon Keeper II asset conversion completed.");
            return true;
        }

        Exception error = conversionError.get();
        String detail = error == null ? "Unknown conversion error" : error.toString();
        showNativeError("OpenKeeper could not convert the Dungeon Keeper II assets.\n\n" + detail);
        return false;
    }

    private static String chooseDungeonKeeperFolder() {
        String script = "set chosenFolder to choose folder with prompt "
                + "\"OpenKeeper needs your Dungeon Keeper II installation folder\"\n"
                + "POSIX path of chosenFolder";
        try {
            Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int status = process.waitFor();
            if (status != 0) {
                if (!error.isBlank() && !error.toLowerCase().contains("user canceled")) {
                    LOGGER.log(Level.WARNING, "macOS folder picker failed: {0}", error);
                }
                return null;
            }
            return output.isBlank() ? null : output;
        } catch (IOException ex) {
            LOGGER.log(Level.ERROR, "Failed to open the macOS folder picker", ex);
            showNativeError("OpenKeeper could not open the macOS folder picker.\n\n" + ex);
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static void showNativeError(String message) {
        String escaped = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        String script = "display alert \"OpenKeeper\" message \"" + escaped
                + "\" as critical buttons {\"OK\"} default button \"OK\"";
        try {
            new ProcessBuilder("/usr/bin/osascript", "-e", script).start().waitFor();
        } catch (IOException ex) {
            LOGGER.log(Level.ERROR, message, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String getArgumentValue(String[] args, String key) {
        String option = "-" + key;
        for (int i = 0; i < args.length; i++) {
            if (option.equalsIgnoreCase(args[i]) && i + 1 < args.length && !args[i + 1].startsWith("-")) {
                return args[i + 1];
            }
        }
        return null;
    }
}
