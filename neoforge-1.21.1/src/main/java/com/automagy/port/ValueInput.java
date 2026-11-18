package com.automagy.port;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Reads a block entity's saved tag with the same calls the newer game versions use. */
public record ValueInput(CompoundTag tag, HolderLookup.Provider registries) {
    public static ValueInput of(CompoundTag tag, HolderLookup.Provider registries) {
        return new ValueInput(tag, registries);
    }

    public int getIntOr(String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    public long getLongOr(String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    public float getFloatOr(String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    public double getDoubleOr(String key, double fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
    }

    public boolean getBooleanOr(String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    public String getStringOr(String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    public <T> Optional<T> read(String key, Codec<T> codec) {
        Tag t = tag.get(key);
        if (t == null) {
            return Optional.empty();
        }
        return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), t).result();
    }

    public ValueInput childOrEmpty(String key) {
        return new ValueInput(tag.getCompound(key), registries);
    }
}
