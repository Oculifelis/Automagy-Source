package com.automagy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.automagy.Automagy;
import com.automagy.block.HourglassBlock;
import com.automagy.block.HourglassBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The hourglass turning over. While the block is TURNING its own model stands aside and this draws the glass tumbling
 * end over end: the first half shows it as it was a moment before, the second half shows the glass it lands as, turned
 * back the rest of the way, so the swap happens edge-on. The models are turn-symmetric (tools/hourglass_models.py), so
 * nothing is ever seen upside down. For a few ticks after landing it is still drawn here, a hair smaller, until the
 * world's own model is rebuilt, so it never blinks.
 *
 * <p>While it runs, the model in the world has no sand in it and this draws the sand instead, its height worked out
 * from the clock every frame: right after a turn all of it sits in the top bulb, and it drains through the neck and
 * piles up below over the length of the timer.
 */
public class HourglassRenderer implements BlockEntityRenderer<HourglassBlockEntity> {
    /** Ticks the landed glass is still drawn here after the turn ends. */
    private static final int LANDING_TICKS = 3;

    public HourglassRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HourglassBlockEntity glass, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = glass.getBlockState();
        if (!(state.getBlock() instanceof HourglassBlock)) {
            return;
        }
        boolean magic = glass.isMagic();
        int sandLight = magic ? LightTexture.FULL_BRIGHT : packedLight;
        // Just landed: the world's own model of it is rebuilt a frame or two later, so the landed glass is drawn here
        // until then, or it blinks out for a frame at the end of the turn.
        if (glass.getLevel() != null && !state.getValue(HourglassBlock.TURNING) && glass.turnSeenAt >= 0L) {
            glass.landedAt = glass.getLevel().getGameTime();
        }
        boolean landing = glass.getLevel() != null && glass.landedAt >= 0L
                && glass.getLevel().getGameTime() - glass.landedAt < LANDING_TICKS;
        if (glass.getLevel() == null || !state.getValue(HourglassBlock.TURNING)) {
            glass.turnSeenAt = -1L;
            if (glass.getLevel() != null && state.getValue(HourglassBlock.RUNNING)) {
                float top = glass.sandOnTop(partialTick);
                drawSand(pose, buffers.getBuffer(RenderType.cutout()), magic, sandLight, top < 0.0F ? 1.0F : top,
                        glass.getLevel().getGameTime() + partialTick);
            }
            if (!landing) {
                return;
            }
        } else {
            glass.landedAt = -1L;
        }
        float now = glass.getLevel().getGameTime() + partialTick;
        // The client only sees the state flip; the turn is timed from the first frame that shows it.
        if (!landing && (glass.turnSeenAt < 0L || now - glass.turnSeenAt > HourglassBlock.TURN_TICKS + 20)) {
            glass.turnSeenAt = glass.getLevel().getGameTime();
        }
        float t = landing ? 1.0F : Mth.clamp((now - glass.turnSeenAt) / (HourglassBlock.TURN_TICKS - 2), 0.0F, 1.0F);
        // Eased: it is lifted, swings, and settles.
        float eased = t * t * (3.0F - 2.0F * t);
        BlockState before = state.setValue(HourglassBlock.TURNING, false)
                .setValue(HourglassBlock.SAND, 7 - state.getValue(HourglassBlock.SAND))
                .setValue(HourglassBlock.RUNNING, false).setValue(HourglassBlock.POWERED, false);
        // Past halfway it is drawn as it will land and turned back the rest of the way, so nothing changes when the
        // world takes over drawing it: the one swap happens edge-on, mid-swing.
        boolean second = eased >= 0.5F;
        BlockState shown = second ? state.setValue(HourglassBlock.TURNING, false).setValue(HourglassBlock.POWERED, false)
                : before;
        pose.pushPose();
        // Lift it clear of the floor while its corners swing past (a 12px frame reaches 10px from its middle).
        pose.translate(0.5D, 0.5D + Mth.sin(t * Mth.PI) * 0.16F, 0.5D);
        pose.mulPose(Axis.XP.rotationDegrees(second ? 180.0F * eased - 180.0F : 180.0F * eased));
        if (landing) {
            // The world's own model may already be drawn here: sit just inside it so the two never flicker.
            pose.scale(0.998F, 0.998F, 0.998F);
        }
        pose.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(shown, pose, buffers, packedLight, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        if (second && !landing && shown.getValue(HourglassBlock.RUNNING)) {
            float top = glass.sandOnTop(partialTick);
            drawSand(pose, buffers.getBuffer(RenderType.cutout()), magic, sandLight, top < 0.0F ? 1.0F : top, now);
        }
        pose.popPose();
    }

    private static final ResourceLocation SAND = ResourceLocation.withDefaultNamespace("block/sand");
    private static final ResourceLocation MAGIC_SAND = Automagy.id("block/magic_hourglass_sand");

    /** Pixels of sand in a full bulb: base, belly and shoulder (1 + 3 + 1), filled from the bottom up. */
    private static final float FILL = 5.0F;

