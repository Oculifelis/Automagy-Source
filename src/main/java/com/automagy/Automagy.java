package com.automagy;

import com.automagy.item.HungryHandMirrorItem;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.common.NeoForgeMod;
import com.automagy.tank.Glyph;
import com.automagy.nether.NetherRune;
import com.automagy.nether.SirenLures;
import com.automagy.xp.Experience;
import com.automagy.xp.ExperienceEvents;
import com.automagy.archive.ArchiveCapacity;
import com.automagy.focus.CraftingKeyPayload;
import com.automagy.ring.EnderDisjunction;
import com.automagy.golem.WorkbenchUpgrade;
import com.automagy.boiler.BoilerBlockEntity;
import com.automagy.locus.EssentiaLocusBlockEntity;
import com.automagy.node.AdvancedNodeJar;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import org.slf4j.Logger;

/** Automagy, reimplemented for Thaumaturge (NeoForge 26.1.2) from the Thaumcraft 4 addon's public descriptions. */
@Mod(Automagy.MODID)
public final class Automagy {
    public static final String MODID = "automagia";
    public static final Logger LOG = LogUtils.getLogger();

    public Automagy(IEventBus modBus) {
        AMRegistry.register(modBus);
        SelfTest.register();
        LegacyIds.register();
        modBus.addListener(Automagy::capabilities);
        modBus.addListener(Glyph::register);
        modBus.addListener(BoilerBlockEntity::registerDataMaps);
        modBus.addListener(WorkbenchUpgrade::register);
        modBus.addListener(ArchiveCapacity::register);
        modBus.addListener(EnderDisjunction::register);
        modBus.addListener(CraftingKeyPayload::register);
        modBus.addListener(NetherRune::register);
        modBus.addListener(Experience::register);
        NeoForgeMod.enableMilkFluid();
        NeoForge.EVENT_BUS.addListener(HungryHandMirrorItem::onPickup);
        NeoForge.EVENT_BUS.addListener(AdvancedNodeJar::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EnderDisjunction::onTeleport);
        NeoForge.EVENT_BUS.addListener(SirenLures::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ExperienceEvents::onImpact);
        NeoForge.EVENT_BUS.addListener(ExperienceEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent e) ->
                e.sendRecipes(AMRegistry.INFUSION_ENCHANTING.get()));
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMRegistry.GREEDY_CHEST_BE.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMRegistry.EAGER_CHEST_BE.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMRegistry.TENACIOUS_CHEST_BE.get(), (be, side) -> be.handler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMRegistry.MELDING_MIRROR_BE.get(), (be, side) -> be.handler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, AMRegistry.THIRSTY_TANK_BE.get(), (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, AMRegistry.BOILER_BE.get(), (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMRegistry.BOILER_BE.get(), (be, side) -> be.fuel());
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMRegistry.GOLEM_WORKBENCH_BE.get(), (be, side) -> VanillaContainerWrapper.of(be.storage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMRegistry.DISPERSING_MIRROR_BE.get(), (be, side) -> be.handler());
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, AMRegistry.SPIRE_CONTROLLER_BE.get(), (be, side) -> be);
        event.registerBlockEntity(AspectCapabilities.CONTAINER, AMRegistry.ESSENTIA_LOCUS_BE.get(), (be, side) -> be);
        // the aggregator has no state of its own: it offers the locus under it as a source
        event.registerBlock(AspectCapabilities.CONTAINER, (level, pos, state, be, side) ->
                level.getBlockEntity(pos.below()) instanceof EssentiaLocusBlockEntity locus ? locus.asSource() : null,
                AMRegistry.ESSENTIA_AGGREGATOR.get());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
