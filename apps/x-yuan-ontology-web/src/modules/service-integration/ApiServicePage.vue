<template>
	<div class="service-page integration-page">
		<header class="service-page-header">
			<div>
				<div class="eyebrow">服务接入 / API 服务</div>
				<h1>API 服务</h1>
				<p>登记外部 HTTP 能力，维护 API 契约，发布后按需启用。</p>
			</div>
			<div class="header-actions">
				<ElButton @click="importDialogVisible = true">从 OpenAPI 导入</ElButton>
				<ElButton type="primary" :icon="Plus" @click="openCreateService">登记 API 服务</ElButton>
			</div>
		</header>

		<div v-if="error" class="page-alert"><ElAlert type="error" :title="error.message" show-icon :closable="false" /></div>

		<section class="service-layout">
			<aside class="service-directory">
				<div class="directory-toolbar">
					<strong>服务目录</strong>
					<ElTag size="small" type="info" effect="plain">{{ services.length }}</ElTag>
				</div>
				<ElInput v-model="searchText" clearable placeholder="搜索服务名称或标识" :prefix-icon="Search" />
				<div class="directory-filters">
					<ElSelect v-model="statusFilter" placeholder="全部状态" clearable>
						<ElOption label="全部" value="" /><ElOption label="草稿" value="DRAFT" /><ElOption label="已发布" value="PUBLISHED" />
					</ElSelect>
				</div>
				<div v-loading="loading" class="service-list">
					<button v-for="item in filteredServices" :key="item.id" type="button" class="service-list-item" :class="{ active: item.id === selectedId }" :aria-pressed="item.id === selectedId" :title="`${item.name} · ${item.id}`" @click="selectService(item.id)">
						<span class="service-list-icon" aria-hidden="true"><ElIcon><component :is="serviceIcon(item.id)" /></ElIcon></span>
						<span class="service-list-main"><strong>{{ item.name || "未命名服务" }}</strong><small>{{ item.id }}</small><span class="service-list-state"><small>{{ item.apiCount ?? item.apis.length }} 个 API</small><ElTag size="small" :type="statusType(item.status, item.enabled)" effect="light">{{ statusLabel(item.status, item.enabled) }}</ElTag></span></span>
					</button>
					<ElEmpty v-if="!filteredServices.length && !loading" description="暂无 API 服务" :image-size="64" />
				</div>
			</aside>

			<main v-if="selectedService" class="service-detail">
				<div class="detail-titlebar">
					<div><div class="detail-id">{{ selectedService.id }} · {{ selectedService.version || "未填写版本" }}</div><h2>{{ selectedService.name || "未命名服务" }} <ElTag :type="statusType(selectedService.status, selectedService.enabled)" effect="light">{{ statusLabel(selectedService.status, selectedService.enabled) }}</ElTag></h2><p>{{ selectedService.definition || "尚未填写业务说明" }}</p></div>
					<div class="detail-actions"><ElButton v-if="selectedService.status === 'DRAFT'" type="primary" :loading="saving" @click="publishService">发布</ElButton><template v-else><ElButton :loading="saving" @click="selectedService.enabled ? disableService() : enableService">{{ selectedService.enabled ? "禁用服务" : "启用服务" }}</ElButton><ElButton :loading="saving" @click="unpublishService">下架</ElButton></template><ElButton text :icon="Edit" @click="openEditService">编辑</ElButton><ElButton v-if="selectedService.status === 'DRAFT'" text type="danger" @click="deleteService">删除</ElButton></div>
				</div>
				<div class="service-meta-grid"><div><span>基础地址</span><strong class="mono endpoint-value" :title="selectedService.baseUrl">{{ selectedService.baseUrl }}</strong></div><div><span>所属领域</span><strong>{{ selectedService.domainId }}</strong></div><div><span>责任方</span><strong>{{ selectedService.owner }}</strong></div><div><span>认证方式</span><ElTag v-if="!selectedService.auth?.configured" size="small" type="warning">待配置</ElTag><strong v-else>{{ selectedService.auth.masked }}</strong></div><div><span>更新时间</span><strong>{{ formatUpdatedAt(selectedService.updatedAt) }}</strong></div><div><span>依据来源</span><strong>{{ selectedService.source || "未填写" }}</strong></div><div><span>默认超时</span><strong>{{ selectedService.timeoutMs }} ms</strong></div><div><span>修订</span><strong>r{{ selectedService.revision }}</strong></div></div>

				<ElTabs v-model="activeTab" class="service-tabs">
					<ElTabPane label="API" name="apis"><div class="tab-toolbar"><div><strong>API 列表</strong><span class="muted">共 {{ selectedService.apis.length }} 个已登记 API</span></div><ElButton type="primary" :icon="Plus" @click="openCreateApi">新增 API</ElButton></div><ElTable :data="selectedService.apis" stripe empty-text="暂无 API"><ElTableColumn prop="name" label="API 名称" min-width="180"><template #default="scope"><strong>{{ scope.row.name || "未命名 API" }}</strong><div class="muted">{{ scope.row.id }}</div></template></ElTableColumn><ElTableColumn label="HTTP 操作" width="180"><template #default="scope"><span class="method-pill" :class="scope.row.method.toLowerCase()">{{ scope.row.method || "-" }}</span><span class="mono path-text">{{ scope.row.path || "-" }}</span></template></ElTableColumn><ElTableColumn label="操作性质" width="100"><template #default="scope"><ElTag size="small" :type="scope.row.operationNature === 'WRITE' ? 'warning' : 'info'">{{ scope.row.operationNature === 'WRITE' ? '写操作' : '只读' }}</ElTag></template></ElTableColumn><ElTableColumn label="状态" width="110"><template #default="scope"><ElTag size="small" :type="statusType(scope.row.status, scope.row.enabled)">{{ statusLabel(scope.row.status, scope.row.enabled) }}</ElTag></template></ElTableColumn><ElTableColumn label="最近测试" width="150"><template #default="scope"><span v-if="scope.row.lastTest" :class="scope.row.lastTest.outcome === 'SUCCESS' ? 'test-success' : 'test-failed'">{{ scope.row.lastTest.outcome === 'SUCCESS' ? '通过' : '失败' }} · {{ scope.row.lastTest.category }}</span><span v-else class="muted">未测试</span></template></ElTableColumn><ElTableColumn label="操作" width="220" fixed="right"><template #default="scope"><ElButton size="small" text @click="openEditApi(scope.row)">编辑</ElButton><ElButton size="small" text type="primary" :loading="testingId === scope.row.id" @click="testApi(scope.row)">测试连接</ElButton><ElButton size="small" text type="danger" @click="deleteApi(scope.row)">删除</ElButton></template></ElTableColumn></ElTable></ElTabPane>
					<ElTabPane name="snapshots"><template #label>快照与变更 <ElTag size="small" effect="plain">{{ snapshots.length }}</ElTag></template><div class="tab-toolbar"><div><strong>快照与变更</strong><span class="muted">保留每次 API 定义，用于审计接口增删和配置变化。</span></div><ElButton :loading="refreshing" @click="createSnapshot">刷新并生成快照</ElButton></div><ElTable :data="snapshots" stripe empty-text="暂无快照"><ElTableColumn prop="snapshotId" label="快照" width="90"><template #default="scope">v{{ scope.row.snapshotId }}</template></ElTableColumn><ElTableColumn prop="capturedAt" label="生成时间" min-width="180" /><ElTableColumn prop="revision" label="修订" width="100" /><ElTableColumn label="API 数量" width="100"><template #default="scope">{{ scope.row.apiCount }}</template></ElTableColumn><ElTableColumn label="变更" min-width="180"><template #default="scope">新增 {{ scope.row.diff?.added || 0 }} · 移除 {{ scope.row.diff?.removed || 0 }} · 修改 {{ scope.row.diff?.changed || 0 }}</template></ElTableColumn></ElTable></ElTabPane>
					<ElTabPane label="调用记录" name="logs"><div class="tab-toolbar"><div><strong>调用记录</strong><span class="muted">保留 API 测试调用结果，便于定位连接和响应问题。</span></div><ElButton @click="loadLogs">刷新</ElButton></div><ElTable :data="logs" stripe empty-text="暂无调用记录"><ElTableColumn prop="occurredAt" label="时间" min-width="180" /><ElTableColumn label="API" min-width="180"><template #default="scope"><strong>{{ scope.row.apiName || scope.row.apiId }}</strong><div class="muted">{{ scope.row.apiId }}</div></template></ElTableColumn><ElTableColumn label="结果" width="120"><template #default="scope"><ElTag size="small" :type="scope.row.resultStatus === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.resultStatus === 'SUCCESS' ? '成功' : '失败' }}</ElTag></template></ElTableColumn><ElTableColumn prop="normalizedError" label="错误" min-width="180" /><ElTableColumn label="耗时" width="100"><template #default="scope">{{ scope.row.latencyMs }} ms</template></ElTableColumn></ElTable></ElTabPane>
					<ElTabPane name="mappings"><template #label>本体映射 <ElTag size="small" effect="plain">{{ mappings.length }}</ElTag></template><div class="tab-toolbar"><div><strong>API 与本体映射</strong><span class="muted">API 调用结果可映射到对象模型元素，目标引用需先存在。</span></div><ElButton type="primary" @click="openCreateMapping">新增映射</ElButton></div><ElTable :data="mappings" stripe empty-text="暂无映射"><ElTableColumn label="API" min-width="180"><template #default="scope"><strong>{{ apiName(scope.row.apiId) }}</strong><div class="muted">{{ scope.row.apiId }}</div></template></ElTableColumn><ElTableColumn prop="targetRef" label="目标引用" min-width="180"><template #default="scope"><span class="mono">{{ scope.row.targetRef }}</span></template></ElTableColumn><ElTableColumn prop="mappingKind" label="映射类型" width="140" /><ElTableColumn prop="description" label="说明" min-width="180" /><ElTableColumn label="操作" width="90"><template #default="scope"><ElButton text type="danger" size="small" @click="removeMapping(scope.row)">删除</ElButton></template></ElTableColumn></ElTable></ElTabPane>
				</ElTabs>
			</main>
			<ElEmpty v-else description="选择或登记一个 API 服务" class="detail-empty" />
		</section>

		<ElDialog v-model="serviceDialogVisible" class="integration-dialog" :title="editingService ? '编辑 API 服务' : '登记 API 服务'" width="800px" destroy-on-close align-center>
			<ElForm class="registration-form" :model="serviceForm" label-position="top">
				<ElFormItem label="服务标识" required><ElInput v-model="serviceForm.id" :disabled="!!editingService" placeholder="例如 API-ITSM" /></ElFormItem>
				<ElFormItem label="服务名称" required><ElInput v-model="serviceForm.name" placeholder="例如 工单查询服务" /></ElFormItem>
				<ElFormItem label="业务说明" required><ElInput v-model="serviceForm.definition" type="textarea" :rows="2" placeholder="说明服务提供的业务能力" /></ElFormItem>
				<div class="form-two-col">
					<ElFormItem label="版本" required><ElInput v-model="serviceForm.version" placeholder="v1" /></ElFormItem>
					<ElFormItem label="环境" required><ElSelect v-model="serviceForm.environment"><ElOption label="生产" value="生产" /><ElOption label="预发" value="预发" /><ElOption label="测试" value="测试" /></ElSelect></ElFormItem>
					<ElFormItem label="领域" required><ElInput v-model="serviceForm.domainId" placeholder="例如 DOMAIN-OPS" /></ElFormItem>
					<ElFormItem label="责任方" required><ElInput v-model="serviceForm.owner" placeholder="例如 运维平台团队" /></ElFormItem>
					<ElFormItem label="认证方式"><ElSelect v-model="serviceAuthType"><ElOption label="无需认证" value="NONE" /><ElOption label="API Key" value="API_KEY" /><ElOption label="Bearer Token" value="BEARER" /><ElOption label="自定义 Header" value="CUSTOM_HEADER" /></ElSelect></ElFormItem>
				</div>
				<ElFormItem label="基础地址" required><ElInput v-model="serviceForm.baseUrl" placeholder="https://api.example.com" /></ElFormItem>
				<ElFormItem label="超时(ms)"><ElInputNumber v-model="serviceForm.timeoutMs" :min="1" :max="60000" /></ElFormItem>
			</ElForm>
			<template #footer><ElButton @click="serviceDialogVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveService">保存草稿</ElButton></template>
		</ElDialog>
		<ApiEditorDialog v-model="apiDialogVisible" :api="editingApi" :service="selectedService" :saving="saving" @save="saveApi" />
		<ElDialog v-model="mappingDialogVisible" class="integration-dialog" title="新增本体映射" width="560px" destroy-on-close align-center>
			<ElForm :model="mappingForm" label-position="top">
				<ElFormItem label="API" required><ElSelect v-model="mappingForm.apiId" style="width: 100%"><ElOption v-for="api in selectedService?.apis || []" :key="api.id" :label="api.name || api.id" :value="api.id" /></ElSelect></ElFormItem>
				<ElFormItem label="目标引用" required><ElInput v-model="mappingForm.targetRef" placeholder="例如 OBJ-HOST 或 OBJ-HOST#ATTR-HOST-IP" /></ElFormItem>
				<ElFormItem label="映射类型"><ElSelect v-model="mappingForm.mappingKind" style="width: 100%"><ElOption label="结果填充" value="RESULT" /><ElOption label="输入绑定" value="INPUT" /><ElOption label="状态同步" value="STATE" /></ElSelect></ElFormItem>
				<ElFormItem label="说明"><ElInput v-model="mappingForm.description" type="textarea" :rows="2" /></ElFormItem>
			</ElForm>
			<template #footer><ElButton @click="mappingDialogVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveMapping">保存映射</ElButton></template>
		</ElDialog>
		<ElDialog v-model="importDialogVisible" class="integration-dialog" title="从 OpenAPI 导入服务" width="760px" destroy-on-close align-center><ElAlert title="支持 OpenAPI 3.0.x JSON。文档中的 info、servers 会自动带入；下方治理字段可覆盖自动解析结果。" type="info" :closable="false" /><ElForm :model="importForm" label-position="top" class="import-form"><div class="form-two-col"><ElFormItem label="服务标识"><ElInput v-model="importForm.id" placeholder="留空则根据标题生成" /></ElFormItem><ElFormItem label="服务名称"><ElInput v-model="importForm.name" placeholder="留空则读取 info.title" /></ElFormItem><ElFormItem label="版本"><ElInput v-model="importForm.version" placeholder="留空则读取 info.version" /></ElFormItem><ElFormItem label="环境"><ElSelect v-model="importForm.environment"><ElOption label="生产" value="生产" /><ElOption label="预发" value="预发" /><ElOption label="测试" value="测试" /></ElSelect></ElFormItem><ElFormItem label="领域"><ElInput v-model="importForm.domainId" placeholder="DOMAIN-OPS" /></ElFormItem><ElFormItem label="责任方"><ElInput v-model="importForm.owner" placeholder="服务接入管理员" /></ElFormItem></div><ElFormItem label="基础地址"><ElInput v-model="importForm.baseUrl" placeholder="留空则读取 servers[0].url" /></ElFormItem><ElFormItem label="服务说明"><ElInput v-model="importForm.definition" type="textarea" :rows="2" placeholder="留空则读取 info.description" /></ElFormItem><ElFormItem label="OpenAPI JSON"><ElInput v-model="importForm.documentText" class="openapi-input" type="textarea" :rows="10" placeholder="粘贴 OpenAPI 3.0.x JSON" /></ElFormItem></ElForm><template #footer><ElButton @click="importDialogVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="handleImportService">解析并导入</ElButton></template></ElDialog>
	</div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Bell, ChromeFilled, Coin, DataLine, Document, Edit, Plus, Search, SetUp } from "@element-plus/icons-vue";

