package com.automagy.golem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/**
 * What an Oculo Anima watches for: an entity type, one named player, or any player (component
 * {@code automagia:entity_watch}).
 */
public record EntityWatch(Optional<Identifier> type, Optional<UUID> player, String name) {
    public static final Codec<EntityWatch> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("type").forGetter(EntityWatch::type),
            UUIDUtil.CODEC.optionalFieldOf("player").forGetter(EntityWatch::player),
            Codec.STRING.optionalFieldOf("name", "").forGetter(EntityWatch::name)).apply(i, EntityWatch::new));
    public static final StreamCodec<ByteBuf, EntityWatch> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), EntityWatch::type,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), EntityWatch::player,
            ByteBufCodecs.STRING_UTF8, EntityWatch::name, EntityWatch::new);

    public static EntityWatch of(Entity entity) {
        if (entity instanceof Player p) {
            return new EntityWatch(Optional.empty(), Optional.of(p.getUUID()), p.getGameProfile().name());
        }
        return new EntityWatch(Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())), Optional.empty(), "");
    }

    public static EntityWatch anyPlayer() {
        return new EntityWatch(Optional.empty(), Optional.empty(), "");
    }

    public boolean matches(Entity entity) {
        if (type.isPresent()) {
            return !(entity instanceof Player) && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(type.get());
        }
        return entity instanceof Player p && player.map(id -> id.equals(p.getUUID())).orElse(true);
    }

    public Component describe() {
        if (type.isPresent()) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(type.get()).map(EntityType::getDescription)
                    .orElse(Component.literal(type.get().toString()));
        }
        return player.isPresent() ? Component.translatable("automagia.entity_watch.player", name)
                : Component.translatable("automagia.entity_watch.players");
    }
}
