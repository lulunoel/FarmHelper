package fr.lulunoel2016.farmhelper.client;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecapParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmHelper");

    private static final Map<Integer, Long> processedMessages = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Long> e) {
            return System.currentTimeMillis() - e.getValue() > 100;
        }
    };

    private static final Pattern RECAP_LINE_PATTERN = Pattern.compile("[|│]?\\s*\\+([\\d.,]+[KkMmBb]?)\\s+([A-Za-zÀ-ÿ]+)");
    private static final Pattern TICKET_PATTERN     = Pattern.compile("[Vv]ous avez gagn[eé]\\s+\\+?(\\d+)\\s+[Tt]ickets?\\s+de\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_KEY_PATTERN  = Pattern.compile("x?(\\d+)\\s*[Cc]l[eé]", Pattern.CASE_INSENSITIVE);
    private static final Pattern VENTE_PATTERN      = Pattern.compile("Vous avez vendu[^p]*pour\\s+([0-9.,]+)\\s*([KkMmBbTtQq][dDtT]?)", Pattern.CASE_INSENSITIVE);

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";
    private static final String NORMAL     = "abcdefghijklmnopqrstuvwxyz";

    private static final Map<String, GlobalStats.TicketType> TICKET_TYPE_MAP = Map.of(
            "unique", GlobalStats.TicketType.STAT_UNIQUE,
            "trait",  GlobalStats.TicketType.TRAIT,
            "stat",   GlobalStats.TicketType.STAT
    );

    private static final Map<String, Double> SUFFIX_MULTIPLIERS = new LinkedHashMap<>() {{
        put("QT", 1e18); put("QD", 1e15); put("T", 1e12);
        put("B", 1e9);   put("M", 1e6);   put("K", 1e3);
    }};

    private static final Predicate<String> IS_RECAP_RESULT =
            t -> List.of("top", "termine", "terminé", "gagnant", "resultat", "résultat", "fin", "concour")
                    .stream().anyMatch(t::contains);

    private static boolean collectingRecap = false;
    private static final Map<String, String> pendingEntries = new LinkedHashMap<>();
    private static long lastRecapLineTime = 0;
    private static WorldType recapDetectedWorld = null;

    private static boolean collectingEvent = false;
    private static WorldType eventWorldType = null;
    private static long lastEventLineTime = 0;

    private static String normalizeSmallCaps(String input) {
        return input.chars()
                .mapToObj(c -> {
                    int idx = SMALL_CAPS.indexOf((char) c);
                    return String.valueOf(idx >= 0 ? NORMAL.charAt(idx) : (char) c);
                })
                .collect(Collectors.joining());
    }

    public static void onChatMessage(String rawText) {
        if (rawText == null) return;
        long now = System.currentTimeMillis();
        int hash = rawText.hashCode();
        processedMessages.entrySet().removeIf(e -> now - e.getValue() > 100);
        if (processedMessages.putIfAbsent(hash, now) != null) return;

        String text = rawText.trim().replaceAll("§[0-9a-fk-or]", "").trim();

        checkForTicketMessage(text);
        checkForEventMessage(text, now);
        checkForVentePattern(text);

        if (collectingRecap && (now - lastRecapLineTime) > 2000) { flushRecap(); return; }

        String normalized = normalizeSmallCaps(text).toLowerCase();
        if (normalized.contains("recap farm") || normalized.contains("recap  farm")) {
            collectingRecap = true;
            pendingEntries.clear();
            lastRecapLineTime = now;
            recapDetectedWorld = detectWorldFromRecapHeader(normalized);
            return;
        }

        if (collectingRecap) {
            Matcher m = RECAP_LINE_PATTERN.matcher(text);
            if (m.find()) {
                String label = m.group(2).trim().replaceAll("[§&][0-9a-fk-or]", "").trim();
                if (!label.isEmpty()) { pendingEntries.put(label, "+" + m.group(1)); lastRecapLineTime = now; }
            } else if (!text.isEmpty() && !pendingEntries.isEmpty()) {
                flushRecap();
            } else if (text.isEmpty()) {
                lastRecapLineTime = now;
            }
        }
    }

    private static WorldType detectWorldFromRecapHeader(String normalized) {
        return Map.of(
                ".*\\(c\\d*\\).*", WorldType.FERME,
                ".*\\(l\\d*\\).*", WorldType.LAC,
                ".*\\(m\\d*\\).*", WorldType.MINE
        ).entrySet().stream()
                .filter(e -> normalized.matches(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> WorldType.detectFromText(normalized));
    }

    private static void checkForTicketMessage(String text) {
        if (!normalizeSmallCaps(text).toLowerCase().contains("ticket")) return;
        Matcher m = TICKET_PATTERN.matcher(text);
        if (!m.find()) return;
        int count = Integer.parseInt(m.group(1));
        String typeKey = m.group(2).trim().toLowerCase();
        Optional.ofNullable(TICKET_TYPE_MAP.get(typeKey)).ifPresentOrElse(
                type -> {
                    switch (type) {
                        case STAT        -> GlobalStats.getInstance().addTicketsStat(count);
                        case STAT_UNIQUE -> GlobalStats.getInstance().addTicketsStatUnique(count);
                        case TRAIT       -> GlobalStats.getInstance().addTicketsTrait(count);
                    }
                },
                () -> LOGGER.warn("[FarmHelper] Type de ticket inconnu: '{}' dans: {}", typeKey, text)
        );
    }

    private static void checkForEventMessage(String text, long now) {
        String normalized = normalizeSmallCaps(text).toLowerCase();
        if (collectingEvent && (now - lastEventLineTime) > 3000) { collectingEvent = false; eventWorldType = null; }

        boolean isEventMsg = List.of("concour", "event", "evenement", "événement")
                .stream().anyMatch(normalized::contains);

        if (isEventMsg) {
            Optional.ofNullable(WorldType.detectEventType(normalized))
                    .filter(dt -> IS_RECAP_RESULT.test(normalized))
                    .ifPresent(dt -> { collectingEvent = true; eventWorldType = dt; lastEventLineTime = now; });
            return;
        }

        if (collectingEvent && eventWorldType != null) {
            lastEventLineTime = now;
            Optional.ofNullable(getCurrentPlayerName())
                    .filter(pn -> !pn.isEmpty())
                    .ifPresent(pn -> Optional.ofNullable(getCurrentMcWorldName()).ifPresent(mw -> {
                        if (text.toLowerCase().contains(pn.toLowerCase()) || normalized.contains(pn.toLowerCase())) {
                            Matcher km = EVENT_KEY_PATTERN.matcher(text);
                            int keys = km.find() ? Integer.parseInt(km.group(1)) : 1;
                            GlobalStats.getInstance().addEventKeys(mw, keys);
                        }
                    }));
            if (text.isEmpty()) { collectingEvent = false; eventWorldType = null; }
        }
    }

    private static String getCurrentPlayerName() {
        return Optional.ofNullable(MinecraftClient.getInstance())
                .map(c -> c.player)
                .map(p -> p.getName().getString())
                .orElse(null);
    }

    private static String getCurrentMcWorldName() {
        return Optional.ofNullable(MinecraftClient.getInstance())
                .map(c -> c.world)
                .map(w -> w.getRegistryKey().getValue().toString())
                .orElse(null);
    }

    public static void tickFlush() {
        long now = System.currentTimeMillis();
        if (collectingRecap && !pendingEntries.isEmpty() && (now - lastRecapLineTime) > 500) flushRecap();
        if (collectingEvent && (now - lastEventLineTime) > 3000) { collectingEvent = false; eventWorldType = null; }
    }

    private static void flushRecap() {
        pendingEntries.entrySet().stream()
                .collect(Collectors.groupingBy(
                        e -> resolveTargetWorld(e.getKey(), e.getValue()),
                        LinkedHashMap::new,
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new)
                ))
                .forEach((world, entries) ->
                        Optional.ofNullable(world)
                                .map(WorldStats::getInstance)
                                .ifPresent(ws -> ws.onRecapReceived(entries))
                );
        pendingEntries.clear();
        collectingRecap = false;
        recapDetectedWorld = null;
    }

    private static String resolveTargetWorld(String label, String value) {
        return Optional.ofNullable(WorldType.detectFromResources(Collections.singletonMap(label, value)))
                .filter(w -> w != WorldType.UNKNOWN)
                .map(WorldType::getMcWorldName)
                .or(() -> Optional.ofNullable(recapDetectedWorld)
                        .filter(w -> w != WorldType.UNKNOWN)
                        .map(WorldType::getMcWorldName))
                .orElseGet(() -> getCurrentMcWorldName());
    }

    private static void checkForVentePattern(String text) {
        Matcher m = VENTE_PATTERN.matcher(text);
        if (!m.find()) return;
        String suffix = m.group(2).trim().toUpperCase();
        try {
            double montant = Double.parseDouble(m.group(1).replace(",", "."));
            double multiplier = SUFFIX_MULTIPLIERS.entrySet().stream()
                    .filter(e -> suffix.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(1.0);
            GlobalStats.getInstance().addVente(montant * multiplier);
        } catch (Exception ignored) {}
    }
}