import { apiServiceApi } from "./api";
import ApiEditorDialog from "./components/ApiEditorDialog.vue";
import type { ApiCallLog, ApiDefinition, ApiError, ApiMapping, ApiService, ApiSnapshot } from "./types";

const services = ref<ApiService[]>([]);
const selectedId = ref("");
const searchText = ref("");
const statusFilter = ref("");
const loading = ref(false);
const saving = ref(false);
const testingId = ref("");
const error = ref<ApiError | null>(null);
const activeTab = ref("apis");
const refreshing = ref(false);
const snapshots = ref<ApiSnapshot[]>([]);
const logs = ref<ApiCallLog[]>([]);
const mappings = ref<ApiMapping[]>([]);
const serviceDialogVisible = ref(false);
const apiDialogVisible = ref(false);
const importDialogVisible = ref(false);
const mappingDialogVisible = ref(false);
const editingService = ref<ApiService | null>(null);
const editingApi = ref<ApiDefinition | null>(null);
const serviceAuthType = ref("NONE");
const serviceForm = reactive<Partial<ApiService>>({ id: "", name: "", definition: "", domainId: "DOMAIN-OPS", owner: "", version: "v1", baseUrl: "", environment: "测试", timeoutMs: 10000 });
const importForm = reactive({ id: "", name: "", definition: "", version: "", baseUrl: "", environment: "测试", domainId: "DOMAIN-OPS", owner: "服务接入管理员", documentText: "" });
const mappingForm = reactive({ apiId: "", targetRef: "", mappingKind: "RESULT", description: "" });

