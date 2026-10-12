# 可替换存储设计与迁移

本次实现把存储依赖收敛到 `modeling.storage.ModelStorage`。当前注册 `JsonModelStorage`，前端、Controller、接口路径和完整 JSON 导出格式保持兼容。PostgreSQL/图数据库适配器仍需实现后才能选择；`postgres` profile 只启用数据源，并不自动替换模型存储。

## 代码边界

```text
Controller / ModelingService
  -> ModelDocument（请求草稿、字段校验、API JSON 组装、变更追踪）
  -> ModelStorage.read / write(workspaceId, session)
     -> objects / relations / domains : DefinitionRepository
     -> layout                       : LayoutRepository
     -> revisions                    : RevisionRepository
     -> audit                        : AuditLog
  -> JsonModelStorage（当前） / PostgreSQL / 图数据库（后续）
```

存储接口没有 Jackson、文件路径、SQL 或图数据库对象。`ModelDefinition` 包含不变的 UUID `elementKey`、元素类别、业务 ID、定义修订号以及不可变的领域字段；对象属性和约束作为对象定义聚合。字段保留嵌套结构、空值、扩展字段和十进制精度。`ModelJsonCodec` 负责与现有 API JSON 转换，新增字段不必逐字段修改仓储接口。

`ModelDocument` 是业务层单次命令的草稿映射，不是存储主文档。按 ID 查询走仓储，按领域查询有独立接口；写入只保存本次发生变化的元素。JSON 适配器在内存中索引 ID；数据库适配器可直接执行查询并按元素写入。图数据库须将关系定义视为具有独立 ID、修订号、基数和多目标声明的元素，不能直接降级为没有身份的普通边。

目前部分校验、改名引用扫描和画布查询仍使用 `findAll()`；数据规模增长后可添加引用查询、分页和图投影接口，数据库切换本身不需要要求整个工作空间作为一个 JSON 文档加载。

## 事务契约

- `read` 给出一致快照，`write` 在一个工作空间内序列化命令。定义、必要的布局变化、修订、审计和复核字段在同一提交中成功或失败。
- 回调或提交失败不改变已提交状态；乐观锁检查也在写事务内。仓储只能在会话回调期间使用，读会话不能写，返回的模型不可变。
- 工作空间提交版本递增；布局有独立修订。纯布局保存不增加定义修订，不产生定义历史；创建对象或对象改名会同步布局并增加布局修订。
- `elementKey` 持久化且不随业务 ID 改变。改名同步更新引用、布局映射及受影响定义历史；删除关系/领域保留内部键墓碑和历史，同名重建使用新键。
- JSON 文件模式使用进程内锁和操作系统文件锁，同一工作空间只允许一个实例；暂只配置一个活动工作空间。
- 数据库实现需要用原生事务和工作空间行锁/等效机制实现相同语义，并共享 `StorageContractTests` 验证。不要在服务中添加数据库专属分支。

## JSON 提交与查看

默认数据目录为 `services/workspace-data/`，与后端 `services/api/` 平级。`workspaces/ENV-OM-001/` 根下可查看定义、布局、内部键、复核、修订和审计，详见 [数据目录说明](../workspace-data/README.md)。

每次提交先在 `.store/staging-<uuid>/` 写完整定义、布局、内部键、复核数据，以及本次新增修订/审计批次；文件强制刷新后生成带 SHA-256 的清单。目录通过原子重命名进入 `.store/commits/<uuid>/`，最后原子替换 `.store/current.json` 指向新提交，才更新内存并返回成功。文件系统不支持原子重命名时拒绝提交，不回退到非原子覆盖。

启动只沿 `current.json` 指定的父提交链读取，验证版本、工作空间和校验和；指针发布前遗留的 staging/孤立提交不会生效。根目录 JSON 是查看副本，提交成功后生成，副本刷新失败不会把已成功提交报告为失败；启动时重新生成。权威提交损坏则拒绝启动并报告文件，不用种子覆盖。直接编辑查看副本不会修改权威提交。

定义文件不存布局，API 导出仍组装 `graph`。备份/迁移必须包含 `.store`，不能只取查看副本。当前保留所有不可变提交并在启动时验证历史链；工作空间很大或历史增长后需增加受控归档/压缩，当前未自动清理已提交历史。保证正常进程异常后的提交恢复；整机断电和存储设备缓存失效还需部署环境与备份策略保障。

## 配置与旧数据移动

从 `services/api/` 执行 `mvnw.cmd spring-boot:run`，默认读取 `../workspace-data/workspaces/ENV-OM-001/`。从其他目录启动或部署 JAR 时使用绝对路径：

```powershell
$env:XIYUAN_DATA_DIR = 'D:\code\本体平台\platform\services\workspace-data'
$env:XIYUAN_STORAGE_BACKEND = 'json'
.\mvnw.cmd spring-boot:run
```

`XIYUAN_WORKSPACE_ID` 指定活动工作空间；保留 `XIYUAN_WORKSPACE_DIR` 作为完整路径覆盖项。Spring 不自动加载 `.env`。

旧目录迁移应先停止后端写入，从 `platform/` 运行：

```powershell
.\tools\storage\move-workspace-data.ps1
```

脚本复制原工作空间到平级目录，同时生成 `migration-backups/`，逐文件 SHA-256 校验并拒绝覆盖已有目标。源文件保留到新后端验收完成。首次加载旧 JSON 时，适配器保留模型字段、定义修订、独立布局修订、复核、历史和审计，为元素生成并持久化内部键，然后一次性提交。历史中已不在当前模型的元素也保留墓碑。旧格式没有改名映射时，无法自动证明两个旧 ID 属于同一元素，不猜测合并历史。

## 后续数据库适配

1. 实现 `ModelStorage` 及会话仓储，用数据库事务、索引和工作空间锁保证契约。通过 `@ConditionalOnProperty(name="xiyuan.storage.backend", havingValue="postgres")` 或具体图数据库名称选择适配器。
2. `StorageSnapshots.capture` 已提供同一读会话的迁移快照，包含内部键、定义/布局修订、复核、墓碑、全部历史和审计。`SnapshotJsonCodec` 编解码 `xiyuan-storage-snapshot` 格式（formatVersion=1）；后续实现目标数据库导入器。前端业务导出不包含完整存储元数据，不能直接作为数据库迁移包。
3. 暂停写入，导入新存储，校验数量、内部键、引用、历史、修订、布局和导出结果，再切换配置。切换前保留旧目录备份。
4. 继承 `StorageContractTests` 验证事务回滚、并发修订、历史身份和布局语义，并增加数据库特有集成测试。

PostgreSQL 可用元素主表与 JSONB 定义载荷、引用索引、布局/历史/审计表；图数据库可用对象/关系定义节点和引用边保留同样的身份与语义。切换所需改动集中在适配器、建表/索引和迁移器，不承诺只改几行配置即可完成新的数据库实现。

## 验证

```powershell
.\mvnw.cmd -B -ntp verify
```

测试使用临时工作空间，不写真实数据。公共契约测试覆盖跨仓储事务、失败回滚、乐观锁、并发、改名、删除同名重建和会话隔离；JSON 测试额外覆盖发布故障注入、副本恢复、文件锁、校验和、旧格式导入、扩展字段与布局分离。
