package me.jellysquid.mods.phosphor.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;

// This class will be initialized very early and should never load any game/mod code.
public class PhosphorConfig {
    private static final Gson gson = createGson();

    private static PhosphorConfig INSTANCE;

    @SerializedName("enable_illegal_thread_access_warnings")
    public boolean enableIllegalThreadAccessWarnings = true;

    @SerializedName("enable_phosphor")
    public boolean enablePhosphor = true;

    @SerializedName("show_patreon_message")
    public boolean showPatreonMessage = true;

    public static PhosphorConfig instance() {
        return INSTANCE;
    }

    public static PhosphorConfig loadConfig() {
        if (INSTANCE != null) {
            return INSTANCE;
        }

        File file = getConfigFile();

        PhosphorConfig config;

        if (!file.exists()) {
            config = new PhosphorConfig();
            config.saveConfig();
        } else {
            try (Reader reader = new FileReader(file)) {
                config = gson.fromJson(reader, PhosphorConfig.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to deserialize config from disk", e);
            }
        }

        INSTANCE = config;

        return config;
    }

    public void saveConfig() {
        File dir = getConfigDirectory();

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("Could not create configuration directory at '" + dir.getAbsolutePath() + "'");
            }
        } else if (!dir.isDirectory()) {
            throw new RuntimeException("Configuration directory at '" + dir.getAbsolutePath() + "' is not a directory");
        }

        try (Writer writer = new FileWriter(getConfigFile())) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize config to disk", e);
        }
    }

    private static File getConfigDirectory() {
        return new File("config");
    }

    private static File getConfigFile() {
        return new File(getConfigDirectory(), "phosphor.json");
    }

    private static Gson createGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
