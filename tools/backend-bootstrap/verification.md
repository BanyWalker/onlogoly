# Java 后端基础工程验证记录

日期：2026-10-09。环境：Windows 11、Eclipse Temurin Java 21.0.11、Maven 3.9.16。对象：Spring Boot 4.1.1 / Spring Modulith 2.1.1 基础工程。

## 已完成检查

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 官方 Initializr 原包 | 通过 | 原包 SHA-256 与 `source-manifest.json` 一致 |
| Maven 发行原包 | 通过 | SHA-512 与 Maven Central 原始校验文件一致；SHA-256 固定在 Wrapper 配置 |
| Windows 本地 Maven 准备脚本 | 通过 | 在独立临时缓存运行脚本后，Wrapper 能输出 Maven 3.9.16 / Java 21.0.11 |
| Java 主代码与测试编译 | 通过 | Maven compiler，Java release 21 |
| Spring 应用上下文与健康端点 | 通过 | 无数据库、无身份平台配置时 `/actuator/health` 返回 UP，未暴露组件细节 |
| 基础模式 API 访问控制 | 通过 | `/api/v1/models` 未授权请求返回 401 |
| 模块依赖边界 | 通过 | `ApplicationModules.of(OntologyApiApplication.class).verify()` |

自动化测试：3 项，失败 0、错误 0、跳过 0。报告：`../../services/api/target/surefire-reports/TEST-com.xiyuan.ontology.OntologyApiApplicationTests.xml`（构建产物，不作为业务验收基线）。

## 打包与运行检查

最终通过的构建命令：在 `platform/services/api/` 执行 `./mvnw.cmd -o -B -ntp verify '-Ddebug=false'`，结果为 `BUILD SUCCESS`，3 项测试通过并生成可执行 JAR。`-o` 验证当前框架版本与构建所需依赖已缓存；新增依赖仍可能需要在线下载。

初次在线构建完成编译与测试后，Maven Central 的 `org.ow2.asm:asm:9.7` 打包依赖下载超时。使用 Maven Central 的 Google 镜像获取该 JAR，并与 `repo1.maven.org` 提供的官方校验值比对后准备缓存，随后离线构建成功。来源与 SHA-256 见 `source-manifest.json` 的 `packagingDependencyRecovery`；未修改全局仓库配置。

运行产物：`../../services/api/target/ontology-api-0.0.1-SNAPSHOT.jar`。通过 `java -jar` 在 `127.0.0.1` 的临时可用端口启动独立进程，完成以下真实 HTTP 检查：

| 路径 | HTTP 状态 | 结果 |
| --- | --- | --- |
| `/actuator/health` | 200 | `status=UP` |
| `/actuator/health/readiness` | 200 | `status=UP` |
| `/api/v1/models` | 401 | 未启用身份平台时拒绝业务路径访问 |
| `/actuator/env` | 401 | 管理详情未向匿名用户开放 |

检查完成后已停止该测试进程。结果、端口与产物 SHA-256 保存在 [runtime-smoke-result.json](./runtime-smoke-result.json)；运行日志位于 `../../services/api/target/runtime-smoke.log`（构建产物）。

## 覆盖范围

这是框架落地验证，未连接生产数据库、未连接真实 OIDC 身份平台、未执行业务数据库迁移，也未验证术语、对象或规则的业务行为。后续业务实现按当前 SRS 与独立治理需求建立验证证据。
