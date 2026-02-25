package fr.lulunoel2016.farmhelper.client;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public final class DataValidator {

    private static final Map<WorldType, Set<String>> WORLD_KEYWORDS;
    private static final Set<String> SHARED_KEYWORDS  = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("money")));
    private static final BiPredicate<String, Set<String>> LABEL_MATCHES =
            (label, keywords) -> keywords.stream().anyMatch(label::contains);

    static {
        Map<WorldType, Set<String>> map = new EnumMap<>(WorldType.class);
        map.put(WorldType.LAC,   new HashSet<>(Arrays.asList("poisson", "perle", "exp")));
        map.put(WorldType.FERME, new HashSet<>(Arrays.asList("culture", "orbe", "exp")));
        map.put(WorldType.MINE,  new HashSet<>(Arrays.asList("bloc", "gemme", "exp")));
        WORLD_KEYWORDS = Collections.unmodifiableMap(map);
    }

    private DataValidator() {}

    public static Map<String, String> validateForWorld(WorldType worldType, Map<String, String> recapData) {
        if (worldType == null || worldType == WorldType.UNKNOWN || recapData == null)
            return new LinkedHashMap<>();

        return recapData.entrySet().stream()
                .filter(e -> isValidForWorld(worldType, e.getKey().toLowerCase()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new));
    }

    private static boolean isValidForWorld(WorldType worldType, String labelLower) {
        return Optional.ofNullable(WORLD_KEYWORDS.get(worldType))
                .map(keywords -> LABEL_MATCHES.test(labelLower, keywords) || LABEL_MATCHES.test(labelLower, SHARED_KEYWORDS))
                .orElse(false);
    }

    public static boolean isMoneyLabel(String labelLower) {
        return LABEL_MATCHES.test(labelLower, SHARED_KEYWORDS);
    }
}
