<template>
	<div class="mcp-page integration-page">
		<header class="page-header">
			<div>
				<div class="eyebrow">服务接入 / MCP 服务</div>
				<h1>MCP 服务</h1>
				<p>接入远程 MCP Server，自动发现工具，按需启用并调试调用。</p>
			</div>
			<div class="header-actions">
				<ElButton type="primary" :icon="Plus" @click="openCreate">登记 MCP 服务</ElButton>
			</div>
		</header>

		<ElAlert v-if="error" class="page-alert" type="error" :title="error.message" :closable="false" show-icon />

		<section class="workspace">
			<aside class="directory">
				<div class="directory-title"><strong>服务目录</strong><div class="directory-title-actions"><span class="directory-count">{{ filteredServers.length }} / {{ servers.length }}</span><ElButton link type="primary" :icon="Plus" @click="openCreate">新增服务</ElButton></div></div>
				<ElInput v-model="searchText" clearable :prefix-icon="Search" placeholder="搜索名称 / 标识" />
				<div class="directory-filter-pills"><button v-for="filter in statusFilters" :key="filter.value" type="button" :class="{ active: statusFilter === filter.value }" @click="statusFilter = filter.value">{{ filter.label }}</button></div>
				<div v-loading="loading" class="server-list">
					<button v-for="item in filteredServers" :key="item.id" type="button" class="server-item" :class="{ active: item.id === selectedId }" :aria-pressed="item.id === selectedId" :title="`${item.name} · ${item.id}`" @click="selectServer(item.id)">
						<span class="server-icon" aria-hidden="true"><ElIcon><Cpu /></ElIcon></span>
						<span class="server-main"><strong>{{ item.name || "未命名服务" }}</strong><small>{{ item.id }}</small><span class="service-list-state"><small>{{ item.toolCount ?? item.tools?.length ?? 0 }} 个工具</small><ElTag size="small" :type="statusType(item.status, item.enabled)" effect="light">{{ statusLabel(item.status, item.enabled) }}</ElTag></span></span>
					</button>
					<ElEmpty v-if="!filteredServers.length && !loading" description="暂无 MCP 服务" :image-size="64" />
				</div>
			</aside>

			<main v-if="selectedServer" class="detail">
				<div class="detail-header">
					<div class="detail-heading"><div class="detail-id">{{ selectedServer.id }} · 修订 r{{ selectedServer.revision }}</div><h2>{{ selectedServer.name || "未命名服务" }} <ElTag :type="statusType(selectedServer.status, selectedServer.enabled)" effect="light">{{ statusLabel(selectedServer.status, selectedServer.enabled) }}</ElTag></h2><p>{{ selectedServer.definition || "尚未填写服务定义" }}</p><div class="detail-subline"><span class="mono">{{ selectedServer.endpointUrl }}</span><span>·</span><span>{{ selectedServer.transport === "HTTP" ? "Streamable HTTP" : selectedServer.transport }}</span><span>·</span><span>{{ selectedServer.protocolVersion || "协议版本待发现" }}</span></div></div>
					<div class="detail-actions"><ElButton v-if="selectedServer.status === 'DRAFT'" type="primary" :loading="saving" @click="publishService">发布</ElButton><template v-else><ElButton :loading="saving" @click="selectedServer.enabled ? disableService() : enableService">{{ selectedServer.enabled ? "停用" : "启用" }}</ElButton><ElButton :loading="refreshing" @click="refreshTools">重新发现</ElButton><ElButton @click="openEdit">更换凭证</ElButton><ElButton :loading="saving" @click="unpublishService">下架</ElButton></template><ElButton text @click="openEdit">编辑</ElButton><ElButton v-if="selectedServer.status === 'DRAFT'" text type="danger" @click="deleteService">删除</ElButton></div>
				</div>

				<ElTabs v-model="activeTab" class="detail-tabs">
					<ElTabPane label="概要" name="overview">
						<section class="config-card">
							<div class="config-card-header"><strong>连接配置</strong><span class="config-reference">FR-MCP-008 · DS-19</span></div>
							<div class="config-grid"><div class="config-row"><span>服务地址</span><strong class="mono">{{ selectedServer.endpointUrl }}</strong></div><div class="config-row"><span>传输方式</span><strong>{{ selectedServer.transport === "HTTP" ? "Streamable HTTP" : selectedServer.transport }} <em>（枚举校验：STREAMABLE_HTTP / HTTP_SSE）</em></strong></div><div class="config-row"><span>协议版本</span><strong class="mono">{{ selectedServer.protocolVersion || "待发现" }}</strong></div><div class="config-row"><span>认证方式</span><strong v-if="selectedServer.auth?.configured">{{ selectedServer.auth.authType }} · {{ selectedServer.auth.masked || "已配置" }}</strong><ElTag v-else size="small" type="warning">待配置</ElTag></div><div class="config-row"><span>领域归属</span><strong>{{ selectedServer.domainId }}</strong></div><div class="config-row"><span>责任方</span><strong>{{ selectedServer.owner }}</strong></div></div>
							<div class="config-section-title">自定义请求头</div><ElTable :data="selectedServer.headers || []" size="small" empty-text="未配置自定义请求头"><ElTableColumn prop="name" label="名称" min-width="180" /><ElTableColumn prop="masked" label="值（掩码）" min-width="240"><template #default="scope"><span class="mono">{{ scope.row.masked }}</span></template></ElTableColumn><ElTableColumn label="操作" width="100"><template #default><ElButton size="small" text @click="openEdit">更换</ElButton></template></ElTableColumn></ElTable><ElButton class="config-add" size="small" plain @click="openEdit">＋ 新增请求头</ElButton><div class="config-note">请求头值入凭证库加密存储，仅掩码回显；随连接测试、能力发现与调用注入。名称非法或重复时拒绝保存。</div>
						</section>
						<section class="config-card"><div class="config-card-header"><strong>服务信息</strong></div><div class="config-grid service-info-grid"><div class="config-row"><span>业务定义</span><strong>{{ selectedServer.definition || "尚未填写服务定义" }}</strong></div><div class="config-row"><span>稳定标识</span><strong class="mono">{{ selectedServer.id }}</strong></div><div class="config-row"><span>修订</span><strong>修订 {{ selectedServer.revision }}</strong></div><div class="config-row"><span>来源</span><strong>{{ selectedServer.source || "MCP 服务注册表" }}</strong></div><div class="config-row"><span>更新时间</span><strong>{{ formatUpdatedAt(selectedServer.updatedAt) }}</strong></div><div class="config-row"><span>默认超时</span><strong>{{ selectedServer.timeoutMs }} ms</strong></div></div></section>
					</ElTabPane>
					<ElTabPane name="tools"><template #label>工具 <ElTag size="small" effect="plain">{{ tools.length }}</ElTag></template>
						<div class="tab-toolbar"><div><strong>工具列表</strong><span class="muted">首次发现默认关闭，启用后才允许调用</span></div><div><ElButton :loading="testing" @click="testConnection">连接测试</ElButton><ElButton :loading="refreshing" @click="refreshTools">刷新工具</ElButton><ElButton type="primary" @click="openCreateMappingFromTool">创建映射</ElButton></div></div>
						<ElTable :data="tools" stripe empty-text="尚未发现工具">
							<ElTableColumn prop="name" label="工具" min-width="220"><template #default="scope"><strong>{{ scope.row.name }}</strong><div class="muted">{{ scope.row.description || "无描述" }}</div></template></ElTableColumn>
							<ElTableColumn label="操作性质" width="110"><template #default="scope"><ElTag size="small" :type="scope.row.operationNature === 'WRITE' ? 'warning' : 'info'">{{ scope.row.operationNature === "WRITE" ? "写操作" : "只读" }}</ElTag></template></ElTableColumn>
							<ElTableColumn label="变更" width="130"><template #default="scope"><ElTag v-if="scope.row.changeStatus && scope.row.changeStatus !== 'UNCHANGED'" size="small" :type="scope.row.changeStatus === 'REMOVED' ? 'danger' : 'warning'">{{ changeLabel(scope.row.changeStatus) }}</ElTag><span v-else class="muted">无变化</span></template></ElTableColumn>
							<ElTableColumn label="启用" width="90"><template #default="scope"><ElSwitch :model-value="scope.row.enabled" :disabled="scope.row.changeStatus === 'REMOVED' || scope.row.pendingReview" @change="(value: boolean | string | number) => toggleTool(scope.row, Boolean(value))" /></template></ElTableColumn>
							<ElTableColumn label="操作" width="150" fixed="right"><template #default="scope"><ElButton size="small" text type="primary" :disabled="!scope.row.enabled" @click="openDebugger(scope.row)">调试调用</ElButton><ElButton v-if="scope.row.pendingReview" size="small" text @click="reviewTool(scope.row)">复核</ElButton></template></ElTableColumn>
						</ElTable>
					</ElTabPane>
					<ElTabPane name="test"><template #label>测试调用 <ElTag size="small" type="info">new</ElTag></template><div class="test-tab-panel"><div><strong>测试调用</strong><p>选择已启用工具，使用与真实调用一致的参数校验和审计链路进行联调。</p></div><ElButton type="primary" :disabled="!tools.some(tool => tool.enabled && tool.changeStatus !== 'REMOVED')" @click="openFirstEnabledTool">开始测试调用</ElButton></div></ElTabPane>

					<ElTabPane name="snapshots"><template #label>快照与变更 <ElTag size="small" effect="plain">{{ snapshots.length }}</ElTag></template><div class="tab-toolbar"><div><strong>快照与变更</strong><span class="muted">保留每次发现结果，用于审计工具增删和输入模式变化。</span></div><ElButton :loading="refreshing" @click="refreshTools">刷新并生成快照</ElButton></div><ElTable :data="snapshots" stripe empty-text="暂无快照"><ElTableColumn prop="snapshotId" label="快照" width="90"><template #default="scope">v{{ scope.row.snapshotId }}</template></ElTableColumn><ElTableColumn prop="discoveredAt" label="发现时间" min-width="180" /><ElTableColumn prop="protocolVersion" label="协议版本" width="150" /><ElTableColumn label="工具数" width="100"><template #default="scope">{{ scope.row.tools?.length || 0 }}</template></ElTableColumn></ElTable></ElTabPane>

					<ElTabPane label="调用记录" name="logs"><div class="tab-toolbar"><div><strong>调用记录</strong><span class="muted">敏感参数已脱敏，只保留可审计摘要。</span></div><ElButton @click="loadLogs">刷新</ElButton></div><ElTable :data="logs" stripe empty-text="暂无调用记录"><ElTableColumn prop="occurredAt" label="时间" min-width="180" /><ElTableColumn prop="toolKey" label="工具" min-width="180" /><ElTableColumn label="结果" width="120"><template #default="scope"><ElTag size="small" :type="scope.row.resultStatus === 'SUCCESS' ? 'success' : scope.row.resultStatus === 'REJECTED' ? 'warning' : 'danger'">{{ scope.row.resultStatus }}</ElTag></template></ElTableColumn><ElTableColumn prop="normalizedError" label="规范化错误" min-width="190" /><ElTableColumn label="耗时" width="100"><template #default="scope">{{ scope.row.latencyMs }} ms</template></ElTableColumn></ElTable></ElTabPane>

					<ElTabPane name="mappings"><template #label>本体映射 <ElTag size="small" effect="plain">{{ mappings.length }}</ElTag></template><div class="tab-toolbar"><div><strong>工具与本体映射</strong><span class="muted">调用结果可映射到对象模型元素，目标引用需先存在。</span></div><ElButton type="primary" @click="openCreateMapping">新增映射</ElButton></div><ElTable :data="mappings" stripe empty-text="暂无映射"><ElTableColumn prop="toolKey" label="工具" min-width="180" /><ElTableColumn prop="targetRef" label="目标引用" min-width="180"><template #default="scope"><span class="mono">{{ scope.row.targetRef }}</span></template></ElTableColumn><ElTableColumn prop="mappingKind" label="映射类型" width="140" /><ElTableColumn prop="description" label="说明" min-width="180" /><ElTableColumn label="操作" width="90"><template #default="scope"><ElButton text type="danger" size="small" @click="removeMapping(scope.row)">删除</ElButton></template></ElTableColumn></ElTable></ElTabPane>
				</ElTabs>
				<section v-if="activeTab === 'snapshots'" class="refresh-records">
					<div class="refresh-records-heading"><strong>刷新记录</strong><span class="muted">失败只记录结果，不会覆盖当前工具或生成新版本。</span></div>
					<ElTable :data="refreshRecords" stripe empty-text="暂无刷新记录">
						<ElTableColumn prop="occurredAt" label="时间" min-width="180" />
						<ElTableColumn label="结果" width="100"><template #default="scope"><ElTag size="small" :type="scope.row.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.outcome === "SUCCESS" ? "成功" : "失败" }}</ElTag></template></ElTableColumn>
						<ElTableColumn label="版本" width="100"><template #default="scope">{{ scope.row.snapshotId ? `v${scope.row.snapshotId}` : "未生成" }}</template></ElTableColumn>
						<ElTableColumn prop="normalizedError" label="错误" min-width="180" />
						<ElTableColumn prop="message" label="说明" min-width="260" />
					</ElTable>
				</section>
			</main>
			<ElEmpty v-else class="detail-empty" description="请选择或登记一个 MCP 服务" />
		</section>

		<ElDialog v-model="serviceDialogVisible" class="integration-dialog" :title="editingServer ? '编辑 MCP 服务' : '登记 MCP 服务'" width="800px" destroy-on-close align-center>
			<ElForm class="registration-form" :model="serviceForm" label-position="top">
				<ElFormItem label="服务标识" required><ElInput v-model="serviceForm.id" :disabled="!!editingServer" placeholder="例如 MCP-CMDB" /></ElFormItem>
				<ElFormItem label="服务名称" required><ElInput v-model="serviceForm.name" placeholder="例如 CMDB 查询服务" /></ElFormItem>
				<ElFormItem label="服务定义" required><ElInput v-model="serviceForm.definition" type="textarea" :rows="2" placeholder="说明服务提供的能力" /></ElFormItem>
				<div class="form-two-col">
					<ElFormItem label="领域" required><ElInput v-model="serviceForm.domainId" placeholder="例如 DOMAIN-OPS" /></ElFormItem>
					<ElFormItem label="责任方" required><ElInput v-model="serviceForm.owner" placeholder="例如 运维平台团队" /></ElFormItem>
				</div>
				<ElFormItem class="form-span-full" label="MCP Endpoint" required><ElInput v-model="serviceForm.endpointUrl" placeholder="https://mcp.example.com" /></ElFormItem>
				<div class="form-two-col">
					<ElFormItem label="认证方式"><ElSelect v-model="serviceForm.authType" style="width:100%"><ElOption label="无需认证" value="NONE" /><ElOption label="Bearer Token" value="BEARER" /><ElOption label="自定义 Header" value="CUSTOM_HEADER" /></ElSelect></ElFormItem>
					<ElFormItem label="超时(ms)"><ElInputNumber v-model="serviceForm.timeoutMs" :min="1000" :max="60000" /></ElFormItem>
				</div>
				<ElFormItem v-if="serviceForm.authType !== 'NONE'" label="认证密钥"><ElInput v-model="serviceForm.secret" type="password" show-password placeholder="仅用于本次运行期连接，不写入文件" /></ElFormItem>
			</ElForm>
			<template #footer><ElButton @click="serviceDialogVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveService">保存草稿</ElButton></template>
		</ElDialog>

		<ElDialog v-model="debugVisible" class="integration-dialog" title="调试调用" width="700px" destroy-on-close align-center><template v-if="selectedTool"><div class="debug-summary"><div><strong>{{ selectedTool.name }}</strong><p>{{ selectedTool.description || "无描述" }}</p></div><ElTag :type="selectedTool.operationNature === 'WRITE' ? 'warning' : 'info'">{{ selectedTool.operationNature === "WRITE" ? "写操作" : "只读" }}</ElTag></div><ElForm label-position="top"><ElFormItem label="输入参数"><ElInput v-model="debugArguments" class="debug-input" type="textarea" :rows="10" :placeholder="schemaPlaceholder(selectedTool.inputSchema)" /></ElFormItem></ElForm><ElAlert v-if="selectedTool.operationNature === 'WRITE'" title="写操作会调用远程服务，请确认参数和业务影响。" type="warning" show-icon :closable="false" /><ElAlert v-if="invokeResult" :title="invokeResult.resultStatus === 'SUCCESS' ? '调用成功' : `调用未完成：${invokeResult.normalizedError || '未知错误'}`" :type="invokeResult.resultStatus === 'SUCCESS' ? 'success' : 'error'" show-icon :closable="false" class="invoke-alert" /><div v-if="invokeResult?.externalContent" class="external-content-note"><ElTag size="small" type="warning">远端内容</ElTag><span>结果来自外部 MCP 服务，使用前请校验内容。</span></div><pre v-if="invokeResult" class="response-view">{{ pretty(invokeResult.response) }}</pre></template><template #footer><ElButton @click="debugVisible = false">关闭</ElButton><ElButton type="primary" :loading="invoking" @click="invokeTool">发送调用</ElButton></template></ElDialog>

		<ElDialog v-model="mappingDialogVisible" class="integration-dialog" title="新增本体映射" width="560px" destroy-on-close align-center><ElForm :model="mappingForm" label-position="top"><ElFormItem label="工具" required><ElSelect v-model="mappingForm.toolKey" style="width:100%"><ElOption v-for="tool in tools.filter(item => item.changeStatus !== 'REMOVED')" :key="tool.toolKey" :label="tool.name" :value="tool.toolKey" /></ElSelect></ElFormItem><ElFormItem label="目标引用" required><ElInput v-model="mappingForm.targetRef" placeholder="例如 OBJ-HOST 或 OBJ-HOST#ATTR-HOST-IP" /></ElFormItem><ElFormItem label="映射类型"><ElSelect v-model="mappingForm.mappingKind" style="width:100%"><ElOption label="结果填充" value="RESULT" /><ElOption label="输入绑定" value="INPUT" /><ElOption label="状态同步" value="STATE" /></ElSelect></ElFormItem><ElFormItem label="说明"><ElInput v-model="mappingForm.description" type="textarea" :rows="2" /></ElFormItem></ElForm><template #footer><ElButton @click="mappingDialogVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveMapping">保存映射</ElButton></template></ElDialog>
	</div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Cpu, Plus, Search } from "@element-plus/icons-vue";

