<template>
	<ElDialog
		v-model="visible"
		class="integration-dialog api-debug-dialog api-workbench-dialog"
		title="调试 API"
		width="1280px"
		align-center
		destroy-on-close
		:close-on-click-modal="false"
		:close-on-press-escape="!sending"
		:show-close="!sending"
	>
		<template #header
			><div class="api-workbench-heading">
				<span class="api-workbench-icon"
					><ElIcon><Connection /></ElIcon></span
				><span>{{ service?.name || "API 服务" }}</span
				><span class="api-heading-divider">/</span><strong>{{ api?.name || "调试 API" }}</strong
				><span class="api-heading-id">{{ api?.id }}</span>
			</div></template
		>
		<div class="api-workbench-navigation">
			<div class="api-mode-tabs" role="tablist" aria-label="API 工作区">
				<button type="button" role="tab" aria-selected="false" :disabled="sending" @click="emit('edit')">编辑</button
				><button type="button" role="tab" aria-selected="true" class="is-active">调试</button>
			</div>
			<span class="api-debug-environment"
				><span class="api-environment-dot"></span>{{ service?.environment || "默认环境"
				}}<span class="api-caption-divider">·</span>{{ service?.version || "v1" }}</span
			>
		</div>
		<div class="api-request-form">
			<div class="api-request-toolbar">
				<div class="api-request-address" :class="api?.method?.toLowerCase()">
					<span class="api-debug-method">{{ api?.method }}</span
					><ElIcon class="api-address-link"><Link /></ElIcon
					><ElInput :model-value="requestUrl" class="api-request-url" readonly aria-label="请求地址" />
				</div>
				<ElButton type="primary" :icon="Position" :loading="sending" @click="send">发送</ElButton
				><ElButton :disabled="sending" :icon="RefreshLeft" @click="reset">重置</ElButton>
			</div>
			<div class="api-request-caption">
				<span>请求地址根据参数自动生成</span><span class="api-caption-divider">·</span
				><span>{{ api?.timeoutMs ?? service?.timeoutMs ?? 10000 }} ms 超时</span>
			</div>
		</div>
		<div class="api-debug-workspace" :style="{ '--api-request-ratio': requestRatio + '%' }">
			<div class="api-debug-request-pane">
				<ElTabs v-model="tab" class="api-request-tabs api-debug-tabs">
					<ElTabPane name="params"
						><template #label
							>Params
							<span v-if="pathRows.length + queryRows.length" class="api-tab-count">{{
								pathRows.length + queryRows.length
							}}</span></template
						>
						<div v-for="group in parameterGroups" :key="group.location" class="api-debug-section">
							<h4>{{ group.title }}</h4>
							<ElTable :data="group.rows" empty-text="暂无参数" class="api-debug-parameter-table"
								><ElTableColumn width="44" align="center"
									><template #default="{ row }"
										><ElCheckbox
											:model-value="isEnabled(group.location, row)"
											:disabled="sending || group.location === 'path' || row.required"
											:aria-label="`启用 ${row.name}`"
											@change="enabled[group.location + ':' + row.name] = Boolean($event)" /></template></ElTableColumn
								><ElTableColumn prop="name" label="参数名" min-width="180"
									><template #default="{ row }"
										><span class="api-debug-parameter-name">{{ row.name }}<sup v-if="row.required">*</sup></span></template
									></ElTableColumn
								><ElTableColumn label="参数值" min-width="260"
									><template #default="{ row }"
										><ElInput
											v-model="group.values[String(row.name)]"
											:disabled="sending || !isEnabled(group.location, row)"
											:aria-label="`${row.name} 参数值`"
											placeholder="输入参数值" /></template></ElTableColumn
								><ElTableColumn label="类型" width="120"
									><template #default="{ row }"
										><span class="api-parameter-type" :data-api-schema-type="row.schema?.type || 'string'">{{
											row.schema?.type || "string"
										}}</span></template
									></ElTableColumn
								><ElTableColumn prop="description" label="说明" min-width="240"
									><template #default="{ row }"
										><span class="api-debug-description">{{ row.description || "—" }}</span></template
									></ElTableColumn
								></ElTable
							>
						</div></ElTabPane
					>
					<ElTabPane label="Body" name="body"
						><template v-if="bodyFormat"
							><div class="api-debug-body-toolbar">
								<ElTag effect="plain">{{ bodyFormat }}</ElTag
								><ElButton v-if="bodyFormat.includes('json')" text :disabled="sending" @click="formatBody">格式化 JSON</ElButton>
							</div>
							<ElInput
								v-model="bodyText"
								type="textarea"
								:rows="9"
								class="api-json-input"
								aria-label="请求 Body"
								:disabled="sending"
								placeholder="填写请求 Body"
						/></template>
						<div v-else class="api-body-empty">
							<ElIcon><Document /></ElIcon><span>此 API 未配置请求体</span>
						</div></ElTabPane
					>
					<ElTabPane name="headers"
						><template #label
							>Headers <span v-if="headerRows.length" class="api-tab-count">{{ headerRows.length }}</span></template
						>
						<p class="api-editor-hint api-tab-description">认证信息和 Content-Type 会随请求自动附加。</p>
						<ElTable :data="headerRows" empty-text="暂无自定义 Header" class="api-debug-parameter-table"
							><ElTableColumn width="44" align="center"
								><template #default="{ row }"
									><ElCheckbox
										:model-value="isEnabled('header', row)"
										:disabled="sending || row.required"
										:aria-label="`启用 ${row.name}`"
										@change="enabled['header:' + row.name] = Boolean($event)" /></template></ElTableColumn
							><ElTableColumn prop="name" label="Header 名称" min-width="220" /><ElTableColumn label="参数值" min-width="300"
								><template #default="{ row }"
									><ElInput
										v-model="headerValues[String(row.name)]"
										:disabled="sending || !isEnabled('header', row)"
										:aria-label="`${row.name} Header 值`"
										placeholder="输入 Header 值" /></template></ElTableColumn
							><ElTableColumn prop="description" label="说明" min-width="240" /></ElTable
					></ElTabPane>
					<ElTabPane name="auth"
						><template #label>Auth <span class="api-tab-count">1</span></template>
						<div class="api-auth-summary">
							<strong>请求认证</strong><span class="api-editor-hint">默认使用接口配置的认证方式，修改仅对本次调试生效。</span>
						</div>
						<ElForm label-position="top" :disabled="sending"><ApiAuthEditor v-model="auth" /></ElForm
					></ElTabPane>
					<ElTabPane name="variables"
						><template #label
							>Variables <span v-if="variableRows.length" class="api-tab-count">{{ variableRows.length }}</span></template
						><ElTable v-if="variableRows.length" :data="variableRows" class="api-debug-parameter-table"
							><ElTableColumn prop="name" label="变量名" min-width="240" /><ElTableColumn label="当前值" min-width="360"
								><template #default="{ row }"
									><ElInput
										v-model="variableValues[row.name]"
										:disabled="sending"
										:aria-label="`${row.name} 变量值`"
										placeholder="输入变量值" /></template></ElTableColumn
						></ElTable>
						<div v-else class="api-body-empty">
							<ElIcon><SetUp /></ElIcon><span>当前请求未使用环境变量</span>
						</div></ElTabPane
					>
				</ElTabs>
			</div>
			<div
				class="api-debug-splitter"
				role="separator"
				aria-label="调整请求与响应区域高度"
				aria-orientation="horizontal"
				tabindex="0"
				:aria-valuenow="requestRatio"
				:aria-valuemin="30"
				:aria-valuemax="70"
				@pointerdown="startResize"
				@keydown.up.prevent="requestRatio = Math.max(30, requestRatio - 5)"
				@keydown.down.prevent="requestRatio = Math.min(70, requestRatio + 5)"
			>
				<span></span>
			</div>
			<section
				v-loading="sending"
				element-loading-text="正在发送请求…"
				class="api-debug-response-pane"
				aria-label="返回响应"
				aria-live="polite"
			>
				<div class="api-debug-response-heading">
					<h3>返回响应</h3>
					<div v-if="result" class="api-response-metrics">
						<span
							:class="result.response?.status && result.response.status < 400 ? 'api-response-success' : 'api-response-failed'"
							>{{ result.response?.status ?? result.category }}</span
						><span>{{ result.latencyMs }} ms</span><span>{{ responseSize }}</span>
					</div>
					<span v-else class="api-editor-hint">尚未发送请求</span>
				</div>
				<ElAlert
					v-if="error || localError"
					:title="error || localError"
					type="error"
					:closable="false"
					show-icon
					class="api-debug-error"
				/>
				<template v-if="result"
					><ElTabs v-model="responseTab" class="api-response-tabs"
						><ElTabPane label="Body" name="body" /><ElTabPane
							:label="`Headers (${Object.keys(result.response?.headers ?? {}).length})`"
							name="headers" /><ElTabPane label="实际请求" name="request"
					/></ElTabs>
					<div class="api-debug-response-content">
						<template v-if="responseTab === 'body'"
							><div class="api-response-code-toolbar">
								<span>{{ isJsonResponse ? "JSON" : "Raw" }}</span
								><ElButton text size="small" :icon="CopyDocument" @click="copyResponse">复制</ElButton>
							</div>
							<pre class="api-response-code">{{ formattedResponse }}</pre></template
						><ElTable
							v-else-if="responseTab === 'headers'"
							:data="Object.entries(result.response?.headers ?? {}).map(([name, value]) => ({ name, value }))"
							empty-text="暂无响应 Header"
							><ElTableColumn prop="name" label="Header 名称" min-width="220" /><ElTableColumn
								prop="value"
								label="值"
								min-width="400"
						/></ElTable>
						<pre v-else class="api-response-code">{{ JSON.stringify(result.request ?? {}, null, 2) }}</pre>
					</div></template
				>
				<div v-else class="api-debug-response-empty">
					<div class="api-response-empty-icon">
						<ElIcon><Position /></ElIcon>
					</div>
					<strong>{{ error ? "请求未完成" : "准备就绪，发送你的第一个请求" }}</strong>
					<p>{{ error ? "检查请求配置后重新发送" : "点击「发送」按钮，查看返回结果与响应详情" }}</p>
				</div>
			</section>
		</div>
	</ElDialog>
