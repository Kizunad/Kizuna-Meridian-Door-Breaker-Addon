package dev.kizuna.meridian.doorbreaker.clienttest;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastEntry;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhases;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.server.TechniqueService;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 破门斩动作连拍，给人看：玩家手持铁斧，经功法库公开入口施放，第三人称正面每隔几 tick 拍一张。
 * 拍三样：后五式各自的整段动作；一次承接（劈门接万门一斩）前后逐 tick 的过渡；玩家和一只卫道士各挨一次受击僵直。
 * 截图写到运行目录 {@code screenshots/}，文件名是「场景_序号_t起手后第几 tick」。这里不判画面对错。
 */
public final class DoorBreakerMotionClientTest implements FabricClientGameTest {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 960;
    private static final String PREFIX = "meridian_door_breaker:";
    private static final List<String> VOLUMES = List.of(PREFIX + "upper", PREFIX + "middle", PREFIX + "lower");
    /** 能单独起手的四式；万门一斩只能承接，另拍。 */
    private static final List<String> SOLO_MOVES = List.of("hook", "sweep", "close", "chain");
    private static final String TRUE_QI = "true_qi";
    private static final int PLAYER_QI = 300;
    private static final int QI_DENSITY = 1;
    private static final long MILLIS_PER_TICK = 50;
    private static final int FRAMES_PER_MOVE = 10;
    /** 逐 tick 连拍（-PclienttestEveryTick）：每个场景从头到尾一 tick 一张，拿去合成 20 帧每秒的视频。 */
    private static final boolean EVERY_TICK = Boolean.getBoolean("meridian.doorbreaker.clienttest.everyTick");
    /** 逐 tick 连拍时，一式放完以后多拍这么多 tick，把淡回站立也拍进去。 */
    private static final int AFTER_MOVE_TICKS = 12;
    private static final int STAGGER_FILM_TICKS = 34;
    /** 逐 tick 连拍时每走一格等这么多客户端 tick，让这一格的包都到了再拍。 */
    private static final int STEP_SETTLE_TICKS = 2;
    /** 一式收势结束后再等这么久，冷却和 PA 的停播都过了再起下一式。 */
    private static final int BETWEEN_MOVES_TICKS = 30;
    /** 承接窗口从前式收势的一半开；多等两 tick 再按，稳在窗口里。 */
    private static final double FOLLOW_UP_RECOVERY_RATIO = 0.5;
    private static final int FOLLOW_UP_MARGIN_TICKS = 2;
    private static final int BEFORE_FOLLOW_UP_FRAMES = 3;
    private static final int AFTER_FOLLOW_UP_FRAMES = 8;
    private static final int FRAME_STEP_TICKS = 2;
    private static final long STAGGER_MILLIS = 1000;
    private static final double STAGGER_SPEED = 0.4;
    /** 僵直开始后第几 tick 各拍一张：一顿、松下、愣住；第 8 到 14 tick 逐 tick 拍，看结束时是不是淡回去的。 */
    private static final List<Integer> STAGGER_SHOT_TICKS = List.of(1, 2, 3, 4, 6, 8, 9, 10, 11, 12, 13, 14, 16, 20, 26);
    private static final Vec3 PLAYER_FEET = new Vec3(0.5, 0, 0.5);
    private static final Vec3 SOUTH = new Vec3(0.5, 1.62, 8.5);
    private static final Vec3 MOB_FEET = new Vec3(0.5, 0, 6.5);
    private static final Vec3 MOB_VIEWER_FEET = new Vec3(3.5, 0, 3.0);
    private static final Vec3 MOB_VIEW = new Vec3(0.5, 1.2, 6.5);
    private static final AABB MOB_BOX = new AABB(-1, -1, -1, 1, 2, 1);
    private static final float FACING_NORTH = 180;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(WINDOW_WIDTH, WINDOW_HEIGHT);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            Stage stage = Stage.prepare(context, world);
            arm(stage);
            for (String move : SOLO_MOVES) {
                solo(stage, move);
            }
            cleaveIntoFinale(stage);
            playerStagger(stage);
            mobStagger(stage);
        }
    }

    private static void arm(Stage stage) {
        // 生存模式、学全三卷、开脉、升真炁、补满真元，主手一把铁斧，站在原点朝南；镜头转到身前。
        stage.command("gamemode survival @a");
        // 每次施放怎么结束的记进日志：哪一式没放完、为什么，看截图时对得上。
        stage.onServer(server -> TechniqueFabric.service().addCastEndListener(
                ended -> System.out.println("DOOR_MOTION 施放结束 " + ended)));
        stage.onServer(server -> TechniqueFabric.service().addStaggerListener(changed -> System.out.println(
                "DOOR_MOTION 僵直 " + changed.kind() + " 剩余 " + changed.remainingMillis() + " 毫秒，世界 tick "
                        + server.overworld().getGameTime())));
        VOLUMES.forEach(volume -> stage.command("meridian technique learn @a " + volume));
        stage.command("meridian opening complete @a all");
        stage.command("meridian body realm @a " + TRUE_QI);
        refill(stage);
        stage.look(stage.at(PLAYER_FEET), stage.at(SOUTH));
        stage.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE)));
        stage.camera(CameraType.THIRD_PERSON_FRONT);
    }

    private static void refill(Stage stage) {
        // 每式起手前把身体养好、疲劳清掉，再把全身内腔覆盖成密度 1 的真元：连着放几式不歇，身体会累到后面的式放到一半失败。
        stage.command("meridian body heal @a all");
        stage.command("meridian qi set @a " + PLAYER_QI + " all lumen " + QI_DENSITY);
        stage.settle();
    }

    private static void solo(Stage stage, String move) {
        // 一式从起手到收势，等间隔拍若干张。
        refill(stage);
        int castTicks = ticks(castMillis(phases(stage, move)));
        if (EVERY_TICK) {
            stage.command("tick freeze");
            stage.onServer(server -> cast(player(server), move, CastEntry.AUTO));
            film(stage, move, 0, castTicks + AFTER_MOVE_TICKS);
            stage.command("tick unfreeze");
            stage.pause(BETWEEN_MOVES_TICKS);
            return;
        }
        stage.onServer(server -> cast(player(server), move, CastEntry.AUTO));
        int shot = 0;
        for (int frame = 0; frame < FRAMES_PER_MOVE; frame++) {
            int target = castTicks * (2 * frame + 1) / (2 * FRAMES_PER_MOVE);
            stage.pause(target - shot);
            shot = target;
            stage.shoot(name(move, frame, shot));
        }
        stage.pause(castTicks - shot + BETWEEN_MOVES_TICKS);
    }

    private static void cleaveIntoFinale(Stage stage) {
        // 劈门打到收势过半时接万门一斩。接之前隔两 tick 拍三张，接上以后逐 tick 拍八张看过渡，之后等间隔拍完万门一斩。
        refill(stage);
        CastPhases cleave = phases(stage, "cleave");
        int untilWindow = ticks(cleave.getWarmupMillis() + cleave.getActiveMillis()
                + (long) (cleave.getRecoveryMillis() * FOLLOW_UP_RECOVERY_RATIO)) + FOLLOW_UP_MARGIN_TICKS;
        if (EVERY_TICK) {
            // 劈门从起手拍到接招那一刻，接上万门一斩后继续拍到它放完，是一段连着的镜头。
            stage.command("tick freeze");
            stage.onServer(server -> cast(player(server), "cleave", CastEntry.AUTO));
            film(stage, "combo", 0, untilWindow);
            stage.onServer(server -> cast(player(server), "finale", CastEntry.FOLLOW_UP));
            film(stage, "combo", untilWindow, ticks(castMillis(phases(stage, "finale"))) + AFTER_MOVE_TICKS);
            stage.command("tick unfreeze");
            stage.pause(BETWEEN_MOVES_TICKS);
            return;
        }
        stage.onServer(server -> cast(player(server), "cleave", CastEntry.AUTO));
        stage.pause(untilWindow - BEFORE_FOLLOW_UP_FRAMES * FRAME_STEP_TICKS);
        for (int frame = 0; frame < BEFORE_FOLLOW_UP_FRAMES; frame++) {
            stage.shoot(name("follow_up_a_cleave", frame, untilWindow - (BEFORE_FOLLOW_UP_FRAMES - frame) * FRAME_STEP_TICKS));
            stage.pause(FRAME_STEP_TICKS);
        }
        stage.onServer(server -> cast(player(server), "finale", CastEntry.FOLLOW_UP));
        for (int frame = 0; frame < AFTER_FOLLOW_UP_FRAMES; frame++) {
            stage.pause(1);
            stage.shoot(name("follow_up_b_finale", frame, frame + 1));
        }
        int finaleTicks = ticks(castMillis(phases(stage, "finale")));
        int shot = AFTER_FOLLOW_UP_FRAMES;
        for (int frame = 0; frame < FRAMES_PER_MOVE; frame++) {
            int target = AFTER_FOLLOW_UP_FRAMES + (finaleTicks - AFTER_FOLLOW_UP_FRAMES) * (2 * frame + 1) / (2 * FRAMES_PER_MOVE);
            stage.pause(target - shot);
            shot = target;
            stage.shoot(name("finale", frame, shot));
        }
        stage.pause(finaleTicks - shot + BETWEEN_MOVES_TICKS);
    }

    private static void playerStagger(Stage stage) {
        // 玩家站着挨一次一秒的受击僵直。
        if (EVERY_TICK) {
            stage.command("tick freeze");
        }
        stage.onServer(server -> stagger(player(server)));
        staggerShots(stage, "stagger_player");
    }

    private static void mobStagger(Stage stage) {
        // 生一只真炁卫道士，关 AI、面朝北；旁观者在它侧前方看它挨同样一次僵直。
        stage.command("gamemode spectator @a");
        stage.camera(CameraType.FIRST_PERSON);
        Vec3 feet = stage.at(MOB_FEET);
        stage.command(String.format(Locale.ROOT, "meridian humanoid spawn minecraft:vindicator %s %.2f %.2f %.2f",
                TRUE_QI, feet.x, feet.y, feet.z));
        stage.settle();
        stage.onServer(server -> {
            Vindicator mob = vindicator(server, feet);
            mob.setNoAi(true /* noAi */);
            mob.setYRot(FACING_NORTH);
            mob.setYHeadRot(FACING_NORTH);
            mob.setYBodyRot(FACING_NORTH);
        });
        stage.look(stage.at(MOB_VIEWER_FEET), stage.at(MOB_VIEW));
        if (EVERY_TICK) {
            stage.command("tick freeze");
        }
        stage.onServer(server -> stagger(vindicator(server, feet)));
        staggerShots(stage, "stagger_vindicator");
    }

    private static void film(Stage stage, String scene, int firstFrame, int ticks) {
        // 游戏已经冻结：每次只放行一个 tick，等它的包到了再拍，所以一张正好是一个游戏 tick，截图多慢都不会错位。
        for (int frame = firstFrame + 1; frame <= firstFrame + ticks; frame++) {
            stage.command("tick step 1");
            stage.pause(STEP_SETTLE_TICKS);
            stage.shoot(String.format(Locale.ROOT, "film_%s_%03d", scene, frame));
        }
    }

    private static void staggerShots(Stage stage, String scene) {
        if (EVERY_TICK) {
            film(stage, scene, 0, STAGGER_FILM_TICKS);
            stage.command("tick unfreeze");
            stage.pause(BETWEEN_MOVES_TICKS);
            return;
        }
        int shot = 0;
        for (int frame = 0; frame < STAGGER_SHOT_TICKS.size(); frame++) {
            int target = STAGGER_SHOT_TICKS.get(frame);
            stage.pause(target - shot);
            shot = target;
            stage.shoot(name(scene, frame, shot));
        }
        stage.pause(BETWEEN_MOVES_TICKS);
    }

    private static String name(String scene, int frame, int tick) {
        return String.format(Locale.ROOT, "%s_%02d_t%03d", scene, frame, tick);
    }

    private static CastPhases phases(Stage stage, String move) {
        return stage.computeOnServer(server ->
                TechniqueFabric.service().registry().findLatest(PREFIX + move).orElseThrow().getPhases());
    }

    private static long castMillis(CastPhases phases) {
        return phases.getWarmupMillis() + phases.getActiveMillis() + phases.getRecoveryMillis();
    }

    private static int ticks(long millis) {
        return Math.toIntExact(millis / MILLIS_PER_TICK);
    }

    private static void cast(LivingEntity caster, String move, CastEntry entry) {
        // 精确引用取生产注册表，身体从原版伤害桥读，与 GameTest 相同；承接要在请求里写明是承接。起手失败直接让本场失败。
        var definition = TechniqueFabric.service().registry().findLatest(PREFIX + move).orElseThrow();
        var reference = TechniqueReference.newBuilder()
                .setId(definition.getId())
                .setVersion(definition.getVersion())
                .build();
        var request = CastRequest.newBuilder()
                .setCastId("clienttest_" + move + "_" + UUID.randomUUID())
                .setBodyId(VanillaDamageFabric.inspectBody(caster).orElseThrow().getId())
                .setTechnique(reference)
                .setEntry(entry)
                .build();
        CastOutcome outcome = TechniqueFabric.service().cast(caster.getUUID(), request);
        if (outcome.getFailure() != TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED) {
            throw new AssertionError(move + " 起手失败：" + outcome.getReason());
        }
    }

    private static void stagger(LivingEntity target) {
        TechniqueFabric.service().stagger(target.getUUID(), new TechniqueService.Stagger(STAGGER_MILLIS, STAGGER_SPEED));
    }

    private static LivingEntity player(MinecraftServer server) {
        // 单人世界里只有测试客户端这一个玩家。
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static Vindicator vindicator(MinecraftServer server, Vec3 feet) {
        // 生成命令在原地生出的那一只；没有或多出来都说明场地不对。
        List<Vindicator> mobs = server.overworld().getEntitiesOfClass(Vindicator.class, MOB_BOX.move(feet));
        if (mobs.size() != 1) {
            throw new AssertionError("卫道士生成位置附近应只有一只：" + mobs);
        }
        return mobs.getFirst();
    }
}
