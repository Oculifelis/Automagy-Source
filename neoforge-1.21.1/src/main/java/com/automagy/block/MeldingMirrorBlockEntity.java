package com.automagy.block;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A one-way mirror. Its crystalline eye watches a magic mirror; while that mirror is paired, whatever is thrown into
 * the melding mirror or piped into it comes out of the watched mirror's partner.
 */
public class MeldingMirrorBlockEntity extends BlockEntity implements com.automagy.port.PreRemove {
    private ItemStack eye = ItemStack.EMPTY;
    private @Nullable IItemHandler handler;

    public MeldingMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.MELDING_MIRROR_BE.get(), pos, state);
    }

    ItemStack eye() {
        return eye;
    }

    void setEye(ItemStack stack) {
        eye = stack;
        setChanged();
    }

    /** The mirror items come out of: the partner of the mirror the eye watches, when that pair is sound. */
    private @Nullable BlockEntityMirror destination() {
        GlobalPos watched = eye.get(AMRegistry.LINK.get());
        if (watched == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        ServerLevel there = server.getServer().getLevel(watched.dimension());
        if (there == null || !there.isLoaded(watched.pos())
                || !(there.getBlockEntity(watched.pos()) instanceof BlockEntityMirror mirror)
                || !mirror.linked || mirror.link == null || !mirror.isLinkValid()) {
            return null;
        }
        ServerLevel far = server.getServer().getLevel(mirror.link.dimension());
        return far != null && far.getBlockEntity(mirror.link.pos()) instanceof BlockEntityMirror out ? out : null;
    }

    private boolean send(ItemStack stack) {
        BlockEntityMirror out = destination();
        if (out == null || !out.transportDirect(stack)) {
            return false;
        }
        level.playSound(null, worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.1f, 1f);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MeldingMirrorBlockEntity be) {
        if (level.getGameTime() % 4 != 0 || be.eye.isEmpty()) {
            return;
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.1), Entity::isAlive)) {
            if (be.send(item.getItem().copy())) {
                item.discard();
            } else {
                return;
            }
        }
    }

    /** Pipes and hoppers can put items in, never take any out. Items go through at once. */
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
            if (stack.isEmpty() || destination() == null) {
                return stack;
            }
            if (!simulate && !send(stack.copy())) {
                // the pair broke between the check and the send: drop rather than lose the items
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack.copy());
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
            return destination() != null;
        }
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !eye.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, eye);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        eye = in.read("eye", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.store("eye", ItemStack.OPTIONAL_CODEC, eye);
    }
}
