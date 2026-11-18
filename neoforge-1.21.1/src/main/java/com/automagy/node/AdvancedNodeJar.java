package com.automagy.node;

import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.capability.IPlayerKnowledge;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.nodes.NodeModifier;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityJarNode;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.node.NodeJarRitual;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Advanced node jarring. Thaumaturge's node jar, built with a lid of {@code automagia:node_jar_lid} (silverwood logs)
 * instead of slabs and walls of {@code automagia:node_jar_glass} (warded glass): right-click the lid with a wand that
 * has no focus and, for the usual vis, the node is jarred at once and keeps its modifier.
 */
public final class AdvancedNodeJar {
    public static final TagKey<Block> LID = TagKey.create(Registries.BLOCK, Automagy.id("node_jar_lid"));
    public static final TagKey<Block> GLASS = TagKey.create(Registries.BLOCK, Automagy.id("node_jar_glass"));
    private static final int VIS = 70;

    private AdvancedNodeJar() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ICaster caster) || !caster.getFocusStack(stack).isEmpty()
                || !event.getLevel().getBlockState(event.getPos()).is(LID)) {
            return;
        }
        BlockPos node = findNode(event.getLevel(), event.getPos());
        if (node == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level) {
            jar(level, node, event.getEntity());
        }
    }

    /** The node whose lid this block is part of: two blocks below the lid's middle. */
    private static @Nullable BlockPos findNode(net.minecraft.world.level.Level level, BlockPos lid) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos at = lid.offset(dx, -2, dz);
                if (level.getBlockEntity(at) instanceof BlockEntityNode n && !(n instanceof BlockEntityJarNode)) {
                    return at;
                }
            }
        }
        return null;
    }

    private static boolean fits(net.minecraft.world.level.Level level, BlockPos node) {
        for (BlockPos p : NodeJarRitual.structurePositions(node)) {
            if (!level.getBlockState(p).is(p.getY() == node.getY() + 2 ? LID : GLASS)) {
                return false;
            }
        }
        return true;
    }

    private static void jar(ServerLevel level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof BlockEntityNode node) || node.isJarring()) {
            return;
        }
        IPlayerKnowledge knowledge = KnowledgeAccess.of(player);
        if (knowledge == null || !knowledge.isResearchKnown(Automagy.id("advanced_node_jar"))) {
            player.displayClientMessage(Component.translatable("automagia.node_jar.research"), true);
            return;
        }
        if (!fits(level, pos)) {
            player.displayClientMessage(Component.translatable("automagia.node_jar.structure"), true);
            return;
        }
        Map<ResourceKey<IAspect>, Integer> cost = new LinkedHashMap<>();
        for (ResourceKey<IAspect> primal : TTAspects.PRIMALS) {
            cost.put(primal, VIS);
        }
        if (!WandVisHelper.consumeSpecificFromHotbar(player, cost, true)) {
            player.displayClientMessage(Component.translatable("automagia.node_jar.vis", VIS), true);
            return;
        }
        NodeModifier modifier = node.getNodeModifier();
        for (BlockPos p : NodeJarRitual.structurePositions(pos)) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        NodeJarRitual.completeJar(level, pos, node);
        if (level.getBlockEntity(pos) instanceof BlockEntityJarNode jar) {
            jar.setNodeModifier(modifier);
            jar.setChanged();
            level.sendBlockUpdated(pos, jar.getBlockState(), jar.getBlockState(), Block.UPDATE_ALL);
        }
    }
}
