<template>
	<ElDialog
		:model-value="modelValue"
		class="integration-dialog api-editor-dialog"
		:title="api ? '编辑 API' : '新增 API'"
		width="1080px"
		align-center
		destroy-on-close
		:close-on-click-modal="false"
		:close-on-press-escape="!saving"
		:show-close="!saving"
		@update:model-value="emit('update:modelValue', $event)"
	>
		<div class="api-editor-context">
			<span
				>{{ service?.name }} <span class="api-editor-hint">/ {{ service?.id }}</span></span
			><ElButton text type="primary" @click="toggleAdvanced">{{ advanced ? "返回可视化配置" : "高级 JSON" }}</ElButton>
		</div>
		<p v-if="!advanced && hiddenParameterCount" class="api-editor-hint">
			另有 {{ hiddenParameterCount }} 个引用或 Cookie 参数已保留，可通过高级 JSON 编辑。
		</p>
		<ElAlert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="api-editor-error" />
		<div class="api-editor-scroll">
			<template v-if="advanced"
				><p class="api-editor-hint">编辑 Header、入参和出参的完整契约。返回可视化配置时将校验并应用。</p>
				<ElInput v-model="contractText" type="textarea" :rows="22" class="api-json-input" aria-label="API 契约 JSON"
			/></template>
			<ElTabs v-else v-model="tab" class="api-editor-tabs">
				<ElTabPane label="基本信息" name="basic">
					<ElForm :model="form" label-position="top" class="api-basic-form">
						<div class="api-form-grid">
							<ElFormItem label="API 标识" required
								><ElInput v-model="form.id" :disabled="Boolean(api)" placeholder="例如 API-TICKET-LIST" /></ElFormItem
							><ElFormItem label="API 名称" required><ElInput v-model="form.name" placeholder="例如：查询工单" /></ElFormItem>
						</div>
						<ElFormItem label="业务定义" required
							><ElInput v-model="form.definition" type="textarea" :rows="2" placeholder="说明此 API 提供的业务能力"
						/></ElFormItem>
						<div class="api-request-line">
							<ElFormItem label="HTTP 方法" required
								><ElSelect v-model="form.method"
									><ElOption v-for="method in methods" :key="method" :label="method" :value="method" /></ElSelect></ElFormItem
							><ElFormItem label="请求路径" required
								><ElInput v-model="form.path" placeholder="/api/hosts/{hostId}"
							/></ElFormItem>
						</div>
						<p class="api-editor-hint api-url-preview">请求地址：{{ requestUrl }}</p>
						<div class="api-form-grid">
							<ElFormItem label="操作性质"
								><ElSelect v-model="form.operationNature"
									><ElOption label="只读" value="READ" /><ElOption label="写操作" value="WRITE" /></ElSelect></ElFormItem
							><ElFormItem label="超时（ms）"><ElInputNumber v-model="form.timeoutMs" :min="1" :max="60000" /></ElFormItem>
						</div>
					</ElForm>
				</ElTabPane>
				<ElTabPane :label="`请求 Header${headers.length ? ` (${headers.length})` : ''}`" name="headers">
					<div class="api-auth-summary">
						<span>认证方式：{{ authLabel }}</span
						><span class="api-editor-hint">继承服务配置；Content-Type 根据请求体生成，Accept 由响应格式决定。</span>
					</div>
					<ApiParameterEditor v-model="headers" location="header" />
				</ElTabPane>
				<ElTabPane label="入参" name="input">
					<ElTabs v-model="inputTab" type="card"
						><ElTabPane :label="`Path (${paths.length})`" name="path"
							><ApiParameterEditor
								v-model="paths"
								location="path"
								:locked-names="pathParameterNames(form.path ?? '')" /></ElTabPane
						><ElTabPane :label="`Query (${queries.length})`" name="query"
							><ApiParameterEditor v-model="queries" location="query" /></ElTabPane
						><ElTabPane label="Body" name="body"
							><div class="api-editor-toolbar">
								<ElCheckbox
									:model-value="contract.requestBody?.required ?? false"
									:disabled="Boolean(contract.requestBody?.$ref)"
									@change="setBodyRequired(Boolean($event))"
									>请求体必填</ElCheckbox
								><span v-if="['GET', 'HEAD'].includes(form.method ?? '')" class="api-editor-hint"
									>{{ form.method }} 通常不使用请求体，已有配置会保留。</span
								>
							</div>
							<ElAlert
								v-if="contract.requestBody?.$ref"
								title="请求体使用引用定义，请通过高级 JSON 编辑。"
								type="info"
								:closable="false" /><ApiContentEditor v-else v-model="requestContent" /></ElTabPane
					></ElTabs>
				</ElTabPane>
				<ElTabPane label="出参" name="output"><ApiResponseEditor v-model="responses" /></ElTabPane>
			</ElTabs>
		</div>
		<template #footer
			><div class="api-editor-footer">
				<div>
					<ElButton :disabled="saving" @click="emit('update:modelValue', false)">取消</ElButton
					><ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
				</div>
			</div></template
		>
	</ElDialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";

