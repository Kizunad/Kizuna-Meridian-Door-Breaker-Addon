package dev.kizuna.meridian.doorbreaker.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 实际动画资源与正式功法目录共用毫秒时间线，任一侧漂移都使回归失败。 */
public final class DoorBreakerAnimationTimingTest {
    private static final Path ASSETS = Path.of("../../Kizuna-Players-Animation/src/main/resources/assets",
            "kizuna_players_animation");
    private static final long MILLIS_PER_SECOND = 1000;
    private static final long TICK_MILLIS = 50;
    private static final long RAM_START_MILLIS = 1550;
    private static final long RAM_END_MILLIS = 2250;
    private static final List<Long> RAM_IMPULSES = List.of(RAM_START_MILLIS, 1800L, 2050L);
    private static final Map<String, List<Long>> HITS = Map.of(
            "knock", List.of(900L, 1250L, 1700L), "cleave", List.of(3200L), "ram", List.of(2400L));
    private static final String CLIP_PREFIX = "animation.kizuna_players_animation.player.";

    @Test
    public void bothHandsHitAndFinishAtTheAuthoritativeCatalogTimes() throws IOException {
        // 叩门逐击对照腕部接触关键帧，导出的 hit 通知仍只标末击，不参与伤害授权。
        var registry = DoorBreakerCatalog.registry(DoorBreakerCatalog.load());
        for (var entry : HITS.entrySet()) {
            var definition = registry.findLatest("meridian_door_breaker:" + entry.getKey()).orElseThrow();
            var strikes = definition.getEffectsList().stream().filter(TechniqueEffect::hasWeaponStrike).toList();
            var hitTimes = strikes.stream().map(TechniqueEffect::getAtMillis).toList();
            assertEquals(entry.getValue(), hitTimes);
            hitTimes.forEach(time -> assertEquals(0, time % TICK_MILLIS));
            var phases = definition.getPhases();
            long duration = phases.getWarmupMillis() + phases.getActiveMillis() + phases.getRecoveryMillis();
            for (String suffix : List.of("", "_off")) {
                JsonObject clip = clip("door_breaker_" + entry.getKey() + suffix);
                assertEquals(duration, millis(clip.get("animation_length").getAsDouble()));
                var hits = clip.getAsJsonObject("timeline").entrySet().stream()
                        .filter(event -> event.getValue().toString().contains("\"hit\""))
                        .map(event -> millis(Double.parseDouble(event.getKey()))).toList();
                assertEquals(List.of(hitTimes.get(hitTimes.size() - 1)), hits);
                if (entry.getKey().equals("knock")) {
                    assertKnockContacts(clip, suffix, hitTimes);
                }
            }
        }
    }

    private static void assertKnockContacts(JsonObject clip, String suffix, List<Long> hitTimes) {
        // 主副手腕部在三次斧刃接触处都有独立关键帧，保持既有动作曲线不变。
        String hand = "r_hand";
        if (suffix.equals("_off")) {
            hand = "l_hand";
        }
        var rotations = clip.getAsJsonObject("bones").getAsJsonObject(hand).getAsJsonObject("rotation");
        for (long time : hitTimes) {
            String seconds = Double.toString((double) time / MILLIS_PER_SECOND);
            assertTrue("缺少接触腕帧：" + seconds, rotations.has(seconds));
        }
    }

    @Test
    public void ramImpulsesStayWithinTheAnimatedSteps() throws IOException {
        // 冲量在迈步窗口内起效，后两次与落脚事件同刻；命中位于冲步之后。
        var definition = DoorBreakerCatalog.registry(DoorBreakerCatalog.load())
                .findLatest("meridian_door_breaker:ram").orElseThrow();
        var impulses = definition.getEffectsList().stream().filter(TechniqueEffect::hasImpulse)
                .map(TechniqueEffect::getAtMillis).toList();
        assertEquals(RAM_IMPULSES, impulses);
        JsonObject index = JsonParser.parseString(Files.readString(ASSETS.resolve("player_anim_index.json")))
                .getAsJsonObject();
        for (String suffix : List.of("", "_off")) {
            String id = "door_breaker_ram" + suffix;
            var metadata = index.getAsJsonArray("animations").asList().stream()
                    .map(value -> value.getAsJsonObject())
                    .filter(value -> value.get("id").getAsString().equals(id)).findFirst().orElseThrow();
            var window = metadata.getAsJsonArray("slide_window");
            assertEquals(RAM_START_MILLIS, millis(window.get(0).getAsDouble()));
            assertEquals(RAM_END_MILLIS, millis(window.get(1).getAsDouble()));
            var timeline = clip(id).getAsJsonObject("timeline");
            for (long impulse : impulses) {
                assertTrue(impulse >= RAM_START_MILLIS && impulse <= RAM_END_MILLIS);
                if (impulse > RAM_START_MILLIS) {
                    String time = Double.toString((double) impulse / MILLIS_PER_SECOND);
                    assertTrue(timeline.get(time).toString().contains("step_"));
                }
            }
        }
    }

    private static JsonObject clip(String id) throws IOException {
        // 使用当前 PA 工作区的真实交付资源，缺资源必须明确失败。
        Path path = ASSETS.resolve("animations/" + id + ".animation.json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonObject("animations").getAsJsonObject(CLIP_PREFIX + id);
    }

    private static long millis(double seconds) {
        // 时间精度以服务端毫秒为边界，资源中的小数秒只在这里转换。
        return Math.round(seconds * MILLIS_PER_SECOND);
    }
}
