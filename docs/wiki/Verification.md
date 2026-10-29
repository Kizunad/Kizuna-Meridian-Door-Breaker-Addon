# 验证

## 自动覆盖

- Python 检查 JSON 格式、六种伤害类型、Damage 追加标签、分类标签、静态方块物品清单、无入口 Fabric 元数据及测试与正式包一致的清单声明。
- JUnit 使用正式清单解析器读取唯一 `.txtpb`，装入 `human:adult@1` 后检查书卷、前置关系、承接、去重组和曲线期限。
- 叩门三击的时刻、倍率总和、独立去重组、单目标预算和唯一末击耐久均有目录断言；
  动画时序检查三次腕部接触帧、末击通知与主副手总长。腿肌门槛检查四块主要腿肌与 510 N 要求。
- GameTest 由真实 Fabric 元数据发现目录、读取 Human 导出的模板资源，使用启动后的 `TechniqueFabric.service()` 检查下卷前置拒绝与整卷学习，并检查静态 `block_items` 标签覆盖注册表中的 BlockItem。
- `DoorBreakerGameTest.upperScrollLearnsAllThreeTechniques` 从真实物品注册表获取上卷，
  经服务端玩家右键入口学会精确三式，并验证生存模式消耗卷轴。
- 文档检查本地链接，`git diff --check` 检查补丁空白。

完整命令：

```sh
bash "gradlew" test build :gametest:runGametest
python3 "scripts/check_docs.py"
python3 "scripts/check_data.py"
git diff --check
```

GameTest 将原版 BlockItem 注册表写到 `gametest/build/block-items.json`，供维护静态标签时核对。
该文件和测试世界都属于构建输出，不会进入数据包。

GameTest 使用测试模组元数据和内存身体宿主，正式资源与清单路径保持一致；不加载 Human、Damage 的实体执行器，
不验证真实玩家伤害、耐久、破门、盾牌或动画。正式包的纯数据边界另由 JAR 内容断言检查。
完整装配的 `Basic-Techniques` GameTest 验证主副手叩门在 18/25/34 tick 各受伤一次、重新闪红、
三条调试日志、逐击蔓延、目标离开后落空，以及类人生物同样三击；Human 验证临时换物品后的三击总剂量。
专用卷轴图标和阅读背景已随本仓提供，见[美术资源](Art.md)；只读阅读、布局和缺图回退由功法库负责。
GameTest 不覆盖客户端实际渲染；多档 GUI 缩放的布局与背景回退由功法库单测验证。
