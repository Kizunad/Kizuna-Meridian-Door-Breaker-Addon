package dev.kizuna.meridian.doorbreaker.verification;

import dev.kizuna.meridian.human.server.HumanContent;
import dev.kizuna.meridian.protocol.pb.BodyMessages.QiPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.QiCostMatching;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.QiDistribution;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.server.TechniqueAddonResources;
import dev.kizuna.meridian.technique.server.TechniqueCatalogManifest;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DoorBreakerCatalogTest {
    private static final double MOVEMENT_MULTIPLIER = 0.4;
    private static final double EPSILON = 0.000001;
    private static final String NAMESPACE = "meridian_door_breaker";
    private static final String BOOK_ID = NAMESPACE + ":door_breaker";
    private static final String UPPER_ID = NAMESPACE + ":upper";
    private static final String MIDDLE_ID = NAMESPACE + ":middle";
    private static final String LOWER_ID = NAMESPACE + ":lower";
    private static final Map<String, Double> TOTAL_QI_COSTS = Map.of(
            "knock", 3.0, "cleave", 8.0, "ram", 8.0, "hook", 8.0,
            "sweep", 10.0, "close", 8.0, "chain", 18.0, "finale", 30.0);
    private static final int WORKING_LEG_COUNT = 4;
    private static final double MINIMUM_LEG_STRENGTH = 510;
    private static final List<Long> KNOCK_HITS = List.of(900L, 1250L, 1700L);
    private static final List<Double> KNOCK_MULTIPLIERS = List.of(0.3, 0.3, 0.9);
    private static final double KNOCK_TOTAL_MULTIPLIER = 1.5;
    private static final Map<String, Double> TOTAL_LEG_POWER = Map.of(
            "ram", 600.0, "close", 600.0, "chain", 600.0, "finale", 1000.0);

    @Test
    public void legWorkDistributesTotalPowerAcrossCapableAdultMuscles() throws IOException {
        // 按正式成人组织容量校验总功率，防止把整体功率重复施加到每块腿肌。
        TechniqueRegistry registry = DoorBreakerCatalog.registry(DoorBreakerCatalog.load());
        var structure = DoorBreakerCatalog.addons()
                .findTemplate(HumanContent.TEMPLATE_ID, HumanContent.CONTENT_VERSION).orElseThrow().getStructure();
        for (var entry : TOTAL_LEG_POWER.entrySet()) {
            var definition = registry.findLatest(NAMESPACE + ":" + entry.getKey()).orElseThrow();
            var slot = definition.getSlotsList().stream()
                    .filter(candidate -> candidate.getId().equals("leg_muscles")).findFirst().orElseThrow();
            assertEquals(MINIMUM_LEG_STRENGTH, slot.getMinimumStrength().getValue(), 0);
            var muscles = structure.getTissuesList().stream()
                    .filter(tissue -> tissue.getTagsList().containsAll(slot.getTagsList()))
                    .filter(tissue -> tissue.getCapacity().getStrengthNewtons() >= slot.getMinimumStrength().getValue())
                    .toList();
            assertEquals(WORKING_LEG_COUNT, muscles.size());
            assertEquals(WORKING_LEG_COUNT, slot.getMinimumCount());
            assertEquals(WORKING_LEG_COUNT, slot.getMaximumCount());
            var work = definition.getCost().getWork(0);
            double perMuscle = work.getPower().getValue();
            assertEquals(entry.getValue(), muscles.size() * perMuscle, 0);
            for (var muscle : muscles) {
                assertTrue(muscle.getCapacity().getBurstWatts() >= perMuscle);
            }
        }
    }

    @Test
    public void knockHitsThreeTimesWithOneTargetBudgetAndOneDurabilityCharge() throws IOException {
        // 三个独立组允许重复敲同一目标，总倍率和整次不同目标预算保持不变。
        var definition = DoorBreakerCatalog.registry(DoorBreakerCatalog.load())
                .findLatest(NAMESPACE + ":knock").orElseThrow();
        var effects = definition.getEffectsList().stream().filter(TechniqueEffect::hasWeaponStrike).toList();
        assertEquals(KNOCK_HITS, effects.stream().map(TechniqueEffect::getAtMillis).toList());
        var strikes = effects.stream().map(TechniqueEffect::getWeaponStrike).toList();
        assertEquals(KNOCK_MULTIPLIERS, strikes.stream().map(strike -> strike.getMultiplier().getValue()).toList());
        assertEquals(KNOCK_TOTAL_MULTIPLIER,
                strikes.stream().mapToDouble(strike -> strike.getMultiplier().getValue()).sum(), EPSILON);
        assertEquals(strikes.size(), strikes.stream().map(strike -> strike.getDedupGroup()).distinct().count());
        for (int index = 0; index < strikes.size(); index++) {
            var strike = strikes.get(index);
            assertEquals(1, strike.getMaximumTargets());
            assertEquals(1, strike.getCastTargetLimit());
            assertEquals(1, strike.getDurabilityBase());
            assertEquals(index == strikes.size() - 1, strike.getLastStrike());
        }
        var phases = definition.getPhases();
        assertEquals(KNOCK_HITS.get(0).longValue(), phases.getWarmupMillis());
        assertFalse(phases.getInterruptibleByDamage());
    }

    @Test
    public void combatCostsAcceptGeneratedQiAndChargeOneTotalPrice() throws IOException {
        // 任意属性真炁可支付低阶式，总价不随绑定手经条数翻倍，下卷仍要求真炁。
        TechniqueRegistry registry = DoorBreakerCatalog.registry(DoorBreakerCatalog.load());
        for (var entry : TOTAL_QI_COSTS.entrySet()) {
            var definition = registry.findLatest(NAMESPACE + ":" + entry.getKey()).orElseThrow();
            assertEquals(1, definition.getCost().getQiCount());
            var cost = definition.getCost().getQi(0);
            assertEquals(QiCostMatching.ANY_ATTRIBUTE_AT_LEAST_PHASE, cost.getMatching());
            assertTrue(cost.getAttribute().isEmpty());
            assertEquals(QiDistribution.EVEN, cost.getDistribution());
            assertEquals(entry.getValue(), cost.getAmount().getValue(), 0);
            QiPhase minimum = QiPhase.FREE;
            if (entry.getKey().equals("chain") || entry.getKey().equals("finale")) {
                minimum = QiPhase.GAS;
            }
            assertEquals(minimum, cost.getPhase());
        }
    }

    @Test
    public void productionJarContainsDeclaredCatalogWithoutJavaEntrypoint() throws IOException {
        // 检查实际发布 JAR，维护源可被声明路径读取且没有运行时代码。
        try (var jar = new JarFile(System.getProperty("doorBreaker.fabricJar"))) {
            String path = DoorBreakerCatalog.RESOURCE.substring(1);
            String metadata = jarText(jar, "fabric.mod.json");
            assertTrue(metadata.contains("\"meridian_technique:catalogs\": \"" + path + "\""));
            assertFalse(metadata.contains("\"entrypoints\""));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().endsWith(".class")));
            var catalog = TechniqueCatalogManifest.parseText(NAMESPACE + ":" + path, jarText(jar, path));
            assertEquals(DoorBreakerCatalog.load(), catalog);
        }
    }

    @Test
    public void exportedHumanBundleValidatesCatalogWithoutHumanCodeAssembly() throws IOException {
        // 正式清单只依赖 Human JAR 的公开 Protobuf 资源即可完成模板和标签校验。
        var catalog = DoorBreakerCatalog.load();
        var resources = new TechniqueAddonResources(DoorBreakerCatalogTest.class.getClassLoader());
        var addons = resources.registry(catalog);
        assertTrue(addons.findTemplate(HumanContent.TEMPLATE_ID, HumanContent.CONTENT_VERSION).isPresent());
        TechniqueRegistry registry = TechniqueRegistry.load(addons, List.of(catalog));
        assertEquals(8, registry.definitions().size());
    }

    @Test
    public void loadsBookVolumesFollowUpsAndCurveThroughTechniqueRegistry() throws IOException {
        // 目录必须通过文本解析和真实功法注册校验后才能供断言使用。
        var catalog = DoorBreakerCatalog.load();
        TechniqueRegistry registry = DoorBreakerCatalog.registry(catalog);
        var books = registry.books();
        var volumes = registry.volumes();

        assertEquals(1, books.size());
        assertEquals(BOOK_ID, books.get(0).getId());
        assertEquals(3, volumes.size());
        var upper = registry.findVolume(UPPER_ID).orElseThrow();
        var middle = registry.findVolume(MIDDLE_ID).orElseThrow();
        var lower = registry.findVolume(LOWER_ID).orElseThrow();
        assertEquals(3, upper.getTechniquesCount());
        assertEquals(3, middle.getTechniquesCount());
        assertEquals(2, lower.getTechniquesCount());
        assertEquals(UPPER_ID, middle.getPrerequisiteVolumeIds(0));
        assertEquals(MIDDLE_ID, lower.getPrerequisiteVolumeIds(0));
        assertEquals(8, registry.definitions().size());

        verifyFollowUps(registry);
        verifyStrikeGroups(registry);
        verifyFinaleCurve(registry);
    }

    private static void verifyFollowUps(TechniqueRegistry registry) {
        // 承接引用必须解析到目录中的精确功法版本。
        for (TechniqueDefinition definition : registry.definitions()) {
            assertEquals(MOVEMENT_MULTIPLIER, definition.getMovementSpeedMultiplier(), EPSILON);
            for (var transition : definition.getFollowUp().getTransitionsList()) {
                TechniqueReference previous = transition.getPrevious();
                assertTrue(registry.find(previous).isPresent());
            }
        }
        assertTrue(registry.findLatest(NAMESPACE + ":finale").orElseThrow()
                .getFollowUp().getOnlyFollowUp());
    }

    private static String jarText(JarFile jar, String path) throws IOException {
        // 资源从发布包读取，缺失时明确指明打包路径。
        var entry = jar.getJarEntry(path);
        if (entry == null) {
            throw new IOException("发布包缺少资源：" + path);
        }
        try (var stream = jar.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void verifyStrikeGroups(TechniqueRegistry registry) {
        // 横扫两段共享去重组，连门三刀各自保留独立组。
        var sweep = registry.findLatest(NAMESPACE + ":sweep").orElseThrow();
        var sweepGroups = new HashSet<String>();
        var chain = registry.findLatest(NAMESPACE + ":chain").orElseThrow();
        var chainGroups = new HashSet<String>();
        for (var effect : sweep.getEffectsList()) {
            if (effect.hasWeaponStrike()) {
                sweepGroups.add(effect.getWeaponStrike().getDedupGroup());
            }
        }
        for (var effect : chain.getEffectsList()) {
            if (effect.hasWeaponStrike()) {
                chainGroups.add(effect.getWeaponStrike().getDedupGroup());
            }
        }
        assertEquals(1, sweepGroups.size());
        assertEquals(3, chainGroups.size());
        assertFalse(sweepGroups.contains(""));
    }

    private static void verifyFinaleCurve(TechniqueRegistry registry) {
        // 末式曲线最晚时刻必须落在收势结束加冷却的边界内。
        var finale = registry.findLatest(NAMESPACE + ":finale").orElseThrow();
        var phases = finale.getPhases();
        long castDuration = phases.getWarmupMillis()
                + phases.getActiveMillis()
                + phases.getRecoveryMillis();
        long cooldownEnd = castDuration + finale.getCooldownMillis();
        long latestCurveEnd = 0;
        for (var effect : finale.getEffectsList()) {
            if (!effect.hasTissueModifier()) {
                continue;
            }
            var curve = effect.getTissueModifier().getCurveList();
            long end = effect.getAtMillis() + curve.get(curve.size() - 1).getOffsetMillis();
            latestCurveEnd = Math.max(latestCurveEnd, end);
        }
        assertTrue(latestCurveEnd <= cooldownEnd);
    }
}