</template>

<script setup lang="ts">
import { Connection, CopyDocument, Document, Link, Position, RefreshLeft, SetUp } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { copyJson, displayValue, parameterSchema } from "../api-contract";
import type { ApiAuthConfig, ApiDebugRequest, ApiDefinition, ApiParameter, ApiService, ApiTestResult } from "../types";
import ApiAuthEditor from "./ApiAuthEditor.vue";
import "./api-editor.css";
import "./api-workbench.css";

const props = defineProps<{
	modelValue: boolean;
	api: ApiDefinition | null;
	service?: ApiService;
	sending?: boolean;
	result?: ApiTestResult | null;
	error?: string;
}>();
const emit = defineEmits<{ "update:modelValue": [value: boolean]; send: [value: ApiDebugRequest]; edit: [] }>();
const visible = computed({ get: () => props.modelValue, set: value => emit("update:modelValue", value) });
const sending = computed(() => props.sending ?? false);
const result = computed(() => props.result ?? null);
const error = computed(() => props.error ?? "");
const localError = ref("");
const tab = ref("params");
const responseTab = ref("body");
const requestRatio = ref(48);
const pathValues = ref<Record<string, string>>({});
const queryValues = ref<Record<string, string>>({});
const headerValues = ref<Record<string, string>>({});
const variableValues = ref<Record<string, string>>({});
const enabled = ref<Record<string, boolean>>({});
const auth = ref<ApiAuthConfig>({ authType: "INHERIT", inherit: true });
const bodyText = ref("");
let resizeContainer: HTMLElement | null = null;
const pathRows = computed(() => parameters("path"));
const queryRows = computed(() => parameters("query"));
const headerRows = computed(() => parameters("header"));
const parameterGroups = computed(() => [
	{ location: "query", title: "Query 参数", rows: queryRows.value, values: queryValues.value },
	{ location: "path", title: "Path 参数", rows: pathRows.value, values: pathValues.value }
]);
const variableRows = computed(() => {
	const source = `${props.service?.baseUrl ?? ""} ${props.api?.path ?? ""} ${JSON.stringify(props.api?.contract ?? {})}`;
	const names = [...source.matchAll(/{{\s*([^}]+?)\s*}}/g)].map(match => match[1].trim()).filter(Boolean);
	return [...new Set(names)].map(name => ({ name }));
});
const requestUrl = computed(() => {
	const base = resolveVariables(props.service?.baseUrl?.replace(/\/+$/, "") ?? "");
	const path = resolveVariables(props.api?.path ?? "").replace(/(?<!{){([^{}]+)}(?!})/g, (placeholder, name) =>
		pathValues.value[name] ? encodeURIComponent(resolveVariables(pathValues.value[name])) : placeholder
	);
	const query = new URLSearchParams(
		Object.fromEntries(
			Object.entries(selectedValues("query", queryValues.value)).map(([name, value]) => [name, resolveVariables(value)])
		)
	).toString();
	return `${base}/${path.replace(/^\/+/, "")}${query ? "?" + query : ""}`;
});
function resolveVariables(value: string) {
	return value.replace(/{{\s*([^}]+?)\s*}}/g, (placeholder, name) => variableValues.value[name] || placeholder);
}
const bodyFormat = computed(() => Object.keys(props.api?.contract?.requestBody?.content ?? {})[0] ?? "");
const formattedResponse = computed(() => {
	const body = result.value?.response?.body ?? result.value?.message ?? "";
	try {
		return JSON.stringify(JSON.parse(body), null, 2);
	} catch {
		return body;
	}
});
const isJsonResponse = computed(() => {
	try {
		JSON.parse(result.value?.response?.body ?? "");
		return true;
	} catch {
		return false;
	}
});
const responseSize = computed(() => {
	const size = new TextEncoder().encode(result.value?.response?.body ?? "").length;
	return size < 1024 ? `${size} B` : `${(size / 1024).toFixed(2)} KB`;
});
function parameters(location: string): ApiParameter[] {
	return (props.api?.contract?.parameters ?? []).filter(row => row.in === location && row.name);
}
function initialValue(row: ApiParameter) {
	const schema = parameterSchema(row);
	return displayValue(row.example ?? schema.example ?? schema.default ?? "");
}
function isEnabled(location: string, row: ApiParameter) {
	return location === "path" || Boolean(row.required) || enabled.value[`${location}:${row.name}`] !== false;
}
function selectedValues(location: string, values: Record<string, string>) {
	return Object.fromEntries(
		parameters(location)
			.filter(row => isEnabled(location, row))
			.map(row => [row.name!, values[row.name!] ?? ""])
	);
}
function reset() {
	pathValues.value = Object.fromEntries(pathRows.value.map(row => [row.name!, initialValue(row)]));
	queryValues.value = Object.fromEntries(queryRows.value.map(row => [row.name!, initialValue(row)]));
	headerValues.value = Object.fromEntries(
		headerRows.value.map(row => [row.name!, String(row["x-fixed-value"] ?? initialValue(row))])
	);
	variableValues.value = Object.fromEntries(variableRows.value.map(row => [row.name, ""]));
	enabled.value = {};
	auth.value = copyJson((props.api?.contract?.["x-auth"] as ApiAuthConfig | undefined) ?? { authType: "INHERIT", inherit: true });
	localError.value = "";
	const media = props.api?.contract?.requestBody?.content?.[bodyFormat.value];
	bodyText.value =
		media?.example === undefined
			? ""
			: !bodyFormat.value.includes("json") && typeof media.example === "string"
				? media.example
				: JSON.stringify(media.example, null, 2);
}
watch(
	() => [props.modelValue, props.api?.id] as const,
	([open]) => {
		if (open) {
			reset();
			tab.value = "params";
			responseTab.value = "body";
		} else stopResize();
	},
	{ immediate: true }
);
watch(result, () => {
	responseTab.value = "body";
});
function formatBody() {
	try {
		bodyText.value = JSON.stringify(JSON.parse(bodyText.value), null, 2);
		localError.value = "";
	} catch {
		localError.value = "请求 Body 不是有效的 JSON，请检查后重试";
	}
}
function send() {
	if (sending.value) return;
	localError.value = "";
	let body: unknown;
	if (bodyText.value.trim()) {
		try {
			body = bodyFormat.value.includes("json") ? JSON.parse(bodyText.value) : bodyText.value;
		} catch {
			localError.value = "请求 Body 不是有效的 JSON，请检查后发送";
			tab.value = "body";
			return;
		}
	}
	emit("send", {
		path: pathValues.value,
		query: selectedValues("query", queryValues.value),
		headers: selectedValues("header", headerValues.value),
		body,
		auth: auth.value,
		variables: variableValues.value
	});
}
async function copyResponse() {
	try {
		await navigator.clipboard.writeText(formattedResponse.value);
		ElMessage.success("响应内容已复制");
	} catch {
		ElMessage.warning("无法访问剪贴板，请手动复制响应内容");
	}
}
function startResize(event: PointerEvent) {
	if (event.button !== 0) return;
	event.preventDefault();
	resizeContainer = (event.currentTarget as HTMLElement).parentElement;
	window.addEventListener("pointermove", resize);
	window.addEventListener("pointerup", stopResize, { once: true });
	window.addEventListener("pointercancel", stopResize, { once: true });
}
function resize(event: PointerEvent) {
	const bounds = resizeContainer?.getBoundingClientRect();
	if (bounds?.height) requestRatio.value = Math.min(70, Math.max(30, ((event.clientY - bounds.top) / bounds.height) * 100));
}
function stopResize() {
	resizeContainer = null;
	if (typeof window === "undefined") return;
	window.removeEventListener("pointermove", resize);
	window.removeEventListener("pointerup", stopResize);
	window.removeEventListener("pointercancel", stopResize);
}
onBeforeUnmount(stopResize);
</script>
