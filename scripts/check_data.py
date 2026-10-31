"""检查正式 Fabric 包的 JSON 数据、标签和硬依赖。"""

from pathlib import Path
import json
import sys


def read_json(path: Path, failures: list[str]):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        failures.append(f"{path}: JSON 无效：{error}")
        return None


def require_tag(tags: dict[str, dict], name: str, expected: set[str], failures: list[str]) -> None:
    tag = tags.get(name)
    if not isinstance(tag, dict):
        failures.append(f"缺少标签：{name}")
        return
    values = tag.get("values")
    if tag.get("replace") is not False or not isinstance(values, list):
        failures.append(f"{name}: 必须有 replace=false 和 values 数组")
        return
    if len(values) != len(set(values)):
        failures.append(f"{name}: values 含重复项")
    missing = expected.difference(values)
    if missing:
        failures.append(f"{name}: 缺少 {sorted(missing)}")


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    resources = root / "fabric/src/main/resources"
    failures: list[str] = []
    json_files = sorted(resources.rglob("*.json"))
    documents = {path.relative_to(resources).as_posix(): read_json(path, failures)
                 for path in json_files}

    metadata = documents.get("fabric.mod.json")
    if isinstance(metadata, dict):
        if metadata.get("environment") != "*":
            failures.append("fabric.mod.json: 卷轴目录必须在两端加载")
        if metadata.get("entrypoints") or metadata.get("mixins"):
            failures.append("fabric.mod.json: 正式数据包不得声明入口或 mixin")
        dependencies = metadata.get("depends", {})
        for dependency in ("meridian_technique", "meridian_vanilla_damage", "meridian_human_host"):
            if dependency not in dependencies:
                failures.append(f"fabric.mod.json: 缺少硬依赖 {dependency}")
        declared = metadata.get("custom", {}).get("meridian_technique:catalogs")
        expected_catalog = "data/meridian_door_breaker/technique_catalog.txtpb"
        if declared != expected_catalog:
            failures.append("fabric.mod.json: 必须声明唯一功法目录清单")
        test_metadata = read_json(root / "gametest/src/main/resources/fabric.mod.json", failures)
        if isinstance(test_metadata, dict) and test_metadata.get("custom") != metadata.get("custom"):
            failures.append("GameTest 必须使用与正式数据包相同的目录清单声明")

    damage_names = {
        "blunt_main_hand", "blunt_off_hand", "blunt_two_handed",
        "cutting_main_hand", "cutting_off_hand", "cutting_two_handed",
    }
    damage_files = sorted((resources / "data/meridian_door_breaker/damage_type").glob("*.json"))
    actual_damage_names = {path.stem for path in damage_files}
    if actual_damage_names != damage_names:
        failures.append(f"伤害类型应恰为六种，实际为 {sorted(actual_damage_names)}")
    for name in damage_names:
        damage = documents.get(f"data/meridian_door_breaker/damage_type/{name}.json")
        if isinstance(damage, dict) and not {"exhaustion", "message_id", "scaling"}.issubset(damage):
            failures.append(f"{name}: 缺少原版伤害类型字段")

    tags: dict[str, dict] = {}
    for path in sorted((resources / "data").rglob("tags/**/*.json")):
        name = path.relative_to(resources / "data").as_posix()
        tag = documents.get(f"data/{name}")
        if isinstance(tag, dict):
            tags[name] = tag

    all_damage = {f"meridian_door_breaker:{name}" for name in damage_names}
    cutting = {f"meridian_door_breaker:cutting_{hand}"
               for hand in ("main_hand", "off_hand", "two_handed")}
    blunt = {f"meridian_door_breaker:blunt_{hand}"
             for hand in ("main_hand", "off_hand", "two_handed")}
    require_tag(tags, "meridian/tags/damage_type/melee.json", all_damage, failures)
    require_tag(tags, "meridian/tags/damage_type/kind/cutting.json", cutting, failures)
    require_tag(tags, "meridian/tags/damage_type/kind/blunt.json", blunt, failures)
    require_tag(tags, "minecraft/tags/damage_type/bypasses_cooldown.json",
                {"meridian_door_breaker:blunt_main_hand", "meridian_door_breaker:blunt_off_hand"}, failures)
    require_tag(tags, "meridian/tags/damage_type/off_hand.json",
                {"meridian_door_breaker:cutting_off_hand", "meridian_door_breaker:blunt_off_hand"}, failures)
    require_tag(tags, "meridian/tags/damage_type/two_handed.json",
                {"meridian_door_breaker:cutting_two_handed", "meridian_door_breaker:blunt_two_handed"}, failures)
    require_tag(tags, "meridian_door_breaker/tags/item/axes.json", {"#minecraft:axes"}, failures)
    require_tag(tags, "meridian_door_breaker/tags/item/handheld_misc.json",
                {"minecraft:torch", "minecraft:soul_torch", "minecraft:redstone_torch"}, failures)
    require_tag(tags, "meridian_door_breaker/tags/item/edged.json",
                {"#minecraft:swords", "minecraft:trident"}, failures)
    require_tag(tags, "meridian_door_breaker/tags/item/melee_tools.json",
                {"#minecraft:swords", "#minecraft:axes", "#minecraft:pickaxes", "#minecraft:shovels",
                 "#minecraft:hoes", "minecraft:trident"}, failures)
    require_tag(tags, "meridian_door_breaker/tags/item/iron_axes.json",
                {"minecraft:iron_axe"}, failures)
    require_tag(tags, "meridian_door_breaker/tags/item/durable_tools.json",
                {"minecraft:iron_sword", "minecraft:netherite_sword"}, failures)
    require_tag(tags, "meridian_door_breaker/tags/block/breakable_doors.json",
                {"#minecraft:wooden_doors", "#minecraft:wooden_trapdoors", "#minecraft:fence_gates"}, failures)

    block_items = tags.get("meridian_door_breaker/tags/item/block_items.json")
    if not isinstance(block_items, dict):
        failures.append("缺少静态 block_items 物品标签")
    else:
        values = block_items.get("values")
        if not isinstance(values, list) or not values:
            failures.append("block_items: values 必须是非空数组")
        elif any(not isinstance(value, str) or value.startswith("#") or ":" not in value
                 for value in values):
            failures.append("block_items: 只能包含显式物品 ID")
        elif len(values) != len(set(values)):
            failures.append("block_items: values 含重复项")

    catalog = root / "shared/src/main/resources/data/meridian_door_breaker/technique_catalog.txtpb"
    catalog_sources = list((root / "shared/src/main/resources").rglob("*.txtpb"))
    if not catalog.is_file() or catalog_sources != [catalog]:
        failures.append("功法 txtpb 必须只有 shared 中这一份目录")
    elif "protected" in catalog.read_text(encoding="utf-8"):
        failures.append("首版目录不得定义 protected 物品标签")

    if failures:
        print("数据检查失败：\n" + "\n".join(failures), file=sys.stderr)
        return 1
    print(f"数据检查通过：{len(json_files)} 个 JSON、六种伤害类型和全部标签。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
