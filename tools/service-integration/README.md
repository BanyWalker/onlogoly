# 运维服务接入模拟数据

在 `platform/` 下运行（需要 Node.js 20+，后端已启动）：

```powershell
node tools/service-integration/seed-ops-services.mjs --dry-run
node tools/service-integration/seed-ops-services.mjs
```

后端地址可用 `--base-url http://127.0.0.1:8080` 指定。脚本通过现有 API 写入当前工作空间；重复运行跳过现有服务和接口，不覆盖用户编辑。API 导入中断后可重新运行补齐缺失接口。

数据定义位于 [service-integrations.mjs](../../ontology/model-packages/ops/fixtures/service-integrations.mjs)，包含 8 个 API 服务、24 个接口和 8 个 MCP 服务、24 个工具，覆盖 CMDB、指标、告警、日志、发布、容器、巡检、备份、预案、容量及工单场景。API 契约含参数、请求体和合成响应示例；MCP 工具含描述、输入模式及读写属性。

数据使用虚构地址和对象标识，服务为草稿，凭证未配置，MCP 工具关闭，未生成连接测试或工具发现记录。这批数据用于目录和详情展示；连接测试与工具调用仍需要接入实际运行的服务。刷新前端目录即可看到新数据，无需重启后端。
