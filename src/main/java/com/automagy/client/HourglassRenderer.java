package com.automagy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.automagy.Automagy;
import com.automagy.block.HourglassBlock;
import com.automagy.block.HourglassBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hourglass turning over. While the block is TURNING its own model stands aside and this draws the glass tumbling
 * end over end: the first half shows it as it was a moment before, the second half shows the glass it lands as, turned
 * back the rest of the way, so the swap happens edge-on. The models are turn-symmetric (tools/hourglass_models.py), so
 * nothing is ever seen upside down. For a few ticks after landing it is still drawn here, a hair smaller, until the
 * world's own model is rebuilt, so it never blinks.
 *
 * <p>The tumbling glass is lit the way the world lights the resting block: it is tesselated by the same block
 * renderer, with the same face shading, smooth light and ambient occlusion, so the first and last frames of the turn
 * match the block in the world exactly. While it swings, each face is shaded by the way it faces now rather than the
 * way it faced at rest, and the occlusion fades out towards the half turn (where the two models swap) and back in as
 * it lands, so nothing jumps in brightness at any point. (It used to be drawn through the entity render types, whose
 * own directional lights differ from block shading, so it brightened the moment the turn began and darkened again the
 * moment it landed.)
 *
 * <p>While it runs, the model in the world has no sand in it and this draws the sand instead, its height worked out
 * from the clock every frame: right after a turn all of it sits in the top bulb, and it drains through the neck and
 * piles up below over the length of the timer.
 */
public class HourglassRenderer implements BlockEntityRenderer<HourglassBlockEntity, HourglassRenderer.State> {
    /** Ticks the landed glass is still drawn here after the turn ends. */
    private static final int LANDING_TICKS = 3;

    private @Nullable ModelBlockRenderer tesselator;
    private boolean tesselatorAo;

    public HourglassRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** One quad of the glass as the world would light it at rest: per corner colour (shade times occlusion) and light. */
    public record LitQuad(BakedQuad quad, int c0, int c1, int c2, int c3, int l0, int l1, int l2, int l3) {
        int color(int v) {
            return switch (v) { case 0 -> c0; case 1 -> c1; case 2 -> c2; default -> c3; };
        }

        int light(int v) {
            return switch (v) { case 0 -> l0; case 1 -> l1; case 2 -> l2; default -> l3; };
        }
    }

    public static class State extends BlockEntityRenderState {
        public final List<LitQuad> glass = new ArrayList<>();
        public CardinalLighting cardinal = CardinalLighting.DEFAULT;
        public boolean turning;
        /** How far through the turn, 0 to 1. */
        public float t;
        /** Sand still on top while running, 1 to 0; below 0 when none is drawn. */
        public float sand = -1.0F;
        public float time;
        /** Past halfway: the landed glass is drawn, turned back the rest of the way. */
        public boolean second;
        public boolean landing;
        /** Sand on top in the landed glass while it is drawn mid-turn; below 0 when none. */
        public float turnSand = -1.0F;
        /** Magic sand glows; mundane sand takes the light where it stands. */
        public boolean magic;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HourglassBlockEntity glass, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(glass, state, partialTicks, camera, breakProgress);
        BlockState block = glass.getBlockState();
        state.turning = false;
        state.sand = -1.0F;
        state.turnSand = -1.0F;
        if (!(block.getBlock() instanceof HourglassBlock)) {
            glass.turnSeenAt = -1L;
            return;
        }
        state.magic = glass.isMagic();
        if (glass.getLevel() instanceof ClientLevel level) {
            state.cardinal = level.cardinalLighting();
        }
        // Just landed: the world's own model of it is rebuilt a frame or two later, so the landed glass is drawn here
        // until then, or it blinks out for a frame at the end of the turn.
        if (glass.getLevel() != null && !block.getValue(HourglassBlock.TURNING) && glass.turnSeenAt >= 0L) {
            glass.landedAt = glass.getLevel().getGameTime();
        }
        boolean landing = glass.getLevel() != null && glass.landedAt >= 0L
                && glass.getLevel().getGameTime() - glass.landedAt < LANDING_TICKS;
        if (glass.getLevel() == null || !block.getValue(HourglassBlock.TURNING)) {
            glass.turnSeenAt = -1L;
            if (glass.getLevel() != null && block.getValue(HourglassBlock.RUNNING)) {
                float top = glass.sandOnTop(partialTicks);
                state.sand = top < 0.0F ? 1.0F : top;
                state.time = glass.getLevel().getGameTime() + partialTicks;
            }
            if (!landing) {
                return;
            }
        } else {
            glass.landedAt = -1L;
        }
        float now = glass.getLevel().getGameTime() + partialTicks;
        // The client only sees the state flip; the turn is timed from the first frame that shows it.
        if (!landing && (glass.turnSeenAt < 0L || now - glass.turnSeenAt > HourglassBlock.TURN_TICKS + 20)) {
            glass.turnSeenAt = glass.getLevel().getGameTime();
        }
        state.t = landing ? 1.0F : Mth.clamp((now - glass.turnSeenAt) / (HourglassBlock.TURN_TICKS - 2), 0.0F, 1.0F);
        BlockState before = block.setValue(HourglassBlock.TURNING, false)
                .setValue(HourglassBlock.SAND, 7 - block.getValue(HourglassBlock.SAND))
                .setValue(HourglassBlock.RUNNING, false).setValue(HourglassBlock.POWERED, false);
        float eased = state.t * state.t * (3.0F - 2.0F * state.t);
        state.second = eased >= 0.5F;
        state.landing = landing;
        BlockState shown = state.second
                ? block.setValue(HourglassBlock.TURNING, false).setValue(HourglassBlock.POWERED, false) : before;
        if (state.second && !landing && shown.getValue(HourglassBlock.RUNNING)) {
            float top = glass.sandOnTop(partialTicks);
            state.turnSand = top < 0.0F ? 1.0F : top;
            state.time = now;
        }
        tesselate(glass, shown, state);
        state.turning = true;
    }

