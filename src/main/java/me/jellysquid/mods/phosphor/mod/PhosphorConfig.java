package me.jellysquid.mods.phosphor.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;

public class PhosphorConfig {
    private static final Gson gson = createGson();

    @SerializedName("enable_illegal_thread_access_warnings")
    public final boolean enableIllegalThreadAccessWarnings = true;

    @SerializedName("enable_phosphor")
    public final boolean enablePhosphor = true;

    public static PhosphorConfig loadConfig() {
        File file = getConfigFile();

        if (!file.exists()) {
            PhosphorConfig config = new PhosphorConfig();
            config.saveConfig();

            return config;
        }

        try (Reader reader = new FileReader(file)) {
            return gson.fromJson(reader, PhosphorConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize config from disk", e);
        }
    }

    private void saveConfig() {
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
