package dev.kizuna.meridian.doorbreaker.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * 施放占用不比动作长：前摇、出招、收势加起来不超过动作剪辑的长度，身体停下来以后也不再占着。
 *
 * <p>玩家反馈「动画都播完了，还显示正运功中」：收势比剪辑里身体停下来的时刻晚太多时，人站着不动却出不了下一招。
 * 剪辑从 PA 工作区读；身体停下来的算法和 handoff 里体检用的脚本是同一个。</p>
 */
public final class CastOccupancyTest {
    private static final Path ANIMATIONS = Path.of("../../Kizuna-Players-Animation/src/main/resources/assets",
            "kizuna_players_animation", "animations");
    private static final String CLIP_PREFIX = "animation.kizuna_players_animation.player.";
    private static final long MILLIS_PER_SECOND = 1000;
    /** 打坐、站桩这类一做半分钟的不查。 */
    private static final long LONGEST_MILLIS = 20_000;
    /** 身体停下来以后最多还占用这么久。 */
    private static final long IDLE_MARGIN_MILLIS = 300;
    private static final long TICK_MILLIS = 50;
    private static final long SAMPLE_MILLIS = 25;
    /** 任一骨骼离结束姿势超过这么多（旋转按度、位移按模型像素）算还在动。 */
    private static final Map<String, Double> STILL_WITHIN = Map.of("rotation", 5.0, "position", 0.5);

    @Test
    public void castEndsOnceTheBodyHasComeToRest() throws IOException {
        int checked = 0;
        for (TechniqueDefinition definition : DoorBreakerCatalog.registry(DoorBreakerCatalog.load()).definitions()) {
            var phases = definition.getPhases();
            long total = phases.getWarmupMillis() + phases.getActiveMillis() + phases.getRecoveryMillis();
            String clip = definition.getEffectsList().stream().filter(TechniqueEffect::hasPresentation)
                    .map(effect -> effect.getPresentation().getId()).filter(id -> !id.isBlank()).findFirst()
                    .map(id -> id.substring(id.indexOf(':') + 1)).orElse("");
            if (total > LONGEST_MILLIS || !Files.exists(file(clip))) {
                continue;
            }
            // 最后一次出效果之前不能收。
            long settled = definition.getEffectsList().stream().mapToLong(TechniqueEffect::getAtMillis).max().orElse(0) + TICK_MILLIS;
            for (String hand : List.of(clip, clip + "_off")) {
                if (!Files.exists(file(hand))) {
                    continue;
                }
                JsonObject animation = JsonParser.parseString(Files.readString(file(hand))).getAsJsonObject()
                        .getAsJsonObject("animations").getAsJsonObject(CLIP_PREFIX + hand);
                long length = Math.round(animation.get("animation_length").getAsDouble() * MILLIS_PER_SECOND);
                assertTrue(definition.getId() + " 施放 " + total + " 毫秒，比剪辑 " + hand + " 的 " + length + " 毫秒长", total <= length);
                settled = Math.max(settled, settledMillis(animation, length));
            }
            long limit = (settled + TICK_MILLIS - 1) / TICK_MILLIS * TICK_MILLIS + IDLE_MARGIN_MILLIS;
            assertTrue(definition.getId() + " 施放 " + total + " 毫秒，身体在 " + settled + " 毫秒就停下来了，多占了 " + (total - settled) + " 毫秒",
                    total <= limit);
            checked++;
        }
        assertTrue("一个带剪辑的招式都没查到", checked > 0);
    }

    private static Path file(String clip) {
        return ANIMATIONS.resolve(clip + ".animation.json");
    }

    /** 剪辑里身体停下来的时刻：这之后各骨骼离结束姿势都不超过 {@link #STILL_WITHIN}。 */
    private static long settledMillis(JsonObject animation, long length) {
        long movingUntil = 0;
        for (Map.Entry<String, JsonElement> bone : animation.getAsJsonObject("bones").entrySet()) {
            for (Map.Entry<String, Double> channel : STILL_WITHIN.entrySet()) {
                TreeMap<Long, double[]> keys = keys(bone.getValue().getAsJsonObject().get(channel.getKey()));
                if (keys.isEmpty()) {
                    continue;
                }
                double[] end = at(keys, length);
                for (long time = 0; time <= length; time += SAMPLE_MILLIS) {
                    if (distance(at(keys, time), end) > channel.getValue()) {
                        movingUntil = Math.max(movingUntil, time);
                    }
                }
            }
        }
        return Math.min(length, movingUntil + SAMPLE_MILLIS);
    }

    private static TreeMap<Long, double[]> keys(JsonElement channel) {
        // 一条通道写成「时刻（秒）→ 关键帧」；只有一帧时直接写那一帧。
        var keys = new TreeMap<Long, double[]>();
        if (channel == null) {
            return keys;
        }
        if (!channel.isJsonObject() || channel.getAsJsonObject().has("post")) {
            double[] only = vector(channel);
            if (only != null) {
                keys.put(0L, only);
            }
            return keys;
        }
        for (Map.Entry<String, JsonElement> frame : channel.getAsJsonObject().entrySet()) {
            double[] value = vector(frame.getValue());
            if (value != null) {
                keys.put(Math.round(Double.parseDouble(frame.getKey()) * MILLIS_PER_SECOND), value);
            }
        }
        return keys;
    }

    private static double[] vector(JsonElement frame) {
        // 关键帧写成 [x, y, z] 或 {"post": [x, y, z]}；带表达式的不算。
        JsonElement value = frame.isJsonObject() ? frame.getAsJsonObject().get("post") : frame;
        if (value == null || !value.isJsonArray()) {
            return null;
        }
        JsonArray parts = value.getAsJsonArray();
        double[] vector = new double[parts.size()];
        for (int index = 0; index < vector.length; index++) {
            JsonElement part = parts.get(index);
            if (!part.isJsonPrimitive() || !part.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            vector[index] = part.getAsDouble();
        }
        return vector;
    }

    private static double[] at(TreeMap<Long, double[]> keys, long time) {
        // 关键帧之间按直线算，够判断「停没停」。
        var before = keys.floorEntry(time);
        var after = keys.ceilingEntry(time);
        if (before == null) {
            return after.getValue();
        }
        if (after == null || after.getKey().equals(before.getKey())) {
            return before.getValue();
        }
        double ratio = (double) (time - before.getKey()) / (after.getKey() - before.getKey());
        double[] value = new double[before.getValue().length];
        for (int index = 0; index < value.length; index++) {
            value[index] = before.getValue()[index] + (after.getValue()[index] - before.getValue()[index]) * ratio;
        }
        return value;
    }

    private static double distance(double[] left, double[] right) {
        double largest = 0;
        for (int index = 0; index < left.length; index++) {
            largest = Math.max(largest, Math.abs(left[index] - right[index]));
        }
        return largest;
    }
}
