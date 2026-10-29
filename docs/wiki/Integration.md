# 接口与依赖

## 数据边界

`shared/src/main/resources/data/meridian_door_breaker/technique_catalog.txtpb` 是唯一功法目录。
正式数据包由 `fabric/` 打包；Fabric 元数据没有入口声明，正式 JAR 不带 Java 类。
正式元数据通过 `custom.meridian_technique:catalogs` 声明
`data/meridian_door_breaker/technique_catalog.txtpb`，功法库在两端模组初始化时发现并读取，
在服务器启动时合并其他目录完成语义校验。
清单直接编码 `TechniqueCatalog`，`schema_version: 1` 同时表达清单格式版本。
`verification/` 使用功法库的清单解析器并调用 `TechniqueRegistry.load(...)`，仅用于校验，不会进入正式 JAR。

本仓正式元数据硬依赖功法库、`meridian_vanilla_damage` 和 `meridian_human_host`。目录绑定 `human:adult@1`
的手经、臂肌与腿肌标签；整合服还需安装兼容版本的 Framework。
功法库从 Human 服务端 JAR 已导出的 `META-INF/kizuna_meridian/addons/human.pb` 建立模板校验上下文，
不调用 Human 的 Java 内容构造器，也不要求本仓提供入口。缺少资源、版本不兼容或与其他目录身份冲突均中止启动。

## 卷轴与创造栏

正式包使用 `environment: "*"`，两端由同一清单自动生成以下卷轴，无需本仓注册 Java 物品：

| 物品 ID | 显示名 |
|---|---|
| `meridian_door_breaker:upper_scroll` | 破门斩·上卷·木门 |
| `meridian_door_breaker:middle_scroll` | 破门斩·中卷·铁门 |
| `meridian_door_breaker:lower_scroll` | 破门斩·下卷·万门 |

三个卷轴按上、中、下顺序进入功法库提供的「功法」创造栏。tooltip 显示各式短名及前置卷；
短名来自 `assets/meridian_door_breaker/lang/`，沿用原目录设计的叩门、破门、撞门、破盾、横扫、掩门、连门、万门一斩。
右键调用功法库按卷学习：成功生存消耗一个，创造不消耗；缺前置、重复学习、无身体等失败不消耗。

专用图标约定为 `assets/meridian_door_breaker/textures/item/<upper|middle|lower>_scroll.png`，
本仓提供三卷专属图标，资源与尺寸见[美术资源](Art.md)。
阅读背景约定为 `assets/meridian_door_breaker/textures/gui/scroll/<upper|middle|lower>.png`，
三张背景随本仓打包。服务端确认学习成功后，功法库打开只读界面，展示书名·卷名、各式名称与说明、前置卷；
正文在中央纸面内换行和滚动，ESC 关闭。缺失专属资源时由功法库提供通用图标或背景并按资源警告一次。

## 目录规则

书级持物规则按斧、刃器、其他近战工具、方块物品和兜底物品分类；刃器与其他近战工具选手优先级相同，
再按耐久倍率择优，不能仅因木剑属于刃器而压过更耐用的铁镐。耐久选择按铁斧、其他斧、铁或下界合金近战工具、
其他耐久物品的顺序匹配。一次性物品由功法库排在耐久物品候选之后。书卷按上、中、下顺序学习。

真元费用采用 `ANY_ATTRIBUTE_AT_LEAST_PHASE`：上、中卷最低游炁，下卷最低气态真炁，任意属性均可支付。
扣除和释放保留库存原属性与密度，不把真炁转换为游炁。`EVEN` 将每式声明总价平摊到绑定的已开手经，
叩门到万门一斩总价依次为 3、8、8、8、10、8、18、30；任一经脉不足则整次拒绝。
腿部做功绑定四块当前有效力量至少 510 N 的腿肌：撞门、掩门、连门每块 150 W，万门一斩每块 250 W。
门槛由 600 N 下调，给基础 600 N 的股直肌留 15% 疲劳或伤势余量，基础 500 N 的腓肠肌不参与正常绑定。
正常走动与连续出招仍须支付真实做功，重疲劳或重伤会继续被能力检查拒绝。
合计分别为 600 W、1000 W；`WorkCost` 按每块组织结算，不能把整体功率重复施加到每块腿肌。

六种伤害类型分别表达切割或钝击，以及主手、副手或双手出力。Damage 的 `melee`、`kind/cutting`、`kind/blunt`、
`off_hand`、`two_handed` 标签采用 `replace: false`，仅追加本仓类型。`breakable_doors` 默认包含原版木门、木活板门和栅栏门。
主手、副手钝击类型另追加到原版 `bypasses_cooldown`，使间隔 7/9 tick 的叩门三击都完整结算并重新闪红。
该标签对使用这两个伤害类型的其他功法钝击同样生效；切割和双手钝击类型不在此标签中。
三击仍分别经过护甲、身体损伤、溢出蔓延和伤害日志，不绕过盾挡、伤势或其他无敌条件。

`block_items` 是由 Minecraft 1.20.1 注册表导出的静态物品辅助标签，GameTest 会核对标签覆盖完整原版
BlockItem 清单并把清单写到 `gametest/build/block-items.json`。新增模组的方块物品需由整合包补进该标签。
火把虽然属于 BlockItem，仍按其他手持物品造成 0.5 基础伤害；`handheld_misc` 在方块分类前匹配普通、灵魂及红石火把。
铁门与铁活板门不属于可劈门标签。首版不定义 `protected` 标签。

## 当前限制

清单与其他模组的清单、编程式目录共同合并校验。书卷和式的定义不会按加载顺序覆盖。
GameTest 使用相同清单声明及正式 `TechniqueFabric.service()`，测试宿主只提供内存身体进度。
目录数据不实现伤害计算、物品耐久、盾牌、玩家挖掘权限、世界方块变更、AI 或动画。
