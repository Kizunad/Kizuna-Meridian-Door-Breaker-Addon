package dev.kizuna.meridian.doorbreaker.test;

import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.FailureCode;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.world.TechniqueWorldEffects;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ActiveCast;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastEntry;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhases;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.EffectIntent;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.HeldItemRules;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueBinding;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueProgress;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.WeaponStrike;
import dev.kizuna.meridian.technique.server.TechniqueCasting;
import dev.kizuna.meridian.technique.server.TechniqueErrors;
import dev.kizuna.meridian.technique.server.TechniqueHost;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;
import dev.kizuna.meridian.technique.server.TechniqueState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 万门一斩承接施放的内存人体宿主：按 tick 推进身体时钟，经功法库真实的施放状态机和调度器取得意图，
 * 再把打击交给真实世界执行器。冲量、表现和移动锁不在本测范围内执行。
 */
final class FinaleRun implements TechniqueHost {
    private static final String FINALE = "meridian_door_breaker:finale";
    private static final long TICK_MILLIS = 50;
    private static final String BOOK = "meridian_door_breaker:door_breaker";
    private static final String CAST_ID = "finale_gametest";
    private static final String ACTION_INSTANCE_ID = "technique."
            + HexFormat.of().formatHex(CAST_ID.getBytes(StandardCharsets.US_ASCII));
    private static final String DAMAGE_NAMESPACE = "meridian_door_breaker";
    private static final long CAST_START_MILLIS = 1000;
    private static final float ACTOR_X = 2.5f;
    private static final float GROUND_Y = 2;
    private static final float ACTOR_Z = 1.5f;
    private static final float FACING_SOUTH = 0;
    private static final double TARGET_HEALTH = 100;
    private static final double FULL_KNOCKBACK_RESISTANCE = 1;
    private static final double DOSE_TOLERANCE = 0.001;

    private final TechniqueCasting casting;
    private final TechniqueWorldEffects world;
    private final List<WeaponStrike> strikes;
    private final HeldItemRules rules;
    private final LivingEntity actor;
    private final LivingEntity near;
    private final LivingEntity far;
    private final long clipMillis;
    private final List<Long> strikeMillis = new ArrayList<>();
    private BodyMessages.BodySnapshot body;
    private long previousMillis;
    private long ticks;

    private FinaleRun(TechniqueRegistry registry, TechniqueDefinition definition, LivingEntity actor,
                      LivingEntity near, LivingEntity far) {
        // 承接剪辑取目录声明的起播点；只允许两头牛成为打击目标，避免并行测试的实体混入。
        this.casting = new TechniqueCasting(registry);
        this.actor = actor;
        this.near = near;
        this.far = far;
        this.clipMillis = definition.getFollowUp().getTransitions(0).getStartMillis();
        this.strikes = definition.getEffectsList().stream()
                .filter(TechniqueEffect::hasWeaponStrike).map(TechniqueEffect::getWeaponStrike).toList();
        this.rules = registry.books().stream().filter(book -> book.getId().equals(BOOK))
                .findFirst().orElseThrow().getHeldItemRules();
        Set<LivingEntity> targets = Set.of(near, far);
        this.world = new TechniqueWorldEffects(actor, () -> TechniqueWorldEffects.Policy.MOB, targets::contains,
                (striker, source, strike) -> strike.run(), DAMAGE_NAMESPACE);
        this.body = castingBody(definition, rules, clipMillis);
        this.previousMillis = CAST_START_MILLIS - 1;
    }

    static FinaleRun start(TestContext context, float nearDistance, float farDistance) {
        // 目录取自启动后的功法服务，与正式发现路径一致；施放者朝南，两头牛在正前方。
        TechniqueRegistry registry = TechniqueFabric.service().registry();
        TechniqueDefinition definition = registry.findLatest(FINALE).orElseThrow();
        VindicatorEntity actor = actor(context);
        CowEntity near = target(context, nearDistance);
        CowEntity far = target(context, farDistance);
        return new FinaleRun(registry, definition, actor, near, far);
    }

    long tickAt(long definitionMillis) {
        // 目录时刻减去承接起播点后换算为推进次数，向上取整到整 tick。
        return (definitionMillis - clipMillis + TICK_MILLIS - 1) / TICK_MILLIS;
    }

