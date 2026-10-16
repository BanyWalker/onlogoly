<template>
	<ElDialog
		:model-value="modelValue"
		class="integration-dialog api-editor-dialog api-workbench-dialog"
		:title="api ? '编辑 API' : '新增 API'"
		width="1280px"
		align-center
		destroy-on-close
		:close-on-click-modal="false"
		:close-on-press-escape="!saving"
		:show-close="!saving"
		@update:model-value="emit('update:modelValue', $event)"
	>
		<template #header>
			<div class="api-workbench-heading">
				<span class="api-workbench-icon"
					><ElIcon><Connection /></ElIcon></span
				><span>{{ service?.name || "API 服务" }}</span
				><span class="api-heading-divider">/</span><strong>{{ api ? "编辑 API" : "新增 API" }}</strong
				><span class="api-heading-id">{{ form.id }}</span>
				<span class="api-heading-context" aria-label="请求环境与地址">
					<span class="api-heading-environment"
						><span class="api-environment-dot"></span>{{ service?.environment || "默认环境" }}</span
					>
					<span class="api-caption-divider">·</span>
					<span class="api-request-full-url" :title="requestUrl">{{ requestUrl }}</span>
				</span>
			</div>
		</template>
		<div class="api-workbench-navigation">
			<div class="api-mode-tabs" role="tablist" aria-label="API 工作区">
				<button type="button" role="tab" aria-selected="true" class="is-active">编辑</button
				><ElTooltip content="保存当前配置后进入调试"
					><button type="button" role="tab" aria-selected="false" :disabled="saving" @click="save(true)">调试</button></ElTooltip
				>
			</div>
			<ElButton text :icon="Document" :disabled="saving" @click="toggleAdvanced">{{
				advanced ? "返回可视化配置" : "高级 JSON"
			}}</ElButton>
		</div>
		<ElForm :model="form" class="api-request-form" @submit.prevent="save()">
			<div class="api-request-toolbar">
				<div class="api-request-address" :class="form.method?.toLowerCase()">
					<ElSelect v-model="form.method" class="api-request-method" aria-label="HTTP 方法" :disabled="saving"
						><ElOption v-for="method in methods" :key="method" :label="method" :value="method"
					/></ElSelect>
					<ElIcon class="api-address-link"><Link /></ElIcon
					><ElInput
						v-model="form.path"
						class="api-request-url"
						aria-label="请求路径"
						placeholder="输入请求路径，例如 /api/hosts/{hostId}"
						:disabled="saving"
					/>
				</div>
				<ElButton type="primary" :loading="saving" :icon="Check" @click="save()">保存</ElButton
				><ElTooltip content="保存当前配置后进入调试"
					><ElButton :disabled="saving" :icon="VideoPlay" @click="save(true)">调试</ElButton></ElTooltip
				>
			</div>
			<p v-if="basicErrors.path || basicErrors.method" class="api-editor-error-text">
				{{ basicErrors.path || basicErrors.method }}
			</p>
		</ElForm>
		<ElAlert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="api-editor-error" />
		<div class="api-editor-scroll">
			<section class="api-overview-section">
				<div class="api-section-heading">
					<div>
						<h2>{{ form.name || "未命名 API" }}</h2>
						<span class="api-status-dot" :class="{ 'is-published': api?.status === 'PUBLISHED' }"></span
						><span class="api-editor-hint">{{ api?.status === "PUBLISHED" ? "已发布" : "草稿" }}</span
						><span v-if="service?.owner" class="api-owner"
							><ElIcon><User /></ElIcon>{{ service.owner }}</span
						>
					</div>
					<ElButton
						text
						:icon="metadataCollapsed ? ArrowDown : ArrowUp"
						:aria-expanded="!metadataCollapsed"
						@click="metadataCollapsed = !metadataCollapsed"
						>{{ metadataCollapsed ? "展开" : "收起" }}</ElButton
					>
				</div>
				<ElForm v-show="!metadataCollapsed" :model="form" label-position="top" class="api-basic-form">
					<div class="api-form-grid">
						<ElFormItem label="API 名称" required :error="basicErrors.name"
							><ElInput v-model="form.name" placeholder="例如：查询主机详情" :disabled="saving" /></ElFormItem
						><ElFormItem label="API 标识" required :error="basicErrors.id"
							><ElInput v-model="form.id" :disabled="Boolean(api) || saving" placeholder="例如 API-HOST-DETAIL"
						/></ElFormItem>
					</div>
					<ElFormItem label="业务定义" required :error="basicErrors.definition"
						><ElInput
							v-model="form.definition"
							type="textarea"
							:rows="2"
							resize="vertical"
							placeholder="描述此 API 提供的业务能力与使用场景"
							:disabled="saving"
					/></ElFormItem>
				</ElForm>
			</section>
			<section v-if="advanced" class="api-editor-advanced api-workbench-section">
				<div class="api-section-heading">
					<h3>API 契约</h3>
					<span class="api-editor-hint">JSON</span>
				</div>
				<p class="api-editor-hint">编辑 Header、入参和出参的完整契约。返回可视化配置时将校验并应用。</p>
				<ElInput
					v-model="contractText"
					type="textarea"
					:rows="22"
					class="api-json-input"
					aria-label="API 契约 JSON"
					:disabled="saving"
				/>
			</section>
			<template v-else>
				<section class="api-workbench-section api-request-section">
					<div class="api-section-heading">
						<h3>请求参数</h3>
						<span class="api-editor-hint">配置接口的请求结构</span>
					</div>
					<ElTabs v-model="requestTab" class="api-request-tabs">
						<ElTabPane name="params"
							><template #label
								>Params
								<span v-if="paths.length + queries.length" class="api-tab-count">{{
									paths.length + queries.length
								}}</span></template
							>
							<div class="api-parameter-section">
								<h4>Query 参数</h4>
								<ApiParameterEditor v-model="queries" location="query" />
							</div>
							<div class="api-parameter-section">
								<h4>Path 参数 <span class="api-editor-hint">由路径中的 {参数名} 自动生成</span></h4>
								<ApiParameterEditor v-model="paths" location="path" :locked-names="pathParameterNames(form.path ?? '')" /></div
						></ElTabPane>
						<ElTabPane label="Body" name="body">
							<div v-if="!isBodyMethod(form.method)" class="api-body-notice">
								<span class="api-editor-hint">{{ form.method }} 默认不使用请求体</span
								><ElCheckbox :model-value="contract['x-allow-request-body'] === true" @change="setCustomBody(Boolean($event))"
									>为此接口配置请求体</ElCheckbox
								><ElButton v-if="!bodyAllowed && hasBody" text type="danger" @click="clearBody">清空已有请求体</ElButton>
							</div>
							<div v-if="bodyAllowed" class="api-editor-toolbar">
								<ElCheckbox
									:model-value="contract.requestBody?.required ?? false"
									:disabled="Boolean(contract.requestBody?.$ref) || !hasBody"
									@change="setBodyRequired(Boolean($event))"
									>请求体必填</ElCheckbox
								>
							</div>
							<ElAlert
								v-if="bodyAllowed && contract.requestBody?.$ref"
								title="请求体使用引用定义，请通过高级 JSON 编辑。"
								type="info"
								:closable="false"
							/><ApiContentEditor v-else-if="bodyAllowed" v-model="requestContent" single />
							<div v-else class="api-body-empty">
								<ElIcon><Document /></ElIcon><span>此接口无需请求体，可在 Params 中配置请求参数</span>
							</div>
						</ElTabPane>
						<ElTabPane name="headers"
							><template #label
								>Headers <span v-if="headers.length" class="api-tab-count">{{ headers.length }}</span></template
							>
							<p class="api-editor-hint api-tab-description">Content-Type 根据请求体生成，Accept 由响应格式决定。</p>
							<ApiParameterEditor v-model="headers" location="header"
						/></ElTabPane>
						<ElTabPane name="auth"
							><template #label>Auth <span class="api-tab-count">1</span></template>
							<div class="api-auth-summary">
								<strong>请求级认证</strong
								><span class="api-editor-hint">默认继承服务认证（{{ authLabel }}），也可为此接口单独配置。</span>
							</div>
							<ElForm label-position="top"><ApiAuthEditor v-model="authDraft" /></ElForm
						></ElTabPane>
						<ElTabPane label="设置" name="settings"
							><ElForm label-position="top" class="api-basic-form api-settings-form"
								><div class="api-form-grid">
									<ElFormItem label="操作性质"
										><ElSelect v-model="form.operationNature" @change="natureOverridden = true"
											><ElOption label="只读" value="READ" /><ElOption label="写操作" value="WRITE" /></ElSelect></ElFormItem
									><ElFormItem label="请求超时（ms）"
										><ElInputNumber v-model="form.timeoutMs" :min="1" :max="60000"
									/></ElFormItem></div></ElForm
						></ElTabPane>
					</ElTabs>
					<p v-if="hiddenParameterCount" class="api-editor-hint">
						另有 {{ hiddenParameterCount }} 个引用或 Cookie 参数已保留，可通过高级 JSON 编辑。
					</p>
				</section>
				<section class="api-workbench-section api-response-section">
					<div class="api-section-heading">
						<h3>返回响应</h3>
						<span class="api-editor-hint">定义状态码、响应结构与示例</span>
					</div>
					<ApiResponseEditor v-model="responses" />
				</section>
			</template>
		</div>
		<template #footer
			><div class="api-workbench-footer">
				<span
					><ElIcon><InfoFilled /></ElIcon>保存接口配置后，可进入调试发送请求</span
				><ElButton :disabled="saving" @click="emit('update:modelValue', false)">取消</ElButton>
			</div></template
		>
	</ElDialog>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowUp, Check, Connection, Document, InfoFilled, Link, User, VideoPlay } from "@element-plus/icons-vue";
