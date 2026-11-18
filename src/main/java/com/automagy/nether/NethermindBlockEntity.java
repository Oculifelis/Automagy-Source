package com.automagy.nether;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.automagy.SavedEnum;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectContainer;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.content.entity.WispEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Nethermind: a crystalline brain set among runed obsidian that calls blocks and creatures across from the
 * Nether. The runes go in a ring of eight around it, one above and one below, and two in a row out from each side
 * (eighteen in all). A wand starts and stops it. Each conjuring draws ignis centivis through a vis relay (or from the
 * aura) and its rune's essentia cost from jars within eight blocks. Blocks appear in the 9x9x9 space two blocks below
 * the structure; with four or more block runes only their blocks come. Four or more creature runes (never mixed with
 * block runes) summon creatures through a Dimensional Lure 6 to 10 blocks below, in a ring of eight basic runes with
 * open air above it.
 */
public class NethermindBlockEntity extends BlockEntity {
    /** Blocks around a Dimensional Lure that calm the rift: obsidian totems on the ring's corners, nitor nearby. */
    public static final TagKey<Block> CALMING = TagKey.create(Registries.BLOCK, Automagy.id("rift_calming"));
    public static final List<BlockPos> RUNES = runePlaces();
    private static final int BASE_INTERVAL = 100;
    private static final int IGNIS_CENTIVIS = 10;
    private static final int JAR_RADIUS = 8;
    private static final int MAX_LURE_DEPTH = 10;
    private static final int CREATURE_CAP = 8;

    /**
     * Idle, or conjuring. The timer and the paid-for lure crossings belong to {@link State#ACTIVE} only: every change
     * of state goes through {@link #enter} and clears them, and they are saved only while active.
     */
    enum State {
        IDLE, ACTIVE
    }

    private State state = State.IDLE;
    private int timer;
    private int lurePaid;

