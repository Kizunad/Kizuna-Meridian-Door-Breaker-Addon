package dev.kizuna.meridian.doorbreaker.clienttest;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.testing.RecordingConnection;
import dev.kizuna.meridian.technique.fabric.testing.TestPlayers;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

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
    /** -PclienttestScenes=feel：只拍打击反馈那几段（劈卫道士、打尸壳、打骷髅和铁傀儡），镜头架在侧面。 */
    private static final boolean FEEL_SCENES =
            "feel".equals(System.getProperty("meridian.doorbreaker.clienttest.scenes"));
    private static final List<String> PEDDLER_VOLUMES =
            List.of("meridian_peddler:road", "meridian_peddler:ambush", "meridian_peddler:journey");
    private static final Vec3 SIDE_CAMERA = new Vec3(4.6, 1.5, 1.6);
    private static final double CAMERA_EYE = 1.62;
    /** 镜头朝西看，每往前一格低这么多。 */
    private static final double CAMERA_DROP = -0.07;
    private static final String STRIKER_NAME = "Striker";
    private static final String SOUND_RECORD = "sounds.tsv";
    /** 连拍时听到的每一声：场景、第几张、声音文件、音量、音高、离镜头几格。 */
    private static final List<String> HEARD = new CopyOnWriteArrayList<>();
    /** 正在拍的场景和张数，没在拍时是空串。 */
    private static volatile String filming = "";
    /** 打击反馈那几段里出手的假玩家。 */
    private static ServerPlayer striker;
    private static final double WEAPON_REACH = 2.0;
    private static final double LIMB_REACH = 1.5;
    private static final int PUNCH_FILM_TICKS = 34;
    private static final double MOB_ATTACK_DISTANCE = 3;
    private static final int FIRST_PERSON_PITCH = 10;
    private static final int MOB_ATTACK_FILM_TICKS = 160;
    private static final List<Integer> PUNCH_TICKS = List.of(2, 16);
    /** -PclienttestScenes=flow：进架势真按键连出三式，再把真元清空看战斗槽变灰。界面开着拍。 */
    private static final boolean FLOW_SCENES =
            "flow".equals(System.getProperty("meridian.doorbreaker.clienttest.scenes"));
    /** 依次绑在 1、2、3 槽、连着按的三式。 */
    private static final List<String> FLOW_MOVES =
            List.of("meridian_peddler:flying_kick", PREFIX + "hook", "meridian_peddler:snap_kick");
    /** 绑在第 4 槽、一直没放过的一式：真元清空以后按它，被拒只能是因为真元。 */
    private static final String FLOW_SPARE_MOVE = PREFIX + "sweep";
    private static final int FLOW_GUI_SCALE = 2;
    private static final int DRAINED_FILM_TICKS = 15;
    private static final int REFUSED_FILM_TICKS = 25;
    private static final int REFILLED_FILM_TICKS = 15;
    /** 出招请求的回执，按到达先后。 */
    private static final List<CastOutcome> OBSERVED = new CopyOnWriteArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(WINDOW_WIDTH, WINDOW_HEIGHT);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            Stage stage = Stage.prepare(context, world);
            if (FEEL_SCENES) {
                feelScenes(stage);
                return;
            }
            if (FLOW_SCENES) {
                flowScenes(stage);
                return;
            }
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

    private static void feelScenes(Stage stage) {
        // 打击反馈的几段。原版在镜头不在本机玩家身上时不画本机玩家，所以出手的是另一个玩家：
        // 功法库测试夹具里的假玩家，走正式进服入口，在客户端眼里就是别的真玩家。本机玩家当旁观者，站在侧面看。
        stage.command("gamemode spectator @a");
        Vec3 camera = stage.at(SIDE_CAMERA);
        stage.look(camera.subtract(0, CAMERA_EYE, 0), camera.add(-1, CAMERA_DROP, 0));
        stage.camera(CameraType.FIRST_PERSON);
        stage.onServer(server -> {
            // Human 每 tick 检查必装的接收通道，缺了会暂停身体：假玩家照本机客户端声明过的那一套报。
            var channels = ServerPlayNetworking.getSendable((ServerPlayer) player(server));
            striker = TestPlayers.detached(server.overworld(), STRIKER_NAME);
            TestPlayers.logIn(striker, new RecordingConnection(channels));
            Vec3 feet = stage.at(PLAYER_FEET);
            striker.snapTo(feet.x, feet.y, feet.z, 0, 0);
            striker.setGameMode(GameType.SURVIVAL);
            striker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            // 真玩家每 tick 由自己的连接推一次（换了手里的东西要靠它广播），站没站在地上由客户端报；
            // 假玩家的连接不在服务端的连接表里，这两样在这儿替它做。
            ServerTickEvents.END_SERVER_TICK.register(ticking -> {
                striker.setOnGround(true /* onGround */);
                striker.doTick();
            });
        });
        stage.settle();
        VOLUMES.forEach(volume -> stage.command("meridian technique learn " + STRIKER_NAME + " " + volume));
        PEDDLER_VOLUMES.forEach(volume -> stage.command("meridian technique learn " + STRIKER_NAME + " " + volume));
        stage.command("meridian opening complete " + STRIKER_NAME + " all");
        stage.command("meridian body realm " + STRIKER_NAME + " " + TRUE_QI);
        stage.listen(DoorBreakerMotionClientTest::heard);
        target(stage, "minecraft:vindicator", WEAPON_REACH);
        filmCast(stage, "feel_cleave_vindicator", PREFIX + "cleave");
        target(stage, "minecraft:wither_skeleton", WEAPON_REACH);
        filmCast(stage, "feel_knock_skeleton", PREFIX + "knock");
        target(stage, "minecraft:iron_golem", WEAPON_REACH);
        filmCast(stage, "feel_cleave_golem", PREFIX + "cleave");
        stage.onServer(server -> striker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        target(stage, "minecraft:husk", LIMB_REACH);
        filmPunches(stage, "feel_punch_husk");
        target(stage, "minecraft:husk", LIMB_REACH);
        filmCast(stage, "feel_kick_husk", "meridian_peddler:roundhouse");
        filmMobAttack(stage, "feel_mob_attack");
        filmFirstPerson(stage, "feel_first_person");
        stage.write(SOUND_RECORD, String.join("", HEARD));
    }

    private static void flowScenes(Stage stage) {
        // 连贯：玩家进架势（越肩视角），三式绑在 1、2、3 槽，都是真按键、走战斗界面正式的出招请求。
        // 前一式的施放一结束，紧接着按下一式，必须当场起手；被拒（例如「正运功中」）就让本场失败。
        arm(stage);
        PEDDLER_VOLUMES.forEach(volume -> stage.command("meridian technique learn @a " + volume));
        stage.settle();
        for (int slot = 0; slot < FLOW_MOVES.size(); slot++) {
            stage.command("execute as @a run meridian combat slot set " + (slot + 1) + " " + FLOW_MOVES.get(slot));
        }
        stage.command("execute as @a run meridian combat slot set " + (FLOW_MOVES.size() + 1) + " " + FLOW_SPARE_MOVE);
        stage.showHud(FLOW_GUI_SCALE);
        stage.settle();
        stage.press(GLFW.GLFW_KEY_R);
        stage.settle();
        // 架势视角记在客户端配置里：是第一人称才按 V 换成越肩，运行目录里留着上次的配置也拍得一样。
        if (stage.firstPerson()) {
            stage.press(GLFW.GLFW_KEY_V);
            stage.settle();
        }
        if (stage.firstPerson()) {
            throw new AssertionError("进架势以后换不到越肩视角");
        }
        stage.onServer(server -> TechniqueFabric.service().addCastListener(cast -> OBSERVED.add(cast.outcome())));
        refill(stage);
        stage.command("tick freeze");
        int filmed = 0;
        for (int slot = 0; slot < FLOW_MOVES.size(); slot++) {
            String technique = FLOW_MOVES.get(slot);
            pressSlot(stage, slot);
            if (OBSERVED.size() != slot + 1
                    || OBSERVED.getLast().getFailure() != TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED) {
                throw new AssertionError("第 " + filmed + " 张以后按 " + technique + " 没有当场起手：" + OBSERVED);
            }
            int castTicks = ticks(castMillis(stage.computeOnServer(server ->
                    TechniqueFabric.service().registry().findLatest(technique).orElseThrow().getPhases())));
            System.out.println("DOOR_MOTION 连招 " + technique + " 第 " + (filmed + 1) + " 张起手，占 " + castTicks + " tick");
            film(stage, "flow", filmed, castTicks);
            filmed += castTicks;
        }
        // 真元清空：槽里的图标变灰、右下角出真元角标；这时按没在冷却的第 4 槽，动作栏说原因；补满以后亮回来。
        stage.command("meridian qi set @a 0 all lumen " + QI_DENSITY);
        film(stage, "flow", filmed, DRAINED_FILM_TICKS);
        filmed += DRAINED_FILM_TICKS;
        pressSlot(stage, FLOW_MOVES.size());
        if (OBSERVED.getLast().getFailure() == TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED) {
            throw new AssertionError("真元清空以后 " + FLOW_SPARE_MOVE + " 还放得出来");
        }
        System.out.println("DOOR_MOTION 真元清空后按 " + FLOW_SPARE_MOVE + " 被拒：" + OBSERVED.getLast().getFailure()
                + " " + OBSERVED.getLast().getReason());
        film(stage, "flow", filmed, REFUSED_FILM_TICKS);
        filmed += REFUSED_FILM_TICKS;
        stage.command("meridian qi set @a " + PLAYER_QI + " all lumen " + QI_DENSITY);
        film(stage, "flow", filmed, REFILLED_FILM_TICKS);
        stage.command("tick unfreeze");
    }

    private static void pressSlot(Stage stage, int slot) {
        // 按一下原版快捷栏的数字键：架势里它就是出招键。等两 tick 让请求到服务端、回执回来。
        int before = OBSERVED.size();
        stage.press(GLFW.GLFW_KEY_1 + slot);
        stage.pause(STEP_SETTLE_TICKS);
        if (OBSERVED.size() != before + 1) {
            throw new AssertionError("按第 " + (slot + 1) + " 槽以后服务端没有收到出招请求");
        }
    }

    private static void heard(String sound) {
        // 冻结着连拍录不了音：把每一声实际播出的声音记到它所在的那张画面上，事后照这份记录把音轨摆回去。
        if (!filming.isEmpty()) {
            HEARD.add(filming + '\t' + sound + '\n');
        }
    }

    private static void target(Stage stage, String type, double distance) {
        // 换一个目标：清掉上一个，在玩家正南放一只关了 AI、面朝玩家的。
        stage.onServer(server -> server.overworld().getEntitiesOfClass(Mob.class,
                MOB_BOX.inflate(8).move(stage.at(PLAYER_FEET))).forEach(Entity::discard));
        Vec3 feet = stage.at(PLAYER_FEET.add(0, 0, distance));
        stage.command(String.format(Locale.ROOT,
                "summon %s %.2f %.2f %.2f {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}",
                type, feet.x, feet.y, feet.z));
        stage.settle();
    }

    private static void filmFirstPerson(Stage stage, String scene) {
        // 第一人称自己劈一下：假玩家退成旁观者，本机玩家回到生存、学好功法、拿斧子站到原点，镜头就在自己眼睛里。
        String self = "@a[name=!" + STRIKER_NAME + "]";
        stage.onServer(server -> striker.setGameMode(GameType.SPECTATOR));
        stage.command("gamemode survival " + self);
        VOLUMES.forEach(volume -> stage.command("meridian technique learn " + self + " " + volume));
        stage.command("meridian opening complete " + self + " all");
        stage.command("meridian body realm " + self + " " + TRUE_QI);
        Vec3 feet = stage.at(PLAYER_FEET);
        stage.command(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f 0 %d", self, feet.x, feet.y, feet.z,
                FIRST_PERSON_PITCH));
        stage.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.IRON_AXE)));
        target(stage, "minecraft:vindicator", WEAPON_REACH);
        filmCast(stage, scene, PREFIX + "cleave", DoorBreakerMotionClientTest::player);
    }

    private static void filmCast(Stage stage, String scene, String technique) {
        filmCast(stage, scene, technique, server -> striker);
    }

    private static void filmCast(Stage stage, String scene, String technique,
                                 Function<MinecraftServer, LivingEntity> caster) {
        // 养好身体、冻结、起手，从起手拍到收势以后。
        refill(stage);
        int castTicks = ticks(castMillis(stage.computeOnServer(server ->
                TechniqueFabric.service().registry().findLatest(technique).orElseThrow().getPhases())));
        stage.command("tick freeze");
        stage.onServer(server -> castId(caster.apply(server), technique, CastEntry.AUTO));
        film(stage, scene, 0, castTicks + AFTER_MOVE_TICKS);
        stage.command("tick unfreeze");
        stage.pause(BETWEEN_MOVES_TICKS);
    }

    private static void filmPunches(Stage stage, String scene) {
        // 空手用原版攻击打两拳：看空手的风痕和溅血。
        stage.command("tick freeze");
        int filmed = 0;
        for (int punch : PUNCH_TICKS) {
            film(stage, scene, filmed, punch - filmed);
            filmed = punch;
            stage.onServer(server -> {
                var mobs = server.overworld().getEntitiesOfClass(Mob.class,
                        MOB_BOX.inflate(4).move(striker.position()));
                striker.attack(mobs.getFirst());
                striker.swing(InteractionHand.MAIN_HAND, true /* updateSelf */);
                striker.resetAttackStrengthTicker();
            });
        }
        film(stage, scene, filmed, PUNCH_FILM_TICKS - filmed);
        stage.command("tick unfreeze");
        stage.pause(BETWEEN_MOVES_TICKS);
    }

    private static void filmMobAttack(Stage stage, String scene) {
        // 生物挥兵器也有风痕：生一只真炁卫道士，留着 AI，让它自己对出手的玩家放招。先冻结再生，免得没开拍它就动手。
        stage.onServer(server -> server.overworld().getEntitiesOfClass(Mob.class,
                MOB_BOX.inflate(8).move(stage.at(PLAYER_FEET))).forEach(Entity::discard));
        refill(stage);
        stage.command("tick freeze");
        Vec3 feet = stage.at(PLAYER_FEET.add(0, 0, MOB_ATTACK_DISTANCE));
        stage.command(String.format(Locale.ROOT, "meridian humanoid spawn minecraft:vindicator %s %.2f %.2f %.2f",
                TRUE_QI, feet.x, feet.y, feet.z));
        stage.settle();
        film(stage, scene, 0, MOB_ATTACK_FILM_TICKS);
        stage.command("tick unfreeze");
        stage.pause(BETWEEN_MOVES_TICKS);
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
            filming = String.format(Locale.ROOT, "%s\t%d", scene, frame);
            stage.command("tick step 1");
            stage.pause(STEP_SETTLE_TICKS);
            stage.shoot(String.format(Locale.ROOT, "film_%s_%03d", scene, frame));
        }
        filming = "";
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
        castId(caster, PREFIX + move, entry);
    }

    private static void castId(LivingEntity caster, String technique, CastEntry entry) {
        // 精确引用取生产注册表，身体从原版伤害桥读，与 GameTest 相同；承接要在请求里写明是承接。起手失败直接让本场失败。
        String move = technique.substring(technique.indexOf(':') + 1);
        var definition = TechniqueFabric.service().registry().findLatest(technique).orElseThrow();
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
