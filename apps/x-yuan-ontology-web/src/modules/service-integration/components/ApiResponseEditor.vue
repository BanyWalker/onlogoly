<template>
	<div class="api-response-editor">
		<div class="api-editor-toolbar">
			<div class="api-editor-inline">
				<span>响应状态</span
				><ElSelect v-model="activeCode" placeholder="选择状态码" aria-label="响应状态码"
					><ElOption
						v-for="(response, code) in responseDefinitions"
						:key="code"
						:label="`${code} ${response.description ?? ''}`"
						:value="String(code)"
				/></ElSelect>
			</div>
			<div>
				<ElButton type="primary" plain :icon="Plus" @click="openAdd">新增响应</ElButton
				><ElPopconfirm
					v-if="activeResponse"
					title="确认删除此响应定义？"
					confirm-button-text="删除"
					cancel-button-text="取消"
					@confirm="removeResponse"
					><template #reference><ElButton text type="danger">删除响应</ElButton></template></ElPopconfirm
				>
			</div>
		</div>
		<template v-if="activeResponse">
			<ElAlert
				v-if="activeResponse.$ref"
				title="此响应使用引用定义，原始内容已保留，请通过高级 JSON 编辑。"
				type="info"
				:closable="false"
			/>
			<template v-else>
				<ElForm label-position="top"
					><ElFormItem label="响应说明"
						><ElInput
							:model-value="activeResponse.description"
							placeholder="例如：查询成功、参数无效"
							@update:model-value="patchResponse({ description: $event })" /></ElFormItem
				></ElForm>
				<p v-if="activeCode === '204'" class="api-editor-hint">204 表示无响应体，请将内容类型设为“无”。</p>
				<ApiContentEditor
					:key="activeCode"
					:model-value="activeResponse.content ?? {}"
					@update:model-value="patchResponse({ content: $event })"
				/>
				<ElCollapse class="api-response-headers"
					><ElCollapseItem title="响应 Header" name="headers"
						><ApiParameterEditor v-model="headers" location="header" response /></ElCollapseItem
				></ElCollapse>
			</template>
		</template>
		<ElEmpty v-else description="暂无响应定义，点击新增响应进行配置" :image-size="60" />
		<ElDialog v-model="addVisible" title="新增响应" width="440px" append-to-body destroy-on-close>
			<ElForm label-position="top"
				><ElFormItem label="HTTP 状态码"
					><ElSelect v-model="newCode" filterable allow-create default-first-option aria-label="新增响应状态码"
						><ElOption
							v-for="code in ['200', '201', '202', '204', '400', '401', '403', '404', '409', '500', '503', 'default']"
							:key="code"
							:label="code"
							:value="code" /></ElSelect></ElFormItem
				><ElFormItem label="说明"><ElInput v-model="newDescription" placeholder="响应结果说明" /></ElFormItem
			></ElForm>
			<ElAlert v-if="addError" :title="addError" type="error" :closable="false" />
			<template #footer
				><ElButton @click="addVisible = false">取消</ElButton
				><ElButton type="primary" @click="addResponse">添加</ElButton></template
			>
		</ElDialog>
	</div>
</template>

<script setup lang="ts">
import { Plus } from "@element-plus/icons-vue";
import { computed, ref, watch } from "vue";

import type { ApiParameter, ApiResponse } from "../types";
import ApiContentEditor from "./ApiContentEditor.vue";
import ApiParameterEditor from "./ApiParameterEditor.vue";

const props = defineProps<{ modelValue: Record<string, ApiResponse> }>();
const emit = defineEmits<{ "update:modelValue": [value: Record<string, ApiResponse>] }>();
const responseDefinitions = computed(() =>
	Object.fromEntries(Object.entries(props.modelValue).filter(([code]) => !code.startsWith("x-")))
);
const activeCode = ref(Object.keys(responseDefinitions.value)[0] ?? "");
const activeResponse = computed(() => props.modelValue[activeCode.value]);
const addVisible = ref(false);
const newCode = ref("200");
const newDescription = ref("");
const addError = ref("");
const headers = computed<ApiParameter[]>({
	get: () => Object.entries(activeResponse.value?.headers ?? {}).map(([name, header]) => ({ ...header, name, in: "header" })),
	set: rows =>
		patchResponse({ headers: Object.fromEntries(rows.map(({ name, in: _location, ...header }) => [name ?? "", header])) })
});
watch(
	() => Object.keys(responseDefinitions.value).join("|"),
	() => {
		if (!responseDefinitions.value[activeCode.value]) activeCode.value = Object.keys(responseDefinitions.value)[0] ?? "";
	}
);
function patchResponse(fields: Partial<ApiResponse>) {
	emit("update:modelValue", { ...props.modelValue, [activeCode.value]: { ...activeResponse.value, ...fields } });
}
function openAdd() {
	newCode.value = ["200", "201", "400", "500"].find(code => !props.modelValue[code]) ?? "";
	newDescription.value = "";
	addError.value = "";
	addVisible.value = true;
}
function addResponse() {
	const code = newCode.value.trim();
	if (!/^(?:[1-5]\d{2}|[1-5]XX|default)$/.test(code)) {
		addError.value = "请输入有效 HTTP 状态码或 default";
		return;
	}
	if (props.modelValue[code]) {
		addError.value = "该响应状态码已存在";
		return;
	}
	emit("update:modelValue", {
		...props.modelValue,
		[code]: { description: newDescription.value.trim() || (code.startsWith("2") ? "成功" : "错误响应") }
	});
	activeCode.value = code;
	addVisible.value = false;
}
function removeResponse() {
	const responses = { ...props.modelValue };
	delete responses[activeCode.value];
	emit("update:modelValue", responses);
}
</script>
