package com.automagy.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** The "Linked to x,y,z in dimension" tooltip line shared by the mercurial redcrystal and the crystalline eye. */
public final class LinkTooltip {
    private LinkTooltip() {
    }

    public static void add(@Nullable GlobalPos link, Consumer<Component> out) {
        if (link != null) {
            out.accept(Component.translatable("automagia.tip.link", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                    link.dimension().identifier().toString()).withStyle(ChatFormatting.GRAY));
        }
    }
}
