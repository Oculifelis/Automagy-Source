package com.automagy.archive;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.golems.GolemHelper;
import com.leclowndu93150.thaumaturge.api.golems.ProvisionRequest;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The golemic taskmaster. It turns orders from requisition tomes into Thaumaturge provisioning requests, one stack at a
 * time, for golems on Provide seals to fill: at its drop-off (the block its back is on), or at the core's dispersing
 * mirror when there is one and the tome has an output mirror. A request that lapses is asked again once, then dropped.
 */
public class GolemTaskmasterBlockEntity extends ArchiveNodeBlockEntity {
    private static final int PERIOD = 20;
    private static final Codec<Order> ORDER = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("tome").forGetter(Order::tome),
            ItemResource.CODEC.fieldOf("item").forGetter(Order::item),
            Codec.INT.fieldOf("count").forGetter(Order::count),
            Codec.INT.optionalFieldOf("tries", 0).forGetter(Order::tries)).apply(i, Order::new));

    private final Deque<Order> queue = new ArrayDeque<>();
    private @Nullable Order active;
    private @Nullable ProvisionRequest request;

    public GolemTaskmasterBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.GOLEM_TASKMASTER_BE.get(), pos, state);
    }

    record Order(BlockPos tome, ItemResource item, int count, int tries) {
    }

    @Override
    public InventariumCoreBlockEntity.Kind kind() {
        return InventariumCoreBlockEntity.Kind.TASKMASTER;
    }

    void enqueue(BlockPos tome, ItemResource item, int count) {
        queue.add(new Order(tome.immutable(), item, count, 0));
        setChanged();
    }

    private void settle(Order order) {
        if (level.isLoaded(order.tome()) && level.getBlockEntity(order.tome()) instanceof RequisitionTomeBlockEntity tome) {
            tome.settled(order.item(), order.count());
        }
    }

    private void tick(InventariumCoreBlockEntity core) {
        if (active != null && request != null) {
            if (request.isSpent()) {
                settle(active);
            } else if (level.getGameTime() > request.expiresAt()
                    || !GolemHelper.getProvisionRequests(level).contains(request)) {
                if (active.tries() < 1) {
                    queue.addFirst(new Order(active.tome(), active.item(), active.count(), active.tries() + 1));
                } else {
                    settle(active);
                }
            } else {
                return;
            }
            active = null;
            request = null;
            setChanged();
        }
        Order next = queue.poll();
        if (next == null) {
            return;
        }
        Direction toDropOff = getBlockState().getValue(GolemTaskmasterBlock.FACING).getOpposite();
        BlockPos target = worldPosition.relative(toDropOff);
        Direction side = toDropOff.getOpposite();
        BlockPos disperser = core.disperser();
        if (disperser != null && level.getBlockEntity(next.tome()) instanceof RequisitionTomeBlockEntity tome && tome.outputMirror() != null) {
            target = disperser;
            side = Direction.UP;
            core.earmark(next.tome(), next.item(), next.count());
        }
        ItemStack stack = next.item().toStack(next.count());
        GolemHelper.requestProvisioning(level, target, side, stack);
        active = next;
        request = find(target, stack);
        if (request == null) {
            // Thaumaturge took no request (nobody can provide it): give up on this part of the order
            settle(next);
            active = null;
        }
        setChanged();
    }

    private @Nullable ProvisionRequest find(BlockPos target, ItemStack stack) {
        List<ProvisionRequest> all = new ArrayList<>(GolemHelper.getProvisionRequests(level));
        for (int i = all.size() - 1; i >= 0; i--) {
            ProvisionRequest r = all.get(i);
            if (target.equals(r.getPos()) && !r.isSpent() && ItemStack.isSameItemSameComponents(r.getStack(), stack)) {
                return r;
            }
        }
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GolemTaskmasterBlockEntity be) {
        if (level.getGameTime() % PERIOD != pos.asLong() % PERIOD) {
            return;
        }
        InventariumCoreBlockEntity core = be.core();
        if (core != null) {
            be.tick(core);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        queue.clear();
        in.read("queue", ORDER.listOf()).ifPresent(queue::addAll);
        // a request in flight does not outlive a reload: ask again
        in.read("active", ORDER).ifPresent(queue::addFirst);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("queue", ORDER.listOf(), List.copyOf(queue));
        if (active != null) {
            out.store("active", ORDER, active);
        }
    }
}
