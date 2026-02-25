package fr.lulunoel2016.farmhelper.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.util.Objects;
import java.util.Optional;

public class OverlayConfig {

    private static volatile OverlayConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "farmhelper_overlay.json";

    public float posX = 0.01f;
    public float posY = 0.3f;
    public float scale = 1.0f;
    public boolean visible = true;
    public boolean showSessionTotals = true;
    public float opacity = 0.7f;

    public static OverlayConfig getInstance() {
        return Optional.ofNullable(instance).orElseGet(() -> {
            synchronized (OverlayConfig.class) {
                return instance == null ? (instance = load()) : instance;
            }
        });
    }

    public void save() {
        resolveConfigFile().ifPresent(file -> {
            try (Writer w = new FileWriter(file)) { GSON.toJson(this, w); }
            catch (IOException e) { System.err.println("[FarmHelper] Failed to save overlay config: " + e.getMessage()); }
        });
    }

    private static OverlayConfig load() {
        return resolveConfigFile()
                .filter(File::exists)
                .map(file -> {
                    try (Reader r = new FileReader(file)) { return GSON.fromJson(r, OverlayConfig.class); }
                    catch (Exception e) { System.err.println("[FarmHelper] Failed to load: " + e.getMessage()); return (OverlayConfig) null; }
                })
                .filter(Objects::nonNull)
                .orElseGet(() -> { OverlayConfig c = new OverlayConfig(); c.save(); return c; });
    }

    private static Optional<File> resolveConfigFile() {
        return Optional.of(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE).toFile());
    }
}
