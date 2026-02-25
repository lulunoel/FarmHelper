package fr.lulunoel2016.farmhelper.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class OverlayConfigScreen extends Screen {

    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private static final int PREVIEW_W = 140, PREVIEW_H = 120;

    private record ConfigButton(String label, int relX, int relY, Consumer<OverlayConfig> action) {}

    private static final List<ConfigButton> BUTTON_DEFS = List.of(
            new ConfigButton("Taille -",         -125, 0,   c -> c.scale   = clamp(0.5f, 3.0f, c.scale   - 0.1f)),
            new ConfigButton("Taille +",           5,  0,   c -> c.scale   = clamp(0.5f, 3.0f, c.scale   + 0.1f)),
            new ConfigButton("Opacité -",        -125,-25,  c -> c.opacity = clamp(0.1f, 1.0f, c.opacity - 0.1f)),
            new ConfigButton("Opacité +",          5, -25,  c -> c.opacity = clamp(0.1f, 1.0f, c.opacity + 0.1f)),
            new ConfigButton("Mode: Session/Recap",-60, 25, c -> c.showSessionTotals = !c.showSessionTotals),
            new ConfigButton("Reset Stats",        -60, 50, c -> { WorldStats.resetAll(); GlobalStats.getInstance().resetSession(); })
    );

    public OverlayConfigScreen() {
        super(Text.literal("FarmHelper - Configuration Overlay"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2, by = this.height - 70;
        BUTTON_DEFS.forEach(def ->
                addDrawableChild(ButtonWidget.builder(Text.literal(def.label()), btn -> {
                    OverlayConfig cfg = OverlayConfig.getInstance();
                    def.action().accept(cfg);
                    cfg.save();
                }).dimensions(cx + def.relX(), by + def.relY(), 120, 20).build())
        );
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, 0x88000000, 0x88000000);
        super.render(ctx, mx, my, delta);
        OverlayConfig cfg = OverlayConfig.getInstance();
        TextRenderer tr = this.textRenderer;
        drawCentered(ctx, tr, "Glissez l'overlay pour le déplacer | Molette pour la taille", 10, 0xFFFFFF55, true);
        drawCentered(ctx, tr, String.format("Taille: %.1fx | Opacité: %.0f%% | Mode: %s",
                cfg.scale, cfg.opacity * 100, cfg.showSessionTotals ? "Session" : "Recap"), 25, 0xFFAAAAAA, false);
        renderOverlayPreview(ctx, mx, my);
    }

    private void drawCentered(DrawContext ctx, TextRenderer tr, String text, int y, int color, boolean shadow) {
        ctx.drawText(tr, text, (this.width - tr.getWidth(text)) / 2, y, color, shadow);
    }

    private void renderOverlayPreview(DrawContext ctx, int mx, int my) {
        OverlayConfig cfg = OverlayConfig.getInstance();
        int ox = (int)(cfg.posX * this.width), oy = (int)(cfg.posY * this.height);
        int sw = (int)(PREVIEW_W * cfg.scale), sh = (int)(PREVIEW_H * cfg.scale);
        int borderColor = (mx >= ox && mx <= ox+sw && my >= oy && my <= oy+sh) || dragging ? 0xFFFFFF00 : 0x88FFFFFF;
        for (int i = ox; i < ox+sw; i += 4) {
            ctx.fill(i, oy, Math.min(i+2, ox+sw), oy+1, borderColor);
            ctx.fill(i, oy+sh, Math.min(i+2, ox+sw), oy+sh+1, borderColor);
        }
        for (int i = oy; i < oy+sh; i += 4) {
            ctx.fill(ox, i, ox+1, Math.min(i+2, oy+sh), borderColor);
            ctx.fill(ox+sw, i, ox+sw+1, Math.min(i+2, oy+sh), borderColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            OverlayConfig cfg = OverlayConfig.getInstance();
            int ox = (int)(cfg.posX*this.width), oy = (int)(cfg.posY*this.height);
            int sw = (int)(PREVIEW_W*cfg.scale), sh = (int)(PREVIEW_H*cfg.scale);
            if (mx >= ox && mx <= ox+sw && my >= oy && my <= oy+sh) {
                dragging = true; dragOffsetX = (int)mx - ox; dragOffsetY = (int)my - oy; return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && dragging) { dragging = false; OverlayConfig.getInstance().save(); return true; }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging && btn == 0) {
            OverlayConfig cfg = OverlayConfig.getInstance();
            cfg.posX = clamp(0f, 0.95f, (float)(mx - dragOffsetX) / this.width);
            cfg.posY = clamp(0f, 0.95f, (float)(my - dragOffsetY) / this.height);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        OverlayConfig cfg = OverlayConfig.getInstance();
        cfg.scale = clamp(0.5f, 3.0f, (float)(cfg.scale + vAmount * 0.1));
        cfg.save(); return true;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { OverlayConfig.getInstance().save(); super.close(); }

    private static float clamp(float min, float max, float val) { return Math.max(min, Math.min(max, val)); }
}