    long advance() {
        // 每次推进 50 毫秒身体时钟，只执行功法库给出的打击意图，并记下其目录时刻。
        ticks++;
        long nowMillis = CAST_START_MILLIS + ticks * TICK_MILLIS;
        body = body.toBuilder()
                .setSettledAt(time(body, nowMillis))
                .build();
        TechniqueCasting.AdvanceResult result = casting.advance(this, previousMillis);
        requireSuccess(result);
        for (EffectIntent effect : result.effects()) {
            if (effect.hasWeaponStrike()) {
                strikeMillis.add(effect.getScheduledMillis() - CAST_START_MILLIS + clipMillis);
                world.strike(effect);
            }
        }
        previousMillis = result.advancedMillis();
        return ticks;
    }

    CastPhase interrupt() {
        // 与 Damage 判定僵直后调用的同一入口；只有可打断式的起手期会被打断。
        TechniqueCasting.AdvanceResult result = casting.interruptByStagger(this, previousMillis);
        requireSuccess(result);
        previousMillis = result.advancedMillis();
        return result.outcome().getPhase();
    }

    List<Long> strikeMillis() {
        // 返回已执行打击的目录时刻副本。
        return List.copyOf(strikeMillis);
    }

    TechniqueProgress progress() {
        // 从最近提交的身体快照读取功法进度。
        return TechniqueState.read(BodyProtobuf.fromMessage(body));
    }

    double swingDose() {
        // 前荡轻击是目录里的第一刀。
        return dose(strikes.get(0));
    }

    double cleaveDose() {
        // 落地重劈是目录里的最后一刀。
        return dose(strikes.get(strikes.size() - 1));
    }

    void assertLosses(TestContext context, double nearLoss, double farLoss, String stage) {
        // 牛没有护甲和身体宿主，原版生命损失就是世界执行器给出的剂量。
        double nearActual = TARGET_HEALTH - near.getHealth();
        double farActual = TARGET_HEALTH - far.getHealth();
        context.assertTrue(Math.abs(nearActual - nearLoss) < DOSE_TOLERANCE,
                stage + "：身前目标损失 " + nearActual + "，应为 " + nearLoss);
        context.assertTrue(Math.abs(farActual - farLoss) < DOSE_TOLERANCE,
                stage + "：远处目标损失 " + farActual + "，应为 " + farLoss);
    }

    @Override
    public Inspection inspect() {
        // 每次检视返回当前快照与空手结构，出手手位已固定在施放记录里。
        return new Inspection(BodyProtobuf.fromMessage(body), DoorBreakerGameTest.emptyHands());
    }

    @Override
    public Optional<Commit> findActivity(String requestId) {
        // 本测不重放请求，没有可查的已提交回执。
        return Optional.empty();
    }

    @Override
    public Commit submit(Submission submission) {
        // 收势活动只替换功法进度并递增修订号；修订号不符时按 Framework 规则拒绝。
        long revision = body.getRevision();
        if (submission.expectedRevision() != revision) {
            throw new TechniqueErrors.HostRejected(FailureCode.REVISION_CONFLICT);
        }
        long committed = revision + 1;
        body = body.toBuilder()
                .putAllAddonProgress(submission.activity().getProgressMap())
                .setRevision(committed)
                .build();
        return new Commit(committed, submission.activity());
    }

    @Override
    public EffectCommit submitEffect(EffectSubmission submission) {
        // 打击由 advance 直接交给世界执行器，这里只确认回执。
        return new EffectCommit(submission.requestId());
    }

    private double dose(WeaponStrike strike) {
        // 与命中同一算法估算剂量；副手空着时按双手握持。
        var estimate = TechniqueWorldEffects.estimate(actor, TechniqueMessages.Hand.MAIN_HAND, rules, strike, near);
        return estimate.orElseThrow().dose();
    }

    private static void requireSuccess(TechniqueCasting.AdvanceResult result) {
        // 任何结构化失败都说明内存施放记录与功法库约定不一致。
        TechniqueFailure failure = result.outcome().getFailure();
        if (failure != TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED) {
            throw new IllegalStateException("功法库拒绝推进万门一斩：" + failure + " " + result.outcome().getReason());
        }
    }