import {
	copyJson,
	createContract,
	parseContract,
	pathParameterNames,
	replaceParameterGroup,
	syncPathParameters,
	validateContract
} from "../api-contract";
import type { ApiContract, ApiDefinition, ApiParameter, ApiService } from "../types";
import ApiContentEditor from "./ApiContentEditor.vue";
import ApiParameterEditor from "./ApiParameterEditor.vue";
import ApiResponseEditor from "./ApiResponseEditor.vue";
import "./api-editor.css";

const props = defineProps<{ modelValue: boolean; api: ApiDefinition | null; service?: ApiService; saving: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean]; save: [value: Partial<ApiDefinition>] }>();
const methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"];
const form = ref<Partial<ApiDefinition>>({});
const contract = ref<ApiContract>(createContract());
const contractText = ref("");
const advanced = ref(false);
const tab = ref("basic");
const inputTab = ref("query");
const formError = ref("");
const initializing = ref(false);
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
					path: "/",
					operationNature: "READ",
					timeoutMs: props.service?.timeoutMs ?? 10000
				};
		contract.value = copyJson(props.api?.contract ?? createContract());
		advanced.value = false;
		tab.value = "basic";
		inputTab.value = "query";
		formError.value = "";
		try {
			const next = parseContract(JSON.stringify(contract.value));
			if (!next.parameters?.some(parameter => parameter.$ref) && pathParameterNames(form.value.path ?? "").length) {
				next.parameters = syncPathParameters(next.parameters ?? [], form.value.path ?? "");
			}
			const issue = validateContract(next, form.value.path ?? "");
			if (issue) throw new Error(issue.message);
			contract.value = next;
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
	path => {
		if (!props.modelValue || initializing.value || advanced.value) return;
		contract.value.parameters = syncPathParameters(contract.value.parameters ?? [], path ?? "");
	}
);
watch(
	() => form.value.method,
	method => {
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
	contract.value.requestBody = { ...contract.value.requestBody, required };
}
function toggleAdvanced() {
	formError.value = "";
	if (advanced.value) {
		try {
			const next = parseContract(contractText.value);
			const issue = validateContract(next, form.value.path ?? "");
			if (issue) throw new Error(issue.message);
			contract.value = next;
			advanced.value = false;
		} catch (cause) {
			formError.value = cause instanceof Error ? cause.message : "契约 JSON 无效";
		}
	} else {
		contractText.value = JSON.stringify(contract.value, null, 2);
		advanced.value = true;
	}
}
function save() {
	formError.value = "";
	if (!form.value.id?.trim()) {
		tab.value = "basic";
		formError.value = "请填写 API 标识";
		return;
	}
	if (form.value.path && !form.value.path.startsWith("/")) {
		tab.value = "basic";
		formError.value = "请求路径必须以 / 开头";
		return;
	}
	try {
		const next = advanced.value ? parseContract(contractText.value) : copyJson(contract.value);
		const issue = validateContract(next, form.value.path ?? "");
		if (issue) {
			tab.value = issue.tab;
			if (issue.tab === "input")
				inputTab.value = issue.message.includes("路径") ? "path" : issue.message.includes("请求体") ? "body" : "query";
			formError.value = issue.message;
			return;
		}
		emit("save", { ...copyJson(form.value), id: form.value.id.trim(), contract: next });
	} catch (cause) {
		formError.value = cause instanceof Error ? cause.message : "契约 JSON 无效";
	}
}
</script>
