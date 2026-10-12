# 本地工作空间数据

本目录与后端代码 `api/` 平级。业务数据存放在 `workspaces/<workspaceId>/`，可直接用编辑器查看，不必进入后端源码。真实数据和迁移备份不纳入 Git。

| 文件 | 内容 |
| --- | --- |
| `object-model.json` | 当前对象、属性、关系、领域定义 |
| `layout.json` | 画布位置与独立布局修订号 |
| `elements.json` | 不变的内部元素键与当前业务 ID，含删除标记 |
| `revisions/<elementKey>.jsonl` | 元素修订历史，改业务 ID 后仍使用同一文件 |
| `events.jsonl` | 审计事件 |
| `review-flags.json` | 预留复核数据，迁移时保留 |
| `.store/current.json` | 当前已提交版本指针 |
| `.store/commits/` | 不可变事务版本，包括定义、布局和本次新增历史、审计 |

根目录的 JSON 是便于查看的当前版本副本；`.store` 是事务恢复依据。请通过 API 修改数据，直接编辑副本不会修改已提交版本，重启或下一次保存会重新生成副本。查看布局请打开 `layout.json`；对外完整 JSON 导出仍包含对象的 `graph` 坐标。

备份时停止后端并复制整个工作空间目录，包括 `.store`。只复制 `object-model.json` 无法保留布局、内部键和历史。目录应放在支持原子重命名的本地磁盘上；文件模式一次只允许一个后端实例访问同一工作空间。

从 `api/` 启动后端时默认使用本目录。其他启动位置应通过 `XIYUAN_DATA_DIR` 指定本目录的绝对路径，或通过 `XIYUAN_WORKSPACE_DIR` 指定完整工作空间路径。详见 [存储设计](../api/STORAGE.md)。
