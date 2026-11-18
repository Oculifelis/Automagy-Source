package com.automagy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A device whose settings a Thaumaturge wand edits in place: wand right-click steps the selected setting to its next
 * value, sneak + wand right-click selects the next setting. The action bar shows "Setting: value" after each click.
 */
public abstract class ConfigurableBlockEntity extends BlockEntity {
    private int selected;

    protected ConfigurableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract int settingCount();

    /** Lang key suffix of setting i, under automagy.setting. */
    protected abstract String settingKey(int i);

    protected abstract Component valueName(int i);

    /** Steps setting i to its next value (wrapping). */
    protected abstract void step(int i);

    public void wandClick(Player player, boolean sneaking) {
        if (sneaking) {
            selected = (selected + 1) % settingCount();
        } else {
            step(selected);
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        player.sendOverlayMessage(Component.translatable("automagia.msg.setting",
                Component.translatable("automagia.setting." + settingKey(selected)), valueName(selected)));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        selected = in.getIntOr("selected", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("selected", selected);
    }
}
