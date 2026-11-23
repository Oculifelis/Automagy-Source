package com.automagy.block;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirrorBase;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Mercurial redcrystal is bound to a Thaumaturge magic mirror or essentia mirror (right-click the mirror with it) and
 * must be placed within eight blocks of that mirror. It shares its signal with every mercurial crystal bound to the
 * mirror on the other end of the link, in any dimension, as if they touched.
 */
public class MercurialRedcrystalBlock extends RedcrystalBlock implements EntityBlock {
    public static final int REACH = 8;
    private static final int RECHECK = 40;
    /** Mirror -> mercurial crystals bound to it, for crystals in loaded chunks. */
    private static final Map<GlobalPos, Set<GlobalPos>> BOUND = new HashMap<>();

    public MercurialRedcrystalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(MercurialRedcrystalBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MercurialRedcrystalBlockEntity(pos, state);
    }

    static void bind(GlobalPos mirror, GlobalPos crystal) {
        BOUND.computeIfAbsent(mirror, k -> new HashSet<>()).add(crystal);
    }

    static void unbind(GlobalPos mirror, GlobalPos crystal) {
        Set<GlobalPos> set = BOUND.get(mirror);
        if (set != null && set.remove(crystal) && set.isEmpty()) {
            BOUND.remove(mirror);
        }
    }

    public static boolean inReach(GlobalPos mirror, Level level, BlockPos pos) {
        return mirror.dimension() == level.dimension() && mirror.pos().closerThan(pos, REACH + 0.5);
    }

    /** Mercurial crystals on the far side of this crystal's mirror link. */
    private static List<GlobalPos> partners(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null || !(level.getBlockEntity(pos) instanceof MercurialRedcrystalBlockEntity be) || be.mirror() == null) {
            return List.of();
        }
        GlobalPos mirror = be.mirror();
        ServerLevel mirrorLevel = server.getLevel(mirror.dimension());
        if (mirrorLevel == null || !mirrorLevel.isLoaded(mirror.pos())
                || !(mirrorLevel.getBlockEntity(mirror.pos()) instanceof BlockEntityMirrorBase glass)
                || !glass.linked || glass.link == null) {
            return List.of();
        }
        Set<GlobalPos> far = BOUND.get(glass.link);
        return far == null ? List.of() : new ArrayList<>(far);
    }

    @Override
    protected List<BlockPos> farPartners(Level level, BlockPos pos, BlockState state) {
        List<BlockPos> out = new ArrayList<>();
        for (GlobalPos far : partners(level, pos)) {
            if (far.dimension() == level.dimension()) {
                out.add(far.pos());
            }
        }
        return out;
    }

    @Override
    protected int extraInput(Level level, BlockPos pos, BlockState state) {
        int best = 0;
        for (GlobalPos far : partners(level, pos)) {
            ServerLevel there = level.getServer().getLevel(far.dimension());
            if (far.dimension() != level.dimension() && there != null && there.isLoaded(far.pos())) {
                BlockState other = there.getBlockState(far.pos());
                if (other.getBlock() instanceof RedcrystalBlock crystal) {
                    best = Math.max(best, crystal.offer(there, far.pos(), other, pos));
                }
            }
        }
        return best;
    }

    @Override
    protected void notifyFar(Level level, BlockPos pos, BlockState state) {
        for (GlobalPos far : partners(level, pos)) {
            ServerLevel there = level.getServer().getLevel(far.dimension());
            if (there != null && there.isLoaded(far.pos())) {
                there.neighborChanged(far.pos(), this, far.pos());
            }
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        GlobalPos mirror = context.getItemInHand().get(AMRegistry.LINK.get());
        if (mirror == null || !inReach(mirror, context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof MercurialRedcrystalBlockEntity be) {
            be.setMirror(stack.get(AMRegistry.LINK.get()));
            update(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, RECHECK);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        update(level, pos, state);
        level.scheduleTick(pos, this, RECHECK);
    }
}
