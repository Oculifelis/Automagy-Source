package com.automagy;

import com.automagy.item.HungryHandMirrorItem;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * A player without the hungry hand mirror research picks items up (with and without a linked mirror) and then uses the
 * mirror three times in a row. Pickups must send no action bar line; the uses must send exactly one (two-second limit).
 */
final class PickupGateTest {
    private PickupGateTest() {
    }

    static void run(MinecraftServer server) {
        try {
            check(server);
        } catch (RuntimeException ex) {
            Automagy.LOG.error("SELFTEST FAIL pickup gate crashed", ex);
        }
    }

    private static void check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        int[] bars = {0};
        Connection dummy = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void setListenerForServerboundHandshake(PacketListener listener) {
            }
        };
        player.connection = new ServerGamePacketListenerImpl(server, dummy, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public boolean hasChannel(net.minecraft.resources.Identifier payloadId) {
                return false;
            }

            @Override
            public void send(Packet<?> packet) {
                if (packet instanceof ClientboundSetActionBarTextPacket) {
                    bars[0]++;
                }
            }
        };
        boolean noResearch = !KnowledgeAccess.of(player).isResearchComplete(HungryHandMirrorItem.GATE);
        player.getInventory().clearContent();
        pickup(player, level);
        int bare = bars[0];
        ItemStack mirror = new ItemStack(AMRegistry.HUNGRY_HAND_MIRROR.get());
        mirror.set(TTDataComponents.MIRROR_LINK.get(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));
        player.setItemInHand(InteractionHand.MAIN_HAND, mirror);
        for (int i = 0; i < 5; i++) {
            pickup(player, level);
        }
        int withMirror = bars[0] - bare;
        bars[0] = 0;
        for (int i = 0; i < 3; i++) {
            mirror.getItem().use(level, player, InteractionHand.MAIN_HAND);
        }
        int uses = bars[0];
        boolean ok = noResearch && bare == 0 && withMirror == 0 && uses == 1;
        Automagy.LOG.info("SELFTEST {} pickup gate quiet: research missing {}, bars on pickup {} / {} with mirror, on 3 uses {}",
                ok ? "PASS" : "FAIL", noResearch, bare, withMirror, uses);
        player.getInventory().clearContent();
    }

    private static void pickup(FakePlayer player, ServerLevel level) {
        ItemStack stack = new ItemStack(Items.COBBLESTONE, 4);
        ItemEntity entity = new ItemEntity(level, 0, 0, 0, stack.copy());
        entity.setItem(ItemStack.EMPTY);
        HungryHandMirrorItem.onPickup(new ItemEntityPickupEvent.Post(player, entity, stack));
    }
}
