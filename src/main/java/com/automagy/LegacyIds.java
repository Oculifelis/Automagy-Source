package com.automagy;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.api.capability.ResearchFlag;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.content.casters.FocusPouchItem;
import com.leclowndu93150.thaumaturge.content.research.PlayerKnowledge;
import com.leclowndu93150.thaumaturge.content.research.ResearchManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Worlds from before the mod id became "automagia": registry entries are aliased in AMRegistry; this moves the
 *  saved ids that live outside registries (Thaumaturge research and knowledge, spell parts inside foci). */
public final class LegacyIds {
    public static final String OLD = "automagy";
    private static final ResearchFlag[] FLAGS = ResearchFlag.values();

    private LegacyIds() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LegacyIds::onLogin);
    }

    /** The id under the current namespace, or null when it isn't an old one. */
    public static ResourceLocation renamed(ResourceLocation id) {
        return OLD.equals(id.getNamespace()) ? Automagy.id(id.getPath()) : null;
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                if (migrateResearch(player)) {
                    Automagy.LOG.info("Moved old research ids for {}", player.getName().getString());
                }
                migrateFoci(player);
            } catch (RuntimeException e) {
                Automagy.LOG.warn("Could not move old ids for {}", player.getName().getString(), e);
            }
        }
    }

    private static boolean migrateResearch(ServerPlayer player) {
        if (ResearchManager.of(player) instanceof PlayerKnowledge k && migrateResearch(k)) {
            k.queueSync(player);
            return true;
        }
        return false;
    }

    /** Moves research entries and category knowledge from the old namespace. True when something moved. */
    public static boolean migrateResearch(PlayerKnowledge k) {
        boolean changed = false;
        for (ResourceLocation old : List.copyOf(k.knownEntries())) {
            ResourceLocation now = renamed(old);
            if (now == null) {
                continue;
            }
            k.learn(now);
            int stage = k.stageOf(old);
            if (stage > k.stageOf(now)) {
                k.setStage(now, stage);
            }
            if (k.hasCompleted(old)) {
                k.markComplete(now);
            }
            for (ResearchFlag flag : FLAGS) {
                if (k.hasFlag(old, flag)) {
                    k.addFlag(now, flag);
                }
            }
            k.forget(old);
            changed = true;
        }
        ResourceKey<IResearchCategory> oldCat = ResourceKey.create(IResearchCategory.REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath(OLD, "automagy"));
        ResourceKey<IResearchCategory> newCat = ResourceKey.create(IResearchCategory.REGISTRY_KEY, Automagy.id("automagy"));
        for (KnowledgeType type : KnowledgeType.values()) {
            int amount = k.knowledgePoints(type, oldCat);
            if (amount > 0) {
                k.addKnowledgePoints(type, newCat, amount);
                k.addKnowledgePoints(type, oldCat, -amount);
                changed = true;
            }
        }
        return changed;
    }

    private static void migrateFoci(ServerPlayer player) {
        migrateContainer(player.getInventory());
        migrateContainer(player.getEnderChestInventory());
    }

    private static void migrateContainer(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && migrateStack(stack)) {
                container.setChanged();
            }
        }
    }

    /** Renames old spell parts on a focus, a caster's focus or the foci in a pouch. True when something changed. */
    public static boolean migrateStack(ItemStack stack) {
        boolean changed = migrateSpell(stack);
        if (stack.getItem() instanceof ICaster caster) {
            ItemStack focus = caster.getFocusStack(stack);
            if (!focus.isEmpty() && migrateSpell(focus)) {
                caster.setFocus(stack, focus);
                changed = true;
            }
        }
        if (stack.getItem() instanceof FocusPouchItem) {
            NonNullList<ItemStack> contents = FocusPouchItem.getInventory(stack);
            boolean any = false;
            for (ItemStack focus : contents) {
                any |= !focus.isEmpty() && migrateSpell(focus);
            }
            if (any) {
                FocusPouchItem.setInventory(stack, contents);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean migrateSpell(ItemStack focus) {
        Spell spell = Spells.spellOf(focus);
        if (spell == null || spell.isEmpty() || spell.root() == null) {
            return false;
        }
        SpellNode root = rename(spell.root());
        if (root == spell.root()) {
            return false;
        }
        Spells.setSpell(focus, spell.withRoot(root));
        return true;
    }

    /** The same node, or a copy with every old-namespace part key renamed. */
    static SpellNode rename(SpellNode node) {
        boolean changed = false;
        var part = node.part();
        ResourceLocation now = renamed(part.location());
        if (now != null) {
            part = ResourceKey.create(SpellPart.REGISTRY_KEY, now);
            changed = true;
        }
        List<SpellNode> children = new ArrayList<>(node.children().size());
        for (SpellNode child : node.children()) {
            SpellNode c = rename(child);
            changed |= c != child;
            children.add(c);
        }
        return changed ? new SpellNode(part, node.aspect(), node.settings(), children) : node;
    }
}
