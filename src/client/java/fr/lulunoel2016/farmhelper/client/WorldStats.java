package fr.lulunoel2016.farmhelper.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class WorldStats {

    private static final Map<String, WorldStats> instances = new ConcurrentHashMap<>();

    private final Map<String, String> sessionTotals = new LinkedHashMap<>();
    private final Map<String, String> lastRecap     = new LinkedHashMap<>();
    private long sessionStartTime = 0;
    private boolean hasData  = false;
    private boolean paused   = false;
    private long pauseStart  = 0;
    private long pausedTime  = 0;
    private WorldType worldType = null;

    public static WorldStats getInstance(String mcWorldName) {
        return Optional.ofNullable(mcWorldName)
                .filter(n -> !n.isEmpty())
                .map(n -> instances.computeIfAbsent(n, k -> new WorldStats()))
                .orElse(null);
    }

    public void onRecapReceived(Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) return;

        resolveWorldType(entries);

        Map<String, String> validated = DataValidator.validateForWorld(worldType, entries);
        if (validated.isEmpty()) return;

        if (sessionStartTime == 0) sessionStartTime = System.currentTimeMillis();

        lastRecap.clear();
        lastRecap.putAll(validated);

        validated.forEach((key, raw) -> {
            double incoming = parseValue(raw);
            sessionTotals.merge(key, formatValue(incoming),
                    (existing, neu) -> formatValue(parseValue(existing) + incoming));
        });

        hasData = true;
    }

    private void resolveWorldType(Map<String, String> entries) {
        if (worldType == null) {
            worldType = instances.entrySet().stream()
                    .filter(e -> e.getValue() == this)
                    .map(e -> WorldType.fromMcWorldName(e.getKey()))
                    .findFirst()
                    .orElseGet(() -> Optional.ofNullable(WorldType.detectFromResources(entries))
                            .orElse(WorldType.UNKNOWN));
        }
        if (worldType == WorldType.UNKNOWN) {
            worldType = Optional.ofNullable(WorldType.detectFromResources(entries))
                    .orElse(WorldType.UNKNOWN);
        }
    }

    public static double parseValue(String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) return 0;
        String cleaned = valueStr.replace("+", "").replace(",", ".").trim();
        String upper = cleaned.toUpperCase();

        record Suffix(String tag, double multiplier) {}
        List<Suffix> suffixes = List.of(
                new Suffix("QT", 1e18), new Suffix("QD", 1e15),
                new Suffix("T",  1e12), new Suffix("B",  1e9),
                new Suffix("M",  1e6),  new Suffix("K",  1e3)
        );

        for (Suffix s : suffixes) {
            if (upper.endsWith(s.tag())) {
                try { return Double.parseDouble(cleaned.substring(0, cleaned.length() - s.tag().length())) * s.multiplier(); }
                catch (NumberFormatException ignored) { return 0; }
            }
        }
        try { return Double.parseDouble(cleaned); } catch (NumberFormatException e) { return 0; }
    }

    public static String formatValue(double value) {
        record Tier(double threshold, double divisor, String suffix) {}
        return List.of(
                new Tier(1e18, 1e18, "Qt"), new Tier(1e15, 1e15, "Qd"),
                new Tier(1e12, 1e12, "T"),  new Tier(1e9,  1e9,  "B"),
                new Tier(1e6,  1e6,  "M"),  new Tier(1e3,  1e3,  "K")
        ).stream()
                .filter(t -> value >= t.threshold())
                .findFirst()
                .map(t -> String.format("%.2f%s", value / t.divisor(), t.suffix()))
                .orElseGet(() -> value == (long) value ? String.valueOf((long) value) : String.format("%.1f", value));
    }

    public Map<String, String> getSessionTotals() { return sessionTotals; }
    public Map<String, String> getLastRecap()     { return lastRecap; }
    public boolean hasData()                      { return hasData; }

    public void pauseSession() {
        if (!paused && sessionStartTime > 0) { paused = true; pauseStart = System.currentTimeMillis(); }
    }

    public void resumeSession() {
        if (paused) { paused = false; pausedTime += System.currentTimeMillis() - pauseStart; pauseStart = 0; }
    }

    public boolean isPaused() { return paused; }

    public String getSessionDuration() {
        if (sessionStartTime == 0) return "00:00";
        long elapsed = Math.max((paused ? pauseStart : System.currentTimeMillis()) - sessionStartTime - pausedTime, 0);
        long s = elapsed / 1000, m = s / 60, h = m / 60;
        return h > 0
                ? String.format("%dh%02dm%02ds", h, m % 60, s % 60)
                : String.format("%02dm%02ds", m, s % 60);
    }

    public void resetSession() {
        sessionTotals.clear(); lastRecap.clear();
        sessionStartTime = 0; hasData = false;
        paused = false; pauseStart = 0; pausedTime = 0;
    }

    public static void resetAll() { instances.values().forEach(WorldStats::resetSession); }

    public double getElapsedSeconds() {
        if (sessionStartTime == 0) return 0;
        return Math.max(((paused ? pauseStart : System.currentTimeMillis()) - sessionStartTime - pausedTime) / 1000.0, 1.0);
    }

    public double getPerSecond(String key) {
        return parseValue(sessionTotals.getOrDefault(key, "0")) / getElapsedSeconds();
    }

    public String getPerSecondFormatted(String key) {
        double ps = getPerSecond(key);
        if (ps <= 0)    return "0/s";
        if (ps >= 1000) return String.format("%.1fK/s", ps / 1000);
        if (ps >= 1)    return String.format("%.1f/s", ps);
        return              String.format("%.2f/s", ps);
    }
}
