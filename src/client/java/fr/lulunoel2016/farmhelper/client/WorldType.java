package fr.lulunoel2016.farmhelper.client;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public enum WorldType {
    LAC("minecraft:lake_world", "Lac", "🐟", 0xFF5599FF),
    FERME("minecraft:farm_world", "Ferme", "🌱", 0xFF44DD44),
    MINE("minecraft:mine_world", "Mine", "💎", 0xFFFF6666),
    SPAWN("minecraft:overworld", "Spawn/Hub", "⌂", 0xFFAAAAAA),
    ISLAND("minecraft:island_world", "Ile", "☁", 0xFFAAAAAA),
    UNKNOWN("unknown", "Inconnu", "?", 0xFF888888);

    private final String mcWorldName;
    private final String displayName;
    private final String icon;
    private final int themeColor;

    private static final Map<WorldType, List<Predicate<String>>> DETECTION_PREDICATES;
    private static final Map<WorldType, List<String>> RESOURCE_KEYWORDS;

    static {
        DETECTION_PREDICATES = Collections.unmodifiableMap(new EnumMap<WorldType, List<Predicate<String>>>(WorldType.class) {{
            put(LAC,   Arrays.asList(s -> s.contains("lac"), s -> s.contains("peche"), s -> s.contains("pêche"), s -> s.contains("poisson"), s -> s.contains("fish"), s -> s.contains("perle")));
            put(FERME, Arrays.asList(s -> s.contains("champ"), s -> s.contains("ferme"), s -> s.contains("culture"), s -> s.contains("recolte"), s -> s.contains("récolte"), s -> s.contains("orbe")));
            put(MINE,  Arrays.asList(s -> s.contains("mine"), s -> s.contains("minage"), s -> s.contains("gemme"), s -> s.contains("bloc")));
            put(SPAWN, Arrays.asList(s -> s.contains("spawn"), s -> s.contains("hub")));
        }});

        RESOURCE_KEYWORDS = Collections.unmodifiableMap(new EnumMap<WorldType, List<String>>(WorldType.class) {{
            put(LAC,   Arrays.asList("poisson", "perle"));
            put(FERME, Arrays.asList("culture", "orbe"));
            put(MINE,  Arrays.asList("bloc", "gemme"));
        }});
    }

    WorldType(String mcWorldName, String displayName, String icon, int themeColor) {
        this.mcWorldName = mcWorldName;
        this.displayName = displayName;
        this.icon = icon;
        this.themeColor = themeColor;
    }

    public String getMcWorldName() { return mcWorldName; }
    public String getDisplayName() { return displayName; }
    public String getIcon()        { return icon; }
    public int getThemeColor()     { return themeColor; }

    public static WorldType fromMcWorldName(String name) {
        return Stream.of(values())
                .filter(w -> w.mcWorldName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public static WorldType detectFromText(String normalizedText) {
        final String lower = normalizedText.toLowerCase();
        return DETECTION_PREDICATES.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(p -> p.test(lower)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public static WorldType detectFromResources(Map<String, String> entries) {
        return entries.keySet().stream()
                .map(String::toLowerCase)
                .flatMap(lower -> RESOURCE_KEYWORDS.entrySet().stream()
                        .filter(e -> e.getValue().stream().anyMatch(lower::contains))
                        .map(Map.Entry::getKey))
                .findFirst()
                .orElse(null);
    }

    public static WorldType detectEventType(String normalizedText) {
        final String lower = normalizedText.toLowerCase();
        return DETECTION_PREDICATES.entrySet().stream()
                .filter(e -> e.getKey() != SPAWN && e.getKey() != ISLAND && e.getKey() != UNKNOWN)
                .filter(e -> e.getValue().stream().anyMatch(p -> p.test(lower)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