    private static BodyMessages.BodySnapshot castingBody(TechniqueDefinition definition, HeldItemRules rules,
                                                         long clipMillis) {
        // 新生人体写入一次正在起手的承接施放：绑定取起手前修订号，起手提交使修订号加一。
        BodyMessages.BodySnapshot newborn = DoorBreakerGameTest.newBody(UUID.randomUUID());
        ActiveCast active = activeCast(definition, rules, newborn, clipMillis);
        TechniqueProgress progress = TechniqueState.empty().toBuilder()
                .setActiveCast(active)
                .build();
        return newborn.toBuilder()
                .setRevision(newborn.getRevision() + 1)
                .setSettledAt(time(newborn, CAST_START_MILLIS))
                .putAddonProgress(TechniqueState.KEY, TechniqueState.pack(progress))
                .build();
    }

    private static ActiveCast activeCast(TechniqueDefinition definition, HeldItemRules rules,
                                         BodyMessages.BodySnapshot body, long clipMillis) {
        // 与功法库起手同一换算：承接剪辑缩短起手，三段边界此后固定。
        CastPhases phases = definition.getPhases();
        long warmupEnd = CAST_START_MILLIS + phases.getWarmupMillis() - clipMillis;
        long activeEnd = warmupEnd + phases.getActiveMillis();
        long recoveryEnd = activeEnd + phases.getRecoveryMillis();
        return ActiveCast.newBuilder()
                .setRequest(request(definition, body))
                .setStartedMillis(CAST_START_MILLIS)
                .setWarmupEndMillis(warmupEnd)
                .setActiveEndMillis(activeEnd)
                .setRecoveryEndMillis(recoveryEnd)
                .setActionInstanceId(ACTION_INSTANCE_ID)
                .setBinding(binding(body))
                .setInterruptibleByDamage(phases.getInterruptibleByDamage())
                .setSelectedHand(TechniqueMessages.Hand.MAIN_HAND)
                .setMovementSpeedMultiplier(definition.getMovementSpeedMultiplier())
                .setCooldownMillis(definition.getCooldownMillis())
                .setClipStartMillis(clipMillis)
                .setHeldItemRules(rules)
                .build();
    }

    private static CastRequest request(TechniqueDefinition definition, BodyMessages.BodySnapshot body) {
        // 承接入口与目录精确版本；目标由世界几何决定，请求不带目标。
        TechniqueReference technique = TechniqueReference.newBuilder()
                .setId(definition.getId())
                .setVersion(definition.getVersion())
                .build();
        return CastRequest.newBuilder()
                .setBodyId(body.getId())
                .setTechnique(technique)
                .setCastId(CAST_ID)
                .setEntry(CastEntry.FOLLOW_UP)
                .build();
    }

    private static TechniqueBinding binding(BodyMessages.BodySnapshot body) {
        // 打击与收势不读取槽位，绑定只需与请求同属这具身体。
        return TechniqueBinding.newBuilder()
                .setBodyId(body.getId())
                .setRevision(body.getRevision())
                .build();
    }

    private static BodyMessages.TimeCursor time(BodyMessages.BodySnapshot body, long elapsedMillis) {
        // 只改已结算时刻，时间域与纪元沿用出生快照。
        return body.getSettledAt().toBuilder()
                .setElapsedMillis(elapsedMillis)
                .build();
    }

    private static VindicatorEntity actor(TestContext context) {
        // 卫道士只作持斧施放者：无 AI、无重力、朝南站定，副手空着即双手握持。
        VindicatorEntity actor = context.spawnEntity(EntityType.VINDICATOR, ACTOR_X, GROUND_Y, ACTOR_Z);
        still(actor);
        actor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        actor.setYaw(FACING_SOUTH);
        actor.setHeadYaw(FACING_SOUTH);
        return actor;
    }

    private static CowEntity target(TestContext context, float distance) {
        // 无身体宿主的牛保留原版血量；满额击退抗性让被打的牛留在原位。
        CowEntity target = context.spawnEntity(EntityType.COW, ACTOR_X, GROUND_Y, ACTOR_Z + distance);
        still(target);
        target.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)
                .setBaseValue(FULL_KNOCKBACK_RESISTANCE);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(TARGET_HEALTH);
        target.setHealth((float) TARGET_HEALTH);
        return target;
    }

    private static void still(MobEntity mob) {
        // 测试实体不自行移动、不下落、不发声。
        mob.setAiDisabled(true /* aiDisabled */);
        mob.setNoGravity(true /* noGravity */);
        mob.setSilent(true /* silent */);
    }
}
