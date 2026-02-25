package fr.lulunoel2016.farmhelper.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TickHandler {

    private static KeyBinding configKey;
    private static KeyBinding overlayToggleKey;

    private static final WorldType[] TRACKED_WORLDS =
            { WorldType.LAC, WorldType.FERME, WorldType.MINE, WorldType.SPAWN, WorldType.ISLAND };

    public static void register() {
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelper.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.farmhelper"));
        overlayToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelper.overlay_toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.farmhelper"));
        Overlay.register();
        ClientTickEvents.END_CLIENT_TICK.register(TickHandler::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        final String currentWorld = client.world.getRegistryKey().getValue().toString();

        Stream.of(TRACKED_WORLDS)
                .map(wt -> Map.entry(wt.getMcWorldName(), WorldStats.getInstance(wt.getMcWorldName())))
                .forEach(e -> Optional.ofNullable(e.getValue()).ifPresent(
                        ws -> { if (e.getKey().equals(currentWorld)) ws.resumeSession(); else ws.pauseSession(); }
                ));

        RecapParser.tickFlush();

        drainKey(configKey,       () -> client.setScreen(new OverlayConfigScreen()));
        drainKey(overlayToggleKey, () -> {
            OverlayConfig cfg = OverlayConfig.getInstance();
            cfg.visible = !cfg.visible;
            cfg.save();
            client.player.sendMessage(
                    Text.literal("[FarmHelper] ").formatted(Formatting.GOLD)
                            .append(Text.literal("Overlay " + (cfg.visible ? "visible" : "masqué")).formatted(Formatting.AQUA)),
                    false);
        });
    }

    private static void drainKey(KeyBinding key, Runnable action) {
        while (key.wasPressed()) action.run();
    }
}
