package dev.kizuna.meridian.doorbreaker.test;

import dev.kizuna.meridian.doorbreaker.verification.DoorBreakerCatalog;
import dev.kizuna.meridian.human.server.HumanBirth;
import dev.kizuna.meridian.human.server.HumanContent;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.ActivityMessages.BodyActivity;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TimeCursor;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.FailureCode;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.RequestFailure;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.TechniqueScrollItems;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.HandSide;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.HeldItem;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.HeldItems;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueActivityCommit;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueHostRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueHostResponse;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueProgress;
import dev.kizuna.meridian.technique.server.TechniqueHostEntrypoint;
import dev.kizuna.meridian.technique.server.TechniqueProtocol;
import dev.kizuna.meridian.technique.server.TechniqueService;
import dev.kizuna.meridian.technique.server.TechniqueState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.kizuna.meridian.doorbreaker.test.DoorBreakerGameTestTicks.DEFAULT_TIMEOUT;

/** 破门斩整合验证，只装配测试目录和内存人体宿主。 */
public final class DoorBreakerGameTest implements TechniqueHostEntrypoint {
    private static final String UPPER_VOLUME = "meridian_door_breaker:upper";
    private static final String LOWER_VOLUME = "meridian_door_breaker:lower";
    private static final String BLOCK_ITEMS_REPORT = "doorBreaker.blockItemsReport";
    private static final int UPPER_TECHNIQUE_COUNT = 3;
    private static final Map<UUID, MemoryTechniqueHost> HOSTS = new HashMap<>();
    private static final TagKey<Item> BLOCK_ITEMS = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath("meridian_door_breaker", "block_items"));

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void learnsUpperVolumeAfterPrerequisiteRejection(GameTestHelper context) throws Exception {
        // 正式 Fabric 服务在启动时发现清单；测试只提供学习所需的内存身体宿主。
        UUID entityId = UUID.randomUUID();
        MemoryTechniqueHost host = new MemoryTechniqueHost(entityId, newBody(entityId));
        HOSTS.put(entityId, host);
        TechniqueService service = TechniqueFabric.service();
        boolean discovered = service.registry().sources().stream().anyMatch(source -> source.source().equals(
                "meridian_door_breaker_test:data/meridian_door_breaker/technique_catalog.txtpb"));
        context.assertTrue(discovered, "破门斩目录没有通过模组清单发现");
        try {
            rejectLowerVolume(service, entityId);
            context.assertTrue(host.progress().getKnownCount() == 0, "前置拒绝仍写入了功法进度");
            var learned = service.learn(entityId, UPPER_VOLUME);
            context.assertTrue(learned.size() == UPPER_TECHNIQUE_COUNT, "上卷没有一次学会三式");
            context.assertTrue(service.known(entityId).size() == UPPER_TECHNIQUE_COUNT, "身体进度没有保留三式");
        } finally {
            HOSTS.remove(entityId);
        }
        context.succeed();
    }

    @Override
    public byte[] exchange(byte[] payload) {
        // 与正式功法服务使用同一宿主发现入口，只认领本测试登记的身体。
        TechniqueHostRequest request = TechniqueProtocol.decodeHost(payload);
        MemoryTechniqueHost host = HOSTS.get(UUID.fromString(request.getEntityUuid()));
        if (host != null) {
            return host.exchange(payload);
        }
        var response = TechniqueHostResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setUnbound(true /* unbound */)
                .build();
        return TechniqueProtocol.encodeHostResponse(response);
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void upperScrollLearnsAllThreeTechniques(GameTestHelper context) {
        // 正式清单自动生成的上卷物品通过服务端右键入口一次写入三式。
        var player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        UUID entityId = player.getUUID();
        MemoryTechniqueHost host = new MemoryTechniqueHost(entityId, newBody(entityId));
        HOSTS.put(entityId, host);
        try {
            var item = TechniqueScrollItems.scrolls().get(Identifier.parse(UPPER_VOLUME + "_scroll"));
            context.assertTrue(item != null, "上卷清单没有自动注册物品");
            ItemStack stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResult result = player.gameMode.useItem(
                    player, context.getLevel(), stack, InteractionHand.MAIN_HAND);
            context.assertTrue(consumed(result), "上卷右键学习失败");
            context.assertTrue(stack.isEmpty(), "生存模式没有消耗上卷物品");
            context.assertTrue(host.progress().getKnownCount() == UPPER_TECHNIQUE_COUNT, "上卷未学会全部三式");
            TechniqueService service = TechniqueFabric.service();
            var expected = service.registry().findVolume(UPPER_VOLUME).orElseThrow();
            var actual = service.known(entityId).stream()
                    .map(TechniqueService.KnownEntry::technique)
                    .toList();
            context.assertTrue(actual.containsAll(expected.getTechniquesList()), "上卷学会的式身份与清单不符");
        } finally {
            context.getLevel().getServer().getPlayerList().remove(player);
            HOSTS.remove(entityId);
        }
        context.succeed();
    }

    private static boolean consumed(InteractionResult result) {
        // 26.2 的物品使用结果会另带手中物品，不能再按常量同一性比较；服务端成功且不挥手即 1.20.1 的 CONSUME。
        return result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.NONE;
    }

    private static void rejectLowerVolume(TechniqueService service, UUID entityId) {
        // 未学上卷时必须在进度提交前拒绝下卷。
        try {
            service.learn(entityId, LOWER_VOLUME);
            throw new AssertionError("未先学上卷却接受了下卷");
        } catch (TechniqueService.PrerequisiteVolumeException expected) {
            // 只接受明确的前置卷拒绝，其他失败应使测试失败。
        }
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void blockItemsTagCoversVanillaRegistry(GameTestHelper context) throws IOException {
        // 导出原版 BlockItem 清单，并确保维护的静态标签覆盖注册表全部方块物品。
        List<String> blockItemIds = blockItemIds();
        writeBlockItemReport(blockItemIds);
        var tag = BuiltInRegistries.ITEM.get(BLOCK_ITEMS).orElse(null);
        context.assertTrue(tag != null, "缺少 meridian_door_breaker:block_items 标签");
        if (tag == null) {
            return;
        }
        var taggedIds = new HashSet<String>();
        tag.stream().forEach(entry -> taggedIds.add(BuiltInRegistries.ITEM.getKey(entry.value()).toString()));
        List<String> missing = blockItemIds.stream().filter(id -> !taggedIds.contains(id)).toList();
        context.assertTrue(missing.isEmpty(), "block_items 缺少原版方块物品：" + missing);
        context.succeed();
    }

    static BodySnapshot newBody(UUID entityId) {
        // 初始快照使用 Human 已登记模板与正式出生构造器。
        var addons = DoorBreakerCatalog.addons();
        var template = addons.findTemplate(HumanContent.TEMPLATE_ID, HumanContent.CONTENT_VERSION)
                .orElseThrow();
        var time = TimeCursor.newBuilder()
                .setDomain("meridian_door_breaker:test")
                .setEpoch("gametest")
                .setElapsedMillis(0)
                .build();
        try {
            return BodySnapshot.parseFrom(HumanBirth.candidate(template, entityId.toString(), time));
        } catch (IOException exception) {
            throw new IllegalStateException("无法构造测试人体", exception);
        }
    }

    private static List<String> blockItemIds() {
        // 注册表中的项目按稳定 ID 排序，报告可直接用于更新静态数据标签。
        var ids = new ArrayList<String>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem) {
                ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        ids.sort(Comparator.naturalOrder());
        return List.copyOf(ids);
    }

    private static void writeBlockItemReport(List<String> ids) throws IOException {
        // 清单只写入 Gradle 构建目录，不进入正式数据包。
        String output = System.getProperty(BLOCK_ITEMS_REPORT);
        if (output == null || output.isBlank()) {
            throw new IllegalStateException("缺少系统属性：" + BLOCK_ITEMS_REPORT);
        }
        Path report = Path.of(output);
        Files.createDirectories(report.getParent());
        String json = ids.stream()
                .map(id -> "  \"" + id + "\"")
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
        Files.writeString(report, json);
    }

    private static TechniqueHostResponse response(String requestId) {
        // 空手事实显式提供，目录学习不读取物品但协议要求同步检查双手结构。
        return TechniqueHostResponse.newBuilder()
                .setRequestId(requestId)
                .setHeldItems(emptyHands())
                .build();
    }

    static HeldItems emptyHands() {
        // 宿主检视必须带双手结构；只推进施放的测试不按物品事实选择出手。
        return HeldItems.newBuilder()
                .setMainHand(HeldItem.getDefaultInstance())
                .setOffHand(HeldItem.getDefaultInstance())
                .setMainHandSide(HandSide.RIGHT)
                .build();
    }

    private static final class MemoryTechniqueHost implements TechniqueHostEntrypoint {
        private final UUID entityId;
        private BodySnapshot body;

        private MemoryTechniqueHost(UUID entityId, BodySnapshot body) {
            // 测试宿主只认领一具人体，并在活动提交时原子替换其快照进度。
            this.entityId = entityId;
            this.body = body;
        }

        @Override
        public byte[] exchange(byte[] payload) {
            // 请求与回执都通过功法库的字节契约校验。
            TechniqueHostRequest request = TechniqueProtocol.decodeHost(payload);
            if (!request.getEntityUuid().equals(entityId.toString())) {
                return TechniqueProtocol.encodeHostResponse(TechniqueHostResponse.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setUnbound(true /* unbound */)
                        .build());
            }
            if (request.hasInspect()) {
                return inspect(request.getRequestId());
            }
            return commit(request);
        }

        private byte[] inspect(String requestId) {
            // 每次读取返回相同 revision 的权威快照与空手状态。
            var result = response(requestId).toBuilder()
                    .setBody(body)
                    .build();
            return TechniqueProtocol.encodeHostResponse(result);
        }

        private byte[] commit(TechniqueHostRequest request) {
            // 学习活动只更新 Technique 附属进度并递增身体 revision。
            long currentRevision = body.getRevision();
            String requestId = request.getRequestId();
            if (request.getExpectedRevision() != currentRevision) {
                RequestFailure failure = RequestFailure.newBuilder()
                        .setCode(FailureCode.REVISION_CONFLICT)
                        .build();
                return TechniqueProtocol.encodeHostResponse(TechniqueHostResponse.newBuilder()
                        .setRequestId(requestId)
                        .setFailure(failure)
                        .build());
            }
            BodyActivity activity = request.getActivity();
            long committedRevision = currentRevision + 1;
            body = body.toBuilder()
                    .putAllAddonProgress(activity.getProgressMap())
                    .setRevision(committedRevision)
                    .build();
            TechniqueActivityCommit commit = TechniqueActivityCommit.newBuilder()
                    .setRevision(committedRevision)
                    .setActivity(activity)
                    .build();
            return TechniqueProtocol.encodeHostResponse(TechniqueHostResponse.newBuilder()
                    .setRequestId(requestId)
                    .setActivity(commit)
                    .build());
        }

        private TechniqueProgress progress() {
            // 从提交后的身体快照读取实际功法进度。
            return TechniqueState.read(BodyProtobuf.fromMessage(body));
        }
    }
}