const filteredServices = computed(() => services.value.filter(item => (!statusFilter.value || item.status === statusFilter.value) && (!searchText.value || `${item.id} ${item.name} ${item.definition}`.toLowerCase().includes(searchText.value.toLowerCase()))));
const selectedService = computed(() => services.value.find(item => item.id === selectedId.value));
const statusLabel = (status?: string, enabled?: boolean) => status === "PUBLISHED" ? (enabled ? "已发布 · 启用" : "已发布 · 禁用") : "草稿";
const statusType = (status?: string, enabled?: boolean) => status === "PUBLISHED" && enabled ? "success" : "info";
const serviceIcon = (id: string) => id.includes("CMDB") ? Coin : id.includes("METRIC") || id.includes("OBS") ? DataLine : id.includes("ALERT") ? Bell : id.includes("LOG") ? Document : id.includes("CHANGE") || id.includes("RELEASE") ? SetUp : ChromeFilled;
const formatUpdatedAt = (value?: string) => value ? value.replace("T", " ").slice(0, 16) : "—";

function missingPublishFields(service: ApiService): string[] {
	const missing = [
		["服务名称", service.name],
		["业务说明", service.definition],
		["版本", service.version],
		["环境", service.environment],
		["领域", service.domainId],
		["责任方", service.owner],
		["基础地址", service.baseUrl]
	].filter(([, value]) => !String(value ?? "").trim()).map(([label]) => label);
	for (const api of service.apis) {
		const apiLabel = api.name?.trim() || api.id || "API";
		if (!api.name?.trim()) missing.push(`${apiLabel}名称`);
		if (!api.definition?.trim()) missing.push(`${apiLabel}业务定义`);
		if (!api.method?.trim()) missing.push(`${apiLabel}HTTP 方法`);
		if (!api.path?.trim()) missing.push(`${apiLabel}请求路径`);
	}
	return missing;
}

