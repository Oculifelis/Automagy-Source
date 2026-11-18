package com.automagy;

import java.util.Locale;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;

/**
 * Block entity settings kept as enums are saved by their lower-case name, never by ordinal, so adding or reordering a
 * constant cannot remap a saved world. An unknown or missing name loads as the given fallback.
 */
public final class SavedEnum {
    private SavedEnum() {
    }

    public static String id(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    public static void write(ValueOutput out, String key, Enum<?> e) {
        out.putString(key, id(e));
    }

    public static <E extends Enum<E>> E read(ValueInput in, String key, E fallback) {
        String s = in.getStringOr(key, "");
        for (E e : fallback.getDeclaringClass().getEnumConstants()) {
            if (id(e).equals(s)) {
                return e;
            }
        }
        return fallback;
    }

    /** The next constant, wrapping round: what a settings click steps to. */
    public static <E extends Enum<E>> E next(E e) {
        E[] all = e.getDeclaringClass().getEnumConstants();
        return all[(e.ordinal() + 1) % all.length];
    }
}
