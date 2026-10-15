<template>
	<div class="api-schema-editor">
		<div class="api-editor-toolbar">
			<div class="api-editor-inline">
				<span>根节点类型</span
				><ElSelect
					:model-value="modelValue.type ?? ''"
					:disabled="isAdvancedSchema(modelValue)"
					aria-label="根节点类型"
					@change="emit('update:modelValue', changeSchemaType(modelValue, String($event)))"
					><ElOption v-if="!modelValue.type" label="未指定" value="" /><ElOption
						v-for="type in schemaTypes"
						:key="type"
						:label="type"
						:value="type"
				/></ElSelect>
			</div>
			<ElButton text type="primary" @click="openAdvanced">高级结构</ElButton>
		</div>
		<ElAlert
			v-if="isAdvancedSchema(modelValue)"
			title="此结构包含引用或组合定义，原始内容已保留，请通过高级结构编辑。"
			type="info"
			:closable="false"
		/>
		<div v-else class="api-schema-table">
			<div v-if="modelValue.type === 'object' || modelValue.type === 'array'" class="api-schema-row api-table-heading">
				<span>字段名</span><span>类型</span><span>必填</span><span>说明</span><span>示例值</span><span />
			</div>
			<ApiSchemaFields :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" />
			<p v-if="!['object', 'array'].includes(modelValue.type ?? '')" class="api-editor-hint">
				此响应或请求体使用基础类型，可在高级结构中设置格式、默认值、枚举及范围。
			</p>
		</div>
		<ElDialog v-model="advancedVisible" title="编辑字段结构" width="720px" append-to-body destroy-on-close>
			<p class="api-editor-hint">支持格式、默认值、枚举、取值范围和引用等结构配置。</p>
			<ElInput v-model="advancedText" type="textarea" :rows="16" class="api-json-input" aria-label="字段结构 JSON" />
			<ElAlert v-if="advancedError" :title="advancedError" type="error" :closable="false" />
			<template #footer
				><ElButton @click="advancedVisible = false">取消</ElButton
				><ElButton type="primary" @click="applyAdvanced">应用</ElButton></template
			>
		</ElDialog>
	</div>
</template>

<script setup lang="ts">
import { ref } from "vue";

import { changeSchemaType, isAdvancedSchema, schemaTypes, validateSchema } from "../api-contract";
import type { ApiSchema } from "../types";
import ApiSchemaFields from "./ApiSchemaFields.vue";

const props = defineProps<{ modelValue: ApiSchema }>();
const emit = defineEmits<{ "update:modelValue": [value: ApiSchema] }>();
const advancedVisible = ref(false);
const advancedText = ref("");
const advancedError = ref("");
function openAdvanced() {
	advancedText.value = JSON.stringify(props.modelValue, null, 2);
	advancedError.value = "";
	advancedVisible.value = true;
}
function applyAdvanced() {
	try {
		const schema: unknown = JSON.parse(advancedText.value);
		const error = validateSchema(schema);
		if (error) throw new Error(error);
		emit("update:modelValue", schema as ApiSchema);
		advancedVisible.value = false;
	} catch (cause) {
		advancedError.value = cause instanceof Error ? cause.message : "请输入有效的字段结构";
	}
}
</script>
