# 曦元本体平台 Java 后端基础工程

当前对象建模使用可替换的存储接口与 JSON 适配器，实际数据位于后端平级的 [workspace-data](../workspace-data/README.md)。存储契约、配置、事务恢复和迁移步骤见 [STORAGE.md](./STORAGE.md)。

此目录是从 [Spring Initializr](https://start.spring.io/) 下载并适配的 Java 后端工程，供后续术语、对象、规则和公共建模能力开发使用。当前只提供框架、配置和基础验证，尚无本体业务接口、业务表或审批流程。

## 技术基线

| 能力 | 版本/组件 | 当前用途 |
| --- | --- | --- |
| Java | 21 LTS | 与本机已安装 JDK 一致 |
| Spring Boot | 4.1.1，官方稳定发行版 | 启动、依赖管理、REST、配置与健康检查 |
| Spring Modulith | 2.1.1，Initializr 配套版本 | 模块边界及后续模块测试 |
| 构建 | Maven Wrapper 3.3.4 / Maven 3.9.16 | Windows、Linux、macOS 使用同一构建版本 |
| Web | Spring MVC、Bean Validation | REST API 和入参校验基础 |
| 安全 | Spring Security、OAuth2 Resource Server | OIDC/JWT 身份集成基础 |
| 数据访问 | jOOQ、PostgreSQL Driver | 显式启用 PostgreSQL 配置后使用 |
| 数据迁移 | Flyway、PostgreSQL 适配 | 已纳入依赖；按现有约定由部署显式执行 |
| 运维 | Actuator | 最小健康检查 |

依赖版本由 Spring Boot parent 与 Spring Modulith BOM 管理，详见 [pom.xml](./pom.xml)。不导入另一套前端工程，不内置用户表、租户表或演示业务。

## 快速开始

Windows PowerShell：

```powershell
Set-Location 'D:\code\本体平台\platform\services\api'
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
sh mvnw -B -ntp verify
sh mvnw spring-boot:run
```

默认监听 `127.0.0.1:8080`，无需安装数据库或配置身份平台即可启动基础工程。检查：

```powershell
curl.exe --fail http://127.0.0.1:8080/actuator/health
```

预期返回 `{"status":"UP"}`。当前 MVP 默认放行健康检查和 `/api/**`，无需登录；显式启用 `oidc` profile 后，业务 API 需要有效 JWT。前端位于 `../../apps/x-yuan-ontology-web/`，Vite 默认把 `/api` 代理至 `http://127.0.0.1:8080`。

API 服务和 MCP 服务的列表接口分别是 `/api/v1/integrations/api-services` 和 `/api/v1/integrations/mcp-servers`。当前未配置 Java 热更新；新增或修改后端代码后，需停止并重新执行 `spring-boot:run`，否则原进程不会加载新接口。未知 API 路径应返回 404，内部错误分派保留原始错误状态。

## 配置与启用方式

| 配置 | 作用 |
| --- | --- |
| `application.properties` | 基础模式：关闭数据源自动创建、关闭自动迁移、仅暴露健康端点 |
| `application-postgres.properties` | `postgres` profile：读取数据库环境变量，启用 PostgreSQL/jOOQ |
| `application-oidc.properties` | `oidc` profile：读取真实身份平台 issuer 和 audience，启用 JWT 校验 |
| `.env.example` | 环境变量参考；Spring 不会自动加载 `.env` 文件 |

启用数据库与身份平台前，在 IDE 运行配置或当前 PowerShell 会话中设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`OIDC_ISSUER_URI`、`OIDC_AUDIENCE`，然后运行：

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgres,oidc'
```

身份校验仍需在业务开发时补齐工作空间隔离、角色/资源授权；认证成功不等于具备模型编辑权限。Flyway 已作为依赖准备，但不会随应用启动执行。业务迁移仍归 `../../database/migrations/`，后续实施时再接入可追溯的部署迁移步骤。

## 模块组织

```text
api/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
├── modules/                  # 已有领域边界占位，见 modules/README.md
├── src/main/java/com/xiyuan/ontology/
│   ├── OntologyApiApplication.java
│   └── configuration/        # 基础及 OIDC 安全配置
├── src/main/resources/
│   └── application*.properties
└── src/test/java/com/xiyuan/ontology/
    └── OntologyApiApplicationTests.java
```

当前需求基线为 SRS-OM-001 v1.7.0（Approved），技术设计以 [TDD-OM-002 v1.2.4](../../../曦元本体平台SDD开发/02_技术设计/TDD-OM-002_对象建模工作台详细设计.md)（Draft）为唯一事实源（TDD-OM-001 主稿文件已不存在、其结论已被吸收），ADR-OM-001/002 v0.5.0 仍待批准。对象建模期模块边界按 TDD-OM-002 §10.6：modeling.object（图命令、完整 JSON 组装、布局 Repository、文件存储）+ modeling.domaincontext（领域 API 完整实现，DEV-1）+ workspace.commonquery，Spring Modulith 构建期校验，禁止跨模块 Repository 访问。定义、领域和布局原子提交，纯布局独立修订（layout.json 唯一坐标源）；数据写入权、存储五文件布局与完整 JSON 结构见 TDD-OM-002 §10/§5。本轮对象优先，规则/术语原设计保留，领域专门管理及检查/报告/核查暂缓。

## 验证与打包

```powershell
.\mvnw.cmd -B -ntp verify
java -jar .\target\ontology-api-0.0.1-SNAPSHOT.jar
```

基础验证检查无数据库模式的健康状态、MVP API 的匿名访问、未知 API 路径的 404 响应、非公开路径的拒绝行为，以及 Spring Modulith 模块边界。服务接入访问回归可单独运行 `./mvnw.cmd -B -ntp -Dtest=IntegrationAccessTests test`。数据库连通性、真实 OIDC 集成、业务授权和业务场景验收应在后续环境与需求就绪后单独验证。

2026-10-09 已完成当前工程的离线 `verify` 和可执行 JAR 的真实 HTTP 启动检查，详见 [验证记录](../../tools/backend-bootstrap/verification.md)。

首次构建需要下载 Maven 及依赖，此次落地已准备当前用户的 Maven Wrapper 缓存。若 Windows Wrapper 下载受网络影响，可运行 [prepare-maven.ps1](../../tools/backend-bootstrap/prepare-maven.ps1)，利用本地经过校验的 Maven ZIP 准备缓存；脚本不修改全局 Maven 配置。完成一次在线构建不代表所有未来新增依赖都能离线使用。

## 来源与设计关系

下载日期：2026-10-09。原始 ZIP、请求参数、SHA-256 和 Maven 原包位于 [backend-bootstrap](../../tools/backend-bootstrap/README.md)。框架、Modulith 与 Maven 使用 Apache-2.0，原始许可证和组件说明也保存在该目录。

工程声明 Spring Boot 4.1.1 / Spring Modulith 2.1.1 / Java 21，TDD v1.3.0 §1.5 引用的是工程事实，兼容性仍须实施验证。拟新增本地预置账号、会话 Cookie/CSRF 和 Spring Session JDBC，与 bootstrap/OIDC 互斥；当前代码尚未提供 local 模式，启动示例不代表本地账号已实现。OpenAPI/schema、迁移和授权仍需按批准设计实施。
