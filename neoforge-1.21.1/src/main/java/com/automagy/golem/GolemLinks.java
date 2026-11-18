package com.automagy.golem;

import com.automagy.AMRegistry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Golem links. A golem linked to a device carries the device's position (data attachment {@code automagia:golem_link});
 * the device keeps the golems' ids, at most {@link #MAX} of them. A link holds only while both sides agree, so a
 * device that forgot a golem, or a golem relinked elsewhere, simply stops counting.
 */
public final class GolemLinks {
    public static final int MAX = 6;

    private GolemLinks() {
    }

    /** A block entity golems can be linked to. */
    public interface Device {
        Set<UUID> golems();

        void linksChanged();
    }

    public static Optional<GlobalPos> deviceOf(Entity golem) {
        return Optional.ofNullable(golem.getExistingDataOrNull(AMRegistry.GOLEM_LINK.get()));
    }

    /** Whether this golem is linked to the device at {@code pos}, on both sides. */
    public static boolean linked(Entity golem, Level level, Device device, net.minecraft.core.BlockPos pos) {
        return device.golems().contains(golem.getUUID())
                && deviceOf(golem).filter(d -> d.dimension() == level.dimension() && d.pos().equals(pos)).isPresent();
    }

    /** Links the golem to the device, unlinking it from any other loaded device first. False when the device is full. */
    public static boolean link(Entity golem, Level level, Device device, net.minecraft.core.BlockPos pos) {
        if (!device.golems().contains(golem.getUUID()) && device.golems().size() >= MAX) {
            return false;
        }
        deviceOf(golem).ifPresent(old -> {
            if (old.dimension() == level.dimension() && level.getBlockEntity(old.pos()) instanceof Device previous
                    && previous != device) {
                previous.golems().remove(golem.getUUID());
                previous.linksChanged();
            }
        });
        golem.setData(AMRegistry.GOLEM_LINK.get(), GlobalPos.of(level.dimension(), pos));
        device.golems().add(golem.getUUID());
        device.linksChanged();
        return true;
    }
}
