package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.SavedEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Sand falling in an hourglass. The mundane glass flips when activated mid-run (the sand flows back from where it
 * was); the magic one restarts from zero instead, or stops if it was repeating.
 */
public class HourglassBlockEntity extends ConfigurableBlockEntity {
    /** Magic hourglass times, in seconds: fine at the short end, up to an hour. */
    private static final int[] MAGIC_TIMES = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 45, 50, 60, 90, 120,
            180, 240, 300, 600, 900, 1200, 1800, 2700, 3600};
    private static final int PULSE = 4;

    enum Mode { PULSE, CONSTANT, ALTERNATING, NEGATIVE_PULSE }

    enum Gate { TRIGGER, WHILE_POWERED, WHILE_UNPOWERED, IGNORE }

    private int seconds = 5;
    private int remaining;
    private int pulse;
    private boolean lastPowered;
    private Mode mode = Mode.PULSE;
    private Gate gate = Gate.TRIGGER;
    private boolean repeat;
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
        } else if (repeat && remaining > 0) {
            remaining = 0;
        } else {
            remaining = length();
        }
        setChanged();
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
            case NEGATIVE_PULSE -> pulse == 0;
        };
    }

    @Override
    protected int settingCount() {
        return 4;
    }

    @Override
    protected String settingKey(int i) {
        return switch (i) {
            case 0 -> "mode";
            case 1 -> "repeat";
            case 2 -> "redstone";
            default -> "time";
        };
    }

    @Override
    protected Component valueName(int i) {
        return switch (i) {
            case 0 -> Component.translatable("automagia.hourglass.mode." + mode.name().toLowerCase());
            case 1 -> Component.translatable(repeat ? "automagia.value.on" : "automagia.value.off");
            case 2 -> Component.translatable("automagia.hourglass.gate." + gate.name().toLowerCase());
            default -> Component.translatable("automagia.value.seconds", seconds);
        };
    }

    @Override
    protected void step(int i) {
        switch (i) {
            case 0 -> mode = SavedEnum.next(mode);
            case 1 -> repeat = !repeat;
            case 2 -> gate = SavedEnum.next(gate);
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
    }
}
