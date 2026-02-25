package fr.lulunoel2016.farmhelper.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;

public class GlobalStats {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmHelper");
    private static volatile GlobalStats instance;

    private final Map<TicketType, Integer> ticketCounts = new EnumMap<>(TicketType.class);
    private final Map<String, Integer> eventKeysByWorld = new LinkedHashMap<>();
    private final AtomicLong venteSessionStart = new AtomicLong(0);
    private volatile double totalVente = 0;

    public enum TicketType { STAT, TRAIT, STAT_UNIQUE }

    private GlobalStats() {
        Arrays.stream(TicketType.values()).forEach(t -> ticketCounts.put(t, 0));
    }

    public static GlobalStats getInstance() {
        if (instance == null) synchronized (GlobalStats.class) {
            if (instance == null) instance = new GlobalStats();
        }
        return instance;
    }

    private void addTickets(TicketType type, int count) {
        ticketCounts.merge(type, count, Integer::sum);
    }

    public void addTicketsStat(int count)       { addTickets(TicketType.STAT, count); }
    public void addTicketsStatUnique(int count)  { addTickets(TicketType.STAT_UNIQUE, count); }
    public void addTicketsTrait(int count)       { addTickets(TicketType.TRAIT, count); }

    public int getTicketsStat()       { return ticketCounts.getOrDefault(TicketType.STAT, 0); }
    public int getTicketsStatUnique() { return ticketCounts.getOrDefault(TicketType.STAT_UNIQUE, 0); }
    public int getTicketsTrait()      { return ticketCounts.getOrDefault(TicketType.TRAIT, 0); }
    public int getTotalTickets()      { return ticketCounts.values().stream().mapToInt(Integer::intValue).sum(); }

    public void addEventKeys(String mcWorldName, int count) {
        eventKeysByWorld.merge(mcWorldName, count, Integer::sum);
    }

    public int getEventKeys(String mcWorldName)  { return eventKeysByWorld.getOrDefault(mcWorldName, 0); }
    public int getTotalEventKeys()               { return eventKeysByWorld.values().stream().mapToInt(Integer::intValue).sum(); }
    public double getTotalVente()                { return totalVente; }

    public void addVente(double montant) {
        venteSessionStart.compareAndSet(0, System.currentTimeMillis());
        totalVente += montant;
    }

    public double getVentePerSecond() {
        long start = venteSessionStart.get();
        if (start == 0) return 0;
        return totalVente / Math.max((System.currentTimeMillis() - start) / 1000.0, 1.0);
    }

    public void resetSession() {
        Arrays.stream(TicketType.values()).forEach(t -> ticketCounts.put(t, 0));
        totalVente = 0;
        eventKeysByWorld.clear();
        venteSessionStart.set(0);
    }
}
