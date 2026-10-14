// 运维领域合成数据；所有地址、资产标识和响应示例均为虚构。
const source = "FIX-SI-OPS-001 运维服务接入合成案例";
const auth = (authType = "BEARER") => ({ authType, configured: false, masked: "未配置" });
const str = (description, example, extra = {}) => ({ type: "string", description, examples: [example], ...extra });
const integer = (description, example, extra = {}) => ({ type: "integer", description, examples: [example], ...extra });
const environment = str("运行环境", "production", { enum: ["production", "staging", "test"] });
const serviceId = str("业务服务标识", "SVC-PAYMENT");
const hostId = str("主机资产标识", "HOST-OPS-001");
const clusterId = str("集群标识", "K8S-OPS-DEMO");
const namespace = str("命名空间", "payment");
const startTime = str("开始时间（含时区）", "2026-10-13T09:00:00+08:00", { format: "date-time" });
const endTime = str("结束时间（含时区）", "2026-10-13T10:00:00+08:00", { format: "date-time" });
const limit = integer("返回记录上限", 20, { minimum: 1, maximum: 100, default: 20 });
const objectSchema = (properties, required = []) => ({ type: "object", properties, required, additionalProperties: false });
const query = (name, schema, required = false) => ({ name, in: "query", required, description: schema.description, schema });
const pathParam = (name, schema) => ({ name, in: "path", required: true, description: schema.description, schema });

function schemaFor(value) {
	if (Array.isArray(value)) return { type: "array", items: value.length ? schemaFor(value[0]) : { type: "object" } };
	if (value !== null && typeof value === "object") return objectSchema(Object.fromEntries(Object.entries(value).map(([key, item]) => [key, schemaFor(item)])), Object.keys(value));
	return { type: typeof value === "number" ? (Number.isInteger(value) ? "integer" : "number") : typeof value };
}

function endpoint(id, name, definition, method, path, { parameters = [], body, sample, success = "200" }) {
	const example = { code: "OK", data: sample, traceId: "mock-ops-001" };
	return {
		id, name, definition, method, path, operationNature: method === "GET" ? "READ" : "WRITE",
		timeoutMs: 10000, status: "DRAFT", enabled: false, revision: 1, lastTest: null,
		contract: {
			parameters,
			requestBody: body ? { required: true, content: { "application/json": { schema: body, example: Object.fromEntries(Object.entries(body.properties).map(([key, value]) => [key, value.examples?.[0] ?? value.default])) } } } : {},
			responses: {
				[success]: { description: "成功（合成响应示例）", content: { "application/json": { schema: schemaFor(example), example } } },
				"400": { description: "请求参数无效" }, "401": { description: "未认证或凭证无效" }, "500": { description: "运维服务内部异常" }
			}
		}
	};
}

function api(id, name, definition, owner, slug, endpoints, authType = "BEARER") {
	return {
		id, name, definition: `${definition}（运维合成示例）`, domainId: "DOMAIN-OPS", owner, source,
		version: "v1", baseUrl: `https://${slug}.example.invalid`, environment: "测试", timeoutMs: 10000,
		status: "DRAFT", enabled: false, auth: auth(authType), endpoints
	};
}

