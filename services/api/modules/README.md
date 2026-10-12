# 后端模块边界与实施入口

当前需求基线为 SRS-OM-001 v1.7.0（Approved），技术设计以 [TDD-OM-002 v1.2.4](../../../../曦元本体平台SDD开发/02_技术设计/TDD-OM-002_对象建模工作台详细设计.md)（Draft）为唯一事实源（TDD-OM-001 主稿文件已不存在，其关键结论——布局独立 layoutRevision、模块化单体、领域=划分维度——已被 TDD-OM-002 吸收）。ADR-OM-001/002 v0.5.0 仍 Proposed，ADR-OM-003 暂缓详细设计；本轮聚焦对象图、属性关系、领域和完整 JSON。

实际 Java 代码放在 ../src/main/java/com/xiyuan/ontology/。对象建模期模块边界（TDD-OM-002 §10.6，Spring Modulith 构建期校验）：

- **modeling.object**：对象图增改删/临时 id、完整 JSON 组装、布局（layout.json 独立 layoutRevision）、文件存储（FileModelRepository + 内存索引 + ModelValidator，单写入权）；端点：/object-model/*、/object-types*、/relationships*。
- **modeling.domaincontext**：领域维度模块——**领域 API 完整实现（GET/POST/PATCH/DELETE /domains，含删除保护）**（对齐 TDD-OM-002 DEV-1：API 完整、前端仅展示面板+内联创建；~~不开放独立领域 CRUD~~ 口径已废弃）；与 modeling.object 经 DomainRegistryPort 接口交互，禁止跨模块 Repository 访问。
- **workspace.commonquery**：/elements/{id}/references|revisions|diff 公共查询（读模型走内存索引）。
- 术语（terminology）、规则（modeling.rule）本轮暂缓，不作为对象工作区前置；检查、报告和复核闭环暂缓。

当前存储以 [STORAGE.md](../STORAGE.md) 的实现契约为准：`ModelStorage` 提供工作空间事务与定义、布局、修订、审计仓储，`JsonModelStorage` 为首个适配器。JSON 数据位于后端平级的 `services/workspace-data/workspaces/<workspaceId>/`；`.store` 的不可变提交与当前版本指针保证事务恢复，根下 JSON 供直接查看。修订按不变的内部 `elementKey` 命名。pom 中 flyway/jOOQ/PostgreSQL 是未来适配器可用的依赖，目前模型存储仍为 JSON。

当前 `modeling/api` 与 `modeling/service` 已实现对象、属性、关系、领域、布局和导出；`modeling/model` 定义存储无关的领域数据，`modeling/storage` 定义仓储与事务接口，`modeling/storage/json` 实现文件适配器。术语、规则及公共查询 API 尚未实施。
