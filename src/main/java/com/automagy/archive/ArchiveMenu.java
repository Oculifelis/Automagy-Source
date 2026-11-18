package com.automagy.archive;

import com.automagy.AMRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * What an unseen scribe reads (or, toggled, the whole Inventarium), and the requisition tome's order sheet. The lists
 * are sent once, when it opens. On a tome, clicking an item changes the amount on the sheet (the same arithmetic runs
 * on both sides) and Order sends the sheet to the core.
 */
public class ArchiveMenu extends AbstractContainerMenu {
    public static final int ORDER = 0;
    public static final int COLUMNS = 9;
    public static final int ROWS = 5;
    /** What a click on an item does to its amount; {@link #ALT} lets a tome ask for more than is stored. */
    public static final int PLUS = 0, MINUS = 1, PLUS_STACK = 2, MINUS_STACK = 3, PLUS_TEN = 4, MINUS_TEN = 5, ALT = 8;

    public record Entry(ItemStack item, int stored) {
    }

    private final boolean tome;
    private final List<Entry> own;
    private final List<Entry> all;
    private final int[] sheet;
    private final @Nullable RequisitionTomeBlockEntity tomeBe;
    private final BlockPos pos;

    public ArchiveMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBoolean(), buf.readBlockPos(), read(buf), read(buf), null, new SimpleContainer(3));
    }

    private ArchiveMenu(int id, Inventory inv, boolean tome, BlockPos pos, List<Entry> own, List<Entry> all,
                        @Nullable RequisitionTomeBlockEntity tomeBe, Container tomeSlots) {
        super(AMRegistry.ARCHIVE_MENU.get(), id);
        this.tome = tome;
        this.pos = pos;
        this.own = own;
        this.all = all;
        this.tomeBe = tomeBe;
        this.sheet = new int[all.size()];
        if (tome) {
            addSlot(new Slot(tomeSlots, RequisitionTomeBlockEntity.INPUT, 8, 116));
            for (int i = 1; i < 3; i++) {
                addSlot(new Slot(tomeSlots, i, 8 + i * 18, 116) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(AMRegistry.CRYSTALLINE_EYE.get());
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 198));
        }
    }

    public static void openScribe(ServerPlayer player, UnseenScribeBlockEntity scribe) {
        InventariumCoreBlockEntity core = scribe.core();
        List<Entry> own = entries(scribe.read());
        List<Entry> all = core == null ? List.of() : entries(core.totals());
        open(player, false, scribe.getBlockPos(), own, all, null, new SimpleContainer(3), "block.automagia.unseen_scribe");
    }

    public static void openTome(ServerPlayer player, RequisitionTomeBlockEntity tome) {
        InventariumCoreBlockEntity core = tome.core();
        List<Entry> all = core == null ? List.of() : entries(core.totals());
        open(player, true, tome.getBlockPos(), List.of(), all, tome, tome.slots(), "block.automagia.requisition_tome");
    }

    private static void open(ServerPlayer player, boolean tome, BlockPos pos, List<Entry> own, List<Entry> all,
                             @Nullable RequisitionTomeBlockEntity tomeBe, Container slots, String title) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ArchiveMenu(id, inv, tome, pos, own, all, tomeBe, slots),
                Component.translatable(title)), buf -> {
            buf.writeBoolean(tome);
            buf.writeBlockPos(pos);
            write(buf, own);
            write(buf, all);
        });
    }

    private static List<Entry> entries(Map<ItemResource, Integer> counts) {
        List<Entry> out = new ArrayList<>();
        counts.forEach((res, n) -> out.add(new Entry(res.toStack(1), n)));
        out.sort(Comparator.comparing((Entry e) -> e.item().getHoverName().getString()).thenComparing(e -> -e.stored()));
        return out;
    }

    private static void write(RegistryFriendlyByteBuf buf, List<Entry> list) {
        buf.writeVarInt(list.size());
        for (Entry e : list) {
            ItemStack.STREAM_CODEC.encode(buf, e.item());
            buf.writeVarInt(e.stored());
        }
    }

    private static List<Entry> read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Entry(ItemStack.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        }
        return out;
    }

    public boolean isTome() {
        return tome;
    }

    public List<Entry> own() {
        return own;
    }

    public List<Entry> all() {
        return all;
    }

    public int sheet(int index) {
        return index >= 0 && index < sheet.length ? sheet[index] : 0;
    }

    /** The button id for a click on the item at this index of {@link #all()}. */
    public static int clickId(int index, int code) {
        return 1 + (index << 4 | code);
    }

    /** Changes the sheet; the same on both sides. */
    public void apply(int index, int code) {
        if (index < 0 || index >= sheet.length) {
            return;
        }
        Entry e = all.get(index);
        int step = switch (code & 7) {
            case PLUS -> 1;
            case MINUS -> -1;
            case PLUS_STACK -> e.item().getMaxStackSize();
            case MINUS_STACK -> -e.item().getMaxStackSize();
            case PLUS_TEN -> 10;
            case MINUS_TEN -> -10;
            default -> 0;
        };
        int cap = (code & ALT) != 0 ? 64 * 27 : e.stored();
        sheet[index] = Math.max(0, Math.min(cap, sheet[index] + step));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!tome) {
            return false;
        }
        if (id == ORDER) {
            if (tomeBe != null) {
                Map<ItemResource, Integer> wanted = new LinkedHashMap<>();
                for (int i = 0; i < sheet.length; i++) {
                    if (sheet[i] > 0) {
                        wanted.merge(ItemResource.of(all.get(i).item()), sheet[i], Integer::sum);
                    }
                }
                if (!wanted.isEmpty()) {
                    player.sendOverlayMessage(tomeBe.order(wanted));
                }
            }
            java.util.Arrays.fill(sheet, 0);
            return true;
        }
        int packed = id - 1;
        apply(packed >> 4, packed & 15);
        return true;
    }

    /** Called on the client when Order is pressed, to clear its copy of the sheet. */
    public void clearSheet() {
        java.util.Arrays.fill(sheet, 0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        int tomeEnd = tome ? 3 : 0;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < tomeEnd ? moveItemStackTo(stack, tomeEnd, slots.size(), true)
                : tome && moveItemStackTo(stack, 0, 1, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