async function loadServices() {
	loading.value = true;
	error.value = null;
	try {
		services.value = await apiServiceApi.list();
		if (!selectedId.value && services.value.length) selectedId.value = services.value[0].id;
		if (selectedId.value) await selectService(selectedId.value);
	}
	catch (cause) { error.value = cause as ApiError; }
	finally { loading.value = false; }
}
async function selectService(id: string) {
	selectedId.value = id;
	const item = services.value.find(service => service.id === id);
	if (!item) return;
	try {
		const [service, snapshotItems, logItems, mappingItems] = await Promise.all([
			apiServiceApi.get(id),
			apiServiceApi.snapshots(id),
			apiServiceApi.callLogs(id),
			apiServiceApi.mappings(id)
		]);
		services.value.splice(services.value.indexOf(item), 1, service);
		snapshots.value = snapshotItems;
		logs.value = logItems;
		mappings.value = mappingItems;
	}
	catch (cause) { error.value = cause as ApiError; }
}
function openCreateService() { editingService.value = null; serviceAuthType.value = "NONE"; Object.assign(serviceForm, { id: "", name: "", definition: "", domainId: "DOMAIN-OPS", owner: "", version: "v1", baseUrl: "", environment: "测试", timeoutMs: 10000 }); serviceDialogVisible.value = true; }
function openEditService() { if (!selectedService.value) return; editingService.value = selectedService.value; serviceAuthType.value = selectedService.value.auth?.authType || "NONE"; Object.assign(serviceForm, selectedService.value); serviceDialogVisible.value = true; }
function openCreateApi() { editingApi.value = null; apiDialogVisible.value = true; }
function openEditApi(api: ApiDefinition) { editingApi.value = api; apiDialogVisible.value = true; }
function apiName(id: string) { return selectedService.value?.apis.find(api => api.id === id)?.name || id; }
async function loadLogs() {
	if (!selectedService.value) return;
	try { logs.value = await apiServiceApi.callLogs(selectedService.value.id); }
	catch (cause) { ElMessage.error((cause as ApiError).message || "调用记录加载失败"); }
}
async function createSnapshot() {
	if (!selectedService.value) return;
	refreshing.value = true;
	try {
		await apiServiceApi.createSnapshot(selectedService.value.id);
		snapshots.value = await apiServiceApi.snapshots(selectedService.value.id);
		ElMessage.success("API 快照已生成");
	}
	catch (cause) { ElMessage.error((cause as ApiError).message || "快照生成失败"); }
	finally { refreshing.value = false; }
}
function openCreateMapping() {
	mappingForm.apiId = selectedService.value?.apis[0]?.id || "";
	mappingForm.targetRef = "";
	mappingForm.mappingKind = "RESULT";
	mappingForm.description = "";
	mappingDialogVisible.value = true;
}
async function saveMapping() {
	if (!selectedService.value || !mappingForm.apiId || !mappingForm.targetRef.trim()) {
		ElMessage.error("请选择 API 并填写目标引用");
		return;
	}
	saving.value = true;
	try {
		await apiServiceApi.createMapping({
			id: `MAP-${Date.now().toString().slice(-8)}`,
			serviceId: selectedService.value.id,
			apiId: mappingForm.apiId,
			targetRef: mappingForm.targetRef.trim(),
			mappingKind: mappingForm.mappingKind,
			description: mappingForm.description.trim()
		});
		mappings.value = await apiServiceApi.mappings(selectedService.value.id);
		mappingDialogVisible.value = false;
		ElMessage.success("映射已保存");
	}
	catch (cause) { ElMessage.error((cause as ApiError).message || "保存映射失败"); }
	finally { saving.value = false; }
}
async function removeMapping(mapping: ApiMapping) {
	try {
		await ElMessageBox.confirm(`确认删除映射“${mapping.id}”？`, "请确认", { type: "warning" });
		await apiServiceApi.removeMapping(mapping.id);
		if (selectedService.value) mappings.value = await apiServiceApi.mappings(selectedService.value.id);
	}
	catch (cause) {
		if (cause !== "cancel") ElMessage.error((cause as ApiError).message || "删除映射失败");
	}
}
async function saveService() { if (!serviceForm.id) { ElMessage.warning("请填写服务标识"); return; } saving.value = true; try { const body = { id: serviceForm.id, name: serviceForm.name, definition: serviceForm.definition, domainId: serviceForm.domainId, owner: serviceForm.owner, version: serviceForm.version, baseUrl: serviceForm.baseUrl, environment: serviceForm.environment, timeoutMs: serviceForm.timeoutMs, auth: { authType: serviceAuthType.value, configured: editingService.value?.auth?.configured ?? false, masked: editingService.value?.auth?.masked || "未配置" } }; const updated = editingService.value ? await apiServiceApi.update(editingService.value.id, { ...body, revision: editingService.value.revision }) : await apiServiceApi.create(body); await loadServices(); selectedId.value = updated.id; await selectService(updated.id); serviceDialogVisible.value = false; ElMessage.success("API 服务草稿已保存"); } catch (cause) { ElMessage.error((cause as ApiError).message || "保存失败"); } finally { saving.value = false; } }
async function saveApi(body: Partial<ApiDefinition>) { if (!selectedService.value) return; saving.value = true; try { if (editingApi.value) await apiServiceApi.updateApi(selectedService.value.id, editingApi.value.id, body); else await apiServiceApi.createApi(selectedService.value.id, body); await selectService(selectedService.value.id); apiDialogVisible.value = false; ElMessage.success(editingApi.value ? "API 编辑成功" : "API 新增成功"); } catch (cause) { ElMessage.error((cause as ApiError).message || "API 保存失败"); } finally { saving.value = false; } }
async function testApi(api: ApiDefinition) { if (!selectedService.value) return; testingId.value = api.id; try { const result = await apiServiceApi.testApi(selectedService.value.id, api.id); await selectService(selectedService.value.id); ElMessage[result.outcome === "SUCCESS" ? "success" : "error"](result.message); } catch (cause) { ElMessage.error((cause as ApiError).message || "测试失败"); } finally { testingId.value = ""; } }
async function publishService() { if (!selectedService.value) return; const missing = missingPublishFields(selectedService.value); if (missing.length) { ElMessage.error(`请补全必填项：${missing.join("、")}`); return; } await changeServiceState(() => apiServiceApi.publish(selectedService.value!.id, selectedService.value!.revision), "API 服务已发布"); }
async function unpublishService() { if (!selectedService.value) return; await changeServiceState(() => apiServiceApi.unpublish(selectedService.value!.id, selectedService.value!.revision), "API 服务已下架"); }
async function enableService() { if (!selectedService.value) return; await changeServiceState(() => apiServiceApi.enable(selectedService.value!.id, selectedService.value!.revision), "API 服务已启用"); }
async function disableService() { if (!selectedService.value) return; await changeServiceState(() => apiServiceApi.disable(selectedService.value!.id, selectedService.value!.revision), "API 服务已禁用"); }
async function changeServiceState(action: () => Promise<ApiService>, message: string) { saving.value = true; try { const updated = await action(); services.value.splice(services.value.findIndex(item => item.id === updated.id), 1, updated); ElMessage.success(message); } catch (cause) { ElMessage.error((cause as ApiError).message || "状态更新失败"); } finally { saving.value = false; } }
async function deleteApi(api: ApiDefinition) { if (!selectedService.value) return; try { await ElMessageBox.confirm(`确认删除 API “${api.name || api.id}”？`, "请确认", { type: "warning" }); await apiServiceApi.deleteApi(selectedService.value.id, api.id); await selectService(selectedService.value.id); ElMessage.success("API 已删除"); } catch (cause) { if (cause !== "cancel") ElMessage.error((cause as ApiError).message || "操作失败"); } }
async function deleteService() { if (!selectedService.value) return; try { await ElMessageBox.confirm(`确认删除草稿服务“${selectedService.value.name || selectedService.value.id}”？`, "请确认", { type: "warning" }); await apiServiceApi.remove(selectedService.value.id, selectedService.value.revision); await loadServices(); ElMessage.success("API 服务已删除"); } catch (cause) { if (cause !== "cancel") ElMessage.error((cause as ApiError).message || "删除失败"); } }
async function handleImportService() { let document: Record<string, unknown>; try { document = JSON.parse(importForm.documentText); } catch { ElMessage.error("请输入有效的 JSON 文档"); return; } saving.value = true; try { const options = Object.fromEntries(Object.entries(importForm).filter(([key, value]) => key !== "documentText" && typeof value === "string" && value.trim())); const created = await apiServiceApi.importOpenApi(options, document); await loadServices(); selectedId.value = created.id; await selectService(created.id); importDialogVisible.value = false; Object.assign(importForm, { id: "", name: "", definition: "", version: "", baseUrl: "", environment: "测试", domainId: "DOMAIN-OPS", owner: "服务接入管理员", documentText: "" }); ElMessage.success(`已导入服务 ${created.name}`); } catch (cause) { ElMessage.error((cause as ApiError).message || "OpenAPI 导入失败"); } finally { saving.value = false; } }
onMounted(loadServices);
</script>

<style src="./service-integration.css"></style>
