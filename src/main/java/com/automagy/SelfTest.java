package com.automagy;

import com.automagy.archive.InventariumCoreBlockEntity;
import com.automagy.block.GreedyChestBlockEntity;
import com.automagy.block.HourglassBlockEntity;
import com.automagy.block.VisReaderBlockEntity;
import com.automagy.golem.EntitySignalBlockEntity;
import com.automagy.nether.NethermindBlockEntity;
import com.automagy.focus.FocusCrafting;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.api.capability.ResearchFlag;
import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.content.research.PlayerKnowledge;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Save/load round trips of the block entities' state machines, run once the dev server has started when the JVM has
 * {@code -Dautomagia.selftest=true} (only the Gradle server run sets it). Each check loads a saved form into a fresh
 * block entity, saves it, loads that into another fresh one and saves again: both saves must hold the expected values,
 * and keys that belong to another state must be gone. Logs one {@code SELFTEST PASS} or {@code SELFTEST FAIL} line each.
 */
final class SelfTest {
    private SelfTest() {
    }

    static void register() {
        if (Boolean.getBoolean("automagia.selftest")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> run(e.getServer().registryAccess()));
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> PickupGateTest.run(e.getServer()));
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> BehaviourTest.run(e.getServer()));
        }
    }

    private static void run(HolderLookup.Provider reg) {
        try {
            legacy();
            Supplier<BlockEntity> greedy = () -> new GreedyChestBlockEntity(BlockPos.ZERO, AMRegistry.GREEDY_CHEST.get().defaultBlockState());
            check(reg, "greedy_chest mode by name", greedy, tag("mode", "stock"), tag("mode", "stock"));
            check(reg, "greedy_chest default", greedy, tag(), tag("mode", "split"));
            Supplier<BlockEntity> hourglass = () -> new HourglassBlockEntity(BlockPos.ZERO, AMRegistry.HOURGLASS.get().defaultBlockState());
            check(reg, "hourglass mode+gate by name", hourglass, tag("mode", "alternating", "gate", "while_unpowered"),
                    tag("mode", "alternating", "gate", "while_unpowered"));
            check(reg, "hourglass default", hourglass, tag(), tag("mode", "pulse", "gate", "trigger"));
            Supplier<BlockEntity> reader = () -> new VisReaderBlockEntity(BlockPos.ZERO, AMRegistry.VIS_READER.get().defaultBlockState());
            check(reg, "vis_reader mode by name", reader, tag("mode", "count"), tag("mode", "count"));
            check(reg, "vis_reader default", reader, tag(), tag("mode", "total"));
            Supplier<BlockEntity> signal = () -> new EntitySignalBlockEntity(BlockPos.ZERO, AMRegistry.ENTITY_SIGNAL.get().defaultBlockState());
            check(reg, "entity_signal ages by name", signal, tag("ages", "children"), tag("ages", "children"));
            Supplier<BlockEntity> brain = () -> new NethermindBlockEntity(BlockPos.ZERO, AMRegistry.NETHERMIND.get().defaultBlockState());
            check(reg, "nethermind active keeps timer + lure", brain, tag("state", "active", "timer", 37, "lure_paid", 2),
                    tag("state", "active", "timer", 37, "lure_paid", 2));
            check(reg, "nethermind idle drops timer + lure", brain, tag("state", "idle", "timer", 37, "lure_paid", 2),
                    tag("state", "idle"), "timer", "lure_paid");
            check(reg, "nethermind default idle", brain, tag(), tag("state", "idle"), "timer", "lure_paid");
            Supplier<BlockEntity> core = () -> new InventariumCoreBlockEntity(BlockPos.ZERO, AMRegistry.INVENTARIUM_CORE.get().defaultBlockState());
            check(reg, "inventarium formed keeps numbers", core, tag("formed", true, "capacity", 54, "clusters", 2, "memory", 3),
                    tag("formed", true, "capacity", 54, "clusters", 2, "memory", 3));
            check(reg, "inventarium unformed drops numbers", core, tag("formed", false, "capacity", 54, "clusters", 2, "memory", 3),
                    tag("formed", false), "capacity", "clusters", "memory");
        } catch (Exception ex) {
            Automagy.LOG.error("SELFTEST FAIL crashed", ex);
        }
    }

    /** Loads {@code in}, saves, loads that save into a fresh one and saves again; both saves must match. */
    private static void check(HolderLookup.Provider reg, String name, Supplier<BlockEntity> make, CompoundTag in, CompoundTag expect,
            String... absent) {
        BlockEntity a = make.get();
        a.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, reg, in));
        CompoundTag first = a.saveCustomOnly(reg);
        BlockEntity b = make.get();
        b.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, reg, first));
        CompoundTag second = b.saveCustomOnly(reg);
        boolean ok = first.equals(second);
        for (String k : expect.keySet()) {
            ok &= expect.get(k).equals(first.get(k));
        }
        for (String k : absent) {
            ok &= !first.contains(k);
        }
        Automagy.LOG.info("SELFTEST {} {}: {}", ok ? "PASS" : "FAIL", name, second);
    }

    /** Old-world ids: registry aliases from the "automagy" namespace, research and knowledge moved, spell parts renamed. */
    private static void legacy() {
        String block = BuiltInRegistries.BLOCK.getKey(AMRegistry.GREEDY_CHEST.get()).getPath();
        String be = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(AMRegistry.GREEDY_CHEST_BE.get()).getPath();
        Automagy.LOG.info("SELFTEST {} legacy alias block", BuiltInRegistries.BLOCK.getValue(old(block)) == AMRegistry.GREEDY_CHEST.get() ? "PASS" : "FAIL");
        Automagy.LOG.info("SELFTEST {} legacy alias item", BuiltInRegistries.ITEM.getValue(old(block)) == AMRegistry.GREEDY_CHEST.get().asItem() ? "PASS" : "FAIL");
        Automagy.LOG.info("SELFTEST {} legacy alias block entity", BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(old(be)) == AMRegistry.GREEDY_CHEST_BE.get() ? "PASS" : "FAIL");

        PlayerKnowledge k = new PlayerKnowledge();
        Identifier oldId = old("golem_workbench");
        Identifier newId = Automagy.id("golem_workbench");
        ResourceKey<IResearchCategory> oldCat = ResourceKey.create(IResearchCategory.REGISTRY_KEY, old("automagy"));
        ResourceKey<IResearchCategory> newCat = ResourceKey.create(IResearchCategory.REGISTRY_KEY, Automagy.id("automagy"));
        k.addResearch(oldId);
        k.setResearchStage(oldId, 2);
        k.markComplete(oldId);
        k.setResearchFlag(oldId, ResearchFlag.PAGE);
        k.addKnowledge(KnowledgeType.THEORY, oldCat, 5);
        boolean moved = LegacyIds.migrateResearch(k);
        boolean ok = moved && k.isResearchComplete(newId) && k.researchStage(newId) == 2 && k.hasResearchFlag(newId, ResearchFlag.PAGE)
                && !k.researchList().contains(oldId) && k.rawKnowledge(KnowledgeType.THEORY, newCat) == 5
                && k.rawKnowledge(KnowledgeType.THEORY, oldCat) == 0;
        Automagy.LOG.info("SELFTEST {} legacy research moved: {}", ok ? "PASS" : "FAIL", k.researchList());

        ResourceKey<SpellPart> oldPart = ResourceKey.create(SpellPart.REGISTRY_KEY, old("crafting"));
        SpellNode tree = new SpellNode(oldPart, Optional.empty(), Map.of(),
                List.of(new SpellNode(oldPart, Optional.empty(), Map.of("gratis", 1), List.of())));
        SpellNode renamed = LegacyIds.rename(tree);
        boolean spellOk = renamed.part().equals(FocusCrafting.PART) && renamed.children().get(0).part().equals(FocusCrafting.PART)
                && renamed.children().get(0).settings().equals(Map.of("gratis", 1));
        Automagy.LOG.info("SELFTEST {} legacy spell parts renamed", spellOk ? "PASS" : "FAIL");
    }

    private static Identifier old(String path) {
        return Identifier.fromNamespaceAndPath(LegacyIds.OLD, path);
    }

    private static CompoundTag tag(Object... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            switch (kv[i + 1]) {
                case String s -> t.putString(k, s);
                case Integer n -> t.putInt(k, n);
                case Boolean z -> t.putBoolean(k, z);
                default -> throw new IllegalArgumentException(k);
            }
        }
        return t;
    }
}
