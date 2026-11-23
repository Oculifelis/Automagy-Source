package com.automagy.port;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;

/** Writes a block entity's saved tag with the same calls the newer game versions use. */
public record ValueOutput(CompoundTag tag, HolderLookup.Provider registries) {
    public static ValueOutput of(CompoundTag tag, HolderLookup.Provider registries) {
        return new ValueOutput(tag, registries);
    }

    public void putInt(String key, int v) {
        tag.putInt(key, v);
    }

    public void putLong(String key, long v) {
        tag.putLong(key, v);
    }

    public void putFloat(String key, float v) {
        tag.putFloat(key, v);
    }

    public void putDouble(String key, double v) {
        tag.putDouble(key, v);
    }

    public void putBoolean(String key, boolean v) {
        tag.putBoolean(key, v);
    }

    public void putString(String key, String v) {
        tag.putString(key, v);
    }

    public <T> void store(String key, Codec<T> codec, T value) {
        codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).result().ifPresent(t -> tag.put(key, t));
    }

    public <T> void storeNullable(String key, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            store(key, codec, value);
        }
    }

    public ValueOutput child(String key) {
        CompoundTag c = new CompoundTag();
        tag.put(key, c);
        return new ValueOutput(c, registries);
    }
}
