package com.automagy.archive;

import java.util.function.ObjIntConsumer;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** A block a tally box counts by asking it rather than by its item handler: the Inventarium core, a requisition tome. */
public interface TallySource {
    void tally(ObjIntConsumer<ItemResource> out);
}
