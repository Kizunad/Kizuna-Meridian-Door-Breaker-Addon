# 仓库协作约定

- 简体中文沟通、文档和注释；遵守 `~/.codex/AGENTS.md` 的 Java 代码形状规则。
- 本仓只提供破门斩目录与数据包；正式 Fabric 包无入口、无 Java 类，不实现宿主规则。
- `shared/` 保存唯一 `.txtpb` 清单；`fabric/` 保存伤害类型和标签；`verification/`、`gametest/` 仅用于验证。
- 直接使用相邻功法库当前协议，不复制协议源码，不修改依赖仓库。
- 所有数值均为占位。冷却从收势结束计算，按 `dedup_group` 去重，首版不做 `protected`。
- `docs/wiki/` 为文档维护源，README 仅作入口；接口缺口必须如实记录。
- 每个 Java 方法和构造器内部写中文意图注释；公开标识符用英文。
- 交付运行文档检查和 `git diff --check`。
- 未经用户明确授权不提交、不推送、不建远端；保留上游未提交改动。
- 原创部分采用 PolyForm Noncommercial 1.0.0，保留第三方许可。

## 构建和测试

构建、测试一律走工作区的 `kz`（`~/Code/Kizuna-remote-session/scripts/kz`），不直接运行 `./gradlew`。细则以 `~/Code/Kizuna-remote-session/handoff/codex-worker-rules.md` 为准，动手前先读。

- 单测、构建和服务端 GameTest：`kz test ~/Code/<本仓的 worktree>`
- 要带上别的仓的 worktree 一起测：每个加一次 `--with ~/Code/<那个仓的 worktree>`
- 客户端测试：`kz client ~/Code/<本仓的 worktree>` 列出有哪些项，`kz client ~/Code/<本仓的 worktree> <项>` 跑其中一项
- 要自己写 Gradle 参数时：`kz gradle ~/Code/<本仓的 worktree> -- <参数>`
- 不提交、不推送；在自己的 worktree（`~/Code/wt-*`）里改，不改主目录。
