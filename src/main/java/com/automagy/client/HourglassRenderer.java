package com.automagy.client;

import com.automagy.block.HourglassBlock;
import com.automagy.block.HourglassBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hourglass turning over. While the block is TURNING its own model stands aside and this draws the glass as it was
 * a moment before (the sand that is now on top still lying in the bottom bulb), tumbling end over end. Half a turn puts
 * that sand on top, which is exactly the model it lands as.
 */
public class HourglassRenderer implements BlockEntityRenderer<HourglassBlockEntity, HourglassRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;

    public HourglassRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
    }

    public static class State extends BlockEntityRenderState {
        public final BlockModelRenderState glass = new BlockModelRenderState();
        public boolean turning;
        /** How far through the turn, 0 to 1. */
        public float t;
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
        if (glass.getLevel() == null || !(block.getBlock() instanceof HourglassBlock)
                || !block.getValue(HourglassBlock.TURNING)) {
            glass.turnSeenAt = -1L;
            return;
        }
        float now = glass.getLevel().getGameTime() + partialTicks;
        // The client only sees the state flip; the turn is timed from the first frame that shows it.
        if (glass.turnSeenAt < 0L || now - glass.turnSeenAt > HourglassBlock.TURN_TICKS + 20) {
            glass.turnSeenAt = glass.getLevel().getGameTime();
        }
        state.t = Mth.clamp((now - glass.turnSeenAt) / (HourglassBlock.TURN_TICKS - 2), 0.0F, 1.0F);
        BlockState before = block.setValue(HourglassBlock.TURNING, false)
                .setValue(HourglassBlock.SAND, 7 - block.getValue(HourglassBlock.SAND))
                .setValue(HourglassBlock.RUNNING, false).setValue(HourglassBlock.POWERED, false);
        blockModelResolver.update(state.glass, before, DISPLAY);
        state.turning = true;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.turning || state.glass.isEmpty()) {
            return;
        }
        float t = state.t;
        // Eased: it is lifted, swings, and settles.
        float eased = t * t * (3.0F - 2.0F * t);
        pose.pushPose();
        // Lift it clear of the floor while its corners swing past (a 12px frame reaches 10px from its middle).
        pose.translate(0.5D, 0.5D + Mth.sin(t * Mth.PI) * 0.16F, 0.5D);
        pose.mulPose(Axis.XP.rotationDegrees(180.0F * eased));
        pose.translate(-0.5D, -0.5D, -0.5D);
        state.glass.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