export const apiServices = [
	api("API-CMDB-ASSET", "CMDB 资产查询服务", "查询主机资产、服务实例和应用依赖，支撑故障定位与影响分析", "配置管理团队", "cmdb-api", [
		endpoint("EP-CMDB-HOSTS", "查询主机资产", "按环境和主机状态分页查询资产清单", "GET", "/api/v1/hosts", { parameters: [query("environment", environment), query("limit", limit)], sample: { total: 2, items: [{ hostId: "HOST-OPS-001", hostname: "payment-node-01", ip: "192.0.2.11", environment: "test", status: "RUNNING", cpuCores: 16, memoryGb: 64 }] } }),
		endpoint("EP-CMDB-INSTANCES", "查询服务实例", "查询业务服务的实例部署、健康状态和所属主机", "GET", "/api/v1/services/{serviceId}/instances", { parameters: [pathParam("serviceId", serviceId)], sample: { serviceId: "SVC-PAYMENT", instances: [{ instanceId: "INST-PAY-001", hostId: "HOST-OPS-001", health: "HEALTHY", version: "2.6.1" }] } }),
		endpoint("EP-CMDB-DEPENDENCIES", "查询应用依赖", "读取应用的上下游服务依赖及数据库关联", "GET", "/api/v1/applications/{applicationId}/dependencies", { parameters: [pathParam("applicationId", str("应用标识", "APP-PAYMENT"))], sample: { applicationId: "APP-PAYMENT", nodes: ["SVC-PAYMENT", "SVC-ORDER", "DB-PAYMENT"], edges: [{ source: "SVC-PAYMENT", target: "DB-PAYMENT", relation: "CONNECTS_TO" }] } })
	], "API_KEY"),
	api("API-OBS-METRICS", "监控指标查询服务", "读取主机与服务时序指标、可用性和 SLO，支撑运行态评估", "可观测平台团队", "metrics-api", [
		endpoint("EP-METRICS-RANGE", "查询指标时序", "按指标表达式和时间范围查询时序数据", "GET", "/api/v1/metrics/range", { parameters: [query("query", str("指标查询表达式", "cpu_usage_percent{service_id=\"SVC-PAYMENT\"}"), true), query("start", startTime, true), query("end", endTime, true)], sample: { metric: "cpu_usage_percent", unit: "%", points: [{ timestamp: "2026-10-13T09:00:00+08:00", value: 68.5 }] } }),
		endpoint("EP-METRICS-HEALTH", "查询服务健康", "汇总服务请求量、错误率和 P95 延迟", "GET", "/api/v1/services/{serviceId}/health", { parameters: [pathParam("serviceId", serviceId)], sample: { serviceId: "SVC-PAYMENT", health: "DEGRADED", requestRate: 1250, errorRate: 0.012, p95LatencyMs: 320 } }),
		endpoint("EP-METRICS-SLO", "查询 SLO 达成率", "查询服务可用性目标、达成率和错误预算", "GET", "/api/v1/services/{serviceId}/slo", { parameters: [pathParam("serviceId", serviceId), query("window", str("统计窗口", "7d", { enum: ["1d", "7d", "30d"] }))], sample: { target: 99.95, availability: 99.97, errorBudgetRemainingPercent: 60 } })
	]),
	api("API-ALERT-CENTER", "告警事件管理服务", "查询告警事件、读取关联详情并确认接单，串联异常发现与处置", "监控值班团队", "alerts-api", [
		endpoint("EP-ALERT-LIST", "查询活动告警", "按严重级别、服务和状态筛选告警", "GET", "/api/v1/alerts", { parameters: [query("serviceId", serviceId), query("severity", str("严重级别", "CRITICAL", { enum: ["CRITICAL", "MAJOR", "MINOR", "INFO"] })), query("limit", limit)], sample: { total: 1, items: [{ alertId: "ALERT-OPS-001", name: "支付服务错误率升高", severity: "CRITICAL", serviceId: "SVC-PAYMENT", status: "OPEN" }] } }),
		endpoint("EP-ALERT-DETAIL", "读取告警详情", "查询告警触发规则、影响实例和处置时间线", "GET", "/api/v1/alerts/{alertId}", { parameters: [pathParam("alertId", str("告警标识", "ALERT-OPS-001"))], sample: { alertId: "ALERT-OPS-001", rule: "error_rate > 1% for 5m", instanceIds: ["INST-PAY-001"], triggeredAt: "2026-10-13T09:10:00+08:00", status: "OPEN" } }),
		endpoint("EP-ALERT-ACK", "确认告警接单", "登记告警处理人及接单说明", "POST", "/api/v1/alerts/{alertId}/acknowledgements", { parameters: [pathParam("alertId", str("告警标识", "ALERT-OPS-001"))], body: objectSchema({ operator: str("接单人标识", "ops-demo"), comment: str("接单说明", "已接单，排查数据库连接池") }, ["operator"]), sample: { alertId: "ALERT-OPS-001", status: "ACKNOWLEDGED", operator: "ops-demo" }, success: "201" })
	]),
	api("API-LOG-SEARCH", "日志检索服务", "统一检索应用日志、聚合错误模式并读取调用链日志", "日志平台团队", "logs-api", [
		endpoint("EP-LOG-SEARCH", "检索应用日志", "按服务、关键词与时间区间检索日志", "GET", "/api/v1/logs", { parameters: [query("serviceId", serviceId, true), query("keyword", str("日志关键词", "connection timeout")), query("start", startTime, true), query("end", endTime, true), query("limit", limit)], sample: { total: 12, items: [{ level: "ERROR", serviceId: "SVC-PAYMENT", timestamp: "2026-10-13T09:15:00+08:00", message: "Database connection timeout", traceId: "TRACE-OPS-001" }] } }),
		endpoint("EP-LOG-ERRORS", "聚合错误日志", "按错误指纹聚合服务异常日志和发生频次", "GET", "/api/v1/logs/error-groups", { parameters: [query("serviceId", serviceId, true), query("start", startTime, true), query("end", endTime, true)], sample: { groups: [{ fingerprint: "DB_CONNECTION_TIMEOUT", count: 12, firstSeen: "2026-10-13T09:10:00+08:00" }] } }),
		endpoint("EP-LOG-TRACE", "查询调用链日志", "通过 Trace 标识查询跨服务日志上下文", "GET", "/api/v1/traces/{traceId}/logs", { parameters: [pathParam("traceId", str("调用链标识", "TRACE-OPS-001"))], sample: { traceId: "TRACE-OPS-001", entries: [{ serviceId: "SVC-ORDER", message: "request payment" }, { serviceId: "SVC-PAYMENT", message: "connection pool exhausted" }] } })
	]),
	api("API-CHANGE-RELEASE", "发布变更管理服务", "查询变更计划、登记发布申请并跟踪发布流水线结果", "发布管理团队", "release-api", [
		endpoint("EP-CHANGE-LIST", "查询变更计划", "按服务和变更窗口读取待执行及历史变更", "GET", "/api/v1/changes", { parameters: [query("serviceId", serviceId), query("environment", environment)], sample: { items: [{ changeId: "CHG-OPS-001", serviceId: "SVC-PAYMENT", version: "2.6.2", status: "APPROVED", plannedAt: "2026-10-13T22:00:00+08:00" }] } }),
		endpoint("EP-RELEASE-REQUEST", "登记发布申请", "提交服务版本、部署环境和回退方案供审批", "POST", "/api/v1/releases", { body: objectSchema({ serviceId, version: str("目标版本", "2.6.2"), environment, rollbackPlan: str("回退方案", "回退到 2.6.1 并核验支付成功率") }, ["serviceId", "version", "environment", "rollbackPlan"]), sample: { releaseId: "REL-OPS-001", status: "PENDING_APPROVAL" }, success: "201" }),
		endpoint("EP-RELEASE-STATUS", "查询发布执行状态", "读取部署阶段、发布进度和失败原因", "GET", "/api/v1/releases/{releaseId}", { parameters: [pathParam("releaseId", str("发布记录标识", "REL-OPS-001"))], sample: { releaseId: "REL-OPS-001", stage: "CANARY", progress: 25, status: "RUNNING", healthyReplicas: 3 } })
	]),
	api("API-K8S-OPS", "容器集群运维服务", "查询集群与工作负载状态并登记扩缩容操作", "容器平台团队", "k8s-api", [
		endpoint("EP-K8S-CLUSTERS", "查询集群状态", "读取集群版本、节点可用率和资源利用率", "GET", "/api/v1/clusters", { sample: { items: [{ clusterId: "K8S-OPS-DEMO", version: "demo", readyNodes: 6, totalNodes: 6, cpuUsagePercent: 62 }] } }),
		endpoint("EP-K8S-WORKLOADS", "查询工作负载", "查询命名空间下的工作负载、副本数和健康状态", "GET", "/api/v1/clusters/{clusterId}/workloads", { parameters: [pathParam("clusterId", clusterId), query("namespace", namespace, true)], sample: { items: [{ name: "payment-api", namespace: "payment", desiredReplicas: 4, readyReplicas: 3, status: "DEGRADED" }] } }),
		endpoint("EP-K8S-SCALE", "登记工作负载扩缩容", "提交目标副本数、操作人和变更原因", "PATCH", "/api/v1/clusters/{clusterId}/namespaces/{namespace}/deployments/{deployment}/scale", { parameters: [pathParam("clusterId", clusterId), pathParam("namespace", namespace), pathParam("deployment", str("Deployment 名称", "payment-api"))], body: objectSchema({ replicas: integer("目标副本数", 6, { minimum: 1, maximum: 50 }), operator: str("操作人", "ops-demo"), reason: str("变更原因", "业务峰值容量保障") }, ["replicas", "operator", "reason"]), sample: { operationId: "SCALE-OPS-001", status: "ACCEPTED", desiredReplicas: 6 }, success: "202" })
	]),
	api("API-INSPECTION", "巡检任务管理服务", "管理主机、数据库和服务巡检任务，汇总异常检查项", "运维自动化团队", "inspection-api", [
		endpoint("EP-INSPECTION-TASKS", "查询巡检任务", "查询巡检范围、执行频率和最近执行状态", "GET", "/api/v1/inspection/tasks", { parameters: [query("serviceId", serviceId), query("limit", limit)], sample: { items: [{ taskId: "CHECK-OPS-001", name: "支付链路每日巡检", cron: "0 8 * * *", lastStatus: "WARNING" }] } }),
		endpoint("EP-INSPECTION-RUN", "发起巡检执行", "按任务标识发起一次巡检并返回执行记录", "POST", "/api/v1/inspection/tasks/{taskId}/runs", { parameters: [pathParam("taskId", str("巡检任务标识", "CHECK-OPS-001"))], body: objectSchema({ operator: str("发起人", "ops-demo"), scope: str("巡检范围", "payment-chain") }, ["operator", "scope"]), sample: { runId: "RUN-OPS-001", status: "QUEUED" }, success: "202" }),
		endpoint("EP-INSPECTION-REPORT", "读取巡检报告", "读取巡检执行结果、异常项和整改建议", "GET", "/api/v1/inspection/runs/{runId}/report", { parameters: [pathParam("runId", str("巡检执行标识", "RUN-OPS-001"))], sample: { runId: "RUN-OPS-001", passed: 18, warning: 2, failed: 0, findings: [{ check: "disk_usage", hostId: "HOST-OPS-001", observed: 82, threshold: 80, suggestion: "清理过期日志" }] } })
	]),
	api("API-BACKUP", "备份恢复管理服务", "查询备份任务与恢复点，登记数据库备份作业", "数据保障团队", "backup-api", [
		endpoint("EP-BACKUP-JOBS", "查询备份任务", "读取数据库备份策略、最近执行状态和保留周期", "GET", "/api/v1/backup/jobs", { parameters: [query("resourceId", str("数据库资源标识", "DB-PAYMENT"))], sample: { items: [{ jobId: "BKP-OPS-001", resourceId: "DB-PAYMENT", strategy: "FULL_DAILY", retentionDays: 30, lastStatus: "SUCCESS" }] } }),
		endpoint("EP-BACKUP-POINTS", "查询可用恢复点", "查询指定资源可恢复时间点与备份校验结果", "GET", "/api/v1/backup/resources/{resourceId}/recovery-points", { parameters: [pathParam("resourceId", str("数据库资源标识", "DB-PAYMENT"))], sample: { points: [{ recoveryPointId: "RP-OPS-001", createdAt: "2026-10-13T02:00:00+08:00", sizeGb: 128, checksumVerified: true }] } }),
		endpoint("EP-BACKUP-CREATE", "登记手动备份", "提交备份资源、备份类型和操作说明", "POST", "/api/v1/backup/runs", { body: objectSchema({ resourceId: str("数据库资源标识", "DB-PAYMENT"), backupType: str("备份类型", "FULL", { enum: ["FULL", "INCREMENTAL"] }), reason: str("备份原因", "版本发布前备份") }, ["resourceId", "backupType", "reason"]), sample: { backupRunId: "BKR-OPS-001", status: "QUEUED" }, success: "202" })
	], "CUSTOM_HEADER")
];

