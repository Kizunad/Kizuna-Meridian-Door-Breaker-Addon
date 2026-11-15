package dev.kizuna.meridian.doorbreaker.clienttest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 连拍场地：世界原点地表上清出的一片草地。改世界都走服务端命令，和管理员在游戏里输入的一样。 */
final class Stage {
    private static final double EYE_HEIGHT = 1.62;
    private static final int SETTLE_TICKS = 10;
    private static final int CLEAR_RADIUS = 16;
    private static final int CLEAR_HEIGHT = 12;
    private static final boolean CLEAR_SENT_HISTORY = false;
    private final ClientGameTestContext context;
    private final TestSingleplayerContext world;
    private final BlockPos origin;

    private Stage(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin) {
        // 场地只由 prepare 建。
        this.context = context;
        this.world = world;
        this.origin = origin;
    }

    static Stage prepare(ClientGameTestContext context, TestSingleplayerContext world) {
        // 原点取 (0, 0) 处的地表；正午、晴天，界面藏起来，四周清成平地。
        world.getConnection().waitForChunksRender();
        int surface = world.getServer().computeOnServer(
                server -> server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
        Stage stage = new Stage(context, world, new BlockPos(0, surface, 0));
        stage.command("time set noon");
        stage.command("gamerule advance_time false");
        stage.command("weather clear");
        context.runOnClient(client -> {
            if (!client.gui.hud.isHidden()) {
                client.gui.hud.toggle();
            }
        });
        stage.clear();
        return stage;
    }

    private void clear() {
        // 清掉方块和玩家以外的实体，地面铺草。实体直接移除：/kill 的死亡烟会留在画面里。
        BlockPos from = origin.offset(-CLEAR_RADIUS, 0, -CLEAR_RADIUS);
        BlockPos to = origin.offset(CLEAR_RADIUS, CLEAR_HEIGHT, CLEAR_RADIUS);
        command("fill " + coordinates(from) + " " + coordinates(to) + " air");
        command("fill " + coordinates(from.below()) + " " + coordinates(to.atY(origin.getY() - 1)) + " grass_block");
        AABB area = new AABB(Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to.offset(1, 1, 1)));
        world.getServer().runOnServer(server -> server.overworld()
                .getEntities((Entity) null, area, entity -> !(entity instanceof Player))
                .forEach(Entity::discard));
    }

    Vec3 at(Vec3 offset) {
        // 场地坐标换成世界坐标。
        return Vec3.atLowerCornerOf(origin).add(offset);
    }

    void look(Vec3 feet, Vec3 target) {
        // 原版朝向：偏航 0 朝 +Z，俯仰为正时低头；tp 给的是脚底坐标，眼睛在其上方 1.62 格。
        Vec3 direction = target.subtract(feet.add(0, EYE_HEIGHT, 0));
        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double pitch = Math.toDegrees(Math.atan2(-direction.y, direction.horizontalDistance()));
        command(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", feet.x, feet.y, feet.z, yaw, pitch));
        settle();
    }

    <T> T computeOnServer(Function<MinecraftServer, T> action) {
        return world.getServer().computeOnServer(action::apply);
    }

    void onServer(Consumer<MinecraftServer> action) {
        world.getServer().runOnServer(action::accept);
    }

    void command(String command) {
        // 以服务端身份执行。
        world.getServer().runCommand(command);
    }

    void camera(CameraType type) {
        context.runOnClient(client -> client.options.setCameraType(type));
        settle();
    }

    void listen(Consumer<String> heard) {
        // 每播出一声（音乐和环境声除外）报一行：声音文件、音量、音高、离镜头几格。
        context.runOnClient(client -> client.getSoundManager().addListener((sound, events, range) -> {
            if (sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.AMBIENT) {
                return;
            }
            Vec3 ear = client.getSoundManager().getListenerTransform().position();
            Vec3 source = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            double distance = sound.isRelative() ? 0 : ear.distanceTo(source);
            heard.accept(String.format(Locale.ROOT, "%s\t%.3f\t%.3f\t%.2f",
                    sound.getSound().getPath(), sound.getVolume(), sound.getPitch(), distance));
        }));
    }

    void write(String name, String content) {
        // 写到本次运行目录里。
        try {
            Files.writeString(FabricLoader.getInstance().getGameDir().resolve(name), content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    void settle() {
        // 等服务端结果同步到客户端，再等区块网格重建。
        context.waitTicks(SETTLE_TICKS);
        world.getConnection().waitForChunksRender();
    }

    void pause(int ticks) {
        context.waitTicks(ticks);
    }

    void shoot(String name) {
        // 命令回显和右上角提示会挡画面，拍之前清掉；玩家发过的消息历史不清。
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(CLEAR_SENT_HISTORY);
            client.gui.toastManager().clear();
        });
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
