package fr.lulunoel2016.farmhelper.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class Overlay implements HudRenderCallback {

    private static Overlay instance;

    @Environment(EnvType.CLIENT)
    private record AnimationState(String previousValue, long startTime) {
        static final long FADE_DURATION = 300L;
        AnimationState(String value) { this(value, System.currentTimeMillis()); }
        float getAlpha() { long e = System.currentTimeMillis() - startTime(); return e >= FADE_DURATION ? 0f : 1f - (float)e / FADE_DURATION; }
        boolean isActive() { return getAlpha() > 0f; }
    }

    private final Map<String, AnimationState> statAnimations = new HashMap<>();

    public static void register() { instance = new Overlay(); HudRenderCallback.EVENT.register(instance); }
    public static Overlay getInstance() { return instance; }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tc) {
        statAnimations.entrySet().removeIf(e -> !e.getValue().isActive());
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        OverlayConfig cfg = OverlayConfig.getInstance();
        if (!cfg.visible) return;

        String mcWorld = Optional.ofNullable(mc.world.getRegistryKey()).map(k -> k.getValue().toString()).orElse("");
        WorldType cw = WorldType.fromMcWorldName(mcWorld);
        GlobalStats gs = GlobalStats.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int ox = (int)(cfg.posX * sw), oy = (int)(cfg.posY * sh);
        int lh = (int)(11f * cfg.scale), pd = (int)(5f * cfg.scale);
        float at = (float)(System.currentTimeMillis() % 2000L) / 2000f;
        int bg = (int)(cfg.opacity * 255f) << 24;

        if (cw == WorldType.SPAWN || cw == WorldType.ISLAND) {
            renderSpawnOverlay(ctx, mc.textRenderer, cfg, gs, ox, oy, lh, pd, computeSpawnWidth(mc.textRenderer, cfg, gs, cfg.scale), bg, cw, at);
        } else if (EnumSet.of(WorldType.LAC, WorldType.FERME, WorldType.MINE).contains(cw)) {
            renderWorldOverlay(ctx, mc.textRenderer, cfg, gs, cw, ox, oy, lh, pd, computeWorldWidth(mc.textRenderer, cfg, gs, cw, mc, cfg.scale), bg, mc, at);
        }
    }

    private int computeWorldWidth(TextRenderer tr, OverlayConfig cfg, GlobalStats gs, WorldType wt, MinecraftClient mc, float sc) {
        String mcWorld = Optional.ofNullable(mc.world).flatMap(w -> Optional.ofNullable(w.getRegistryKey())).map(k -> k.getValue().toString()).orElse("");
        WorldStats ws = WorldStats.getInstance(mcWorld);
        if (ws == null) return (int)(140f * sc);
        Map<String, String> data = cfg.showSessionTotals ? ws.getSessionTotals() : ws.getLastRecap();
        int max = Stream.of(
                wt.getIcon() + " " + " : " + wt.getDisplayName(),
                "Monde: " + wt.getMcWorldName(),
                cfg.showSessionTotals ? "Session (" + ws.getSessionDuration() + ")" : "Dernier recap (60s)"
        ).mapToInt(tr::getWidth).max().orElse(0);
        max = Math.max(max, data.entrySet().stream().mapToInt(e -> tr.getWidth("+" + e.getValue() + " " + e.getKey())).max().orElse(0));
        int ek = gs.getEventKeys(mcWorld);
        if (ek > 0 || gs.getTotalVente() > 0 || gs.getTotalTickets() > 0) {
            StringBuilder bl = new StringBuilder();
            if (ek > 0) bl.append(ek).append(" Cl");
            appendIfPositive(gs.getTotalVente(), bl, () -> formatValueCompact(gs.getTotalVente()));
            appendIfPositive(gs.getTicketsStat(),        bl, () -> String.valueOf(gs.getTicketsStat()));
            appendIfPositive(gs.getTicketsStatUnique(),  bl, () -> String.valueOf(gs.getTicketsStatUnique()));
            appendIfPositive(gs.getTicketsTrait(),       bl, () -> String.valueOf(gs.getTicketsTrait()));
            max = Math.max(max, tr.getWidth(bl.toString()));
        }
        return max + (int)(15f * sc);
    }

    private int computeSpawnWidth(TextRenderer tr, OverlayConfig cfg, GlobalStats gs, float sc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String mcWorld = Optional.ofNullable(mc.world).flatMap(w -> Optional.ofNullable(w.getRegistryKey())).map(k -> k.getValue().toString()).orElse("");
        int max = Stream.of("FarmHelper", "Vue globale: (" + mcWorld + ") ").mapToInt(tr::getWidth).max().orElse(0);
        for (WorldType wt : new WorldType[]{ WorldType.LAC, WorldType.FERME, WorldType.MINE }) {
            WorldStats ws = WorldStats.getInstance(wt.getMcWorldName());
            if (ws == null || !ws.hasData()) continue;
            Map<String, String> d = cfg.showSessionTotals ? ws.getSessionTotals() : ws.getLastRecap();
            max = Math.max(max, tr.getWidth(wt.getIcon() + " [" + wt.getDisplayName() + "]"));
            max = Math.max(max, tr.getWidth(ws.getSessionDuration()));
            max = Math.max(max, d.entrySet().stream().mapToInt(e -> tr.getWidth("+" + e.getValue() + " " + e.getKey()) + (int)(8f*sc)).max().orElse(0));
        }
        Stream.of(
                Map.entry(gs.getTicketsStat(),       "+" + gs.getTicketsStat()      + " Ticket Stat"),
                Map.entry(gs.getTicketsTrait(),      "+" + gs.getTicketsTrait()     + " Ticket Trait"),
                Map.entry(gs.getTotalEventKeys(),    "+" + gs.getTotalEventKeys()   + " ClEvent")
        ).filter(e -> e.getKey() > 0).forEach(e -> {});
        // compute widths for global ticket/event lines
        if (gs.getTicketsStat() > 0)     max = Math.max(max, tr.getWidth("+" + gs.getTicketsStat()     + " Ticket Stat"));
        if (gs.getTicketsTrait() > 0)    max = Math.max(max, tr.getWidth("+" + gs.getTicketsTrait()    + " Ticket Trait"));
        if (gs.getTotalEventKeys() > 0)  max = Math.max(max, tr.getWidth("+" + gs.getTotalEventKeys()  + " ClEvent"));
        return max + (int)(10f * sc);
    }

    private void renderWorldOverlay(DrawContext ctx, TextRenderer tr, OverlayConfig cfg, GlobalStats gs,
                                    WorldType wt, int ox, int oy, int lh, int pd, int iw, int bg, MinecraftClient mc, float at) {
        String mcWorld = Optional.ofNullable(mc.world).flatMap(w -> Optional.ofNullable(w.getRegistryKey())).map(k -> k.getValue().toString()).orElse("");
        WorldStats ws = WorldStats.getInstance(mcWorld);
        if (ws == null) return;
        Map<String, String> data = cfg.showSessionTotals ? ws.getSessionTotals() : ws.getLastRecap();

        int bonusLines = computeBonusLines(gs, gs.getEventKeys(mcWorld));
        int lineCount  = 2 + (ws.hasData() ? 2 + data.size() : 2) + (bonusLines > 0 ? bonusLines : 0);
        int tw = iw + pd * 2, th = pd * 2 + lineCount * lh;

        ctx.fill(ox, oy, ox+tw, oy+th, bg);
        drawBorder(ctx, wt, ox, oy, tw, th, wt.getThemeColor(), at);

        int x = ox + pd, y = oy + pd;
        ctx.drawText(tr, wt.getIcon() + " : " + wt.getDisplayName(), x, y, wt.getThemeColor(), true); y += lh;
        ctx.drawText(tr, "Monde: " + wt.getMcWorldName(), x, y, 0xFF888888, false); y += lh;

        if (ws.hasData()) {
            ctx.fill(x, y+3, ox+tw-pd, y+4, 0x66FFFFFF); y += lh;
            drawTextShadow(ctx, tr, cfg.showSessionTotals ? "Session (" + ws.getSessionDuration() + ")" : "Dernier recap (60s)",
                    x, y, cfg.showSessionTotals ? 0xFF4CAF50 : 0xFF2196F3, false); y += lh;
            for (Map.Entry<String, String> e : data.entrySet()) {
                String v = "+" + e.getValue(); int vc = colorForStat(e.getKey());
                String ak = mcWorld + ":" + e.getKey();
                statAnimations.computeIfAbsent(ak, k -> new AnimationState(e.getValue()));
                if (!statAnimations.get(ak).previousValue().equals(e.getValue()))
                    statAnimations.put(ak, new AnimationState(e.getValue()));
                drawTextShadow(ctx, tr, v, x, y, vc, true);
                drawTextShadow(ctx, tr, " " + e.getKey(), x + tr.getWidth(v), y, 0xFFDDDDDD, false);
                drawTextShadow(ctx, tr, "  " + ws.getPerSecondFormatted(e.getKey()), x + tr.getWidth(v + " " + e.getKey()), y, 0xFF888888, false);
                y += lh;
            }
        } else {
            drawGradientSeparator(ctx, x, ox+tw-pd, y+3, wt.getThemeColor(), cfg.scale); y += lh;
            drawTextShadow(ctx, tr, "En attente du recap...", x, y, 0xFF666666, false); y += lh;
        }
        if (bonusLines > 0) renderBonusSection(ctx, tr, gs, mcWorld, wt, x, y, ox+tw-pd, lh);
    }

    private void renderSpawnOverlay(DrawContext ctx, TextRenderer tr, OverlayConfig cfg, GlobalStats gs,
                                    int ox, int oy, int lh, int pd, int iw, int bg, WorldType wt, float at) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String mcWorld = Optional.ofNullable(mc.world).flatMap(w -> Optional.ofNullable(w.getRegistryKey())).map(k -> k.getValue().toString()).orElse("");
        Map<WorldType, Map<String, String>> recapByWorld = getRecapStatsByWorld(cfg);
        boolean hasBonusData = gs.getTotalTickets() > 0 || gs.getTotalEventKeys() > 0 || gs.getTotalVente() > 0;
        int bonusLines = hasBonusData ? computeGlobalBonusLines(gs) : 0;
        int lineCount  = 2 + recapByWorld.values().stream().mapToInt(d -> d.isEmpty() ? 0 : 2 + d.size()).sum() + bonusLines;
        int tw = iw + pd * 2, th = pd * 2 + lineCount * lh;

        ctx.fill(ox, oy, ox+tw, oy+th, bg);
        drawBorder(ctx, wt, ox, oy, tw, th, wt.getThemeColor(), at);

        int x = ox + pd, y = oy + pd;
        drawGradientBackground(ctx, ox, oy+pd, ox+tw, oy+pd+lh, wt.getThemeColor() | 0xFF000000, -16777216);
        drawTextShadow(ctx, tr, wt.getIcon() + " FarmHelper: (" + wt.getIcon() + ") ", x, y, wt.getThemeColor(), true); y += lh;
        drawTextShadow(ctx, tr, "Vue globale: " + wt.getDisplayName(), x, y, -7829368, false); y += lh;

        for (Map.Entry<WorldType, Map<String, String>> entry : recapByWorld.entrySet()) {
            WorldType ewt = entry.getKey(); Map<String, String> data = entry.getValue();
            if (data.isEmpty()) continue;
            drawTextShadow(ctx, tr, ewt.getIcon() + " [" + ewt.getDisplayName() + "]", x, y, ewt.getThemeColor(), true); y += lh;
            WorldStats ws = WorldStats.getInstance(ewt.getMcWorldName());
            drawTextShadow(ctx, tr, ws != null ? ws.getSessionDuration() : "", x, y, -5592406, false); y += lh;
            for (Map.Entry<String, String> de : data.entrySet()) {
                String val = "+" + de.getValue(); int vc = colorForStat(de.getKey());
                drawTextShadow(ctx, tr, val, x + (int)(8f*cfg.scale), y, vc, true);
                drawTextShadow(ctx, tr, " " + de.getKey(), x + (int)(8f*cfg.scale) + tr.getWidth(val), y, -2236963, false);
                y += lh;
            }
        }
        if (hasBonusData) renderGlobalBonusSection(ctx, tr, gs, x, y, lh);
    }

    private int renderBonusSection(DrawContext ctx, TextRenderer tr, GlobalStats gs, String mcWorld,
                                   WorldType wt, int x, int y, int re, int lh) {
        ctx.fill(x, y+3, re, y+4, 0x66FFFFFF); y += lh;
        ctx.drawText(tr, "✦ Bonus", x, y, 0xFFFFAA00, true); y += lh;
        final int[] yRef = {y};
        renderBonusEntries(ctx, tr, gs, mcWorld, wt, x, lh, (text, color) -> { ctx.drawText(tr, text, x, yRef[0], color, true); yRef[0] += lh; });
        return yRef[0];
    }

    private void renderGlobalBonusSection(DrawContext ctx, TextRenderer tr, GlobalStats gs, int x, int y, int lh) {
        ctx.drawText(tr, "✦ Bonus", x, y, 0xFFFFAA00, true); y += lh;
        boolean any = false;
        if (gs.getTicketsStat()       > 0) { renderEntry(ctx, tr, "+" + gs.getTicketsStat(),      " Ticket Stat",   x, y, 0xFFFF6BEB, 0xFFDDDDDD); y += lh; any = true; }
        if (gs.getTicketsTrait()      > 0) { renderEntry(ctx, tr, "+" + gs.getTicketsTrait(),     " Ticket Trait",  x, y, 0xFFAA55FF, 0xFFDDDDDD); y += lh; any = true; }
        if (gs.getTotalEventKeys()    > 0) { renderEntry(ctx, tr, "+" + gs.getTotalEventKeys(),   " Clé(s) ",       x, y, 0xFFFFD700, 0xFFDDDDDD); y += lh; any = true; }
        if (gs.getTotalVente()        > 0) { renderEntry(ctx, tr, formatValueCompact(gs.getTotalVente()), " Money", x, y, 0xFFFFA500, 0xFFDDDDDD); y += lh; any = true; }
        if (gs.getTicketsStatUnique() > 0) { renderEntry(ctx, tr, "+" + gs.getTicketsStatUnique()," Ticket Unique", x, y, 0xFF2196F3, 0xFFDDDDDD); y += lh; any = true; }
        if (!any) { ctx.drawText(tr, "Aucun bonus", x, y, 0xFF888888, false); }
    }

    private void renderBonusEntries(DrawContext ctx, TextRenderer tr, GlobalStats gs, String mcWorld,
                                    WorldType wt, int x, int lh, BiConsumer<String, Integer> emit) {
        int ek = gs.getEventKeys(mcWorld);
        boolean any = false;
        if (gs.getTicketsStat()       > 0) { emit.accept("+" + gs.getTicketsStat()     + " Ticket Stat",           0xFFFF6BEB); any = true; }
        if (gs.getTicketsTrait()      > 0) { emit.accept("+" + gs.getTicketsTrait()    + " Ticket Trait",          0xFFAA55FF); any = true; }
        if (ek                        > 0) { emit.accept("+" + ek                      + " Clé(s) " + wt.getDisplayName(), 0xFFFFD700); any = true; }
        if (gs.getTotalVente()        > 0) { emit.accept(formatValueCompact(gs.getTotalVente()) + " Money",        0xFFFFA500); any = true; }
        if (gs.getTicketsStatUnique() > 0) { emit.accept("+" + gs.getTicketsStatUnique()+ " Ticket Unique",        0xFF2196F3); any = true; }
        if (!any) emit.accept("Aucun bonus", 0xFF888888);
    }

    private void renderEntry(DrawContext ctx, TextRenderer tr, String value, String label, int x, int y, int vc, int lc) {
        ctx.drawText(tr, value, x, y, vc, true);
        ctx.drawText(tr, label, x + tr.getWidth(value), y, lc, false);
    }

    private int computeBonusLines(GlobalStats gs, int eventKeys) {
        long count = Arrays.stream(new int[]{ gs.getTicketsStat(), gs.getTicketsTrait(), gs.getTicketsStatUnique(), eventKeys })
                .filter(v -> v > 0).count()
                + (gs.getTotalVente() > 0 ? 1 : 0);
        if (count == 0 && eventKeys == 0 && gs.getTotalVente() == 0 && gs.getTotalTickets() == 0) return 0;
        return (int)(2 + count + (count == 0 ? 1 : 0));
    }

    private int computeGlobalBonusLines(GlobalStats gs) {
        long count = Arrays.stream(new int[]{ gs.getTicketsStat(), gs.getTicketsTrait(), gs.getTicketsStatUnique(), gs.getTotalEventKeys() })
                .filter(v -> v > 0).count()
                + (gs.getTotalVente() > 0 ? 1 : 0);
        return (int)(2 + count + (count == 0 ? 1 : 0));
    }

    private void appendIfPositive(double val, StringBuilder sb, java.util.function.Supplier<String> fmt) {
        if (val > 0) { if (!sb.isEmpty()) sb.append(" | "); sb.append(fmt.get()); }
    }
    private void appendIfPositive(int val, StringBuilder sb, java.util.function.Supplier<String> fmt) {
        if (val > 0) { if (!sb.isEmpty()) sb.append(" | "); sb.append(fmt.get()); }
    }

    private Map<WorldType, Map<String, String>> getRecapStatsByWorld(OverlayConfig cfg) {
        return Arrays.stream(new WorldType[]{ WorldType.LAC, WorldType.FERME, WorldType.MINE })
                .map(wt -> Map.entry(wt, Optional.ofNullable(WorldStats.getInstance(wt.getMcWorldName()))
                        .filter(WorldStats::hasData)
                        .map(ws -> cfg.showSessionTotals ? ws.getSessionTotals() : ws.getLastRecap())
                        .orElse(Collections.emptyMap())))
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }

    // ─── Drawing Helpers ───────────────────────────────────────────────────────

    private int lerpColor(int c1, int c2, float t) {
        int[] a = {(c1>>24)&0xFF,(c1>>16)&0xFF,(c1>>8)&0xFF,c1&0xFF};
        int[] b = {(c2>>24)&0xFF,(c2>>16)&0xFF,(c2>>8)&0xFF,c2&0xFF};
        int[] r = new int[4];
        Arrays.setAll(r, i -> (int)(a[i] + (b[i] - a[i]) * t));
        return (r[0]<<24)|(r[1]<<16)|(r[2]<<8)|r[3];
    }

    private void drawTextShadow(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color, boolean bold) {
        ctx.drawText(tr, text, x+1, y+1, 0xFF000000, bold);
        ctx.drawText(tr, text, x, y, color, bold);
    }

    private void drawGradientSeparator(DrawContext ctx, int x1, int x2, int y, int color, float scale) {
        int t = (int)scale;
        ctx.fill(x1, y, x2, y+t, color);
        ctx.fill(x1, y+2*t, x2, y+3*t, color & 0xFFFFFF | 0x44000000);
    }

    private void drawGradientBackground(DrawContext ctx, int x1, int y1, int x2, int y2, int cLeft, int cRight) {
        for (int x = x1; x < x2; x++) {
            ctx.fill(x, y1, x+1, y2, lerpColor(cLeft, cRight, (float)(x-x1) / Math.max(1, x2-x1)));
        }
    }

    private void drawBorder(DrawContext ctx, WorldType wt, int x, int y, int w, int h, int color, float at) {
        float pulse = (float)Math.sin(at * Math.PI * 2.0) * 0.5f + 0.5f;
        int glowSize = (int)(2f + pulse * 3f);
        int[] nc = getNeonColors(wt);
        drawGlowShadow(ctx, x-glowSize, y-glowSize, w+glowSize*2, h+glowSize*2, 570425344, glowSize);
        drawNeonLine(ctx, x, y, x+w, y, nc[0], nc[1], 2, at, false);
        drawNeonLine(ctx, x, y+h, x+w, y+h, nc[0], nc[1], 2, at, true);
        drawNeonLine(ctx, x, y, x, y+h, nc[0], nc[1], 2, at, false);
        drawNeonLine(ctx, x+w, y, x+w, y+h, nc[0], nc[1], 2, at, true);
        drawInnerGlow(ctx, x+1, y+1, w-2, h-2, nc[0], (int)(150f * pulse));
        drawNeonCorners(ctx, x, y, w, h, nc[0], nc[1], glowSize);
    }

    private int[] getNeonColors(WorldType wt) {
        return switch (wt) {
            case LAC   -> new int[]{ 0xFF00BFFF, 0xFF1E90FF };
            case FERME -> new int[]{ 0xFF7CFC00, 0xFF228B22 };
            case MINE  -> new int[]{ 0xFFFFD700, 0xFFB8860B };
            default    -> new int[]{ 0xFFAAAAAA, 0xFF888888 };
        };
    }

    private void drawNeonLine(DrawContext ctx, int x1, int y1, int x2, int y2,
                               int c1, int c2, int thickness, float at, boolean reverse) {
        boolean horiz = y1 == y2;
        int len = horiz ? Math.abs(x2-x1) : Math.abs(y2-y1);
        int scanCenter = (int)((at % 1f) * len), scanWidth = Math.max(8, len/4);
        for (int i = 0; i < len; i++) {
            int color = lerpColor(c1, c2, (float)i / Math.max(1, len-1));
            int alpha = (int)(255f * (0.6f + 0.4f * Math.max(0.3f, 1f - (float)Math.abs(i - scanCenter) / scanWidth)));
            int fc = color & 0xFFFFFF | alpha << 24;
            if (horiz) ctx.fill(x1 + (x2>x1?i:-i), y1, x1+(x2>x1?i:-i)+1, y1+thickness, fc);
            else       ctx.fill(x1, y1+(y2>y1?i:-i), x1+thickness, y1+(y2>y1?i:-i)+1, fc);
        }
    }

    private void drawGlowShadow(DrawContext ctx, int x, int y, int w, int h, int color, int radius) {
        for (int r = radius; r > 0; r--) {
            int a = (color>>24&0xFF) * (radius-r) / radius;
            int gc = color & 0xFFFFFF | a << 24;
            ctx.fill(x+r, y+r,   x+w-r, y+r+1,   gc);
            ctx.fill(x+r, y+h-r-1, x+w-r, y+h-r, gc);
            ctx.fill(x+r, y+r,   x+r+1, y+h-r,   gc);
            ctx.fill(x+w-r-1, y+r, x+w-r, y+h-r, gc);
        }
    }

    private void drawInnerGlow(DrawContext ctx, int x, int y, int w, int h, int color, int alpha) {
        int gc = color & 0xFFFFFF | Math.min(alpha, 80) << 24;
        ctx.fill(x, y, x+w, y+1, gc); ctx.fill(x, y+h-1, x+w, y+h, gc);
        ctx.fill(x, y, x+1, y+h, gc); ctx.fill(x+w-1, y, x+w, y+h, gc);
    }

    private void drawNeonCorners(DrawContext ctx, int x, int y, int w, int h, int c1, int c2, int sz) {
        Arrays.asList(
                Map.entry(x-sz/2, y-sz/2),    Map.entry(x+w-sz/2, y-sz/2),
                Map.entry(x-sz/2, y+h-sz/2),  Map.entry(x+w-sz/2, y+h-sz/2)
        ).forEach(p -> {
            int c = (p.getKey() == x-sz/2 || p.getKey() == x+w-sz/2) && (p.getValue() == y-sz/2 || p.getValue() == y+h-sz/2) ? c1 : c2;
            ctx.fill(p.getKey(), p.getValue(), p.getKey()+sz, p.getValue()+sz, c1);
            ctx.fill(p.getKey()-1, p.getValue()-1, p.getKey()+sz+1, p.getValue()+1, c1 & 0xFFFFFF | 0x44000000);
            ctx.fill(p.getKey()-1, p.getValue()+sz-1, p.getKey()+sz+1, p.getValue()+sz+1, c1 & 0xFFFFFF | 0x44000000);
        });
    }

    private int colorForStat(String label) {
        String l = label.toLowerCase();
        record ColorRule(String keyword, int color) {}
        return List.of(
                new ColorRule("poisson", 0xFF00BFFF), new ColorRule("culture", 0xFF7CFC00),
                new ColorRule("perle",   0xFF1E90FF), new ColorRule("bloc",    0xFFFFD700),
                new ColorRule("orbe",    0xFFB8860B), new ColorRule("money",   0xFFFFA500),
                new ColorRule("gemme",   0xFF8A2BE2), new ColorRule("ticket",  0xFFDDDDDD),
                new ColorRule("cle",     0xFF21948),  new ColorRule("xp",      0xFFDDDDDD),
                new ColorRule("bonus",   0xFFB8860B)
        ).stream().filter(r -> l.contains(r.keyword())).map(ColorRule::color).findFirst().orElse(0xFFDDDDDD);
    }

    private String formatValueCompact(double value) {
        record Tier(double threshold, double divisor, String suffix) {}
        return List.of(
                new Tier(1e18, 1e18, "QT"), new Tier(1e15, 1e15, "QD"),
                new Tier(1e12, 1e12, "T"),  new Tier(1e9,  1e9,  "B"),
                new Tier(1e6,  1e6,  "M"),  new Tier(1e3,  1e3,  "K")
        ).stream().filter(t -> value >= t.threshold()).findFirst()
                .map(t -> String.format("%.0f %s", value / t.divisor(), t.suffix()))
                .orElseGet(() -> String.format("%.0f", value));
    }
}
