package com.automagy.client;

import com.mojang.logging.LogUtils;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.automagy.Automagy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;

/**
 * Development-only automated client check, inert unless the JVM runs with -Dautomagia.autotest (only the Gradle dev run
 * passes it with -Pautotest, see build.gradle and tools/run_client_test.sh). It makes a fresh creative superflat world
 * "amtest", sets amber blocks before a striped wall and both hourglasses beside them, turns the glasses with
 * redstone and screenshots the turn (every tick) and the sand draining over 15 s; device chests are shot close at the
 * lid seam with the old chest textures (resource pack amtest_before) and again without them; opens a filled golem's
 * workbench for one screen shot; logs "AMTEST"
 * lines and stops the client.
 */
@EventBusSubscriber(modid = Automagy.MODID, value = Dist.CLIENT)
public final class AMClientAutotest {
    private static final String MODE = System.getProperty("automagia.autotest");
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD = "amtest";
    private static final long TIMEOUT_NANOS = 4L * 60L * 1_000_000_000L;

    private static boolean titleSeen;
    private static boolean creating;
    private static long titleAt;
    private static int idleTicks;
    private static int worldTicks;
    private static boolean finished;
    private static int packWait;
    private static final String BEFORE_PACK = "file/amtest_before";

    private AMClientAutotest() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (MODE == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || finished) {
            return;
        }
        if (titleSeen && System.nanoTime() - titleAt > TIMEOUT_NANOS) {
            LOG.info("AMTEST timeout");
            finish(mc);
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            LOG.error("AMTEST step failed", t);
        }
    }

    private static void step(Minecraft mc) {
        if ("icons".equals(MODE)) {
            // page pictures only: no world, so nothing grabs the mouse; export once the title screen is up, then stop
            if (mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++idleTicks > 40) {
                mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
                exportIcons(mc);
                LOG.info("AMTEST done");
                finish(mc);
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && !(mc.screen instanceof TitleScreen) && mc.getOverlay() == null && ++idleTicks > 200) {
                LOG.info("AMTEST dismissing {}", mc.screen.getClass().getName());
                mc.setScreen(new TitleScreen());
                idleTicks = 0;
            }
            if (!creating && mc.screen instanceof TitleScreen title && mc.getOverlay() == null) {
                if (packWait == 0) {
                    setBeforePack(mc, true);
                }
                if (++packWait < 40) {
                    return;
                }
                creating = true;
                titleSeen = true;
                titleAt = System.nanoTime();
                LOG.info("AMTEST title");
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
                mc.options.framerateLimit().set(30);
                mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(4);
                deleteOldWorld(mc);
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
                LOG.info("AMTEST creating world {}", WORLD);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261101L, false, false), ra -> ra.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), title);
            }
            return;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        worldTicks++;
        if (worldTicks == 1) {
            LOG.info("AMTEST joined");
        } else if (worldTicks == 30) {
            build(mc);
            chests(mc);
        } else if (worldTicks >= 70 && worldTicks <= 85 && worldTicks % 5 == 0) {
            chestShot(mc, "before", (worldTicks - 70) / 5);
        } else if (worldTicks == 90) {
            setBeforePack(mc, false);
        } else if (worldTicks >= 200 && worldTicks <= 215 && worldTicks % 5 == 0) {
            chestShot(mc, "after", (worldTicks - 200) / 5);
        } else if (worldTicks == 220) {
            // hourglass row, seen close; both glasses run 15 s so the sand can be caught on its way down
            run(mc, java.util.List.of("data merge block 1 -60 -1 {seconds:15}", "data merge block 2 -60 -1 {seconds:15}",
                "tp @a 2.0 -61.12 1.3 180 0"));
        } else if (worldTicks == 250) {
            shot(mc, "scene");
            // a rising signal under each glass turns it (the real redstone path, HourglassBlock.neighborChanged)
            run(mc, java.util.List.of("setblock 1 -61 -1 minecraft:redstone_block", "setblock 2 -61 -1 minecraft:redstone_block"));
        } else if (worldTicks >= 251 && worldTicks <= 270) {
            shot(mc, String.format("turn_%02d", worldTicks - 250));
            if (worldTicks % 4 == 0) {
                logGlass(mc);
            }
        } else if (worldTicks == 268 || worldTicks == 350 || worldTicks == 450 || worldTicks == 540 || worldTicks == 552 || worldTicks == 570) {
            logGlass(mc);
        }
        if (worldTicks == 272) {
            shot(mc, "drain_000");
        } else if (worldTicks == 350) {
            shot(mc, "drain_033");
        } else if (worldTicks == 450) {
            shot(mc, "drain_066");
        } else if (worldTicks == 540) {
            shot(mc, "drain_096");
        } else if (worldTicks == 570) {
            shot(mc, "drain_100");
        } else if (worldTicks == 575) {
            workbench(mc);
        } else if (worldTicks == 590) {
            mc.options.hideGui = false;
        } else if (worldTicks == 600) {
            shot(mc, "workbench");
        } else if (worldTicks == 610) {
            LOG.info("AMTEST done");
            finish(mc);
        }
    }

    /** Device chests in a row at x 20, 22, 24 (eager, greedy, tenacious), for close looks at the lid seam. */
    private static void chests(Minecraft mc) {
        run(mc, java.util.List.of("gamemode spectator @a", "setblock 20 -60 -1 automagia:eager_chest",
            "setblock 22 -60 -1 automagia:greedy_chest", "setblock 24 -60 -1 automagia:tenacious_chest",
            "tp @a 22.5 -61.02 2.3 180 0"));
    }

    /** Shot n of a chest pass: 0 all three, then each chest close at the seam, slightly from the side. */
    private static void chestShot(Minecraft mc, String pass, int n) {
        String[] names = {"all", "eager", "greedy", "tenacious"};
        if (n == 0) {
            shot(mc, "chest_" + pass + "_all");
            run(mc, java.util.List.of("tp @a 20.85 -61.02 0.75 165 4"));
        } else {
            shot(mc, "chest_" + pass + "_" + names[n]);
            if (n < 3) {
                run(mc, java.util.List.of("tp @a " + (20.85 + 2 * n) + " -61.02 0.75 165 4"));
            } else {
                run(mc, java.util.List.of("tp @a 22.5 -61.02 2.3 180 0"));
            }
        }
    }

    /** The run script drops resourcepacks/amtest_before (the chest textures as they were) for the "before" shots. */
    private static void setBeforePack(Minecraft mc, boolean on) {
        var repo = mc.getResourcePackRepository();
        repo.reload();
        boolean changed = on ? repo.addPack(BEFORE_PACK) : repo.removePack(BEFORE_PACK);
        LOG.info("AMTEST before pack {} -> {} (selected {})", on ? "on" : "off", changed, repo.getSelectedIds());
        if (changed) {
            mc.reloadResourcePacks();
        }
    }

    /** A golem's workbench with items in storage and in the player's inventory, opened for one screen shot (one pattern
     *  window open, two shaded). */
    private static void workbench(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            var pos = new net.minecraft.core.BlockPos(5, -60, -1);
            var level = server.overworld();
            level.setBlockAndUpdate(pos, com.automagy.AMRegistry.GOLEM_WORKBENCH.get().defaultBlockState());
            var player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.CREATIVE);
            if (level.getBlockEntity(pos) instanceof com.automagy.golem.GolemWorkbenchBlockEntity be) {
                net.minecraft.world.item.Item[] items = {net.minecraft.world.item.Items.OAK_PLANKS, net.minecraft.world.item.Items.STICK,
                    net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.REDSTONE};
                for (int i = 0; i < 27; i += 2) {
                    be.storage().setItem(i, new net.minecraft.world.item.ItemStack(items[(i / 2) % 4], 1 + i));
                }
                for (int i = 0; i < 36; i += 3) {
                    player.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(items[(i / 3) % 4], 1 + i));
                }
                player.openMenu(be, buf -> buf.writeBlockPos(pos));
                LOG.info("AMTEST workbench opened, {} pattern(s) open", be.openPatterns());
            } else {
                LOG.error("AMTEST no workbench block entity");
            }
        });
    }

    private static void logGlass(Minecraft mc) {
        for (int x = 1; x <= 2; x++) {
            LOG.info("AMTEST glass {} {}", x, mc.level.getBlockState(new net.minecraft.core.BlockPos(x, -60, -1)));
        }
    }

    private static void deleteOldWorld(Minecraft mc) {
        LevelStorageSource source = mc.getLevelSource();
        if (!source.levelExists(WORLD)) {
            return;
        }
        try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
            access.deleteLevel();
            LOG.info("AMTEST deleted old world {}", WORLD);
        } catch (Exception e) {
            LOG.warn("AMTEST could not delete old world {}", WORLD, e);
        }
    }

    /**
     * Superflat: grass at y=-61, the player stands at y=-60. A black and white striped wall behind two columns of amber
     * (to see through them), and both hourglasses beside them on the same row. GUI hidden, camera close and level.
     */
    private static void build(Minecraft mc) {
        mc.options.hideGui = true;
        mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
        java.util.List<String> c = new java.util.ArrayList<>(java.util.List.of(
            "time set noon", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
            "gamerule spawn_mobs false", "fill -10 -60 -6 10 -52 8 minecraft:air", "clear @a"));
        for (int x = -4; x <= 4; x++) {
            c.add("fill " + x + " -60 -2 " + x + " -57 -2 minecraft:" + ((x & 1) == 0 ? "black" : "white") + "_wool");
        }
        c.addAll(java.util.List.of(
            "fill -2 -60 -1 -2 -59 -1 automagia:glowing_amber", "fill -1 -60 -1 -1 -59 -1 automagia:glowing_amber_bricks",
            "setblock 1 -60 -1 automagia:hourglass", "setblock 2 -60 -1 automagia:magic_hourglass",
            "tp @a 2.0 -61.12 1.3 180 0"));
        run(mc, c);
    }

    private static void view(Minecraft mc, String tp) {
        run(mc, java.util.List.of(tp));
    }

    private static void run(Minecraft mc, java.util.List<String> commands) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            LOG.error("AMTEST no integrated server");
            return;
        }
        server.execute(() -> {
            for (String c : commands) {
                LOG.info("AMTEST /{}", c);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
            }
        });
    }

    private static void shot(Minecraft mc, String view) {
        String name = "amtest_" + view + "_" + (System.currentTimeMillis() / 1000) + ".png";
        File file = new File(new File(mc.gameDirectory, "screenshots"), name);
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> LOG.info("AMTEST screenshot message: {}", msg.getString()));
        LOG.info("AMTEST shot {}", file.getAbsolutePath());
    }

    /** Every automagia item's inventory icon, drawn by the game's own item renderer into a 256 px target (16 px per GUI
     *  pixel) and saved to run/client/icons/&lt;id&gt;.png on a clear background: the CurseForge page pictures. */
    private static void exportIcons(Minecraft mc) {
        int size = 256;
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(size, size, true, Minecraft.ON_OSX);
        target.setClearColor(0, 0, 0, 0);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0, 16, 16, 0, 1000, 21000),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var view = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.translation(0, 0, -11000);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("icons");
        int n = 0;
        try {
            java.nio.file.Files.createDirectories(dir);
            for (var e : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
                net.minecraft.resources.ResourceLocation id = e.getKey().location();
                if (!id.getNamespace().equals(Automagy.MODID)) continue;
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                var g = new net.minecraft.client.gui.GuiGraphics(mc, mc.renderBuffers().bufferSource());
                g.renderItem(new net.minecraft.world.item.ItemStack(e.getValue()), 0, 0);
                g.flush();
                try (var img = new com.mojang.blaze3d.platform.NativeImage(size, size, false)) {
                    com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
                    img.downloadTexture(0, false);
                    img.flipY();
                    img.writeToFile(dir.resolve(id.getPath() + ".png"));
                }
                n++;
            }
        } catch (java.io.IOException e) {
            LOG.error("AMTEST icon export failed", e);
        } finally {
            view.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
        LOG.info("AMTEST icons {} saved to {}", n, dir);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        LOG.info("AMTEST stopping client");
        mc.stop();
    }
}
