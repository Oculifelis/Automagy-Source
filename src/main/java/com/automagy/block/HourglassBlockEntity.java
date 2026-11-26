package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.SavedEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Sand falling in an hourglass. The mundane glass flips when activated mid-run (the sand flows back from where it
 * was). The magic one chooses: flip like the mundane glass, move the sand to the top (restart) or move it to the
 * bottom (end early with no signal, which also ends a repeat).
 */
public class HourglassBlockEntity extends ConfigurableBlockEntity {
    /** Magic hourglass times, in seconds: fine at the short end, up to an hour. */
    private static final int[] MAGIC_TIMES = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 45, 50, 60, 90, 120,
            180, 240, 300, 600, 900, 1200, 1800, 2700, 3600};
    private static final int PULSE = 4;

    enum Mode { PULSE, CONSTANT, ALTERNATING, NEGATIVE_PULSE, DISABLED }

    /** What a magic glass does when activated while sand is still falling. */
    enum Flip { TOP, BOTTOM, STANDARD }

    enum Gate { TRIGGER, WHILE_POWERED, WHILE_UNPOWERED, IGNORE }

    private int seconds = 5;
    private int remaining;
    private int pulse;
    private boolean lastPowered;
    private Mode mode = Mode.PULSE;
    private Gate gate = Gate.TRIGGER;
    private boolean repeat;
    private Flip flip = Flip.TOP;
    private int lastLevel;

    public HourglassBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.HOURGLASS_BE.get(), pos, state);
    }

    private boolean magic() {
        return getBlockState().getBlock() instanceof HourglassBlock glass && glass.magic();
    }

    private int length() {
        return seconds * 20;
    }

    public int sandLevel() {
        return remaining <= 0 ? 0 : Math.max(1, (int) Math.ceil(15.0 * remaining / length()));
    }

    /** Eighths of sand left in the top bulb, as the model draws it (HourglassBlock.SAND). */
    private int eighths() {
        return remaining <= 0 ? 0 : Math.clamp((long) Math.ceil(8.0 * remaining / length()) - 1, 0, 7);
    }

    /** Client-side: game time the renderer first saw the current turn, so the tumble starts from its own frame. */
    public long turnSeenAt = -1L;
    /** Client only: game time the turn was seen to end, while the landed glass is still drawn by the renderer. */
    public long landedAt = -1L;
    /** Client only: game time the last synced `remaining` was first read, so the sand can sink by the clock. */
    private long syncedAt = Long.MIN_VALUE;

    /**
     * Client side, for the renderer: how much sand is still in the top bulb, from 1 just turned to 0 run out, worked
     * out from the last synced count and the clock every frame so the sand sinks smoothly instead of in eighths.
     */
    public float sandOnTop(float partialTick) {
        if (level == null || length() <= 0) {
            return -1.0F;
        }
        if (syncedAt == Long.MIN_VALUE) {
            syncedAt = level.getGameTime();
        }
        double left = remaining - (level.getGameTime() - syncedAt + (double) partialTick);
        return (float) Math.max(0.0D, Math.min(1.0D, left / length()));
    }

    public boolean isMagic() {
        return magic();
    }

    public void activate() {
        if (!magic()) {
            remaining = remaining > 0 ? length() - remaining : length();
            if (level != null && !level.isClientSide()) { // turned over: the glass tumbles end over end
                BlockState now = level.getBlockState(worldPosition);
                if (now.getBlock() instanceof HourglassBlock && !now.getValue(HourglassBlock.TURNING)) {
                    level.setBlock(worldPosition, now.setValue(HourglassBlock.TURNING, true)
                            .setValue(HourglassBlock.SAND, eighths()), Block.UPDATE_ALL);
                    level.scheduleTick(worldPosition, now.getBlock(), HourglassBlock.TURN_TICKS);
                }
            }
        } else if (remaining <= 0) {
            remaining = length();
        } else {
            remaining = switch (flip) {
                case TOP -> length();
                case BOTTOM -> 0; // straight to the bottom: no pulse, and serverTick does not restart a repeat
                case STANDARD -> length() - remaining;
            };
        }
        setChanged();
        if (level != null && !level.isClientSide()) { // the client draws the sand from this count
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void onRedstone(boolean powered) {
        if (powered != lastPowered) {
            lastPowered = powered;
            if (powered && (!magic() || gate == Gate.TRIGGER)) {
                activate();
            }
            setChanged();
        }
    }

    public void stepMundaneTime(Player player) {
        seconds = seconds >= 60 ? 5 : seconds + 5;
        setChanged();
        player.sendOverlayMessage(Component.translatable("automagia.msg.hourglass_time", seconds));
    }

    private boolean flowing() {
        if (!magic()) {
            return true;
        }
        return switch (gate) {
            case WHILE_POWERED -> lastPowered;
            case WHILE_UNPOWERED -> !lastPowered;
            default -> true;
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HourglassBlockEntity glass) {
        boolean magic = glass.magic();
        if (magic && glass.remaining <= 0 && glass.gate != Gate.TRIGGER && glass.gate != Gate.IGNORE && glass.flowing()
                && glass.pulse == 0) {
            glass.remaining = glass.length(); // gated glasses run by themselves while the gate is open
        }
        boolean settled = false;
        if (glass.remaining > 0 && glass.flowing()) {
            glass.remaining--;
            if (glass.remaining == 0) {
                settled = true;
                if (magic && glass.repeat) {
                    glass.remaining = glass.length();
                }
            }
            glass.setChanged();
        }
        if (settled) {
            glass.pulse = PULSE;
        } else if (glass.pulse > 0) {
            glass.pulse--;
        }

        BlockState now = level.getBlockState(pos); // activate() may have set TURNING this tick: keep it
        if (now.getBlock() instanceof HourglassBlock) {
            BlockState next = now.setValue(HourglassBlock.POWERED, glass.output())
                    .setValue(HourglassBlock.SAND, glass.eighths())
                    .setValue(HourglassBlock.RUNNING, glass.remaining > 0 && glass.flowing());
            if (next != now) {
                level.setBlock(pos, next, Block.UPDATE_ALL);
            }
        }
        int sand = glass.sandLevel();
        if (sand != glass.lastLevel) {
            glass.lastLevel = sand;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private boolean output() {
        boolean falling = remaining > 0;
        if (!magic()) {
            return pulse > 0;
        }
        return switch (mode) {
            case PULSE -> pulse > 0;
            case CONSTANT -> falling;
            case ALTERNATING -> falling && (remaining / 10) % 2 == 0;
            case NEGATIVE_PULSE -> falling && pulse == 0;
            case DISABLED -> false;
        };
    }

    @Override
    protected int settingCount() {
        return 5;
    }

    @Override
    protected String settingKey(int i) {
        return switch (i) {
            case 0 -> "mode";
            case 1 -> "repeat";
            case 2 -> "redstone";
            case 3 -> "time";
            default -> "reactivation";
        };
    }

    @Override
    protected Component valueName(int i) {
        return switch (i) {
            case 0 -> Component.translatable("automagia.hourglass.mode." + mode.name().toLowerCase());
            case 1 -> Component.translatable(repeat ? "automagia.value.on" : "automagia.value.off");
            case 2 -> Component.translatable("automagia.hourglass.gate." + gate.name().toLowerCase());
            case 3 -> Component.translatable("automagia.value.seconds", seconds);
            default -> Component.translatable("automagia.hourglass.flip." + flip.name().toLowerCase());
        };
    }

    @Override
    protected void step(int i) {
        switch (i) {
            case 0 -> mode = SavedEnum.next(mode);
            case 1 -> repeat = !repeat;
            case 2 -> gate = SavedEnum.next(gate);
            case 4 -> flip = SavedEnum.next(flip);
            default -> {
                int next = MAGIC_TIMES[0];
                for (int t : MAGIC_TIMES) {
                    if (t > seconds) {
                        next = t;
                        break;
                    }
                }
                seconds = next;
                remaining = Math.min(remaining, length());
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        seconds = Math.max(1, in.getIntOr("seconds", 5));
        remaining = in.getIntOr("remaining", 0);
        pulse = in.getIntOr("pulse", 0);
        lastPowered = in.getBooleanOr("last_powered", false);
        mode = SavedEnum.read(in, "mode", Mode.PULSE);
        gate = SavedEnum.read(in, "gate", Gate.TRIGGER);
        repeat = in.getBooleanOr("repeat", false);
        flip = SavedEnum.read(in, "flip", Flip.TOP);
        syncedAt = Long.MIN_VALUE;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("seconds", seconds);
        out.putInt("remaining", remaining);
        out.putInt("pulse", pulse);
        out.putBoolean("last_powered", lastPowered);
        SavedEnum.write(out, "mode", mode);
        SavedEnum.write(out, "gate", gate);
        out.putBoolean("repeat", repeat);
        SavedEnum.write(out, "flip", flip);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
