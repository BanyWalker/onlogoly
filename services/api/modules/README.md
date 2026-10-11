# 后端模块边界与实施入口

当前需求基线为 SRS-OM-001 v1.7.0（Approved），技术设计以 [TDD-OM-002 v1.2.4](../../../../曦元本体平台SDD开发/02_技术设计/TDD-OM-002_对象建模工作台详细设计.md)（Draft）为唯一事实源（TDD-OM-001 主稿文件已不存在，其关键结论——布局独立 layoutRevision、模块化单体、领域=划分维度——已被 TDD-OM-002 吸收）。ADR-OM-001/002 v0.5.0 仍 Proposed，ADR-OM-003 暂缓详细设计；本轮聚焦对象图、属性关系、领域和完整 JSON。

实际 Java 代码放在 ../src/main/java/com/xiyuan/ontology/。对象建模期模块边界（TDD-OM-002 §10.6，Spring Modulith 构建期校验）：

- **modeling.object**：对象图增改删/临时 id、完整 JSON 组装、布局（layout.json 独立 layoutRevision）、文件存储（FileModelRepository + 内存索引 + ModelValidator，单写入权）；端点：/object-model/*、/object-types*、/relationships*。
- **modeling.domaincontext**：领域维度模块——**领域 API 完整实现（GET/POST/PATCH/DELETE /domains，含删除保护）**（对齐 TDD-OM-002 DEV-1：API 完整、前端仅展示面板+内联创建；~~不开放独立领域 CRUD~~ 口径已废弃）；与 modeling.object 经 DomainRegistryPort 接口交互，禁止跨模块 Repository 访问。
- **workspace.commonquery**：/elements/{id}/references|revisions|diff 公共查询（读模型走内存索引）。
- 术语（terminology）、规则（modeling.rule）本轮暂缓，不作为对象工作区前置；检查、报告和复核闭环暂缓。

首期持久化为本地 JSON 文件（用户确认，TDD-OM-002 DEV-4/§10）：data/workspaces/<workspaceId>/ 五文件布局（object-model.json 主文档权威事实源 + layout.json + revisions/<revSeq>.jsonl + review-flags.json + events.jsonl），原子写 + 工作空间锁 + 启动自检；pom 中 flyway/jOOQ/PostgreSQL 为目标态依赖（数据库迁移方向），MVP 不激活。修订文件按内部代理键 revSeq 命名以支撑标识可编辑。

当前未形成术语、对象、规则业务 Controller/Repository 或业务迁移。实际业务代码按 TDD-OM-002 §10.6 包结构与 §13 切片在实施阶段完成。
