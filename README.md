# 破门斩

本仓提供 `meridian_door_breaker` 命名空间下的一书三卷八式目录，以及 Minecraft 1.20.1
伤害类型和数据标签。正式 Fabric 包不包含 Java 运行时代码，也不负责施放、伤害或劈门规则；这些行为由
功法库、Damage、Human 与宿主实现。

目录维护源只有一份：[technique_catalog.txtpb](shared/src/main/resources/data/meridian_door_breaker/technique_catalog.txtpb)。
数据结构和接口边界见 [数据目录](docs/wiki/Home.md)、[上游接口](docs/wiki/Integration.md) 和
[验证记录](docs/wiki/Verification.md)。

## 构建

本地相邻工作区提供 Framework、Technique、Human 与 Damage 依赖。运行完整检查：

```sh
bash "gradlew" test build :gametest:runGametest
```

正式包通过 Fabric 元数据的清单声明，由功法库在服务端启动时发现、解析并合并校验。