import { mcpApi } from "./mcp-api";
import type { McpApiError, McpCallLog, McpInvokeResult, McpMapping, McpRefreshRecord, McpServer, McpSnapshot, McpTool } from "./mcp-types";

const servers = ref<McpServer[]>([]);
const selectedId = ref("");
const searchText = ref("");
const statusFilter = ref("");
const loading = ref(false);
const saving = ref(false);
const testing = ref(false);
const refreshing = ref(false);
const invoking = ref(false);
const error = ref<McpApiError | null>(null);
const activeTab = ref("overview");
const tools = ref<McpTool[]>([]);
const snapshots = ref<McpSnapshot[]>([]);
const refreshRecords = ref<McpRefreshRecord[]>([]);
const logs = ref<McpCallLog[]>([]);
const mappings = ref<McpMapping[]>([]);
const serviceDialogVisible = ref(false);
const mappingDialogVisible = ref(false);
const debugVisible = ref(false);
const editingServer = ref<McpServer | null>(null);
const selectedTool = ref<McpTool | null>(null);
const debugArguments = ref("{}");
const invokeResult = ref<McpInvokeResult | null>(null);
const serviceForm = reactive({ id: "", name: "", definition: "", domainId: "DOMAIN-OPS", owner: "", endpointUrl: "", authType: "NONE", secret: "", timeoutMs: 10000 });
const mappingForm = reactive({ toolKey: "", targetRef: "", mappingKind: "RESULT", description: "" });

