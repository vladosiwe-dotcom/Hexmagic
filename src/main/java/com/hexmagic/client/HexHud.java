package com.hexmagic.client;

import com.hexmagic.ability.HexAbility;
import com.hexmagic.compat.IronsCompat;
import com.hexmagic.entity.HexDrillEntity;
import com.hexmagic.registry.HexItems;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Ability list, barrier charges and the drill charge meter. Shown only while the staff is held. */
public final class HexHud implements LayeredDraw.Layer {
    private static final int COLOR_READY = 0xFF8CDCFF;
    private static final int COLOR_DIM = 0xFF2A4A5C;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_LOCKED = 0xFFFF5040;
    private static final int COLOR_GOLD = 0xFFFFD36B;
    private static final float[] COS = new float[6], SIN = new float[6];

    static {
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + 60 * i);
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        if (!HexItems.isHoldingStaff(mc.player) || !HexClientConfig.SHOW_HUD.get()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float time = mc.player.tickCount + partial;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();

        // ---- ability column on the left
        int rows = HexAbility.COUNT + (IronsCompat.LOADED ? 1 : 0);
        int rowH = 15;
        int x = 8, y = Math.max(14, h / 2 - rows * rowH / 2 - 70 + HexClientConfig.HUD_OFFSET_Y.get());
        for (int i = 0; i < rows; i++) {
            int yy = y + i * rowH + 12;
            boolean irons = i == HexAbility.SELECT_IRONS;
            HexAbility ab = irons ? null : HexAbility.VALUES[i];
            boolean selected = HexClientState.selected == i;
            boolean selectable = irons || !(ab == HexAbility.REFLECTION);
            float cd = irons ? 0f : HexClientState.cooldownMax[i] > 0 ? HexClientState.cooldown[i] / (float) HexClientState.cooldownMax[i] : 0f;
            boolean locked = ab != null && ab.isPassive() && HexClientState.passivesDisabled > 0;
            int color = locked ? COLOR_LOCKED : cd > 0 ? COLOR_DIM : irons ? COLOR_GOLD : ab.kind == HexAbility.Kind.ULTIMATE ? COLOR_GOLD : COLOR_READY;
            int cx = x + 7 + (selected ? 3 : 0), cy = yy + 6;
            if (selected) hexagon(g, cx, cy, 8.5f, COLOR_SELECTED, 0.9f);
            hexagon(g, cx, cy, 6.5f, color, selected ? 1f : 0.85f);
            if (cd > 0f) {
                // Dark sweep shrinking from the top as the cooldown runs out.
                int top = (int) (cy - 6.5f);
                g.enableScissor(cx - 8, top, cx + 8, top + (int) (13f * cd + 0.5f));
                hexagon(g, cx, cy, 6.5f, 0xFF102028, 0.9f);
                g.disableScissor();
            }
            String num = irons ? "S" : String.valueOf(i + 1);
            g.drawString(font, num, cx - font.width(num) / 2, cy - 4, locked ? 0xFF300000 : 0xFF0A1A24, false);
            Component name = irons ? Component.translatable("ability.hexmagic.irons") : ab.displayName();
            int textColor = selected ? 0xFFFFFFFF : locked ? COLOR_LOCKED : selectable ? 0xFFBFE8FF : 0xFF7FA8B8;
            g.drawString(font, name, cx + 12, cy - 4, textColor, true);
            if (cd > 0f && !irons) {
                String s = String.format("%.1f", HexClientState.cooldown[i] / 20f);
                g.drawString(font, s, cx + 14 + font.width(name), cy - 4, 0xFF7FA8B8, true);
            }
            if (ab == HexAbility.HEX_ATTACK && HexClientState.swarmReady && !locked) {
                float idle = Mth.clamp(HexClientState.idleTicks / (float) HexClientState.idleMax, 0f, 1f);
                g.fill(cx + 12, cy + 5, cx + 12 + (int) (40 * idle), cy + 6, 0xAAFF8040);
                g.fill(cx + 12 + (int) (40 * idle), cy + 5, cx + 52, cy + 6, 0x66123040);
            }
        }

        // ---- barrier charges above the column
        int ccx = x + 7, ccy = y;
        for (int i = 0; i < HexClientState.maxCharges; i++) {
            int cx = ccx + i * 15;
            boolean full = i < HexClientState.charges;
            hexagon(g, cx, ccy, 6f, full ? COLOR_READY : COLOR_DIM, 0.9f);
            if (!full && i == HexClientState.charges) {
                float f = Mth.clamp(HexClientState.chargeRegen / (float) Math.max(1, HexClientState.chargeRegenMax), 0f, 1f);
                int top = (int) (ccy + 6f - 12f * f);
                g.enableScissor(cx - 7, top, cx + 7, ccy + 7);
                hexagon(g, cx, ccy, 6f, 0xFF5FA8CC, 0.9f);
                g.disableScissor();
            }
        }
        if (HexClientState.passivesDisabled > 0) {
            String s = Component.translatable("hud.hexmagic.passives_off", HexClientState.passivesDisabled / 20 + 1).getString();
            g.drawString(font, s, ccx + HexClientState.maxCharges * 15 + 2, ccy - 4, COLOR_LOCKED, true);
        } else if (HexClientState.onPlatform) {
            g.drawString(font, Component.translatable("hud.hexmagic.platform_boost"), ccx + HexClientState.maxCharges * 15 + 2, ccy - 4, COLOR_GOLD, true);
        }

        // ---- drill charge meter under the crosshair
        if (HexClientState.chargingDrill != -1 && mc.level != null && mc.level.getEntity(HexClientState.chargingDrill) instanceof HexDrillEntity drill
                && drill.getState() == HexDrillEntity.ST_CHARGING) {
            float charge = Mth.lerp(partial, drill.clientChargeO, drill.clientCharge);
            float seconds = charge * HexDrillEntity.FULL_CHARGE_TICKS / 20f;
            int tier = HexDrillEntity.tierFor(seconds);
            int bw = 90, bh = 5;
            int bx = w / 2 - bw / 2, by = h / 2 + 18;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xAA081820);
            int fillW = (int) (bw * charge);
            int fillColor = tier < 0 ? 0xFF4A7A8C : tier == HexDrillEntity.TIER_SECONDS.length - 1 ? 0xFFFF5A40 : 0xFF8CDCFF;
            g.fill(bx, by, bx + fillW, by + bh, fillColor);
            for (float s : HexDrillEntity.TIER_SECONDS) {
                int tx = bx + (int) (bw * s / 3f);
                g.fill(tx, by - 2, tx + 1, by + bh + 2, 0xFFFFFFFF);
            }
            String label = tier < 0 ? Component.translatable("hud.hexmagic.drill_charging").getString()
                    : Component.translatable("hud.hexmagic.drill_damage", (int) HexDrillEntity.TIER_DAMAGE[tier]).getString();
            float pulse = tier == HexDrillEntity.TIER_SECONDS.length - 1 ? 0.75f + 0.25f * Mth.sin(time * 0.8f) : 1f;
            int lc = tier == HexDrillEntity.TIER_SECONDS.length - 1 ? 0xFF000000 | ((int) (255 * pulse) << 16) | 0x6050 : 0xFFBFE8FF;
            g.drawCenteredString(font, label, w / 2, by + bh + 4, lc);
        }
        g.flush();
    }

    /** Filled hexagon drawn with the gui render type. */
    private static void hexagon(GuiGraphics g, float cx, float cy, float r, int argb, float alphaMul) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        int a = (int) (((argb >>> 24) & 0xFF) * alphaMul);
        int cr = (argb >>> 16) & 0xFF, cg = (argb >>> 8) & 0xFF, cb = argb & 0xFF;
        for (int i = 0; i < 6; i += 2) {
            int j = i + 1, k = (i + 2) % 6;
            vc.addVertex(m, cx, cy, 0).setColor(cr, cg, cb, a);
            vc.addVertex(m, cx + COS[i] * r, cy + SIN[i] * r, 0).setColor(cr, cg, cb, a);
            vc.addVertex(m, cx + COS[j] * r, cy + SIN[j] * r, 0).setColor(cr, cg, cb, a);
            vc.addVertex(m, cx + COS[k] * r, cy + SIN[k] * r, 0).setColor(cr, cg, cb, a);
        }
        g.flush();
    }
}