    public NethermindBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.NETHERMIND_BE.get(), pos, state);
    }

    private static List<BlockPos> runePlaces() {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    out.add(new BlockPos(x, 0, z));
                }
            }
        }
        out.add(new BlockPos(0, 1, 0));
        out.add(new BlockPos(0, -1, 0));
        for (int d = 2; d <= 3; d++) {
            out.add(new BlockPos(d, 0, 0));
            out.add(new BlockPos(-d, 0, 0));
            out.add(new BlockPos(0, 0, d));
            out.add(new BlockPos(0, 0, -d));
        }
        return List.copyOf(out);
    }

    public boolean active() {
        return state == State.ACTIVE;
    }

    /** The one place the state changes: the timer and any lure credit start again from nothing. */
    private void enter(State next) {
        state = next;
        timer = 0;
        lurePaid = 0;
        setChanged();
    }

    /** What the structure is now: the runes, the lure if it needs one, or the reason it cannot run. */
    record Survey(List<Placed> runes, @Nullable String problem, @Nullable BlockPos lure, int celerity, boolean creatures) {
    }

    record Placed(BlockPos pos, NetherRune rune) {
    }

    Survey survey() {
        Level level = this.level;
        List<Placed> runes = new ArrayList<>();
        int blocks = 0, creatures = 0, celerity = 0, depth = 0;
        for (BlockPos off : RUNES) {
            BlockPos p = worldPosition.offset(off);
            NetherRune rune = NetherRune.of(level.getBlockState(p));
            if (rune == null) {
                return new Survey(List.of(), "structure", null, 0, false);
            }
            runes.add(new Placed(p, rune));
            switch (rune.kind()) {
                case BLOCK -> blocks++;
                case CREATURE -> {
                    creatures++;
                    depth = Math.max(depth, rune.lureDistance());
                }
                case CELERITY -> celerity++;
                default -> {
                }
            }
        }
        if (worldPosition.getY() - 12 < level.getMinY()) {
            return new Survey(runes, "yTooLow", null, celerity, false);
        }
        if (blocks > 0 && creatures > 0) {
            return new Survey(runes, "invalidMix", null, celerity, false);
        }
        if (creatures == 0) {
            final boolean onlyBlocks = blocks >= 4;
            runes.removeIf(r -> onlyBlocks ? r.rune().kind() != NetherRune.Kind.BLOCK
                    : r.rune().kind() != NetherRune.Kind.BLOCK && r.rune().kind() != NetherRune.Kind.BASIC);
            return new Survey(runes, null, null, celerity, false);
        }
        if (creatures < 4) {
            return new Survey(runes, "needMoreMobRunes", null, celerity, true);
        }
        runes.removeIf(r -> r.rune().kind() != NetherRune.Kind.CREATURE);
        // the lure: straight down through open air from the cap under the brain
        BlockPos p = worldPosition.below(2);
        for (int d = 2; d <= MAX_LURE_DEPTH; d++, p = p.below()) {
            BlockState s = level.getBlockState(p);
            if (s.is(AMRegistry.DIMENSIONAL_LURE.get())) {
                if (d < depth) {
                    return new Survey(runes, "missingLureExtraDistance", null, celerity, true);
                }
                if (!lureRing(level, p)) {
                    return new Survey(runes, "missingLure", null, celerity, true);
                }
                return new Survey(runes, null, p, celerity, true);
            }
            if (!s.isAir()) {
                break;
            }
        }
        return new Survey(runes, "missingLure", null, celerity, true);
    }

    private static boolean lureRing(Level level, BlockPos lure) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                NetherRune r = NetherRune.of(level.getBlockState(lure.offset(x, 0, z)));
                if (r == null || r.kind() != NetherRune.Kind.BASIC) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Starts it if the structure stands; the key of the chat message to show. */
    String start() {
        Survey s = survey();
        if (s.problem() != null) {
            return "automagia.nethermind." + s.problem();
        }
        enter(State.ACTIVE);
        return "automagia.nethermind.started";
    }

    void stop() {
        enter(State.IDLE);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NethermindBlockEntity be) {
        if (be.state != State.ACTIVE || !(level instanceof ServerLevel server)) {
            return;
        }
        if (++be.timer % 20 == 0 && server.getRandom().nextInt(4) == 0) {
            server.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.15f, 0.6f);
        }
        if (be.timer < BASE_INTERVAL - 6 * 12) {
            return;
        }
        Survey s = be.survey();
        if (s.problem() != null) {
            be.stop();
            return;
        }
        if (be.timer < BASE_INTERVAL - 6 * Math.min(12, s.celerity())) {
            return;
        }
        be.timer = 0;
        if (s.runes().isEmpty()) {
            return;
        }
        Placed pick = s.runes().get(server.getRandom().nextInt(s.runes().size()));
        NetherRune.Conjure what = pick.rune().pick(server.getRandom());
        if (what == null) {
            return;
        }
        BlockPos target = s.creatures() ? s.lure().above() : be.blockSpot(server, what);
        if (target == null || s.creatures() && be.crowded(server, s.lure(), what)) {
            return;
        }
        List<IAspectContainer> jars = Essentia.around(server, pos, JAR_RADIUS);
        AspectList cost = pick.rune().cost();
        boolean lureDue = s.creatures() && be.lurePaid <= 0;
        if (lureDue) {
            cost = cost.merge(pick.rune().lureCost());
        }
        if (!be.ignis(server, true) || !Essentia.pay(jars, cost)) {
            return;
        }
        be.ignis(server, false);
        if (lureDue) {
            be.lurePaid = Math.max(1, pick.rune().lureEvery());
        }
        if (s.creatures()) {
            be.lurePaid--;
            int spawns = be.unstable(server, s.lure(), s.runes()) ? 2 : 1;
            for (int i = 0; i < spawns; i++) {
                be.spawn(server, target, what);
            }
        } else {
            what.block().ifPresent(b -> server.setBlockAndUpdate(target, b.defaultBlockState()));
            server.levelEvent(2001, target, Block.getId(what.block().get().defaultBlockState()));
        }
        be.setChanged();
    }

    private boolean ignis(ServerLevel server, boolean simulate) {
        if (VisRelayHelper.drainCentivis(server, worldPosition, TTAspects.IGNIS, IGNIS_CENTIVIS, true) >= IGNIS_CENTIVIS) {
            if (!simulate) {
                VisRelayHelper.drainCentivis(server, worldPosition, TTAspects.IGNIS, IGNIS_CENTIVIS, false);
            }
            return true;
        }
        float vis = IGNIS_CENTIVIS / 100f;
        return AuraHelper.drainVis(server, worldPosition, vis, simulate) >= vis;
    }

    private @Nullable BlockPos blockSpot(ServerLevel server, NetherRune.Conjure what) {
        if (what.block().isEmpty()) {
            return null;
        }
        for (int tries = 0; tries < 8; tries++) {
            BlockPos p = worldPosition.offset(server.getRandom().nextInt(9) - 4, -3 - server.getRandom().nextInt(9), server.getRandom().nextInt(9) - 4);
            if (server.getBlockState(p).canBeReplaced()) {
                return p;
            }
        }
        return null;
    }

    private boolean crowded(ServerLevel server, BlockPos lure, NetherRune.Conjure what) {
        if (what.entity().isEmpty()) {
            return true;
        }
        EntityType<?> type = what.entity().get();
        return server.getEntities((Entity) null, new AABB(lure).inflate(16), e -> e.getType() == type).size() >= CREATURE_CAP;
    }

    /** Rolls the rift's instability, eased by calming blocks on the ring's corners and near the lure. */
    private boolean unstable(ServerLevel server, BlockPos lure, List<Placed> runes) {
        int chance = runes.stream().mapToInt(r -> r.rune().instability()).max().orElse(0);
        int calm = 0;
        for (BlockPos c : List.of(lure.offset(1, 1, 1), lure.offset(-1, 1, 1), lure.offset(1, 1, -1), lure.offset(-1, 1, -1))) {
            if (server.getBlockState(c).is(CALMING)) {
                calm++;
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(lure.offset(-3, -1, -3), lure.offset(3, 3, 3))) {
            if (server.getBlockState(p).is(CALMING) && !server.getBlockState(p).is(SirenLureBlockEntity.TOTEMS)) {
                calm++;
                break;
            }
        }
        chance = chance * (5 - calm) / 5;
        if (server.getRandom().nextInt(100) < chance) {
            AuraHelper.addFlux(server, lure, 2f);
            server.playSound(null, lure, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.4f, 1.6f);
            return true;
        }
        return false;
    }

    private void spawn(ServerLevel server, BlockPos at, NetherRune.Conjure what) {
        what.entity().ifPresent(type -> {
            Entity e = type.spawn(server, at, EntitySpawnReason.MOB_SUMMONED);
            if (e instanceof WispEntity wisp && what.aspect().isPresent()) {
                wisp.setAspect(what.aspect().get());
            }
            if (e != null) {
                server.levelEvent(2004, at, 0);
            }
        });
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        state = SavedEnum.read(in, "state", State.IDLE);
        boolean on = state == State.ACTIVE;
        timer = on ? in.getIntOr("timer", 0) : 0;
        lurePaid = on ? in.getIntOr("lure_paid", 0) : 0;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        SavedEnum.write(out, "state", state);
        if (state == State.ACTIVE) {
            out.putInt("timer", timer);
            out.putInt("lure_paid", lurePaid);
        }
    }
}