    /** The sand, `top` of it (1 to 0) still in the upper bulb, the rest in the lower, heaped in the middle. */
    static void drawSand(PoseStack pose, VertexConsumer out, boolean magic, int light, float top, float time) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(magic ? MAGIC_SAND : SAND);
        float bottom = 1.0F - top;
        // Each bulb holds FILL pixels of sand: its 1px base, the 3px belly and the 1px shoulder. Both bulbs fill and
        // empty strictly by the clock, so a glass that has just turned shows no sand at all in its lower bulb.
        if (top > 0.0F) {
            float h = top * FILL;
            box(pose, out, sprite, light, 6.5F, 9.0F, 6.5F, 9.5F, 9.0F + Math.min(1.0F, h), 9.5F, h < 1.0F, 0.0F);
            if (h > 1.0F) {
                box(pose, out, sprite, light, 5.5F, 10.0F, 5.5F, 10.5F, 10.0F + Math.min(3.0F, h - 1.0F), 10.5F, true, 0.0F);
            }
            if (h > 4.0F) {
                box(pose, out, sprite, light, 6.5F, 13.0F, 6.5F, 9.5F, 9.0F + h, 9.5F, true, 0.0F);
            }
        }
        float heapTop = 2.0F;
        if (bottom > 0.0F) {
            float h = bottom * FILL;
            box(pose, out, sprite, light, 6.5F, 2.0F, 6.5F, 9.5F, 2.0F + Math.min(1.0F, h), 9.5F, h < 1.0F, 0.0F);
            if (h > 1.0F) {
                box(pose, out, sprite, light, 5.5F, 3.0F, 5.5F, 10.5F, 3.0F + Math.min(3.0F, h - 1.0F), 10.5F, true, 0.0F);
            }
            if (h > 4.0F) {
                box(pose, out, sprite, light, 6.5F, 6.0F, 6.5F, 9.5F, 2.0F + h, 9.5F, true, 0.0F);
            }
            heapTop = 2.0F + h;
            if (h < 4.5F) {
                // the little heap where the thread lands, never more sand than has fallen
                float heap = Math.min(0.5F, h);
                box(pose, out, sprite, light, 7.0F, heapTop, 7.0F, 9.0F, heapTop + heap, 9.0F, true, 0.0F);
                heapTop += heap;
            }
        }
        if (top > 0.0F && heapTop < 10.0F) {
            // the thread through the neck; its grain slides down the sprite so it is seen to fall
            box(pose, out, sprite, light, 7.75F, heapTop, 7.75F, 8.25F, 10.0F, 8.25F, false, (time * 0.35F) % 16.0F);
        }
    }

    /** A sand box in sixteenths, sides always, top only when asked. */
    private static void box(PoseStack pose, VertexConsumer out, TextureAtlasSprite sprite, int light, float x0, float y0, float z0,
                            float x1, float y1, float z1, boolean ends, float scroll) {
        if (y1 - y0 < 0.001F) {
            return;
        }
        PoseStack.Pose p = pose.last();
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        // north (-z), south (+z), west (-x), east (+x)
        quad(p, out, sprite, light, d, b, c, a, b, c, a, e, c, d, e, c, 0, 0, -1, 16 - x1, 16 - y1, 16 - x0, 16 - y0, scroll);
        quad(p, out, sprite, light, a, b, f, d, b, f, d, e, f, a, e, f, 0, 0, 1, x0, 16 - y1, x1, 16 - y0, scroll);
        quad(p, out, sprite, light, a, b, c, a, b, f, a, e, f, a, e, c, -1, 0, 0, z0, 16 - y1, z1, 16 - y0, scroll);
        quad(p, out, sprite, light, d, b, f, d, b, c, d, e, c, d, e, f, 1, 0, 0, 16 - z1, 16 - y1, 16 - z0, 16 - y0, scroll);
        if (ends) {
            quad(p, out, sprite, light, a, e, f, d, e, f, d, e, c, a, e, c, 0, 1, 0, x0, z0, x1, z1, 0.0F);
            quad(p, out, sprite, light, a, b, c, d, b, c, d, b, f, a, b, f, 0, -1, 0, x0, 16 - z1, x1, 16 - z0, 0.0F);
        }
    }

    /** Corners counter-clockwise from bottom left as seen from outside; u/v in the sprite's sixteenths. */
    private static void quad(PoseStack.Pose p, VertexConsumer out, TextureAtlasSprite sprite, int light,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz, float u0, float v0, float u1, float v1, float scroll) {
        if (scroll != 0.0F) {
            // slide the window up the sprite (grains move down), wrapping within the room the sprite leaves
            float len = v1 - v0, room = 16.0F - len;
            v0 = room <= 0.0F ? 0.0F : room - (scroll % room);
            v1 = v0 + len;
        }
        float ua = sprite.getU(u0 / 16), ub = sprite.getU(u1 / 16), va = sprite.getV(v0 / 16), vb = sprite.getV(v1 / 16);
        vertex(p, out, light, x1, y1, z1, ua, vb, nx, ny, nz);
        vertex(p, out, light, x2, y2, z2, ub, vb, nx, ny, nz);
        vertex(p, out, light, x3, y3, z3, ub, va, nx, ny, nz);
        vertex(p, out, light, x4, y4, z4, ua, va, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer out, int light, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }
}
