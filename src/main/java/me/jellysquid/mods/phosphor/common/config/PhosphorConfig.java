package me.jellysquid.mods.phosphor.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;

public class PhosphorConfig {
    private static final Gson gson = createGson();

    @SerializedName("enable_illegal_thread_access_warnings")
    public boolean enableIllegalThreadAccessWarnings = true;

    @SerializedName("enable_phosphor")
    public boolean enablePhosphor = true;

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
        try (Writer writer = new FileWriter(getConfigFile())) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize config to disk", e);
        }
    }

    private static File getConfigFile() {
        return new File("config/phosphor.json");
    }

    private static Gson createGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
