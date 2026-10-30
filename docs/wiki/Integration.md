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
腿部做功固定绑定六块腿肌，不设绝对力量门槛：双侧臀大肌、股直肌、腓肠肌均参与。
基础爆发功率分别为 650、500、400 W，均高于各式分摊的功率。
`WorkCost` 按每块组织结算，总功率平分；正常运动和出招消耗仍由身体做功预算结算。
重疲劳或重伤使当前可用功率或耐力不足时，做功预检仍返回 `INSUFFICIENT_CAPABILITY`。

| 式 | 每块功率 | 合计功率 | 做功时长 |
|---|---:|---:|---:|
| 撞门 | 100 W | 600 W | 0.9 s |
| 掩门 | 100 W | 600 W | 0.6 s |
| 连门 | 100 W | 600 W | 0.8 s |
| 万门一斩 | 约 166.67 W | 1000 W | 0.4 s |

六种伤害类型分别表达切割或钝击，以及主手、副手或双手出力。Damage 的 `melee`、`kind/cutting`、`kind/blunt`、
`off_hand`、`two_handed` 标签采用 `replace: false`，仅追加本仓类型。`breakable_doors` 默认包含原版木门、木活板门和栅栏门。
主手、副手钝击类型另追加到原版 `bypasses_cooldown`，使间隔 7/9 tick 的叩门三击都完整结算并重新闪红。
该标签对使用这两个伤害类型的其他功法钝击同样生效；切割和双手钝击类型不在此标签中。
三击仍分别经过护甲、身体损伤、溢出蔓延和伤害日志，不绕过盾挡、伤势或其他无敌条件。

`block_items` 是由 Minecraft 1.20.1 注册表导出的静态物品辅助标签，GameTest 会核对标签覆盖完整原版
BlockItem 清单并把清单写到 `gametest/build/block-items.json`。新增模组的方块物品需由整合包补进该标签。
火把虽然属于 BlockItem，仍按其他手持物品造成 0.5 基础伤害；`handheld_misc` 在方块分类前匹配普通、灵魂及红石火把。
铁门与铁活板门不属于可劈门标签。首版不定义 `protected` 标签。

## 音效

八式都用功法库的 `sound` 效果（`SoundCue`）配原版音效，不带音频文件，数值全在目录里。时刻对齐出招
`weapon_strike.at_millis`、冲量时刻和 PA 动画的踏步帧。服务端在该时刻按 `SoundCategory.PLAYERS` 播放，类人与玩家同一路径；
起手被打断后，之后的声音都不再播放。

- `CASTER`：在施放者位置响，挥空也响。挥风声和施放者自己的脚步都用它。
- `TARGET`：紧跟同一时刻的 `weapon_strike`，只在那一击真正造成伤害的目标处响，每个目标一次；挥空不响。
- 破门、万门一斩的劈门仍用原版方块破坏声；破盾打掉盾牌时由原版播放盾牌碎裂声。
- 带 ± 的音调每次播放在该范围内随机。