const selectedServer = computed(() => servers.value.find(item => item.id === selectedId.value) || null);
const statusFilters = [{ label: "全部", value: "" }, { label: "已启用", value: "ENABLED" }, { label: "草稿", value: "DRAFT" }, { label: "已禁用", value: "DISABLED" }];
const matchesStatus = (status: string, enabled: boolean, filter: string) => !filter ? true : filter === "DRAFT" ? status === "DRAFT" : filter === "ENABLED" ? status === "PUBLISHED" && enabled : status === "PUBLISHED" && !enabled;
const filteredServers = computed(() => servers.value.filter(item => matchesStatus(item.status, item.enabled, statusFilter.value) && (!searchText.value || `${item.id} ${item.name} ${item.definition}`.toLowerCase().includes(searchText.value.toLowerCase()))));
const statusLabel = (status?: string, enabled?: boolean) => status === "PUBLISHED" ? (enabled ? "已发布 · 启用" : "已发布 · 禁用") : "草稿";
const statusType = (status?: string, enabled?: boolean) => status === "PUBLISHED" && enabled ? "success" : "info";
const formatUpdatedAt = (value?: string) => value ? value.replace("T", " ").slice(0, 16) : "—";
const changeLabel = (change?: string) => ({ ADDED: "新增", REMOVED: "已移除", SCHEMA_CHANGED: "输入已变更" })[change || ""] || change;

