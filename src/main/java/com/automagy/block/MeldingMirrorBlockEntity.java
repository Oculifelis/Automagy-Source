package com.automagy.block;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A one-way mirror. Its crystalline eye watches a magic mirror; while that mirror is paired, whatever is thrown into
 * the melding mirror or piped into it comes out of the watched mirror's partner.
 */
public class MeldingMirrorBlockEntity extends BlockEntity {
    private ItemStack eye = ItemStack.EMPTY;
    private @Nullable ResourceHandler<ItemResource> handler;

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
                || !mirror.paired || mirror.link == null || !mirror.verifyPairing()) {
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

    /** Pipes and hoppers can put items in, never take any out. Items go through when the transfer commits. */
    public ResourceHandler<ItemResource> handler() {
        if (handler == null) {
            handler = new InputHandler();
        }
        return handler;
    }

    private final class InputHandler extends SnapshotJournal<List<ItemStack>> implements ResourceHandler<ItemResource> {
        private List<ItemStack> pending = new ArrayList<>();

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int i) {
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int i) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int i, ItemResource res) {
            return res.getMaxStackSize();
        }

        @Override
        public boolean isValid(int i, ItemResource res) {
            return destination() != null;
        }

        @Override
        public int insert(int i, ItemResource res, int amount, TransactionContext tx) {
            if (res.isEmpty() || amount <= 0 || destination() == null) {
                return 0;
            }
            updateSnapshots(tx);
            pending.add(res.toStack(amount));
            return amount;
        }

        @Override
        public int extract(int i, ItemResource res, int amount, TransactionContext tx) {
            return 0;
        }

        @Override
        protected List<ItemStack> createSnapshot() {
            return new ArrayList<>(pending);
        }

        @Override
        protected void revertToSnapshot(List<ItemStack> snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onRootCommit(List<ItemStack> original) {
            List<ItemStack> go = pending;
            pending = new ArrayList<>();
            for (ItemStack stack : go) {
                if (!send(stack)) {
                    // the pair broke between the check and the commit: drop rather than lose the items
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
                }
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !eye.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, eye);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        eye = in.read("eye", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("eye", ItemStack.OPTIONAL_CODEC, eye);
    }
}
