package com.automagy.archive;

import com.automagy.AMRegistry;
import com.automagy.block.MeldingMirrorBlockEntity;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ItemResource;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The dispersing mirror. While it is linked to an Inventarium core, taskmasters send each tome's order to it, and it
 * passes what arrives on to the output mirror of the tome that asked for it. Anything nobody ordered is spat out.
 */
public class DispersingMirrorBlockEntity extends ArchiveNodeBlockEntity {
    private @Nullable IItemHandler handler;

    public DispersingMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.DISPERSING_MIRROR_BE.get(), pos, state);
    }

    @Override
    public InventariumCoreBlockEntity.Kind kind() {
        return InventariumCoreBlockEntity.Kind.DISPERSER;
    }

    private void receive(ItemStack stack) {
        InventariumCoreBlockEntity core = core();
        BlockPos tomePos = core == null ? null : core.claim(ItemResource.of(stack), stack.getCount());
        if (tomePos != null && level.getBlockEntity(tomePos) instanceof RequisitionTomeBlockEntity tome) {
            GlobalPos out = tome.outputMirror();
            if (out != null && deliver((ServerLevel) level, out, stack)) {
                level.playSound(null, worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.1f, 1f);
                return;
            }
        }
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
    }

    /** Puts items out at a mirror: a magic mirror sends them out of itself, a melding mirror on through its own eye;
     *  anything else gets them dropped on top. */
    static boolean deliver(ServerLevel level, GlobalPos where, ItemStack stack) {
        ServerLevel there = level.getServer().getLevel(where.dimension());
        if (there == null || !there.isLoaded(where.pos())) {
            return false;
        }
        if (there.getBlockEntity(where.pos()) instanceof BlockEntityMirror mirror) {
            return mirror.transportDirect(stack);
        }
        if (there.getBlockEntity(where.pos()) instanceof MeldingMirrorBlockEntity melding) {
            IItemHandler h = melding.handler();
            if (!h.insertItem(0, stack, true).isEmpty()) {
                return false;
            }
            h.insertItem(0, stack, false);
            return true;
        }
        BlockPos p = where.pos();
        there.addFreshEntity(new ItemEntity(there, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, stack));
        return true;
    }

    /** Golems and pipes put items in; nothing comes out. Items move on at once. */
    public IItemHandler handler() {
        if (handler == null) {
            handler = new InputHandler();
        }
        return handler;
    }

    private final class InputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int i) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int i, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return stack;
            }
            if (!simulate) {
                receive(stack.copy());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int i, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int i) {
            return 64;
        }

        @Override
        public boolean isItemValid(int i, ItemStack stack) {
            return true;
        }
    }
}
