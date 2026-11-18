package com.automagy.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Fluid still textures that repeat every 4 blocks instead of every block. Next to
 * {@code <still>.png} put 16 sprites {@code <still>_<x>_<z>.png} (x, z = 0..3, cut from one seamless 64x64 surface and
 * animated in step); each block then shows the sprite for its position. Without them the plain still texture is used.
 */
public final class FluidTextures {
    private FluidTextures() {
    }

    /** Client fluid extensions with the 4-block still repeat, untinted. */
    public static IClientFluidTypeExtensions extensions(ResourceLocation still, ResourceLocation flow, ResourceLocation overlay) {
        ResourceLocation[] parts = new ResourceLocation[16];
        for (int i = 0; i < 16; i++) {
            parts[i] = still.withPath(still.getPath() + "_" + (i >> 2) + "_" + (i & 3));
        }
        return new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flow;
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return overlay;
            }

            /** Draws the fluid as vanilla does, moving the still sprite UVs onto the sprite for the block position. */
            @Override
            public boolean renderFluid(FluidState fluidState, BlockAndTintGetter getter, BlockPos pos, VertexConsumer output,
                                       BlockState blockState) {
                TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
                TextureAtlasSprite part = atlas.getSprite(parts[(Math.floorMod(pos.getX(), 4) << 2) | Math.floorMod(pos.getZ(), 4)]);
                TextureAtlasSprite base = atlas.getSprite(still);
                if (part.contents().name().equals(MissingTextureAtlasSprite.getLocation())
                        || base.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                    return false;
                }
                Minecraft.getInstance().getBlockRenderer().getLiquidBlockRenderer()
                        .tesselate(getter, pos, new Moved(output, base, part), blockState, fluidState);
                return true;
            }
        };
    }

    /** Passes vertices through, mapping UVs inside {@code from} to the same spot in {@code to}. */
    private record Moved(VertexConsumer out, TextureAtlasSprite from, TextureAtlasSprite to) implements VertexConsumer {
        private static final float EPS = 1.0E-6F;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (u >= from.getU0() - EPS && u <= from.getU1() + EPS && v >= from.getV0() - EPS && v <= from.getV1() + EPS) {
                u = to.getU0() + (u - from.getU0()) / (from.getU1() - from.getU0()) * (to.getU1() - to.getU0());
                v = to.getV0() + (v - from.getV0()) / (from.getV1() - from.getV0()) * (to.getV1() - to.getV0());
            }
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(x, y, z);
            return this;
        }
    }
}
