package com.automagy.archive;

import com.automagy.AMRegistry;
import com.automagy.block.MeldingMirrorBlockEntity;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The dispersing mirror. While it is linked to an Inventarium core, taskmasters send each tome's order to it, and it
 * passes what arrives on to the output mirror of the tome that asked for it. Anything nobody ordered is spat out.
 */
public class DispersingMirrorBlockEntity extends ArchiveNodeBlockEntity {
    private @Nullable ResourceHandler<ItemResource> handler;

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
            try (Transaction tx = Transaction.openRoot()) {
                int n = melding.handler().insert(0, ItemResource.of(stack), stack.getCount(), tx);
                if (n == stack.getCount()) {
                    tx.commit();
                    return true;
                }
            }
            return false;
        }
        BlockPos p = where.pos();
        there.addFreshEntity(new ItemEntity(there, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, stack));
        return true;
    }

    /** Golems and pipes put items in; nothing comes out. Items move on when the transfer commits. */
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
            return true;
        }

        @Override
        public int insert(int i, ItemResource res, int amount, TransactionContext tx) {
            if (res.isEmpty() || amount <= 0) {
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
            go.forEach(DispersingMirrorBlockEntity.this::receive);
        }
    }
}