    /** Tesselates the glass as the chunk renderer would at its own spot, keeping each quad's corner colours and light. */
    private void tesselate(HourglassBlockEntity glass, BlockState shown, State state) {
        state.glass.clear();
        if (!(glass.getLevel() instanceof ClientLevel level)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean ao = mc.options.ambientOcclusion().get();
        if (tesselator == null || tesselatorAo != ao) {
            tesselator = new ModelBlockRenderer(ao, false, mc.getBlockColors());
            tesselatorAo = ao;
        }
        state.cardinal = level.cardinalLighting();
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(shown);
        tesselator.tesselateBlock((x, y, z, quad, in) -> state.glass.add(new LitQuad(quad,
                        in.getColor(0), in.getColor(1), in.getColor(2), in.getColor(3),
                        in.getLightCoords(0), in.getLightCoords(1), in.getLightCoords(2), in.getLightCoords(3))),
                0.0F, 0.0F, 0.0F, level, glass.getBlockPos(), shown, model, shown.getSeed(glass.getBlockPos()));
    }

    /** The world's face shading for a face whose normal points along {@code n}, blended between the six sides. */
    static float shadeFor(CardinalLighting c, Vector3f n) {
        return n.x * n.x * (n.x < 0 ? c.west() : c.east())
                + n.y * n.y * (n.y < 0 ? c.down() : c.up())
                + n.z * n.z * (n.z < 0 ? c.north() : c.south());
    }

    /** Packed light from {@code a} towards {@code b}: all of {@code a} at w = 1, all of {@code b} at w = 0. */
    private static int lerpLight(int a, int b, float w) {
        int blockA = a & 0xFFFF, skyA = a >>> 16, blockB = b & 0xFFFF, skyB = b >>> 16;
        int block = Math.round(blockB + (blockA - blockB) * w), sky = Math.round(skyB + (skyA - skyB) * w);
        return (sky << 16) | block;
    }

    /**
     * Draws the tesselated glass under the pose's turn. {@code w} is how much of the resting occlusion and smooth light
     * is kept (1 at rest, 0 edge-on); faces are shaded by where they point after {@code turn}.
     */
    private static void drawGlass(PoseStack.Pose pose, VertexConsumer out, List<LitQuad> quads, boolean translucent,
                                  CardinalLighting cardinal, Quaternionf turn, float w, int flatLight) {
        Vector3f n = new Vector3f(), pos = new Vector3f(), normal = new Vector3f();
        for (LitQuad lit : quads) {
            BakedQuad quad = lit.quad();
            if ((quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT) != translucent) {
                continue;
            }
            Direction dir = quad.direction();
            boolean shade = quad.materialInfo().shade();
            float restShade = shade ? cardinal.byFace(dir) : cardinal.up();
            float nowShade = shade ? shadeFor(cardinal, turn.transform(n.set(dir.getStepX(), dir.getStepY(), dir.getStepZ()))) : cardinal.up();
            pose.transformNormal(normal.set(dir.getStepX(), dir.getStepY(), dir.getStepZ()), normal);
            for (int v = 0; v < 4; v++) {
                int c = lit.color(v);
                // the corner's colour is tint x shade x occlusion: take the occlusion back out of the brightest channel
                float rest = Math.max(ARGB.red(c), Math.max(ARGB.green(c), ARGB.blue(c))) / 255.0F;
                float occlusion = restShade <= 0.0F ? 1.0F : Math.min(1.0F, rest / restShade);
                float target = nowShade * (1.0F + (occlusion - 1.0F) * w);
                int color = rest <= 0.0F ? c : ARGB.scaleRGB(c, target / rest);
                int light = lerpLight(lit.light(v), flatLight, w);
                pose.pose().transformPosition(quad.position(v), pos);
                long uv = quad.packedUV(v);
                out.addVertex(pos.x(), pos.y(), pos.z(), color, UVPair.unpackU(uv), UVPair.unpackV(uv),
                        OverlayTexture.NO_OVERLAY, light, normal.x(), normal.y(), normal.z());
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.sand >= 0.0F) {
            float top = state.sand, time = state.time;
            TextureAtlasSprite sprite = sandSprite(state.magic);
            int light = state.magic ? LightCoordsUtil.pack(15, 15) : state.lightCoords;
            CardinalLighting cardinal = state.cardinal;
            Quaternionf still = state.magic ? null : new Quaternionf();
            collector.submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (last, vc) -> drawSand(last, vc, sprite, top, time, light, cardinal, still));
        }
        if (!state.turning || state.glass.isEmpty()) {
            return;
        }
        float t = state.t;
        // Eased: it is lifted, swings, and settles.
        float eased = t * t * (3.0F - 2.0F * t);
        float degrees = state.second ? 180.0F * eased - 180.0F : 180.0F * eased;
        Quaternionf turn = Axis.XP.rotationDegrees(degrees);
        // The resting occlusion and smooth light are kept at the ends and fade out edge-on, where the models swap.
        float w = Math.abs(Mth.cos(degrees * Mth.DEG_TO_RAD));
        pose.pushPose();
        // Lift it clear of the floor while its corners swing past (a 12px frame reaches 10px from its middle).
        pose.translate(0.5D, 0.5D + Mth.sin(t * Mth.PI) * 0.16F, 0.5D);
        pose.mulPose(turn);
        if (state.landing) {
            // The world's own model may already be drawn here: sit just inside it so the two never flicker.
            pose.scale(0.998F, 0.998F, 0.998F);
        }
        pose.translate(-0.5D, -0.5D, -0.5D);
        List<LitQuad> quads = List.copyOf(state.glass);
        CardinalLighting cardinal = state.cardinal;
        int flat = state.lightCoords;
        collector.submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (last, vc) -> drawGlass(last, vc, quads, false, cardinal, turn, w, flat));
        collector.submitCustomGeometry(pose, RenderTypes.translucentMovingBlock(), (last, vc) -> drawGlass(last, vc, quads, true, cardinal, turn, w, flat));
        if (state.turnSand >= 0.0F) {
            float top = state.turnSand, time = state.time;
            TextureAtlasSprite sprite = sandSprite(state.magic);
            int light = state.magic ? LightCoordsUtil.pack(15, 15) : state.lightCoords;
            // shaded by where each face points now, the same as the glass and the sand of the model it replaces
            Quaternionf sandTurn = state.magic ? null : turn;
            collector.submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (last, vc) -> drawSand(last, vc, sprite, top, time, light, cardinal, sandTurn));
        }
        pose.popPose();
    }

    private static final Identifier SAND = Identifier.withDefaultNamespace("block/sand");
    private static final Identifier MAGIC_SAND = Automagy.id("block/magic_hourglass_sand");

    private static TextureAtlasSprite sandSprite(boolean magic) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(magic ? MAGIC_SAND : SAND);
    }

    /** Pixels of sand in a full bulb: base, belly and shoulder (1 + 3 + 1), filled from the bottom up. */
    private static final float FILL = 5.0F;

    /** The sand, `top` of it (1 to 0) still in the upper bulb, the rest in the lower, heaped in the middle. */
    static void drawSand(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite, float top, float time, int light,
                         CardinalLighting cardinal, @Nullable Quaternionf turn) {
        Shade sh = new Shade(cardinal, turn);
        float bottom = 1.0F - top;
        // Each bulb holds FILL pixels of sand: its 1px base, the 3px belly and the 1px shoulder. Both bulbs fill and
        // empty strictly by the clock, so a glass that has just turned shows no sand at all in its lower bulb.
        if (top > 0.0F) {
            float h = top * FILL;
            box(pose, out, sh, sprite, light, 6.5F, 9.0F, 6.5F, 9.5F, 9.0F + Math.min(1.0F, h), 9.5F, h < 1.0F, 0.0F);
            if (h > 1.0F) {
                box(pose, out, sh, sprite, light, 5.5F, 10.0F, 5.5F, 10.5F, 10.0F + Math.min(3.0F, h - 1.0F), 10.5F, true, 0.0F);
            }
            if (h > 4.0F) {
                box(pose, out, sh, sprite, light, 6.5F, 13.0F, 6.5F, 9.5F, 9.0F + h, 9.5F, true, 0.0F);
            }
        }
        float heapTop = 2.0F;
        if (bottom > 0.0F) {
            float h = bottom * FILL;
            box(pose, out, sh, sprite, light, 6.5F, 2.0F, 6.5F, 9.5F, 2.0F + Math.min(1.0F, h), 9.5F, h < 1.0F, 0.0F);
            if (h > 1.0F) {
                box(pose, out, sh, sprite, light, 5.5F, 3.0F, 5.5F, 10.5F, 3.0F + Math.min(3.0F, h - 1.0F), 10.5F, true, 0.0F);
            }
            if (h > 4.0F) {
                box(pose, out, sh, sprite, light, 6.5F, 6.0F, 6.5F, 9.5F, 2.0F + h, 9.5F, true, 0.0F);
            }
            heapTop = 2.0F + h;
            if (h < 4.5F) {
                // the little heap where the thread lands, never more sand than has fallen
                float heap = Math.min(0.5F, h);
                box(pose, out, sh, sprite, light, 7.0F, heapTop, 7.0F, 9.0F, heapTop + heap, 9.0F, true, 0.0F);
                heapTop += heap;
            }
        }
        if (top > 0.0F && heapTop < 10.0F) {
            // the thread through the neck; its grain slides down the sprite so it is seen to fall
            box(pose, out, sh, sprite, light, 7.75F, heapTop, 7.75F, 8.25F, 10.0F, 8.25F, false, (time * 0.35F) % 16.0F);
        }
    }

    /** A sand box in sixteenths, sides always, top and bottom only when asked. */
    private static void box(PoseStack.Pose p, VertexConsumer out, Shade sh, TextureAtlasSprite sprite, int light, float x0, float y0, float z0,
                            float x1, float y1, float z1, boolean ends, float scroll) {
        if (y1 - y0 < 0.001F) {
            return;
        }
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        // north (-z), south (+z), west (-x), east (+x)
        quad(p, out, sh, sprite, light, d, b, c, a, b, c, a, e, c, d, e, c, 0, 0, -1, 16 - x1, 16 - y1, 16 - x0, 16 - y0, scroll);
        quad(p, out, sh, sprite, light, a, b, f, d, b, f, d, e, f, a, e, f, 0, 0, 1, x0, 16 - y1, x1, 16 - y0, scroll);
        quad(p, out, sh, sprite, light, a, b, c, a, b, f, a, e, f, a, e, c, -1, 0, 0, z0, 16 - y1, z1, 16 - y0, scroll);
        quad(p, out, sh, sprite, light, d, b, f, d, b, c, d, e, c, d, e, f, 1, 0, 0, 16 - z1, 16 - y1, 16 - z0, 16 - y0, scroll);
        if (ends) {
            quad(p, out, sh, sprite, light, a, e, f, d, e, f, d, e, c, a, e, c, 0, 1, 0, x0, z0, x1, z1, 0.0F);
            quad(p, out, sh, sprite, light, a, b, c, d, b, c, d, b, f, a, b, f, 0, -1, 0, x0, 16 - z1, x1, 16 - z0, 0.0F);
        }
    }

    /** Corners counter-clockwise from bottom left as seen from outside; u/v in the sprite's sixteenths. */
    /** Face shading for the sand: the world's, by where each face points after the turn; none for glowing sand. */
    private record Shade(CardinalLighting cardinal, @Nullable Quaternionf turn) {
        int color(float nx, float ny, float nz) {
            if (turn == null) {
                return 0xFFFFFFFF;
            }
            float b = shadeFor(cardinal, turn.transform(new Vector3f(nx, ny, nz)));
            return ARGB.gray(b);
        }
    }

    private static void quad(PoseStack.Pose p, VertexConsumer out, Shade sh, TextureAtlasSprite sprite, int light,
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
        int color = sh.color(nx, ny, nz);
        vertex(p, out, color, light, x1, y1, z1, ua, vb, nx, ny, nz);
        vertex(p, out, color, light, x2, y2, z2, ub, vb, nx, ny, nz);
        vertex(p, out, color, light, x3, y3, z3, ub, va, nx, ny, nz);
        vertex(p, out, color, light, x4, y4, z4, ua, va, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer out, int color, int light, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }
}
