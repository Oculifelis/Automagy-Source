package com.automagy.tank;

import com.automagy.Automagy;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Vishroom soup. Its source blocks now and then grow a fungus beside them, chosen from the block tag
 * {@code automagia:vishroom_soup_grows}; a player standing in a source block hears the whispers (nausea) for a while.
 */
public abstract class VishroomSoupFluid extends BaseFlowingFluid {
    public static final TagKey<Block> GROWS = TagKey.create(Registries.BLOCK, Automagy.id("vishroom_soup_grows"));

    protected VishroomSoupFluid(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
        if (!state.isSource() || random.nextInt(4) != 0) {
            return;
        }
        BlockPos at = pos.offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
        if (!level.isEmptyBlock(at) || level.getMaxLocalRawBrightness(at) > 12) {
            return;
        }
        Optional<Holder<Block>> pick = BuiltInRegistries.BLOCK.getRandomElementOf(GROWS, random);
        if (pick.isEmpty()) {
            return;
        }
        BlockState plant = pick.get().value().defaultBlockState();
        if (plant.canSurvive(level, at)) {
            level.setBlock(at, plant, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
        if (!level.isClientSide() && entity instanceof Player player && level.getFluidState(pos).isSource()
                && !player.hasEffect(MobEffects.NAUSEA)) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200));
        }
    }

    public static class Source extends VishroomSoupFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends VishroomSoupFluid {
        public Flowing(Properties properties) {
            super(properties);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.material.Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
