# 验证

## 自动覆盖

- Python 检查 JSON 格式、六种伤害类型、Damage 追加标签、分类标签、静态方块物品清单、无入口 Fabric 元数据及测试与正式包一致的清单声明。
- JUnit 使用正式清单解析器读取唯一 `.txtpb`，装入 `human:adult@1` 后检查书卷、前置关系、承接、去重组和曲线期限。
- 叩门三击的时刻、倍率总和、独立去重组、单目标预算和唯一末击耐久均有目录断言；
  动画时序检查三次腕部接触帧、末击通知与主副手总长。
  腿槽检查六块肌肉、无力量门槛、各式总功率守恒以及每块基础爆发功率充足。
- 万门一斩两刀的时刻、倍率、范围、目标数、独立去重组、相同双手倍率、唯一末击与唯一劈门时刻均有目录断言；
  另查轻击早于起手结束、晚于承接起播点，且功法库预报的首击为 2300 毫秒。
- 八式的 `sound` 效果随目录经功法库校验：ID 语法、音量 (0, 4]、音调与抖动范围、锚点必填，`TARGET` 声音必须紧跟同刻 `weapon_strike`。
  世界里的实际播放由完整装配的 `Basic-Techniques` GameTest 按本仓目录逐条核对，见下文。
- GameTest 由真实 Fabric 元数据发现目录、读取 Human 导出的模板资源，使用启动后的 `TechniqueFabric.service()` 检查下卷前置拒绝与整卷学习，并检查静态 `block_items` 标签覆盖注册表中的 BlockItem。
- `DoorBreakerGameTest.upperScrollLearnsAllThreeTechniques` 从真实物品注册表获取上卷，
  经服务端玩家右键入口学会精确三式，并验证生存模式消耗卷轴。
- `DoorBreakerFinaleGameTest` 用内存宿主写入一次承接起手的万门一斩，逐 tick 走功法库真实的施放状态机与调度器，
  再由真实世界执行器让持铁斧的卫道士出刀，目标是身前 2 格、3 格的两头牛：
  2.3 秒只有近处的牛受一次 ×0.8 剂量，4.85 秒两头牛再各受一次 ×2.5 剂量；
  起手 2.5 秒僵直打断时，轻击已生效，重劈不出，进度记为打断。
  冲量、表现和移动限速不在本例执行。
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
同一批用例按本仓目录核对叩门每击的挥风声与命中声（落空的击没有命中声），`staggerInCleaveWarmupSilencesTheRemainingSounds` 验证破门起手僵直后不再出声。
本仓目录改动后先执行 `bash "gradlew" :fabric:build`，Basic 的 GameTest 才会装配到新目录。
同一装配的 `LegTechniqueGameTest` 验证步行 60 秒、疾跑 15 秒后仍可连续三次撞门，
重疲劳或腿肌重伤时六肌绑定仍通过，但真实做功预算拒绝施放。
专用卷轴图标和阅读背景已随本仓提供，见[美术资源](Art.md)；只读阅读、布局和缺图回退由功法库负责。
GameTest 不覆盖客户端实际渲染；多档 GUI 缩放的布局与背景回退由功法库单测验证。