import { computed, nextTick, ref, watch } from "vue";

import {
	copyJson,
	createContract,
	isBodyMethod,
	parseContract,
	pathParameterNames,
	replaceParameterGroup,
	suggestedOperationNature,
	syncPathParameters,
	validateContract
} from "../api-contract";
import type { ApiAuthConfig, ApiContract, ApiDefinition, ApiParameter, ApiService } from "../types";
import ApiAuthEditor from "./ApiAuthEditor.vue";
import ApiContentEditor from "./ApiContentEditor.vue";
import ApiParameterEditor from "./ApiParameterEditor.vue";
import ApiResponseEditor from "./ApiResponseEditor.vue";
import "./api-editor.css";
import "./api-workbench.css";

const props = defineProps<{ modelValue: boolean; api: ApiDefinition | null; service?: ApiService; saving: boolean }>();
const emit = defineEmits<{
	"update:modelValue": [value: boolean];
	save: [value: Partial<ApiDefinition>];
	"save-debug": [value: Partial<ApiDefinition>];
}>();
const methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"];
const form = ref<Partial<ApiDefinition>>({});
const contract = ref<ApiContract>(createContract());
const contractText = ref("");
const advanced = ref(false);
const tab = ref("basic");
const inputTab = ref("query");
const metadataCollapsed = ref(false);
const requestTab = computed({
	get: () =>
		["auth", "headers", "settings"].includes(tab.value)
			? tab.value
			: tab.value === "input" && inputTab.value === "body"
				? "body"
				: "params",
	set: value => {
		tab.value = ["auth", "headers", "settings"].includes(value) ? value : "input";
		inputTab.value = value === "body" ? "body" : "query";
	}
});
const formError = ref("");
const initializing = ref(false);
const natureOverridden = ref(false);
const authDraft = ref<ApiAuthConfig>({ authType: "INHERIT", inherit: true });
const submitted = ref(false);
const basicErrors = computed<Record<string, string>>(() => {
	if (!submitted.value) return {};
	const errors: Record<string, string> = {};
	for (const [key, label] of [
		["id", "API 标识"],
		["name", "API 名称"],
		["definition", "业务定义"],
		["method", "HTTP 方法"],
		["path", "请求路径"]
	] as const) {
		if (!form.value[key]?.trim()) errors[key] = `请填写${label}`;
	}
	const path = form.value.path ?? "";
	if (path && (!path.startsWith("/") || /[?#\s]/.test(path) || /[{}]/.test(path.replace(/\{[^{}/\s]+\}/g, ""))))
		errors.path = "请填写以 / 开头的相对路径；查询参数请在入参中配置";
	if (form.value.method && !methods.includes(form.value.method)) errors.method = "请选择有效的 HTTP 方法";
	return errors;
});
const hasBody = computed(
	() => Boolean(contract.value.requestBody?.$ref) || Object.keys(contract.value.requestBody?.content ?? {}).length > 0
);
const bodyAllowed = computed(() => isBodyMethod(form.value.method) || contract.value["x-allow-request-body"] === true);
const hiddenParameterCount = computed(
	() => (contract.value.parameters ?? []).filter(row => !["path", "query", "header"].includes(row.in ?? "")).length
);
const requestUrl = computed(
	() => `${props.service?.baseUrl?.replace(/\/+$/, "") ?? ""}/${form.value.path?.replace(/^\/+/, "") ?? ""}`
);
const authLabel = computed(
	() =>
		({ NONE: "无需认证", API_KEY: "API Key", BEARER: "Bearer Token", CUSTOM_HEADER: "自定义 Header" })[
			props.service?.auth?.authType ?? "NONE"
		] ?? props.service?.auth?.authType
);
function parameterGroup(location: string) {
	return computed<ApiParameter[]>({
		get: () => (contract.value.parameters ?? []).filter(row => row.in === location),
		set: rows => {
			contract.value.parameters = replaceParameterGroup(contract.value.parameters ?? [], location, rows);
		}
	});
}
const headers = parameterGroup("header");
const paths = parameterGroup("path");
const queries = parameterGroup("query");
const requestContent = computed({
	get: () => contract.value.requestBody?.content ?? {},
	set: content => {
		contract.value.requestBody = { ...contract.value.requestBody, content };
		if (!Object.keys(content).length) contract.value.requestBody.required = false;
	}
});
const responses = computed({
	get: () => contract.value.responses ?? {},
	set: value => {
		contract.value.responses = value;
	}
});

watch(
	() => props.modelValue,
	async visible => {
		if (!visible) return;
		initializing.value = true;
		form.value = props.api
			? copyJson(props.api)
			: {
					id: `API-${Date.now().toString().slice(-6)}`,
					name: "",
					definition: "",
					method: "GET",
					path: "",
					operationNature: "READ",
					timeoutMs: props.service?.timeoutMs ?? 10000
				};
		contract.value = copyJson(props.api?.contract ?? createContract());
		authDraft.value = copyJson((contract.value["x-auth"] as ApiAuthConfig | undefined) ?? { authType: "INHERIT", inherit: true });
		advanced.value = false;
		metadataCollapsed.value = false;
		tab.value = "basic";
		inputTab.value = "query";
		formError.value = "";
		submitted.value = false;
		natureOverridden.value = Boolean(
			props.api?.operationNature && props.api.operationNature !== suggestedOperationNature(props.api.method)
		);
		try {
			const next = parseContract(JSON.stringify(contract.value));
			if (!next.parameters?.some(parameter => parameter.$ref) && pathParameterNames(form.value.path ?? "").length) {
				next.parameters = syncPathParameters(next.parameters ?? [], form.value.path ?? "");
			}
			const issue = validateContract(next, form.value.path ?? "");
			if (issue && !["MULTIPLE_REQUEST_FORMATS", "EXAMPLE_REVIEW"].includes(issue.code ?? "")) throw new Error(issue.message);
			contract.value = next;
			if (issue) {
				tab.value = issue.tab;
				inputTab.value = issue.section ?? "body";
				formError.value = issue.message;
			}
		} catch (cause) {
			contractText.value = JSON.stringify(contract.value, null, 2);
			advanced.value = true;
			formError.value = `已有契约需要检查：${cause instanceof Error ? cause.message : "格式无效"}`;
		}
		await nextTick();
		initializing.value = false;
	}
);
watch(
	() => form.value.path,
	(path, previousPath) => {
		if (!props.modelValue || initializing.value || advanced.value) return;
		contract.value.parameters = syncPathParameters(contract.value.parameters ?? [], path ?? "", previousPath);
	}
);
watch(
	() => form.value.method,
	method => {
		if (initializing.value || !props.modelValue) return;
		if (!natureOverridden.value) form.value.operationNature = suggestedOperationNature(method);
		if (
			!initializing.value &&
			!props.api &&
			["POST", "PUT", "PATCH"].includes(method ?? "") &&
			!Object.keys(contract.value.requestBody ?? {}).length
		) {
			contract.value.requestBody = { content: { "application/json": { schema: { type: "object", properties: {} } } } };
		}
	}
);
function setBodyRequired(required: boolean) {
	contract.value.requestBody = { ...contract.value.requestBody, required: bodyAllowed.value && hasBody.value && required };
}
function setCustomBody(allowed: boolean) {
	if (allowed) contract.value["x-allow-request-body"] = true;
	else delete contract.value["x-allow-request-body"];
}
function clearBody() {
	delete contract.value.requestBody;
}
function toggleAdvanced() {
	formError.value = "";
	if (advanced.value) {
		try {
			const next = parseContract(contractText.value);
			const issue = validateContract(next, form.value.path ?? "");
			if (issue && !["MULTIPLE_REQUEST_FORMATS", "EXAMPLE_REVIEW"].includes(issue.code ?? "")) throw new Error(issue.message);
			contract.value = next;
			authDraft.value = copyJson((next["x-auth"] as ApiAuthConfig | undefined) ?? { authType: "INHERIT", inherit: true });
			advanced.value = false;
			if (issue) {
				tab.value = issue.tab;
				inputTab.value = issue.section ?? "body";
				formError.value = issue.message;
			}
		} catch (cause) {
			formError.value = cause instanceof Error ? cause.message : "契约 JSON 无效";
		}
	} else {
		contract.value["x-auth"] = copyJson(authDraft.value);
		contractText.value = JSON.stringify(contract.value, null, 2);
		advanced.value = true;
	}
}
function save(debug = false) {
	if (props.saving) return;
	formError.value = "";
	try {
		const next = advanced.value ? parseContract(contractText.value) : copyJson(contract.value);
		if (!advanced.value) next["x-auth"] = copyJson(authDraft.value);
		submitted.value = true;
		if (Object.keys(basicErrors.value).length) {
			metadataCollapsed.value = false;
			contract.value = next;
			advanced.value = false;
			tab.value = "basic";
			formError.value = "请完善基本信息中的必填项和请求路径";
			return;
		}
		if (
			!isBodyMethod(form.value.method) &&
			next["x-allow-request-body"] !== true &&
			(next.requestBody?.$ref || Object.keys(next.requestBody?.content ?? {}).length)
		) {
			contract.value = next;
			advanced.value = false;
			tab.value = "input";
			inputTab.value = "body";
			formError.value = `${form.value.method} 默认不使用请求体，请显式开启或清空请求体配置`;
			return;
		}
		const issue = validateContract(next, form.value.path ?? "");
		if (issue) {
			tab.value = issue.tab;
			if (issue.tab === "input")
				inputTab.value =
					issue.section ?? (issue.message.includes("路径") ? "path" : issue.message.includes("请求体") ? "body" : "query");
			formError.value = issue.message;
			return;
		}
		const value = {
			...copyJson(form.value),
			id: form.value.id!.trim(),
			name: form.value.name!.trim(),
			definition: form.value.definition!.trim(),
			contract: next
		};
		if (debug) emit("save-debug", value);
		else emit("save", value);
	} catch (cause) {
		formError.value = cause instanceof Error ? cause.message : "契约 JSON 无效";
	}
}
</script>