function missingPublishFields(server: McpServer): string[] {
	return [
		["服务名称", server.name],
		["服务定义", server.definition],
		["领域", server.domainId],
		["责任方", server.owner],
		["MCP Endpoint", server.endpointUrl]
	].filter(([, value]) => !String(value ?? "").trim()).map(([label]) => label);
}

function upsertServer(server: McpServer) { const index = servers.value.findIndex(item => item.id === server.id); if (index >= 0) servers.value.splice(index, 1, server); else servers.value.push(server); }
async function loadServers() { loading.value = true; error.value = null; try { const items = await mcpApi.list(); servers.value = items; if (!selectedId.value || !items.some(item => item.id === selectedId.value)) selectedId.value = items[0]?.id || ""; if (selectedId.value) await selectServer(selectedId.value); } catch (cause) { error.value = cause as McpApiError; } finally { loading.value = false; } }
async function selectServer(id: string) { selectedId.value = id; try { const [server, snapshotItems, logItems, mappingItems] = await Promise.all([mcpApi.get(id), mcpApi.snapshots(id), mcpApi.logs(id), mcpApi.mappings(id)]); upsertServer(server); tools.value = server.tools || []; snapshots.value = snapshotItems; refreshRecords.value = server.refreshRecords || []; logs.value = logItems; mappings.value = mappingItems; } catch (cause) { error.value = cause as McpApiError; } }
async function testConnection() { if (!selectedServer.value) return; testing.value = true; try { const result = await mcpApi.discover(selectedServer.value.id); await selectServer(selectedServer.value.id); ElMessage[result.outcome === "SUCCESS" ? "success" : "error"](result.outcome === "SUCCESS" ? `连接成功，发现 ${result.tools?.length || 0} 个工具` : `${result.normalizedError || "连接失败"}：${result.message || "请检查 Endpoint"}`); } catch (cause) { ElMessage.error((cause as McpApiError).message || "连接测试失败"); } finally { testing.value = false; } }
async function refreshTools() {
	if (!selectedServer.value) return;
	refreshing.value = true;
	try {
		const result = await mcpApi.refresh(selectedServer.value.id);
		await selectServer(selectedServer.value.id);
		if (result.outcome !== "SUCCESS") {
			ElMessage.error(`${result.normalizedError || "刷新失败"}：${result.message || "无法从远端 MCP 服务获取工具"}`);
			return;
		}
		const diff = result.diff;
		ElMessage.success(diff ? `刷新完成：新增 ${diff.added}，移除 ${diff.removed}，输入变更 ${diff.schemaChanged}` : "刷新完成");
	} catch (cause) {
		ElMessage.error((cause as McpApiError).message || "刷新失败");
	} finally {
		refreshing.value = false;
	}
}
async function publishService() { if (!selectedServer.value) return; const missing = missingPublishFields(selectedServer.value); if (missing.length) { ElMessage.error(`请补全必填项：${missing.join("、")}`); return; } await changeServiceState(() => mcpApi.publish(selectedServer.value!.id, selectedServer.value!.revision), "MCP 服务已发布"); }
async function unpublishService() { if (!selectedServer.value) return; await changeServiceState(() => mcpApi.unpublish(selectedServer.value!.id, selectedServer.value!.revision), "MCP 服务已下架"); }
async function enableService() { if (!selectedServer.value) return; await changeServiceState(() => mcpApi.enable(selectedServer.value!.id, selectedServer.value!.revision), "MCP 服务已启用"); }
async function disableService() { if (!selectedServer.value) return; await changeServiceState(() => mcpApi.disable(selectedServer.value!.id, selectedServer.value!.revision), "MCP 服务已禁用"); }
async function changeServiceState(action: () => Promise<McpServer>, message: string) { saving.value = true; try { upsertServer(await action()); ElMessage.success(message); } catch (cause) { ElMessage.error((cause as McpApiError).message || "状态更新失败"); } finally { saving.value = false; } }
async function toggleTool(tool: McpTool, enabled: boolean) { if (!selectedServer.value) return; try { await mcpApi.updateTool(selectedServer.value.id, tool.toolKey, { enabled }); await selectServer(selectedServer.value.id); } catch (cause) { ElMessage.error((cause as McpApiError).message || "工具状态更新失败"); } }
async function reviewTool(tool: McpTool) { if (!selectedServer.value) return; try { await mcpApi.reviewTool(selectedServer.value.id, tool.toolKey); await selectServer(selectedServer.value.id); ElMessage.success("工具变更已复核"); } catch (cause) { ElMessage.error((cause as McpApiError).message || "复核失败"); } }
function openCreate() { editingServer.value = null; Object.assign(serviceForm, { id: `MCP-${Date.now().toString().slice(-6)}`, name: "", definition: "", domainId: "DOMAIN-OPS", owner: "", endpointUrl: "", authType: "NONE", secret: "", timeoutMs: 10000 }); serviceDialogVisible.value = true; }
function openEdit() { if (!selectedServer.value) return; editingServer.value = selectedServer.value; Object.assign(serviceForm, { id: selectedServer.value.id, name: selectedServer.value.name, definition: selectedServer.value.definition, domainId: selectedServer.value.domainId, owner: selectedServer.value.owner, endpointUrl: selectedServer.value.endpointUrl, authType: selectedServer.value.auth?.authType || "NONE", secret: "", timeoutMs: selectedServer.value.timeoutMs }); serviceDialogVisible.value = true; }
async function saveService() { if (!serviceForm.id) { ElMessage.warning("请填写服务标识"); return; } saving.value = true; try { const body = { id: serviceForm.id, name: serviceForm.name, definition: serviceForm.definition, domainId: serviceForm.domainId, owner: serviceForm.owner, endpointUrl: serviceForm.endpointUrl, transport: "HTTP" as const, timeoutMs: serviceForm.timeoutMs, auth: { authType: serviceForm.authType, configured: Boolean(serviceForm.secret), masked: serviceForm.secret ? "已配置" : "未配置", secret: serviceForm.secret } }; const result = editingServer.value ? await mcpApi.update(editingServer.value.id, { ...body, revision: editingServer.value.revision }) : await mcpApi.create(body); upsertServer(result); selectedId.value = result.id; serviceDialogVisible.value = false; await selectServer(result.id); ElMessage.success("MCP 服务草稿已保存"); } catch (cause) { ElMessage.error((cause as McpApiError).message || "保存失败"); } finally { saving.value = false; } }
async function deleteService() { if (!selectedServer.value) return; try { await ElMessageBox.confirm(`确认删除草稿服务“${selectedServer.value.name || selectedServer.value.id}”？存在映射时无法删除。`, "请确认", { type: "warning" }); await mcpApi.remove(selectedServer.value.id, selectedServer.value.revision); await loadServers(); ElMessage.success("MCP 服务已删除"); } catch (cause) { if (cause !== "cancel") ElMessage.error((cause as McpApiError).message || "删除失败"); } }
function schemaPlaceholder(schema: Record<string, unknown>) { return JSON.stringify(schema?.properties || {}, null, 2); }
function openDebugger(tool: McpTool) { selectedTool.value = tool; debugArguments.value = "{}"; invokeResult.value = null; debugVisible.value = true; }
function openFirstEnabledTool() { const tool = tools.value.find(item => item.enabled && item.changeStatus !== "REMOVED"); if (tool) openDebugger(tool); }
async function invokeTool() { if (!selectedServer.value || !selectedTool.value) return; let args: Record<string, unknown>; try { const parsed: unknown = JSON.parse(debugArguments.value || "{}"); if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error(); args = parsed as Record<string, unknown>; } catch { ElMessage.error("输入参数必须是 JSON 对象"); return; } if (selectedTool.value.operationNature === "WRITE") { try { await ElMessageBox.confirm("这是写操作，将调用远程 MCP 服务。确认继续？", "确认调用", { type: "warning", confirmButtonText: "继续调用" }); } catch { return; } } invoking.value = true; try { invokeResult.value = await mcpApi.invoke(selectedServer.value.id, selectedTool.value.toolKey, { arguments: args, confirm: selectedTool.value.operationNature === "WRITE" }); await loadLogs(); } catch (cause) { ElMessage.error((cause as McpApiError).message || "调用失败"); } finally { invoking.value = false; } }
function pretty(value: unknown) { return JSON.stringify(value, null, 2); }
async function loadLogs() { if (selectedServer.value) logs.value = await mcpApi.logs(selectedServer.value.id); }
function openCreateMapping() { mappingForm.toolKey = tools.value.find(tool => tool.enabled && tool.changeStatus !== "REMOVED")?.toolKey || tools.value[0]?.toolKey || ""; mappingForm.targetRef = ""; mappingForm.mappingKind = "RESULT"; mappingForm.description = ""; mappingDialogVisible.value = true; }
function openCreateMappingFromTool() { openCreateMapping(); }
async function saveMapping() { if (!selectedServer.value || !mappingForm.toolKey || !mappingForm.targetRef) { ElMessage.warning("请选择工具并填写目标引用"); return; } saving.value = true; try { await mcpApi.createMapping({ id: `MAP-${Date.now().toString().slice(-8)}`, serverId: selectedServer.value.id, ...mappingForm }); mappings.value = await mcpApi.mappings(selectedServer.value.id); mappingDialogVisible.value = false; ElMessage.success("映射已保存"); } catch (cause) { ElMessage.error((cause as McpApiError).message || "保存映射失败"); } finally { saving.value = false; } }
async function removeMapping(mapping: McpMapping) { try { await ElMessageBox.confirm(`确认删除映射“${mapping.id}”？`, "请确认", { type: "warning" }); await mcpApi.removeMapping(mapping.id); mappings.value = await mcpApi.mappings(selectedServer.value?.id); } catch (cause) { if (cause !== "cancel") ElMessage.error((cause as McpApiError).message || "删除映射失败"); } }

onMounted(loadServers);
</script>

<style src="./service-integration.css"></style>
