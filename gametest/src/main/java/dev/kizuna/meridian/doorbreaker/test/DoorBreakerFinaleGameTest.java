package dev.kizuna.meridian.doorbreaker.test;

import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueProgress;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.List;

/** 万门一斩两刀：2.3 秒前荡轻击与 4.85 秒落地重劈，以及起手被僵直打断时的结算。 */
public final class DoorBreakerFinaleGameTest {
    private static final long SWING_MILLIS = 2300;
    private static final long CLEAVE_MILLIS = 4850;
    private static final long INTERRUPT_MILLIS = 2500;
    private static final double SWING_MULTIPLIER = 0.8;
    private static final double CLEAVE_MULTIPLIER = 2.5;
    private static final double RATIO_TOLERANCE = 0.001;
    private static final float NEAR_DISTANCE = 2;
    private static final float FAR_DISTANCE = 3;
    private static final double NO_LOSS = 0;
    private static final long SETTLE_TICKS = 3;
    private static final int TEST_TICKS = 120;

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = TEST_TICKS)
    public void swingHitsOnlyTheNearTargetBeforeTheLandingCleave(TestContext context) {
        // 前荡轻击只够到身前 2 格，落地重劈再砍身前目标并够到 3 格外的目标。
        FinaleRun run = FinaleRun.start(context, NEAR_DISTANCE, FAR_DISTANCE);
        long afterSwing = run.tickAt(SWING_MILLIS) + 1;
        long afterCleave = run.tickAt(CLEAVE_MILLIS) + SETTLE_TICKS;
        context.runAtEveryTick(() -> {
            long tick = run.advance();
            if (tick == afterSwing) {
                double ratio = run.swingDose() / run.cleaveDose();
                context.assertTrue(Math.abs(ratio - SWING_MULTIPLIER / CLEAVE_MULTIPLIER) < RATIO_TOLERANCE,
                        "两刀剂量比不是 0.8 : 2.5：" + ratio);
                run.assertLosses(context, run.swingDose(), NO_LOSS, "前荡轻击后");
            }
            if (tick == afterCleave) {
                context.assertTrue(run.strikeMillis().equals(List.of(SWING_MILLIS, CLEAVE_MILLIS)),
                        "出刀时刻与目录不符：" + run.strikeMillis());
                run.assertLosses(context, run.swingDose() + run.cleaveDose(), run.cleaveDose(), "落地重劈后");
                context.complete();
            }
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = TEST_TICKS)
    public void staggerInWarmupKeepsTheSwingAndDropsTheCleave(TestContext context) {
        // 起手 2.5 秒被僵直打断：已结算的轻击不撤销，此后不再出落地重劈。
        FinaleRun run = FinaleRun.start(context, NEAR_DISTANCE, FAR_DISTANCE);
        long interruptTick = run.tickAt(INTERRUPT_MILLIS);
        long afterCleave = run.tickAt(CLEAVE_MILLIS) + SETTLE_TICKS;
        context.runAtEveryTick(() -> {
            long tick = run.advance();
            if (tick == interruptTick) {
                CastPhase phase = run.interrupt();
                context.assertTrue(phase == CastPhase.INTERRUPTED, "起手 2.5 秒没有被僵直打断：" + phase);
                run.assertLosses(context, run.swingDose(), NO_LOSS, "打断时");
            }
            if (tick == afterCleave) {
                context.assertTrue(run.strikeMillis().equals(List.of(SWING_MILLIS)),
                        "打断后仍然出刀：" + run.strikeMillis());
                run.assertLosses(context, run.swingDose(), NO_LOSS, "原定重劈时刻之后");
                TechniqueProgress progress = run.progress();
                context.assertTrue(!progress.hasActiveCast(), "打断后仍保留施放记录");
                context.assertTrue(progress.getLastCast().getEnding() == CastPhase.INTERRUPTED,
                        "上一式没有记为打断：" + progress.getLastCast().getEnding());
                context.complete();
            }
        });
    }
}