function tool(name, description, properties, required = [], operationNature = "READ") {
	return {
		name, toolKey: name, description, inputSchema: objectSchema(properties, required),
		enabled: false, operationNature, changeStatus: "UNCHANGED", pendingReview: false, revision: 1,
		annotations: { readOnlyHint: operationNature === "READ", destructiveHint: operationNature === "WRITE", openWorldHint: true }
	};
}

function mcp(id, name, definition, owner, slug, tools, authType = "BEARER") {
	return {
		id, name, definition: `${definition}（运维合成示例）`, domainId: "DOMAIN-OPS", owner, source,
		endpointUrl: `https://${slug}.example.invalid/mcp`, transport: "HTTP", protocolVersion: "2025-06-18", timeoutMs: 10000,
		status: "DRAFT", enabled: false, auth: auth(authType), tools, snapshots: [], lastTest: null
	};
}

export const mcpServers = [
	mcp("MCP-ALERT-TRIAGE", "告警诊断助手", "关联告警、定位受影响对象并生成根因排查建议", "智能运维团队", "alert-triage", [
		tool("list_active_alerts", "查询服务当前活动告警及严重级别", { serviceId, severity: str("告警级别", "CRITICAL", { enum: ["CRITICAL", "MAJOR", "MINOR", "INFO"] }), limit }, ["serviceId"]),
		tool("correlate_alerts", "按时间窗口和拓扑关系聚合关联告警", { alertId: str("主告警标识", "ALERT-OPS-001"), windowMinutes: integer("关联时间窗口（分钟）", 15, { minimum: 1, maximum: 120, default: 15 }) }, ["alertId"]),
		tool("analyze_alert_root_cause", "结合指标、日志和最近变更生成候选根因与证据", { alertId: str("告警标识", "ALERT-OPS-001"), includeChanges: { type: "boolean", description: "是否关联近期变更", default: true, examples: [true] } }, ["alertId"])
	]),
	mcp("MCP-METRICS", "指标分析工具服务", "分析服务指标趋势、异常波动与 SLO 消耗", "可观测平台团队", "metrics-analysis", [
		tool("query_metric_range", "查询指标时序并返回采样点", { serviceId, metric: str("指标名称", "cpu_usage_percent"), startTime, endTime }, ["serviceId", "metric", "startTime", "endTime"]),
		tool("detect_metric_anomalies", "识别指标异常区间并解释与基线的差异", { serviceId, metric: str("指标名称", "error_rate"), lookbackMinutes: integer("回溯分钟数", 60, { minimum: 5, maximum: 1440, default: 60 }) }, ["serviceId", "metric"]),
		tool("summarize_service_slo", "汇总可用性目标、当前达成率与错误预算趋势", { serviceId, window: str("统计窗口", "7d", { enum: ["1d", "7d", "30d"], default: "7d" }) }, ["serviceId"])
	]),
	mcp("MCP-LOG-ANALYSIS", "日志分析工具服务", "检索服务日志、归纳错误模式并关联调用链上下文", "日志平台团队", "log-analysis", [
		tool("search_service_logs", "根据服务、关键词和时间范围检索日志", { serviceId, keyword: str("检索关键词", "connection timeout"), startTime, endTime, limit }, ["serviceId", "startTime", "endTime"]),
		tool("summarize_error_patterns", "聚合日志错误指纹并按频次返回异常模式", { serviceId, startTime, endTime }, ["serviceId", "startTime", "endTime"]),
		tool("trace_request_logs", "读取一个请求在多个服务中的日志上下文", { traceId: str("调用链标识", "TRACE-OPS-001") }, ["traceId"])
	]),
	mcp("MCP-K8S-INSPECT", "容器巡检工具服务", "检查 Pod、节点与集群事件，提供工作负载异常诊断", "容器平台团队", "k8s-inspect", [
		tool("inspect_workload_health", "检查工作负载副本、探针状态和重启次数", { clusterId, namespace, workload: str("工作负载名称", "payment-api") }, ["clusterId", "namespace", "workload"]),
		tool("inspect_node_pressure", "查询节点 CPU、内存、磁盘压力与调度条件", { clusterId, nodeName: str("节点名称", "payment-node-01") }, ["clusterId", "nodeName"]),
		tool("list_cluster_events", "检索命名空间内的调度失败与容器异常事件", { clusterId, namespace, limit }, ["clusterId", "namespace"])
	], "CUSTOM_HEADER"),
	mcp("MCP-RELEASE-CHECK", "发布核验工具服务", "检查发布前置条件、对比版本变化并验证灰度运行结果", "发布管理团队", "release-check", [
		tool("check_release_readiness", "检查变更审批、容量、告警与备份是否满足发布条件", { serviceId, version: str("目标版本", "2.6.2"), environment }, ["serviceId", "version", "environment"]),
		tool("compare_release_versions", "比较两个版本的配置、镜像和依赖差异", { serviceId, fromVersion: str("原版本", "2.6.1"), toVersion: str("目标版本", "2.6.2") }, ["serviceId", "fromVersion", "toVersion"]),
		tool("verify_canary_health", "比较灰度组与稳定组错误率和延迟并提出发布建议", { releaseId: str("发布记录标识", "REL-OPS-001"), observationMinutes: integer("观察窗口（分钟）", 15, { minimum: 5, maximum: 120, default: 15 }) }, ["releaseId"])
	]),
	mcp("MCP-RUNBOOK", "运维预案工具服务", "检索故障处置预案、检查执行条件并提交预案执行任务", "运维自动化团队", "runbook", [
		tool("search_runbooks", "按故障关键词和对象类型查找处置预案", { keyword: str("故障关键词", "数据库连接池耗尽"), objectType: str("运维对象类型", "service", { enum: ["service", "host", "database", "cluster"] }), limit }, ["keyword"]),
		tool("preview_runbook_execution", "预览预案步骤、影响对象及执行前置条件", { runbookId: str("预案标识", "RB-OPS-001"), targetId: serviceId }, ["runbookId", "targetId"]),
		tool("execute_runbook", "提交运维预案执行任务，需要操作人和已批准变更单", { runbookId: str("预案标识", "RB-OPS-001"), targetId: serviceId, operator: str("操作人", "ops-demo"), changeId: str("已批准变更单", "CHG-OPS-001") }, ["runbookId", "targetId", "operator", "changeId"], "WRITE")
	]),
	mcp("MCP-CAPACITY", "容量评估工具服务", "评估资源利用率、预测峰值容量并给出扩容建议", "基础设施团队", "capacity", [
		tool("summarize_host_capacity", "汇总主机 CPU、内存、磁盘使用与剩余容量", { hostId, lookbackDays: integer("回溯天数", 7, { minimum: 1, maximum: 90, default: 7 }) }, ["hostId"]),
		tool("forecast_service_capacity", "根据流量趋势预测服务容量缺口", { serviceId, forecastDays: integer("预测天数", 7, { minimum: 1, maximum: 30, default: 7 }) }, ["serviceId"]),
		tool("recommend_replica_count", "结合峰值请求量、单实例吞吐和冗余比例建议副本数", { serviceId, peakRps: integer("预期峰值请求量（RPS）", 3000, { minimum: 1 }), perReplicaRps: integer("单实例承载量（RPS）", 800, { minimum: 1 }), headroomPercent: integer("容量冗余百分比", 30, { minimum: 0, maximum: 100, default: 30 }) }, ["serviceId", "peakRps", "perReplicaRps"])
	]),
	mcp("MCP-ITSM", "运维工单协同工具服务", "查询事件工单、登记故障协同任务并更新处理进展", "运维服务台团队", "itsm-tools", [
		tool("search_incident_tickets", "按服务和工单状态检索故障工单", { serviceId, status: str("工单状态", "PROCESSING", { enum: ["OPEN", "PROCESSING", "RESOLVED", "CLOSED"] }), limit }, ["serviceId"]),
		tool("create_incident_ticket", "根据告警登记故障工单与责任人", { title: str("工单标题", "支付服务数据库连接超时"), serviceId, alertId: str("关联告警", "ALERT-OPS-001"), priority: str("工单优先级", "P1", { enum: ["P1", "P2", "P3", "P4"] }), assignee: str("处理人", "ops-demo") }, ["title", "serviceId", "priority", "assignee"], "WRITE"),
		tool("update_ticket_progress", "追加工单处理进展与下一步计划", { ticketId: str("工单标识", "WO-OPS-001"), progress: str("处理进展", "已定位连接池耗尽，正在调整容量"), operator: str("更新人", "ops-demo") }, ["ticketId", "progress", "operator"], "WRITE")
	])
];