| 式 | 时刻 ms | 声音（`minecraft:`） | 音量 | 音调 | 锚点 | 用途 |
|---|---:|---|---:|---|---|---|
| 叩门 | 0 | `item.armor.equip_leather` | 0.5 | 0.9 | CASTER | 起手提器 |
| 叩门 | 900 / 1250 | `entity.player.attack.weak` | 0.5 | 0.8 ±0.05 | CASTER | 挥风 |
| 叩门 | 900 / 1250 | `entity.player.attack.knockback` | 0.7 | 0.9 ±0.05 | TARGET | 前两下轻敲 |
| 叩门 | 1700 | `entity.player.attack.weak` | 0.6 | 0.7 ±0.05 | CASTER | 挥风 |
| 叩门 | 1700 | `entity.player.attack.knockback` | 1 | 0.6 ±0.05 | TARGET | 末一下重敲 |
| 破门 | 0 | `item.armor.equip_leather` | 0.6 | 0.8 | CASTER | 举斧 |
| 破门 | 850 | `block.stone.step` | 0.5 | 0.9 | CASTER | 踏步 |
| 破门 | 3200 | `entity.player.attack.sweep` | 0.8 | 0.7 ±0.05 | CASTER | 劈风 |
| 破门 | 3200 | `entity.player.attack.strong` | 1 | 0.8 ±0.05 | TARGET | 劈中 |
| 破门 | 4500 | `block.stone.step` | 0.4 | 0.9 | CASTER | 收步 |
| 撞门 | 0 | `item.armor.equip_leather` | 0.5 | 0.8 | CASTER | 起手 |
| 撞门 | 1550 / 1800 / 2050 | `block.stone.step` | 0.7 | 0.8 / 0.85 / 0.9 ±0.05 | CASTER | 三步冲刺 |
| 撞门 | 2400 | `entity.player.attack.knockback` | 1 | 0.55 ±0.05 | TARGET | 肩撞 |
| 撞门 | 3650 | `block.stone.step` | 0.4 | 0.9 | CASTER | 收步 |
| 破盾 | 0 | `item.armor.equip_leather` | 0.5 | 0.9 | CASTER | 起手 |
| 破盾 | 800 | `block.stone.step` | 0.4 | 0.9 | CASTER | 踏步 |
| 破盾 | 2050 | `entity.player.attack.weak` | 0.6 | 0.7 ±0.05 | CASTER | 挥风 |
| 破盾 | 2050 | `entity.player.attack.strong` | 0.9 | 0.9 ±0.05 | TARGET | 命中 |
| 破盾 | 2400 / 3200 | `block.stone.step` | 0.4 | 0.9 / 0.85 | CASTER | 收步 |
| 横扫 | 0 | `item.armor.equip_leather` | 0.6 | 0.8 | CASTER | 起手 |
| 横扫 | 800 | `block.stone.step` | 0.4 | 0.9 | CASTER | 踏步 |
| 横扫 | 3350 | `entity.player.attack.sweep` | 0.7 | 0.9 ±0.05 | CASTER | 第一扫 |
| 横扫 | 3350 | `entity.player.attack.strong` | 0.8 | 0.9 ±0.05 | TARGET | 第一扫命中 |
| 横扫 | 3700 | `entity.player.attack.sweep` | 0.9 | 0.75 ±0.05 | CASTER | 第二扫 |
| 横扫 | 3700 | `entity.player.attack.strong` | 1 | 0.8 ±0.05 | TARGET | 第二扫命中 |
| 横扫 | 4300 | `block.stone.step` | 0.4 | 0.9 | CASTER | 收步 |
| 掩门 | 0 | `item.armor.equip_leather` | 0.5 | 0.9 | CASTER | 起手 |
| 掩门 | 750 / 1050 | `block.stone.step` | 0.6 | 0.9 / 0.95 ±0.05 | CASTER | 两次后撤蹬地 |
| 掩门 | 2600 | `entity.player.attack.weak` | 0.6 | 0.8 ±0.05 | CASTER | 挥风 |
| 掩门 | 2600 | `entity.player.attack.strong` | 0.9 | 0.9 ±0.05 | TARGET | 命中 |
| 掩门 | 3450 | `block.stone.step` | 0.4 | 0.9 | CASTER | 收步 |
| 连门 | 0 | `item.armor.equip_leather` | 0.6 | 0.8 | CASTER | 起手 |
| 连门 | 1550 / 3050 / 4850 | `block.stone.step` | 0.6 / 0.6 / 0.7 | 0.9 / 0.9 / 0.85 ±0.05 | CASTER | 三次前冲 |
| 连门 | 1700 / 3200 / 5000 | `entity.player.attack.sweep` | 0.7 / 0.7 / 0.9 | 0.9 / 0.85 / 0.75 ±0.05 | CASTER | 三刀劈风 |
| 连门 | 1700 / 3200 / 5000 | `entity.player.attack.strong` | 0.8 / 0.9 / 1 | 0.95 / 0.9 / 0.8 ±0.05 | TARGET | 三刀命中 |
| 连门 | 6350 | `block.stone.step` | 0.4 | 0.9 | CASTER | 收步 |
| 万门一斩 | 0 | `item.armor.equip_leather` | 0.7 | 0.7 | CASTER | 起手（承接时从 1500 ms 起，不响） |
| 万门一斩 | 1850 | `block.stone.step` | 0.5 | 0.9 | CASTER | 踏步 |
| 万门一斩 | 2300 | `entity.player.attack.weak` | 0.6 | 0.8 ±0.05 | CASTER | 前荡挥风 |
| 万门一斩 | 2300 | `entity.player.attack.strong` | 0.7 | 1 ±0.05 | TARGET | 前荡命中 |
| 万门一斩 | 4050 | `entity.rabbit.jump` | 1 | 0.55 | CASTER | 跃起 |
| 万门一斩 | 4700 | `block.stone.step` | 0.7 | 0.8 | CASTER | 落地 |
| 万门一斩 | 4850 | `entity.player.attack.sweep` | 1 | 0.6 ±0.05 | CASTER | 重劈劈风 |
| 万门一斩 | 4850 | `entity.player.attack.strong` | 1 | 0.7 ±0.05 | TARGET | 重劈命中 |
| 万门一斩 | 6550 | `block.stone.step` | 0.4 | 0.9 | CASTER | 收步 |

承接时从 `start_millis` 起播，之前的效果（含 0 ms 的起手声）不执行：叩门承撞门 300 ms，破门、破盾承叩门 400 ms，
撞门承掩门 400 ms，横扫承叩门 500 ms，万门一斩承破门或连门 1500 ms。各式第一个非 0 ms 的声音都晚于这些时刻，
所以连招里只省掉起手声。

## 当前限制

清单与其他模组的清单、编程式目录共同合并校验。书卷和式的定义不会按加载顺序覆盖。
GameTest 使用相同清单声明及正式 `TechniqueFabric.service()`，测试宿主只提供内存身体进度。
目录数据不实现伤害计算、物品耐久、盾牌、玩家挖掘权限、世界方块变更、AI 或动画。
