# Java 后端框架来源与落地记录

检索和落地日期：2026-10-09。目标是为已有 Vue 3 前端准备 Java 后端基础工程，便于后续模块化开发。

## 选择

选择 **Spring Boot 4.1.1 + Spring Modulith 2.1.1 + Java 21**，从官方 Spring Initializr 下载可构建的 Maven 工程，落入 `../../services/api/`。

| 候选 | 官方来源 | 适配判断 |
| --- | --- | --- |
| Spring Boot + Spring Modulith | [Spring Boot](https://github.com/spring-projects/spring-boot)、[Spring Modulith](https://github.com/spring-projects/spring-modulith)、[Initializr](https://start.spring.io/) | 推荐；支持独立 REST 服务、模块化单体、Spring Security 与 jOOQ/PostgreSQL，不绑定管理系统业务模型 |
| RuoYi-Vue-Plus | [dromara/RuoYi-Vue-Plus](https://github.com/dromara/RuoYi-Vue-Plus) | 官方 6.X 分支同样采用 Java 21 / Boot 4.1.1，具有多租户、权限与后台管理能力；其 Sa-Token、MyBatis-Plus 和内置治理约定与当前 Spring Security/jOOQ 方向不同，暂未引入 |

该选择适合逐步建立本体业务模块。若后续需要直接复用完整的用户、租户、菜单和工作流系统，应重新比较后台快速开发平台的收益与迁移成本。

## 官方证据与版本

- [Spring Initializr 元数据](https://start.spring.io/metadata/client)：检索时默认稳定版本为 `4.1.1.RELEASE`，存在 4.2 预览和快照版本；本次未采用预览/快照。
- [Spring Boot 4.1.1 Release](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.1)：官方稳定发行记录。
- [Spring Boot 4.1 系统要求](https://docs.spring.io/spring-boot/4.1/system-requirements.html)：支持 Java 21，使用 Spring Framework 7 系列。
- [Maven Central 中的 4.1.1 parent](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/4.1.1/spring-boot-starter-parent-4.1.1.pom)：构建依赖坐标。
- Spring Modulith `2.1.1` 由这次 Initializr 工程的 BOM 配置给出；Maven 构建负责验证依赖可解析。

原始生成器 ZIP 的 parent 为 `4.1.1.RELEASE`，本地已按 Maven Central 的实际坐标规范化为 `4.1.1`；这项适配记录在 `source-manifest.json` 中。

## 本地资产

| 文件 | 用途 |
| --- | --- |
| `spring-initializr-ontology-api-boot-4.1.1.zip` | 未修改的官方生成器原始工程 |
| `source-manifest.json` | 完整下载 URL、请求参数、原包 SHA-256 和本地适配说明 |
| `apache-maven-3.9.16-bin.zip` | 官方 Maven 发行原包，用于准备本机 Wrapper 缓存 |
| `apache-maven-3.9.16-bin.zip.sha512` | 从 Maven Central 获取的原包校验值 |
| `prepare-maven.ps1` | 校验本地原包并准备 Wrapper 缓存，不更改全局 Maven 配置 |
| `spring-boot-LICENSE.txt` | Spring Boot 官方 Apache-2.0 许可证文本 |
| `spring-modulith-LICENSE.txt` | Spring Modulith 官方 Apache-2.0 许可证文本 |
| `THIRD-PARTY-NOTICES.md` | 框架和构建组件的来源、版本、许可证说明 |
| `verification.md` / `runtime-smoke-result.json` | 编译、测试、打包和真实 HTTP 启动验证记录 |

原始包与适配后的工程分开保存。校验值用于定位下载版本和检测内容变更；项目自身业务源码的授权策略需要独立确定。

## 本地验证

在 Java 21 环境执行 `../../services/api/mvnw.cmd -B -ntp verify`。验证结果记录在 [verification.md](./verification.md)。本次只验证框架基础，不验证后续本体业务、生产数据库和真实身份平台。
